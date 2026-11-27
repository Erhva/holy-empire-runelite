/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * Optional POH board presentation sorting. Moves ALL cells in each row as a
 * unit, including the native Enter House button. Click handlers are never
 * altered. Leaves the game ordering untouched unless explicitly enabled.
 */
final class HolyEmpireHouseAdvertisementSorter
{
	static final int[] COLUMNS = {InterfaceID.PohBoard.NAME,	  InterfaceID.PohBoard.LOCATION,
								  InterfaceID.PohBoard.TIME,	  InterfaceID.PohBoard.LEVEL,
								  InterfaceID.PohBoard.ALTAR,	  InterfaceID.PohBoard.NEXUS,
								  InterfaceID.PohBoard.GLORY,	  InterfaceID.PohBoard.POOL,
								  InterfaceID.PohBoard.SPELLBOOK, InterfaceID.PohBoard.ARMOUR};
	private final Map<Widget, Integer> savedOriginalY = new WeakHashMap<>();

	static final class Row
	{
		final String name;
		final int y;
		final boolean member;
		final Widget nameWidget;

		Row(String name, int y, boolean member, Widget nameWidget)
		{
			this.name = name;
			this.y = y;
			this.member = member;
			this.nameWidget = nameWidget;
		}
	}

	/** Stable partition: preserve the original ordering within each group. */
	static <T> List<T> empireFirst(List<T> rows, java.util.function.Predicate<T> isMember)
	{
		List<T> result = new ArrayList<>(rows);
		result.sort(Comparator.comparing(value -> !isMember.test(value)));
		return result;
	}

	void clear()
	{
		savedOriginalY.clear();
	}

	void restore()
	{
		for (Map.Entry<Widget, Integer> entry : savedOriginalY.entrySet())
		{
			Widget widget = entry.getKey();
			if (widget != null)
			{
				widget.setOriginalY(entry.getValue());
				widget.revalidate();
			}
		}
		savedOriginalY.clear();
	}

	/**
	 * Invoke only on the client render thread. Skip inconsistent boards
	 * rather than risk separating a name from its clickable house button.
	 */
	void pinEmpireHosts(Client client, Map<String, List<String>> roster)
	{
		if (roster == null || roster.isEmpty())
		{
			return;
		}
		Widget names = client.getWidget(InterfaceID.PohBoard.NAME);
		Widget arrows = client.getWidget(InterfaceID.PohBoard.BUTTON);
		if (names == null || arrows == null)
		{
			return;
		}
		Widget[] nameWidgets = names.getDynamicChildren();
		Widget[] arrowWidgets = arrows.getDynamicChildren();
		if (nameWidgets == null || arrowWidgets == null)
		{
			return;
		}
		List<Row> rows = new ArrayList<>();
		Set<String> namesSeen = new HashSet<>();
		Set<Integer> ysSeen = new HashSet<>();
		for (Widget widget : nameWidgets)
		{
			if (widget == null || widget.isHidden() || widget.getOpacity() == 255)
			{
				continue;
			}
			String name = WomDirectoryClient.normalize(
				HolyEmpireHouseAdvertisementOverlay.plainHostName(widget.getText()));
			if (name.isEmpty())
			{
				continue;
			}
			int y = widget.getRelativeY();
			if (!namesSeen.add(name) || !ysSeen.add(y))
			{
				return; // Ambiguous list; do not modify native click targets.
			}
			rows.add(new Row(
				name, y, HolyEmpireHouseAdvertisementOverlay.isRosterHost(widget.getText(), roster),
				widget));
		}
		if (rows.size() < 2 || rows.size() > 75)
		{
			return;
		}
		rows.sort(Comparator.comparingInt(row -> row.y));
		List<Row> order = empireFirst(rows, row -> row.member);
		if (order.equals(rows))
		{
			return;
		}

		Map<Integer, Row> byY = new HashMap<>();
		Map<String, Row> byName = new HashMap<>();
		Map<Row, Integer> originalIndex = new HashMap<>();
		Map<Row, Integer> targetIndex = new HashMap<>();
		for (int i = 0; i < rows.size(); i++)
		{
			Row row = rows.get(i);
			byY.put(row.y, row);
			byName.put(row.name, row);
			originalIndex.put(row, i);
			targetIndex.put(order.get(i), i);
		}

		// Validate native Enter House click targets before touching ANY
		// coordinates. Reordering only the names would mislead the player.
		Map<Row, Widget> buttonsByRow = new HashMap<>();
		for (Widget arrow : arrowWidgets)
		{
			if (arrow == null || arrow.isHidden() || arrow.getOpacity() == 255)
			{
				continue;
			}
			String host =
				HolyEmpireHouseAdvertisementOverlay.enterButtonHost(arrow.getOnOpListener());
			Row row = byName.get(host);
			if (row != null && buttonsByRow.putIfAbsent(row, arrow) != null)
			{
				return;
			}
		}
		if (buttonsByRow.size() != rows.size())
		{
			return;
		}

		// Collect all movements before changing widget positions.
		Map<Widget, Integer> newOriginalYs = new HashMap<>();
		for (int column : COLUMNS)
		{
			Widget container = client.getWidget(column);
			if (container == null)
			{
				return;
			}
			Widget[] cells = container.getDynamicChildren();
			if (cells == null)
			{
				return;
			}
			for (Widget cell : cells)
			{
				if (cell == null)
				{
					continue;
				}
				Row source = byY.get(cell.getRelativeY());
				if (source != null && cell.getHeight() > 0)
				{
					newOriginalYs.put(cell, targetIndex.get(source) * cell.getHeight());
				}
			}
		}
		for (Row row : rows)
		{
			Widget arrow = buttonsByRow.get(row);
			int oldIndex = originalIndex.get(row);
			int newIndex = targetIndex.get(row);
			int height = arrow.getHeight();
			if (height < 1)
			{
				return;
			}
			int oldY = arrow.getRelativeY();
			int padding = (oldY - height * oldIndex) / (2 * oldIndex + 1);
			int targetY = newIndex * height + (2 * newIndex + 1) * padding;
			newOriginalYs.put(arrow, targetY);
		}
		if (!newOriginalYs.keySet().containsAll(
				new java.util.HashSet<>(Arrays.asList(nameWidgets))))
		{
			// Hidden/padded native widgets can be unrelated to rows. Require
			// every actual visible host row to have a planned position.
			for (Row row : rows)
			{
				if (!newOriginalYs.containsKey(row.nameWidget))
				{
					return;
				}
			}
		}
		for (Map.Entry<Widget, Integer> move : newOriginalYs.entrySet())
		{
			Widget widget = move.getKey();
			if (widget.getOriginalY() != move.getValue())
			{
				savedOriginalY.putIfAbsent(widget, widget.getOriginalY());
				widget.setOriginalY(move.getValue());
				widget.revalidate();
			}
		}
	}
}
