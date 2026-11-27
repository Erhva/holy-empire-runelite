/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventTimeDisplayTest
{
	@Test public void ongoingHasPrecedenceOverDateAndCountdown()
	{
		Instant now = Instant.parse("2026-11-22T23:30:00Z");
		assertEquals("HAPPENING NOW",
					 EventTimeDisplay.label(now.minusSeconds(15), now.plusSeconds(150), now,
											ZoneId.of("America/Chicago")));
	}

	@Test public void countdownBeginsAtFourHoursAndUpdatesToOneMinute()
	{
		Instant start = Instant.parse("2026-11-23T04:00:00Z");
		ZoneId zone = ZoneId.of("America/New_York");
		assertEquals("Starts in 4h 0m", EventTimeDisplay.label(start, start.plusSeconds(3600),
															   start.minusSeconds(14400), zone));
		assertEquals("Starts in 3h 59m", EventTimeDisplay.label(start, start.plusSeconds(3600),
																start.minusSeconds(14340), zone));
		assertEquals("Starts in 1m", EventTimeDisplay.label(start, start.plusSeconds(3600),
															start.minusSeconds(12), zone));
		assertFalse(
			EventTimeDisplay.label(start, start.plusSeconds(3600), start.minusSeconds(14401), zone)
				.startsWith("Starts in"));
	}

	@Test public void distantDatesAreShownInThePlayersOwnTimeZone()
	{
		Instant starts = Instant.parse("2026-12-03T02:00:00Z");
		Instant now = Instant.parse("2026-11-22T00:00:00Z");
		String chicago = EventTimeDisplay.label(starts, starts.plusSeconds(3600), now,
												ZoneId.of("America/Chicago"));
		String london = EventTimeDisplay.label(starts, starts.plusSeconds(3600), now,
											   ZoneId.of("Europe/London"));
		assertTrue(chicago.contains("8:00 PM"));
		assertTrue(london.contains("2:00 AM"));
		assertNotEquals(chicago, london);
	}

	@Test public void missingEndTimestampIsOneCalendarDayNotAnException()
	{
		Instant start = Instant.parse("2026-11-23T01:00:00Z");
		ZoneId zone = ZoneId.of("America/Chicago");
		assertEquals("HAPPENING NOW",
					 EventTimeDisplay.label(start, null, start.plusSeconds(3600), zone));
		assertFalse("A one-day event must eventually stop being current",
					"HAPPENING NOW".equals(EventTimeDisplay.label(
						start, null, start.plusSeconds(86400 + 3600), zone)));
		assertEquals("Date unavailable", EventTimeDisplay.label(null, null, start, zone));
	}
}
