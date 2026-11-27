/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prefix our rank icon before the player name; retain native Player Indicators icons. */
final class FederationMenuRankIcons
{
	private static final Pattern OLD_INSIDE_BRACKET =
		Pattern.compile("( <col=[0-9a-fA-F]{6}>\\[)<img=([0-9]+)> ?");

	private FederationMenuRankIcons()
	{
	}

	/**
	 * Player Indicators runs before this plugin and puts the clan/friends-chat
	 * rank before the player name. Its icon always wins over ours for people
	 * in our own chapter, even if a previous refresh left one of our icons first.
	 */
	static boolean hasNativeRankIcon(String text, Set<Integer> owned)
	{
		if (text == null)
		{
			return false;
		}
		int offset = 0;
		while (text.startsWith("<img=", offset))
		{
			int end = text.indexOf('>', offset + 5);
			if (end < 0 || end - offset > 16)
			{
				return false;
			}
			int index;
			try
			{
				index = Integer.parseInt(text.substring(offset + 5, end));
			}
			catch (NumberFormatException invalid)
			{
				return false;
			}
			if (owned == null || !owned.contains(index))
			{
				return true;
			}
			offset = end + 1;
		}
		return false;
	}

	static String apply(String text, int iconIndex, Set<Integer> owned, boolean enabled)
	{
		if (text == null)
		{
			return null;
		}
		String cleaned = text;
		// Remove only our prior prefix (not a native icon following it).
		while (cleaned.startsWith("<img="))
		{
			int end = cleaned.indexOf('>');
			if (end < 0 || end > 16)
			{
				break;
			}
			int index;
			try
			{
				index = Integer.parseInt(cleaned.substring(5, end));
			}
			catch (NumberFormatException ex)
			{
				break;
			}
			if (!owned.contains(index))
			{
				break;
			}
			cleaned = cleaned.substring(end + 1);
		}
		// Also clean up tags produced by earlier developer versions that embedded
		// our icon within [Chapter]; the icon now belongs before the player name.
		Matcher old = OLD_INSIDE_BRACKET.matcher(cleaned);
		if (old.find())
		{
			int prior;
			try
			{
				prior = Integer.parseInt(old.group(2));
			}
			catch (NumberFormatException e)
			{
				prior = -1;
			}
			if (owned.contains(prior))
			{
				cleaned =
					cleaned.substring(0, old.start()) + old.group(1) + cleaned.substring(old.end());
			}
		}
		return enabled && iconIndex >= 0 ? "<img=" + iconIndex + ">" + cleaned : cleaned;
	}
}
