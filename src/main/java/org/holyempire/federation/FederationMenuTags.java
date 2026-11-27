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
import java.util.Set;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Render selected blue CC tags after Player Indicators preserves player-name styling. */
final class FederationMenuTags
{
	private FederationMenuTags()
	{
	}

	static String apply(String target, List<String> clans, Color color, boolean enabled)
	{
		return apply(target, clans, color, enabled, clans == null ? "" : String.join(", ", clans),
					 Collections.emptySet());
	}

	/**
	 * Keep canonical roster identities for recognition, while showing an
	 * optional user-facing abbreviation. Known former labels allow edits to
	 * update a menu in place instead of piling up old bracketed tags.
	 */
	static String apply(String target, List<String> clans, Color color, boolean enabled,
						String displayName, Set<String> knownNames)
	{
		if (target == null || clans == null || clans.isEmpty())
		{
			return target;
		}
		String canonical = String.join(", ", clans).replaceAll("[<>\\r\\n]", "");
		String display = displayName == null || displayName.isEmpty() ? canonical : displayName;
		if (canonical.isEmpty())
		{
			return target;
		}
		// Remove only a suffix known to have been produced by Holy Empire.
		// Leave other plugins' colored brackets alone.
		String clean = target;
		Matcher suffix =
			Pattern
				.compile(
					" <col=[0-9a-fA-F]{6}>\\[(?:<img=[0-9]+> ?)?([^<>\\r\\n]{1,240})\\]</col>$")
				.matcher(target);
		if (suffix.find() &&
			(canonical.equals(suffix.group(1)) || display.equals(suffix.group(1)) ||
			 knownNames != null && knownNames.contains(suffix.group(1))))
		{
			clean = target.substring(0, suffix.start());
		}
		clean = clean.replaceFirst(" \\[HE: " + Pattern.quote(canonical) + "\\]$", "");
		if (!enabled || color == null)
		{
			return clean;
		}
		String hex = String.format(Locale.ROOT, "%06x", color.getRGB() & 0xffffff);
		return clean + " <col=" + hex + ">[" + display + "]</col>";
	}

	static String append(String target, List<String> clans, Color color)
	{
		return apply(target, clans, color, true);
	}
}
