/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.text.StyleConstants;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** UI carousel draws all 24h achievements fairly without network calls. */
public class WomAchievementRotatorTest
{
	private static List<WomAchievementsClient.Achievement> examples(int count)
	{
		List<WomAchievementsClient.Achievement> list = new ArrayList<>();
		long now = Instant.now().getEpochSecond();
		for (int i = 0; i < count; i++)
		{
			list.add(new WomAchievementsClient.Achievement("Member " + i, "99 Skill " + i,
														   "Chapter " + (i % 4), now - 60 - i));
		}
		return list;
	}

	private static Component find(Component root, String name)
	{
		if (name.equals(root.getName()))
			return root;
		if (root instanceof Container)
		{
			for (Component child : ((Container)root).getComponents())
			{
				Component found = find(child, name);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	@Test public void randomBagShowsEveryAchievementBeforeAnyRepeats() throws Exception
	{
		final WomAchievementRotator rotator = new WomAchievementRotator(new Random(731));
		final List<WomAchievementsClient.Achievement> all = examples(45);
		SwingUtilities.invokeAndWait(() -> {
			rotator.setAchievements(all);
			assertEquals(45, rotator.totalAchievements());
			Set<WomAchievementsClient.Achievement> round = new HashSet<>();
			for (int i = 0; i < 45; i++)
			{
				WomAchievementsClient.Achievement current = rotator.current();
				assertNotNull(current);
				assertTrue("Repeat occurred before full shuffle completed", round.add(current));
				if (i < 44)
					rotator.advance();
			}
			assertEquals(new HashSet<>(all), round);
			WomAchievementsClient.Achievement last = rotator.current();
			rotator.advance();
			assertNotEquals("Shuffle boundary cannot immediately repeat", last, rotator.current());

			Set<WomAchievementsClient.Achievement> second = new HashSet<>();
			for (int i = 0; i < 45; i++)
			{
				assertTrue(second.add(rotator.current()));
				if (i < 44)
					rotator.advance();
			}
			assertEquals(round, second);
			assertFalse("Hidden carousel must not consume Swing timer frames",
						rotator.animationRunning());
			rotator.stopAnimation();
		});
	}

	@Test public void unchangedFeedDoesNotRestartAndNewFeedKeepsCurrent() throws Exception
	{
		WomAchievementRotator rotator = new WomAchievementRotator(new Random(5));
		List<WomAchievementsClient.Achievement> original = examples(8);
		SwingUtilities.invokeAndWait(() -> {
			rotator.setAchievements(original);
			rotator.advance();
			WomAchievementsClient.Achievement current = rotator.current();
			rotator.setAchievements(new ArrayList<>(original));
			assertSame("Same feed must not restart rotation", current, rotator.current());

			List<WomAchievementsClient.Achievement> withNew = new ArrayList<>(original);
			withNew.add(examples(9).get(8));
			rotator.setAchievements(withNew);
			assertEquals(9, rotator.totalAchievements());
			assertEquals("New WOM refresh cannot flash the current item", current,
						 rotator.current());
			Set<WomAchievementsClient.Achievement> seen = new HashSet<>();
			seen.add(rotator.current());
			for (int i = 0; i < 8; i++)
			{
				rotator.advance();
				assertTrue(seen.add(rotator.current()));
			}
			assertEquals(9, seen.size());
			rotator.setAchievements(Collections.emptyList());
			assertNull(rotator.current());
			assertFalse(rotator.animationRunning());
			rotator.stopAnimation();
		});
	}

	@Test public void oneCardHasPlayerAchievementChapterAndNoTimestamp() throws Exception
	{
		WomAchievementRotator rotator = new WomAchievementRotator(new Random(1));
		SwingUtilities.invokeAndWait(() -> {
			rotator.setAchievements(examples(1));
			assertEquals(1, rotator.totalAchievements());
			assertEquals("Member 0",
						 ((JLabel)find(rotator, "holyEmpireWomAchievementPlayer")).getText());
			assertEquals(
				"99 Skill 0",
				((JTextPane)find(rotator, "holyEmpireWomAchievementDescription")).getText());
			assertEquals("Chapter 0",
						 ((JLabel)find(rotator, "holyEmpireWomAchievementChapter")).getText());
			assertEquals(128, rotator.getPreferredSize().height);
			assertEquals("Last 24 Hours",
						 ((JLabel)find(rotator, "holyEmpireWomAchievementFooter")).getText());
			assertEquals(javax.swing.text.StyleConstants.ALIGN_CENTER,
						 javax.swing.text.StyleConstants.getAlignment(
							 ((JTextPane)find(rotator, "holyEmpireWomAchievementDescription"))
								 .getParagraphAttributes()));
			JTextPane description = (JTextPane)find(rotator, "holyEmpireWomAchievementDescription");
			assertEquals(
				"Achievement text must be visible against dark panel", HolyEmpireTheme.TEXT,
				javax.swing.text.StyleConstants.getForeground(
					description.getStyledDocument().getCharacterElement(0).getAttributes()));
			assertEquals(18f,
						 ((JTextPane)find(rotator, "holyEmpireWomAchievementDescription"))
							 .getFont()
							 .getSize2D(),
						 0f);
			assertEquals(
				16f,
				((JLabel)find(rotator, "holyEmpireWomAchievementChapter")).getFont().getSize2D(),
				0f);
			assertEquals(7, rotator.getComponentCount());
			rotator.setSize(240, 128);
			rotator.doLayout();
			Component footer = find(rotator, "holyEmpireWomAchievementFooter");
			assertTrue("Footer must fit within the smaller card",
					   footer.getY() + footer.getHeight() <= rotator.getHeight() - 5);
			assertTrue("Description must retain room for at least one full line",
					   find(rotator, "holyEmpireWomAchievementDescription").getHeight() >= 20);
			assertFalse(rotator.animationRunning());
			rotator.stopAnimation();
		});
	}

	@Test public void last24HoursFooterDoesNotFadeWithAchievements() throws Exception
	{
		WomAchievementRotator rotator = new WomAchievementRotator(new Random(9));
		SwingUtilities.invokeAndWait(() -> {
			rotator.setAchievements(examples(3));
			rotator.setSize(260, 128);
			rotator.doLayout();
			JLabel footer = (JLabel)find(rotator, "holyEmpireWomAchievementFooter");
			assertNotNull(footer);
			Rectangle fixed = footer.getBounds();
			assertTrue(fixed.width > 0 && fixed.height > 0 && fixed.y > 0);

			BufferedImage normal = renderCard(rotator);
			long start = System.nanoTime() + (WomAchievementRotator.HOLD_MILLIS + 40) * 1_000_000L;
			rotator.tick(start); // Begin fading out the current achievement.
			rotator.tick(start + WomAchievementRotator.FADE_MILLIS * 500_000L);
			BufferedImage midFade = renderCard(rotator);

			for (int y = fixed.y; y < fixed.y + fixed.height; y++)
				for (int x = fixed.x; x < fixed.x + fixed.width; x++)
					assertEquals("Last 24 Hours must stay fully opaque", normal.getRGB(x, y),
								 midFade.getRGB(x, y));

			boolean dynamicChanged = false;
			for (int y = 1; y < fixed.y && !dynamicChanged; y++)
				for (int x = 1; x < normal.getWidth() - 1; x++)
					if (normal.getRGB(x, y) != midFade.getRGB(x, y))
					{
						dynamicChanged = true;
						break;
					}
			assertTrue("Achievement content must still fade", dynamicChanged);
			rotator.stopAnimation();
		});
	}

	private static BufferedImage renderCard(WomAchievementRotator rotator)
	{
		BufferedImage image =
			new BufferedImage(rotator.getWidth(), rotator.getHeight(), BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D paint = image.createGraphics();
		try
		{
			rotator.paint(paint);
		}
		finally
		{
			paint.dispose();
		}
		return image;
	}

	@Test public void emptyFeedAndExpiredAchievementsShowSingleEmptyState() throws Exception
	{
		WomAchievementRotator rotator = new WomAchievementRotator(new Random(4));
		long now = Instant.now().getEpochSecond();
		SwingUtilities.invokeAndWait(() -> {
			rotator.setAchievements(Collections.emptyList());
			assertEquals(
				"No recently tracked WOM achievements.",
				((JTextPane)find(rotator, "holyEmpireWomAchievementDescription")).getText());
			rotator.setAchievements(List.of(new WomAchievementsClient.Achievement(
				"Expired", "99 Magic", "Holy Empire", now - 86401)));
			assertEquals(0, rotator.totalAchievements());
			assertNull(rotator.current());
			assertFalse(rotator.animationRunning());
			rotator.stopAnimation();
		});
	}

	@Test public void spotlightAppearsAboveAnnouncementsAndWhileTheyLoad() throws Exception
	{
		FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		try
		{
			panel[0].update(
				Collections.singletonMap("member", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(() -> {
				((javax.swing.JToggleButton)find(panel[0], "holyEmpireAnnouncementsTab")).doClick();
				assertNotNull("Achievement display is visible even if official feed loads",
							  find(panel[0], "holyEmpireWomAchievementRotator"));
			});
			long now = Instant.now().getEpochSecond();
			EmpireCommunicationsClient.Notice notice = new EmpireCommunicationsClient.Notice(
				42, "announcement", "Official Notice", "Message", now, 0);
			panel[0].displayCommunications(
				new EmpireCommunicationsClient.Snapshot(now, 42, List.of(notice), List.of(), null),
				() -> true);
			panel[0].displayWomAchievements(new WomAchievementsClient.Snapshot(now, examples(4)),
											() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				Component spotlight = find(panel[0], "holyEmpireWomAchievementRotator");
				Component footer = find(panel[0], "holyEmpireWomAchievementFooter");
				Component official = find(panel[0], "holyEmpireAnnouncement:42");
				assertNotNull(spotlight);
				assertNotNull(footer);
				assertNotNull(official);
				Container content = spotlight.getParent();
				assertSame(spotlight, footer.getParent());
				assertSame(content, official.getParent());
				JLabel todays = (JLabel)find(panel[0], "holyEmpireAchievementsHeading");
				assertNotNull(todays);
				assertEquals("Today's Achievements", todays.getText());
				assertEquals(javax.swing.SwingConstants.CENTER, todays.getHorizontalAlignment());
				assertEquals(Integer.MAX_VALUE, todays.getMaximumSize().width);
				assertEquals(14f, todays.getFont().getSize2D(), 0f);
				Component announcementsHeading = null;
				for (Component child : content.getComponents())
					if (child instanceof JLabel &&
						"Announcements".equals(((JLabel)child).getText()))
						announcementsHeading = child;
				assertNotNull(announcementsHeading);
				assertTrue("Achievements label must be above the carousel",
						   content.getComponentZOrder(todays) <
							   content.getComponentZOrder(spotlight));
				assertTrue("Achievement carousel must be above announcements heading",
						   content.getComponentZOrder(spotlight) <
							   content.getComponentZOrder(announcementsHeading));
				assertTrue("Notices must follow announcements heading",
						   content.getComponentZOrder(announcementsHeading) <
							   content.getComponentZOrder(official));
			});
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}

	@Test public void frequentSidebarRemountsDoNotFreezeAchievementRotation() throws Exception
	{
		WomAchievementRotator rotator = new WomAchievementRotator(new Random(17));
		SwingUtilities.invokeAndWait(() -> {
			rotator.setAchievements(examples(6));
			rotator.addNotify();
			assertTrue(rotator.animationRunning());
			WomAchievementsClient.Achievement first = rotator.current();
			long origin = System.nanoTime();
			// Simulate page rebuilds during one 7-second hold period.
			for (int i = 1; i <= 3; i++)
			{
				rotator.removeNotify();
				rotator.addNotify();
				rotator.tick(origin + i * 2_000_000_000L);
			}
			rotator.tick(origin + (WomAchievementRotator.HOLD_MILLIS + 20) * 1_000_000L);
			rotator.tick(origin + (WomAchievementRotator.HOLD_MILLIS +
								   WomAchievementRotator.FADE_MILLIS + 70) *
									  1_000_000L);
			assertNotEquals("Rebuilding the sidebar must not restart rotation", first,
							rotator.current());
			rotator.removeNotify();
			assertFalse(rotator.animationRunning());
			rotator.stopAnimation();
		});
	}

	@Test public void feedUpdateDoesNotRebuildAnnouncementsPage() throws Exception
	{
		FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		try
		{
			panel[0].update(
				Collections.singletonMap("member", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			long now = Instant.now().getEpochSecond();
			panel[0].displayCommunications(
				new EmpireCommunicationsClient.Snapshot(now, 0, List.of(), List.of(), null),
				() -> true);
			panel[0].displayWomAchievements(new WomAchievementsClient.Snapshot(now, examples(4)),
											() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			final Container[] parent = {null};
			final WomAchievementRotator[] rotator = {null};
			SwingUtilities.invokeAndWait(() -> {
				((javax.swing.JToggleButton)find(panel[0], "holyEmpireAnnouncementsTab")).doClick();
				rotator[0] =
					(WomAchievementRotator)find(panel[0], "holyEmpireWomAchievementRotator");
				assertNotNull(rotator[0]);
				parent[0] = rotator[0].getParent();
				assertNotNull(parent[0]);
			});
			panel[0].displayWomAchievements(new WomAchievementsClient.Snapshot(now, examples(8)),
											() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertSame("Background WOM poll must not recreate the rotator", rotator[0],
						   find(panel[0], "holyEmpireWomAchievementRotator"));
				assertSame("Background WOM poll must not rebuild the layout", parent[0],
						   rotator[0].getParent());
				assertEquals(8, rotator[0].totalAchievements());
			});
		}
		finally
		{
			panel[0].stopPrivateCacheTimer();
		}
	}
}
