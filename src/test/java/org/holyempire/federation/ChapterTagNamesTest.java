/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChapterTagNamesTest
{
	private final List<String> empire = Collections.singletonList("Holy Empire");
	private final FederationConfig defaults = new FederationConfig() {};

	@Test public void allSeventeenChaptersHaveEditableTagsDefaultingToRealNames() throws Exception
	{
		assertEquals(17, HolyEmpireChapterCatalog.ORDERED.size());
		Set<Integer> positions = new HashSet<>();
		for (HolyEmpireChapterCatalog.Chapter chapter : HolyEmpireChapterCatalog.ORDERED)
		{
			String suffix = "";
			for (String segment : chapter.name.split(" "))
			{
				suffix += segment.substring(0, 1).toUpperCase() + segment.substring(1);
			}
			Method method = FederationConfig.class.getMethod("tagName" + suffix);
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			ConfigItem color = FederationConfig.class.getMethod("dotColor" + suffix)
								   .getAnnotation(ConfigItem.class);
			assertNotNull(chapter.name, item);
			assertEquals("chapterDotsSection", item.section());
			assertEquals(chapter.name, method.invoke(defaults));
			assertEquals("The tag editor follows its chapter color", color.position() + 1,
						 item.position());
			assertTrue("No configuration position collisions", positions.add(item.position()));
		}
		assertEquals("Holy Empire", ChapterTagNames.affiliation(defaults, empire));
	}

	@Test public void customAbbreviationsUseTheExistingAdvancedToggle()
	{
		FederationConfig enabled = new FederationConfig() {
			@Override public boolean useChapterDotColors()
			{
				return true;
			}
			@Override public String tagNameHolyEmpire()
			{
				return "HE";
			}
			@Override public String tagNameHolyChaos()
			{
				return "HC";
			}
		};
		FederationConfig disabled = new FederationConfig() {
			@Override public String tagNameHolyEmpire()
			{
				return "HE";
			}
		};
		List<String> both = Arrays.asList("Holy Chaos", "Holy Empire");
		assertEquals("HE", ChapterTagNames.affiliation(enabled, empire));
		assertEquals("HC, HE", ChapterTagNames.affiliation(enabled, both));
		assertEquals("Holy Empire", ChapterTagNames.affiliation(disabled, empire));
		assertEquals("Holy Chaos, Holy Empire", ChapterTagNames.affiliation(defaults, both));
		assertEquals("Unrecognized chapters remain unchanged", "Unknown",
					 ChapterTagNames.affiliation(enabled, Collections.singletonList("Unknown")));
	}

	@Test public void blankOverlongAndMarkupLabelsFallBackToRealChapterName()
	{
		for (String input :
			 Arrays.asList("  ", "<img=1>", "[HE]", "A\nB", "0123456789012345678901234567890",
						   "<col=ffffff>HE</col>"))
		{
			assertEquals(input, "Holy Empire", ChapterTagNames.safe(input, "Holy Empire"));
		}
		assertEquals("Holy Empire", ChapterTagNames.safe(null, "Holy Empire"));
		assertEquals("HE", ChapterTagNames.safe(" HE ", "Holy Empire"));
		assertEquals("The Guard", ChapterTagNames.safe(" The   Guard ", "Holy Empire"));
	}

	@Test public void menuLabelsReplacePriorAliasesAndNeverDuplicate()
	{
		Set<String> known = new HashSet<>();
		known.add("Holy Empire");
		known.add("HE");
		String base = "<img=4><col=ffffff>Erhva</col>";
		String old = FederationMenuTags.apply(base, empire, Color.BLUE, true, "Holy Empire", known);
		assertTrue(old.endsWith("[Holy Empire]</col>"));
		String next = FederationMenuTags.apply(old, empire, Color.BLUE, true, "HE", known);
		assertTrue(next.endsWith("[HE]</col>"));
		assertFalse(next.contains("[Holy Empire]"));
		assertEquals(next, FederationMenuTags.apply(next, empire, Color.BLUE, true, "HE", known));
		assertEquals(base, FederationMenuTags.apply(next, empire, Color.BLUE, false, "HE", known));
		assertEquals("Do not remove unrelated menu decorations",
					 base + " <col=ff0000>[Some Other Plugin]</col> <col=0000ff>[HE]</col>",
					 FederationMenuTags.apply(base + " <col=ff0000>[Some Other Plugin]</col>",
											  empire, Color.BLUE, true, "HE", known));
	}

	@Test public void chatMenuRecognitionSupportsCurrentAndPriorTagAliases()
	{
		Map<String, List<String>> members = new HashMap<>();
		members.put("erhva", empire);
		Map<String, Set<String>> known = new HashMap<>();
		known.put("Holy Empire", new HashSet<>(Arrays.asList("Holy Empire", "HE", "Empire")));
		assertEquals("erhva",
					 FederationChatMenuTags.rosterName(
						 "<col=ffffff>Erhva</col> <col=98c8f8>[HE]</col>", members, known));
		assertEquals("erhva",
					 FederationChatMenuTags.rosterName(
						 "<col=ffffff>Erhva</col> <col=98c8f8>[Empire]</col>", members, known));
		assertNull("An unknown suffix cannot impersonate an Empire tag",
				   FederationChatMenuTags.rosterName(
					   "<col=ffffff>Erhva</col> <col=98c8f8>[Fake]</col>", members, known));
		assertNull(FederationChatMenuTags.rosterName(
			"<col=ffffff>Not Member</col> <col=98c8f8>[HE]</col>", members, known));
	}
}
