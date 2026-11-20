/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Collection;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;

/**
 * Holy Empire-specific player identification is never drawn in PvP.
 * Unlike ordinary friends/clan indicators, cross-chapter roster data could
 * identify opponents, including members of other Holy Empire clans.
 */
final class HolyEmpirePvpPolicy
{
	private HolyEmpirePvpPolicy()
	{
	}

	static boolean suppressPlayerIndicators(Client client)
	{
		if (client == null)
		{
			return true;
		}
		return suppressPlayerIndicators(client.getVarbitValue(VarbitID.INSIDE_WILDERNESS),
										client.getVarbitValue(VarbitID.PVP_AREA_CLIENT),
										client.getWorldType());
	}

	static boolean suppressPlayerIndicators(int wilderness, int pvpArea,
											Collection<WorldType> worldTypes)
	{
		// When the current world isn't known yet, hide the indicators until it is.
		if (worldTypes == null || wilderness != 0 || pvpArea != 0)
		{
			return true;
		}
		return WorldType.isPvpWorld(worldTypes) || worldTypes.contains(WorldType.PVP) ||
			worldTypes.contains(WorldType.HIGH_RISK) || worldTypes.contains(WorldType.DEADMAN) ||
			worldTypes.contains(WorldType.BOUNTY) || worldTypes.contains(WorldType.PVP_ARENA) ||
			worldTypes.contains(WorldType.LAST_MAN_STANDING) ||
			worldTypes.contains(WorldType.TOURNAMENT_WORLD);
	}
}
