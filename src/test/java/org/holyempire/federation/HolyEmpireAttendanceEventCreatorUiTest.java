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
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Assert;
import org.junit.Test;

/** A chapter event creator may track even without an administrator UI tab. */
public class HolyEmpireAttendanceEventCreatorUiTest
{
	static Component find(Component root, String name)
	{
		if (name.equals(root.getName()))
			return root;
		if (root instanceof Container)
			for (Component c : ((Container)root).getComponents())
			{
				Component result = find(c, name);
				if (result != null)
					return result;
			}
		return null;
	}

	@Test public void verifiedNonAdminCannotSeeEventAttendanceButton() throws Exception
	{
		FederationPanel[] panel = {null};
		String id = "1573933979667538011";
		final int[] clicks = {0};
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setDiscordAuthorization(() -> true);
			panel[0].setAttendanceActions((event, scope, chapter, name) -> {
				Assert.assertEquals(id, event);
				Assert.assertEquals("empire", scope);
				clicks[0]++;
			}, () -> {}, () -> {});
		});
		try
		{
			panel[0].update(
				Collections.singletonMap("member", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(panel[0], "holyEmpireEventsTab")).doClick();
				Assert.assertFalse(
					"An ordinary member cannot administer",
					((JToggleButton)find(panel[0], "holyEmpireAdminControlsTab")).isVisible());
			});
			Instant start = Instant.now().plusSeconds(4000);
			DiscordEventsClient.Event event = new DiscordEventsClient.Event(
				"empire", "", id, "Empire games", "", "scheduled", "", start,
				start.plusSeconds(3600), "https://discord.com/events/293070715706671104/" + id);
			panel[0].displayEvents(
				new DiscordEventsClient.Snapshot(System.currentTimeMillis(),
												 Collections.singletonList(event)),
				() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				Assert.assertNull("A member must never see Track Attendance",
								  find(panel[0], "holyEmpireEventAttendance:" + id));
				Assert.assertEquals(0, clicks[0]);
			});
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}
}
