/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Instant;
import org.junit.Assert;
import org.junit.Test;

/** OAuth bearer refresh must beat 10-minute privileged session expiry. */
public class HolyEmpireDiscordRenewalTest
{
	private static ChapterEventsClient.MemberSession officer(String token, String chapter,
															 long validUntil, boolean admin,
															 boolean emperor, String role)
	{
		return new ChapterEventsClient.MemberSession(token, chapter, validUntil, admin, emperor,
													 role);
	}

	@Test public void matchingHighLordRenewalRetainsCapabilityScope()
	{
		long exp = Instant.now().getEpochSecond() + 600;
		ChapterEventsClient.MemberSession old =
			officer("a".repeat(40), "Holy Chaos", exp, true, false, "High Lord");
		ChapterEventsClient.MemberSession fresh =
			officer("b".repeat(40), "Holy Chaos", exp + 600, true, false, "High Lord");
		Assert.assertTrue(FederationPlugin.sameRenewedDiscordAuthority(old, fresh));
		Assert.assertTrue(FederationPlugin.currentCommunicationsRequest(fresh, "Holy Chaos", fresh,
																		"Holy Chaos"));
		Assert.assertFalse(
			"Old bearer request must not overwrite renewed authority",
			FederationPlugin.currentCommunicationsRequest(old, "Holy Chaos", fresh, "Holy Chaos"));
		Assert.assertFalse("Cross-chapter request is stale",
						   FederationPlugin.currentCommunicationsRequest(fresh, "Holy Chaos", fresh,
																		 "Holy Kingdom"));
	}

	@Test public void renewalRevokesIfChapterOrRoleOrElevationChanges()
	{
		long exp = Instant.now().getEpochSecond() + 600;
		ChapterEventsClient.MemberSession old =
			officer("a".repeat(40), "Holy Chaos", exp, true, false, "High Lord");
		Assert.assertFalse(FederationPlugin.sameRenewedDiscordAuthority(
			old, officer("b".repeat(40), "Holy Kingdom", exp, true, false, "High Lord")));
		Assert.assertFalse(FederationPlugin.sameRenewedDiscordAuthority(
			old, officer("b".repeat(40), "Holy Chaos", exp, true, false, "Primarch")));
		Assert.assertFalse(FederationPlugin.sameRenewedDiscordAuthority(
			old, officer("b".repeat(40), "Holy Chaos", exp, false, false, "High Lord")));
		Assert.assertFalse(FederationPlugin.sameRenewedDiscordAuthority(
			old, officer("b".repeat(40), "Holy Chaos", exp, true, true, "High Lord")));
		Assert.assertFalse(FederationPlugin.sameRenewedDiscordAuthority(null, old));
	}

	@Test public void expiredBearerCannotTransferItsOldPermissions()
	{
		long exp = Instant.now().getEpochSecond();
		ChapterEventsClient.MemberSession expired =
			officer("a".repeat(40), "Holy Chaos", exp, true, false, "High Lord");
		ChapterEventsClient.MemberSession renewed =
			officer("b".repeat(40), "Holy Chaos", exp + 600, true, false, "High Lord");
		Assert.assertFalse(FederationPlugin.sameRenewedDiscordAuthority(expired, renewed));
	}

	@Test public void privilegedDiscordSessionSilentlyRenewsWellBeforeExpiry()
	{
		long now = Instant.now().getEpochSecond();
		ChapterEventsClient.MemberSession officer = new ChapterEventsClient.MemberSession(
			"a".repeat(40), "Holy Chaos", now + 600, true, false, "Deputy Owner");
		long delay = FederationPlugin.discordRenewalDelay(officer);
		Assert.assertTrue("Elevated verification must rotate before ten minutes",
						  delay >= 15000 && delay <= 240000);
	}

	@Test public void ordinaryMemberRemembersVerifiedDiscordWithoutPrompt()
	{
		long now = Instant.now().getEpochSecond();
		ChapterEventsClient.MemberSession member = new ChapterEventsClient.MemberSession(
			"a".repeat(40), "Holy Chaos", now + 3600, false, false, "");
		long delay = FederationPlugin.discordRenewalDelay(member);
		Assert.assertTrue(delay > 240000 && delay <= 840000);
	}

	@Test public void nearlyExpiredBearerRetriesImmediately()
	{
		long now = Instant.now().getEpochSecond();
		ChapterEventsClient.MemberSession officer = new ChapterEventsClient.MemberSession(
			"a".repeat(40), "Holy Chaos", now + 40, true, false, "Chapter Admin");
		long delay = FederationPlugin.discordRenewalDelay(officer);
		Assert.assertTrue(delay >= 15000 && delay <= 20000);
	}
}
