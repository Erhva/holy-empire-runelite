/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

/** Read-only capture using the legacy RuneLite ban-list widget structure. */
final class FederationBanWidgetCapture
{
	// Observed in P2GR's former Ban List Enhanced exporter; verify in game before release.
	static final int BANS_GROUP = 689;
	static final int BANS_CONTAINER_CHILD = 6;

	private FederationBanWidgetCapture()
	{
	}

	static FederationBanRegistry.Snapshot read(Client client, Instant now)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			throw new IllegalStateException("Log into RuneScape before capturing.");
		}
		ClanSettings settings = client.getClanSettings();
		if (settings == null || FederationBanRegistry.officialChapter(settings.getName()) == null)
		{
			throw new IllegalStateException(
				"Join your own configured Holy Empire clan before capturing.");
		}
		Widget page = client.getWidget(BANS_GROUP, 0);
		Widget entries = client.getWidget(BANS_GROUP, BANS_CONTAINER_CHILD);
		if (page == null || entries == null || page.isHidden() || entries.isHidden())
		{
			throw new IllegalStateException(
				"Open your clan's Settings → Bans page before pressing Capture Ban List.");
		}
		Widget[] children = entries.getChildren();
		if (children == null)
		{
			throw new IllegalStateException(
				"Ban-list UI is not populated; open Bans and try again.");
		}
		// RuneLite getChildren() is a sparse dynamic-child array. Its allocated
		// length can greatly exceed the number of bans shown (e.g. hundreds of
		// null or empty slots for only three banned players). Never treat the
		// array capacity as a ban count; validate actual RSNs after filtering.
		if (children.length > 16384)
		{
			throw new IllegalStateException("Ban-list widget exceeded safe inspection limit.");
		}
		List<String> names = new ArrayList<>();
		for (Widget child : children)
		{
			if (child == null || child.getText() == null)
			{
				continue;
			}
			String name = Text.removeTags(child.getText());
			if (name != null)
			{
				names.add(name);
			}
		}
		return FederationBanRegistry.fromVisibleWidget(settings.getName(), names, now);
	}
}
