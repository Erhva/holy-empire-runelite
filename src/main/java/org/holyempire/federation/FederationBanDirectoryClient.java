/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/**
 * Read-only combined private ban view. Retrieved only with a verified officer's
 * bearer token. The response is NEVER written to disk or RuneLite settings.
 */
final class FederationBanDirectoryClient
{
	static final int MAX_RESPONSE = 600_000;
	static final int MAX_ENTRIES = 5000;
	static final long CACHE_MILLIS = 5 * 60 * 1000L;

	private FederationBanDirectoryClient()
	{
	}

	static final class Entry
	{
		final String chapter;
		final String name;
		final String identityStatus;
		final long capturedAt;

		Entry(String chapter, String name, String identityStatus, long capturedAt)
		{
			this.chapter = chapter;
			this.name = name;
			this.identityStatus = identityStatus;
			this.capturedAt = capturedAt;
		}
	}

	static final class Chapter
	{
		final String name;
		final long capturedAt;
		final int revision;

		Chapter(String name, long capturedAt, int revision)
		{
			this.name = name;
			this.capturedAt = capturedAt;
			this.revision = revision;
		}
	}

	static final class Snapshot
	{
		final List<Entry> entries;
		final Map<String, Chapter> chapters;

		Snapshot(List<Entry> entries, Map<String, Chapter> chapters)
		{
			this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
			this.chapters = Collections.unmodifiableMap(new LinkedHashMap<>(chapters));
		}
	}

	static Snapshot fetch(String chapter, String base, String bearer, OkHttpClient client)
		throws Exception
	{
		String approvedChapter = FederationBanRegistry.officialChapter(chapter);
		if (approvedChapter == null)
		{
			throw new IllegalArgumentException("Unknown chapter.");
		}
		if (!FederationBanUploadClient.acceptedEndpoint(base))
		{
			throw new IllegalArgumentException("Official HTTPS ban endpoint required.");
		}
		if (bearer == null || bearer.length() < 24 || bearer.length() > 256)
		{
			throw new IllegalArgumentException("Officer credential is missing.");
		}
		JsonObject requestJson = new JsonObject();
		requestJson.addProperty("chapter", approvedChapter);
		requestJson.addProperty("mode", "list");
		OkHttpClient bounded = client.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(8, TimeUnit.SECONDS)
								   .build();
		Request request =
			new Request.Builder()
				.url(base.replaceAll("/+$", "") + "/v1/ban-check")
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
										 requestJson.toString().getBytes(StandardCharsets.UTF_8)))
				.header("Authorization", "Bearer " + bearer)
				.header("Accept", "application/json")
				.build();
		try (Response response = bounded.newCall(request).execute())
		{
			if (response.code() != 200)
			{
				throw new IOException("Private ban directory returned HTTP " + response.code() +
									  ".");
			}
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > MAX_RESPONSE)
			{
				throw new IOException("Private ban directory response too large.");
			}
			byte[] bytes;
			try (InputStream stream = body.byteStream())
			{
				bytes = stream.readNBytes(MAX_RESPONSE + 1);
			}
			if (bytes.length > MAX_RESPONSE)
			{
				throw new IOException("Private ban directory response too large.");
			}
			try
			{
				JsonObject document = new JsonParser()
										  .parse(new String(bytes, StandardCharsets.UTF_8))
										  .getAsJsonObject();
				if (document.get("schema_version").getAsInt() != 1 ||
					!"ban-list".equals(document.get("kind").getAsString()))
				{
					throw new IOException("Unexpected private ban directory schema.");
				}
				JsonArray rawEntries = document.getAsJsonArray("entries");
				JsonArray rawChapters = document.getAsJsonArray("snapshots");
				if (rawEntries == null || rawEntries.size() > MAX_ENTRIES || rawChapters == null ||
					rawChapters.size() > HolyEmpireChapterCatalog.ORDERED.size())
				{
					throw new IOException("Private ban directory exceeds safe limits.");
				}
				Map<String, Chapter> chapters = new LinkedHashMap<>();
				for (JsonElement element : rawChapters)
				{
					JsonObject row = element.getAsJsonObject();
					String chapterName = row.get("chapter").getAsString();
					long when = row.get("captured_at").getAsLong();
					int revision = row.get("revision").getAsInt();
					if (FederationBanRegistry.officialChapter(chapterName) == null || when <= 0 ||
						revision < 1 || chapters.containsKey(chapterName))
					{
						throw new IOException("Invalid chapter snapshot metadata.");
					}
					chapters.put(chapterName, new Chapter(chapterName, when, revision));
				}
				List<Entry> entries = new ArrayList<>();
				for (JsonElement element : rawEntries)
				{
					JsonObject row = element.getAsJsonObject();
					String from = row.get("chapter").getAsString();
					String name = row.get("current_name").getAsString();
					String identity = row.get("identity_status").getAsString();
					long when = row.get("captured_at").getAsLong();
					if (!chapters.containsKey(from) ||
						FederationBanRegistry.validName(name) == null ||
						!(identity.equals("tracked") || identity.equals("unresolved")) ||
						when != chapters.get(from).capturedAt)
					{
						throw new IOException("Invalid private ban directory entry.");
					}
					entries.add(new Entry(from, name, identity, when));
				}
				return new Snapshot(entries, chapters);
			}
			catch (IOException failure)
			{
				throw failure;
			}
			catch (RuntimeException failure)
			{
				throw new IOException("Invalid private ban directory response.", failure);
			}
		}
	}
}
