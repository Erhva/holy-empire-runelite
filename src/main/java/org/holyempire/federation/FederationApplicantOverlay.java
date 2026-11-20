/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Read-only markings over the native Applicants interface. Do not mutate
 * native widgets or list contents and do not enable input interception.
 */
public final class FederationApplicantOverlay extends Overlay
{
	private static final Color BANNED = new Color(238, 57, 57);
	private final Client client;
	private final FederationPlugin plugin;

	@Inject
	public FederationApplicantOverlay(Client client, FederationPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		Widget frame = client.getWidget(InterfaceID.ClansApplicants.FRAME);
		if (frame == null || frame.isHidden())
		{
			return null;
		}
		Rectangle bounds = frame.getBounds();
		if (bounds == null || bounds.width < 150)
		{
			return null;
		}
		FederationBanDirectoryClient.Snapshot approved = plugin.officerDirectoryForApplicants();
		// Only paint confirmed matches. The old generic "checking applicants"
		// banner obscured the game's invitation/selection controls on every screen.
		if (approved == null)
		{
			return null;
		}

		Widget applicantList = client.getWidget(InterfaceID.ClansApplicants.LIST_CONTAINER);
		Set<String> located = new HashSet<>();
		int matches = markApplicantRows(graphics, applicantList, approved, located, 0);
		if (matches == 0)
		{
			markApplicantRows(graphics, client.getWidget(InterfaceID.ClansApplicants.LIST),
							  approved, located, 0);
		}

		// Display the warning immediately to the RIGHT of the selected player's
		// name at the TOP of the right-hand details pane. Never cover stat icons.
		String banned = FederationApplicantGuard.matchedBannedApplicant(
			approved, plugin.selectedApplicantFields());
		if (banned != null)
		{
			Widget detail = client.getWidget(InterfaceID.ClansApplicants.INFO);
			Rectangle selectedBounds = detail == null ? null : detail.getBounds();
			if (selectedBounds != null && selectedBounds.width > 110)
			{
				Rectangle nameBounds = findSelectedNameBounds(
					client.getWidget(InterfaceID.ClansApplicants.INFO_CONTAINER), selectedBounds,
					banned, 0);
				drawSelectedBanText(graphics, selectedBounds, nameBounds, banned);
			}
		}
		return null;
	}

	/**
	 * Draw only large, bold RuneScape-style red lettering beside the name.
	 * No background box, fill or badge border obscures the native game UI.
	 */
	static void drawSelectedBanText(Graphics2D graphics, Rectangle pane, Rectangle nameBounds,
									String name)
	{
		Font previous = graphics.getFont();
		Color previousColor = graphics.getColor();
		try
		{
			graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
			Rectangle text = selectedBadgeBounds(graphics, pane, nameBounds, name);
			graphics.setColor(BANNED);
			graphics.drawString("[BANNED]", text.x, text.y + graphics.getFontMetrics().getAscent());
		}
		finally
		{
			graphics.setFont(previous);
			graphics.setColor(previousColor);
		}
	}

	/**
	 * Find the actual player-name widget (not the overall info pane).
	 * Prefer its bounds, with a fixed detail-header fallback for OSRS layouts
	 * that center the name in an oversized text widget.
	 */
	static Rectangle findSelectedNameBounds(Widget widget, Rectangle pane, String name, int depth)
	{
		if (widget == null || widget.isHidden() || depth > 7)
		{
			return null;
		}
		Rectangle best = null;
		String text = FederationApplicantGuard.extractExactName(widget.getText());
		Rectangle bounds = widget.getBounds();
		if (text != null &&
			WomDirectoryClient.normalize(text).equals(WomDirectoryClient.normalize(name)) &&
			bounds != null && bounds.width > 4 && bounds.height > 6 && bounds.y >= pane.y &&
			bounds.y < pane.y + 110 && bounds.x >= pane.x && bounds.x < pane.x + pane.width)
		{
			best = bounds;
		}
		Widget[] children = widget.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				Rectangle found = findSelectedNameBounds(child, pane, name, depth + 1);
				if (found != null && (best == null || (long)found.width * found.height <
														  (long)best.width * best.height))
				{
					best = found;
				}
			}
		}
		return best;
	}

	/** Positioned beside the header name, never at the bottom over skill data. */
	static Rectangle selectedBadgeBounds(Graphics2D graphics, Rectangle pane, Rectangle nameBounds,
										 String name)
	{
		int badgeWidth = graphics.getFontMetrics().stringWidth("[BANNED]") + 14;
		int badgeHeight = 21;
		int nameWidth = graphics.getFontMetrics().stringWidth(name);
		// OSRS fonts render larger than the plugin font; provide extra clearance.
		int estimatedRight = pane.x + pane.width / 2 + Math.max(65, (int)(nameWidth * 1.15f)) + 12;
		int x = estimatedRight;
		if (nameBounds != null && nameBounds.width < pane.width * 0.55f)
		{
			x = Math.max(estimatedRight, nameBounds.x + nameBounds.width + 9);
		}
		x = Math.min(pane.x + pane.width - badgeWidth - 8, Math.max(pane.x + 8, x));
		// The native name row is between the top Skill total button and the
		// lower Skill total summary. Shift slightly below the pane top to align
		// the red warning with the selected name, without covering lower skills.
		int y = pane.y + 6;
		return new Rectangle(x, y, badgeWidth, badgeHeight);
	}

	static int markApplicantRows(Graphics2D g, Widget current,
								 FederationBanDirectoryClient.Snapshot approved, Set<String> seen,
								 int depth)
	{
		if (current == null || current.isHidden() || depth > 7 || seen.size() > 75)
		{
			return 0;
		}
		int count = 0;
		String text = current.getText();
		if (text == null || text.isEmpty())
		{
			text = current.getName();
		}
		String name = FederationApplicantGuard.extractExactName(text);
		if (name != null &&
			!FederationRecruitmentGuard.matchingChapters(approved, name).isEmpty() &&
			seen.add(WomDirectoryClient.normalize(name)))
		{
			count++; // The header flags this even if OSRS supplies no row bounds.
			Rectangle row = current.getBounds();
			if (row != null && row.width > 25 && row.height > 6)
			{
				g.setColor(new Color(180, 5, 5, 60));
				g.fillRoundRect(row.x, row.y, Math.max(120, row.width), row.height, 5, 5);
				g.setColor(BANNED);
				g.setFont(g.getFont().deriveFont(Font.BOLD, 10f));
				int right = row.x + Math.max(5, row.width - 68);
				g.drawString("[BANNED]", right, row.y + Math.max(11, row.height - 3));
			}
		}
		Widget[] children = current.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				count += markApplicantRows(g, child, approved, seen, depth + 1);
			}
		}
		return count;
	}
}
