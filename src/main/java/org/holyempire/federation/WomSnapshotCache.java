/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.client.util.Filepath;

/** Private local cache of PUBLIC WOM rosters, not an identity-verification mechanism. */
final class WomSnapshotCache
{
	static final long FRESH_MS = 30L * 60 * 1000;
	static final long MAX_STALE_MS = 24L * 60 * 60 * 1000;
	static final long FAILURE_BACKOFF_MS = 10L * 60 * 1000;
	static final int FILE_LIMIT = 5 * 1024 * 1024;
	interface Fetcher
	{
		WomDirectoryClient.Snapshot load() throws Exception;
	}

	static final class Result
	{
		final WomDirectoryClient.Snapshot snapshot;
		final boolean stale;

		Result(WomDirectoryClient.Snapshot snapshot, boolean stale)
		{
			this.snapshot = snapshot;
			this.stale = stale;
		}
	}

	private WomDirectoryClient.Snapshot cached;
	private long fetchedAt;
	private long retryAt;
	private boolean diskChecked;

	synchronized Result get(long now, Filepath path, Fetcher fetcher) throws Exception
	{
		return get(now, path, fetcher, false);
	}

	synchronized Result get(long now, Filepath path, Fetcher fetcher, boolean force)
		throws Exception
	{
		if (!diskChecked)
		{
			diskChecked = true;
			read(path, now);
		}
		long age = now - fetchedAt;
		if (!force && cached != null && age >= 0 && age < FRESH_MS)
		{
			return new Result(cached, false);
		}
		if (now < retryAt)
		{
			if (cached != null && age >= 0 && age < MAX_STALE_MS)
			{
				return new Result(cached, true);
			}
			throw new IOException("WOM request cooldown active");
		}
		try
		{
			WomDirectoryClient.Snapshot current = fetcher.load();
			if (current == null || current.members.isEmpty() ||
				current.chapters.size() != current.directory.groupCount())
			{
				throw new IOException("Incomplete WOM roster snapshot");
			}
			cached = current;
			fetchedAt = current.sourceTimestampMs > 0 ? current.sourceTimestampMs : now;
			retryAt = 0;
			try
			{
				write(path, current, fetchedAt);
			}
			catch (Exception writeError)
			{
				// Disk-cache failure does not invalidate fresh remote data.
				org.slf4j.LoggerFactory.getLogger(WomSnapshotCache.class)
					.warn("Could not persist public WOM roster cache", writeError);
			}
			return new Result(current, false);
		}
		catch (Exception failure)
		{
			// All client roster fetches use the fixed public CDN and share
			// the same conservative retry delay. There are no client WOM
			// API requests or WOM-specific rate-limit exceptions.
			long delay = FAILURE_BACKOFF_MS;
			retryAt = now + delay;
			org.slf4j.LoggerFactory.getLogger(WomSnapshotCache.class)
				.warn("WOM roster refresh failed; retry in at least {} seconds: {}", delay / 1000,
					  failure.toString());
			if (cached != null && age >= 0 && age < MAX_STALE_MS)
			{
				return new Result(cached, true);
			}
			throw failure;
		}
	}

	private void read(Filepath path, long now)
	{
		try
		{
			if (!path.isFile() || path.size() > FILE_LIMIT)
			{
				return;
			}
			byte[] bytes;
			try (FileChannel input =
					 path.openFileChannel(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))
			{
				long size = input.size();
				if (size < 1 || size > FILE_LIMIT)
				{
					return;
				}
				ByteBuffer buffer = ByteBuffer.allocate((int)size);
				while (buffer.hasRemaining())
				{
					if (input.read(buffer) < 0)
					{
						return;
					}
				}
				bytes = buffer.array();
			}
			JsonObject document =
				new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			if (document.get("schema").getAsInt() != 2)
			{
				return;
			}
			// Old disk caches cannot supply nonmember ban warnings. Fetch the
			// upgraded manifest once rather than appearing silently clear for hours.
			if (!document.has("publicBanWarningsAvailable"))
			{
				return;
			}
			long timestamp = document.get("fetchedAt").getAsLong();
			if (timestamp > now || now - timestamp > MAX_STALE_MS)
			{
				return;
			}
			JsonArray groups = document.getAsJsonArray("groups");
			if (groups == null)
			{
				return;
			}
			ChapterDirectory directory =
				ChapterDirectory.parse(document.getAsJsonArray("directory"), groups.size());
			Map<String, Set<String>> combined = new HashMap<>();
			Map<String, List<WomDirectoryClient.ChapterMember>> chapters = new LinkedHashMap<>();
			Set<Integer> ids = new HashSet<>();
			for (JsonElement item : groups)
			{
				JsonObject group = item.getAsJsonObject();
				int id = group.get("id").getAsInt();
				HolyEmpireChapterCatalog.Chapter chapter = directory.forGroup(id);
				if (chapter == null || !ids.add(id))
				{
					return;
				}
				WomDirectoryClient.mergeGroup(group, id, chapter.name, combined, chapters);
			}
			if (ids.size() != directory.groupCount())
			{
				return;
			}
			Map<String, List<String>> members = new HashMap<>();
			for (Map.Entry<String, Set<String>> entry : combined.entrySet())
			{
				members.put(entry.getKey(),
							Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
			}
			Set<String> publicHashes = new HashSet<>();
			boolean warningAvailable = document.has("publicBanWarningsAvailable") &&
									   document.get("publicBanWarningsAvailable").getAsBoolean();
			if (warningAvailable)
			{
				JsonArray digests = document.getAsJsonArray("publicBanDigests");
				if (digests == null || digests.size() > 5000)
				{
					return;
				}
				for (JsonElement item : digests)
				{
					String digest = item.getAsString();
					if (!digest.matches("[0-9a-f]{64}") || !publicHashes.add(digest))
					{
						return;
					}
				}
			}
			cached = new WomDirectoryClient.Snapshot(
				Collections.unmodifiableMap(members), Collections.unmodifiableMap(chapters),
				timestamp, publicHashes, warningAvailable, directory);
			fetchedAt = timestamp;
		}
		catch (Exception error)
		{
			// Corrupt/incompatible local data is ignored; the live WOM path is authoritative.
			org.slf4j.LoggerFactory.getLogger(WomSnapshotCache.class)
				.debug("Ignoring unreadable WOM roster cache: {}", error.toString());
		}
	}

	private void write(Filepath path, WomDirectoryClient.Snapshot snapshot, long now)
		throws IOException
	{
		JsonObject document = new JsonObject();
		document.addProperty("schema", 2);
		document.add("directory", snapshot.directory.toJson());
		document.addProperty("fetchedAt", now);
		JsonArray groups = new JsonArray();
		for (HolyEmpireChapterCatalog.Chapter chapter : snapshot.directory.ordered)
		{
			if (!chapter.hasWomRoster())
			{
				continue;
			}
			List<WomDirectoryClient.ChapterMember> roster = snapshot.chapters.get(chapter.name);
			if (roster == null || roster.isEmpty())
			{
				throw new IOException("Missing WOM chapter");
			}
			JsonObject group = new JsonObject();
			group.addProperty("id", chapter.groupId);
			JsonArray memberships = new JsonArray();
			for (WomDirectoryClient.ChapterMember member : roster)
			{
				JsonObject item = new JsonObject();
				JsonObject player = new JsonObject();
				player.addProperty("displayName", member.name);
				item.add("player", player);
				item.addProperty("role", member.rank);
				memberships.add(item);
			}
			group.add("memberships", memberships);
			groups.add(group);
		}
		document.add("groups", groups);
		document.addProperty("publicBanWarningsAvailable", snapshot.publicBanStatusAvailable);
		if (snapshot.publicBanStatusAvailable)
		{
			JsonArray digests = new JsonArray();
			for (String hash : new TreeSet<>(snapshot.publicBanHashes))
			{
				digests.add(hash);
			}
			document.add("publicBanDigests", digests);
		}
		byte[] bytes = document.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > FILE_LIMIT)
		{
			throw new IOException("WOM cache exceeds size limit");
		}
		path.getParent().createDirectories();
		Filepath temp = path.getParent().createTempFile("holy-empire-cache-", ".tmp");
		try
		{
			temp.write(bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
			try
			{
				temp.moveTo(path, StandardCopyOption.ATOMIC_MOVE,
							StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				temp.moveTo(path, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			temp.deleteIfExists();
		}
	}
}
