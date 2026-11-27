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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmpireCommunicationsPanelTest
{
	private static Component named(Component root, String name)
	{
		if (name.equals(root.getName()))
			return root;
		if (root instanceof Container)
		{
			for (Component child : ((Container)root).getComponents())
			{
				Component found = named(child, name);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	@Test public void publisherEditorsEnforceLimitsOnTypedAndPastedText() throws Exception
	{
		FederationPanel[] panel = {null};
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			try
			{
				for (String name : new String[] {"broadcastBody", "emergencyBody", "announcementBody"})
				{
					java.lang.reflect.Field field = FederationPanel.class.getDeclaredField(name);
					field.setAccessible(true);
					JTextArea input = (JTextArea) field.get(panel[0]);
					int maximum = "announcementBody".equals(name) ? 350 : 200;
					input.setText("x".repeat(maximum + 70));
					assertEquals(name, maximum, input.getText().length());
					input.replaceRange("short", 0, maximum);
					assertEquals("short", input.getText());
				}
			}
			catch (ReflectiveOperationException error)
			{
				throw new AssertionError(error);
			}
		});
		panel[0].stopPrivateCacheTimer();
	}

	@Test public void newsIsPublicToRosterButOwnerControlsRequireExplicitGate() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		final AtomicBoolean owner = new AtomicBoolean(false);
		final List<String> posted = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setAdminPublishing((kind, title, body)
											-> posted.add(kind + "|" + title + "|" + body),
										owner::get, () -> {});
		});
		try
		{
			panel[0].update(
				Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JToggleButton news = (JToggleButton)named(panel[0], "holyEmpireAnnouncementsTab");
				assertTrue(news.isVisible());
				assertFalse(
					((JToggleButton)named(panel[0], "holyEmpireAdminControlsTab")).isVisible());
				news.doClick();
				assertNotNull(named(panel[0], "holyEmpireAnnouncementsTab"));
				assertNull(named(panel[0], "holyEmpireAdminPublish:broadcast"));
			});
			panel[0].setAdminEligible(true);
			panel[0].setBroadcastEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JToggleButton admin = (JToggleButton)named(panel[0], "holyEmpireAdminControlsTab");
				assertTrue(admin.isVisible());
				admin.doClick();
				assertTrue(((JToggleButton)named(panel[0], "holyEmpireAdminAnnouncementsSubtab"))
							   .isSelected());
				((JToggleButton)named(panel[0], "holyEmpireAdminBroadcastsSubtab")).doClick();
				assertNull("No separate Owner verification control",
						   named(panel[0], "holyEmpireAdminConnectOwner"));
				assertFalse(
					((JButton)named(panel[0], "holyEmpireAdminPublish:broadcast")).isEnabled());
			});
			owner.set(true);
			panel[0].showPublishStatus("Owner verified", () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JButton send = (JButton)named(panel[0], "holyEmpireAdminPublish:broadcast");
				assertTrue(send.isEnabled());
				((JTextField)named(panel[0], "holyEmpireAdminTitle:broadcast"))
					.setText("Holy Empire");
				((JTextArea)named(panel[0], "holyEmpireAdminBody:broadcast")).setText("Hello");
				send.doClick();
			});
			assertEquals(List.of("broadcast|Holy Empire|Hello"), posted);
			panel[0].setAdminEligible(false);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JToggleButton admin = (JToggleButton)named(panel[0], "holyEmpireAdminControlsTab");
				assertFalse(admin.isVisible());
				assertNull("Owner publishing removed on rank loss",
						   named(panel[0], "holyEmpireAdminPublish:broadcast"));
			});
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}

	@Test public void recoveredFeedClearsErrorWithUnchangedSequence() throws Exception
	{
		FederationPanel[] panel = {null};
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		try
		{
			panel[0].update(
				Collections.singletonMap("member", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {});
			long now = Instant.now().getEpochSecond();
			EmpireCommunicationsClient.Snapshot feed = new EmpireCommunicationsClient.Snapshot(
				now, 0, Collections.emptyList(), Collections.emptyList(), null);
			panel[0].displayCommunications(feed, () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)named(panel[0], "holyEmpireAnnouncementsTab")).doClick();
				assertNull(named(panel[0], "holyEmpireAnnouncementsFeedUnavailable"));
			});
			panel[0].failCommunications("Temporary network error", () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JTextArea warning =
					(JTextArea)named(panel[0], "holyEmpireAnnouncementsFeedUnavailable");
				assertNotNull("Temporary error must be visible", warning);
				assertTrue(warning.getText().contains("Feed unavailable"));
			});
			// A successful retry with the exact same sequence must redraw
			// the Announcements tab to clear the stale red error.
			panel[0].displayCommunications(feed, () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNull("The stale error survives an unchanged feed",
						   named(panel[0], "holyEmpireAnnouncementsFeedUnavailable"));
			});
			panel[0].failCommunications("Temporary network error", () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			panel[0].displayCommunications(
				new EmpireCommunicationsClient.Snapshot(now + 30, 0, Collections.emptyList(),
														Collections.emptyList(), null),
				() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertNull(named(panel[0], "holyEmpireAnnouncementsFeedUnavailable")));
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}

	@Test
	public void emergencyTickerPersistsAcrossEveryPageAndAnnouncementsArePlainText()
		throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		try
		{
			panel[0].update(
				Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			panel[0].setMembershipConfirmed(true);
			SwingUtilities.invokeAndWait(() -> {});
			long now = Instant.now().getEpochSecond();
			EmpireCommunicationsClient.Notice news = new EmpireCommunicationsClient.Notice(
				1, "announcement", "<b>Important</b>", "Test <script>", now, 0);
			EmpireCommunicationsClient.Notice emergency = new EmpireCommunicationsClient.Notice(
				2, "emergency", "Security Alert", "Check the clan Discord", now, now + 7200);
			EmpireCommunicationsClient.Snapshot feed = new EmpireCommunicationsClient.Snapshot(
				now, 2, List.of(news), List.of(emergency), emergency);
			SwingUtilities.invokeAndWait(() -> {
				EmpireEmergencyTicker local = new EmpireEmergencyTicker();
				local.setWarning(emergency);
				assertTrue("Standalone ticker should show", local.isVisible());
				local.stopTicker();
			});
			panel[0].displayCommunications(feed, () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				Component ticker = named(panel[0], "holyEmpireEmergencyTicker");
				assertNotNull(ticker);
				assertTrue("ticker=" + ((EmpireEmergencyTicker)ticker).currentWarning() +
							   " visible=" + ticker.isVisible(),
						   ticker.isVisible());
				assertTrue(
					((EmpireEmergencyTicker)ticker).currentWarning().contains("Security Alert"));
				((JToggleButton)named(panel[0], "holyEmpireAnnouncementsTab")).doClick();
				JTextArea title = (JTextArea)named(panel[0], "holyEmpireAnnouncementTitle:1");
				assertNotNull(title);
				assertEquals("<b>Important</b>", title.getText());
				assertEquals(
					"Test <script>",
					((JTextArea)named(panel[0], "holyEmpireAnnouncementBody:1")).getText());
				((JToggleButton)named(panel[0], "holyEmpireRosterTab")).doClick();
				assertTrue("Emergency ticker persists on Roster",
						   named(panel[0], "holyEmpireEmergencyTicker").isVisible());
				((JToggleButton)named(panel[0], "holyEmpireEventsTab")).doClick();
				assertTrue("Emergency ticker persists on Events",
						   named(panel[0], "holyEmpireEmergencyTicker").isVisible());
			});
			panel[0].displayCommunications(
				new EmpireCommunicationsClient.Snapshot(now, 3, List.of(news),
														Collections.emptyList(), null),
				() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertFalse("Emergency banner clears after expiry or removal",
								   named(panel[0], "holyEmpireEmergencyTicker").isVisible()));
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}

	@Test public void emergencyTickerRequiresProvenCurrentMembership() throws Exception
	{
		FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		try
		{
			long now = Instant.now().getEpochSecond();
			EmpireCommunicationsClient.Notice emergency = new EmpireCommunicationsClient.Notice(
				50, "emergency", "Test Alert", "For confirmed members only", now, now + 3600);
			panel[0].displayCommunications(new EmpireCommunicationsClient.Snapshot(
											   now, 50, List.of(), List.of(emergency), emergency),
										   () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertFalse(named(panel[0], "holyEmpireEmergencyTicker").isVisible()));
			panel[0].update(
				Collections.singletonMap("member", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertFalse(named(panel[0], "holyEmpireEmergencyTicker").isVisible()));
			panel[0].setMembershipConfirmed(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertTrue(named(panel[0], "holyEmpireEmergencyTicker").isVisible()));
			panel[0].setMembershipConfirmed(false);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertFalse(named(panel[0], "holyEmpireEmergencyTicker").isVisible()));
			panel[0].setMembershipConfirmed(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertTrue(named(panel[0], "holyEmpireEmergencyTicker").isVisible()));
			panel[0].showNotVerified();
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertFalse(named(panel[0], "holyEmpireEmergencyTicker").isVisible()));
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}

	@Test public void scopedBroadcastsAndFullWidthEmergencyButtons() throws Exception
	{
		FederationPanel[] panel = new FederationPanel[1];
		List<String> audiences = new ArrayList<>();
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setAdminPublishing((k, t, b) -> {}, () -> true, () -> {});
			panel[0].setBroadcastScopePublisher((kind, title, body, scope) -> audiences.add(scope));
			panel[0].setEmergencyActions(action -> {});
		});
		try
		{
			panel[0].update(
				Collections.singletonMap("Owner", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			panel[0].setAdminEligible(true);
			panel[0].setBroadcastEligible(true);
			long now = Instant.now().getEpochSecond();
			EmpireCommunicationsClient.Notice emergency = new EmpireCommunicationsClient.Notice(
				5, "emergency", "Alert", "Warning", now, now + 3600);
			panel[0].displayCommunications(new EmpireCommunicationsClient.Snapshot(
											   now, 5, List.of(), List.of(emergency), emergency),
										   () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)named(panel[0], "holyEmpireAdminControlsTab")).doClick();
				((JToggleButton)named(panel[0], "holyEmpireAdminBroadcastsSubtab")).doClick();
				JComboBox<?> combo = (JComboBox<?>)named(panel[0], "holyEmpireBroadcastAudience");
				assertEquals(3, combo.getItemCount());
				assertEquals("Chapter Command", combo.getItemAt(0));
				assertEquals("All Admins", combo.getItemAt(1));
				assertEquals("Empire-Wide", combo.getItemAt(2));
				((JTextArea)named(panel[0], "holyEmpireAdminBody:broadcast"))
					.setText("Test broadcast");
				for (int i = 0; i < 3; i++)
				{
					combo.setSelectedIndex(i);
					((JButton)named(panel[0], "holyEmpireAdminPublish:broadcast")).doClick();
				}
				JButton end = (JButton)named(panel[0], "holyEmpireEndEmergency");
				JButton cancel = (JButton)named(panel[0], "holyEmpireCancelEmergency");
				assertEquals("End Emergency", end.getText());
				assertEquals("Cancel Quietly", cancel.getText());
				assertSame(end.getParent(), cancel.getParent());
				assertTrue(end.getParent().getLayout() instanceof BoxLayout);
				assertEquals(Integer.MAX_VALUE, end.getMaximumSize().width);
				assertEquals(Integer.MAX_VALUE, cancel.getMaximumSize().width);
			});
			assertEquals(List.of("chapter_command", "all_admins", "empire"), audiences);
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}
}
