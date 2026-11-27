/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Presentation order and descriptions; WOM GROUPS/CLANS remain indexed by their existing IDs. */
final class HolyEmpireChapterCatalog
{
	static final class Chapter
	{
		final int groupId;
		final int order;
		final String clanChat;
		final String name;
		final String iconKey;
		final String iconSha256;
		final String access;
		final String detail;

		Chapter(String name, String iconKey, String access, String detail)
		{
			this(legacyId(name), name, legacyChat(name), iconKey, access, detail, 0);
		}

		Chapter(int groupId, String name, String clanChat, String iconKey, String access,
				String detail, int order)
		{
			this(groupId, name, clanChat, iconKey, access, detail, order, null);
		}

		Chapter(int groupId, String name, String clanChat, String iconKey, String access,
				String detail, int order, String iconSha256)
		{
			this.iconSha256 = iconSha256;
			this.groupId = groupId;
			this.name = name;
			this.clanChat = clanChat;
			this.iconKey = iconKey;
			this.access = access;
			this.detail = detail;
			this.order = order;
		}

		boolean hasWomRoster()
		{
			return groupId > 0;
		}

		String subtitle()
		{
			return detail.isEmpty() ? access : access + " · " + detail;
		}
	}

	static final List<Chapter> DEFAULT_ORDERED = Collections.unmodifiableList(
		Arrays.asList(new Chapter("Holy Empire", "holy-empire", "P2P only", ""),
					  new Chapter("World 465", "world-465", "P2P only", "EU/UK clan"),
					  new Chapter("House of 465", "house-of-465", "P2P only", "EU/UK clan"),
					  new Chapter("Holy Guard", "holy-guard", "P2P only", "PvP clan"),
					  new Chapter("Holy Kingdom", "holy-kingdom", "P2P only", ""),
					  new Chapter("Sacred Order", "sacred-order", "P2P only", "US clan"),
					  new Chapter("Holy Heart", "holy-heart", "P2P only", ""),
					  new Chapter("Six Shooters", "six-shooters", "F2P friendly", ""),
					  new Chapter("Cadia", "cadia", "P2P only", "US clan"),
					  new Chapter("Holy Cow", "holy-cow", "F2P friendly", ""),
					  new Chapter("Holy Soul", "holy-soul", "F2P friendly", ""),
					  new Chapter("Holy Calf", "holy-calf", "F2P friendly", ""),
					  new Chapter("Holy Lights", "holy-lights", "F2P friendly", ""),
					  new Chapter("Holy Smokes", "holy-smokes", "F2P friendly", ""),
					  new Chapter("Sacred Blade", "sacred-blade", "P2P only", "US clan"),
					  // WOM group 27094 is registered but currently empty; preserve legacy
					  // 16-group fallback until a valid 17-group V2 roster is published.
					  new Chapter("Ultramarines", "ultramarines", "F2P friendly", ""),
					  new Chapter("Holy Chaos", "holy-chaos", "F2P friendly", "")));

	static volatile List<Chapter> ORDERED = DEFAULT_ORDERED;

	private static int legacyId(String name)
	{
		for (int i = 0; i < WomDirectoryClient.CLANS.length; i++)
		{
			if (WomDirectoryClient.CLANS[i].equals(name))
			{
				return WomDirectoryClient.GROUPS[i];
			}
		}
		return 0;
	}

	private static String legacyChat(String name)
	{
		for (int i = 0; i < WomDirectoryClient.CLANS.length; i++)
		{
			if (WomDirectoryClient.CLANS[i].equals(name))
			{
				return WomDirectoryClient.CLAN_CHATS[i];
			}
		}
		return name;
	}

	static void update(ChapterDirectory directory)
	{
		ORDERED = directory.ordered;
	}

	private HolyEmpireChapterCatalog()
	{
	}

	static Chapter get(String name)
	{
		for (Chapter chapter : ORDERED)
		{
			if (chapter.name.equals(name))
			{
				return chapter;
			}
		}
		return null;
	}
}
