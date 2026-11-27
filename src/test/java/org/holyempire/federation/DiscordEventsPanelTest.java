/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Container;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordEventsPanelTest
{
	@Test public void rsvpOnlyPayloadChangesCanUpdateCardsWithoutWholePageRefresh() throws Exception
	{
		java.time.Instant future = java.time.Instant.now().plusSeconds(1800);
		DiscordEventsClient.Event before = new DiscordEventsClient.Event(
			"chapter", "Holy Chaos", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			"Chapter Test", "Notes", "scheduled", "W465", future,
			future.plusSeconds(3600), "https://discord.com/channels/1/2/3",
			"", 0, 0, 0, "", 0);
		DiscordEventsClient.Event after = new DiscordEventsClient.Event(
			"chapter", "Holy Chaos", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			"Chapter Test", "Notes", "scheduled", "W465", future,
			future.plusSeconds(3600), "https://discord.com/channels/1/2/3",
			"", 1, 0, 0, "going", 1);
		DiscordEventsClient.Event renamed = new DiscordEventsClient.Event(
			"chapter", "Holy Chaos", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
			"Renamed", "Notes", "scheduled", "W465", future,
			future.plusSeconds(3600), "https://discord.com/channels/1/2/3",
			"", 1, 0, 0, "going", 1);
		java.lang.reflect.Method only = FederationPanel.class.getDeclaredMethod(
			"rsvpOnlyFeedChange", java.util.List.class, java.util.List.class);
		only.setAccessible(true);
		org.junit.Assert.assertTrue((Boolean)only.invoke(null,
			java.util.List.of(before), java.util.List.of(after)));
		org.junit.Assert.assertFalse((Boolean)only.invoke(null,
			java.util.List.of(before), java.util.List.of(renamed)));
	}

	@Test public void personalRsvpSymbolMappingIsExclusive()
	{
		assertEquals("✓", FederationPanel.personalEventRsvpMark("going"));
		assertEquals("?", FederationPanel.personalEventRsvpMark("maybe"));
		assertEquals("✕", FederationPanel.personalEventRsvpMark("declined"));
		assertEquals("", FederationPanel.personalEventRsvpMark(""));
		assertEquals("", FederationPanel.personalEventRsvpMark(null));
	}

	@Test public void expandedEmpireBellMatchesPersonalDiscordInterest() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		String id = "234567890123456789";
		try
		{
			SwingUtilities.invokeAndWait(() ->
				((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			for (String rsvp : new String[]{"going", "", "declined"})
			{
				Instant start = Instant.now().plusSeconds(7200);
				DiscordEventsClient.Event event = new DiscordEventsClient.Event(
					"empire", "", id, "Weekly Empire Meeting", "", "scheduled",
					"World 465", start, start.plusSeconds(3600),
					"https://discord.com/events/736564064834289774/" + id,
					"", 0, 0, 0, rsvp, 42);
				panel.displayEvents(new DiscordEventsClient.Snapshot(
					start.getEpochSecond(), List.of(event)), () -> true);
				SwingUtilities.invokeAndWait(() -> {});
				SwingUtilities.invokeAndWait(() -> {
					JButton link = (JButton)named(panel, "holyEmpireEventRSVP:" + id);
					JLabel bell = (JLabel)named(panel, "holyEmpireEventDiscordBell:" + id);
					Container actions = (Container)named(panel, "holyEmpireEventDiscordActionRow:" + id);
					assertNotNull("Official Discord event button remains", link);
					assertNotNull("Expanded event has a Discord bell status box", bell);
					assertNotNull("Button and bell have a dedicated row", actions);
					assertSame("Button and bell share a horizontal action row",
						link.getParent(), bell.getParent());
					assertSame(actions, bell.getParent());
					assertSame(link, actions.getComponent(0));
					assertSame(bell, actions.getComponent(1));
					assertEquals("Event Details / RSVP", link.getText());
					assertEquals("https://discord.com/events/736564064834289774/" + id,
						link.getClientProperty("holyEmpireEventURL"));
					assertEquals(link.getPreferredSize().height, bell.getPreferredSize().height);
					assertNotNull("Bell is drawn as a Swing icon", bell.getIcon());
					assertEquals(20, bell.getIcon().getIconWidth());
					assertEquals(20, bell.getIcon().getIconHeight());
					javax.swing.border.LineBorder border =
						(javax.swing.border.LineBorder)bell.getBorder();
					if ("going".equals(rsvp))
					{
						assertEquals(HolyEmpireTheme.RSVP_GOING_EDGE, border.getLineColor());
						assertEquals(2, border.getThickness());
						assertTrue(bell.getToolTipText().contains("Discord: You marked Interested"));
					}
					else
					{
						assertEquals(HolyEmpireTheme.SILVER_EDGE, border.getLineColor());
						assertEquals(1, border.getThickness());
						assertTrue(bell.getToolTipText().contains("Discord: Interested status not confirmed"));
					}
					assertEquals("going".equals(rsvp) ? "✓" : "",
						((JLabel)named(panel, "holyEmpireEventPersonalRsvp:" + id)).getText());
				});
			}
		}
		finally { panel.stopPrivateCacheTimer(); }
	}

	@Test public void collapsedChapterRsvpSymbolsUseTheirOwnRightHandColumn() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		String id = "abcdef0123456789abcdef0123456789";
		try
		{
			panel.setChapterEventsScope("Holy Chaos");
			panel.setChapterRsvpHandler((eventId, choice) -> {}, () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() ->
				((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			String[] responses = {"going", "maybe", "declined"};
			String[] symbols = {"✓", "?", "✕"};
			java.awt.Color[] colors = {HolyEmpireTheme.RSVP_GOING_EDGE,
				HolyEmpireTheme.GOLD, HolyEmpireTheme.RSVP_DECLINED_EDGE};
			for (int i = 0; i < responses.length; i++)
			{
				Instant start = Instant.now().plusSeconds(7200);
				DiscordEventsClient.Event event = new DiscordEventsClient.Event(
					"chapter", "Holy Chaos", id, "A very long event title",
					"Details", "scheduled", "World 465", start,
					start.plusSeconds(3600), "https://discord.com/channels/1/2/3",
					"", 1, 0, 0, responses[i]);
				panel.displayChapterEvents(List.of(event), () -> true);
				SwingUtilities.invokeAndWait(() -> {});
				final int index = i;
				SwingUtilities.invokeAndWait(() -> {
					JButton toggle = (JButton)named(panel, "holyEmpireEventCollapse:" + id);
					assertNotNull(toggle);
					if (((javax.swing.JPanel)named(panel,
						"holyEmpireEventExpanded:" + id)).isVisible()) toggle.doClick();
					JLabel badge = (JLabel)named(panel, "holyEmpireEventPersonalRsvp:" + id);
					assertNotNull(badge);
					assertNull("Only Empire scheduled events display the Discord bell",
						named(panel, "holyEmpireEventDiscordBell:" + id));
					assertEquals(symbols[index], badge.getText());
					assertEquals(colors[index], badge.getForeground());
					assertTrue(badge.isVisible());
					java.awt.Container footer = badge.getParent();
					assertTrue(footer.getLayout() instanceof java.awt.BorderLayout);
					assertSame(badge, ((java.awt.BorderLayout)footer.getLayout())
						.getLayoutComponent(java.awt.BorderLayout.EAST));
					assertSame(named(panel, "holyEmpireEventCompactDate:" + id),
						((java.awt.BorderLayout)footer.getLayout())
						.getLayoutComponent(java.awt.BorderLayout.CENTER));
					java.awt.Container header = toggle.getParent().getParent();
					assertSame(toggle.getParent(), ((java.awt.BorderLayout)header.getLayout())
						.getLayoutComponent(java.awt.BorderLayout.EAST));
				});
			}
		}
		finally { panel.stopPrivateCacheTimer(); }
	}

	@Test public void attendanceStartsOnlyWithinOneHourOrDuringEvent()
	{
		Instant start = Instant.parse("2026-12-02T19:00:00Z");
		Instant end = start.plusSeconds(3600);
		assertFalse(FederationPanel.eventAttendanceWindow(
			start, end, start.minusSeconds(3601)));
		assertTrue(FederationPanel.eventAttendanceWindow(
			start, end, start.minusSeconds(3600)));
		assertTrue(FederationPanel.eventAttendanceWindow(start, end, start));
		assertFalse(FederationPanel.eventAttendanceWindow(start, end, end));
		assertFalse(FederationPanel.eventAttendanceWindow(null, end, start));
	}

	@Test public void eachEventCanCollapseToTypeTitleAndDateAndRestoreSavedChoice()
		throws Exception
	{
		final java.util.List<String> persisted = new java.util.ArrayList<>();
		String id = "555555555555555555";
		Instant now = Instant.now();
		DiscordEventsClient.Event event = new DiscordEventsClient.Event(
			id, "Long Title for a Sacred Blade Empire Event", "Some very long details",
			"scheduled", "W465", now.plusSeconds(7200), now.plusSeconds(10800),
			"https://discord.com/events/123456789012345678/" + id);
		FederationPanel panel = verified(new int[1]);
		try
		{
			SwingUtilities.invokeAndWait(() -> {
				panel.setEventCollapsePersistence("", persisted::add);
				((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick();
			});
			panel.displayEvents(new DiscordEventsClient.Snapshot(now.getEpochSecond(),
				List.of(event)), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				javax.swing.JPanel expanded = (javax.swing.JPanel)named(panel,
					"holyEmpireEventExpanded:" + id);
				JLabel date = (JLabel)named(panel, "holyEmpireEventCompactDate:" + id);
				assertNotNull(expanded);
				assertTrue(expanded.isVisible());
				assertFalse(date.isVisible());
				JButton toggle = (JButton)named(panel, "holyEmpireEventCollapse:" + id);
				toggle.doClick();
				assertFalse(expanded.isVisible());
				assertTrue(date.isVisible());
				assertTrue(persisted.contains("empire:" + id));
				assertEquals(event.name, ((JTextArea)named(panel,
					"holyEmpireEventName:" + id)).getText());
			});
		}
		finally { panel.stopPrivateCacheTimer(); }
		FederationPanel restored = verified(new int[1]);
		try
		{
			SwingUtilities.invokeAndWait(() -> {
				restored.setEventCollapsePersistence(persisted.get(persisted.size()-1), v -> {});
				((JToggleButton)named(restored, "holyEmpireEventsTab")).doClick();
			});
			restored.displayEvents(new DiscordEventsClient.Snapshot(now.getEpochSecond(),
				List.of(event)), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> assertFalse(
				((javax.swing.JPanel)named(restored, "holyEmpireEventExpanded:" + id)).isVisible()));
		}
		finally { restored.stopPrivateCacheTimer(); }
	}

	private static Component named(Component node, String name)
	{
		if (name.equals(node.getName()))
		{
			return node;
		}
		if (node instanceof Container)
		{
			for (Component child : ((Container)node).getComponents())
			{
				Component found = named(child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static FederationPanel verified(int[] fetches) throws Exception
	{
		final FederationPanel[] holder = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			holder[0] = new FederationPanel();
			holder[0].setEventsLoader(() -> fetches[0]++);
		});
		holder[0].update(
			Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
			Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		return holder[0];
	}

	private static DiscordEventsClient.Snapshot example()
	{
		Instant now = Instant.now().plusSeconds(3600);
		DiscordEventsClient.Event event = new DiscordEventsClient.Event(
			"empire", "", "234567890123456789", "<b>Clan Mass</b>", "Bring your best gear",
			"scheduled", "World 465", now, now.plusSeconds(7200),
			"https://discord.com/events/123456789012345678/234567890123456789", "", 0, 0, 0, "",
			25);
		return new DiscordEventsClient.Snapshot(now.getEpochSecond(), List.of(event));
	}

	@Test public void routineDiscordSuccessIsNotAnEventsStatusLine() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		try
		{
			panel.setChapterEventsScope("Holy Chaos");
			panel.setChapterRsvpHandler((eventId, choice) -> {}, () -> true);
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			panel.rsvpResult("Discord verified as HE Test Chapter Member. "
								 + "Chapter RSVPs are enabled.",
							 () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertNull("Successful connection must not leave a banner",
								  named(panel, "holyEmpireRsvpStatus")));
			panel.rsvpResult("RSVP updated successfully.", () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JTextArea status = (JTextArea)named(panel, "holyEmpireRsvpStatus");
				assertNotNull("RSVP feedback must remain visible", status);
				assertEquals("RSVP updated successfully.", status.getText());
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void identicalEmpireRefreshAndEmptyChapterLoadClearProgress() throws Exception
	{
		int[] empireFetches = {0}, chapterFetches = {0};
		FederationPanel panel = verified(empireFetches);
		try
		{
			panel.setChapterEventsHandlers(code -> {}, () -> chapterFetches[0]++);
			panel.setChapterEventsScope("Holy Chaos");
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick();
				assertNotNull(named(panel, "holyEmpireEventsLoading"));
				assertNotNull(named(panel, "holyEmpireChapterEventsLoading"));
			});
			panel.displayEvents(example(), () -> true);
			panel.displayChapterEvents(Collections.emptyList(), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNull("Empire loading must clear on success",
						   named(panel, "holyEmpireEventsLoading"));
				assertNull("An empty chapter feed must still clear loading",
						   named(panel, "holyEmpireChapterEventsLoading"));
				assertNotNull(named(panel, "holyEmpireEvent:234567890123456789"));
				assertEquals(1, empireFetches[0]);
				assertEquals(1, chapterFetches[0]);
				((JButton)named(panel, "holyEmpireEventsRefresh")).doClick();
				// The chapter fetch displays its progress alongside existing
				// cards, and also exposes the pending Empire refresh.
				assertNotNull(named(panel, "holyEmpireEventsLoading"));
				assertNotNull(named(panel, "holyEmpireChapterEventsLoading"));
			});
			panel.displayEvents(example(), () -> true);
			panel.displayChapterEvents(Collections.emptyList(), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertEquals(2, empireFetches[0]);
				assertEquals(2, chapterFetches[0]);
				assertNull("Identical Empire response must clear progress",
						   named(panel, "holyEmpireEventsLoading"));
				assertNull("Repeated empty chapter response must clear progress",
						   named(panel, "holyEmpireChapterEventsLoading"));
				assertNotNull("Existing cards remain after unchanged refresh",
							  named(panel, "holyEmpireEvent:234567890123456789"));
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void refreshingEventsDoesNotMoveTheViewport() throws Exception
	{
		int[] fetches = {0};
		FederationPanel panel = verified(fetches);
		final JScrollPane[] host = new JScrollPane[1];
		try
		{
			SwingUtilities.invokeAndWait(() -> {
				JScrollPane scroller = new JScrollPane(panel);
				panel.setPreferredSize(new Dimension(280, 1400));
				scroller.setSize(310, 240);
				scroller.doLayout();
				host[0] = scroller;
				((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick();
			});
			panel.displayEvents(example(), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				host[0].doLayout();
				host[0].getViewport().setViewPosition(new Point(0, 275));
				assertEquals(275, host[0].getViewport().getViewPosition().y);
				JButton refresh = (JButton)named(panel, "holyEmpireEventsRefresh");
				assertNotNull(refresh);
				assertFalse(refresh.isRequestFocusEnabled());
				refresh.doClick();
			});
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertEquals("Clicking Refresh must not scroll the Events page", 275,
									host[0].getViewport().getViewPosition().y));
			panel.displayEvents(example(), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertEquals("Completing event loading must not scroll the page", 275,
									host[0].getViewport().getViewPosition().y));
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void verifiedMemberSeesEventsTabAndRSVPWithoutOfficerAccess() throws Exception
	{
		int[] calls = {0};
		FederationPanel panel = verified(calls);
		try
		{
			SwingUtilities.invokeAndWait(() -> {
				JToggleButton events = (JToggleButton)named(panel, "holyEmpireEventsTab");
				assertNotNull(events);
				assertTrue(events.isVisible());
				assertFalse(((JToggleButton)named(panel, "holyEmpireBanListTab")).isVisible());
				events.doClick();
				assertTrue(events.isSelected());
				assertEquals(1, calls[0]);
			});
			panel.displayEvents(example(), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNotNull(named(panel, "holyEmpireEvent:234567890123456789"));
				JTextArea title = (JTextArea)named(panel, "holyEmpireEventName:234567890123456789");
				assertEquals("<b>Clan Mass</b>", title.getText());
				assertEquals("Interested: 25",
							 ((JLabel)named(panel, "holyEmpireEventInterested:234567890123456789"))
								 .getText());
				JButton rsvp = (JButton)named(panel, "holyEmpireEventRSVP:234567890123456789");
				assertNotNull(rsvp);
				assertEquals("https://discord.com/events/123456789012345678/234567890123456789",
							 rsvp.getClientProperty("holyEmpireEventURL"));
				((JToggleButton)named(panel, "holyEmpireRosterTab")).doClick();
				assertNotNull(named(panel, "holyEmpireGlobalSearch"));
				assertNull(named(panel, "holyEmpireEventRSVP:234567890123456789"));
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void chapterRsvpUsesVerifiedDiscordConnectionAndSkipsEmpire() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		java.util.concurrent.atomic.AtomicBoolean connected =
			new java.util.concurrent.atomic.AtomicBoolean(false);
		java.util.List<String> selections = new java.util.ArrayList<>();
		String chapterId = "abcdef0123456789abcdef0123456789";
		String post = "https://discord.com/channels/293070715706671104/"
					  + "1573933979667538010/167890123456789011";
		DiscordEventsClient.Event chapter = new DiscordEventsClient.Event(
			"chapter", "Holy Chaos", chapterId, "Chapter Practice", "Training", "scheduled",
			"World 465", Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), post, "",
			2, 1, 0, "maybe");
		try
		{
			panel.setChapterRsvpHandler(
				(id, choice) -> selections.add(id + ":" + choice), connected::get);
			panel.setDiscordConnector(() -> {});
			panel.setDiscordAuthorization(connected::get);
			panel.setChapterEventsScope("Holy Chaos");
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			panel.displayEvents(example(), () -> true);
			panel.displayChapterEvents(List.of(chapter), () -> true);
			SwingUtilities.invokeAndWait(() -> {
				JLabel total = (JLabel)named(panel, "holyEmpireEventInterested:" + chapterId);
				assertEquals("Interested: 3", total.getText());
				assertNull(named(panel, "holyEmpireEventRsvpCounts:" + chapterId));
				JButton toggle = (JButton)named(panel, "holyEmpireEventRsvpOpen:" + chapterId);
				assertNotNull(toggle);
				assertTrue(toggle.getText().contains("Maybe"));
				assertFalse(
					((JButton)named(panel, "holyEmpireEventRsvp:going:" + chapterId)).isEnabled());
				java.awt.Component options =
					named(panel, "holyEmpireEventRsvpOptions:" + chapterId);
				assertFalse(options.isVisible());
				toggle.doClick();
				assertTrue("RSVP choices expand on demand", options.isVisible());
				toggle.doClick();
				assertFalse("RSVP choices collapse on second click", options.isVisible());
				assertNull("Only one global verification button is allowed",
						   named(panel, "holyEmpireRsvpOneClickConnect"));
				assertTrue(((JButton)named(panel, "holyEmpireDiscordVerify")).isVisible());
				assertNull("Manual code verification was removed",
						   named(panel, "holyEmpireRsvpManualToggle"));
				assertNull(named(panel, "holyEmpireRsvpConnect"));
				assertNull(named(panel, "holyEmpireEventRsvp:going:234567890123456789"));
			});
			connected.set(true);
			panel.chapterEventsConnected(() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNull(named(panel, "holyEmpireRsvpOneClickConnect"));
				assertFalse("Verification control hides after authorization",
							((JButton)named(panel, "holyEmpireDiscordVerify")).isVisible());
				assertNull(named(panel, "holyEmpireRsvpConnect"));
				JButton toggle = (JButton)named(panel, "holyEmpireEventRsvpOpen:" + chapterId);
				assertNotNull(toggle);
				toggle.doClick();
				JButton going = (JButton)named(panel, "holyEmpireEventRsvp:going:" + chapterId);
				assertTrue(going.isEnabled());
				assertTrue(going.isVisible());
				going.doClick();
			});
			assertEquals(List.of(chapterId + ":going"), selections);
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void rsvpOutlineFollowsGoingAndDeclinedWhilePostButtonIsCentered() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		String post = "https://discord.com/channels/293070715706671104/"
					  + "1573933979667538010/167890123456789011";
		String goingId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
		String declinedId = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
		Instant time = Instant.now().plusSeconds(3600);
		DiscordEventsClient.Event going = new DiscordEventsClient.Event(
			"chapter", "Holy Chaos", goingId, "Going Event", "", "scheduled", "", time,
			time.plusSeconds(3600), post, "", 3, 2, 0, "going");
		DiscordEventsClient.Event declined = new DiscordEventsClient.Event(
			"chapter", "Holy Chaos", declinedId, "Declined Event", "", "scheduled", "", time,
			time.plusSeconds(3600), post, "", 1, 1, 2, "declined");
		try
		{
			panel.setChapterEventsScope("Holy Chaos");
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			panel.displayEvents(example(), () -> true);
			panel.displayChapterEvents(List.of(going, declined), () -> true);
			SwingUtilities.invokeAndWait(() -> {
				for (String id : new String[] {goingId, declinedId})
				{
					Component row = named(panel, "holyEmpireEventDiscordPostRow:" + id);
					assertNotNull("View Discord Post has its own centered row", row);
					assertEquals(
						java.awt.FlowLayout.CENTER,
						((java.awt.FlowLayout)((Container)row).getLayout()).getAlignment());
					assertNotNull(named(panel, "holyEmpireEventDiscordPost:" + id));
				}
				JButton green = (JButton)named(panel, "holyEmpireEventRsvpOpen:" + goingId);
				JButton red = (JButton)named(panel, "holyEmpireEventRsvpOpen:" + declinedId);
				assertNotNull(green);
				assertNotNull(red);
				javax.swing.border.LineBorder goingBorder =
					(javax.swing.border.LineBorder)((javax.swing.border.CompoundBorder)
														green.getBorder())
						.getOutsideBorder();
				javax.swing.border.LineBorder declinedBorder =
					(javax.swing.border.LineBorder)((javax.swing.border.CompoundBorder)
														red.getBorder())
						.getOutsideBorder();
				assertEquals(HolyEmpireTheme.RSVP_GOING_EDGE, goingBorder.getLineColor());
				assertEquals(HolyEmpireTheme.RSVP_DECLINED_EDGE, declinedBorder.getLineColor());
				assertEquals(2, goingBorder.getThickness());
				assertEquals(2, declinedBorder.getThickness());
				for (java.awt.event.MouseListener listener : green.getMouseListeners())
				{
					listener.mouseEntered(new java.awt.event.MouseEvent(
						green, java.awt.event.MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(),
						0, 0, 0, 0, false));
				}
				javax.swing.border.LineBorder hover =
					(javax.swing.border.LineBorder)((javax.swing.border.CompoundBorder)
														green.getBorder())
						.getOutsideBorder();
				assertEquals("Hover preserves RSVP green status", HolyEmpireTheme.RSVP_GOING_EDGE,
							 hover.getLineColor());
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test
	public void historyOpensInNestedEventsScreenWithoutImagesAndHonorsSettings() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		String oldId = "100000000000000001";
		String futureId = "100000000000000002";
		Instant now = Instant.now();
		DiscordEventsClient.Event old = new DiscordEventsClient.Event(
			"chapter", "Holy Chaos", oldId, "Past Castle Wars", "Completed event", "scheduled",
			"World 465", now.minusSeconds(40L * 86400), now.minusSeconds(40L * 86400 - 3600), "",
			"https://cdn.discordapp.com/attachments/1/2/image.png");
		DiscordEventsClient.Event future = new DiscordEventsClient.Event(
			futureId, "Future Clan Mass", "", "scheduled", "", now.plusSeconds(3600),
			now.plusSeconds(7200), "https://discord.com/events/123456789012345678/" + futureId);
		try
		{
			panel.setChapterEventsScope("Holy Chaos");
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			panel.displayEvents(
				new DiscordEventsClient.Snapshot(now.getEpochSecond(), List.of(future)),
				() -> true);
			panel.displayChapterEvents(List.of(old), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNotNull(named(panel, "holyEmpireEvent:" + futureId));
				assertNull(named(panel, "holyEmpireHistoryEvent:" + oldId));
				assertNull("History is a separate nested Events screen",
						   named(panel, "holyEmpireEventHistoryEmpty"));
				assertNull(named(panel, "holyEmpireEventHistoryWindow"));
				assertNotNull(named(panel, "holyEmpireEventsHistoryOpen"));
				((JButton)named(panel, "holyEmpireEventsHistoryOpen")).doClick();
				assertNull("Upcoming events are hidden in History",
						   named(panel, "holyEmpireEvent:" + futureId));
				assertNotNull(named(panel, "holyEmpireEventHistoryEmpty"));
				assertNotNull(named(panel, "holyEmpireEventsHistoryBack"));
				assertEquals("Past 30 days · Change under Holy Empire settings",
							 ((javax.swing.JTextArea)named(panel, "holyEmpireEventHistoryWindow"))
								 .getText());
			});
			panel.setEventHistoryDays(60);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNotNull(named(panel, "holyEmpireHistoryEvent:" + oldId));
				assertNull("Historical event never loads an image preview",
						   named(panel, "holyEmpireEventImagePreview:" + oldId));
				assertNull("No event-image button for historical event",
						   named(panel, "holyEmpireEventImage:" + oldId));
				assertNull("History not mixed into upcoming",
						   named(panel, "holyEmpireEvent:" + oldId));
			});
			panel.setEventHistoryDays(14);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNull(named(panel, "holyEmpireHistoryEvent:" + oldId));
				((JButton)named(panel, "holyEmpireEventsHistoryBack")).doClick();
				assertNotNull(named(panel, "holyEmpireEvent:" + futureId));
				assertNull(named(panel, "holyEmpireEventHistoryWindow"));
				assertNotNull(named(panel, "holyEmpireEventsHistoryOpen"));
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void logoutClearsEventDetailsAndGatesEventsTab() throws Exception
	{
		int[] calls = {0};
		FederationPanel panel = verified(calls);
		try
		{
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			panel.displayEvents(example(), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			panel.showNotVerified();
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertFalse(
					((JToggleButton)named(panel, "holyEmpireEventsTab")).getParent().isVisible());
				assertNull(named(panel, "holyEmpireEvent:234567890123456789"));
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void empireEventsAndScopedChapterEventsAreDistinctOnSamePage() throws Exception
	{
		int[] publicLoads = {0};
		int[] privateLoads = {0};
		FederationPanel panel = verified(publicLoads);
		try
		{
			panel.setChapterEventsHandlers(code -> {}, () -> privateLoads[0]++);
			panel.setChapterEventsScope("Holy Chaos");
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			panel.displayEvents(example(), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNull("Synced-roster members need no Discord verification",
						   named(panel, "holyEmpireChapterEventsConnect"));
				assertNull(named(panel, "holyEmpireChapterEventCode"));
				assertNull("Unverified chapter members must not see chapter events",
						   named(panel, "holyEmpireEvent:abcdef0123456789abcdef0123456789"));
				assertNull("Nonofficers may not create chapter events",
						   named(panel, "holyEmpireChapterEventCreate"));
				JLabel label = (JLabel)named(panel, "holyEmpireEventScope:234567890123456789");
				assertEquals("EMPIRE EVENT", label.getText());
			});
			// A synchronized member's chapter events load automatically
			// when opening Events; no Discord connect action is required.
			assertEquals(1, privateLoads[0]);
			DiscordEventsClient.Event chapter = new DiscordEventsClient.Event(
				"chapter", "Holy Chaos", "abcdef0123456789abcdef0123456789", "Private Castle Wars",
				"Only our chapter", "scheduled", "World 465", Instant.parse("2026-12-01T19:00:00Z"),
				Instant.parse("2026-12-01T20:00:00Z"), "");
			panel.displayChapterEvents(List.of(chapter), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JLabel label =
					(JLabel)named(panel, "holyEmpireEventScope:abcdef0123456789abcdef0123456789");
				assertNotNull(label);
				assertEquals("CHAPTER EVENT", label.getText());
				assertEquals("Holy Chaos", ((JLabel)named(panel,
					"holyEmpireEventChapterHeading:abcdef0123456789abcdef0123456789")).getText());
				assertNull("Chapter events are not Discord scheduled events",
						   named(panel, "holyEmpireEventRSVP:abcdef0123456789abcdef0123456789"));
				assertNotNull("Discord Empire events keep their RSVP links",
							  named(panel, "holyEmpireEventRSVP:234567890123456789"));
			});
			// Even a forged event list for another chapter is not displayed.
			DiscordEventsClient.Event foreign = new DiscordEventsClient.Event(
				"chapter", "Holy Kingdom", "0123456789abcdef0123456789abcdef", "Foreign event", "",
				"scheduled", "", Instant.parse("2026-12-01T19:00:00Z"),
				Instant.parse("2026-12-01T20:00:00Z"), "");
			panel.displayChapterEvents(List.of(foreign), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertNull(named(panel, "holyEmpireEvent:0123456789abcdef0123456789abcdef")));
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void empireGoldChapterSilverAndConfirmedDiscordPostLink() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		try
		{
			panel.setChapterEventsScope("Holy Chaos");
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			panel.displayEvents(example(), () -> true);
			String id = "abcdef0123456789abcdef0123456789";
			String post = "https://discord.com/channels/293070715706671104/"
						  + "1573933979667538010/167890123456789011";
			String image = "https://cdn.discordapp.com/attachments/123/456/image.png";
			DiscordEventsClient.Event event = new DiscordEventsClient.Event(
				"chapter", "Holy Chaos", id, "Chapter Mass", "Bring supplies", "scheduled",
				"World 465", Instant.parse("2026-12-01T19:00:00Z"),
				Instant.parse("2026-12-01T20:00:00Z"), post, image);
			panel.displayChapterEvents(List.of(event), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JLabel empire = (JLabel)named(panel, "holyEmpireEventScope:234567890123456789");
				JLabel chapter = (JLabel)named(panel, "holyEmpireEventScope:" + id);
				JTextArea empireTitle =
					(JTextArea)named(panel, "holyEmpireEventName:234567890123456789");
				JTextArea chapterTitle = (JTextArea)named(panel, "holyEmpireEventName:" + id);
				assertEquals(HolyEmpireTheme.GOLD, empire.getForeground());
				assertEquals(HolyEmpireTheme.GOLD, empireTitle.getForeground());
				assertEquals(HolyEmpireTheme.SILVER, chapter.getForeground());
				assertEquals(HolyEmpireTheme.SILVER, chapterTitle.getForeground());
				JButton link = (JButton)named(panel, "holyEmpireEventDiscordPost:" + id);
				assertNotNull("Chapter announcement has its real Discord link", link);
				assertEquals(post, link.getClientProperty("holyEmpireEventURL"));
				assertNull("The separate View Event Image control was removed",
						   named(panel, "holyEmpireEventImage:" + id));
				assertNotNull("Images must be embedded in the event card",
							  named(panel, "holyEmpireEventImagePreview:" + id));
				assertEquals(18f, chapterTitle.getFont().getSize2D(), 0.05f);
				assertNull("Chapter events are not Discord scheduled RSVP events",
						   named(panel, "holyEmpireEventRSVP:" + id));
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void imageFillsCardWidthWithoutChangingAspectRatio()
	{
		java.awt.image.BufferedImage pixels =
			new java.awt.image.BufferedImage(210, 105, java.awt.image.BufferedImage.TYPE_INT_RGB);
		javax.swing.ImageIcon source = new javax.swing.ImageIcon(pixels);
		javax.swing.ImageIcon resized = FederationPanel.fitEventCardImage(source, 382);
		assertNotNull(resized);
		assertEquals(382, resized.getIconWidth());
		assertEquals(191, resized.getIconHeight());
		javax.swing.ImageIcon reflow = FederationPanel.fitEventCardImage(source, 298);
		assertEquals(298, reflow.getIconWidth());
		assertEquals(149, reflow.getIconHeight());
	}

	@Test public void eventImageBeforeTimeCenteredAndAllEventTextEnlarged() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		String id = "234567890123456789";
		try
		{
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			Instant when = Instant.now().plusSeconds(3600);
			String url = "https://roster.holyempireosrs.cc/v2/event-images/" + id +
						 "/0123456789abcdef0123456789abcdef.png";
			DiscordEventsClient.Event event = new DiscordEventsClient.Event(
				"empire", "", id, "Pest Control Event", "Bring supplies", "scheduled",
				"World 344 Pest Control (Not Needed) FC", when, when.plusSeconds(3600),
				"https://discord.com/events/123456789012345678/" + id, url);
			panel.displayEvents(
				new DiscordEventsClient.Snapshot(when.getEpochSecond(), List.of(event)),
				() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JLabel image = (JLabel)named(panel, "holyEmpireEventImagePreview:" + id);
				JLabel time = (JLabel)named(panel, "holyEmpireEventTime:" + id);
				JTextArea location = (JTextArea)named(panel, "holyEmpireEventLocation:" + id);
				JTextArea title = (JTextArea)named(panel, "holyEmpireEventName:" + id);
				JTextArea body = (JTextArea)named(panel, "holyEmpireEventDescription:" + id);
				JLabel scope = (JLabel)named(panel, "holyEmpireEventScope:" + id);
				JLabel interested = (JLabel)named(panel, "holyEmpireEventInterested:" + id);
				JButton link = (JButton)named(panel, "holyEmpireEventRSVP:" + id);
				assertNotNull(image);
				assertNotNull(time);
				assertNotNull(location);
				assertNotNull(body);
				assertNotNull(link);
				assertSame(time.getParent(), location.getParent());
				assertSame(image.getParent(), time.getParent());
				List<Component> children =
					java.util.Arrays.asList(image.getParent().getComponents());
				assertTrue(children.indexOf(image) < children.indexOf(time));
				assertTrue(children.indexOf(time) < children.indexOf(location));
				assertEquals(javax.swing.SwingConstants.CENTER, image.getHorizontalAlignment());
				assertEquals(Integer.MAX_VALUE, image.getMaximumSize().width);
				assertEquals(Component.LEFT_ALIGNMENT, time.getAlignmentX(), 0f);
				assertEquals(Component.LEFT_ALIGNMENT, location.getAlignmentX(), 0f);
				assertEquals(0, location.getInsets().left);
				assertEquals(13f, scope.getFont().getSize2D(), .05f);
				assertEquals(12f, interested.getFont().getSize2D(), .05f);
				assertEquals(18f, title.getFont().getSize2D(), .05f);
				assertEquals(SwingTextSafety.plainLabel("Date").getFont().getSize2D() + 2f,
							 time.getFont().getSize2D(), .05f);
				assertEquals(new JTextArea().getFont().getSize2D() + 2f,
							 location.getFont().getSize2D(), .05f);
				assertEquals(new JTextArea().getFont().getSize2D() + 2f, body.getFont().getSize2D(),
							 .05f);
				assertEquals(
					HolyEmpireTheme.button("Event Details / RSVP", false).getFont().getSize2D(),
					link.getFont().getSize2D(), .05f);
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void rosterMemberSeesEventsFromMultipleChaptersWithoutDiscord() throws Exception
	{
		int[] publicLoads = {0};
		int[] chapterLoads = {0};
		FederationPanel panel = verified(publicLoads);
		try
		{
			panel.setChapterEventsHandlers(code -> {}, () -> chapterLoads[0]++);
			panel.setChapterEventsScope("*");
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick();
				assertNull(named(panel, "holyEmpireChapterEventsConnect"));
				assertNull(named(panel, "holyEmpireChapterEventCode"));
			});
			assertEquals(1, chapterLoads[0]);
			DiscordEventsClient.Event chaos = new DiscordEventsClient.Event(
				"chapter", "Holy Chaos", "1234567890abcdef1234567890abcdef", "Chaos Practice",
				"Practice run", "scheduled", "World 465", Instant.parse("2026-12-01T19:00:00Z"),
				Instant.parse("2026-12-01T20:00:00Z"), "");
			DiscordEventsClient.Event kingdom = new DiscordEventsClient.Event(
				"chapter", "Holy Kingdom", "fedcba0987654321fedcba0987654321", "Kingdom Practice",
				"Practice run", "scheduled", "World 465", Instant.parse("2026-12-02T19:00:00Z"),
				Instant.parse("2026-12-02T20:00:00Z"), "");
			panel.displayChapterEvents(List.of(chaos, kingdom), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNotNull(named(panel, "holyEmpireEvent:1234567890abcdef1234567890abcdef"));
				assertNotNull(named(panel, "holyEmpireEvent:fedcba0987654321fedcba0987654321"));
				assertNull(named(panel, "holyEmpireChapterEventsConnect"));
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void editorNeverAppearsEvenForApprovedChapterOfficers() throws Exception
	{
		FederationPanel panel = verified(new int[1]);
		try
		{
			panel.setChapterEventsHandlers(code -> {}, () -> {});
			panel.setChapterEventsScope("Holy Chaos");
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			SwingUtilities.invokeAndWait(
				() -> assertNull(named(panel, "holyEmpireChapterEventCreate")));
			panel.setOfficerSettingsEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertNull("Discord is now the only chapter event authoring surface",
								  named(panel, "holyEmpireChapterEventCreate")));
			panel.setOfficerSettingsEligible(false);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> assertNull(named(panel, "holyEmpireChapterEventCreate")));
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}

	@Test public void eventLoadFailureProducesRetryControl() throws Exception
	{
		int[] calls = {0};
		FederationPanel panel = verified(calls);
		try
		{
			SwingUtilities.invokeAndWait(
				() -> ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
			panel.failEvents("test unavailable", () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertNotNull(named(panel, "holyEmpireEventsError"));
				JButton retry = (JButton)named(panel, "holyEmpireEventsRefresh");
				assertNotNull(retry);
				retry.doClick();
				assertEquals(2, calls[0]);
			});
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}
}
