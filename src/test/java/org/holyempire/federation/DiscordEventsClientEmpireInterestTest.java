/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordEventsClientEmpireInterestTest
{
	@Test public void onlyVerifiedInterestedEmpireEventsShowGreenCheck()
	{
		Instant start = Instant.parse("2026-12-02T19:00:00Z");
		DiscordEventsClient.Event interested = new DiscordEventsClient.Event(
			"empire","", "1233333333333333333", "Interested event", "", "scheduled",
			"W465", start, start.plusSeconds(3600),
			"https://discord.com/events/736564064834289774/1233333333333333333",
			"", 0,0,0,"",45);
		DiscordEventsClient.Event notInterested = new DiscordEventsClient.Event(
			"empire","", "2333333333333333333", "Other event", "", "scheduled",
			"W465", start, start.plusSeconds(3600),
			"https://discord.com/events/736564064834289774/2333333333333333333",
			"", 0,0,0,"",20);
		DiscordEventsClient.Event chapter = new DiscordEventsClient.Event(
			"chapter", "Holy Empire", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			"Chapter event", "", "scheduled", "W465", start,
			start.plusSeconds(3600), "https://discord.com/channels/1/2/3",
			"", 0,1,0,"maybe",1);
		DiscordEventsClient.Snapshot publicFeed = new DiscordEventsClient.Snapshot(
			start.getEpochSecond(), List.of(interested, notInterested, chapter));
		DiscordEventsClient.Snapshot personal = DiscordEventsClient.withPersonalEmpireInterest(
			publicFeed, Set.of(interested.id));
		assertEquals("going", personal.events.get(0).myRsvp);
		assertEquals("", personal.events.get(1).myRsvp);
		assertEquals("maybe", personal.events.get(2).myRsvp);
		assertEquals("✓", FederationPanel.personalEventRsvpMark(personal.events.get(0).myRsvp));
		assertEquals("", FederationPanel.personalEventRsvpMark(personal.events.get(1).myRsvp));
		assertEquals(45, personal.events.get(0).interestedCount);
		assertEquals("", publicFeed.events.get(0).myRsvp);
		assertEquals("", DiscordEventsClient.withPersonalEmpireInterest(
			personal, Set.of()).events.get(0).myRsvp);
	}
}
