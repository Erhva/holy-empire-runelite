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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** HTTPS-only manifest + immutable compressed public WOM snapshot client. */
final class CentralRosterClient
{
	static final int MAX_MANIFEST = 16_384;
	static final int MAX_COMPRESSED = 6_000_000;
	static final int MAX_UNCOMPRESSED = 12_000_000;
	static final long MAX_AGE_SEC = 24 * 60 * 60;

	private CentralRosterClient()
	{
	}

	static URL url(String base, String suffix) throws IOException
	{
		URI uri;
		try
		{
			uri = new URI(base);
		}
		catch (URISyntaxException e)
		{
			throw new IOException("Invalid central roster URL", e);
		}
		if (uri.getRawUserInfo() != null || uri.getRawFragment() != null ||
			uri.getRawQuery() != null || uri.getHost() == null)
		{
			throw new IOException("Invalid central roster URL");
		}
		String protocol = uri.getScheme();
		if (!"https".equalsIgnoreCase(protocol))
		{
			throw new IOException("Central roster requires HTTPS");
		}
		String root = base.replaceAll("/+$", "");
		return new URL(root + suffix);
	}

	private static byte[] read(URL url, int limit, OkHttpClient http) throws IOException
	{
		// Clone RuneLite's injected client to retain its TLS/platform integration,
		// while preventing redirects to another host (including the private API).
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(8, TimeUnit.SECONDS)
								   .build();
		Request request = new Request.Builder()
							  .url(url)
							  .get()
							  .header("Accept", "application/json, application/gzip")
							  .header("User-Agent", "HolyEmpire-RuneLite/0.5")
							  .build();
		try (Response response = bounded.newCall(request).execute())
		{
			if (response.code() != 200)
			{
				throw new IOException("Holy Empire CDN returned HTTP " + response.code());
			}
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > limit)
			{
				throw new IOException("Central roster response too large");
			}
			try (InputStream in = body.byteStream())
			{
				byte[] result = in.readNBytes(limit + 1);
				if (result.length > limit)
				{
					throw new IOException("Central roster response too large");
				}
				return result;
			}
		}
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder result = new StringBuilder();
		for (byte b : bytes)
		{
			result.append(String.format(Locale.ROOT, "%02x", b & 255));
		}
		return result.toString();
	}

	/**
	 * Absent or malformed warning data cannot invalidate the public roster.
	 * Only checksum-verified, roster-bound digests enable non-officer warnings.
	 */
	static Set<String> readPublicWarnings(String base, JsonObject manifest, String rosterDigest,
										  long rosterTime, OkHttpClient http) throws Exception
	{
		if (!manifest.has("warning_path") || !manifest.has("warning_sha256") ||
			!manifest.has("warning_size"))
		{
			return null;
		}
		String path = manifest.get("warning_path").getAsString();
		String digest = manifest.get("warning_sha256").getAsString();
		int size = manifest.get("warning_size").getAsInt();
		if (!path.matches("snapshots/[0-9a-f]{64}\\.json\\.gz") ||
			!digest.matches("[0-9a-f]{64}") || !path.equals("snapshots/" + digest + ".json.gz") ||
			size < 20 || size > 512_000)
		{
			throw new IOException("Invalid nonmember warning metadata");
		}
		byte[] compressed = read(url(base, "/v1/" + path), 512_000, http);
		if (compressed.length != size ||
			!hex(MessageDigest.getInstance("SHA-256").digest(compressed)).equals(digest))
		{
			throw new IOException("Nonmember warning feed failed checksum verification");
		}
		byte[] json;
		try (InputStream zipped = new GZIPInputStream(new ByteArrayInputStream(compressed)))
		{
			json = zipped.readNBytes(512_001);
		}
		if (json.length > 512_000)
		{
			throw new IOException("Nonmember warning feed too large");
		}
		JsonObject payload =
			new JsonParser().parse(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
		if (payload.get("schema_version").getAsInt() != 1 ||
			!"nonmember-ban-warnings".equals(payload.get("kind").getAsString()) ||
			!rosterDigest.equals(payload.get("roster_sha256").getAsString()) ||
			payload.get("generated_at").getAsLong() != rosterTime)
		{
			throw new IOException("Nonmember warning data does not match WOM roster");
		}
		JsonArray entries = payload.getAsJsonArray("digests");
		if (entries == null || entries.size() > 5000)
		{
			throw new IOException("Invalid warning index");
		}
		Set<String> hashes = new HashSet<>();
		for (JsonElement item : entries)
		{
			String value = item.getAsString();
			if (!value.matches("[0-9a-f]{64}") || !hashes.add(value))
			{
				throw new IOException("Invalid or repeated warning entry");
			}
		}
		return hashes;
	}

	static WomDirectoryClient.Snapshot fetch(String base, OkHttpClient http) throws Exception
	{
		try
		{
			return fetchVersion(base, http, 2);
		}
		catch (IOException missingV2)
		{
			// V1 remains available for existing deployments until V2 is published.
			// Never downgrade on malformed V2 data, checksum failures or timeouts.
			if (!"Holy Empire CDN returned HTTP 404".equals(missingV2.getMessage()))
			{
				throw missingV2;
			}
			return fetchVersion(base, http, 1);
		}
	}

	private static JsonObject readManifest(String base, OkHttpClient http, int version)
		throws Exception
	{
		return new JsonParser()
			.parse(
				new String(read(url(base, "/v" + version + "/manifest.json"), MAX_MANIFEST, http),
						   StandardCharsets.UTF_8))
			.getAsJsonObject();
	}

	private static WomDirectoryClient.Snapshot fetchVersion(String base, OkHttpClient http,
															int version) throws Exception
	{
		JsonObject manifest = readManifest(base, http, version);
		if (manifest.get("schema_version").getAsInt() != version)
		{
			throw new IOException("Unexpected central roster schema version");
		}
		int count = manifest.get("group_count").getAsInt();
		if ((version == 1 && count != WomDirectoryClient.GROUPS.length) ||
			(version == 2 && (count < 16 || count > ChapterDirectory.MAX_GROUPS)))
		{
			throw new IOException("Unexpected central roster schema group count");
		}
		long asOf = manifest.get("generated_at").getAsLong();
		long now = System.currentTimeMillis() / 1000;
		if (asOf > now + 120 || now - asOf < 0 || now - asOf > MAX_AGE_SEC)
		{
			throw new IOException("Central roster too old or in future");
		}
		String path = manifest.get("path").getAsString();
		String digest = manifest.get("sha256").getAsString();
		if (!path.matches("snapshots/[0-9a-f]{64}\\.json\\.gz") ||
			!digest.matches("[0-9a-f]{64}") || !path.equals("snapshots/" + digest + ".json.gz"))
		{
			throw new IOException("Invalid central roster manifest");
		}
		byte[] zipped = read(url(base, "/v" + version + "/" + path), MAX_COMPRESSED, http);
		if (zipped.length != manifest.get("size").getAsInt() ||
			!hex(MessageDigest.getInstance("SHA-256").digest(zipped)).equals(digest))
		{
			throw new IOException("Central roster integrity mismatch");
		}
		byte[] unzipped;
		try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(zipped)))
		{
			unzipped = in.readNBytes(MAX_UNCOMPRESSED + 1);
		}
		if (unzipped.length > MAX_UNCOMPRESSED)
		{
			throw new IOException("Uncompressed roster too large");
		}
		JsonObject data =
			new JsonParser().parse(new String(unzipped, StandardCharsets.UTF_8)).getAsJsonObject();
		if (data.get("schema_version").getAsInt() != version ||
			data.get("generated_at").getAsLong() != asOf ||
			data.get("group_count").getAsInt() != count)
		{
			throw new IOException("Central roster metadata mismatch");
		}
		ChapterDirectory directory =
			version == 2 ? ChapterDirectory.parse(data.getAsJsonArray("chapters"), count)
						 : ChapterDirectory.legacy();
		JsonArray groups = data.getAsJsonArray("groups");
		if (groups == null || groups.size() != count || directory.groupCount() != count)
		{
			throw new IOException("Missing WOM group");
		}
		Map<String, Set<String>> combined = new HashMap<>();
		Map<String, List<WomDirectoryClient.ChapterMember>> chapters = new LinkedHashMap<>();
		Set<Integer> groupIds = new HashSet<>();
		for (JsonElement item : groups)
		{
			JsonObject group = item.getAsJsonObject();
			int id = group.get("id").getAsInt();
			HolyEmpireChapterCatalog.Chapter chapter = directory.forGroup(id);
			if (chapter == null || !groupIds.add(id) ||
				(group.has("clan") && !chapter.name.equals(group.get("clan").getAsString())))
			{
				throw new IOException("Unexpected WOM group in central roster");
			}
			WomDirectoryClient.mergeGroup(group, id, chapter.name, combined, chapters);
		}
		if (groupIds.size() != count || chapters.size() != count)
		{
			throw new IOException("Incomplete WOM chapter directory");
		}
		Map<String, List<String>> members = new HashMap<>();
		for (Map.Entry<String, Set<String>> member : combined.entrySet())
		{
			members.put(member.getKey(),
						Collections.unmodifiableList(new ArrayList<>(member.getValue())));
		}
		Set<String> publicHashes = Collections.emptySet();
		boolean publicStatusAvailable = false;
		try
		{
			JsonObject warningManifest = manifest;
			String warningDigest = digest;
			if (version == 2)
			{
				// Public ban warnings are still bound to the legacy V1 checksum.
				// Keep them available without changing the private ban-list protocol.
				warningManifest = readManifest(base, http, 1);
				if (warningManifest.get("schema_version").getAsInt() != 1 ||
					warningManifest.get("generated_at").getAsLong() != asOf)
				{
					throw new IOException("Unmatched legacy warning generation");
				}
				warningDigest = warningManifest.get("sha256").getAsString();
			}
			Set<String> warnings =
				readPublicWarnings(base, warningManifest, warningDigest, asOf, http);
			if (warnings != null)
			{
				publicHashes = warnings;
				publicStatusAvailable = true;
			}
		}
		catch (Exception invalid)
		{
			org.slf4j.LoggerFactory.getLogger(CentralRosterClient.class)
				.warn("Public nonmember warnings unavailable; preserving verified WOM roster");
		}
		return new WomDirectoryClient.Snapshot(Collections.unmodifiableMap(members),
											   Collections.unmodifiableMap(chapters), asOf * 1000,
											   publicHashes, publicStatusAvailable, directory);
	}
}
