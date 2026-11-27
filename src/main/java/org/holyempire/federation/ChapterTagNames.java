/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.util.ArrayList;
import java.util.List;

/**
 * User-facing right-click abbreviations. The original WOM chapter identities
 * and names never change; options are active only with the advanced toggle.
 */
final class ChapterTagNames
{
	private ChapterTagNames()
	{
	}

	static String affiliation(FederationConfig config, List<String> chapters)
	{
		if (chapters == null || chapters.isEmpty())
		{
			return "";
		}
		List<String> display = new ArrayList<>();
		for (String chapter : chapters)
		{
			if (chapter == null || chapter.isEmpty())
			{
				continue;
			}
			display.add(config != null && config.useChapterDotColors() ? forChapter(config, chapter)
																	   : chapter);
		}
		return String.join(", ", display);
	}

	static String safe(String configured, String defaultName)
	{
		if (configured == null)
		{
			return defaultName;
		}
		if (configured.codePoints().anyMatch(Character::isISOControl))
		{
			return defaultName;
		}
		String trimmed = configured.trim().replaceAll("\\s+", " ");
		// Disallow markup/control delimiters and excessively long menu text.
		// A blank/invalid setting falls back to the real chapter name.
		return trimmed.length() >= 1 && trimmed.length() <= 24 &&
				trimmed.matches("[\\p{L}\\p{N}][\\p{L}\\p{N} ._'-]{0,23}")
			? trimmed
			: defaultName;
	}

	private static String forChapter(FederationConfig config, String chapter)
	{
		switch (chapter)
		{
		case "Holy Empire":
			return safe(config.tagNameHolyEmpire(), chapter);
		case "World 465":
			return safe(config.tagNameWorld465(), chapter);
		case "House of 465":
			return safe(config.tagNameHouseOf465(), chapter);
		case "Holy Guard":
			return safe(config.tagNameHolyGuard(), chapter);
		case "Holy Kingdom":
			return safe(config.tagNameHolyKingdom(), chapter);
		case "Sacred Order":
			return safe(config.tagNameSacredOrder(), chapter);
		case "Holy Heart":
			return safe(config.tagNameHolyHeart(), chapter);
		case "Six Shooters":
			return safe(config.tagNameSixShooters(), chapter);
		case "Cadia":
			return safe(config.tagNameCadia(), chapter);
		case "Holy Cow":
			return safe(config.tagNameHolyCow(), chapter);
		case "Holy Soul":
			return safe(config.tagNameHolySoul(), chapter);
		case "Holy Calf":
			return safe(config.tagNameHolyCalf(), chapter);
		case "Holy Lights":
			return safe(config.tagNameHolyLights(), chapter);
		case "Holy Smokes":
			return safe(config.tagNameHolySmokes(), chapter);
		case "Sacred Blade":
			return safe(config.tagNameSacredBlade(), chapter);
		case "Ultramarines":
			return safe(config.tagNameUltramarines(), chapter);
		case "Holy Chaos":
			return safe(config.tagNameHolyChaos(), chapter);
		default:
			return chapter;
		}
	}
}
