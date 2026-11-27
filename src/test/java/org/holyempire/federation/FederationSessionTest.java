/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationSessionTest
{
	private WomDirectoryClient.Snapshot sample(String privileged)
	{
		Map<String, List<String>> map = new HashMap<>();
		map.put("alpha", Collections.singletonList("Holy Empire"));
		map.put("beta", Collections.singletonList("Holy Chaos"));
		return new WomDirectoryClient.Snapshot(Collections.unmodifiableMap(map));
	}

	@Test public void chapterRankDataFollowsSessionEligibility()
	{
		Map<String, List<String>> roster = new HashMap<>();
		roster.put("alpha", Collections.singletonList("Holy Empire"));
		Map<String, List<WomDirectoryClient.ChapterMember>> chapters = new HashMap<>();
		chapters.put("Holy Empire", Collections.singletonList(
										new WomDirectoryClient.ChapterMember("Alpha", "owner")));
		FederationSession state = new FederationSession();
		state.observe("alpha", "Holy Empire");
		assertTrue(state.publish("alpha", state.generation(),
								 new WomDirectoryClient.Snapshot(roster, chapters)));
		assertEquals("owner", state.chaptersFor("alpha").get("Holy Empire").get(0).rank);
		state.observe("outsider", "Holy Empire");
		assertTrue(state.chaptersFor("alpha").isEmpty());
		assertTrue(state.chaptersFor("outsider").isEmpty());
	}

	@Test public void loginAndLogoutImmediatelyClear()
	{
		FederationSession state = new FederationSession();
		assertTrue(state.observe("Alpha", "Holy Empire"));
		assertTrue(state.publish("alpha", state.generation(), sample("alpha")));
		assertTrue(state.isEligible());
		assertEquals(2, state.rosterFor("alpha").size());
		assertTrue(state.observe("", ""));
		assertTrue(state.rosterFor("").isEmpty());
		assertFalse(state.isEligible());
	}

	@Test public void anyRosterMemberCanSeeRoster()
	{
		FederationSession state = new FederationSession();
		state.observe("beta", "Holy Chaos");
		assertTrue(state.publish("beta", state.generation(), sample("alpha")));
		assertTrue(state.isEligible());
		assertEquals(2, state.rosterFor("beta").size());
	}

	@Test public void unknownAccountCannotSeeRoster()
	{
		FederationSession state = new FederationSession();
		state.observe("outsider", "Holy Empire");
		assertTrue(state.publish("outsider", state.generation(), sample("alpha")));
		assertFalse(state.isEligible());
		assertTrue(state.rosterFor("outsider").isEmpty());
	}

	@Test public void oldResponseCannotOverwriteLaterLogin()
	{
		FederationSession state = new FederationSession();
		state.observe("alpha", "Holy Empire");
		long firstGeneration = state.generation();
		state.observe("beta", "Holy Chaos");
		long secondGeneration = state.generation();
		assertFalse(state.publish("alpha", firstGeneration, sample("alpha")));
		assertFalse(state.invalidate("alpha", firstGeneration));
		assertTrue(state.publish("beta", secondGeneration, sample("beta")));
		assertTrue(state.rosterFor("alpha").isEmpty());
		assertEquals(2, state.rosterFor("beta").size());
	}

	@Test public void returningToSameAccountRejectsOldSnapshot()
	{
		FederationSession state = new FederationSession();
		state.observe("alpha", "Holy Empire");
		long old = state.generation();
		state.observe("beta", "Holy Chaos");
		state.observe("alpha", "Holy Empire");
		assertFalse(state.publish("alpha", old, sample("alpha")));
		assertTrue(state.rosterFor("alpha").isEmpty());
	}

	@Test public void failedRefreshDisablesPreviouslyEligibleAccount()
	{
		FederationSession state = new FederationSession();
		state.observe("alpha", "Holy Empire");
		long generation = state.generation();
		state.publish("alpha", generation, sample("alpha"));
		assertTrue(state.invalidate("alpha", generation));
		assertTrue(state.rosterFor("alpha").isEmpty());
	}

	@Test public void womRosterMembershipGrantsAccessRegardlessOfNativeChapter()
	{
		FederationSession state = new FederationSession();
		state.observe("alpha", "Holy Chaos");
		assertTrue(state.publish("alpha", state.generation(), sample("alpha")));
		assertTrue(state.isEligible());
		assertEquals(2, state.rosterFor("alpha").size());
		state.observe("alpha", "Holy Empire");
		assertTrue(state.publish("alpha", state.generation(), sample("alpha")));
		assertTrue(state.isEligible());
	}

	@Test public void nativeClanDisappearanceRetainsWomBasedMembershipAfterRefresh()
	{
		FederationSession state = new FederationSession();
		state.observe("alpha", "Holy Empire");
		long before = state.generation();
		assertTrue(state.publish("alpha", before, sample("alpha")));
		assertTrue(state.isEligible());
		state.observe("alpha", "");
		assertFalse("Chapter changes invalidate old session evidence", state.isEligible());
		assertFalse(state.publish("alpha", before, sample("alpha")));
		assertTrue(state.publish("alpha", state.generation(), sample("alpha")));
		assertTrue("WOM membership is sufficient without native clan access", state.isEligible());
	}

	@Test public void nativeChapterSwitchRejectsInFlightResponseEvenForSameAccount()
	{
		FederationSession state = new FederationSession();
		state.observe("alpha", "Holy Empire");
		long old = state.generation();
		state.observe("alpha", "Holy Chaos");
		assertFalse(state.publish("alpha", old, sample("alpha")));
		assertTrue(state.publish("alpha", state.generation(), sample("alpha")));
		assertTrue(state.isEligible());
	}
	@Test public void underscoreNamesMatchRosterAndRank()
	{
		Map<String, List<String>> roster = new HashMap<>();
		roster.put("my sp00n", Collections.singletonList("Sacred Order"));
		Map<String, List<WomDirectoryClient.ChapterMember>> chapters = new HashMap<>();
		chapters.put("Sacred Order", Collections.singletonList(
			new WomDirectoryClient.ChapterMember("my sp00n", "sheriff")));
		FederationSession state = new FederationSession();
		state.observe("MY_SP00N", "");
		assertTrue(state.publish("my sp00n", state.generation(),
			new WomDirectoryClient.Snapshot(roster, chapters)));
		assertTrue(state.isEligible());
		assertEquals(Collections.singletonList("Sacred Order"),
			state.rosterFor("MY_SP00N").get(WomDirectoryClient.normalize("MY_SP00N")));
		assertEquals("sheriff", state.rankFor("MY_SP00N").rank);
		assertFalse(state.observe("my sp00n", ""));
		assertTrue(state.rosterFor("MY_SP00N").containsKey("my sp00n"));
		assertNull(state.rankFor("MYSP00N"));
	}
}
