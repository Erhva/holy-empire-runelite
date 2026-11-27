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

public class FederationRecruitmentGuardTest
{
	private FederationBanDirectoryClient.Snapshot snapshot()
	{
		Map<String, FederationBanDirectoryClient.Chapter> chapters = new LinkedHashMap<>();
		chapters.put("Holy Chaos",
					 new FederationBanDirectoryClient.Chapter("Holy Chaos", 1795000000, 1));
		chapters.put("Holy Empire",
					 new FederationBanDirectoryClient.Chapter("Holy Empire", 1795000001, 2));
		return new FederationBanDirectoryClient.Snapshot(
			Arrays.asList(new FederationBanDirectoryClient.Entry("Holy Chaos", "Banned One",
																 "tracked", 1795000000),
						  new FederationBanDirectoryClient.Entry("Holy Empire", "Banned One",
																 "unresolved", 1795000001),
						  new FederationBanDirectoryClient.Entry("Holy Chaos", "Other Name",
																 "tracked", 1795000000)),
			chapters);
	}

	@Test public void matchesOnlyNativeRecruitMenuOption()
	{
		assertEquals("Banned One", FederationRecruitmentGuard.recruitmentTarget(
									   "Recruit", "Banned One", "Other Name"));
		assertEquals("Banned One",
					 FederationRecruitmentGuard.recruitmentTarget(
						 "<col=fff>Recruit</col>", null, "<col=fff>Banned One</col> (level-126)"));
		assertEquals("Banned One", FederationRecruitmentGuard.recruitmentTarget(
									   "Recruit", null, "Banned One [Holy Chaos]"));
		for (String other : Arrays.asList("Invite", "Invite to party", "Trade with", "Follow",
										  "Recruit to party", "Accept", "Kick", "Examine", "Apply"))
		{
			assertNull(
				"Never intercept unrelated menu action: " + other,
				FederationRecruitmentGuard.recruitmentTarget(other, "Banned One", "Banned One"));
		}
	}

	@Test public void exactCaseInsensitiveNameAndAllMatchedChapters()
	{
		List<String> matches =
			FederationRecruitmentGuard.matchingChapters(snapshot(), "banned   one");
		assertEquals(Arrays.asList("Holy Chaos", "Holy Empire"), matches);
		assertTrue(FederationRecruitmentGuard.matchingChapters(snapshot(), "Banned").isEmpty());
		assertTrue(FederationRecruitmentGuard.matchingChapters(snapshot(), "Not Banned").isEmpty());
		assertTrue(FederationRecruitmentGuard.matchingChapters(null, "Banned One").isEmpty());
	}

	@Test public void keyMemberRecruitPopupUsesExactRequestedWording()
	{
		assertEquals("HOLY EMPIRE BAN-LIST WARNING\n\n"
						 + "Banned One is banned in:\nHoly Chaos\nHoly Empire\n\n"
						 + "Confirm their ban status before recruiting.",
					 FederationRecruitmentGuard.recruitPopupWarningText(
						 "Banned One",
						 FederationRecruitmentGuard.matchingChapters(snapshot(), "Banned One")));
		assertNull(FederationRecruitmentGuard.recruitPopupWarningText("Not Banned",
																	  Collections.emptyList()));
		assertNull(FederationRecruitmentGuard.recruitPopupWarningText(
			"Invalid#Name", Collections.singletonList("Holy Chaos")));
	}

	@Test public void warnedRecruitmentDisplaysChaptersOnlyToApprovedOfficer()
	{
		String matching = FederationRecruitmentGuard.warningText(
			"Banned One", FederationRecruitmentGuard.matchingChapters(snapshot(), "Banned One"),
			true);
		assertTrue(matching.contains("BAN-LIST WARNING"));
		assertTrue(matching.contains("Holy Chaos"));
		assertTrue(matching.contains("Holy Empire"));
		assertNull(
			"A verified non-match causes no false accusation",
			FederationRecruitmentGuard.warningText("Not Banned", Collections.emptyList(), true));
		String hidden =
			FederationRecruitmentGuard.warningText("Banned One", Collections.emptyList(), false);
		assertTrue(hidden.contains("could not be verified"));
		assertFalse(hidden.contains("Holy Chaos"));
		assertFalse(hidden.contains("is banned"));
		assertNull(FederationRecruitmentGuard.recruitmentTarget("Recruit", "Invalid#Name",
																"Invalid#Name"));
	}
}
