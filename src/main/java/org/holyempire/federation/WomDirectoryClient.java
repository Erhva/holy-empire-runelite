/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;

final class WomDirectoryClient
{
	static final int[] GROUPS = {4586,	26050, 24957, 25581, 25023, 25200, 25714, 26631,
								 26034, 25197, 25721, 25972, 25577, 24596, 24039, 25230};
	static final String[] CLANS = {"Holy Empire", "Holy Chaos",	  "Holy Guard",	 "Sacred Order",
								   "Holy Heart",  "Holy Soul",	  "Holy Lights", "Six Shooters",
								   "Holy Smokes", "Holy Cow",	  "Holy Calf",	 "Sacred Blade",
								   "Cadia",		  "House of 465", "World 465",	 "Holy Kingdom"};
	static final String[] CLAN_CHATS = {
		"Holy Empire", "Holy Chaos",   "Holy Guard",  "Sacred Order", "Holy Heart", "Holy Soul",
		"Holy Lights", "Six Shooters", "Holy Smokes", "Holy Cow",	  "Holy Calf",	"Empire",
		"Cadia",	   "House of 465", "world 465",	  "holy kingdom"};
	private static final WomSnapshotCache SHARED_CACHE = new WomSnapshotCache();
	private static volatile Filepath cacheLocation;

	static void usePluginCache(Filepath file)
	{
		cacheLocation = file;
	}

	private static volatile boolean lastResultStale = false;

	static boolean isUsingStaleCache()
	{
		return lastResultStale;
	}

	static final class RateLimited extends IOException
	{
		final long retryMillis;

		RateLimited(int group, long retryMillis)
		{
			super("WOM group " + group + " returned HTTP 429");
			this.retryMillis = retryMillis;
		}
	}

	static final int LIMIT = 2097152;

	static final class ChapterMember
	{
		final String name;
		final String rank;

		ChapterMember(String name, String rank)
		{
			this.name = name;
			this.rank = rank;
		}

		String readableRank()
		{
			if (rank == null || rank.trim().isEmpty())
				return "Unranked";
			String[] parts = rank.replace('_', ' ').trim().split(" ");
			StringBuilder result = new StringBuilder();
			for (String part : parts)
			{
				if (part.isEmpty())
					continue;
				if (result.length() > 0)
					result.append(' ');
				if (part.equalsIgnoreCase("tzkal"))
					result.append("TzKal");
				else if (part.equalsIgnoreCase("tztok"))
					result.append("TzTok");
				else
					result.append(Character.toUpperCase(part.charAt(0)))
						.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
			return result.length() == 0 ? "Unranked" : result.toString();
		}
	}

	static final class Snapshot
	{
		final Map<String, List<String>> members;
		final Map<String, List<ChapterMember>> chapters;
		final long sourceTimestampMs;
		final Set<String> publicBanHashes;
		final boolean publicBanStatusAvailable;

		Snapshot(Map<String, List<String>> members)
		{
			this(members, Collections.emptyMap(), 0);
		}

		Snapshot(Map<String, List<String>> members, Map<String, List<ChapterMember>> chapters)
		{
			this(members, chapters, 0);
		}

		Snapshot(Map<String, List<String>> members, Map<String, List<ChapterMember>> chapters,
				 long sourceTimestampMs)
		{
			this(members, chapters, sourceTimestampMs, Collections.emptySet(), false);
		}

		Snapshot(Map<String, List<String>> members, Map<String, List<ChapterMember>> chapters,
				 long sourceTimestampMs, Set<String> publicBanHashes,
				 boolean publicBanStatusAvailable)
		{
			this.members = members;
			this.chapters = chapters;
			this.sourceTimestampMs = sourceTimestampMs;
			this.publicBanHashes = Collections.unmodifiableSet(new HashSet<>(publicBanHashes));
			this.publicBanStatusAvailable = publicBanStatusAvailable;
		}

		boolean eligible(String account)
		{
			return members.containsKey(normalize(account));
		}
	}

	static String normalize(String value)
	{
		return value == null
			? ""
			: value.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	static void mergeGroup(JsonObject body, int index, Map<String, Set<String>> combined)
		throws IOException
	{
		mergeGroup(body, index, combined, new HashMap<>());
	}

	static void mergeGroup(JsonObject body, int index, Map<String, Set<String>> combined,
						   Map<String, List<ChapterMember>> chapters) throws IOException
	{
		if (!body.has("id") || body.get("id").getAsInt() != GROUPS[index])
			throw new IOException("WOM group identity mismatch");
		JsonArray list = body.getAsJsonArray("memberships");
		if (list == null || list.size() == 0 || list.size() > 2000)
			throw new IOException("Invalid WOM roster");
		List<ChapterMember> chapter = new ArrayList<>();
		for (JsonElement item : list)
		{
			JsonObject membership = item.getAsJsonObject();
			JsonObject player = membership.getAsJsonObject("player");
			if (player == null)
				throw new IOException("Invalid WOM member");
			String name = player.has("displayName") && !player.get("displayName").isJsonNull()
							  ? player.get("displayName").getAsString()
							  : player.get("username").getAsString();
			String key = normalize(name);
			if (!key.matches("[a-z0-9 _-]{1,12}"))
				throw new IOException("Invalid RuneScape name");
			combined.computeIfAbsent(key, k -> new TreeSet<>()).add(CLANS[index]);
			String rank = membership.has("role") && !membership.get("role").isJsonNull()
							  ? membership.get("role").getAsString()
							  : "unranked";
			chapter.add(new ChapterMember(name, rank));
		}
		// Explicit Emperor display precedence, then original owner precedence;
		// retain WOM's relative order for every other member.
		chapter.sort(Comparator.comparingInt(m
											 -> HolyEmpireRankDisplay.isEmperor(m.name) ? 0
												: m.rank.equalsIgnoreCase("owner")		? 1
																						: 2));
		chapters.put(CLANS[index], Collections.unmodifiableList(chapter));
	}

	static Snapshot fetch(OkHttpClient http) throws Exception
	{
		return fetchFromCentral(http, false);
	}

	/** Equipment-triggered refresh bypasses our local TTL, never WOM itself. */
	static Snapshot fetchFreshForVexillum(OkHttpClient http) throws Exception
	{
		return fetchFromCentral(http, true);
	}

	private static Snapshot fetchFromCentral(OkHttpClient http, boolean force) throws Exception
	{
		Filepath cachePath = cacheLocation;
		if (cachePath == null)
			throw new IOException("Holy Empire plugin data directory unavailable");
		WomSnapshotCache.Result result =
			SHARED_CACHE.get(System.currentTimeMillis(), cachePath,
							 () -> fetchCentralOnly(RosterEndpoint.baseUrl(), http), force);
		lastResultStale = result.stale;
		return result.snapshot;
	}

	/** Public clients use the CDN only. Never shift CDN outage load onto WOM. */
	static Snapshot fetchCentralOnly(String base, OkHttpClient http) throws Exception
	{
		if (base == null || base.trim().isEmpty())
			throw new IOException("Holy Empire public roster CDN endpoint is not configured");
		return CentralRosterClient.fetch(base, http);
	}
}
