/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WomChapterRosterTest
{
	private JsonObject member(String name, String rank)
	{
		JsonObject m = new JsonObject();
		JsonObject player = new JsonObject();
		player.addProperty("displayName", name);
		m.add("player", player);
		if (rank != null)
		{
			m.addProperty("role", rank);
		}
		return m;
	}

	@Test
	public void ownerFirstAndChapterSpecificRankOrder()
	{
		JsonObject group = new JsonObject();
		group.addProperty("id", 4586);
		JsonArray members = new JsonArray();
		members.add(member("Low Member", "bronze"));
		members.add(member("Staff Member", "captain"));
		members.add(member("Owner Member", "owner"));
		members.add(member("Top Member", "deputy_owner"));
		group.add("memberships", members);
		Map<String, Set<String>> merged = new HashMap<>();
		Map<String, List<WomDirectoryClient.ChapterMember>> chapters = new HashMap<>();
		try
		{
			WomDirectoryClient.mergeGroup(group, 0, merged, chapters);
		}
		catch (java.io.IOException e)
		{
			throw new AssertionError(e);
		}
		List<WomDirectoryClient.ChapterMember> ordered = chapters.get("Holy Empire");
		assertEquals(4, ordered.size());
		assertEquals("Owner Member", ordered.get(0).name);
		assertEquals("Staff Member", ordered.get(2).name);
		assertEquals("Deputy Owner", ordered.get(3).readableRank());
		assertEquals("Bronze", ordered.get(1).readableRank());
	}

	@Test
	public void nullRankIsStillVisible()
	{
		assertEquals("Unranked",
					 new WomDirectoryClient.ChapterMember("Someone", "unranked").readableRank());
	}

	@Test
	public void chapterSearchPreservesRankOrder()
	{
		List<WomDirectoryClient.ChapterMember> members =
			Arrays.asList(new WomDirectoryClient.ChapterMember("Owner", "owner"),
						  new WomDirectoryClient.ChapterMember("Deputy One", "deputy_owner"),
						  new WomDirectoryClient.ChapterMember("Deputy Two", "deputy_owner"),
						  new WomDirectoryClient.ChapterMember("Bronze User", "bronze"));
		List<WomDirectoryClient.ChapterMember> found =
			HolyEmpireRosterIndex.searchChapter(members, "deputy");
		assertEquals(2, found.size());
		assertEquals("Deputy One", found.get(0).name);
		assertEquals("Deputy Two", found.get(1).name);
		assertEquals(1, HolyEmpireRosterIndex.searchChapter(members, "bronze user").size());
	}

	@Test
	public void chatsStayAlignedWithAll16Groups()
	{
		assertEquals(WomDirectoryClient.GROUPS.length, WomDirectoryClient.CLANS.length);
		assertEquals(WomDirectoryClient.GROUPS.length, WomDirectoryClient.CLAN_CHATS.length);
		assertEquals("holy kingdom", WomDirectoryClient.CLAN_CHATS[15]);
	}
}
