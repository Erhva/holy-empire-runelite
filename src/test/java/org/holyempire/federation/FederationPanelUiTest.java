/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationPanelUiTest
{
	private static Component named(Container parent, String name)
	{
		for (Component child : parent.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component match = named((Container)child, name);
				if (match != null)
				{
					return match;
				}
			}
		}
		return null;
	}

	@Test public void chapterSearchKeepsItsFieldWhileFiltering() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		Map<String, List<String>> combined =
			Collections.singletonMap("captain", Collections.singletonList("Holy Chaos"));
		Map<String, List<WomDirectoryClient.ChapterMember>> chapters = new HashMap<>();
		chapters.put(
			"Holy Chaos",
			Arrays.asList(new WomDirectoryClient.ChapterMember("Owner Player", "owner"),
						  new WomDirectoryClient.ChapterMember("Captain", "captain"),
						  new WomDirectoryClient.ChapterMember("Bronze Player", "bronze")));
		panel[0].update(combined, chapters);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JTextField global = (JTextField)named(panel[0], "holyEmpireGlobalSearch");
			assertNotNull(global);
			Container before = global.getParent();
			global.setText("captain");
			assertSame(before, global.getParent());
			JButton chapter = (JButton)named(panel[0], "holyEmpireChapter:Holy Chaos");
			assertNotNull(chapter);
			chapter.doClick();
			JTextField search = (JTextField)named(panel[0], "holyEmpireChapterSearch");
			assertNotNull(search);
			Container chapterParent = search.getParent();
			search.setText("captain");
			assertSame(chapterParent, search.getParent());
			assertNotNull(named(panel[0], "holyEmpireChapterBack"));
		});
	}

	@Test
	public void delayedRosterUpdateCannotReenableMemberUiAfterNativeClanRevocation()
		throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		Map<String, List<String>> roster =
			Collections.singletonMap("alpha", Collections.singletonList("Holy Empire"));
		panel[0].update(roster, Collections.emptyMap(), false, () -> false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> { assertNull(named(panel[0], "holyEmpireChapter:Holy Empire")); });
		panel[0].update(roster, Collections.emptyMap(), false, () -> true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> { assertNotNull(named(panel[0], "holyEmpireChapter:Holy Empire")); });
	}

	@Test public void staleDenialAndLoadingEventsCannotEraseLaterVerifiedRoster() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		Map<String, List<String>> roster =
			Collections.singletonMap("alpha", Collections.singletonList("Holy Empire"));
		panel[0].update(roster, Collections.emptyMap(), false, () -> true);
		panel[0].showNotVerified(() -> false);
		panel[0].showMessage("Previous login checking...", () -> false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> { assertNotNull(named(panel[0], "holyEmpireChapter:Holy Empire")); });
		panel[0].showNotVerified(() -> true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> { assertNull(named(panel[0], "holyEmpireChapter:Holy Empire")); });
	}

	@Test public void officerSettingsAppearOnlyForVerifiedOwnerOrDeputy() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		final java.util.concurrent.atomic.AtomicBoolean connected =
			new java.util.concurrent.atomic.AtomicBoolean(false);
		final int[] connectCalls = {0};
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setDiscordConnector(() -> connectCalls[0]++);
			panel[0].setDiscordAuthorization(connected::get);
		});
		panel[0].update(Collections.singletonMap("alpha", Collections.singletonList("Holy Empire")),
						Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JButton global = (JButton)named(panel[0], "holyEmpireDiscordVerify");
			assertNotNull(global);
			assertTrue(global.isVisible());
			global.doClick();
			assertEquals(1, connectCalls[0]);
			assertNull(named(panel[0], "holyEmpireOfficerBanSettings"));
			assertNull(named(panel[0], "holyEmpireAdminConnectOwner"));
			assertNull(named(panel[0], "holyEmpireRsvpOneClickConnect"));
		});
		// The plugin grants this flag only after native rank + server role checks.
		panel[0].setOfficerSettingsEligible(true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JToggleButton bans = (JToggleButton)named(panel[0], "holyEmpireBanListTab");
			assertTrue(bans.isVisible());
			bans.doClick();
			assertNotNull(named(panel[0], "holyEmpireBanRefresh"));
			assertNull(named(panel[0], "holyEmpireOfficerBanSettings"));
			assertTrue(((JButton)named(panel[0], "holyEmpireDiscordVerify")).isVisible());
		});
		connected.set(true);
		panel[0].chapterEventsConnected(() -> true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(((JButton)named(panel[0], "holyEmpireDiscordVerify")).isVisible());
			assertTrue(((JToggleButton)named(panel[0], "holyEmpireBanListTab")).isSelected());
		});
		panel[0].setOfficerSettingsEligible(false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(((JToggleButton)named(panel[0], "holyEmpireBanListTab")).isVisible());
			assertTrue(((JToggleButton)named(panel[0], "holyEmpireAnnouncementsTab")).isSelected());
		});
		connected.set(false);
		panel[0].chapterEventsExpired(() -> true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> assertTrue(((JButton)named(panel[0], "holyEmpireDiscordVerify")).isVisible()));
		// Queued grants cannot resurrect revoked tools.
		panel[0].setOfficerSettingsEligible(true);
		panel[0].setOfficerSettingsEligible(false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			()
				-> assertFalse(
					((JToggleButton)named(panel[0], "holyEmpireBanListTab")).isVisible()));
		panel[0].stopPrivateCacheTimer();
	}

	@Test public void tabsRequireVerifiedOfficerRankAndRosterIsDefault() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		final int[] loads = {0};
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setBanDirectoryLoader(force -> loads[0]++);
		});
		panel[0].update(Collections.singletonMap("alpha", Collections.singletonList("Holy Chaos")),
						Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JToggleButton roster = (JToggleButton)named(panel[0], "holyEmpireRosterTab");
			JToggleButton bans = (JToggleButton)named(panel[0], "holyEmpireBanListTab");
			assertTrue(roster.isSelected());
			assertFalse(bans.isVisible());
			JPanel navigation = (JPanel)named(panel[0], "holyEmpireNavigationTabs");
			navigation.setSize(280, 135);
			navigation.doLayout();
			JToggleButton events = (JToggleButton)named(panel[0], "holyEmpireEventsTab");
			assertTrue("Events are available to verified non-officers", events.isVisible());
			assertTrue("Events appear to the left of Roster",
					   events.getX() < roster.getX() && events.getWidth() > 0);
			assertTrue("Events must not require an officer rank", events.isEnabled());
			assertNotNull(named(panel[0], "holyEmpireGlobalSearch"));
		});
		panel[0].setOfficerSettingsEligible(true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JToggleButton bans = (JToggleButton)named(panel[0], "holyEmpireBanListTab");
			assertTrue(bans.isVisible());
			bans.doClick();
			assertTrue(bans.isSelected());
			assertEquals(1, loads[0]);
			assertNotNull(named(panel[0], "holyEmpireBanGlobalSearch"));
			assertNull(named(panel[0], "holyEmpireOfficerBanLookup"));
		});
		panel[0].setOfficerSettingsEligible(false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(((JToggleButton)named(panel[0], "holyEmpireBanListTab")).isVisible());
			assertTrue(((JToggleButton)named(panel[0], "holyEmpireAnnouncementsTab")).isSelected());
			assertNull(named(panel[0], "holyEmpireBanRefresh"));
		});
		panel[0].stopPrivateCacheTimer();
	}

	@Test public void synchronizedEmptyBanListShowsNoBansAndZeroCount() throws Exception
	{
		FederationPanel[] panel = {null};
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		try
		{
			panel[0].update(
				Collections.singletonMap("officer", Collections.singletonList("Holy Chaos")),
				Collections.emptyMap());
			panel[0].setOfficerSettingsEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> { ((JToggleButton)named(panel[0], "holyEmpireBanListTab")).doClick(); });
			Map<String, FederationBanDirectoryClient.Chapter> chapters = new LinkedHashMap<>();
			chapters.put("Holy Chaos",
						 new FederationBanDirectoryClient.Chapter("Holy Chaos", 1795000000, 2));
			panel[0].displayBanDirectory(
				new FederationBanDirectoryClient.Snapshot(Collections.emptyList(), chapters),
				() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JLabel label = (JLabel)named(panel[0], "holyEmpireBanChapterStatus:Holy Chaos");
				JLabel count = (JLabel)named(panel[0], "holyEmpireBanChapterCount:Holy Chaos");
				assertNotNull(label);
				assertNotNull(count);
				assertEquals("Synchronized - No Bans", label.getText());
				assertEquals("0", count.getText());
				JLabel other = (JLabel)named(panel[0], "holyEmpireBanChapterStatus:Holy Empire");
				assertEquals("Not synchronized yet", other.getText());
				((JButton)named(panel[0], "holyEmpireBanChapter:Holy Chaos")).doClick();
				assertTrue("Empty synchronized chapter must remain browsable",
						   named(panel[0], "holyEmpireBanChapterBack") != null);
			});
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}

	@Test public void clearBanReasonButtonRequiresOwnChapterAndStoredReason() throws Exception
	{
		FederationPanel[] panel = new FederationPanel[1];
		java.util.concurrent.atomic.AtomicReference<String> target =
			new java.util.concurrent.atomic.AtomicReference<>("");
		java.util.concurrent.atomic.AtomicInteger revision =
			new java.util.concurrent.atomic.AtomicInteger();
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setBanDirectoryLoader(force -> {});
			panel[0].setBanReasonClearAction((entry, rev) -> {
				target.set(entry.name);
				revision.set(rev);
			}, entry -> entry.chapter.equals("Holy Chaos"));
		});
		try
		{
			panel[0].update(Map.of("Officer", List.of("Holy Chaos")), Collections.emptyMap());
			panel[0].setOfficerSettingsEligible(true);
			SwingUtilities.invokeAndWait(() ->
				((JToggleButton)named(panel[0], "holyEmpireBanListTab")).doClick());
			Map<String,FederationBanDirectoryClient.Chapter> chapters = new LinkedHashMap<>();
			chapters.put("Holy Chaos",
				new FederationBanDirectoryClient.Chapter("Holy Chaos", 1795000000, 7));
			chapters.put("Holy Empire",
				new FederationBanDirectoryClient.Chapter("Holy Empire", 1795000000, 8));
			FederationBanDirectoryClient.Snapshot snapshot =
				new FederationBanDirectoryClient.Snapshot(Arrays.asList(
					new FederationBanDirectoryClient.Entry("Holy Chaos", "Annotated A",
						"tracked", 1795000000, "Other", "Entered by mistake", "Officer", false),
					new FederationBanDirectoryClient.Entry("Holy Chaos", "No Reason B",
						"tracked", 1795000000),
					new FederationBanDirectoryClient.Entry("Holy Empire", "Annotated C",
						"tracked", 1795000000, "Begging", "", "Other Officer", true)),
					chapters);
			panel[0].displayBanDirectory(snapshot, () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JTextField)named(panel[0], "holyEmpireBanGlobalSearch")).setText("Annotated");
				JButton clear = (JButton)named(panel[0],
					"holyEmpireClearBanReason:Holy Chaos:Annotated A");
				assertNotNull("Own chapter's saved reason can be cleared", clear);
				assertNull("Other chapters cannot be edited",
					named(panel[0], "holyEmpireClearBanReason:Holy Empire:Annotated C"));
				clear.doClick();
				assertEquals("Annotated A",target.get());
				assertEquals(7,revision.get());
				((JTextField)named(panel[0], "holyEmpireBanGlobalSearch")).setText("No Reason");
				assertNull("No clear button for empty annotations",
					named(panel[0], "holyEmpireClearBanReason:Holy Chaos:No Reason B"));
			});
		}
		finally { panel[0].stopPrivateCacheTimer(); }
	}

	@Test public void combinedBanSearchCachesAndRevokes() throws Exception
	{
		FederationPanel[] panel = new FederationPanel[1];
		int[] loads = {0};
		boolean[] forced = {false};
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationPanel();
			panel[0].setBanDirectoryLoader(force -> {
				loads[0]++;
				forced[0] = force;
			});
		});
		panel[0].update(Collections.singletonMap("alpha", Collections.singletonList("Holy Chaos")),
						Collections.emptyMap());
		panel[0].setOfficerSettingsEligible(true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			((JToggleButton)named(panel[0], "holyEmpireBanListTab")).doClick();
			assertEquals(1, loads[0]);
			assertFalse(forced[0]);
		});
		Map<String, FederationBanDirectoryClient.Chapter> chapters = new LinkedHashMap<>();
		chapters.put("Holy Chaos",
					 new FederationBanDirectoryClient.Chapter("Holy Chaos", 1795000000, 1));
		chapters.put("Holy Empire",
					 new FederationBanDirectoryClient.Chapter("Holy Empire", 1795000001, 2));
		FederationBanDirectoryClient.Snapshot snapshot = new FederationBanDirectoryClient.Snapshot(
			Arrays.asList(new FederationBanDirectoryClient.Entry("Holy Chaos", "Example One",
																 "tracked", 1795000000),
						  new FederationBanDirectoryClient.Entry("Holy Empire", "Example Two",
																 "unresolved", 1795000001)),
			chapters);
		panel[0].displayBanDirectory(snapshot, () -> true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertNotNull(named(panel[0], "holyEmpireBanChapter:Holy Chaos"));
			assertNotNull(named(panel[0], "holyEmpireBanChapter:Holy Empire"));
			JTextField global = (JTextField)named(panel[0], "holyEmpireBanGlobalSearch");
			global.setText("Example");
			assertEquals(2, countNamed(panel[0], "holyEmpireBanEntry"));
			global.setText("Two");
			assertEquals(1, countNamed(panel[0], "holyEmpireBanEntry"));
			global.setText("");
			((JButton)named(panel[0], "holyEmpireBanChapter:Holy Chaos")).doClick();
			JTextField local = (JTextField)named(panel[0], "holyEmpireBanChapterSearch");
			local.setText("One");
			assertEquals(1, countNamed(panel[0], "holyEmpireBanEntry"));
			local.setText("Two");
			assertEquals(0, countNamed(panel[0], "holyEmpireBanEntry"));
			((JButton)named(panel[0], "holyEmpireBanChapterBack")).doClick();
			((JToggleButton)named(panel[0], "holyEmpireRosterTab")).doClick();
			((JToggleButton)named(panel[0], "holyEmpireBanListTab")).doClick();
			assertEquals("Fresh private cache is reused", 1, loads[0]);
			((JButton)named(panel[0], "holyEmpireBanRefresh")).doClick();
			assertEquals(2, loads[0]);
			assertTrue("Manual refresh bypasses the cache", forced[0]);
		});
		panel[0].setOfficerSettingsEligible(false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertFalse(((JToggleButton)named(panel[0], "holyEmpireBanListTab")).isVisible());
			assertNull(named(panel[0], "holyEmpireBanEntry"));
		});
		panel[0].displayBanDirectory(snapshot, () -> false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> assertNull(named(panel[0], "holyEmpireBanEntry")));
		panel[0].stopPrivateCacheTimer();
	}

	private static int countNamed(Container parent, String name)
	{
		int found = 0;
		for (Component component : parent.getComponents())
		{
			if (name.equals(component.getName()))
			{
				found++;
			}
			if (component instanceof Container)
			{
				found += countNamed((Container)component, name);
			}
		}
		return found;
	}

	@Test public void chapterRosterDoesNotRenderServerSuppliedNameAsHtml() throws Exception
	{
		String untrustedName = "<html><b>Sample &amp; Player</b></html>";
		String untrustedRank = "<html><i>Untrusted Rank</i></html>";
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		Map<String, List<String>> combined =
			Collections.singletonMap(untrustedName, Collections.singletonList("Holy Chaos"));
		Map<String, List<WomDirectoryClient.ChapterMember>> chapters = Collections.singletonMap(
			"Holy Chaos", Collections.singletonList(
							  new WomDirectoryClient.ChapterMember(untrustedName, untrustedRank)));
		panel[0].update(combined, chapters);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JButton chapter = (JButton)named(panel[0], "holyEmpireChapter:Holy Chaos");
			assertNotNull(chapter);
			chapter.doClick();
			JPanel row = (JPanel)named(panel[0], "holyEmpireRosterRow:" + untrustedName);
			assertNotNull(row);
			JLabel name = (JLabel)row.getComponent(0);
			JLabel rank = (JLabel)row.getComponent(1);
			assertEquals(untrustedName, name.getText());
			assertEquals(Boolean.TRUE, name.getClientProperty("html.disable"));
			assertNull(name.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
			assertEquals(Boolean.TRUE, rank.getClientProperty("html.disable"));
			assertNull(rank.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
		});
	}
}
