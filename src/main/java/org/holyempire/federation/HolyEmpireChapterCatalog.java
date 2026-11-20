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
		final String name;
		final String iconKey;
		final String access;
		final String detail;

		Chapter(String name, String iconKey, String access, String detail)
		{
			this.name = name;
			this.iconKey = iconKey;
			this.access = access;
			this.detail = detail;
		}

		boolean hasWomRoster()
		{
			for (String configured : WomDirectoryClient.CLANS)
			{
				if (configured.equals(name))
				{
					return true;
				}
			}
			return false;
		}

		String subtitle()
		{
			return detail.isEmpty() ? access : access + " · " + detail;
		}
	}

	static final List<Chapter> ORDERED = Collections.unmodifiableList(Arrays.asList(
		new Chapter("Holy Empire", "holy-empire", "P2P only", ""),
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
		// No WOM group ID supplied for Ultramarines. Display it without inventing a roster.
		new Chapter("Ultramarines", "ultramarines", "F2P friendly", ""),
		new Chapter("Holy Chaos", "holy-chaos", "F2P friendly", "")));

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
