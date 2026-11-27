/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;
import static org.junit.Assert.*;

public class SameChapterVisibilityTest
{
	private static List<String> chapters(String... names)
	{
		return Arrays.asList(names);
	}

	@Test public void overlappingAnyVerifiedChapterMakesPlayersSameChapter()
	{
		assertTrue(
			SameChapterVisibility.isSameChapter(chapters("Holy Empire"), chapters("Holy Empire")));
		assertTrue(SameChapterVisibility.isSameChapter(chapters("Holy Chaos", "Holy Empire"),
													   chapters("Sacred Blade", "Holy Empire")));
		assertTrue(SameChapterVisibility.isSameChapter(chapters("  holy Empire  "),
													   chapters("HOLY EMPIRE")));
		assertFalse(
			SameChapterVisibility.isSameChapter(chapters("Holy Empire"), chapters("Holy Chaos")));
		assertFalse(
			SameChapterVisibility.isSameChapter(chapters("Holy Empire"), chapters("Holy Heart")));
		assertFalse(
			SameChapterVisibility.isSameChapter(Collections.emptyList(), chapters("Holy Empire")));
		assertFalse(
			SameChapterVisibility.isSameChapter(chapters("Holy Empire"), Collections.emptyList()));
		assertFalse(SameChapterVisibility.isSameChapter(null, chapters("Holy Empire")));
		assertFalse(SameChapterVisibility.isSameChapter(chapters("Holy Empire"), null));
	}

	@Test public void sameChapterRankIsIndependentAndDefersToNativeRanks()
	{
		// Other chapters retain the existing global option.
		assertTrue(SameChapterVisibility.showRankIcon(true, false, false, false, true));
		assertFalse(SameChapterVisibility.showRankIcon(false, false, true, true, false));

		// Own-chapter ranks require both toggles, and defer to native icons.
		assertTrue(SameChapterVisibility.showRankIcon(false, true, true, true, false));
		assertFalse(SameChapterVisibility.showRankIcon(true, true, false, true, false));
		assertFalse(SameChapterVisibility.showRankIcon(true, true, true, true, true));

		// The Player Indicators option must not be bypassed by our local toggle.
		assertFalse(SameChapterVisibility.showRankIcon(true, true, true, false, false));
		assertFalse(SameChapterVisibility.showRankIcon(false, true, false, false, true));
	}

	@Test public void fourSameChapterTogglesWorkIndependentlyAndPreserveGlobalToggles()
	{
		FederationConfig defaults = new FederationConfig() {};
		assertTrue(defaults.showSameChapterMenu());
		assertTrue(defaults.showSameChapterRankIcons());
		assertTrue(defaults.showSameChapterOverhead());
		assertFalse(defaults.showSameChapterDots());

		assertTrue(SameChapterVisibility.showMenuTag(true, true, true));
		assertFalse(SameChapterVisibility.showMenuTag(true, true, false));
		assertTrue(SameChapterVisibility.showMenuTag(true, false, false));
		assertFalse(SameChapterVisibility.showMenuTag(false, false, true));
		assertTrue(SameChapterVisibility.showMenuTag(false, true, true));
		assertFalse(SameChapterVisibility.showMenuTag(true, true, false));

		assertFalse(SameChapterVisibility.showOverhead(true, true, false));
		assertTrue(SameChapterVisibility.showOverhead(true, true, true));
		assertTrue(SameChapterVisibility.showOverhead(true, false, false));
		assertFalse(SameChapterVisibility.showOverhead(false, false, true));

		assertFalse(SameChapterVisibility.showDot(true, true, false));
		assertTrue(SameChapterVisibility.showDot(true, true, true));
		assertTrue(SameChapterVisibility.showDot(true, false, false));
		assertFalse(SameChapterVisibility.showDot(false, false, true));

		// Only an enabled same-chapter dot can replace a native clan dot.
		assertFalse(FederationDotPriority.allowFederationDot(false, false, true, 0, 0));
		assertTrue(FederationDotPriority.allowFederationDot(false, false, false, 0, 0));
		assertFalse(FederationDotPriority.allowFederationDot(true, false, false, 0, 0));
		assertFalse(FederationDotPriority.allowFederationDot(false, true, false, 0, 0));
		assertFalse(FederationDotPriority.allowFederationDot(false, false, false, 17, 17));
	}

	@Test public void sameChapterSettingsAreExplicitOptions() throws Exception
	{
		ConfigSection section = FederationConfig.class.getField("sameChapterSection")
									.getAnnotation(ConfigSection.class);
		assertNotNull(section);
		assertEquals("Chapter Indicators", section.name());
		assertEquals("Choose which indicators appear for members in the same chapter as you.",
					 section.description());
		assertTrue(section.closedByDefault());
		assertEquals("Show the chapter name in right-click menus for members of your own chapter.",
					 FederationConfig.class.getMethod("showSameChapterMenu")
						 .getAnnotation(ConfigItem.class)
						 .description());
		assertEquals("Show Empire minimap dots for members of your chapter.",
					 FederationConfig.class.getMethod("showSameChapterDots")
						 .getAnnotation(ConfigItem.class)
						 .description());
		for (String key : chapters("showSameChapterOverhead", "showSameChapterMenu",
								   "showSameChapterRankIcons", "showSameChapterDots"))
		{
			Method method = FederationConfig.class.getMethod(key);
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			assertNotNull("Missing toggle " + key, item);
			assertEquals("sameChapterSection", item.section());
			assertEquals(key.equals("showSameChapterOverhead") ? "Show chapter overhead names"
						 : key.equals("showSameChapterMenu")   ? "Show chapter in right-click menu"
						 : key.equals("showSameChapterRankIcons") ? "Show rank in right-click menu"
																  : "Show chapter minimap dots",
						 item.name());
		}
	}

	@Test public void allWikiSpecialAndCustomRankSpritesAreBundled()
	{
		for (String rank :
			 chapters("owner", "deputy_owner", "administrator", "guest", "tzkal", "tztok",
					  "captain", "legend", "vanguard", "brigadier", "priest", "doctor",
					  "zamorakian", "saradominist", "red_topaz", "short_green_guy"))
		{
			javax.swing.Icon icon = HolyEmpireAssets.rankIcon(rank);
			assertNotNull("OSRS Wiki rank icon missing: " + rank, icon);
			assertTrue(icon.getIconWidth() > 0);
		}
		assertNull(HolyEmpireAssets.rankIcon("unknown_title_not_on_wiki"));
		assertNotNull("Keep the prior custom Emperor exception", HolyEmpireAssets.emperorIcon());
	}
}
