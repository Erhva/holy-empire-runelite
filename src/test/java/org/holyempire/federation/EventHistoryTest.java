/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 */
package org.holyempire.federation;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventHistoryTest
{
	private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

	private static DiscordEventsClient.Event event(String id, int daysAgo, Integer durationHours)
	{
		Instant start = NOW.minusSeconds(daysAgo * 86400L);
		Instant end = durationHours == null ? null : start.plusSeconds(durationHours * 3600L);
		return new DiscordEventsClient.Event("chapter", "Holy Chaos", id, "Test", "", "scheduled",
											 "", start, end, "");
	}

	@Test public void lastThirtyDaysIsTheDefaultAndIsConfigurable()
	{
		FederationConfig defaults = new FederationConfig() {};
		assertEquals(30, defaults.eventHistoryDays());
		assertTrue(EventHistory.isHistorical(event("a", 10, 1), NOW, 30));
		assertFalse(EventHistory.isHistorical(event("b", 40, 1), NOW, 30));
		assertTrue(EventHistory.isHistorical(event("b", 40, 1), NOW, 60));
		assertFalse(EventHistory.isHistorical(event("c", 370, 1), NOW, 365));
	}

	@Test public void recentFirstAndFutureEventsStaySeparate()
	{
		DiscordEventsClient.Event older = event("older", 20, 1);
		DiscordEventsClient.Event newer = event("newer", 5, 1);
		DiscordEventsClient.Event upcoming = event("future", -2, 1);
		List<DiscordEventsClient.Event> all = Arrays.asList(older, upcoming, newer);
		assertEquals(Arrays.asList(newer, older), EventHistory.past(all, NOW, 30));
		assertEquals(List.of(upcoming), EventHistory.current(all, NOW));
	}

	@Test public void eventWithoutAnEndUsesOneDayGracePeriod()
	{
		assertTrue(EventHistory.isHistorical(event("old", 5, null), NOW, 30));
		assertFalse(EventHistory.isHistorical(event("today", 0, null), NOW, 30));
		assertEquals(List.of(event("today", 0, null)).size(), 1);
	}
}
