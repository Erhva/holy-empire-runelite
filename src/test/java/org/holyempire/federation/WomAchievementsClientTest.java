/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import org.junit.Test;
import static org.junit.Assert.*;

public class WomAchievementsClientTest
{
	@Rule public TemporaryFolder folder = new TemporaryFolder();

	@Test public void diskCacheRestoresLatestVerifiedNewsWithoutNetwork() throws Exception
	{
		long now = Instant.now().getEpochSecond();
		Filepath path = Filepath.Unchecked.getRooted(folder.getRoot().toPath())
							.joinSegment("wom-achievements.json");
		byte[] payload = feed(now, entry("Example", "99 Agility", now - 30));
		WomAchievementsClient.parse(payload, Instant.now());
		WomAchievementsClient.writeCached(path, payload);
		WomAchievementsClient.Snapshot restored = WomAchievementsClient.readCached(path);
		assertNotNull(restored);
		assertEquals(1, restored.entries.size());
		assertEquals("Example", restored.entries.get(0).player);
		assertEquals("99 Agility", restored.entries.get(0).description);
	}

	@Test public void diskCacheRejectsStaleMalformedOrOversizedFeeds() throws Exception
	{
		long now = Instant.now().getEpochSecond();
		Filepath file = Filepath.Unchecked.getRooted(folder.getRoot().toPath())
							.joinSegment("wom-achievements.json");
		assertNull(WomAchievementsClient.readCached(file));
		for (byte[] invalid :
			 new byte[][] {feed(now - 7201, entry("Example", "99 Magic", now - 100)),
						   feed(now, entry("<script>", "99 Magic", now - 100)), new byte[64001]})
		{
			file.write(invalid);
			if (invalid.length > 64000)
				assertNull(WomAchievementsClient.readCached(file));
			else
			{
				try
				{
					WomAchievementsClient.readCached(file);
					fail("Rejected unsafe or expired cached feed");
				}
				catch (java.io.IOException expected)
				{
				}
			}
		}
	}

	@Test public void diskCacheCannotReadSymlinkOutsidePluginDirectory() throws Exception
	{
		long now = Instant.now().getEpochSecond();
		Path outside = folder.newFile("outside-news.json").toPath();
		Files.write(outside, feed(now, entry("Example", "99 Magic", now - 20)));
		Path inside = folder.newFolder("plugin-data").toPath();
		Files.createSymbolicLink(inside.resolve("wom-achievements.json"), outside);
		Filepath file = Filepath.Unchecked.getRooted(inside).joinSegment("wom-achievements.json");
		try
		{
			WomAchievementsClient.readCached(file);
			fail("Local public-news cache must reject filesystem symlinks");
		}
		catch (java.io.IOException | SecurityException expected)
		{
		}
	}

	private static byte[] feed(long now, String entries)
	{
		return ("{\"schema_version\":1,\"kind\":\"holy-empire-wom-achievements\","
				+ "\"generated_at\":" + now + ",\"window_hours\":24,\"entries\":[" + entries + "]}")
			.getBytes(StandardCharsets.UTF_8);
	}

	private static String entry(String player, String title, long timestamp)
	{
		return "{\"player\":\"" + player + "\",\"achievement\":\"" + title +
			"\",\"chapter\":\"Holy Guard\",\"created_at\":" + timestamp + "}";
	}

	private static Component find(Component root, String name)
	{
		if (name.equals(root.getName()))
			return root;
		if (root instanceof Container)
			for (Component child : ((Container)root).getComponents())
			{
				Component match = find(child, name);
				if (match != null)
					return match;
			}
		return null;
	}

	@Test public void parsesAndDeduplicates24HourFeed() throws Exception
	{
		long now = 1800000000L;
		String recent = entry("Example", "99 Agility", now - 60);
		WomAchievementsClient.Snapshot result = WomAchievementsClient.parse(
			feed(now, recent + "," + recent + "," + entry("Old", "100 Boss KC", now - 90000)),
			Instant.ofEpochSecond(now));
		assertEquals(1, result.entries.size());
		assertEquals("Example", result.entries.get(0).player);
		assertEquals("99 Agility", result.entries.get(0).description);
	}

	@Test public void rejectsStaleAndInjectedFeed() throws Exception
	{
		long now = 1800000000L;
		for (byte[] bytes : new byte[][] {feed(now - 7201, entry("Member", "99 Ranged", now - 45)),
										  feed(now, entry("<script>", "Bad", now - 45)),
										  feed(now, entry("Member", "<b>99 Magic</b>", now - 45))})
		{
			try
			{
				WomAchievementsClient.parse(bytes, Instant.ofEpochSecond(now));
				fail("Rejected feed was accepted");
			}
			catch (java.io.IOException expected)
			{
			}
		}
	}

	@Test public void announcementsDisplayWomNewsBelowOfficialMessages() throws Exception
	{
		FederationPanel[] p = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> p[0] = new FederationPanel());
		try
		{
			p[0].update(
				Collections.singletonMap("example", Collections.singletonList("Holy Guard")),
				Collections.emptyMap());
			long now = Instant.now().getEpochSecond();
			p[0].displayCommunications(
				new EmpireCommunicationsClient.Snapshot(now, 0, List.of(), List.of(), null),
				() -> true);
			p[0].displayWomAchievements(
				new WomAchievementsClient.Snapshot(
					now, List.of(new WomAchievementsClient.Achievement("Example", "99 Agility",
																	   "Holy Guard", now - 30))),
				() -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p[0], "holyEmpireAnnouncementsTab")).doClick();
				assertEquals("Today's Achievements",
							 ((JLabel)find(p[0], "holyEmpireAchievementsHeading")).getText());
				assertNotNull(find(p[0], "holyEmpireWomAchievementRotator"));
				assertEquals("Example",
							 ((JLabel)find(p[0], "holyEmpireWomAchievementPlayer")).getText());
				assertEquals("99 Agility", ((javax.swing.JTextPane)find(
												p[0], "holyEmpireWomAchievementDescription"))
											   .getText());
				assertEquals("Holy Guard",
							 ((JLabel)find(p[0], "holyEmpireWomAchievementChapter")).getText());
				assertNull("Old multi-card achievement list must be removed",
						   find(p[0], "holyEmpireWomAchievement:0"));
			});
		}
		finally
		{
			p[0].stopPrivateCacheTimer();
		}
	}
}
