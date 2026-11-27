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
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Assert;
import org.junit.Test;

public class HolyEmpireAttendanceFinishUiTest
{
	private static Component find(Component root, String name)
	{
		if (name.equals(root.getName()))
			return root;
		if (root instanceof Container)
			for (Component child : ((Container)root).getComponents())
			{
				Component result = find(child, name);
				if (result != null)
					return result;
			}
		return null;
	}

	@Test public void announcementCounterLimitsInputAndKeepsControls() throws Exception
	{
		FederationPanel[] p = {null};
		SwingUtilities.invokeAndWait(() -> {
			p[0] = new FederationPanel();
			p[0].setAdminPublishing((kind, title, body) -> {}, () -> true, () -> {});
			p[0].setDiscordAuthorization(() -> true);
			p[0].setAnnouncementActions(
				(kind, title, body, url) -> {}, (action, id, title, body, url) -> {});
		});
		try
		{
			p[0].update(
				Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			p[0].setAdminEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p[0], "holyEmpireAdminControlsTab")).doClick();
				((JToggleButton)find(p[0], "holyEmpireAdminAnnouncementsSubtab")).doClick();
				JTextArea body = (JTextArea)find(p[0], "holyEmpireAdminBody:announcement");
				Assert.assertNotNull(body);
				body.setText("x".repeat(600));
				Assert.assertEquals(350, body.getText().length());
				JLabel counter = (JLabel)find(p[0], "holyEmpireAnnouncementCharacterCounter");
				Assert.assertTrue(counter.getText().contains("350 / 350"));
				Assert.assertNotNull(find(p[0], "holyEmpireAdminPublish:announcement"));
			});
		}
		finally
		{
			p[0].stopPrivateCacheTimer();
		}
	}

	@Test public void archivedEventOffersCopyAttendance() throws Exception
	{
		FederationPanel[] p = {null};
		AtomicBoolean callback = new AtomicBoolean();
		String eventId = "1573933979667538011";
		SwingUtilities.invokeAndWait(() -> {
			p[0] = new FederationPanel();
			p[0].setDiscordAuthorization(() -> true);
			p[0].setAttendanceHistoryAction(id -> {
				Assert.assertEquals(eventId, id);
				callback.set(true);
			});
		});
		try
		{
			p[0].update(
				Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			Instant ended = Instant.now().minusSeconds(1800);
			DiscordEventsClient.Event event = new DiscordEventsClient.Event(
				"empire", "", eventId, "Past Event", "", "scheduled", "", ended.minusSeconds(3600),
				ended, "https://discord.com/events/293070715706671104/" + eventId);
			p[0].displayEvents(new DiscordEventsClient.Snapshot(System.currentTimeMillis(),
																Collections.singletonList(event)),
							   () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p[0], "holyEmpireEventsTab")).doClick();
				((JButton)find(p[0], "holyEmpireEventsHistoryOpen")).doClick();
				JButton copy = (JButton)find(p[0], "holyEmpireEventCopyAttendance:" + eventId);
				Assert.assertNotNull(copy);
				copy.doClick();
				Assert.assertTrue(callback.get());
			});
		}
		finally
		{
			p[0].stopPrivateCacheTimer();
		}
	}
}
