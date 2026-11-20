/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationApplicantOverlayTest
{
	private static Widget widget(String text, Rectangle bounds, boolean hidden, Widget... children)
	{
		return (Widget)Proxy.newProxyInstance(Widget.class.getClassLoader(),
											  new Class<?>[] {Widget.class},
											  (proxy, method, arguments) -> {
												  switch (method.getName())
												  {
												  case "isHidden":
													  return hidden;
												  case "getText":
													  return text;
												  case "getName":
													  return "";
												  case "getBounds":
													  return bounds;
												  case "getChildren":
													  return children;
												  case "toString":
													  return "applicants-test-widget";
												  default:
													  return null;
												  }
											  });
	}

	private static FederationBanDirectoryClient.Snapshot directory()
	{
		Map<String, FederationBanDirectoryClient.Chapter> chapters = new LinkedHashMap<>();
		chapters.put("Holy Chaos",
					 new FederationBanDirectoryClient.Chapter("Holy Chaos", 1795000000L, 8));
		return new FederationBanDirectoryClient.Snapshot(
			Arrays.asList(new FederationBanDirectoryClient.Entry("Holy Chaos", "Harohad", "tracked",
																 1795000000L)),
			chapters);
	}

	@Test
	public void drawsBannedApplicantMarkerBeforeAnyAcceptClick()
	{
		BufferedImage canvas = new BufferedImage(600, 280, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		graphics.setColor(Color.WHITE);
		graphics.fillRect(0, 0, 600, 280);
		Widget list =
			widget("", null, false, widget("Other Safe", new Rectangle(40, 50, 160, 20), false),
				   widget("<col=ffffff>Harohad</col>", new Rectangle(40, 80, 160, 20), false),
				   widget("Harohad", new Rectangle(40, 80, 160, 20), false));
		int marked = FederationApplicantOverlay.markApplicantRows(graphics, list, directory(),
																  new HashSet<>(), 0);
		graphics.dispose();
		assertEquals("Duplicate widgets for same banned player need only one badge", 1, marked);
		assertNotEquals("The banned row must be visibly marked", Color.WHITE.getRGB(),
						canvas.getRGB(45, 85));
		assertEquals("The clean row must remain unchanged", Color.WHITE.getRGB(),
					 canvas.getRGB(45, 55));
	}

	@Test
	public void visibleBanWithoutRowCoordinatesStillRaisesWarningCount()
	{
		BufferedImage canvas = new BufferedImage(250, 130, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		Widget container = widget("", null, false, widget("Harohad", null, false));
		assertEquals(1, FederationApplicantOverlay.markApplicantRows(
							graphics, container, directory(), new HashSet<>(), 0));
		graphics.dispose();
	}

	@Test
	public void selectedWarningSitsRightOfApplicantNameNotOnSkills()
	{
		BufferedImage canvas = new BufferedImage(1200, 850, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD, 11f));
		// Approximate the reported live RuneLite Applicants right-side detail pane.
		Rectangle info = new Rectangle(475, 185, 595, 495);
		Widget name = widget("<col=ffdf6e>Harohad</col>", new Rectangle(692, 193, 150, 56), false);
		Widget details =
			widget("", info, false, widget("", new Rectangle(500, 275, 500, 360), false), name);
		Rectangle found =
			FederationApplicantOverlay.findSelectedNameBounds(details, info, "Harohad", 0);
		assertEquals(name.getBounds(), found);
		Rectangle badge =
			FederationApplicantOverlay.selectedBadgeBounds(graphics, info, found, "Harohad");
		assertTrue("Warning must be to the right of the centered player name", badge.x >= 840);
		assertTrue("Warning must remain within the right-side details pane",
				   badge.x + badge.width <= info.x + info.width);
		assertEquals("Badge moves 6 px down to align with the selected name", info.y + 6, badge.y);
		assertTrue("Badge must end above the underlying game detail summary",
				   badge.y + badge.height <= info.y + 27);
		assertEquals("Badge height and x alignment are unchanged", 21, badge.height);
		assertTrue("Badge must be small enough not to cover the game controls", badge.height <= 23);
		graphics.dispose();
	}

	@Test
	public void selectedBanUsesRuneScapeBoldRedTextWithoutBackground()
	{
		BufferedImage image = new BufferedImage(1200, 760, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		java.awt.Font originalFont = g.getFont();
		Color originalColor = g.getColor();
		Rectangle pane = new Rectangle(475, 185, 595, 495);
		Rectangle name = new Rectangle(692, 193, 150, 56);
		FederationApplicantOverlay.drawSelectedBanText(g, pane, name, "Harohad");
		assertEquals("Font state must be restored for subsequent overlays", originalFont,
					 g.getFont());
		assertEquals("Color state must be restored for subsequent overlays", originalColor,
					 g.getColor());
		int visible = 0;
		int red = 0;
		for (int y = pane.y; y < pane.y + 45; y++)
		{
			for (int x = pane.x + pane.width / 2; x < pane.x + pane.width; x++)
			{
				int pixel = image.getRGB(x, y);
				if (((pixel >>> 24) & 0xff) == 0)
				{
					continue;
				}
				visible++;
				Color color = new Color(pixel, true);
				if (color.getRed() > color.getGreen() + 75 && color.getRed() > color.getBlue() + 75)
				{
					red++;
				}
			}
		}
		assertTrue("Bold red lettering must actually render", red > 30);
		assertTrue("Background must stay transparent, no filled red rectangle", visible < 2000);
		assertEquals("No overlay background may paint under the details on left", 0,
					 image.getRGB(pane.x + 5, pane.y + 5));
		g.dispose();
	}

	@Test
	public void missingNameWidgetUsesTopRowFallback()
	{
		BufferedImage canvas = new BufferedImage(1100, 750, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		Rectangle info = new Rectangle(475, 185, 595, 495);
		assertNull(FederationApplicantOverlay.findSelectedNameBounds(
			widget("", info, false, widget("Combat: 36", new Rectangle(610, 260, 190, 28), false)),
			info, "Harohad", 0));
		Rectangle badge =
			FederationApplicantOverlay.selectedBadgeBounds(graphics, info, null, "Harohad");
		assertTrue(badge.x > info.x + info.width / 2);
		assertEquals("Fallback shifts down the same 6 px", info.y + 6, badge.y);
		assertTrue(badge.x + badge.width < info.x + info.width);
		graphics.dispose();
	}

	@Test
	public void unrelatedOrHiddenRowsCannotProduceFalseBannedMarkers()
	{
		BufferedImage canvas = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = canvas.createGraphics();
		Widget list =
			widget("", null, false, widget("Harohad 2", new Rectangle(20, 20, 130, 20), false),
				   widget("Harohad", new Rectangle(20, 50, 130, 20), true),
				   widget("Not Harohad", new Rectangle(20, 80, 130, 20), false));
		assertEquals(0, FederationApplicantOverlay.markApplicantRows(graphics, list, directory(),
																	 new HashSet<>(), 0));
		graphics.dispose();
	}
}
