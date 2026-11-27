/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireHouseAdvertisementSorterTest
{
	private static final class FakeWidget implements InvocationHandler
	{
		final Widget proxy;
		final String text;
		final int height;
		final Object[] listener;
		Widget[] children;
		int y;
		int movements;

		FakeWidget(String text, int y, int height, Object[] listener)
		{
			this.text = text;
			this.y = y;
			this.height = height;
			this.listener = listener;
			proxy = (Widget)Proxy.newProxyInstance(Widget.class.getClassLoader(),
												   new Class[] {Widget.class}, this);
		}

		@Override public Object invoke(Object p, java.lang.reflect.Method m, Object[] args)
		{
			switch (m.getName())
			{
			case "getText":
				return text;
			case "getRelativeY":
			case "getOriginalY":
				return y;
			case "setOriginalY":
				y = (Integer)args[0];
				movements++;
				return p;
			case "getHeight":
				return height;
			case "getOpacity":
				return 0;
			case "isHidden":
				return false;
			case "getDynamicChildren":
				return children;
			case "getOnOpListener":
				return listener;
			case "revalidate":
				return null;
			case "hashCode":
				return System.identityHashCode(p);
			case "equals":
				return p == args[0];
			case "toString":
				return text;
			default:
				throw new AssertionError("Unexpected Widget method " + m.getName());
			}
		}
	}

	private static final class Board
	{
		final Map<Integer, FakeWidget> containers = new HashMap<>();
		final Map<String, FakeWidget> widgets = new HashMap<>();
		Client client;

		Board(boolean includeAllArrows)
		{
			for (int column : HolyEmpireHouseAdvertisementSorter.COLUMNS)
			{
				FakeWidget guest = new FakeWidget(
					column == InterfaceID.PohBoard.NAME ? "GuestHost" : "", 0, 20, null);
				FakeWidget empire = new FakeWidget(
					column == InterfaceID.PohBoard.NAME ? "Erhva" : "", 20, 20, null);
				FakeWidget parent = new FakeWidget("", 0, 10, null);
				parent.children = new Widget[] {guest.proxy, empire.proxy};
				containers.put(column, parent);
				widgets.put(column + ":guest", guest);
				widgets.put(column + ":empire", empire);
			}
			FakeWidget guestArrow = new FakeWidget("", 1, 18, new Object[] {3110, "GuestHost"});
			FakeWidget empireArrow = new FakeWidget("", 21, 18, new Object[] {3110, "Erhva"});
			FakeWidget arrowParent = new FakeWidget("", 0, 10, null);
			arrowParent.children = includeAllArrows
									   ? new Widget[] {guestArrow.proxy, empireArrow.proxy}
									   : new Widget[] {empireArrow.proxy};
			containers.put(InterfaceID.PohBoard.BUTTON, arrowParent);
			widgets.put("arrow:guest", guestArrow);
			widgets.put("arrow:empire", empireArrow);
			client = (Client)Proxy.newProxyInstance(
				Client.class.getClassLoader(), new Class[] {Client.class}, (p, m, args) -> {
					if ("getWidget".equals(m.getName()) && args.length == 1)
					{
						FakeWidget container = containers.get((Integer)args[0]);
						return container == null ? null : container.proxy;
					}
					if ("hashCode".equals(m.getName()))
						return System.identityHashCode(p);
					if ("equals".equals(m.getName()))
						return p == args[0];
					throw new AssertionError("Unexpected Client method " + m.getName());
				});
		}
	}

	private static Map<String, List<String>> roster()
	{
		Map<String, List<String>> roster = new HashMap<>();
		roster.put("erhva", Arrays.asList("Holy Empire"));
		return roster;
	}

	@Test public void empireFirstSortIsStable()
	{
		List<String> names = Arrays.asList("guest1", "empire1", "guest2", "empire2");
		List<String> sorted = HolyEmpireHouseAdvertisementSorter.empireFirst(
			names, name -> name.startsWith("empire"));
		assertEquals(Arrays.asList("empire1", "empire2", "guest1", "guest2"), sorted);
		assertEquals("Input order remains unchanged", "guest1", names.get(0));
	}

	@Test public void movesNamesStatsAndClickArrowsTogetherThenRestoresAll()
	{
		Board board = new Board(true);
		HolyEmpireHouseAdvertisementSorter sorter = new HolyEmpireHouseAdvertisementSorter();
		sorter.pinEmpireHosts(board.client, roster());
		for (int column : HolyEmpireHouseAdvertisementSorter.COLUMNS)
		{
			assertEquals("Empire row moved up in column " + column, 0,
						 board.widgets.get(column + ":empire").y);
			assertEquals("Guest moved below in column " + column, 20,
						 board.widgets.get(column + ":guest").y);
		}
		assertEquals("Enter House button follows Empire", 1, board.widgets.get("arrow:empire").y);
		assertEquals("Enter House button follows Guest", 21, board.widgets.get("arrow:guest").y);
		int oldMoves = board.widgets.get("arrow:empire").movements;
		sorter.pinEmpireHosts(board.client, roster());
		assertEquals("Already sorted rows must not move again", oldMoves,
					 board.widgets.get("arrow:empire").movements);
		sorter.restore();
		for (int column : HolyEmpireHouseAdvertisementSorter.COLUMNS)
		{
			assertEquals(20, board.widgets.get(column + ":empire").y);
			assertEquals(0, board.widgets.get(column + ":guest").y);
		}
		assertEquals(21, board.widgets.get("arrow:empire").y);
		assertEquals(1, board.widgets.get("arrow:guest").y);
	}

	@Test public void missingClickTargetPreventsAllRowMovement()
	{
		Board board = new Board(false);
		HolyEmpireHouseAdvertisementSorter sorter = new HolyEmpireHouseAdvertisementSorter();
		sorter.pinEmpireHosts(board.client, roster());
		for (int column : HolyEmpireHouseAdvertisementSorter.COLUMNS)
		{
			assertEquals(20, board.widgets.get(column + ":empire").y);
			assertEquals(0, board.widgets.get(column + ":guest").y);
			assertEquals(0, board.widgets.get(column + ":empire").movements);
		}
	}
}
