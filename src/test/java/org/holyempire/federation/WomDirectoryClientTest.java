/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

	@Test public void bronzeRosterMemberQualifies() throws Exception
	{
		Map<String, Set<String>> roster = new HashMap<>();
		add(4586, "Bronze User", "bronze", roster);
		assertTrue(roster.containsKey("bronze user"));
		assertTrue(roster.containsKey("bronze user"));
	}

	@Test public void memberInMultipleGroupsHasBothLabels() throws Exception
	{
		Map<String, Set<String>> roster = new HashMap<>();
		add(4586, "Test User", "bronze", roster);
		add(26050, "Test User", "chaos", roster);
		assertTrue(roster.containsKey("test user"));
		assertEquals(new TreeSet<>(Arrays.asList("Holy Empire", "Holy Chaos")),
					 roster.get("test user"));
	}

	@Test public void missingRankStillCountsAsMember() throws Exception
	{
		Map<String, Set<String>> roster = new HashMap<>();
		add(4586, "Test User", null, roster);
		assertTrue(roster.containsKey("test user"));
	}

	@Test(expected = IOException.class) public void mismatchedGroupRejected() throws Exception
	{
		WomDirectoryClient.mergeGroup(
			new JsonParser().parse("{\"id\":26050,\"memberships\":[]}").getAsJsonObject(), 0,
			new HashMap<>());
	}

	@Test public void normalizesRuneScapeNonbreakingSpaces()
	{
		assertEquals("test user", WomDirectoryClient.normalize(" Test\u00a0User "));
	}
	@Test public void normalizesRuneScapeUnderscores()
	{
		assertEquals("my sp00n", WomDirectoryClient.normalize("MY_SP00N"));
		assertEquals("my sp00n", WomDirectoryClient.normalize(" my__sp00n "));
		assertEquals("mysp00n", WomDirectoryClient.normalize("mysp00n"));
	}
	@Test public void jagexSeparatorsAndDistinctIdentities() throws Exception
	{
		assertEquals("my sp00n", WomDirectoryClient.normalize("MY-SP00N"));
		assertEquals("my sp00n", WomDirectoryClient.normalize("MY\u00a0SP00N"));
		assertNotEquals("my sp00n", WomDirectoryClient.normalize("my spOOn"));
		assertNotEquals("my sp00n", WomDirectoryClient.normalize("mysp00n"));
		assertNotEquals("my sp00n", WomDirectoryClient.normalize("my sp00n\u200b"));
		assertNotEquals("alpha", WomDirectoryClient.normalize("alph\u00e9a"));
		Map<String, Set<String>> roster = new HashMap<>();
		add(4586, "My-Sp00n", "bronze", roster);
		assertTrue(roster.containsKey("my sp00n"));
	}

	@Test(expected = IOException.class) public void duplicateCanonicalNamesRejected() throws Exception
	{
		String json = "{\"id\":4586,\"memberships\":["
			+ "{\"player\":{\"displayName\":\"My-Sp00n\"}},"
			+ "{\"player\":{\"displayName\":\"My_Sp00n\"}}]}";
		WomDirectoryClient.mergeGroup(new JsonParser().parse(json).getAsJsonObject(), 0,
			new HashMap<>());
	}
}
