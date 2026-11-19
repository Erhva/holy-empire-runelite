/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Instant;
import java.util.*;

/** Local, user-initiated evidence snapshots; no HTTP or automatic uploads. */
final class FederationBanRegistry
{
	static final String CSV_HEADER = "chapter,rsn,captured_at_utc,source";
	static final int MAX_PER_CHAPTER = 100;

	static final class Snapshot
	{
		final String chapter;
		final String timestamp;
		final String source;
		final List<String> names;

		Snapshot(String chapter, String timestamp, String source, List<String> names)
		{
			this.chapter = chapter;
			this.timestamp = timestamp;
			this.source = source;
			this.names = Collections.unmodifiableList(new ArrayList<>(names));
		}
	}

	private final Map<String, Snapshot> snapshots = new LinkedHashMap<>();

	static String officialChapter(String raw)
	{
		for (HolyEmpireChapterCatalog.Chapter chapter : HolyEmpireChapterCatalog.ORDERED)
			if (WomDirectoryClient.normalize(chapter.name)
					.equals(WomDirectoryClient.normalize(raw)))
				return chapter.name;
		return null;
	}

	static String validName(String raw)
	{
		if (raw == null)
			return null;
		String cleaned = raw.replace('\u00a0', ' ').trim().replaceAll(" +", " ");
		return cleaned.matches("[A-Za-z0-9 _-]{1,12}") ? cleaned : null;
	}

	static Snapshot fromVisibleWidget(String chapter, List<String> rawNames, Instant capturedAt)
	{
		String canonical = officialChapter(chapter);
		if (canonical == null)
			throw new IllegalArgumentException(
				"The active clan is not a configured Holy Empire chapter.");
		if (rawNames == null)
			throw new IllegalArgumentException("Ban-list entries are unavailable.");
		Map<String, String> names = new LinkedHashMap<>();
		for (String raw : rawNames)
		{
			String name = validName(raw);
			if (name != null)
				names.putIfAbsent(WomDirectoryClient.normalize(name), name);
		}
		if (names.isEmpty())
			throw new IllegalArgumentException(
				"No banned names were detected; the previous snapshot was not changed.");
		if (names.size() > MAX_PER_CHAPTER)
			throw new IllegalArgumentException(
				"Unexpected number of ban-list entries; capture was rejected.");
		return new Snapshot(canonical, capturedAt.toString(), "LIVE_WIDGET",
							new ArrayList<>(names.values()));
	}

	synchronized void put(Snapshot snapshot)
	{
		snapshots.put(snapshot.chapter, snapshot);
	}

	/** Do not carry private captures across account changes or logout. */
	synchronized void clear()
	{
		snapshots.clear();
	}

	synchronized int total()
	{
		int sum = 0;
		for (Snapshot s : snapshots.values())
			sum += s.names.size();
		return sum;
	}

	synchronized int chapterCount()
	{
		return snapshots.size();
	}

	synchronized List<String> matches(String rsn)
	{
		String normalized = WomDirectoryClient.normalize(rsn);
		if (validName(rsn) == null)
			return Collections.emptyList();
		List<String> matches = new ArrayList<>();
		for (Snapshot s : snapshots.values())
		{
			for (String entry : s.names)
			{
				if (WomDirectoryClient.normalize(entry).equals(normalized))
				{
					matches.add(s.chapter + " (" + s.timestamp + ", " + s.source + ")");
					break;
				}
			}
		}
		return Collections.unmodifiableList(matches);
	}

	synchronized List<Snapshot> all()
	{
		return Collections.unmodifiableList(new ArrayList<>(snapshots.values()));
	}

	synchronized String toCsv()
	{
		StringBuilder out = new StringBuilder(CSV_HEADER).append('\n');
		for (HolyEmpireChapterCatalog.Chapter chapter : HolyEmpireChapterCatalog.ORDERED)
		{
			Snapshot s = snapshots.get(chapter.name);
			if (s == null)
				continue;
			for (String rsn : s.names)
				out.append(s.chapter)
					.append(',')
					.append(rsn)
					.append(',')
					.append(s.timestamp)
					.append(',')
					.append(s.source)
					.append('\n');
		}
		return out.toString();
	}

	/** Import only the documented export schema, atomically, without network access. */
	synchronized int importCsv(String csv)
	{
		if (csv == null || csv.length() > 256_000)
			throw new IllegalArgumentException("CSV exceeds the limit.");
		String[] rows = csv.split("\\r?\\n", -1);
		if (rows.length < 2 || rows.length > 1_705 || !CSV_HEADER.equals(rows[0]))
			throw new IllegalArgumentException("Expected Holy Empire ban-list CSV header.");
		Map<String, Map<String, String>> names = new LinkedHashMap<>();
		Map<String, String> times = new HashMap<>();
		int count = 0;
		for (int i = 1; i < rows.length; i++)
		{
			String line = rows[i];
			if (line.isEmpty())
				continue;
			String[] fields = line.split(",", -1);
			if (fields.length != 4)
				throw new IllegalArgumentException("Invalid CSV row " + (i + 1));
			String chapter = officialChapter(fields[0]);
			String name = validName(fields[1]);
			String timestamp = fields[2];
			if (chapter == null || name == null || timestamp.length() > 40 ||
				!(fields[3].equals("LIVE_WIDGET") || fields[3].equals("IMPORTED_FILE")))
				throw new IllegalArgumentException("Invalid ban-list entry at row " + (i + 1));
			try
			{
				Instant.parse(timestamp);
			}
			catch (RuntimeException ex)
			{
				throw new IllegalArgumentException("Invalid capture date at row " + (i + 1));
			}
			if (times.containsKey(chapter) && !times.get(chapter).equals(timestamp))
				throw new IllegalArgumentException("Mixed timestamps for " + chapter);
			times.put(chapter, timestamp);
			Map<String, String> entries =
				names.computeIfAbsent(chapter, k -> new LinkedHashMap<>());
			if (entries.putIfAbsent(WomDirectoryClient.normalize(name), name) != null)
				throw new IllegalArgumentException("Duplicate player at row " + (i + 1));
			if (entries.size() > MAX_PER_CHAPTER)
				throw new IllegalArgumentException("More than 100 bans for " + chapter);
			count++;
		}
		if (count == 0)
			throw new IllegalArgumentException("CSV contains no banned players.");
		Map<String, Snapshot> staged = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, String>> entry : names.entrySet())
		{
			String chapter = entry.getKey();
			staged.put(chapter, new Snapshot(chapter, times.get(chapter), "IMPORTED_FILE",
											 new ArrayList<>(entry.getValue().values())));
		}
		snapshots.putAll(staged);
		return count;
	}
}
