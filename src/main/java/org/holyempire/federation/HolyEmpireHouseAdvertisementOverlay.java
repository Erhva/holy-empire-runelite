/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/**
 * Visual-only blue-and-gold styling for POH hosts in the local WOM roster.
 * Do not change Widget.text, row ordering, menus, or house entry actions.
 * The tag uses no server calls, and honors PvP player-indicator suppression.
 */
public final class HolyEmpireHouseAdvertisementOverlay extends Overlay
{
	static final Color TAG_COLOR = FederationPalette.DEFAULT_DOT_COLOR;
	static final Color GOLD_GLOW = new Color(244, 197, 91);

	private final Client client;
	private final FederationPlugin plugin;
	private final FederationConfig config;
	// Only the original RuneLite text widget is colored: never draw a duplicate
	// host name. Weak keys avoid retaining a house-board widget after it closes.
	private final Map<Widget, Integer> originalNameColors = new WeakHashMap<>();
	private final HolyEmpireHouseAdvertisementSorter sorter =
		new HolyEmpireHouseAdvertisementSorter();

	@Inject
	public HolyEmpireHouseAdvertisementOverlay(Client client, FederationPlugin plugin,
											   FederationConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);
		drawAfterLayer(InterfaceID.PohBoard.SCROLLING);
	}

	static String plainHostName(String widgetText)
	{
		if (widgetText == null || widgetText.length() > 160)
		{
			return "";
		}
		return Text.removeTags(widgetText).replace('\u00a0', ' ').trim();
	}

	static boolean isRosterHost(String widgetText, Map<String, List<String>> roster)
	{
		if (roster == null || roster.isEmpty())
		{
			return false;
		}
		String name = WomDirectoryClient.normalize(plainHostName(widgetText));
		return !name.isEmpty() && name.length() <= 12 && roster.containsKey(name) &&
			!roster.get(name).isEmpty();
	}

	/** The native POH enter script identifies its corresponding host. */
	static String enterButtonHost(Object[] onOp)
	{
		return onOp != null && onOp.length > 1 && onOp[1] instanceof String
			? WomDirectoryClient.normalize((String)onOp[1])
			: "";
	}

	/** Use RuneLite's own text layout; repainting glyphs made overlapping names. */
	static void colorNativeHostWidget(Widget widget, boolean empireHost,
									  Map<Widget, Integer> originalColors)
	{
		if (widget == null)
		{
			return;
		}
		if (empireHost)
		{
			originalColors.putIfAbsent(widget, widget.getTextColor());
			if (widget.getTextColor() != TAG_COLOR.getRGB())
			{
				widget.setTextColor(TAG_COLOR.getRGB());
			}
		}
		else
		{
			Integer original = originalColors.remove(widget);
			if (original != null)
			{
				widget.setTextColor(original);
			}
		}
	}

	private void restoreHostColors()
	{
		for (Map.Entry<Widget, Integer> entry : originalNameColors.entrySet())
		{
			Widget widget = entry.getKey();
			if (widget != null)
			{
				widget.setTextColor(entry.getValue());
			}
		}
		originalNameColors.clear();
	}

	/** Gold frame inset within a native UI widget's measured bounds. No fill. */
	private static void drawTrim(Graphics2D g, Rectangle bounds, float strength, float cornerRadius)
	{
		if (bounds == null || bounds.width < 8 || bounds.height < 8)
		{
			return;
		}
		Stroke oldStroke = g.getStroke();
		Composite oldComposite = g.getComposite();
		Color oldColor = g.getColor();
		try
		{
			// Pixel-aligned to the exact widget rectangle; no invented offsets or
			// blue background fill that could hide the arrow sprite.
			// Round buttons follow the outer edges more closely than the
			// rectangular host-name boxes. Keep the native arrow unobscured.
			float inset = cornerRadius > 0f ? 0.5f : 1f;
			float x = bounds.x + inset;
			float y = bounds.y + inset;
			float w = bounds.width - 2f * inset;
			float h = bounds.height - 2f * inset;
			Shape outline = new RoundRectangle2D.Float(x, y, w, h, cornerRadius, cornerRadius);
			g.setColor(GOLD_GLOW);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f * strength));
			g.setStroke(new BasicStroke(2.8f));
			g.draw(outline);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.9f * strength));
			g.setStroke(new BasicStroke(1f));
			g.draw(outline);
		}
		finally
		{
			g.setStroke(oldStroke);
			g.setComposite(oldComposite);
			g.setColor(oldColor);
		}
	}

	/** Extend the gold outline to the board's actual left column edge. */
	static Rectangle alignedNameBox(Rectangle name, Rectangle scrolling)
	{
		if (name == null || scrolling == null)
		{
			return null;
		}
		// The name widget's scrolling bounds are inset from the native left
		// column divider. Keep the border at the inner edge of the
		// name column, without extending into the surrounding frame.
		int left = scrolling.x - 1;
		int right = Math.min(name.x + name.width, scrolling.x + scrolling.width);
		return new Rectangle(left, name.y, Math.max(0, right - left), name.height);
	}

	/** A gold border around the whole host-name box, not around its letters. */
	static void drawGoldNameBox(Graphics2D graphics, Rectangle bounds)
	{
		drawTrim(graphics, bounds, 1.0f, 0f);
	}

	/** Outline the actual Enter House arrow widget without tinting its sprite. */
	static void drawGoldArrowTrim(Graphics2D graphics, Rectangle bounds)
	{
		drawTrim(graphics, bounds, 0.95f, 10f);
	}

	@Override public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN ||
			HolyEmpirePvpPolicy.suppressPlayerIndicators(client))
		{
			restoreHostColors();
			sorter.clear();
			return null;
		}
		Map<String, List<String>> roster = plugin.members();
		if (roster.isEmpty())
		{
			restoreHostColors();
			sorter.restore();
			return null;
		}
		Widget board = client.getWidget(InterfaceID.PohBoard.FRAME);
		Widget scrolling = client.getWidget(InterfaceID.PohBoard.SCROLLING);
		Widget names = client.getWidget(InterfaceID.PohBoard.NAME);
		if (board == null || board.isHidden() || scrolling == null || scrolling.isHidden() ||
			names == null || names.isHidden())
		{
			restoreHostColors();
			sorter.clear();
			return null;
		}
		// Sorting is opt-in: every native column and click arrow must move
		// together. Do it only while the board is mounted on the client.
		if (config.pinEmpireHouseHostsFirst())
		{
			sorter.pinEmpireHosts(client, roster);
		}
		else
		{
			sorter.restore();
		}
		if (!config.markEmpireHouseHosts())
		{
			restoreHostColors();
			return null;
		}
		Rectangle visible = scrolling.getBounds();
		Widget[] hosts = names.getDynamicChildren();
		if (visible == null || visible.width < 60 || visible.height < 10 || hosts == null ||
			hosts.length == 0)
		{
			restoreHostColors();
			return null;
		}
		Widget buttons = client.getWidget(InterfaceID.PohBoard.BUTTON);
		Widget[] entryButtons =
			buttons == null || buttons.isHidden() ? null : buttons.getDynamicChildren();

		Color originalColor = graphics.getColor();
		Shape originalClip = graphics.getClip();
		Object originalHint = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
									  RenderingHints.VALUE_ANTIALIAS_ON);
			// Never paint outside the scrollable rows, even when the host list moves.
			// Permit the name-frame's outer left stroke to reach the native
			// column edge while retaining the original vertical clip.
			graphics.clip(
				new Rectangle(visible.x - 3, visible.y, visible.width + 3, visible.height));
			Set<String> matchedHosts = new HashSet<>();
			for (Widget widget : hosts)
			{
				if (widget == null)
				{
					continue;
				}
				boolean member = !widget.isHidden() && isRosterHost(widget.getText(), roster);
				colorNativeHostWidget(widget, member, originalNameColors);
				if (!member)
				{
					continue;
				}
				Rectangle row = widget.getBounds();
				if (row == null || row.height < 10 || !row.intersects(visible))
				{
					continue;
				}
				String name = plainHostName(widget.getText());
				matchedHosts.add(WomDirectoryClient.normalize(name));

				// Native RuneScape text already displays in Empire blue. Do
				// not add custom lettering; it would duplicate or clip the name.
				Rectangle box = alignedNameBox(row, visible);
				drawGoldNameBox(graphics, box);
			}
			// Widget 52:19 is the board's actual Enter House button column.
			// The onOp argument identifies the corresponding host even after
			// other POH plugins reorder or filter the advertisements.
			if (entryButtons != null && !matchedHosts.isEmpty())
			{
				for (Widget button : entryButtons)
				{
					if (button == null || button.isHidden())
					{
						continue;
					}
					if (!matchedHosts.contains(enterButtonHost(button.getOnOpListener())))
					{
						continue;
					}
					Rectangle bounds = button.getBounds();
					if (bounds == null || !bounds.intersects(visible))
					{
						continue;
					}
					drawGoldArrowTrim(graphics, bounds);
				}
			}
		}
		finally
		{
			graphics.setColor(originalColor);
			graphics.setClip(originalClip);
			if (originalHint != null)
			{
				graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalHint);
			}
		}
		return null;
	}
}
