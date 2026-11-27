/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.Text;

/**
 * Resolve chat-menu player names without relying on a world Player object.
 * Only RuneLite's native chat-player actions qualify; unrelated widgets and
 * chat-message content are never interpreted as a player identity.
 */
final class FederationChatMenuTags
{
	private static final Pattern OUR_CHAPTER_SUFFIX =
		Pattern.compile(" <col=[0-9a-fA-F]{6}>\\[([^<>\\]\\r\\n]{1,128})\\]</col>$");

	private FederationChatMenuTags()
	{
	}

	/** Identify chat-player actions without changing native targets. */
	static boolean isChatPlayerIdentityAction(MenuAction action, int packedWidget, String option)
	{
		if (action != MenuAction.CC_OP && action != MenuAction.CC_OP_LOW_PRIORITY)
		{
			return false;
		}
		int group = WidgetUtil.componentToInterface(packedWidget);
		if (group != InterfaceID.CHATBOX && group != InterfaceID.PM_CHAT &&
			group != InterfaceID.CHATCHANNEL_CURRENT)
		{
			return false;
		}
		return java.util.Arrays.asList("Add friend", "Remove friend",
			"Add ignore", "Remove ignore", "Message", "Reply",
			"Report", "Kick", "Ban", "Lookup").contains(option);
	}

	static boolean isChatPlayerMenu(MenuAction action, int packedWidget, String option)
	{
		if (action != MenuAction.CC_OP && action != MenuAction.CC_OP_LOW_PRIORITY)
		{
			return false;
		}
		int group = WidgetUtil.componentToInterface(packedWidget);
		if (group != InterfaceID.CHATBOX && group != InterfaceID.PM_CHAT &&
			group != InterfaceID.CHATCHANNEL_CURRENT)
		{
			return false;
		}
		if (option == null)
		{
			return false;
		}
		switch (option)
		{
		// Native social, moderation and messaging handlers can consume the
		// menu target as the actual account name. Never add a chapter suffix
		// or rank icon to these entries: it can corrupt "Add friend" and
		// other original RuneScape actions.
		case "Add friend":
		case "Remove friend":
		case "Add ignore":
		case "Remove ignore":
		case "Message":
		case "Reply":
		case "Report":
		case "Kick":
		case "Ban":
			return false;
		case "Lookup":
			return true;
		default:
			return false;
		}
	}

	/**
	 * Return a normalized roster key only when the displayed chat target is
	 * exactly a registered RSN. On repeated decoration accept only an existing
	 * chapter suffix that matches that same account's roster chapters.
	 */
	static String rosterName(String target, Map<String, List<String>> roster)
	{
		return rosterName(target, roster, java.util.Collections.emptyMap());
	}

	static String rosterName(String target, Map<String, List<String>> roster,
							 Map<String, Set<String>> knownLabels)
	{
		if (target == null || target.length() > 280 || roster == null || roster.isEmpty())
		{
			return null;
		}
		String key = WomDirectoryClient.normalize(Text.removeTags(target));
		if (member(key, roster))
		{
			return key;
		}
		Matcher matcher = OUR_CHAPTER_SUFFIX.matcher(target);
		if (!matcher.find())
		{
			return null;
		}
		String bare = target.substring(0, matcher.start());
		key = WomDirectoryClient.normalize(Text.removeTags(bare));
		if (!member(key, roster))
		{
			return null;
		}
		String expected = String.join(", ", roster.get(key)).replaceAll("[<>\\r\\n]", "");
		Set<String> aliases = knownLabels == null ? null : knownLabels.get(expected);
		return expected.equals(matcher.group(1)) ||
				aliases != null && aliases.contains(matcher.group(1))
			? key
			: null;
	}

	private static boolean member(String key, Map<String, List<String>> roster)
	{
		List<String> chapters = roster.get(key);
		return key != null && !key.isEmpty() && chapters != null && !chapters.isEmpty();
	}
}
