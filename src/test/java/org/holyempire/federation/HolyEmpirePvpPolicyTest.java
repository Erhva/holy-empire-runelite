/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpirePvpPolicyTest
{
	@Test public void nonPvpWorldDoesNotSuppressEmpirePlayerIdentifiers()
	{
		assertFalse(
			HolyEmpirePvpPolicy.suppressPlayerIndicators(0, 0, EnumSet.of(WorldType.MEMBERS)));
		assertFalse(
			HolyEmpirePvpPolicy.suppressPlayerIndicators(0, 0, EnumSet.noneOf(WorldType.class)));
	}

	@Test public void wildernessAndPvpAreaVarbitsSuppressAllEmpireIdentifiers()
	{
		assertTrue(
			HolyEmpirePvpPolicy.suppressPlayerIndicators(1, 0, EnumSet.of(WorldType.MEMBERS)));
		assertTrue(
			HolyEmpirePvpPolicy.suppressPlayerIndicators(0, 1, EnumSet.of(WorldType.MEMBERS)));
		assertTrue(
			HolyEmpirePvpPolicy.suppressPlayerIndicators(2, 0, EnumSet.of(WorldType.MEMBERS)));
		assertTrue(
			HolyEmpirePvpPolicy.suppressPlayerIndicators(0, 3, EnumSet.of(WorldType.MEMBERS)));
	}

	@Test public void pvpWorldTypesSuppressIdentifiersEvenOutsideWilderness()
	{
		for (WorldType world : new WorldType[] {
				 WorldType.PVP, WorldType.HIGH_RISK, WorldType.DEADMAN, WorldType.BOUNTY,
				 WorldType.PVP_ARENA, WorldType.LAST_MAN_STANDING, WorldType.TOURNAMENT_WORLD})
		{
			assertTrue("PvP indicator exposure on " + world,
					   HolyEmpirePvpPolicy.suppressPlayerIndicators(
						   0, 0, EnumSet.of(WorldType.MEMBERS, world)));
		}
	}

	@Test public void missingWorldContextDefaultsToNoEmpirePlayerIdentifiers()
	{
		assertTrue(HolyEmpirePvpPolicy.suppressPlayerIndicators(0, 0, null));
		assertTrue(HolyEmpirePvpPolicy.suppressPlayerIndicators((Client)null));
	}

	@Test public void realClientVarbitsAndWorldTypesFeedSameGate()
	{
		Client nonPvp = client(0, 0, EnumSet.of(WorldType.MEMBERS));
		Client wilderness = client(1, 0, EnumSet.of(WorldType.MEMBERS));
		Client pvpArea = client(0, 1, EnumSet.of(WorldType.MEMBERS));
		Client pvpWorld = client(0, 0, EnumSet.of(WorldType.MEMBERS, WorldType.PVP));
		assertFalse(HolyEmpirePvpPolicy.suppressPlayerIndicators(nonPvp));
		assertTrue(HolyEmpirePvpPolicy.suppressPlayerIndicators(wilderness));
		assertTrue(HolyEmpirePvpPolicy.suppressPlayerIndicators(pvpArea));
		assertTrue(HolyEmpirePvpPolicy.suppressPlayerIndicators(pvpWorld));
	}

	@Test public void minimapOverlayIsSuppressedBeforeLookingUpPlayersInPvp()
	{
		Client wilderness = client(1, 0, EnumSet.of(WorldType.MEMBERS));
		FederationMinimapOverlay overlay =
			new FederationMinimapOverlay(wilderness, null, new FederationConfig() {}, null, null);
		assertNull(overlay.render(null));
	}

	private static Client client(int wilderness, int pvpArea, Set<WorldType> worldTypes)
	{
		return (Client)Proxy.newProxyInstance(
			Client.class.getClassLoader(), new Class<?>[] {Client.class},
			(proxy, method, arguments) -> {
				if ("getVarbitValue".equals(method.getName()))
				{
					int id = ((Number)arguments[0]).intValue();
					if (id == VarbitID.INSIDE_WILDERNESS)
					{
						return wilderness;
					}
					if (id == VarbitID.PVP_AREA_CLIENT)
					{
						return pvpArea;
					}
					return 0;
				}
				if ("getWorldType".equals(method.getName()))
				{
					return worldTypes == null ? null : EnumSet.copyOf(worldTypes);
				}
				throw new AssertionError("Unexpected Client API read: " + method.getName());
			});
	}
}
