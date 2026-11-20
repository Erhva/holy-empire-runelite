/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Render selected blue CC tags after Player Indicators preserves player-name styling. */
final class FederationMenuTags
{
	private FederationMenuTags()
	{
	}

	static String apply(String target, List<String> clans, Color color, boolean enabled)
	{
		if (target == null || clans == null || clans.isEmpty())
		{
			return target;
		}
		String affiliation = String.join(", ", clans).replaceAll("[<>\\r\\n]", "");
		if (affiliation.isEmpty())
		{
			return target;
		}
		// On color/toggle changes, remove only this plugin's trailing CC suffix.
		String quoted = Pattern.quote(affiliation);
		String oldColored = " <col=[0-9a-fA-F]{6}>\\[(?:<img=[0-9]+> ?)?" + quoted + "\\]</col>$";
		String clean = target.replaceFirst(oldColored, "");
		clean = clean.replaceFirst(" \\[HE: " + quoted + "\\]$", "");
		if (!enabled || color == null)
		{
			return clean;
		}
		String hex = String.format(Locale.ROOT, "%06x", color.getRGB() & 0xffffff);
		return clean + " <col=" + hex + ">[" + affiliation + "]</col>";
	}

	static String append(String target, List<String> clans, Color color)
	{
		return apply(target, clans, color, true);
	}
}
