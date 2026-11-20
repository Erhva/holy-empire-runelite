/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.*;
import net.runelite.client.util.Text;

/** Pure recruitment menu identification and authorized ban-directory matching. */
final class FederationRecruitmentGuard
{
	private FederationRecruitmentGuard()
	{
	}

	static String recruitmentTarget(String action, String playerName, String menuTarget)
	{
		if (action == null || !"recruit".equalsIgnoreCase(Text.removeTags(action).trim()))
		{
			return null;
		}
		String candidate = playerName == null || playerName.isBlank()
							   ? Text.removeTags(menuTarget == null ? "" : menuTarget).trim()
							   : playerName.trim();
		// The native player menu often adds combat level and our chapter tag.
		int detail = candidate.indexOf(" (level-");
		if (detail < 0)
		{
			detail = candidate.indexOf(" (skill-");
		}
		if (detail >= 0)
		{
			candidate = candidate.substring(0, detail).trim();
		}
		int chapterTag = candidate.indexOf(" [");
		if (chapterTag >= 0)
		{
			candidate = candidate.substring(0, chapterTag).trim();
		}
		return FederationBanRegistry.validName(candidate);
	}

	static List<String> matchingChapters(FederationBanDirectoryClient.Snapshot snapshot,
										 String playerName)
	{
		if (snapshot == null || FederationBanRegistry.validName(playerName) == null)
		{
			return Collections.emptyList();
		}
		String normalized = WomDirectoryClient.normalize(playerName);
		Set<String> chapters = new LinkedHashSet<>();
		for (FederationBanDirectoryClient.Entry entry : snapshot.entries)
		{
			if (WomDirectoryClient.normalize(entry.name).equals(normalized))
			{
				chapters.add(entry.chapter);
			}
		}
		return Collections.unmodifiableList(new ArrayList<>(chapters));
	}

	/** Private, key-member-only text for the native Recruit pop-up. */
	static String recruitPopupWarningText(String name, List<String> chapters)
	{
		String player = FederationBanRegistry.validName(name);
		if (player == null || chapters == null || chapters.isEmpty())
		{
			return null;
		}
		return "HOLY EMPIRE BAN-LIST WARNING\n\n" + player + " is banned in:\n" +
			String.join("\n", chapters) + "\n\nConfirm their ban status before recruiting.";
	}

	static String warningText(String name, List<String> chapters, boolean statusAvailable)
	{
		String player = FederationBanRegistry.validName(name);
		if (player == null)
		{
			return null;
		}
		if (!chapters.isEmpty())
		{
			return "HOLY EMPIRE BAN-LIST WARNING\n\n" + player + " is banned in:\n" +
				String.join("\n", chapters) +
				"\n\nConsult an authorized Owner or Deputy Owner before recruiting.";
		}
		if (!statusAvailable)
		{
			return "HOLY EMPIRE RECRUITMENT WARNING\n\n"
				+ "The ban-list status of " + player + " could not be verified. "
				+ "Ask an authorized Owner or Deputy Owner to check before recruiting.";
		}
		return null;
	}
}
