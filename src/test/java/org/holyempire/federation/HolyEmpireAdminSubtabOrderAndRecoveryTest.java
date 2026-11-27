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
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Admin controls stay ordered and appear on every permitted navigation. */
public class HolyEmpireAdminSubtabOrderAndRecoveryTest
{
	private static Component find(Component root, String id)
	{
		if (id.equals(root.getName()))
			return root;
		if (root instanceof Container)
			for (Component child : ((Container)root).getComponents())
			{
				Component result = find(child, id);
				if (result != null)
					return result;
			}
		return null;
	}

	@Test public void attendanceIsDefaultAndNavigationRemembersLastClickedTab() throws Exception
	{
		FederationPanel[] holder = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FederationPanel());
		FederationPanel p = holder[0];
		try
		{
			p.update(Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
					 Collections.emptyMap());
			p.setAdminEligible(true);
			p.setBroadcastEligible(true);
			p.setAttendanceEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(
					"Attendance is initial default",
					((JToggleButton)find(p, "holyEmpireAdminAttendanceSubtab")).isSelected());
				((JToggleButton)find(p, "holyEmpireAdminBroadcastsSubtab")).doClick();
				assertTrue(
					((JToggleButton)find(p, "holyEmpireAdminBroadcastsSubtab")).isSelected());
				((JToggleButton)find(p, "holyEmpireEventsTab")).doClick();
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(
					"Remember Broadcasts on return",
					((JToggleButton)find(p, "holyEmpireAdminBroadcastsSubtab")).isSelected());
				((JToggleButton)find(p, "holyEmpireAdminAnnouncementsSubtab")).doClick();
				((JToggleButton)find(p, "holyEmpireAnnouncementsTab")).doClick();
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(
					"Remember Announcements on return",
					((JToggleButton)find(p, "holyEmpireAdminAnnouncementsSubtab")).isSelected());
			});
			// A temporary permission loss shows the safe Attendance fallback
			// without erasing the last explicitly selected subtab.
			p.setAdminEligible(false);
			p.setBroadcastEligible(false);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p, "holyEmpireEventsTab")).doClick();
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(
					((JToggleButton)find(p, "holyEmpireAdminAttendanceSubtab")).isSelected());
			});
			p.setAdminEligible(true);
			p.setBroadcastEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p, "holyEmpireEventsTab")).doClick();
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(
					"Explicit choice restored when authority is restored",
					((JToggleButton)find(p, "holyEmpireAdminAnnouncementsSubtab")).isSelected());
			});
		}
		finally
		{
			p.stopPrivateCacheTimer();
		}
	}

	@Test public void allThreeTabsAndAttendanceOnlyHaveNoPhantomRows() throws Exception
	{
		FederationPanel[] holder = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new FederationPanel());
		FederationPanel p = holder[0];
		try
		{
			p.update(Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
					 Collections.emptyMap());
			p.setAdminEligible(true);
			p.setBroadcastEligible(true);
			p.setAttendanceEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				Container tabs = find(p, "holyEmpireAdminAttendanceSubtab").getParent();
				assertEquals(3, tabs.getComponentCount());
				assertEquals(0,
							 tabs.getComponentZOrder(find(p, "holyEmpireAdminAttendanceSubtab")));
				assertEquals(
					1, tabs.getComponentZOrder(find(p, "holyEmpireAdminAnnouncementsSubtab")));
				assertEquals(2,
							 tabs.getComponentZOrder(find(p, "holyEmpireAdminBroadcastsSubtab")));
				for (String id : new String[] {"holyEmpireAdminAttendanceSubtab",
											   "holyEmpireAdminAnnouncementsSubtab",
											   "holyEmpireAdminBroadcastsSubtab"})
					assertTrue(id + " visible", find(p, id).isVisible());
				((JToggleButton)find(p, "holyEmpireAnnouncementsTab")).doClick();
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(find(p, "holyEmpireAdminAnnouncementsSubtab").isVisible());
				assertTrue(find(p, "holyEmpireAdminBroadcastsSubtab").isVisible());
			});
			p.setAdminEligible(false);
			p.setBroadcastEligible(false);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				Container tabs = find(p, "holyEmpireAdminAttendanceSubtab").getParent();
				assertTrue(find(p, "holyEmpireAdminAttendanceSubtab").isVisible());
				assertFalse(find(p, "holyEmpireAdminAnnouncementsSubtab").isVisible());
				assertFalse(find(p, "holyEmpireAdminBroadcastsSubtab").isVisible());
				assertTrue("Hidden tabs must not reserve large blank rows",
						   tabs.getPreferredSize().height <= 48);
				((JToggleButton)find(p, "holyEmpireEventsTab")).doClick();
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(find(p, "holyEmpireAdminAttendanceSubtab").isVisible());
			});
			p.setAdminEligible(true);
			p.setBroadcastEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertTrue("Announcements return when authorization returns",
						   find(p, "holyEmpireAdminAnnouncementsSubtab").isVisible());
				assertTrue("Broadcasts return when authorization returns",
						   find(p, "holyEmpireAdminBroadcastsSubtab").isVisible());
			});
		}
		finally
		{
			p.stopPrivateCacheTimer();
		}
	}
}
