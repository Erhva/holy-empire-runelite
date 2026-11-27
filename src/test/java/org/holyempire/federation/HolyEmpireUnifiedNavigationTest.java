/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** The global Discord prompt and announcements-first UX are single-purpose. */
public class HolyEmpireUnifiedNavigationTest
{
	private static Component find(Component root, String name)
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

	@Test public void freshLoginStartsAnnouncementsAndPreservesChosenPage() throws Exception
	{
		FederationPanel[] view = new FederationPanel[1];
		AtomicBoolean connected = new AtomicBoolean(false);
		int[] calls = {0};
		SwingUtilities.invokeAndWait(() -> {
			view[0] = new FederationPanel();
			view[0].setDiscordConnector(() -> calls[0]++);
			view[0].setDiscordAuthorization(connected::get);
			view[0].setEventsLoader(() -> {});
		});
		try
		{
			view[0].beginLoginSession();
			view[0].showMessage("Checking Holy Empire membership...");
			view[0].update(
				Collections.singletonMap("alpha", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertTrue(
					((JToggleButton)find(view[0], "holyEmpireAnnouncementsTab")).isSelected());
				assertNotNull(find(view[0], "holyEmpireDiscordVerify"));
				assertNull(find(view[0], "holyEmpireOfficerBanSettings"));
				assertNull(find(view[0], "holyEmpireRsvpOneClickConnect"));
				assertNull(find(view[0], "holyEmpireAdminConnectOwner"));
				((JButton)find(view[0], "holyEmpireDiscordVerify")).doClick();
				assertEquals(1, calls[0]);
				((JToggleButton)find(view[0], "holyEmpireRosterTab")).doClick();
				assertTrue(((JToggleButton)find(view[0], "holyEmpireRosterTab")).isSelected());
			});
			view[0].update(
				Collections.singletonMap("alpha", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertTrue("Routine roster refresh cannot change the open page",
						   ((JToggleButton)find(view[0], "holyEmpireRosterTab")).isSelected());
				((JToggleButton)find(view[0], "holyEmpireEventsTab")).doClick();
			});
			view[0].update(
				Collections.singletonMap("alpha", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertTrue(
						"Events page must persist through background refresh",
						((JToggleButton)find(view[0], "holyEmpireEventsTab")).isSelected()));
			connected.set(true);
			view[0].chapterEventsConnected(() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertFalse("Global verification disappears after OAuth",
							((JButton)find(view[0], "holyEmpireDiscordVerify")).isVisible());
				assertTrue("OAuth must not change the open page",
						   ((JToggleButton)find(view[0], "holyEmpireEventsTab")).isSelected());
				assertNull(find(view[0], "holyEmpireEventsPrivacyNotice"));
			});
			view[0].beginLoginSession();
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertTrue(
						"A new login must return to announcements",
						((JToggleButton)find(view[0], "holyEmpireAnnouncementsTab")).isSelected()));
		}
		finally
		{
			view[0].stopPrivateCacheTimer();
		}
	}

	@Test public void navigationOrderAndSingleButtonPositionAreStable() throws Exception
	{
		FederationPanel[] view = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			view[0] = new FederationPanel();
			view[0].setDiscordConnector(() -> {});
			view[0].setDiscordAuthorization(() -> false);
		});
		try
		{
			view[0].update(
				Collections.singletonMap("alpha", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			view[0].setOfficerSettingsEligible(true);
			view[0].setAdminEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JPanel navigation = (JPanel)find(view[0], "holyEmpireNavigationTabs");
				navigation.setSize(320, 200);
				navigation.doLayout();
				JToggleButton announcements =
					(JToggleButton)find(view[0], "holyEmpireAnnouncementsTab");
				JToggleButton events = (JToggleButton)find(view[0], "holyEmpireEventsTab");
				JToggleButton roster = (JToggleButton)find(view[0], "holyEmpireRosterTab");
				JToggleButton admin = (JToggleButton)find(view[0], "holyEmpireAdminControlsTab");
				JToggleButton bans = (JToggleButton)find(view[0], "holyEmpireBanListTab");
				JButton verify = (JButton)find(view[0], "holyEmpireDiscordVerify");
				assertSame("Global verification belongs directly under navigation", navigation,
						   verify.getParent());
				assertTrue(announcements.getY() < events.getY());
				assertTrue(events.getX() < roster.getX());
				assertEquals(events.getY(), roster.getY());
				assertTrue(admin.getX() < bans.getX());
				assertEquals(admin.getY(), bans.getY());
				assertTrue(verify.getY() > admin.getY());
				assertTrue(verify.getWidth() >= admin.getWidth() + bans.getWidth());
				admin.doClick();
				assertNull("Owner page must not have its own verification button",
						   find(view[0], "holyEmpireAdminConnectOwner"));
				bans.doClick();
				assertNull("Ban page must not have its own verification button",
						   find(view[0], "holyEmpireOfficerBanSettings"));
			});
		}
		finally
		{
			view[0].stopPrivateCacheTimer();
		}
	}
}
