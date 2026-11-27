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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmpireAnnouncementsManagementUiTest
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

	@Test public void ownerCanSeeManagementControlsAndDiscordPostLink() throws Exception
	{
		FederationPanel[] view = new FederationPanel[1];
		AtomicBoolean owner = new AtomicBoolean(true);
		SwingUtilities.invokeAndWait(() -> {
			view[0] = new FederationPanel();
			view[0].setAdminPublishing((kind, title, body) -> {}, owner::get, () -> {});
			view[0].setDiscordAuthorization(owner::get);
			view[0].setAnnouncementActions(
				(kind, title, body, url) -> {}, (action, id, title, body, url) -> {});
		});
		try
		{
			view[0].update(
				Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			long now = Instant.now().getEpochSecond();
			String url = "https://discord.com/channels/736564064834289774/"
						 + "1573933979667538010/1573933979667538011";
			EmpireCommunicationsClient.Notice notice = new EmpireCommunicationsClient.Notice(
				81, "announcement", "Important", "Details", now, 0, url, now, "empire", "",
				"Discord Test Admin", true);
			view[0].displayCommunications(
				new EmpireCommunicationsClient.Snapshot(now, 81, List.of(notice), List.of(), null),
				() -> true);
			view[0].setAdminEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(view[0], "holyEmpireAnnouncementsTab")).doClick();
				assertNotNull(find(view[0], "holyEmpireAnnouncementDiscordPost:81"));
				assertEquals("Posted by Discord Test Admin",
							 ((JLabel)find(view[0], "holyEmpireAnnouncementPoster:81")).getText());
				assertNotNull(find(view[0], "holyEmpireAnnouncementControls:81"));
				assertTrue(((JButton)find(view[0], "holyEmpireAnnouncementEdit:81")).isEnabled());
				assertTrue(((JButton)find(view[0], "holyEmpireAnnouncementRemove:81")).isEnabled());
				((JToggleButton)find(view[0], "holyEmpireAdminControlsTab")).doClick();
				((JToggleButton)find(view[0], "holyEmpireAdminAnnouncementsSubtab")).doClick();
				assertNotNull(find(view[0], "holyEmpireAdminAnnouncementDiscordLink"));
				assertNull("Editing is now beneath each announcement, not Admin Controls",
						   find(view[0], "holyEmpireAnnouncementEdit:81"));
			});
			owner.set(false);
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(view[0], "holyEmpireAnnouncementsTab")).doClick();
				assertNull("Expired Discord verification hides the action box",
						   find(view[0], "holyEmpireAnnouncementControls:81"));
				assertNotNull(find(view[0], "holyEmpireAnnouncementPoster:81"));
			});
		}
		finally
		{
			view[0].stopPrivateCacheTimer();
		}
	}
}
