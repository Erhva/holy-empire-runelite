/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Assert;
import org.junit.Test;

public class HolyEmpireAttendanceMultiObserverEventUiTest
{
	private static Component find(Component root, String id)
	{
		if (id.equals(root.getName()))
			return root;
		if (root instanceof Container)
			for (Component c : ((Container)root).getComponents())
			{
				Component found = find(c, id);
				if (found != null)
					return found;
			}
		return null;
	}

	@Test public void discoversActiveEventWhenDiscordAlreadyRemovedIt() throws Exception
	{
		FederationPanel[] panel = {null};
		String id = "1573933979667538033";
		long start = Instant.now().minusSeconds(4000).getEpochSecond();
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setDiscordAuthorization(() -> true);
		});
		try
		{
			panel[0].update(
				Collections.singletonMap("observer", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			Map<String, HolyEmpireAttendanceEventStatusClient.State> active = new HashMap<>();
			active.put(id, new HolyEmpireAttendanceEventStatusClient.State(
							   "ongoing", 1, start + 3000, 0, "empire", "", "Recovered Empire Mass",
							   "", "", start));
			panel[0].displayAttendanceServerStates(active);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(panel[0], "holyEmpireEventsTab")).doClick();
				Assert.assertNotNull(find(panel[0], "holyEmpireEvent:" + id));
				JLabel time = (JLabel)find(panel[0], "holyEmpireEventTime:" + id);
				Assert.assertTrue(time.getText().startsWith("ONGOING"));
				Assert.assertTrue(panel[0].attendanceVisibleEventIds().contains(id));
			});
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}

	@Test public void keepsEventOngoingUntilLastRemoteTrackerCloses() throws Exception
	{
		FederationPanel[] panel = {null};
		String id = "1573933979667538011";
		Instant end = Instant.now().minusSeconds(600);
		DiscordEventsClient.Event event = new DiscordEventsClient.Event(
			"empire", "", id, "Past Scheduled Event", "", "scheduled", "", end.minusSeconds(3600),
			end, "https://discord.com/events/293070715706671104/" + id);
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setDiscordAuthorization(() -> true);
		});
		try
		{
			panel[0].update(
				Collections.singletonMap("observer", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			panel[0].displayEvents(
				new DiscordEventsClient.Snapshot(System.currentTimeMillis(),
												 Collections.singletonList(event)),
				() -> true);
			Map<String, HolyEmpireAttendanceEventStatusClient.State> active = new HashMap<>();
			active.put(id, new HolyEmpireAttendanceEventStatusClient.State(
							   "ongoing", 2, end.getEpochSecond(), 0));
			panel[0].displayAttendanceServerStates(active);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(panel[0], "holyEmpireEventsTab")).doClick();
				Assert.assertNotNull("Still active after scheduled end",
									 find(panel[0], "holyEmpireEvent:" + id));
				JLabel status = (JLabel)find(panel[0], "holyEmpireEventTime:" + id);
				Assert.assertTrue(status.getText().startsWith("ONGOING"));
			});
			// Discord may remove past events from its scheduled feed.
			panel[0].displayEvents(new DiscordEventsClient.Snapshot(System.currentTimeMillis(),
																	Collections.emptyList()),
								   () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> Assert.assertNotNull(find(panel[0], "holyEmpireEvent:" + id)));
			Map<String, HolyEmpireAttendanceEventStatusClient.State> finished = new HashMap<>();
			finished.put(id, new HolyEmpireAttendanceEventStatusClient.State(
								 "ended", 0, end.getEpochSecond(), end.getEpochSecond() + 1200));
			panel[0].displayAttendanceServerStates(finished);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				Assert.assertNull(find(panel[0], "holyEmpireEvent:" + id));
				((JButton)find(panel[0], "holyEmpireEventsHistoryOpen")).doClick();
				Assert.assertNotNull("Event is now historical",
									 find(panel[0], "holyEmpireHistoryEvent:" + id));
			});
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}
}
