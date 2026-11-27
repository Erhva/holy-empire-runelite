/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

/**
 * Private on-disk outbox, scoped to this RuneLite plugin's local data folder.
 * Keeps incomplete attendance through client crashes and retries it when the
 * verified Discord identity reconnects. No bearer tokens are stored here.
 *
 * Every checkpoint is an uploadable immutable report snapshot. A snapshot
 * written during active tracking represents observation up to checkpointAt,
 * not extra time after the client crashed.
 */
final class HolyEmpireAttendanceRecoveryStore
{
	static final int VERSION = 1;
	private static final int MAX_BYTES = HolyEmpireAttendanceArchiveClient.MAX_REQUEST + 2048;
	private static final String PREFIX = "report-";
	private static final String SUFFIX = ".json";
	private final Filepath directory;

	static final class Pending
	{
		final String reportId;
		final String host;
		final JsonObject payload;
		final boolean wasRunning;
		final long checkpointAt;

		Pending(String reportId, String host, JsonObject payload, boolean wasRunning,
				long checkpointAt)
		{
			this.reportId = reportId;
			this.host = host;
			this.payload = payload;
			this.wasRunning = wasRunning;
			this.checkpointAt = checkpointAt;
		}
	}

	HolyEmpireAttendanceRecoveryStore(Filepath pluginDirectory) throws IOException
	{
		// Production-only rooted Filepath supplied by RuneLite's plugin data
		// directory. No unrestricted filesystem root is ever constructed.
		directory = pluginDirectory.joinSegment("attendance-recovery");
		directory.createDirectories();
	}

	private static JsonObject readDocument(Filepath file) throws IOException
	{
		try (FileChannel channel =
				 file.openFileChannel(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))
		{
			long length = channel.size();
			if (length < 1 || length > MAX_BYTES)
				throw new IOException("Attendance journal exceeds safe limit");
			ByteBuffer buffer = ByteBuffer.allocate((int)length);
			while (buffer.hasRemaining())
				if (channel.read(buffer) < 0)
					throw new IOException("Truncated attendance journal");
			return new JsonParser()
				.parse(new String(buffer.array(), StandardCharsets.UTF_8))
				.getAsJsonObject();
		}
	}

	private static String safeId(String id) throws IOException
	{
		try
		{
			UUID parsed = UUID.fromString(id);
			if (!parsed.toString().equals(id))
				throw new IllegalArgumentException();
			return id;
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid attendance journal ID", error);
		}
	}

	private Filepath file(String id) throws IOException
	{
		return directory.joinSegment(PREFIX + safeId(id) + SUFFIX);
	}

	synchronized void checkpoint(HolyEmpireAttendanceTracker.Report report, long capturedAt)
		throws IOException
	{
		if (report == null || capturedAt < report.startedAt || capturedAt <= 0)
			throw new IOException("Invalid attendance checkpoint");
		JsonObject data = HolyEmpireAttendanceArchiveClient.encodeCheckpoint(report, capturedAt);
		JsonObject envelope = new JsonObject();
		envelope.addProperty("version", VERSION);
		envelope.addProperty("checkpoint_at", capturedAt);
		envelope.addProperty("was_running", report.running);
		envelope.add("payload", data);
		byte[] bytes = envelope.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_BYTES)
			throw new IOException("Attendance journal exceeds the archive limit");
		Filepath target = file(report.reportId);
		if (target.isFile() && target.size() <= MAX_BYTES)
		{
			try
			{
				JsonObject old = readDocument(target);
				if (old.get("version").getAsInt() == VERSION &&
					report.reportId.equals(
						old.getAsJsonObject("payload").get("report_id").getAsString()))
				{
					boolean priorFinished = !old.get("was_running").getAsBoolean();
					long previous = old.get("checkpoint_at").getAsLong();
					if ((priorFinished && report.running) ||
						(previous > capturedAt && (report.running || priorFinished)))
						return;
				}
			}
			catch (RuntimeException | IOException invalid)
			{
				// A malformed prior checkpoint may be replaced by valid
				// evidence from this same live report only.
			}
		}
		Filepath temporary = directory.createTempFile(".attendance-", ".tmp");
		try
		{
			try (FileChannel channel = temporary.openFileChannel(
					 StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
			{
				ByteBuffer buffer = ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining())
					channel.write(buffer);
				channel.force(true);
			}
			try
			{
				temporary.moveTo(target, StandardCopyOption.REPLACE_EXISTING,
								 StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException unsupported)
			{
				temporary.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			temporary.deleteIfExists();
		}
	}

	synchronized List<Pending> pendingFor(String account) throws IOException
	{
		String normalized = WomDirectoryClient.normalize(account);
		List<Pending> output = new ArrayList<>();
		try (Stream<Filepath> entries = directory.walk(1))
		{
			for (Filepath item : (Iterable<Filepath>)entries::iterator)
			{
				if (!item.isFile() || item.size() > MAX_BYTES)
					continue;
				try
				{
					String filename = item.getFileName();
					if (!filename.matches("report-[0-9a-f-]{36}\\.json"))
						continue;
					String id =
						filename.substring(PREFIX.length(), filename.length() - SUFFIX.length());
					safeId(id);
					JsonObject envelope = readDocument(item);
					if (envelope.get("version").getAsInt() != VERSION)
						continue;
					JsonObject payload = envelope.getAsJsonObject("payload");
					if (payload == null || !id.equals(payload.get("report_id").getAsString()) ||
						!normalized.equals(
							WomDirectoryClient.normalize(payload.get("host").getAsString())) ||
						payload.get("schema_version").getAsInt() != 2)
						continue;
					output.add(new Pending(id, normalized, payload,
										   envelope.get("was_running").getAsBoolean(),
										   envelope.get("checkpoint_at").getAsLong()));
				}
				catch (RuntimeException | IOException ignored)
				{
					// Corrupt records remain on disk for manual recovery.
				}
			}
		}
		output.sort(Comparator.comparingLong(a -> a.checkpointAt));
		return output;
	}

	synchronized void acknowledge(String reportId) throws IOException
	{
		file(reportId).deleteIfExists();
	}

	Filepath directory()
	{
		return directory;
	}
}
