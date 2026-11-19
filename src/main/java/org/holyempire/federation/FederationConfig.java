/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import net.runelite.client.config.*;

@ConfigGroup("holyEmpireFederation")
public interface FederationConfig extends Config {
	@ConfigSection(name = "Empire Indicators",
				   description =
					   "Default for Holy Empire dots, overhead names, and right-click CC tags.",
				   position = 0)
	String minimapDotSection = "minimapDotSection";

	@ConfigItem(keyName = "showFederationDots", name = "Show Holy Empire dots",
				description = "Highlight Holy Empire members on the minimap.", position = 0,
				section = minimapDotSection)
	default boolean
	showFederationDots()
	{
		return true;
	}

	@ConfigItem(keyName = "showOverheadNames", name = "Show overhead names",
				description = "Show Holy Empire players overhead using the indicator color; "
							  + "other Player Indicators categories keep priority.",
				position = 1, section = minimapDotSection)
	default boolean
	showOverheadNames()
	{
		return true;
	}

	@ConfigItem(keyName = "showClanMenu", name = "Show CC in right-click menu",
				description = "Append a colored CC tag to player right-click menus.", position = 2,
				section = minimapDotSection)
	default boolean
	showClanMenu()
	{
		return true;
	}

	@ConfigItem(keyName = "showClanRankIcons", name = "Show chapter rank in menu",
				description =
					"Display chapter rank icons before player names in right-click menus for "
					+
					"members of other chapters. Icons are hidden for members of your own chapters.",
				position = 3, section = minimapDotSection)
	default boolean
	showClanRankIcons()
	{
		return true;
	}

	@ConfigItem(keyName = "memberColor", name = "Indicator color",
				description = "Default color unless shared chapter colors are enabled.",
				position = 4, section = minimapDotSection)
	default Color
	memberColor()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigSection(name = "Chapter Indicators",
				   description =
					   "Choose which indicators appear for members in the same chapter as you.",
				   position = 6, closedByDefault = true)
	String sameChapterSection = "sameChapterSection";

	@ConfigItem(keyName = "showSameChapterOverhead", name = "Show chapter overhead names",
				description = "Show overhead names for players who share your chapter. Other "
							  + "Player Indicators categories keep priority.",
				position = 0, section = sameChapterSection)
	default boolean
	showSameChapterOverhead()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSameChapterMenu", name = "Show chapter tags in menu",
		description = "Show chapter brackets in right-click menus for members of your own chapter.",
		position = 1, section = sameChapterSection)
	default boolean
	showSameChapterMenu()
	{
		return true;
	}

	@ConfigItem(keyName = "showSameChapterDots", name = "Show chapter minimap dots",
				description = "Show Empire minimap dots for members of your chapter.", position = 2,
				section = sameChapterSection)
	default boolean
	showSameChapterDots()
	{
		return false;
	}

	@ConfigSection(name = "Advanced: Chapter colors",
				   description = "One shared color per chapter for minimap dots, overhead names, "
								 + "and right-click chapter tags.",
				   position = 10, closedByDefault = true)
	String chapterDotsSection = "chapterDotsSection";

	@ConfigItem(
		keyName = "useChapterDotColors", name = "Use custom chapter colors",
		description =
			"Apply each chapter color to minimap dots, overhead names, and right-click CC tags.",
		position = 0, section = chapterDotsSection)
	default boolean
	useChapterDotColors()
	{
		return false;
	}

	@ConfigItem(keyName = "dotColorHolyEmpire", name = "Holy Empire",
				description = "Shared indicator color for Holy Empire.", position = 1,
				section = chapterDotsSection)
	default Color
	dotColorHolyEmpire()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorWorld465", name = "World 465",
				description = "Shared indicator color for World 465.", position = 2,
				section = chapterDotsSection)
	default Color
	dotColorWorld465()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHouseOf465", name = "House of 465",
				description = "Shared indicator color for House of 465.", position = 3,
				section = chapterDotsSection)
	default Color
	dotColorHouseOf465()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolyGuard", name = "Holy Guard",
				description = "Shared indicator color for Holy Guard.", position = 4,
				section = chapterDotsSection)
	default Color
	dotColorHolyGuard()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolyKingdom", name = "Holy Kingdom",
				description = "Shared indicator color for Holy Kingdom.", position = 5,
				section = chapterDotsSection)
	default Color
	dotColorHolyKingdom()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorSacredOrder", name = "Sacred Order",
				description = "Shared indicator color for Sacred Order.", position = 6,
				section = chapterDotsSection)
	default Color
	dotColorSacredOrder()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolyHeart", name = "Holy Heart",
				description = "Shared indicator color for Holy Heart.", position = 7,
				section = chapterDotsSection)
	default Color
	dotColorHolyHeart()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorSixShooters", name = "Six Shooters",
				description = "Shared indicator color for Six Shooters.", position = 8,
				section = chapterDotsSection)
	default Color
	dotColorSixShooters()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorCadia", name = "Cadia",
				description = "Shared indicator color for Cadia.", position = 9,
				section = chapterDotsSection)
	default Color
	dotColorCadia()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolyCow", name = "Holy Cow",
				description = "Shared indicator color for Holy Cow.", position = 10,
				section = chapterDotsSection)
	default Color
	dotColorHolyCow()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolySoul", name = "Holy Soul",
				description = "Shared indicator color for Holy Soul.", position = 11,
				section = chapterDotsSection)
	default Color
	dotColorHolySoul()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolyCalf", name = "Holy Calf",
				description = "Shared indicator color for Holy Calf.", position = 12,
				section = chapterDotsSection)
	default Color
	dotColorHolyCalf()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolyLights", name = "Holy Lights",
				description = "Shared indicator color for Holy Lights.", position = 13,
				section = chapterDotsSection)
	default Color
	dotColorHolyLights()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolySmokes", name = "Holy Smokes",
				description = "Shared indicator color for Holy Smokes.", position = 14,
				section = chapterDotsSection)
	default Color
	dotColorHolySmokes()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorSacredBlade", name = "Sacred Blade",
				description = "Shared indicator color for Sacred Blade.", position = 15,
				section = chapterDotsSection)
	default Color
	dotColorSacredBlade()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorUltramarines", name = "Ultramarines",
				description = "Shared indicator color for Ultramarines.", position = 16,
				section = chapterDotsSection)
	default Color
	dotColorUltramarines()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "dotColorHolyChaos", name = "Holy Chaos",
				description = "Shared indicator color for Holy Chaos.", position = 17,
				section = chapterDotsSection)
	default Color
	dotColorHolyChaos()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "banSyncEndpoint", name = "Protected sync endpoint",
				description = "HTTPS URL of the privately authenticated central ban registry "
							  + "(not the public WOM roster URL).",
				position = 19, hidden = true)
	default String
	banSyncEndpoint()
	{
		return FederationBanUploadClient.OFFICIAL_ENDPOINT;
	}

	@ConfigItem(keyName = "banSyncToken", name = "Chapter upload credential",
				description = "Private chapter-scoped credential. Never share with other players.",
				position = 20, secret = true, hidden = true)
	default String
	banSyncToken()
	{
		return "";
	}
}
