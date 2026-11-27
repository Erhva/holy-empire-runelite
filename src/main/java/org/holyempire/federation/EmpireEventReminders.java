/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.Notification;
import net.runelite.client.util.Text;

/**
 * Scoped, timezone-independent reminder engine. Calendar reads happen on
 * the existing background worker; only events authorized for the active
 * roster account are stored. A reminder is delivered once per event, start
 * time and lead time even if both sources are refreshed repeatedly.
 */
final class EmpireEventReminders
{
	static final long DELIVERY_GRACE_SECONDS = 6L * 60;
	static final int MAX_MINUTES = 7 * 24 * 60;

	static final class Slot
	{
		final int minutes;
		final Notification notification;

		Slot(int minutes, Notification notification)
		{
			this.minutes = minutes;
			this.notification = notification;
		}
	}

	static final class Alert
	{
		final String text;
		final Notification notification;
		final boolean starting;

		Alert(String text, Notification notification, boolean starting)
		{
			this.text = text;
			this.notification = notification;
			this.starting = starting;
		}
	}

	private String activeAccount = "";
	private List<DiscordEventsClient.Event> publicEvents = Collections.emptyList();
	private List<DiscordEventsClient.Event> chapterEvents = Collections.emptyList();
	// Keys only; do not retain event titles or roster records after logout.
	private final Map<String, Instant> sent = new HashMap<>();

	synchronized void publish(String account, boolean publicFeed,
							  List<DiscordEventsClient.Event> events)
	{
		if (account == null || account.isEmpty() || events == null)
		{
			return;
		}
		if (!account.equals(activeAccount))
		{
			clearFeeds();
			activeAccount = account;
		}
		List<DiscordEventsClient.Event> snapshot =
			Collections.unmodifiableList(new ArrayList<>(events));
		if (publicFeed)
		{
			publicEvents = snapshot;
		}
		else
		{
			chapterEvents = snapshot;
		}
	}

	synchronized void clearFeed(boolean publicFeed)
	{
		if (publicFeed)
		{
			publicEvents = Collections.emptyList();
		}
		else
		{
			chapterEvents = Collections.emptyList();
		}
	}

	synchronized void clearFeeds()
	{
		publicEvents = Collections.emptyList();
		chapterEvents = Collections.emptyList();
		activeAccount = "";
	}

	synchronized List<Alert> due(String account, Instant now, Slot... settings)
	{
		return due(account, now, Notification.ON, settings);
	}

	synchronized List<Alert> due(String account, Instant now, Notification startingNotification,
								 Slot... settings)
	{
		if (account == null || account.isEmpty() || !account.equals(activeAccount) || now == null ||
			settings == null)
		{
			return Collections.emptyList();
		}
		sent.entrySet().removeIf(e -> !e.getValue().isAfter(now.minus(Duration.ofDays(1))));
		List<Alert> alerts = new ArrayList<>();
		Set<String> seenThisCheck = new HashSet<>();
		for (DiscordEventsClient.Event event : combined())
		{
			if (event == null || event.start == null || event.id == null || event.id.isEmpty() ||
				(!"scheduled".equals(event.status) && !"live".equals(event.status)) ||
				(!"empire".equals(event.scope) && !"chapter".equals(event.scope)))
			{
				continue;
			}
			long until = Duration.between(now, event.start).getSeconds();
			String eventKey = account + "|" + event.scope + "|" + event.chapter + "|" + event.id +
							  "|" + event.start;
			if (until <= 0)
			{
				// The Discord feed can transition to 'live' near its start.
				// A six-minute grace accommodates the five-minute feed poll,
				// without notifying users about long-finished events.
				if (until >= -DELIVERY_GRACE_SECONDS && startingNotification != null &&
					startingNotification.isEnabled())
				{
					String key = eventKey + "|START";
					if (seenThisCheck.add(key) && !sent.containsKey(key))
					{
						sent.put(key, event.start);
						alerts.add(new Alert(startingMessage(event), startingNotification, true));
					}
				}
				continue;
			}
			// A live event should not produce an upcoming reminder.
			if (!"scheduled".equals(event.status))
			{
				continue;
			}
			for (Slot setting : settings)
			{
				if (setting == null || setting.notification == null ||
					!setting.notification.isEnabled() || setting.minutes < 1 ||
					setting.minutes > MAX_MINUTES)
				{
					continue;
				}
				long lead = setting.minutes * 60L;
				if (until > lead || until < lead - DELIVERY_GRACE_SECONDS)
				{
					continue;
				}
				String key = eventKey + "|" + setting.minutes;
				if (!seenThisCheck.add(key) || sent.containsKey(key))
				{
					continue;
				}
				sent.put(key, event.start);
				alerts.add(new Alert(message(event, setting.minutes), setting.notification, false));
			}
		}
		if (sent.size() > 1500)
		{
			sent.entrySet().removeIf(e -> !e.getValue().isAfter(now));
		}
		return alerts;
	}

	private List<DiscordEventsClient.Event> combined()
	{
		List<DiscordEventsClient.Event> result =
			new ArrayList<>(publicEvents.size() + chapterEvents.size());
		result.addAll(publicEvents);
		result.addAll(chapterEvents);
		return result;
	}

	static String leadLabel(int minutes)
	{
		if (minutes % 60 == 0)
		{
			int hours = minutes / 60;
			return hours + (hours == 1 ? " hour" : " hours");
		}
		if (minutes < 60)
		{
			return minutes + (minutes == 1 ? " minute" : " minutes");
		}
		return (minutes / 60) + "h " + (minutes % 60) + "m";
	}

	private static String eventLabel(DiscordEventsClient.Event event)
	{
		String title = Text.removeTags(event.name == null ? "" : event.name)
						   .replaceAll("[\\r\\n]+", " ")
						   .trim();
		String origin =
			"chapter".equals(event.scope) ? "Holy Empire (" + event.chapter + ")" : "Holy Empire";
		return origin + ": " + title;
	}

	static String message(DiscordEventsClient.Event event, int minutes)
	{
		return eventLabel(event) + " starts in about " + leadLabel(minutes) + ".";
	}

	static String startingMessage(DiscordEventsClient.Event event)
	{
		return eventLabel(event) + " is starting now!";
	}
}
