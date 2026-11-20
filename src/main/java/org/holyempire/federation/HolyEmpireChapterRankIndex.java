/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indexed rank lookup avoids a linear roster scan for every global search result. */
final class HolyEmpireChapterRankIndex
{
	private HolyEmpireChapterRankIndex()
	{
	}

	static Map<String, Map<String, WomDirectoryClient.ChapterMember>>
	build(Map<String, List<WomDirectoryClient.ChapterMember>> chapters)
	{
		Map<String, Map<String, WomDirectoryClient.ChapterMember>> indexed = new HashMap<>();
		for (Map.Entry<String, List<WomDirectoryClient.ChapterMember>> chapter :
			 chapters.entrySet())
		{
			Map<String, WomDirectoryClient.ChapterMember> names = new LinkedHashMap<>();
			for (WomDirectoryClient.ChapterMember member : chapter.getValue())
			{
				names.putIfAbsent(WomDirectoryClient.normalize(member.name), member);
			}
			indexed.put(chapter.getKey(), Collections.unmodifiableMap(names));
		}
		return Collections.unmodifiableMap(indexed);
	}

	static WomDirectoryClient.ChapterMember
	find(Map<String, Map<String, WomDirectoryClient.ChapterMember>> index, String chapter,
		 String name)
	{
		return index.getOrDefault(chapter, Collections.emptyMap())
			.get(WomDirectoryClient.normalize(name));
	}
}
