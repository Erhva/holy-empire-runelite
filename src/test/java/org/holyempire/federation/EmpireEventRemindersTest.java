/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.config.Notification;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmpireEventRemindersTest
{
	private static final Instant NOW = Instant.parse("2026-12-01T15:00:00Z");
	private static final String ACCOUNT = "erhva";

	private static DiscordEventsClient.Event empire(String id, Instant start)
	{
		return new DiscordEventsClient.Event("empire", "", id, "<b>Boss Mass</b>", "", "scheduled",
											 "", start, null,
											 "https://discord.com/events/293070715706671104/" + id);
	}

	private static DiscordEventsClient.Event chapter(String id, Instant start)
	{
		return new DiscordEventsClient.Event("chapter", "Holy Chaos", id, "Chaos Bingo", "",
											 "scheduled", "", start, start.plusSeconds(3600),
											 "https://discord.com/channels/293070715706671104/1/2");
	}

	private static EmpireEventReminders.Slot[] defaults()
	{
		return new EmpireEventReminders.Slot[] {new EmpireEventReminders.Slot(240, Notification.ON),
												new EmpireEventReminders.Slot(60, Notification.ON),
												new EmpireEventReminders.Slot(15, Notification.ON)};
	}

	@Test public void nativeRuneLiteNotificationOptionsAndLeadTimesHaveRequestedDefaults()
	{
		FederationConfig c = new FederationConfig() {};
		assertTrue(c.eventReminderFourHours().isEnabled());
		assertTrue(c.eventReminderOneHour().isEnabled());
		assertTrue(c.eventReminderFifteenMinutes().isEnabled());
		assertEquals(240, c.eventReminderFourHoursMinutes());
		assertEquals(60, c.eventReminderOneHourMinutes());
		assertEquals(15, c.eventReminderFifteenMinutesMinutes());
		assertTrue("Start alerts are on by default", c.eventStartingNotification().isEnabled());
		assertFalse("Uncustomized alerts use red/green chat rather than desktop defaults",
					c.eventStartingNotification().isOverride());
		assertFalse(c.eventReminderFourHours().isOverride());
	}

	@Test public void fourHoursOneHourFifteenMinutesEachFireExactlyOnce()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, true,
						  Collections.singletonList(empire("e1", NOW.plusSeconds(4 * 3600))));
		assertEquals(1, reminders.due(ACCOUNT, NOW, defaults()).size());
		assertEquals("Holy Empire: Boss Mass starts in about 4 hours.",
					 EmpireEventReminders.message(empire("e1", NOW), 240));
		assertTrue(reminders.due(ACCOUNT, NOW.plusSeconds(60), defaults()).isEmpty());
		assertEquals(1, reminders.due(ACCOUNT, NOW.plusSeconds(3 * 3600), defaults()).size());
		assertEquals(
			1, reminders.due(ACCOUNT, NOW.plusSeconds(3 * 3600 + 45 * 60), defaults()).size());
		List<EmpireEventReminders.Alert> starting =
			reminders.due(ACCOUNT, NOW.plusSeconds(4 * 3600), defaults());
		assertEquals(1, starting.size());
		assertTrue(starting.get(0).starting);
		assertEquals("Holy Empire: Boss Mass is starting now!", starting.get(0).text);
		assertTrue(reminders.due(ACCOUNT, NOW.plusSeconds(4 * 3600 + 20), defaults()).isEmpty());
	}

	@Test public void reminderDefaultsAreIndependentAndAllowCustomMinutes()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, true,
						  Collections.singletonList(empire("e2", NOW.plusSeconds(37 * 60))));
		EmpireEventReminders.Slot[] custom = {new EmpireEventReminders.Slot(37, Notification.ON),
											  new EmpireEventReminders.Slot(60, Notification.OFF),
											  new EmpireEventReminders.Slot(15, Notification.OFF)};
		List<EmpireEventReminders.Alert> due = reminders.due(ACCOUNT, NOW, custom);
		assertEquals(1, due.size());
		assertTrue(due.get(0).text.contains("37 minutes"));
		assertTrue(reminders.due(ACCOUNT, NOW.plusSeconds(10), custom).isEmpty());
		EmpireEventReminders later = new EmpireEventReminders();
		later.publish(ACCOUNT, true,
					  Collections.singletonList(empire("e2", NOW.plusSeconds(37 * 60))));
		assertTrue(
			later.due(ACCOUNT, NOW, new EmpireEventReminders.Slot(37, Notification.OFF)).isEmpty());
	}

	@Test public void empireAndChapterEventsAreBothIncludedAndDeduplicated()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, true,
						  Collections.singletonList(empire("e3", NOW.plusSeconds(15 * 60))));
		reminders.publish(ACCOUNT, false,
						  Collections.singletonList(chapter("c1", NOW.plusSeconds(15 * 60))));
		List<EmpireEventReminders.Alert> due = reminders.due(ACCOUNT, NOW, defaults());
		assertEquals(2, due.size());
		assertTrue(due.stream().anyMatch(a -> a.text.startsWith("Holy Empire: Boss Mass")));
		assertTrue(
			due.stream().anyMatch(a -> a.text.startsWith("Holy Empire (Holy Chaos): Chaos Bingo")));
		assertTrue(reminders.due(ACCOUNT, NOW, defaults()).isEmpty());
	}

	@Test public void canceledAndOldEventsAreNeverNotified()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, true,
						  Collections.singletonList(empire("e4", NOW.minusSeconds(12 * 60))));
		assertTrue(reminders.due(ACCOUNT, NOW, defaults()).isEmpty());
		reminders.publish(ACCOUNT, true, Collections.emptyList());
		assertTrue(reminders.due(ACCOUNT, NOW, defaults()).isEmpty());
		reminders.publish(ACCOUNT, true,
						  Collections.singletonList(new DiscordEventsClient.Event(
							  "empire", "", "e4", "Live event", "", "live", "",
							  NOW.plusSeconds(15 * 60), null, "")));
		assertTrue(reminders.due(ACCOUNT, NOW, defaults()).isEmpty());
	}

	@Test public void neverLeaksNotificationsToAnotherAccountOrAfterLogout()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, false,
						  Collections.singletonList(chapter("private", NOW.plusSeconds(15 * 60))));
		assertTrue(reminders.due("someoneelse", NOW, defaults()).isEmpty());
		reminders.clearFeeds();
		assertTrue(reminders.due(ACCOUNT, NOW, defaults()).isEmpty());
		reminders.publish("someoneelse", true,
						  Collections.singletonList(empire("public", NOW.plusSeconds(15 * 60))));
		assertTrue(reminders.due(ACCOUNT, NOW, defaults()).isEmpty());
		assertEquals(1, reminders.due("someoneelse", NOW, defaults()).size());
	}

	@Test public void eventStartingUsesGreenChatAndIsDeduplicated()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, true, Collections.singletonList(empire("start1", NOW)));
		List<EmpireEventReminders.Alert> due =
			reminders.due(ACCOUNT, NOW, Notification.ON, defaults());
		assertEquals(1, due.size());
		assertTrue(due.get(0).starting);
		assertEquals("Holy Empire: Boss Mass is starting now!", due.get(0).text);
		assertEquals(net.runelite.api.ChatMessageType.GAMEMESSAGE,
					 EmpireEventChatMessages.build(due.get(0)).getType());
		assertTrue(EmpireEventChatMessages.build(due.get(0))
					   .getRuneLiteFormattedMessage()
					   .contains("<col=00cc00>"));
		assertFalse(EmpireEventChatMessages.build(due.get(0))
						.getRuneLiteFormattedMessage()
						.contains("<col=ff0000>"));
		assertTrue(
			reminders.due(ACCOUNT, NOW.plusSeconds(60), Notification.ON, defaults()).isEmpty());
	}

	@Test public void redReminderChatIsDefaultAndGreenWorksForLiveEvent()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, false,
						  Collections.singletonList(chapter("chap", NOW.plusSeconds(3600))));
		List<EmpireEventReminders.Alert> alerts =
			reminders.due(ACCOUNT, NOW, Notification.ON, defaults());
		assertEquals(1, alerts.size());
		assertFalse(alerts.get(0).starting);
		assertEquals("Holy Empire (Holy Chaos): Chaos Bingo starts in about 1 hour.",
					 alerts.get(0).text);
		assertEquals(net.runelite.api.ChatMessageType.GAMEMESSAGE,
					 EmpireEventChatMessages.build(alerts.get(0)).getType());
		assertTrue(EmpireEventChatMessages.build(alerts.get(0))
					   .getRuneLiteFormattedMessage()
					   .contains("<col=ff0000>"));
		assertFalse(EmpireEventChatMessages.build(alerts.get(0))
						.getRuneLiteFormattedMessage()
						.contains("<col=00cc00>"));
		reminders.publish(ACCOUNT, false,
						  Collections.singletonList(new DiscordEventsClient.Event(
							  "chapter", "Holy Chaos", "chap", "Chaos Bingo", "", "live", "",
							  NOW.plusSeconds(3600), NOW.plusSeconds(7200), "")));
		List<EmpireEventReminders.Alert> starting =
			reminders.due(ACCOUNT, NOW.plusSeconds(3600 + 90), Notification.ON, defaults());
		assertEquals(1, starting.size());
		assertTrue(starting.get(0).starting);
		assertEquals("Holy Empire (Holy Chaos): Chaos Bingo is starting now!",
					 starting.get(0).text);
		assertTrue(reminders.due(ACCOUNT, NOW.plusSeconds(3600 + 120), Notification.ON, defaults())
					   .isEmpty());
	}

	@Test public void startingAlertCanBeDisabledWithoutDisablingOtherReminders()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, true, Collections.singletonList(empire("startDisabled", NOW)));
		assertTrue(reminders.due(ACCOUNT, NOW, Notification.OFF, defaults()).isEmpty());
		assertEquals(1, reminders.due(ACCOUNT, NOW, Notification.ON, defaults()).size());
		assertTrue(
			reminders.due(ACCOUNT, NOW.plusSeconds(10), Notification.ON, defaults()).isEmpty());
	}

	@Test public void missedStartWindowNeverBackfillsAnOldEvent()
	{
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, true,
						  Collections.singletonList(empire("tooOld", NOW.minusSeconds(7 * 60))));
		assertTrue(reminders.due(ACCOUNT, NOW, Notification.ON, defaults()).isEmpty());
		reminders.publish(
			ACCOUNT, true,
			Collections.singletonList(new DiscordEventsClient.Event(
				"empire", "", "done", "Old", "", "completed", "", NOW.minusSeconds(60), null, "")));
		assertTrue(reminders.due(ACCOUNT, NOW, Notification.ON, defaults()).isEmpty());
	}

	@Test public void customizedAlertCanStillUseNativeRuneLiteChannels()
	{
		net.runelite.client.config.Notification custom =
			Notification.ON.withOverride(true).withGameMessage(false);
		assertTrue(custom.isEnabled());
		assertTrue(custom.isOverride());
		EmpireEventReminders reminders = new EmpireEventReminders();
		reminders.publish(ACCOUNT, true,
						  Collections.singletonList(empire("custom", NOW.plusSeconds(900))));
		List<EmpireEventReminders.Alert> notifications =
			reminders.due(ACCOUNT, NOW, Notification.ON, new EmpireEventReminders.Slot(15, custom));
		assertEquals(1, notifications.size());
		assertSame(custom, notifications.get(0).notification);
	}

	@Test public void barelyLateFetchStillNotifiesButOldReminderDoesNot()
	{
		EmpireEventReminders ok = new EmpireEventReminders();
		ok.publish(ACCOUNT, true,
				   Collections.singletonList(empire("early", NOW.plusSeconds(15 * 60 - 4 * 60))));
		assertEquals(1, ok.due(ACCOUNT, NOW, defaults()).size());
		EmpireEventReminders late = new EmpireEventReminders();
		late.publish(ACCOUNT, true,
					 Collections.singletonList(empire("late", NOW.plusSeconds(15 * 60 - 7 * 60))));
		assertTrue(late.due(ACCOUNT, NOW, defaults()).isEmpty());
	}
}
