/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class HolyEmpireRosterIndex
{
	private HolyEmpireRosterIndex()
	{
	}

	static List<String> search(Map<String, List<String>> roster, String query)
	{
		String normalized = WomDirectoryClient.normalize(query);
		List<String> found = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : roster.entrySet())
		{
			if (normalized.isEmpty() ||
				WomDirectoryClient.normalize(e.getKey()).contains(normalized))
			{
				found.add(e.getKey());
			}
		}
		found.sort(String.CASE_INSENSITIVE_ORDER);
		return found;
	}

	static List<WomDirectoryClient.ChapterMember>
	searchChapter(List<WomDirectoryClient.ChapterMember> ranked, String query)
	{
		String needle = WomDirectoryClient.normalize(query);
		if (needle.isEmpty())
		{
			return new ArrayList<>(ranked);
		}
		List<WomDirectoryClient.ChapterMember> found = new ArrayList<>();
		for (WomDirectoryClient.ChapterMember member : ranked)
		{
			if (WomDirectoryClient.normalize(member.name).contains(needle) ||
				WomDirectoryClient.normalize(member.readableRank()).contains(needle))
			{
				found.add(member);
			}
		}
		// Preserve WOM rank order when searching.
		return found;
	}

	static List<String> inClan(Map<String, List<String>> roster, String clan)
	{
		List<String> found = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : roster.entrySet())
		{
			if (e.getValue().contains(clan))
			{
				found.add(e.getKey());
			}
		}
		found.sort(String.CASE_INSENSITIVE_ORDER);
		return found;
	}
}
