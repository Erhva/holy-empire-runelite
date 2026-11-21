/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
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
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		panel[0].setOfficerSettingsOpener(() -> {});
		Map<String, List<String>> roster =
			Collections.singletonMap("alpha", Collections.singletonList("Holy Empire"));
		panel[0].update(roster, Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> assertNull(named(panel[0], "holyEmpireOfficerBanSettings")));

		// The owner/deputy-only control appears within Ban List, never the roster.
		panel[0].setOfficerSettingsEligible(true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertNull(named(panel[0], "holyEmpireOfficerBanSettings"));
			JToggleButton bans = (JToggleButton)named(panel[0], "holyEmpireBanListTab");
			assertNotNull(bans);
			bans.doClick();
			JButton admin = (JButton)named(panel[0], "holyEmpireOfficerBanSettings");
			assertNotNull(admin);
			assertEquals("Admin Verification", admin.getText());
			((JToggleButton)named(panel[0], "holyEmpireRosterTab")).doClick();
			assertNull(named(panel[0], "holyEmpireOfficerBanSettings"));
			bans.doClick();
			assertNotNull(named(panel[0], "holyEmpireOfficerBanSettings"));
		});

		panel[0].setOfficerSettingsEligible(false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> assertNull(named(panel[0], "holyEmpireOfficerBanSettings")));

		// Rapid grant then revoke before EDT dispatch must not leave controls visible.
		panel[0].setOfficerSettingsEligible(true);
		panel[0].setOfficerSettingsEligible(false);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> assertNull(named(panel[0], "holyEmpireOfficerBanSettings")));

		panel[0].setOfficerSettingsEligible(true);
		panel[0].showNotVerified();
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(
			() -> assertNull(named(panel[0], "holyEmpireOfficerBanSettings")));
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
			navigation.setSize(280, 44);
			navigation.doLayout();
			assertTrue(
				"Roster is centered within Swing pixel rounding",
				Math.abs(navigation.getWidth() / 2 - (roster.getX() + roster.getWidth() / 2)) <= 1);
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
			assertTrue(((JToggleButton)named(panel[0], "holyEmpireRosterTab")).isSelected());
			assertNull(named(panel[0], "holyEmpireBanRefresh"));
		});
		panel[0].stopPrivateCacheTimer();
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
