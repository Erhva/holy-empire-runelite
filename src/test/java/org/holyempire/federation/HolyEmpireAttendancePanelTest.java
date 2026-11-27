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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Assert;
import org.junit.Test;

/** Real Swing UI acceptance for chapter-admin-only and linked event controls. */
public class HolyEmpireAttendancePanelTest
{
	private static Component find(Component root, String name)
	{
		if (name.equals(root.getName()))
			return root;
		if (root instanceof Container)
			for (Component c : ((Container)root).getComponents())
			{
				Component found = find(c, name);
				if (found != null)
					return found;
			}
		return null;
	}

	@Test public void chapterAdminSeesManualAttendanceAndEventAction() throws Exception
	{
		FederationPanel[] panel = {null};
		List<String> starts = new ArrayList<>();
		AtomicInteger stops = new AtomicInteger();
		AtomicInteger saves = new AtomicInteger();
		Map<String, List<String>> roster = new LinkedHashMap<>();
		roster.put("host", Collections.singletonList("Holy Empire"));
		roster.put("visitor", Arrays.asList("Holy Chaos", "World 465"));
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setAttendanceActions(
				(id, scope, chapter, title)
					-> starts.add(id + "|" + scope + "|" + chapter + "|" + title),
				stops::incrementAndGet, saves::incrementAndGet);
			panel[0].setEventsLoader(() -> {});
		});
		try
		{
			panel[0].update(roster, Collections.emptyMap());
			panel[0].setAttendanceEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JToggleButton admin = (JToggleButton)find(panel[0], "holyEmpireAdminControlsTab");
				Assert.assertTrue("Verified chapter admin needs Admin Controls", admin.isVisible());
				admin.doClick();
				JToggleButton attendance =
					(JToggleButton)find(panel[0], "holyEmpireAdminAttendanceSubtab");
				Assert.assertNotNull(attendance);
				attendance.doClick();
				JButton start = (JButton)find(panel[0], "holyEmpireAttendanceStartManual");
				Assert.assertTrue(start.isEnabled());
				JTextField name = (JTextField)find(panel[0], "holyEmpireAttendanceManualTitle");
				Assert.assertEquals("", name.getText());
				start.doClick();
				Assert.assertEquals(0, starts.size());
				Assert.assertNotNull(find(panel[0], "holyEmpireAttendanceNameError"));
				name.setText("Holy Empire event");
				((JButton)find(panel[0], "holyEmpireAttendanceStartManual")).doClick();
				Assert.assertEquals(1, starts.size());
				Assert.assertTrue(starts.get(0).contains("|manual||Holy Empire event"));

				JToggleButton events = (JToggleButton)find(panel[0], "holyEmpireEventsTab");
				events.doClick();
			});
			Instant start = Instant.now().plusSeconds(3600);
			String eventId = "1573933979667538011";
			DiscordEventsClient.Event event = new DiscordEventsClient.Event(
				"empire", "", eventId, "Clan Recruitment", "Welcome", "scheduled", "World 465",
				start, start.plusSeconds(3600),
				"https://discord.com/events/293070715706671104/" + eventId);
			panel[0].displayEvents(
				new DiscordEventsClient.Snapshot(System.currentTimeMillis(),
												 Collections.singletonList(event)),
				() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JButton track = (JButton)find(panel[0], "holyEmpireEventAttendance:" + eventId);
				Assert.assertNotNull("Event card must show event-specific tracking", track);
				Assert.assertTrue(track.isEnabled());
				track.doClick();
				Assert.assertEquals(2, starts.size());
				Assert.assertEquals(eventId + "|empire||Clan Recruitment", starts.get(1));
			});

			HolyEmpireAttendanceTracker tracker = new HolyEmpireAttendanceTracker();
			tracker.start(eventId, "empire", "", "Clan Recruitment", "host", roster,
						  System.currentTimeMillis() - 2000);
			tracker.observe(Collections.singleton("visitor"), System.currentTimeMillis() - 800);
			panel[0].displayAttendance(tracker.report(), "Recording", false);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JToggleButton admin = (JToggleButton)find(panel[0], "holyEmpireAdminControlsTab");
				admin.doClick();
				((JToggleButton)find(panel[0], "holyEmpireAdminAttendanceSubtab")).doClick();
				JButton stop = (JButton)find(panel[0], "holyEmpireAttendanceStop");
				Assert.assertNotNull(stop);
				stop.doClick();
				Assert.assertEquals(1, stops.get());
			});
			panel[0].displayAttendance(tracker.finish(System.currentTimeMillis()),
									   "Archive pending", false);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JButton save = (JButton)find(panel[0], "holyEmpireAttendanceSave");
				Assert.assertNotNull(save);
				save.doClick();
				Assert.assertEquals(1, saves.get());
				Assert.assertNotNull(find(panel[0], "holyEmpireAttendanceMember:visitor"));
				Assert.assertNotNull(find(panel[0], "holyEmpireAttendanceReportId"));
				Assert.assertEquals("Save Attendance Report", save.getText());
				Component status = find(panel[0], "holyEmpireAttendanceStatus");
				Assert.assertNotNull(status);
				Assert.assertSame(save.getParent(), status.getParent());
				java.util.List<Component> items =
					java.util.Arrays.asList(save.getParent().getComponents());
				Assert.assertTrue(items.indexOf(status) > items.indexOf(save));
				JButton copy = (JButton)find(panel[0], "holyEmpireAttendanceCopyList");
				copy.doClick();
				// Clipboard feedback rebuilds the panel; reacquire its button.
				copy = (JButton)find(panel[0], "holyEmpireAttendanceCopyList");
				Component copyStatus = find(panel[0], "holyEmpireAttendanceCopyStatus");
				Assert.assertNotNull(copyStatus);
				Assert.assertSame(copy.getParent(), copyStatus.getParent());
				items = java.util.Arrays.asList(copy.getParent().getComponents());
				Assert.assertTrue(items.indexOf(copyStatus) >
								  items.indexOf(find(panel[0], "holyEmpireAttendanceCopyList")));
				JToggleButton events = (JToggleButton)find(panel[0], "holyEmpireEventsTab");
				events.doClick();
				Assert.assertNotNull("Linked Empire event reports remain visible",
									 find(panel[0], "holyEmpireAttendanceSummary"));
				((JToggleButton)find(panel[0], "holyEmpireAdminControlsTab")).doClick();
				((JToggleButton)find(panel[0], "holyEmpireAdminAttendanceSubtab")).doClick();
				JButton next = (JButton)find(panel[0], "holyEmpireAttendanceStartManual");
				Assert.assertTrue("Pending archive must not block a new report", next.isEnabled());
				JTextField newTitle = (JTextField)find(panel[0], "holyEmpireAttendanceManualTitle");
				newTitle.setText("Second event");
				next.doClick();
				Assert.assertEquals(3, starts.size());
			});
			panel[0].displayAttendance(tracker.report(),
									   "Attendance queued for the private Discord archive.", false);
			SwingUtilities.invokeAndWait(() -> {
				Component summary = find(panel[0], "holyEmpireAttendanceSummary");
				Assert.assertNotNull(summary);
				Assert.assertTrue(((JTextArea)summary).getText().contains("QUEUED FOR ARCHIVE"));
			});
			panel[0].displayAttendance(tracker.report(),
									   "Attendance archived for Discord. Report #4", true);
			SwingUtilities.invokeAndWait(() -> {
				JButton save = (JButton)find(panel[0], "holyEmpireAttendanceSave");
				Assert.assertNotNull(save);
				Assert.assertTrue("Archived reports must still allow Save for a success response",
								  save.isEnabled());
				Assert.assertTrue(((JTextArea)find(panel[0], "holyEmpireAttendanceSummary"))
									  .getText()
									  .contains("SAVED"));
				save.doClick();
				Assert.assertEquals(2, saves.get());
			});
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}
}
