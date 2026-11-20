/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.ItemID;

/**
 * Observes equipment changes only. Never clicks, equips, or changes game items.
 * Every genuine unequipped -> equipped transition triggers at most one refresh.
 */
final class ClanVexillumEquipTrigger
{
	private static final long MIN_INTERVAL_MS = 15_000L;
	private boolean equipped;
	private long lastTriggeredAt = -1L;

	static boolean isClanVexillum(int itemId)
	{
		switch (itemId)
		{
		case ItemID.CLAN_VEXILLUM_BLACK:
		case ItemID.CLAN_VEXILLUM_ORANGE:
		case ItemID.CLAN_VEXILLUM_BLUE:
		case ItemID.CLAN_VEXILLUM_RED:
		case ItemID.CLAN_VEXILLUM_GREEN:
		case ItemID.CLAN_VEXILLUM_YELLOW:
		case ItemID.CLAN_VEXILLUM_LIGHTBLUE:
		case ItemID.CLAN_VEXILLUM_PINK:
			return true;
		default:
			return false;
		}
	}

	boolean onContainerChanged(int containerId, ItemContainer equipment, long now)
	{
		if (containerId != InventoryID.EQUIPMENT.getId() || equipment == null)
		{
			return false;
		}
		Item[] items = equipment.getItems();
		int weaponIndex = EquipmentInventorySlot.WEAPON.getSlotIdx();
		boolean nowEquipped = items != null && items.length > weaponIndex &&
							  items[weaponIndex] != null &&
							  isClanVexillum(items[weaponIndex].getId());
		boolean newlyEquipped = nowEquipped && !equipped;
		equipped = nowEquipped;
		if (!newlyEquipped)
		{
			return false;
		}
		if (lastTriggeredAt >= 0 && now >= lastTriggeredAt &&
			now - lastTriggeredAt < MIN_INTERVAL_MS)
		{
			return false;
		}
		lastTriggeredAt = now;
		return true;
	}

	void reset()
	{
		equipped = false;
		lastTriggeredAt = -1L;
	}
}
