/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireMemberExperienceTest
{
	private static Component named(Container root, String name)
	{
		for (Component child : root.getComponents())
		{
			if (name.equals(child.getName()))
			{
				return child;
			}
			if (child instanceof Container)
			{
				Component found = named((Container)child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static final Color GOLD = new Color(255, 215, 0);

	@Test public void onlyVerifiedMembersCanBrowseAndOthersGetJoinLink() throws Exception
	{
		final FederationPanel[] ref = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> ref[0] = new FederationPanel());
		assertNull(named(ref[0], "holyEmpireChapter:Holy Empire"));
		ref[0].showNotVerified();
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JTextArea denied = (JTextArea)named(ref[0], "holyEmpireAccessDenied");
			assertNotNull(denied);
			assertEquals("Only verified members of the Holy Empire may access this plugin.",
						 denied.getText());
			JButton join = (JButton)named(ref[0], "holyEmpireJoinHere");
			assertNotNull(join);
			assertEquals("Join The Empire Here", join.getText());
			assertEquals(SwingTextSafety.boxedTooltip("https://discord.gg/w465"), join.getToolTipText());
			JPanel buttonRow = (JPanel)named(ref[0], "holyEmpireJoinButtonRow");
			assertNotNull("Join button must be inside a centering row", buttonRow);
			buttonRow.setSize(300, join.getPreferredSize().height + 6);
			buttonRow.doLayout();
			assertEquals((300 - join.getWidth()) / 2, join.getX());
			JTextArea details = (JTextArea)named(ref[0], "holyEmpireJoinInstructions");
			assertNotNull("Discord instructions missing beneath Join button", details);
			assertEquals(
				"The link above takes you to our discord. Follow the steps and create a ticket "
					+
					"to join one of our chapters. Access requires membership in your in-game clan "
					+ "and the matching WOM roster. WOM sync may take 6 hours or more.",
				details.getText());
			assertTrue(details.getLineWrap());
			assertTrue(details.getWrapStyleWord());
			assertEquals(SwingTextSafety.boxedTooltip(details.getText()), details.getToolTipText());
			JLabel footer =
				(JLabel)((BorderLayout)ref[0].getLayout()).getLayoutComponent(BorderLayout.SOUTH);
			assertFalse("Unverified footer must not show Membership required", footer.isVisible());
			assertFalse(footer.getText().contains("Membership required"));
			assertNull(named(ref[0], "holyEmpireChapter:Holy Empire"));
			assertNull(named(ref[0], "holyEmpireGlobalRank:not needed:Holy Empire"));
		});
	}

	@Test public void developerPreviewHidesAndRestoresWithoutChangingVerification() throws Exception
	{
		final FederationPanel[] ref = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> ref[0] = new FederationPanel());
		Map<String, List<String>> member =
			Collections.singletonMap("alpha", Collections.singletonList("Holy Empire"));
		ref[0].update(member, Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertNotNull(named(ref[0], "holyEmpireChapter:Holy Empire"));
			ref[0].setPreviewUnverifiedScreen(true);
		});
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertNotNull(named(ref[0], "holyEmpireAccessDenied"));
			assertNotNull(named(ref[0], "holyEmpireJoinHere"));
			assertNull(named(ref[0], "holyEmpireChapter:Holy Empire"));
			ref[0].setPreviewUnverifiedScreen(false);
		});
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertNull(named(ref[0], "holyEmpireAccessDenied"));
			assertNotNull(named(ref[0], "holyEmpireChapter:Holy Empire"));
			JLabel title = (JLabel)named(ref[0], "holyEmpireHeader");
			JTextField search = (JTextField)named(ref[0], "holyEmpireGlobalSearch");
			JPanel banner = (JPanel)title.getParent();
			JPanel home = (JPanel)search.getParent();
			JPanel navigation = (JPanel)named(ref[0], "holyEmpireNavigationTabs");
			JPanel top = (JPanel)named(ref[0], "holyEmpireTop");
			assertTrue("Permanent Holy Empire banner is above the roster tabs",
					   top.getComponentZOrder(banner) < top.getComponentZOrder(navigation));
			home.setSize(220, 160);
			home.doLayout();
			int actualGap = search.getY();
			assertTrue("Compact space beneath navigation", actualGap >= 5 && actualGap <= 12);
			JPanel headerCards = (JPanel)named(ref[0], "holyEmpireHeaderCards");
			assertEquals("Inactive chapter header must not cause extra blank space",
						 home.getPreferredSize().height, headerCards.getPreferredSize().height);
		});
		ref[0].showNotVerified();
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertNotNull("Real denial persists without preview",
						  named(ref[0], "holyEmpireAccessDenied"));
		});
	}

	@Test public void globalSearchDisplaysRankIconEmperorAndWrappedStatus() throws Exception
	{
		final FederationPanel[] ref = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> ref[0] = new FederationPanel());
		Map<String, List<String>> members = new HashMap<>();
		members.put("not needed", Collections.singletonList("Holy Empire"));
		members.put("captain", Arrays.asList("Holy Guard", "Holy Empire"));
		Map<String, List<WomDirectoryClient.ChapterMember>> rosters = new HashMap<>();
		rosters.put("Holy Empire",
					Arrays.asList(new WomDirectoryClient.ChapterMember("Not Needed", "owner"),
								  new WomDirectoryClient.ChapterMember("Captain", "captain")));
		rosters.put("Holy Guard", Collections.singletonList(new WomDirectoryClient.ChapterMember(
									  "Captain", "deputy_owner")));
		ref[0].update(members, rosters);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JLabel title = (JLabel)named(ref[0], "holyEmpireHeader");
			assertNotNull(title);
			assertEquals("Holy Empire", title.getText());
			assertEquals(GOLD, title.getForeground());
			assertEquals(JLabel.CENTER, title.getHorizontalAlignment());
			JLabel footer =
				(JLabel)((BorderLayout)ref[0].getLayout()).getLayoutComponent(BorderLayout.SOUTH);
			assertTrue(footer.getText().contains("<br>"));
			assertTrue(footer.getToolTipText().contains("2 WOM rosters"));

			JTextField search = (JTextField)named(ref[0], "holyEmpireGlobalSearch");
			search.setText("not needed");
			JLabel rank = (JLabel)named(ref[0], "holyEmpireGlobalRank:not needed:Holy Empire");
			assertNotNull(rank);
			assertEquals("Holy Empire · Emperor", rank.getText());
			assertNotNull(rank.getIcon());
			assertEquals(GOLD, rank.getForeground());
			assertEquals("Emperor",
						 HolyEmpireRankDisplay.title(
							 new WomDirectoryClient.ChapterMember("Not Needed", "unranked")));
			assertTrue(HolyEmpireRankDisplay.isEmperor("  NOT   NEEDED  "));
			assertFalse(HolyEmpireRankDisplay.isEmperor("Not Needed Alt"));

			search.setText("captain");
			for (String clan : Arrays.asList("Holy Guard", "Holy Empire"))
			{
				JLabel result = (JLabel)named(ref[0], "holyEmpireGlobalRank:captain:" + clan);
				assertNotNull(result);
				assertNotNull("Missing rank icon for " + clan, result.getIcon());
				assertTrue(result.getText().contains(" · "));
			}
			assertEquals(
				"Holy Guard · Deputy Owner",
				((JLabel)named(ref[0], "holyEmpireGlobalRank:captain:Holy Guard")).getText());
			((JButton)named(ref[0], "holyEmpireChapter:Holy Empire")).doClick();
			JLabel emperor = (JLabel)named(ref[0], "holyEmpireRank:Not Needed");
			assertNotNull(emperor);
			assertEquals("Emperor", emperor.getText());
			assertNotNull(emperor.getIcon());
			assertEquals(GOLD, emperor.getForeground());
			assertNull(named(ref[0], "holyEmpireJoinHere"));
		});
	}
}
