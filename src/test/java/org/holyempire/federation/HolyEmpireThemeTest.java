/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Theme-only regressions. No new game input, data access or authorization. */
public class HolyEmpireThemeTest
{
	private static Component named(Container root, String id)
	{
		for (Component child : root.getComponents())
		{
			if (id.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = named((Container)child, id);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void hover(AbstractButton button, boolean enter)
	{
		MouseEvent event =
			new MouseEvent(button, enter ? MouseEvent.MOUSE_ENTERED : MouseEvent.MOUSE_EXITED, 0, 0,
						   4, 4, 0, false);
		for (java.awt.event.MouseListener listener : button.getMouseListeners())
		{
			if (enter)
			{
				listener.mouseEntered(event);
			}
			else
			{
				listener.mouseExited(event);
			}
		}
	}

	@Test
	public void mottoAndImperialChapterHoverKeepHolyEmpireBlue() throws Exception
	{
		FederationPanel[] ref = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> ref[0] = new FederationPanel());
		try
		{
			ref[0].update(
				Collections.singletonMap("Not Needed", Collections.singletonList("Holy Empire")),
				Collections.singletonMap(
					"Holy Empire",
					Arrays.asList(new WomDirectoryClient.ChapterMember("Not Needed", "owner"))));
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JLabel title = (JLabel)named(ref[0], "holyEmpireHeader");
				JLabel motto = (JLabel)named(ref[0], "holyEmpireImperialMotto");
				assertEquals("Holy Empire", title.getText());
				assertEquals(HolyEmpireRankDisplay.GOLD, title.getForeground());
				assertEquals("For the Emperor!", motto.getText());
				assertEquals(HolyEmpireTheme.BLUE, motto.getForeground());
				JTextField search = (JTextField)named(ref[0], "holyEmpireGlobalSearch");
				assertEquals(HolyEmpireTheme.BLUE, search.getCaretColor());
				JButton chapter = (JButton)named(ref[0], "holyEmpireChapter:Holy Empire");
				assertEquals("imperial", chapter.getClientProperty("holyEmpireThemeRole"));
				assertEquals(Cursor.HAND_CURSOR, chapter.getCursor().getType());
				hover(chapter, true);
				assertEquals(Boolean.TRUE, chapter.getClientProperty("holyEmpireHover"));
				hover(chapter, false);
				assertEquals(Boolean.FALSE, chapter.getClientProperty("holyEmpireHover"));
				search.setText("not needed");
				JPanel emperor = (JPanel)named(ref[0], "holyEmpireGlobalRow:Not Needed");
				assertNotNull(emperor);
				assertEquals("emperor", emperor.getClientProperty("holyEmpireThemeRole"));
				JLabel rank = (JLabel)named(ref[0], "holyEmpireGlobalRank:Not Needed:Holy Empire");
				assertEquals("Holy Empire · Emperor", rank.getText());
				assertEquals(HolyEmpireRankDisplay.GOLD, rank.getForeground());
			});
		}
		finally
		{
			SwingUtilities.invokeAndWait(() -> ref[0].stopPrivateCacheTimer());
		}
	}

	@Test
	public void restrictedBanStylingDoesNotChangeOfficerGate() throws Exception
	{
		FederationPanel[] ref = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			ref[0] = new FederationPanel();
			ref[0].setBanDirectoryLoader(force -> {});
		});
		try
		{
			ref[0].update(
				Collections.singletonMap("Officer", Collections.singletonList("Holy Chaos")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertFalse(
						((JToggleButton)named(ref[0], "holyEmpireBanListTab")).isVisible()));
			ref[0].setOfficerSettingsEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JToggleButton bans = (JToggleButton)named(ref[0], "holyEmpireBanListTab");
				assertEquals("restricted", bans.getClientProperty("holyEmpireThemeRole"));
				bans.doClick();
				JLabel sub = (JLabel)named(ref[0], "holyEmpireRestrictedSubtitle");
				assertEquals("Restricted Chapter Records", sub.getText());
				JButton refresh = (JButton)named(ref[0], "holyEmpireBanRefresh");
				assertEquals("restricted", refresh.getClientProperty("holyEmpireThemeRole"));
			});
			Map<String, FederationBanDirectoryClient.Chapter> chapters = new LinkedHashMap<>();
			chapters.put("Holy Chaos",
						 new FederationBanDirectoryClient.Chapter("Holy Chaos", 1795000000, 1));
			FederationBanDirectoryClient.Snapshot snapshot =
				new FederationBanDirectoryClient.Snapshot(
					Arrays.asList(new FederationBanDirectoryClient.Entry(
						"Holy Chaos", "Example Player", "tracked", 1795000000)),
					chapters);
			ref[0].displayBanDirectory(snapshot, () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				JButton chapter = (JButton)named(ref[0], "holyEmpireBanChapter:Holy Chaos");
				assertEquals("restricted", chapter.getClientProperty("holyEmpireThemeRole"));
				JTextField search = (JTextField)named(ref[0], "holyEmpireBanGlobalSearch");
				search.setText("Example");
				JPanel entry = (JPanel)named(ref[0], "holyEmpireBanEntry");
				assertEquals("restrictedRow", entry.getClientProperty("holyEmpireThemeRole"));
			});
			ref[0].setOfficerSettingsEligible(false);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertFalse(((JToggleButton)named(ref[0], "holyEmpireBanListTab")).isVisible());
				assertNull(named(ref[0], "holyEmpireBanEntry"));
			});
		}
		finally
		{
			SwingUtilities.invokeAndWait(() -> ref[0].stopPrivateCacheTimer());
		}
	}
}
