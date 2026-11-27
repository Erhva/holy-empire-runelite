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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side event attendance view. The saved report is a read-only baseline;
 * the active tracker uploads only NEW game ticks. Never re-upload prior spans.
 */
final class HolyEmpireAttendanceCumulative
{
	private HolyEmpireAttendanceCumulative() {}

	static final class Member
	{
		final String name;
		final String chapter;
		final long ticks;
		final long lastSeenSeconds;

		Member(String name, String chapter, long ticks, long lastSeenSeconds)
		{
			this.name=name;
			this.chapter=chapter;
			this.ticks=ticks;
			this.lastSeenSeconds=lastSeenSeconds;
		}

		long seconds()
		{
			return ticks * HolyEmpireAttendanceTracker.GAME_TICK_MILLIS / 1000L;
		}

		boolean meets(int minutes)
		{
			return ticks >= (long)minutes * 100L;
		}
	}

	static final class Previous
	{
		final String eventId;
		final int number;
		final int sourceSessions;
		final Map<String, Member> members;

		Previous(String eventId, int number, int sourceSessions,
			Map<String, Member> members)
		{
			this.eventId=eventId;
			this.number=number;
			this.sourceSessions=sourceSessions;
			this.members=java.util.Collections.unmodifiableMap(members);
		}
	}

	static Previous fromServer(JsonObject report, int number)
	{
		if (report == null || !report.has("event_id") || number <= 0)
			throw new IllegalArgumentException("Invalid stored event report");
		String event=report.get("event_id").getAsString();
		JsonArray entries=report.getAsJsonArray("attendees");
		if (entries == null || entries.size() > 2000)
			throw new IllegalArgumentException("Invalid event member list");
		Map<String, Member> seen=new LinkedHashMap<>();
		for (JsonElement entry : entries)
		{
			JsonObject row=entry.getAsJsonObject();
			String name=WomDirectoryClient.normalize(row.get("rsn").getAsString());
			String chapter=row.get("chapter").getAsString();
			long ticks=row.get("ticks").getAsLong();
			long lastSeen=row.get("last_seen").getAsLong();
			if (!name.matches("[a-z0-9 _-]{1,12}") ||
				chapter.length() > 64 || chapter.isEmpty() ||
				ticks < 1 || ticks > 144000 || lastSeen <= 0 ||
				seen.putIfAbsent(name,new Member(name,chapter,ticks,lastSeen)) != null)
				throw new IllegalArgumentException("Invalid stored attendance member");
		}
		int sessions=report.has("source_sessions")
			? report.get("source_sessions").getAsInt() : 1;
		if (sessions < 1)
			throw new IllegalArgumentException("Invalid source session count");
		return new Previous(event,number,sessions,seen);
	}

	static List<Member> forDisplay(Previous previous, HolyEmpireAttendanceTracker.Report local)
	{
		if (local == null)
			return java.util.Collections.emptyList();
		Map<String, Member> totals=new LinkedHashMap<>();
		if (previous != null && previous.eventId.equals(local.eventId) &&
			!"manual".equals(local.scope))
			totals.putAll(previous.members);
		for (HolyEmpireAttendanceTracker.Entry current : local.entries)
		{
			String key=WomDirectoryClient.normalize(current.name);
			Member prior=totals.get(key);
			long ticks=current.ticks;
			if (prior != null)
			{
				// The consolidated v1 baseline has totals but no exact
				// game-tick spans. Sum only disjoint sequential sessions;
				// otherwise conservatively avoid double-counting. The
				// server performs exact interval union on archive.
				if (current.firstSeen / 1000L > prior.lastSeenSeconds)
					ticks=Math.min(144000L,prior.ticks+current.ticks);
				else
					ticks=Math.max(prior.ticks,current.ticks);
			}
			totals.put(key,new Member(current.name,current.chapter,ticks,
				Math.max(prior == null ? 0 : prior.lastSeenSeconds,
					current.lastSeen / 1000L)));
		}
		List<Member> sorted=new ArrayList<>(totals.values());
		sorted.sort(Comparator.comparing((Member row) -> row.chapter,
				String.CASE_INSENSITIVE_ORDER)
			.thenComparing(row -> row.name,String.CASE_INSENSITIVE_ORDER));
		return sorted;
	}
}