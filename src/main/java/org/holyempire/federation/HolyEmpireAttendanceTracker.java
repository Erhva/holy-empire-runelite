/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Local, manually controlled proximity evidence. One nearby game tick gives
 * 600 ms credit, never wall-clock time when the host is offline or hopping.
 * Only names in the downloaded, verified WOM directory can earn attendance.
 */
final class HolyEmpireAttendanceTracker
{
	static final int RADIUS_TILES = 15;
	static final long GAME_TICK_MILLIS = 600L;
	private static final int MAX_ATTENDEES = 2000;
	private static final int MAX_ROSTER_MEMBERS = 12000;
	private static final long MAX_TICKS = 144000L; // 24 hours
	private final Map<String, Row> attendees = new LinkedHashMap<>();
	private Map<String, List<String>> membership = Collections.emptyMap();
	private String host = "";
	private String title = "";
	private String eventId = "";
	private String reportId = "";
	private String scope = "";
	private String chapter = "";
	private long startedAt;
	private long endedAt;
	private long observedTicks;
	private boolean running;
	private boolean paused;

	static final class Entry
	{
		final String name;
		final String chapter;
		final List<String> chapters;
		final List<long[]> spans;
		final long ticks;
		final long firstSeen;
		final long lastSeen;

		Entry(Row row)
		{
			name = row.name;
			chapter = row.chapter;
			chapters = Collections.unmodifiableList(new ArrayList<>(row.chapters));
			List<long[]> samples = new ArrayList<>();
			for (long[] range : row.spans)
				samples.add(range.clone());
			spans = Collections.unmodifiableList(samples);
			ticks = row.ticks;
			firstSeen = row.firstSeen;
			lastSeen = row.lastSeen;
		}

		long seconds()
		{
			return ticks * GAME_TICK_MILLIS / 1000L;
		}
	}

	static final class Report
	{
		final String eventId;
		final String reportId;
		final String scope;
		final String chapter;
		final String title;
		final String host;
		final long startedAt;
		final long endedAt;
		final boolean running;
		final boolean paused;
		final List<Entry> entries;

		Report(HolyEmpireAttendanceTracker session)
		{
			eventId = session.eventId;
			reportId = session.reportId;
			scope = session.scope;
			chapter = session.chapter;
			title = session.title;
			host = session.host;
			startedAt = session.startedAt;
			endedAt = session.running ? 0 : session.endedAt;
			running = session.running;
			paused = session.running && session.paused;
			List<Entry> sorted = new ArrayList<>();
			for (Row row : session.attendees.values())
			{
				sorted.add(new Entry(row));
			}
			sorted.sort(Comparator.comparing((Entry e) -> e.chapter)
							.thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
			entries = Collections.unmodifiableList(sorted);
		}

		int members()
		{
			return entries.size();
		}

		long totalMemberSeconds()
		{
			long total = 0;
			for (Entry e : entries)
			{
				total += e.seconds();
			}
			return total;
		}
	}

	private static final class Row
	{
		final String name;
		final String chapter;
		final List<String> chapters;
		long ticks;
		final List<long[]> spans = new ArrayList<>();
		final long firstSeen;
		long lastSeen;

		Row(String name, List<String> all, long firstSeen)
		{
			this.name = name;
			chapters = new ArrayList<>(all);
			chapter = all.get(0);
			this.firstSeen = firstSeen;
		}
	}

	synchronized boolean active()
	{
		return running;
	}

	synchronized boolean paused()
	{
		return running && paused;
	}

	synchronized void pause()
	{
		if (!running || paused)
			throw new IllegalStateException("Attendance is not currently recording");
		paused = true;
	}

	synchronized void resume()
	{
		if (!running || !paused)
			throw new IllegalStateException("Attendance is not paused");
		paused = false;
	}

	synchronized boolean hasReport()
	{
		return !eventId.isEmpty();
	}

	synchronized void start(String eventId, String scope, String chapter, String title, String host,
							Map<String, List<String>> verifiedRoster, long now)
	{
		if (running)
		{
			throw new IllegalStateException("An attendance session is already running");
		}
		String account = WomDirectoryClient.normalize(host);
		if (account.isEmpty() || verifiedRoster == null || !verifiedRoster.containsKey(account) ||
			!("manual".equals(scope) || "empire".equals(scope) || "chapter".equals(scope)) ||
			eventId == null ||
			!eventId.matches("(?:manual:[0-9a-f-]{36}|[0-9a-f]{32}|[0-9]{15,22})") ||
			title == null || title.trim().isEmpty() || title.length() > 100 ||
			(chapter == null || chapter.length() > 48) || now <= 0)
		{
			throw new IllegalArgumentException(
				"A verified attendance host and valid event are required");
		}
		Map<String, List<String>> verified = new LinkedHashMap<>();
		for (Map.Entry<String, List<String>> member : verifiedRoster.entrySet())
		{
			String name = WomDirectoryClient.normalize(member.getKey());
			if (!name.matches("[a-z0-9 _-]{1,12}") || member.getValue() == null ||
				member.getValue().isEmpty())
			{
				continue;
			}
			// Preserve WOM chapter order for multi-chapter members; a member
			// appears only once in the Empire-wide total.
			Set<String> chapters = new LinkedHashSet<>(member.getValue());
			chapters.remove("");
			if (!chapters.isEmpty())
			{
				verified.put(name, new ArrayList<>(chapters));
			}
		}
		if (verified.size() > MAX_ROSTER_MEMBERS || !verified.containsKey(account))
		{
			throw new IllegalArgumentException("Complete verified WOM roster unavailable");
		}
		this.eventId = eventId;
		this.reportId = java.util.UUID.randomUUID().toString();
		this.scope = scope;
		this.chapter = chapter;
		this.title = title.trim();
		this.host = account;
		this.startedAt = now;
		this.endedAt = 0;
		this.observedTicks = 0;
		this.attendees.clear();
		this.membership = verified;
		this.running = true;
		this.paused = false;
	}

	/** Called once per actual logged-in RuneLite GameTick, with visible nearby players. */
	synchronized void observe(Set<String> nearbyNames, long now)
	{
		if (!running || paused || nearbyNames == null || observedTicks >= MAX_TICKS)
		{
			return;
		}
		observedTicks++;
		Set<String> countedThisTick = new LinkedHashSet<>();
		for (String raw : nearbyNames)
		{
			String name = WomDirectoryClient.normalize(raw);
			if (!countedThisTick.add(name))
			{
				continue;
			}
			List<String> chapters = membership.get(name);
			if (chapters == null ||
				attendees.size() >= MAX_ATTENDEES && !attendees.containsKey(name))
			{
				continue;
			}
			Row row = attendees.get(name);
			if (row == null)
			{
				row = new Row(name, chapters, now);
				attendees.put(name, row);
			}
			// Compact consecutive 600ms wall-clock observation buckets.
			// These allow overlapping observers to merge without counting
			// the same member's tick twice.
			long bucket = Math.round((double)now / GAME_TICK_MILLIS);
			if (!row.spans.isEmpty())
			{
				long[] last = row.spans.get(row.spans.size() - 1);
				if (bucket <= last[1])
					continue;
				if (bucket == last[1] + 1)
					last[1] = bucket;
				else
					row.spans.add(new long[] {bucket, bucket});
			}
			else
				row.spans.add(new long[] {bucket, bucket});
			row.ticks++;
			row.lastSeen = now;
		}
	}

	synchronized Report finish(long now)
	{
		if (!running)
		{
			throw new IllegalStateException("No attendance session is running");
		}
		endedAt = Math.max(now, startedAt);
		running = false;
		paused = false;
		membership = Collections.emptyMap();
		return new Report(this);
	}

	synchronized Report report()
	{
		return hasReport() ? new Report(this) : null;
	}

	synchronized void clear()
	{
		if (running)
		{
			throw new IllegalStateException("Stop attendance before discarding the report");
		}
		eventId = "";
		reportId = "";
		paused = false;
		attendees.clear();
		membership = Collections.emptyMap();
	}
}
