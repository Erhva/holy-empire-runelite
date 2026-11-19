/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WomDirectoryClientTest
{
	private static void add(int group, String name, String rank, Map<String, Set<String>> roster)
		throws IOException
	{
		String role = rank == null ? "null" : "\"" + rank + "\"";
		String body = "{\"id\":" + group + ",\"memberships\":[{\"player\":{\"displayName\":\"" +
					  name + "\"},\"role\":" + role + "}]}";
		int index = group == 4586 ? 0 : 1;
		WomDirectoryClient.mergeGroup(new JsonParser().parse(body).getAsJsonObject(), index,
									  roster);
	}

	@Test
	public void bronzeRosterMemberQualifies() throws Exception
	{
		Map<String, Set<String>> roster = new HashMap<>();
		add(4586, "Bronze User", "bronze", roster);
		assertTrue(roster.containsKey("bronze user"));
		assertTrue(roster.containsKey("bronze user"));
	}

	@Test
	public void memberInMultipleGroupsHasBothLabels() throws Exception
	{
		Map<String, Set<String>> roster = new HashMap<>();
		add(4586, "Test User", "bronze", roster);
		add(26050, "Test User", "chaos", roster);
		assertTrue(roster.containsKey("test user"));
		assertEquals(new TreeSet<>(Arrays.asList("Holy Empire", "Holy Chaos")),
					 roster.get("test user"));
	}

	@Test
	public void missingRankStillCountsAsMember() throws Exception
	{
		Map<String, Set<String>> roster = new HashMap<>();
		add(4586, "Test User", null, roster);
		assertTrue(roster.containsKey("test user"));
	}

	@Test(expected = IOException.class)
	public void mismatchedGroupRejected() throws Exception
	{
		WomDirectoryClient.mergeGroup(
			new JsonParser().parse("{\"id\":26050,\"memberships\":[]}").getAsJsonObject(), 0,
			new HashMap<>());
	}

	@Test
	public void normalizesRuneScapeNonbreakingSpaces()
	{
		assertEquals("test user", WomDirectoryClient.normalize(" Test\u00a0User "));
	}
}
