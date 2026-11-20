/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireChapterRankIndexTest
{
	@Test public void rankLookupRetainsChapterAndDuplicatePrecedence()
	{
		Map<String, List<WomDirectoryClient.ChapterMember>> chapters = new LinkedHashMap<>();
		WomDirectoryClient.ChapterMember owner =
			new WomDirectoryClient.ChapterMember("Holy Hero", "owner");
		WomDirectoryClient.ChapterMember duplicate =
			new WomDirectoryClient.ChapterMember("holy hero", "recruit");
		WomDirectoryClient.ChapterMember deputy =
			new WomDirectoryClient.ChapterMember("Holy Hero", "deputy_owner");
		chapters.put("Holy Empire", Arrays.asList(owner, duplicate));
		chapters.put("Holy Chaos", Collections.singletonList(deputy));
		Map<String, Map<String, WomDirectoryClient.ChapterMember>> index =
			HolyEmpireChapterRankIndex.build(chapters);
		assertSame(owner, HolyEmpireChapterRankIndex.find(index, "Holy Empire", "HOLY  HERO"));
		assertSame(deputy, HolyEmpireChapterRankIndex.find(index, "Holy Chaos", "holy hero"));
		assertNull(HolyEmpireChapterRankIndex.find(index, "Holy Guard", "Holy Hero"));
		assertNull(HolyEmpireChapterRankIndex.find(index, "Holy Empire", "Not There"));
		try
		{
			index.clear();
			fail("Index should be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
		}
	}

	@Test public void largeRosterUsesDirectIndexedMemberLookup()
	{
		Map<String, List<WomDirectoryClient.ChapterMember>> chapters = new LinkedHashMap<>();
		List<WomDirectoryClient.ChapterMember> members = new java.util.ArrayList<>();
		for (int i = 0; i < 1800; i++)
		{
			members.add(new WomDirectoryClient.ChapterMember("user " + i, "recruit"));
		}
		chapters.put("Holy Chaos", members);
		Map<String, Map<String, WomDirectoryClient.ChapterMember>> index =
			HolyEmpireChapterRankIndex.build(chapters);
		assertEquals(1800, index.get("Holy Chaos").size());
		for (int i = 0; i < 1800; i++)
		{
			assertEquals("user " + i,
						 HolyEmpireChapterRankIndex.find(index, "Holy Chaos", "USER  " + i).name);
		}
	}
}
