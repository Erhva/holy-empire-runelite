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
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.party.PartyService;
import net.runelite.client.plugins.playerindicators.PlayerIndicatorsConfig.HighlightSetting;
import net.runelite.client.plugins.playerindicators.PlayerIndicatorsConfig;
import net.runelite.client.ui.overlay.*;

public class HolyEmpireNamesOverlay extends Overlay
{
	private final Client client;
	private final FederationPlugin plugin;
	private final FederationConfig config;
	private final PlayerIndicatorsConfig indicators;
	private final PartyService party;

	@Inject
	public HolyEmpireNamesOverlay(Client client, FederationPlugin plugin, FederationConfig config,
								  ConfigManager manager, PartyService party)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.party = party;
		this.indicators = manager.getConfig(PlayerIndicatorsConfig.class);
		setPosition(OverlayPosition.DYNAMIC);
		// Player Indicators draws at MED. Higher priority paints our color over 'Highlight others'.
		setPriority(PRIORITY_HIGH);
	}

	private static boolean enabled(HighlightSetting setting, boolean inPvp)
	{
		return setting == HighlightSetting.ENABLED || (setting == HighlightSetting.PVP && inPvp);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverheadNames())
		{
			return null;
		}
		Map<String, List<String>> roster = plugin.members();
		if (roster.isEmpty())
		{
			return null;
		}
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}
		boolean inPvp = client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1 ||
						client.getVarbitValue(VarbitID.PVP_AREA_CLIENT) == 1;
		int team = local.getTeam();
		List<String> localChapters = roster.getOrDefault(
			WomDirectoryClient.normalize(local.getName()), Collections.emptyList());
		for (Player player : client.getPlayers())
		{
			if (player == null || player == local || player.getName() == null)
			{
				continue;
			}
			List<String> playerClans = roster.get(WomDirectoryClient.normalize(player.getName()));
			if (playerClans == null || playerClans.isEmpty())
			{
				continue;
			}
			boolean same = SameChapterVisibility.isSameChapter(localChapters, playerClans);
			if (!SameChapterVisibility.showOverhead(config.showOverheadNames(), same,
													config.showSameChapterOverhead()))
			{
				continue;
			}
			boolean inParty =
				party.isInParty() && party.getMemberByDisplayName(player.getName()) != null;
			if (!HolyEmpireOverheadPriority.mayDraw(
					inParty && enabled(indicators.highlightPartyMembers(), inPvp),
					player.isFriend() && enabled(indicators.highlightFriends(), inPvp),
					player.isFriendsChatMember() &&
						enabled(indicators.highlightFriendsChat(), inPvp),
					team != 0 && player.getTeam() == team &&
						enabled(indicators.highlightTeamMembers(), inPvp),
					player.isClanMember() && enabled(indicators.highlightClanMembers(), inPvp)))
			{
				continue;
			}
			String name = net.runelite.client.util.Text.sanitize(player.getName());
			Point pos =
				player.getCanvasTextLocation(graphics, name, player.getLogicalHeight() + 40);
			if (pos != null)
			{
				OverlayUtil.renderTextLocation(
					graphics, pos, name, ChapterIndicatorColors.overheadColor(config, playerClans));
			}
		}
		return null;
	}
}
