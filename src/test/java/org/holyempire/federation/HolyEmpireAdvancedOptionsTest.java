/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireAdvancedOptionsTest
{
	@Test public void allThreeChannelsUseSameChapterColorOrSharedFallback()
	{
		FederationConfig config = new FederationConfig() {
			@Override public Color memberColor()
			{
				return Color.BLUE;
			}
			@Override public Color dotColorHolyEmpire()
			{
				return Color.RED;
			}
			@Override public Color dotColorHolyChaos()
			{
				return Color.ORANGE;
			}
			@Override public boolean useChapterDotColors()
			{
				return true;
			}
		};
		List<String> both = Arrays.asList("Holy Chaos", "Holy Empire");
		assertEquals("First configured chapter wins", "Holy Empire",
					 ChapterIndicatorColors.primaryChapter(both));
		for (List<String> chapters : Arrays.asList(both, Collections.singletonList("Holy Chaos"),
												   Collections.singletonList("Unknown")))
		{
			Color dot = ChapterIndicatorColors.dotColor(config, chapters);
			assertEquals(dot, ChapterIndicatorColors.overheadColor(config, chapters));
			assertEquals(dot, ChapterIndicatorColors.menuColor(config, chapters));
		}
		assertEquals(Color.RED, ChapterIndicatorColors.menuColor(config, both));
		assertEquals(Color.ORANGE, ChapterIndicatorColors.overheadColor(
									   config, Collections.singletonList("Holy Chaos")));
		assertEquals(Color.BLUE,
					 ChapterIndicatorColors.menuColor(config, Collections.singletonList("Other")));
		FederationConfig original = new FederationConfig() {
			@Override public Color memberColor()
			{
				return Color.MAGENTA;
			}
			@Override public Color dotColorHolyEmpire()
			{
				return Color.RED;
			}
		};
		for (Color color : Arrays.asList(ChapterIndicatorColors.dotColor(
											 original, Collections.singletonList("Holy Empire")),
										 ChapterIndicatorColors.overheadColor(
											 original, Collections.singletonList("Holy Empire")),
										 ChapterIndicatorColors.menuColor(
											 original, Collections.singletonList("Holy Empire"))))
		{
			assertEquals(Color.MAGENTA, color);
		}
		assertTrue(original.showClanRankIcons());
		assertFalse(original.useChapterDotColors());
	}

	@Test public void oneSectionOffersExactlyOnePickerForEachChapter() throws Exception
	{
		int configured = 0;
		for (HolyEmpireChapterCatalog.Chapter chapter : HolyEmpireChapterCatalog.ORDERED)
		{
			String suffix = "";
			for (String token : chapter.name.split(" "))
			{
				suffix += token.substring(0, 1).toUpperCase() + token.substring(1);
			}
			ConfigItem item = FederationConfig.class.getMethod("dotColor" + suffix)
								  .getAnnotation(ConfigItem.class);
			assertNotNull(chapter.name, item);
			assertEquals("chapterDotsSection", item.section());
			configured++;
		}
		assertEquals(17, configured);
		ConfigSection section = FederationConfig.class.getField("chapterDotsSection")
									.getAnnotation(ConfigSection.class);
		assertEquals("Advanced: Chapter Colors", section.name());
		assertTrue(section.closedByDefault());
		assertEquals("Empire Indicators", FederationConfig.class.getField("minimapDotSection")
											  .getAnnotation(ConfigSection.class)
											  .name());
		String[] empireSettings = {"showFederationDots", "showOverheadNames", "showClanMenu",
								   "showClanRankIcons", "memberColor"};
		for (int position = 0; position < empireSettings.length; position++)
		{
			ConfigItem item = FederationConfig.class.getMethod(empireSettings[position])
								  .getAnnotation(ConfigItem.class);
			assertNotNull(item);
			assertEquals("minimapDotSection", item.section());
			assertEquals("Empire Indicators order: " + empireSettings[position], position,
						 item.position());
		}
		assertEquals(
			"Indicator color",
			FederationConfig.class.getMethod("memberColor").getAnnotation(ConfigItem.class).name());
		for (String old : new String[] {"developerSection", "privateBanSyncSection"})
		{
			try
			{
				FederationConfig.class.getField(old);
				fail(old + " must not appear in regular RuneLite settings");
			}
			catch (NoSuchFieldException expected)
			{
			}
		}
		try
		{
			FederationConfig.class.getMethod("previewUnverifiedScreen");
			fail("Developer options must not appear in RuneLite settings");
		}
		catch (NoSuchMethodException expected)
		{
		}
		for (String name : new String[] {"banSyncEndpoint", "banSyncToken"})
		{
			ConfigItem hidden =
				FederationConfig.class.getMethod(name).getAnnotation(ConfigItem.class);
			assertTrue("Officer-only setting must remain hidden from regular configuration",
					   hidden.hidden());
			assertEquals("", hidden.section());
		}
		assertTrue(FederationConfig.class.getMethod("banSyncToken")
					   .getAnnotation(ConfigItem.class)
					   .secret());
		ConfigItem rankSetting =
			FederationConfig.class.getMethod("showClanRankIcons").getAnnotation(ConfigItem.class);
		assertNotNull(rankSetting);
		assertEquals("Show chapter rank in menu", rankSetting.name());
		assertEquals("showClanRankIcons", rankSetting.keyName());
		try
		{
			FederationConfig.class.getMethod("emergencyDirectWomFallback");
			fail("Emergency direct WOM fallback should not appear in settings");
		}
		catch (NoSuchMethodException expected)
		{
			// Setting and behavioral fallback are intentionally retired.
		}
		for (java.lang.reflect.Method m : FederationConfig.class.getMethods())
		{
			assertFalse("Old independent overhead color picker remains: " + m.getName(),
						m.getName().startsWith("overheadColor"));
		}
	}

	@Test public void menuRankIconRestoredToPrefixWithoutDuplicatingNativeIcons()
	{
		Set<Integer> owned = new HashSet<>(Collections.singletonList(90));
		String nativePlayer = "<img=4><col=ff0000>Player</col> (level-126)";
		String chapter = " <col=98c8f8>[Holy Chaos]</col>";
		String with = FederationMenuRankIcons.apply(nativePlayer + chapter, 90, owned, true);
		assertEquals("<img=90>" + nativePlayer + chapter, with);
		assertEquals(with, FederationMenuRankIcons.apply(with, 90, owned, true));
		assertEquals(nativePlayer + chapter, FederationMenuRankIcons.apply(with, -1, owned, false));
		assertEquals("<img=90>" + nativePlayer,
					 FederationMenuRankIcons.apply(nativePlayer, 90, owned, true));
		assertEquals(nativePlayer,
					 FederationMenuRankIcons.apply("<img=90>" + nativePlayer, -1, owned, false));
		String priorEmbedded = nativePlayer + " <col=98c8f8>[<img=90> Holy Chaos]</col>";
		assertEquals(with, FederationMenuRankIcons.apply(priorEmbedded, 90, owned, true));
		assertEquals(nativePlayer + chapter,
					 FederationMenuRankIcons.apply(priorEmbedded, -1, owned, false));
		String otherIcon = nativePlayer + " <col=98c8f8>[<img=25> Holy Chaos]</col>";
		assertEquals(otherIcon, FederationMenuRankIcons.apply(otherIcon, -1, owned, false));
		assertNull(FederationMenuRankIcons.apply(null, 90, owned, true));
	}

	@Test public void nativePlayerIndicatorsRankWinsForSameChapterMenus()
	{
		Set<Integer> own = new HashSet<>(Collections.singletonList(90));
		String name = "<col=ff0000>Player</col> (level-126)";
		String chapter = " <col=98c8f8>[Holy Empire]</col>";
		String nativeIcon = "<img=4>";
		assertTrue(FederationMenuRankIcons.hasNativeRankIcon(nativeIcon + name, own));
		assertTrue(FederationMenuRankIcons.hasNativeRankIcon("<img=90>" + nativeIcon + name, own));
		assertFalse(FederationMenuRankIcons.hasNativeRankIcon("<img=90>" + name, own));
		assertFalse(FederationMenuRankIcons.hasNativeRankIcon(name, own));
		assertFalse(FederationMenuRankIcons.hasNativeRankIcon(null, own));

		// The Player Indicators icon stays first, and a legacy Holy Empire
		// prefix is cleaned without disturbing the native icon or chapter.
		boolean showRank = SameChapterVisibility.showRankIcon(
			true, true, true, true,
			FederationMenuRankIcons.hasNativeRankIcon(nativeIcon + name, own));
		assertFalse(showRank);
		String existing = "<img=90>" + nativeIcon + name + chapter;
		assertEquals(nativeIcon + name + chapter,
					 FederationMenuRankIcons.apply(existing, -1, own, showRank));
		// If Player Indicators does not render a rank, our own toggle can
		// supply one without changing the independently controlled CC tag.
		assertTrue(SameChapterVisibility.showRankIcon(false, true, true, true, false));
		assertEquals("<img=90>" + name + chapter,
					 FederationMenuRankIcons.apply(name + chapter, 90, own, true));
		assertEquals(name, FederationMenuTags.apply(name, Collections.singletonList("Holy Empire"),
													java.awt.Color.BLUE, false));
	}

	@Test public void emperorAndRankIndexRetainMembershipBoundaries()
	{
		FederationSession session = new FederationSession();
		session.observe("captain", "Holy Empire");
		Map<String, List<String>> combined = new HashMap<>();
		combined.put("captain", Collections.singletonList("Holy Empire"));
		combined.put("not needed", Collections.singletonList("Holy Empire"));
		Map<String, List<WomDirectoryClient.ChapterMember>> groups = new HashMap<>();
		groups.put("Holy Chaos", Collections.singletonList(
									 new WomDirectoryClient.ChapterMember("Captain", "general")));
		groups.put("Holy Empire",
				   Arrays.asList(new WomDirectoryClient.ChapterMember("Captain", "deputy_owner"),
								 new WomDirectoryClient.ChapterMember("Not Needed", "owner")));
		assertTrue(session.publish("captain", session.generation(),
								   new WomDirectoryClient.Snapshot(combined, groups)));
		assertEquals("deputy_owner", session.rankFor("Captain").rank);
		assertEquals("Emperor", HolyEmpireRankDisplay.title(session.rankFor("NOT NEEDED")));
		session.observe("outsider", "Holy Empire");
		assertNull(session.rankFor("Captain"));
	}
}
