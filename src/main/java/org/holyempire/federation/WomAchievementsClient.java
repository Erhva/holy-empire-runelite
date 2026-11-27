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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import net.runelite.client.util.Filepath;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Read the shared server cache; never call WOM directly from every RuneLite user. */
final class WomAchievementsClient
{
	static final class Achievement
	{
		final String player, description, chapter;
		final long createdAt;

		Achievement(String player, String description, String chapter, long createdAt)
		{
			this.player = player;
			this.description = description;
			this.chapter = chapter;
			this.createdAt = createdAt;
		}

		@Override public boolean equals(Object value)
		{
			if (!(value instanceof Achievement))
				return false;
			Achievement a = (Achievement)value;
			return player.equals(a.player) && description.equals(a.description) &&
				chapter.equals(a.chapter) && createdAt == a.createdAt;
		}

		@Override public int hashCode()
		{
			return Objects.hash(player, description, chapter, createdAt);
		}
	}

	static final class Snapshot
	{
		final long generatedAt;
		final List<Achievement> entries;

		Snapshot(long generatedAt, List<Achievement> entries)
		{
			this.generatedAt = generatedAt;
			this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
		}
	}

	static Snapshot fetch(OkHttpClient http) throws IOException
	{
		return fetch(http, null);
	}

	static Snapshot fetch(OkHttpClient http, Filepath cache) throws IOException
	{
		String origin = ChapterEventsClient.origin();
		if (!ChapterEventsClient.acceptedChapterOrigin(origin))
			throw new IOException("Untrusted WOM achievement origin");
		Request request = new Request.Builder()
							  .url(origin + "/v1/empire-comms/achievements")
							  .header("Accept", "application/json")
							  .get()
							  .build();
		OkHttpClient bounded = http.newBuilder()
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(8, TimeUnit.SECONDS)
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .build();
		try (Response response = bounded.newCall(request).execute())
		{
			if (response.code() != 200 || !response.header("Content-Type", "")
											   .toLowerCase(Locale.ROOT)
											   .startsWith("application/json"))
				throw new IOException("WOM achievements temporarily unavailable");
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > 64_000)
				throw new IOException("WOM achievements too large");
			byte[] payload;
			try (InputStream stream = body.byteStream())
			{
				payload = stream.readNBytes(64_001);
			}
			if (payload.length > 64_000)
				throw new IOException("WOM achievements too large");
			Snapshot result = parse(payload, Instant.now());
			if (cache != null)
			{
				try
				{
					writeCached(cache, payload);
				}
				catch (Exception ignored)
				{
					// A read-only filesystem must never break fresh news.
				}
			}
			return result;
		}
	}

	/** This feed is PUBLIC news, not an identity/permission cache. */
	static Snapshot readCached(Filepath cache) throws IOException
	{
		if (cache == null || !cache.isFile() || cache.size() < 1 || cache.size() > 64_000)
			return null;
		try (FileChannel input =
				 cache.openFileChannel(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))
		{
			long size = input.size();
			if (size < 1 || size > 64_000)
				return null;
			ByteBuffer bytes = ByteBuffer.allocate((int)size);
			while (bytes.hasRemaining())
			{
				if (input.read(bytes) < 0)
					return null;
			}
			// Time checks and 24-hour filtering are identical to HTTP data.
			return parse(bytes.array(), Instant.now());
		}
	}

	static void writeCached(Filepath cache, byte[] validated) throws IOException
	{
		if (cache == null || validated == null || validated.length < 1 || validated.length > 64_000)
			throw new IOException("Invalid WOM achievement cache");
		cache.getParent().createDirectories();
		Filepath temp = cache.getParent().createTempFile("holy-empire-achievements-", ".tmp");
		try
		{
			temp.write(validated, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
			try
			{
				temp.moveTo(cache, StandardCopyOption.ATOMIC_MOVE,
							StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException error)
			{
				temp.moveTo(cache, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			temp.deleteIfExists();
		}
	}

	private static String text(JsonObject doc, String key, int limit, String pattern)
		throws IOException
	{
		JsonElement value = doc.get(key);
		if (value == null || !value.isJsonPrimitive())
			throw new IOException("Invalid WOM achievement text");
		String result = value.getAsString();
		if (result.isEmpty() || result.length() > limit || !result.matches(pattern))
			throw new IOException("Invalid WOM achievement text");
		return result;
	}

	static Snapshot parse(byte[] bytes, Instant current) throws IOException
	{
		try
		{
			JsonObject doc =
				new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
			if (doc.get("schema_version").getAsInt() != 1 ||
				!"holy-empire-wom-achievements".equals(doc.get("kind").getAsString()) ||
				doc.get("window_hours").getAsInt() != 24)
				throw new IOException("Unsupported WOM achievements feed");
			long now = current.getEpochSecond();
			long generated = doc.get("generated_at").getAsLong();
			if (generated > now + 300 || generated < now - 7200)
				throw new IOException("WOM achievements feed stale");
			JsonArray entries = doc.getAsJsonArray("entries");
			if (entries == null || entries.size() > 120)
				throw new IOException("WOM achievements list malformed");
			List<Achievement> output = new ArrayList<>();
			Set<String> dedup = new HashSet<>();
			for (JsonElement element : entries)
			{
				JsonObject row = element.getAsJsonObject();
				String player = text(row, "player", 12, "[A-Za-z0-9 _-]+");
				String description = text(row, "achievement", 110, "[^<>\\p{Cntrl}]+");
				String chapter = text(row, "chapter", 32, "[A-Za-z0-9 _-]+");
				long created = row.get("created_at").getAsLong();
				if (created < now - 86400 || created > now + 300)
					continue;
				String key = player + "|" + description + "|" + created;
				if (dedup.add(key))
					output.add(new Achievement(player, description, chapter, created));
			}
			output.sort((a, b) -> Long.compare(b.createdAt, a.createdAt));
			return new Snapshot(generated, output);
		}
		catch (IOException error)
		{
			throw error;
		}
		catch (RuntimeException error)
		{
			throw new IOException("Malformed WOM achievements feed", error);
		}
	}
}
