/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

public class FederationPublicBanWarningsTest
{
	private final Map<String, List<String>> roster = new HashMap<>();
	private final Set<String> hashes = new HashSet<>();

	private void prepare()
	{
		roster.put("rostered", Collections.singletonList("Holy Empire"));
		hashes.add(FederationPublicBanWarnings.hash("Unrostered"));
		// A mistakenly published hash must still be hidden for any roster member.
		hashes.add(FederationPublicBanWarnings.hash("Rostered"));
	}

	@Test public void regularMembersWarnOnlyNonRosteredNames()
	{
		prepare();
		assertTrue(FederationPublicBanWarnings.matches(" unROSTERED ", hashes, roster));
		assertFalse(FederationPublicBanWarnings.matches("Rostered", hashes, roster));
		assertFalse(FederationPublicBanWarnings.matches("Unknown", hashes, roster));
		assertFalse(FederationPublicBanWarnings.matches("Invalid#Name", hashes, roster));
		String message = FederationPublicBanWarnings.publicWarningText("Unrostered");
		assertTrue(message.contains("BAN-LIST WARNING"));
		assertFalse("Public notice must not expose chapter provenance",
					message.contains("Holy Chaos"));
	}

	@Test public void nonKeyMemberRecruitPopupUsesExactRequestedWording()
	{
		assertEquals("HOLY EMPIRE BAN-LIST WARNING\n\n"
						 + "Unrostered appears on a chapter ban-list\n\n"
						 + "Consult an admin before recruiting",
					 FederationPublicBanWarnings.recruitPopupWarningText("Unrostered"));
		assertNull(FederationPublicBanWarnings.recruitPopupWarningText("Invalid#Name"));
		assertFalse("Non-key warning cannot disclose specific chapter",
					FederationPublicBanWarnings.recruitPopupWarningText("Unrostered")
						.contains("Holy Chaos"));
	}

	@Test public void keyHolderCanStillReceivePrivateWarningsForRosteredTarget()
	{
		prepare();
		Map<String, FederationBanDirectoryClient.Chapter> chapter = new LinkedHashMap<>();
		chapter.put("Holy Chaos",
					new FederationBanDirectoryClient.Chapter("Holy Chaos", 1795000000, 1));
		FederationBanDirectoryClient.Snapshot bans = new FederationBanDirectoryClient.Snapshot(
			Collections.singletonList(new FederationBanDirectoryClient.Entry(
				"Holy Chaos", "Rostered", "tracked", 1795000000)),
			chapter);
		List<String> matches = FederationRecruitmentGuard.matchingChapters(bans, "rostered");
		assertEquals(Collections.singletonList("Holy Chaos"), matches);
		assertTrue(
			FederationPublicBanWarnings.privateVisible(true, true, "Rostered", matches, roster));
		assertFalse(
			FederationPublicBanWarnings.privateVisible(false, false, "Rostered", matches, roster));
	}

	@Test public void womMemberWithoutNativeClanStillGetsRecruitmentWarnings()
	{
		prepare();
		FederationSession session = new FederationSession();
		session.observe("rostered", "");
		long generation = session.generation();
		WomDirectoryClient.Snapshot data = new WomDirectoryClient.Snapshot(
			roster, Collections.emptyMap(), System.currentTimeMillis(), hashes, true);
		assertTrue(session.publish("rostered", generation, data));
		assertTrue("WOM roster must grant public warning access", session.isEligible());
		assertEquals("", session.nativeChapter());
		assertTrue(session.publicBanWarningAvailable("rostered"));
		assertTrue(session.publicBanWarningMatch("rostered", "Unrostered"));
		assertFalse(session.publicBanWarningMatch("rostered", "Rostered"));
		assertEquals("Unrostered",
					 FederationRecruitmentGuard.recruitmentTarget(
						 "Recruit", null, "<col=ffffff>Unrostered</col> (level-126)"));
		assertTrue(FederationPublicBanWarnings.publicWarningText("Unrostered")
					   .contains("BAN-LIST WARNING"));
		session.observe("outsider", "");
		assertFalse(session.publicBanWarningMatch("outsider", "Unrostered"));
	}

	@Test public void sessionClearsNonMemberWarningsOnAccountSwitch()
	{
		prepare();
		FederationSession session = new FederationSession();
		session.observe("Rostered", "Holy Empire");
		long generation = session.generation();
		WomDirectoryClient.Snapshot data = new WomDirectoryClient.Snapshot(
			roster, Collections.emptyMap(), System.currentTimeMillis(), hashes, true);
		assertTrue(session.publish("rostered", generation, data));
		assertTrue(session.publicBanWarningMatch("Rostered", "Unrostered"));
		assertFalse(session.publicBanWarningMatch("Rostered", "Rostered"));
		session.observe("New Account", "Holy Empire");
		assertFalse(session.publicBanWarningMatch("Rostered", "Unrostered"));
	}
}
