/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.widgets.Widget;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.*;

public class HolyEmpireHouseAdvertisementOverlayTest
{
	private static Map<String, List<String>> roster()
	{
		Map<String, List<String>> result = new HashMap<>();
		result.put("erhva", Collections.singletonList("Holy Chaos"));
		result.put("holy host", Collections.singletonList("World 465"));
		return result;
	}

	@Test public void matchesExactRosteredHostsAcrossChapters()
	{
		Map<String, List<String>> members = roster();
		assertTrue(HolyEmpireHouseAdvertisementOverlay.isRosterHost("Erhva", members));
		assertTrue(HolyEmpireHouseAdvertisementOverlay.isRosterHost(
			"<col=ffffff>  HOLY\u00a0HOST  </col>", members));
		assertFalse(HolyEmpireHouseAdvertisementOverlay.isRosterHost("Erhva Alt", members));
		assertFalse(HolyEmpireHouseAdvertisementOverlay.isRosterHost("A stranger", members));
		assertFalse(HolyEmpireHouseAdvertisementOverlay.isRosterHost("Not Needed", members));
	}

	@Test public void neverLabelsMissingOrInvalidRosterData()
	{
		assertFalse(
			HolyEmpireHouseAdvertisementOverlay.isRosterHost("Erhva", Collections.emptyMap()));
		assertFalse(HolyEmpireHouseAdvertisementOverlay.isRosterHost(null, roster()));
		assertFalse(HolyEmpireHouseAdvertisementOverlay.isRosterHost("", roster()));
		assertFalse(HolyEmpireHouseAdvertisementOverlay.isRosterHost(
			"A very long impossible RuneScape username", roster()));
		Map<String, List<String>> emptyMembership = new HashMap<>();
		emptyMembership.put("erhva", Collections.emptyList());
		assertFalse(HolyEmpireHouseAdvertisementOverlay.isRosterHost("Erhva", emptyMembership));
	}

	@Test public void nameFrameExtendsToTheLeftEdgeOfBoard()
	{
		Rectangle row = new Rectangle(85, 60, 300, 54);
		Rectangle board = new Rectangle(65, 24, 1290, 360);
		Rectangle frame = HolyEmpireHouseAdvertisementOverlay.alignedNameBox(row, board);
		assertEquals(64, frame.x);
		assertEquals(row.x + row.width, frame.x + frame.width);
		assertEquals(row.y, frame.y);
		assertEquals(row.height, frame.height);
	}

	@Test public void enterButtonHostMustMatchRealNativeClickTarget()
	{
		assertEquals("erhva", HolyEmpireHouseAdvertisementOverlay.enterButtonHost(
								  new Object[] {3110, "ERHVA"}));
		assertEquals("holy host", HolyEmpireHouseAdvertisementOverlay.enterButtonHost(
									  new Object[] {3110, "Holy\u00a0Host"}));
		assertEquals("", HolyEmpireHouseAdvertisementOverlay.enterButtonHost(null));
		assertEquals("",
					 HolyEmpireHouseAdvertisementOverlay.enterButtonHost(new Object[] {"Enter"}));
		assertEquals("",
					 HolyEmpireHouseAdvertisementOverlay.enterButtonHost(new Object[] {3110, 7}));
	}

	@Test public void colorsOriginalWidgetWithoutDrawingDuplicateLetters()
	{
		AtomicInteger nativeColor = new AtomicInteger(0xffffff);
		AtomicInteger textWrites = new AtomicInteger(0);
		Widget host = (Widget)Proxy.newProxyInstance(
			Widget.class.getClassLoader(), new Class[] {Widget.class}, (proxy, method, args) -> {
				switch (method.getName())
				{
				case "getTextColor":
					return nativeColor.get();
				case "setTextColor":
					nativeColor.set((Integer)args[0]);
					return proxy;
				case "setText":
					textWrites.incrementAndGet();
					return proxy;
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "hostWidget";
				default:
					throw new AssertionError("Unexpected widget API: " + method.getName());
				}
			});
		Map<Widget, Integer> originals = new HashMap<>();
		HolyEmpireHouseAdvertisementOverlay.colorNativeHostWidget(host, true, originals);
		assertEquals(FederationPalette.DEFAULT_DOT_COLOR.getRGB(), nativeColor.get());
		assertEquals(Integer.valueOf(0xffffff), originals.get(host));
		HolyEmpireHouseAdvertisementOverlay.colorNativeHostWidget(host, true, originals);
		assertEquals("Preserve the original color after repeated refreshes",
					 Integer.valueOf(0xffffff), originals.get(host));
		assertEquals("Do not replace or redraw the original host string", 0, textWrites.get());
		HolyEmpireHouseAdvertisementOverlay.colorNativeHostWidget(host, false, originals);
		assertEquals("Restore the original host color when membership is lost", 0xffffff,
					 nativeColor.get());
		assertTrue(originals.isEmpty());
	}

	@Test public void roundedArrowTrimLeavesArrowInteriorClear()
	{
		BufferedImage name = new BufferedImage(110, 40, BufferedImage.TYPE_INT_ARGB);
		BufferedImage arrow = new BufferedImage(56, 40, BufferedImage.TYPE_INT_ARGB);
		Graphics2D ng = name.createGraphics();
		Graphics2D ag = arrow.createGraphics();
		try
		{
			HolyEmpireHouseAdvertisementOverlay.drawGoldNameBox(ng, new Rectangle(4, 4, 100, 30));
			HolyEmpireHouseAdvertisementOverlay.drawGoldArrowTrim(ag, new Rectangle(5, 5, 44, 28));
		}
		finally
		{
			ng.dispose();
			ag.dispose();
		}
		assertEquals("No name text painting inside border", 0, name.getRGB(52, 19));
		assertEquals("No blue tint or gold fill inside Enter House arrow", 0, arrow.getRGB(27, 19));
		assertEquals("Rounded arrow top-left corner remains clear", 0, arrow.getRGB(5, 5));
		assertTrue("Arrow top border should be painted", (arrow.getRGB(26, 6) >>> 24) > 10);
		int decorated = 0;
		for (int y = 0; y < arrow.getHeight(); y++)
		{
			for (int x = 0; x < arrow.getWidth(); x++)
			{
				if (((arrow.getRGB(x, y) >>> 24) & 0xff) > 30)
				{
					decorated++;
				}
			}
		}
		assertTrue("Rounded gold trim should be visible", decorated > 70);
	}

	@Test public void nameBorderIsVisiblePastNativeScrollingInset()
	{
		BufferedImage image = new BufferedImage(420, 120, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			Rectangle nativeName = new Rectangle(82, 22, 302, 61);
			Rectangle scrolling = new Rectangle(40, 0, 380, 118);
			Rectangle box =
				HolyEmpireHouseAdvertisementOverlay.alignedNameBox(nativeName, scrolling);
			graphics.clip(
				new Rectangle(scrolling.x - 3, scrolling.y, scrolling.width + 3, scrolling.height));
			HolyEmpireHouseAdvertisementOverlay.drawGoldNameBox(graphics, box);
		}
		finally
		{
			graphics.dispose();
		}
		assertTrue("The gold left frame reaches the inner edge of the name column x=40",
				   (image.getRGB(40, 52) >>> 24) > 20);
		assertEquals("Nothing is drawn inside the host box", 0, image.getRGB(110, 52));
	}

	@Test public void usesEmpireBlueAndIsEnabledByDefault()
	{
		assertEquals(FederationPalette.DEFAULT_DOT_COLOR,
					 HolyEmpireHouseAdvertisementOverlay.TAG_COLOR);
		assertTrue(new FederationConfig() {}.markEmpireHouseHosts());
		assertTrue("Empire-first ordering is enabled by default",
				   new FederationConfig() {}.pinEmpireHouseHostsFirst());
	}
}
