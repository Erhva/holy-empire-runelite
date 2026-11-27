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
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationSidebarLinksTest
{
	private static Component named(Container root, String name)
	{
		for (Component c : root.getComponents())
		{
			if (name.equals(c.getName()))
			{
				return c;
			}
			if (c instanceof Container)
			{
				Component found = named((Container)c, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test public void bothIconButtonsHaveExactDestinationsAndBundledArtwork() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		SwingUtilities.invokeAndWait(() -> {
			JPanel social = (JPanel)named(panel[0], "holyEmpireSocialLinks");
			assertNotNull(social);
			assertEquals(2, social.getComponentCount());
			JButton discord = (JButton)named(panel[0], "holyEmpireDiscordLink");
			JButton clanFinder = (JButton)named(panel[0], "holyEmpireClanFinderLink");
			assertNotNull(discord);
			assertNotNull(clanFinder);
			assertSame(social, discord.getParent());
			assertSame(social, clanFinder.getParent());
			assertEquals("https://discord.gg/w465", discord.getClientProperty("holyEmpireLinkURL"));
			assertEquals("https://osrsclanfinder.com/clans/holy-empire",
						 clanFinder.getClientProperty("holyEmpireLinkURL"));
			assertTrue(discord.getToolTipText().contains("Discord"));
			assertTrue(clanFinder.getToolTipText().contains("ClanFinder"));
			assertEquals(1, discord.getActionListeners().length);
			assertEquals(1, clanFinder.getActionListeners().length);
			assertNotNull("Bundled Discord mark missing", discord.getIcon());
			assertNotNull("User-provided ClanFinder mark missing", clanFinder.getIcon());
			assertEquals(22, discord.getIcon().getIconWidth());
			assertEquals(22, clanFinder.getIcon().getIconWidth());
			assertEquals(31, discord.getPreferredSize().width);
			assertEquals(31, clanFinder.getPreferredSize().height);
			assertEquals("Discord link", discord.getAccessibleContext().getAccessibleName());
			assertEquals("ClanFinder link", clanFinder.getAccessibleContext().getAccessibleName());
		});
		assertNotNull(FederationPanel.class.getResource("/social-icons/discord.png"));
		assertNotNull(FederationPanel.class.getResource("/social-icons/clanfinder.png"));
	}

	@Test public void shortcutButtonsProvideVisibleHoverAndHandCursor() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		SwingUtilities.invokeAndWait(() -> {
			for (String name : new String[] {"holyEmpireDiscordLink", "holyEmpireClanFinderLink"})
			{
				JButton button = (JButton)named(panel[0], name);
				assertEquals(java.awt.Cursor.HAND_CURSOR, button.getCursor().getType());
				assertFalse(button.isContentAreaFilled());
				java.awt.event.MouseEvent event = new java.awt.event.MouseEvent(
					button, java.awt.event.MouseEvent.MOUSE_ENTERED, 0, 0, 5, 5, 0, false);
				for (java.awt.event.MouseListener listener : button.getMouseListeners())
				{
					listener.mouseEntered(event);
				}
				assertEquals(Boolean.TRUE, button.getClientProperty("holyEmpireHover"));
				// Testing the actual pixels prevents a regression where the RuneLite
				// LAF colors only the inset content area, not the whole 31x31 square.
				button.setSize(31, 31);
				java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
					31, 31, java.awt.image.BufferedImage.TYPE_INT_ARGB);
				java.awt.Graphics2D graphics = image.createGraphics();
				button.paint(graphics);
				graphics.dispose();
				int highlighted = HolyEmpireTheme.HOVER.getRGB();
				for (int[] point : new int[][] {{2, 2}, {28, 2}, {2, 28}, {28, 28}})
				{
					assertEquals("Unfilled hover corner at " + point[0] + "," + point[1] + " for " +
									 name,
								 highlighted, image.getRGB(point[0], point[1]));
				}
				assertEquals(HolyEmpireTheme.HOVER, button.getBackground());
				event = new java.awt.event.MouseEvent(
					button, java.awt.event.MouseEvent.MOUSE_EXITED, 0, 0, 5, 5, 0, false);
				for (java.awt.event.MouseListener listener : button.getMouseListeners())
				{
					listener.mouseExited(event);
				}
				assertEquals(Boolean.FALSE, button.getClientProperty("holyEmpireHover"));
				assertFalse("Native content painting must stay off", button.isContentAreaFilled());
			}
		});
	}

	@Test public void shortcutsRemainAtTheTopThroughUnverifiedAndChapterViews() throws Exception
	{
		final FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		final JButton[] buttons = new JButton[2];
		SwingUtilities.invokeAndWait(() -> {
			buttons[0] = (JButton)named(panel[0], "holyEmpireDiscordLink");
			buttons[1] = (JButton)named(panel[0], "holyEmpireClanFinderLink");
			assertNotNull(named(panel[0], "holyEmpireTop"));
		});
		panel[0].showNotVerified();
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertSame(buttons[0], named(panel[0], "holyEmpireDiscordLink"));
			assertSame(buttons[1], named(panel[0], "holyEmpireClanFinderLink"));
			assertNotNull(named(panel[0], "holyEmpireAccessDenied"));
		});
		panel[0].update(
			Collections.singletonMap("member", Collections.singletonList("Holy Empire")),
			Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			((JButton)named(panel[0], "holyEmpireChapter:Holy Empire")).doClick();
			assertSame(buttons[0], named(panel[0], "holyEmpireDiscordLink"));
			assertSame(buttons[1], named(panel[0], "holyEmpireClanFinderLink"));
			assertNotNull(named(panel[0], "holyEmpireChapterSearch"));
		});
		panel[0].setPreviewUnverifiedScreen(true);
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertSame(buttons[0], named(panel[0], "holyEmpireDiscordLink"));
			assertSame(buttons[1], named(panel[0], "holyEmpireClanFinderLink"));
		});
	}
}
