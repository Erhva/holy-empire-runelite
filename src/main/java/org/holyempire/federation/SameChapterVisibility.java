/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.List;

/** Shared membership of any verified Holy Empire chapter, not surname/tag matching. */
final class SameChapterVisibility
{
	private SameChapterVisibility()
	{
	}

	static boolean isSameChapter(List<String> localChapters, List<String> targetChapters)
	{
		if (localChapters == null || localChapters.isEmpty() || targetChapters == null ||
			targetChapters.isEmpty())
		{
			return false;
		}
		for (String chapter : localChapters)
		{
			if (chapter == null)
			{
				continue;
			}
			for (String target : targetChapters)
			{
				if (WomDirectoryClient.normalize(chapter).equals(
						WomDirectoryClient.normalize(target)))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Other chapters follow the global rank toggle. Our chapter gets its own,
	 * but must not supersede an existing Player Indicators icon.
	 */
	static boolean showRankIcon(boolean globalEnabled, boolean sameChapter,
								boolean sameChapterEnabled,
								boolean playerIndicatorsClanRanksEnabled,
								boolean nativeRankIconPresent)
	{
		// Player Indicators' global clan rank setting takes priority for
		// same-chapter players, as does any native rank icon it rendered.
		return sameChapter
			? sameChapterEnabled && playerIndicatorsClanRanksEnabled && !nativeRankIconPresent
			: globalEnabled;
	}

	/** Same-chapter CC visibility is independent of the Empire-wide CC setting. */
	static boolean showMenuTag(boolean globalEnabled, boolean sameChapter, boolean showSame)
	{
		return sameChapter ? showSame : globalEnabled;
	}

	static boolean showOverhead(boolean enabled, boolean sameChapter, boolean allowSame)
	{
		return enabled && (!sameChapter || allowSame);
	}

	static boolean showDot(boolean enabled, boolean sameChapter, boolean allowSame)
	{
		return enabled && (!sameChapter || allowSame);
	}
}
