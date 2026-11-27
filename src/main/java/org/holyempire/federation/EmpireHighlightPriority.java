/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import net.runelite.api.Player;
import net.runelite.client.plugins.playerindicators.PlayerIndicatorsConfig;
import net.runelite.client.plugins.playerindicators.PlayerIndicatorsConfig.HighlightSetting;

/**
 * A configurable slot in RuneLite Player Indicators' ordering:
 * Party > Friends > Friends Chat > Team > Clan > Others.
 * The slot affects visual highlighting, not the independently toggled
 * [Chapter] menu label or native Player Indicators rank icons.
 */
public enum EmpireHighlightPriority
{
	ABOVE_ALL("Above party / all highlights", 7),
	BELOW_PARTY("Below party", 6),
	BELOW_FRIENDS("Below friends", 5),
	BELOW_FRIENDS_CHAT("Below friends chat", 4),
	BELOW_TEAM("Below team", 3),
	BELOW_CLAN("Below clan (default)", 2),
	BELOW_OTHERS("Below others / lowest", 1);

	private final String label;
	private final int level;

	EmpireHighlightPriority(String label, int level)
	{
		this.label = label;
		this.level = level;
	}

	@Override public String toString()
	{
		return label;
	}

	/** Ordered highest to lowest, including the Empire row at its saved slot. */
	String[] order()
	{
		String[] names = {"Party", "Friends", "Friends Chat", "Team", "Clan", "Others"};
		String[] result = new String[7];
		int empirePosition = ordinal();
		for (int pos = 0; pos < result.length; pos++)
		{
			result[pos] =
				pos == empirePosition ? "Holy Empire" : names[pos < empirePosition ? pos : pos - 1];
		}
		return result;
	}

	int position()
	{
		return ordinal() + 1;
	}

	EmpireHighlightPriority moveUp()
	{
		return values()[Math.max(0, ordinal() - 1)];
	}

	EmpireHighlightPriority moveDown()
	{
		return values()[Math.min(values().length - 1, ordinal() + 1)];
	}

	String positionLabel()
	{
		return position() + " of 7";
	}

	/**
	 * A higher-priority enabled category wins; disabled and PvP-only
	 * categories don't suppress Empire highlights outside their scope.
	 */
	boolean mayHighlight(boolean party, boolean friend, boolean friendsChat, boolean team,
						 boolean clan, boolean others)
	{
		int selected = party		 ? 6
					   : friend		 ? 5
					   : friendsChat ? 4
					   : team		 ? 3
					   : clan		 ? 2
					   : others		 ? 1
									 : 0;
		return level > selected;
	}

	boolean outranksNativeFriendDot()
	{
		return level > 5;
	}

	boolean outranksNativeFriendsChatDot()
	{
		return level > 4;
	}

	boolean outranksNativeTeamDot()
	{
		return level > 3;
	}

	boolean outranksNativeClanDot()
	{
		return level > 2;
	}

	static boolean enabled(HighlightSetting setting, boolean inPvp)
	{
		return setting == HighlightSetting.ENABLED || (setting == HighlightSetting.PVP && inPvp);
	}

	static boolean canHighlight(EmpireHighlightPriority priority, Player target, int localTeam,
								boolean inParty, PlayerIndicatorsConfig config, boolean inPvp)
	{
		if (target == null || config == null)
		{
			return false;
		}
		if (priority == null)
		{
			priority = BELOW_CLAN;
		}
		boolean friend = target.isFriend();
		boolean friendsChat = target.isFriendsChatMember();
		boolean clan = target.isClanMember();
		boolean team = localTeam != 0 && target.getTeam() == localTeam;
		return priority.mayHighlight(inParty && enabled(config.highlightPartyMembers(), inPvp),
									 friend && enabled(config.highlightFriends(), inPvp),
									 friendsChat && enabled(config.highlightFriendsChat(), inPvp),
									 team && enabled(config.highlightTeamMembers(), inPvp),
									 clan && enabled(config.highlightClanMembers(), inPvp),
									 !friendsChat && !clan &&
										 enabled(config.highlightOthers(), inPvp));
	}

	static EmpireHighlightPriority selected(EmpireHighlightPriority value)
	{
		return value == null ? BELOW_CLAN : value;
	}
}
