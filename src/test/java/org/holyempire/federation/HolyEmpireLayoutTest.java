/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireLayoutTest
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

	@Test public void spacingAndHeaderHeightDoNotUseTallChapterDetailCard() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		panel[0].update(Collections.singletonMap("alpha", Collections.singletonList("Holy Empire")),
						Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JLabel title = (JLabel)named(panel[0], "holyEmpireHeader");
			JTextField search = (JTextField)named(panel[0], "holyEmpireGlobalSearch");
			JPanel banner = (JPanel)named(panel[0], "holyEmpireBrandHeader");
			JPanel navigation = (JPanel)named(panel[0], "holyEmpireNavigationTabs");
			JPanel top = (JPanel)named(panel[0], "holyEmpireTop");
			JPanel home = (JPanel)search.getParent();
			JPanel card = (JPanel)named(panel[0], "holyEmpireHeaderCards");
			assertSame(banner, title.getParent());
			assertTrue("Brand title must be above navigation",
					   top.getComponentZOrder(banner) < top.getComponentZOrder(navigation));
			assertTrue("Search belongs below the navigation",
					   top.getComponentZOrder(navigation) < top.getComponentZOrder(card));
			home.setSize(270, 150);
			home.doLayout();
			assertEquals("For the Emperor!",
						 ((JLabel)named(panel[0], "holyEmpireImperialMotto")).getText());
			assertTrue("Search stays close beneath the navigation",
					   search.getY() >= 5 && search.getY() <= 12);
			assertEquals("No hidden chapter-detail space", home.getPreferredSize().height,
						 card.getPreferredSize().height);
			assertNotNull(named(panel[0], "holyEmpireChapter:Holy Empire"));
			((JButton)named(panel[0], "holyEmpireChapter:Holy Empire")).doClick();
			assertNotNull(named(panel[0], "holyEmpireChapterSearch"));
			JPanel detail =
				(JPanel)((JTextField)named(panel[0], "holyEmpireChapterSearch")).getParent();
			assertEquals("Chapter detail header uses only its visible height",
						 detail.getPreferredSize().height, card.getPreferredSize().height);
			((JButton)named(panel[0], "holyEmpireChapterBack")).doClick();
			assertEquals(home.getPreferredSize().height, card.getPreferredSize().height);
		});
	}

	@Test
	public void chapterCardsUseSixteenPointNamesAndFourteenPointDescriptions() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		panel[0].update(Collections.singletonMap("alpha", Collections.singletonList("Holy Empire")),
						Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JButton card = (JButton)named(panel[0], "holyEmpireChapter:Holy Empire");
			assertNotNull(card);
			JLabel name = (JLabel)named(card, "holyEmpireChapterTitle:Holy Empire");
			JLabel description = (JLabel)named(card, "holyEmpireChapterDescription:Holy Empire");
			assertNotNull(name);
			assertNotNull(description);
			assertEquals(16f, name.getFont().getSize2D(), 0f);
			JLabel banner = (JLabel)named(panel[0], "holyEmpireHeader");
			assertNotNull(banner);
			assertEquals("Holy Empire banner is now 20pt", 20f, banner.getFont().getSize2D(), 0f);
			assertTrue(banner.getFont().isBold());
			assertTrue(name.getFont().isBold());
			assertEquals(14f, description.getFont().getSize2D(), 0f);
		});
	}

	@Test public void loadingMessageIsNotRepeatedInTheFooter() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		panel[0].showMessage("Checking Holy Empire membership...");
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JLabel footer =
				(JLabel)((BorderLayout)panel[0].getLayout()).getLayoutComponent(BorderLayout.SOUTH);
			assertFalse("Loading text must appear only in content, not footer", footer.isVisible());
			assertEquals("Checking Holy Empire membership...", footer.getText());
			int count = 0;
			for (java.awt.Component c : ((JPanel)((BorderLayout)panel[0].getLayout())
											 .getLayoutComponent(BorderLayout.CENTER))
											.getComponents())
			{
				if (c instanceof JTextArea &&
					((JTextArea)c).getText().equals("Checking Holy Empire membership..."))
				{
					count++;
				}
			}
			assertEquals(1, count);
		});
	}

	@Test public void previewAndRealDenialShareNewCenteredJoinScreen() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		panel[0].showNotVerified();
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JButton join = (JButton)named(panel[0], "holyEmpireJoinHere");
			assertNotNull(join);
			assertEquals("Join The Empire Here", join.getText());
			JPanel holder = (JPanel)named(panel[0], "holyEmpireJoinButtonRow");
			assertNotNull(holder);
			assertSame(join, holder.getComponent(0));
			holder.setSize(300, join.getPreferredSize().height + 6);
			holder.doLayout();
			assertEquals((300 - join.getWidth()) / 2, join.getX());
			JTextArea instructions = (JTextArea)named(panel[0], "holyEmpireJoinInstructions");
			assertNotNull(instructions);
			assertTrue(instructions.getText().contains(
				"membership in your in-game clan and the matching WOM roster"));
			assertTrue(instructions.getText().contains("6 hours or more"));
			JLabel footer =
				(JLabel)((BorderLayout)panel[0].getLayout()).getLayoutComponent(BorderLayout.SOUTH);
			assertFalse(footer.isVisible());
		});
		panel[0].showNotVerified();
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertNotNull(named(panel[0], "holyEmpireJoinInstructions"));
			JLabel footer =
				(JLabel)((BorderLayout)panel[0].getLayout()).getLayoutComponent(BorderLayout.SOUTH);
			assertFalse(footer.isVisible());
		});
	}
}
