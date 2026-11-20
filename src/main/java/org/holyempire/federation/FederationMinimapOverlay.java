/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class FederationMinimapOverlay extends Overlay
{
	private final Client client;
	private final FederationPlugin plugin;
	private final FederationConfig config;

	@Inject
	public FederationMinimapOverlay(Client client, FederationPlugin plugin, FederationConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showFederationDots())
		{
			return null;
		}
		Map<String, List<String>> lookup = plugin.members();
		if (lookup.isEmpty())
		{
			return null;
		}
		Player local = client.getLocalPlayer();
		int localTeam = local == null ? 0 : local.getTeam();
		List<String> localChapters =
			local == null ? Collections.emptyList()
						  : lookup.getOrDefault(WomDirectoryClient.normalize(local.getName()),
												Collections.emptyList());
		for (Player player : client.getPlayers())
		{
			if (player == null || player == local || player.getName() == null)
			{
				continue;
			}
			String name = WomDirectoryClient.normalize(player.getName());
			List<String> playerClans = lookup.get(name);
			if (playerClans == null || playerClans.isEmpty())
			{
				continue;
			}
			boolean same = SameChapterVisibility.isSameChapter(localChapters, playerClans);
			if (!SameChapterVisibility.showDot(config.showFederationDots(), same,
											   config.showSameChapterDots()))
			{
				continue;
			}
			// A deliberate same-chapter opt-in may replace the native clan dot, while
			// preserving higher-priority friends, Friends Chat, and team markers.
			if (!FederationDotPriority.allowFederationDot(
					player.isFriend(), player.isFriendsChatMember(), player.isClanMember() && !same,
					localTeam, player.getTeam()))
			{
				continue;
			}
			net.runelite.api.Point location =
				Perspective.localToMinimap(client, player.getLocalLocation());
			if (location != null)
			{
				FederationMinimapDot.draw(g, location.getX(), location.getY(),
										  ChapterIndicatorColors.dotColor(config, playerClans));
			}
		}
		return null;
	}
}
