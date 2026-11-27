/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmpireHighlightPriorityTest
{
	private boolean wins(EmpireHighlightPriority at, int category)
	{
		return at.mayHighlight(category == 6, category == 5, category == 4, category == 3,
							   category == 2, category == 1);
	}

	@Test public void eachSlotChangesPriorityForAllPlayerIndicatorsCategories()
	{
		EmpireHighlightPriority[] slots = EmpireHighlightPriority.values();
		assertEquals(7, slots.length);
		for (int i = 0; i < slots.length; ++i)
		{
			int cutoff = 6 - i;
			for (int piCategory = 1; piCategory <= 6; ++piCategory)
			{
				assertEquals(slots[i] + " vs Player Indicators category " + piCategory,
							 piCategory <= cutoff, wins(slots[i], piCategory));
			}
			assertTrue("No competing category must allow Empire", wins(slots[i], 0));
		}
	}

	@Test public void allSevenSlotsRenderOneAndOnlyOneEmpireRow()
	{
		for (EmpireHighlightPriority priority : EmpireHighlightPriority.values())
		{
			String[] order = priority.order();
			assertEquals(7, order.length);
			assertEquals("Holy Empire", order[priority.ordinal()]);
			assertEquals(priority.ordinal() + 1, priority.position());
			assertEquals(priority.position() + " of 7", priority.positionLabel());
			if (priority != EmpireHighlightPriority.ABOVE_ALL)
			{
				assertEquals(priority, priority.moveUp().moveDown());
			}
			if (priority != EmpireHighlightPriority.BELOW_OTHERS)
			{
				assertEquals(priority, priority.moveDown().moveUp());
			}
			int empireCount = 0;
			String[] nativeOrder = {"Party", "Friends", "Friends Chat", "Team", "Clan", "Others"};
			int nativeIndex = 0;
			for (String row : order)
			{
				if ("Holy Empire".equals(row))
				{
					empireCount++;
				}
				else
				{
					assertEquals(nativeOrder[nativeIndex++], row);
				}
			}
			assertEquals(1, empireCount);
			assertEquals(6, nativeIndex);
		}
	}

	@Test public void actualPlayerIndicatorsOrderWinsWhenMembershipsOverlap()
	{
		EmpireHighlightPriority high = EmpireHighlightPriority.BELOW_FRIENDS;
		assertFalse(high.mayHighlight(false, true, false, false, true, false));
		assertTrue(high.mayHighlight(false, false, false, false, true, false));
		assertFalse(EmpireHighlightPriority.BELOW_PARTY.mayHighlight(true, false, false, false,
																	 false, false));
		assertTrue(
			EmpireHighlightPriority.ABOVE_ALL.mayHighlight(true, true, true, true, true, true));
		assertFalse(EmpireHighlightPriority.BELOW_OTHERS.mayHighlight(false, false, false, false,
																	  false, true));
	}

	@Test public void defaultMaintainsExistingOverheadAndDotPrecedence() throws Exception
	{
		FederationConfig defaults = new FederationConfig() {};
		assertEquals(EmpireHighlightPriority.BELOW_CLAN, defaults.empireHighlightPriority());
		EmpireHighlightPriority priority = defaults.empireHighlightPriority();
		for (int category = 2; category <= 6; category++)
		{
			assertFalse(wins(priority, category));
		}
		assertTrue(wins(priority, 1));
		assertTrue(wins(priority, 0));

		assertFalse(FederationDotPriority.allowFederationDot(true, false, false, 0, 0, priority));
		assertFalse(FederationDotPriority.allowFederationDot(false, true, false, 0, 0, priority));
		assertFalse(FederationDotPriority.allowFederationDot(false, false, true, 0, 0, priority));
		assertFalse(
			FederationDotPriority.allowFederationDot(false, false, false, 10, 10, priority));
		assertTrue(FederationDotPriority.allowFederationDot(false, false, false, 10, 11, priority));

		ConfigItem item = FederationConfig.class.getMethod("empireHighlightPriority")
							  .getAnnotation(ConfigItem.class);
		assertNotNull(item);
		assertEquals("minimapDotSection", item.section());
		assertEquals("Empire highlight priority", item.name());
		assertEquals("empireHighlightPriority", item.keyName());
	}

	@Test public void selectedPriorityChangesNativeDotOverrideExplicitly()
	{
		assertTrue(FederationDotPriority.allowFederationDot(true, false, false, 0, 0,
															EmpireHighlightPriority.ABOVE_ALL));
		assertTrue(FederationDotPriority.allowFederationDot(true, false, false, 0, 0,
															EmpireHighlightPriority.BELOW_PARTY));
		assertFalse(FederationDotPriority.allowFederationDot(
			true, false, false, 0, 0, EmpireHighlightPriority.BELOW_FRIENDS));
		assertTrue(FederationDotPriority.allowFederationDot(false, true, false, 0, 0,
															EmpireHighlightPriority.BELOW_FRIENDS));
		assertFalse(FederationDotPriority.allowFederationDot(
			false, true, false, 0, 0, EmpireHighlightPriority.BELOW_FRIENDS_CHAT));
		assertTrue(FederationDotPriority.allowFederationDot(
			false, false, false, 11, 11, EmpireHighlightPriority.BELOW_FRIENDS_CHAT));
		assertFalse(FederationDotPriority.allowFederationDot(false, false, false, 11, 11,
															 EmpireHighlightPriority.BELOW_TEAM));
		assertTrue(FederationDotPriority.allowFederationDot(false, false, true, 0, 0,
															EmpireHighlightPriority.BELOW_TEAM));
		assertFalse(FederationDotPriority.allowFederationDot(false, false, true, 0, 0,
															 EmpireHighlightPriority.BELOW_CLAN));
	}

	@Test public void pvpOnlyCategoriesCannotBlockInNonPvpWorld()
	{
		assertFalse(
			EmpireHighlightPriority.enabled(net.runelite.client.plugins.playerindicators
												.PlayerIndicatorsConfig.HighlightSetting.PVP,
											false));
		assertTrue(EmpireHighlightPriority.enabled(net.runelite.client.plugins.playerindicators
													   .PlayerIndicatorsConfig.HighlightSetting.PVP,
												   true));
		assertFalse(
			EmpireHighlightPriority.enabled(net.runelite.client.plugins.playerindicators
												.PlayerIndicatorsConfig.HighlightSetting.DISABLED,
											true));
	}
}
