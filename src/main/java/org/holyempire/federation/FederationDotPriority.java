/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

/** Native minimap markers win unless the user expressly elevates Empire dots. */
final class FederationDotPriority
{
	private FederationDotPriority()
	{
	}

	static boolean allowFederationDot(boolean isFriend, boolean friendsChat, boolean clanMember,
									  int localTeam, int playerTeam)
	{
		return allowFederationDot(isFriend, friendsChat, clanMember, localTeam, playerTeam,
								  EmpireHighlightPriority.BELOW_CLAN);
	}

	static boolean allowFederationDot(boolean isFriend, boolean friendsChat, boolean clanMember,
									  int localTeam, int playerTeam,
									  EmpireHighlightPriority selected)
	{
		EmpireHighlightPriority priority = EmpireHighlightPriority.selected(selected);
		return (!isFriend || priority.outranksNativeFriendDot()) &&
			(!friendsChat || priority.outranksNativeFriendsChatDot()) &&
			(!clanMember || priority.outranksNativeClanDot()) &&
			(localTeam == 0 || localTeam != playerTeam || priority.outranksNativeTeamDot());
	}
}
