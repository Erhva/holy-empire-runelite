/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 */
package org.holyempire.federation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-authorized history is split by time, never by untrusted event title. */
final class EventHistory
{
	private EventHistory()
	{
	}

	static Instant finishedAt(DiscordEventsClient.Event e)
	{
		if (e == null || e.start == null)
		{
			return null;
		}
		// Single-day Discord events may not specify an end. Treat such an
		// event as past only after its start-day window has elapsed.
		return e.end == null ? e.start.plus(Duration.ofDays(1)) : e.end;
	}

	static boolean isHistorical(DiscordEventsClient.Event e, Instant now, int days)
	{
		Instant finished = finishedAt(e);
		if (finished == null || now == null || days < 1)
		{
			return false;
		}
		int bounded = Math.min(365, days);
		return !finished.isAfter(now) && finished.isAfter(now.minus(Duration.ofDays(bounded)));
	}

	static List<DiscordEventsClient.Event> past(List<DiscordEventsClient.Event> events, Instant now,
												int days)
	{
		List<DiscordEventsClient.Event> result = new ArrayList<>();
		for (DiscordEventsClient.Event e : events)
		{
			if (isHistorical(e, now, days))
			{
				result.add(e);
			}
		}
		result.sort(Comparator.comparing((DiscordEventsClient.Event e) -> e.start)
						.reversed()
						.thenComparing(e -> e.id));
		return result;
	}

	static List<DiscordEventsClient.Event> current(List<DiscordEventsClient.Event> events,
												   Instant now)
	{
		List<DiscordEventsClient.Event> result = new ArrayList<>();
		for (DiscordEventsClient.Event e : events)
		{
			Instant finished = finishedAt(e);
			if (finished != null && finished.isAfter(now))
			{
				result.add(e);
			}
		}
		result.sort(Comparator.comparing((DiscordEventsClient.Event e) -> e.start)
						.thenComparing(e -> e.id));
		return result;
	}
}
