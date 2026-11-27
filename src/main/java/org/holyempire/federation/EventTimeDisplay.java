/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class EventTimeDisplay
{
	private EventTimeDisplay()
	{
	}

	static String label(Instant start, Instant end, Instant now, ZoneId zone)
	{
		if (start == null)
		{
			return "Date unavailable";
		}
		final ZoneId localZone = zone != null ? zone : ZoneId.systemDefault();
		final Instant checkedNow = now != null ? now : Instant.now();
		// Empire-wide events can omit an end timestamp. Treat those as
		// one-calendar-day events in the viewer's zone, including DST changes.
		final Instant ending = end != null && end.isAfter(start)
								   ? end
								   : start.atZone(localZone).plusDays(1).toInstant();
		if (!checkedNow.isBefore(start) && checkedNow.isBefore(ending))
		{
			return "HAPPENING NOW";
		}
		if (checkedNow.isBefore(start))
		{
			long seconds = Duration.between(checkedNow, start).getSeconds();
			if (seconds > 0 && seconds <= 4 * 3600)
			{
				long minutes = Math.max(1, (seconds + 59) / 60);
				long hours = minutes / 60;
				long remainder = minutes % 60;
				return "Starts in " + (hours > 0 ? hours + "h " : "") + remainder + "m";
			}
		}
		return DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a z", Locale.getDefault())
			.withZone(localZone)
			.format(start);
	}
}
