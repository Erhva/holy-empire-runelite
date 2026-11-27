/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.util.LinkedHashSet;
import java.util.Set;

/** Session-local read-only comparison of the visible native clan bans. */
final class FederationBanResyncReminder
{
	private String chapter = "";
	private Set<String> baseline;
	private Set<String> candidate;
	private int stableReads;

	/** A native clan-officer action, not evidence of server success. */
	static boolean isModerationAction(String option)
	{
		if (option == null)
			return false;
		String cleaned = net.runelite.client.util.Text.removeTags(option)
			.trim().toLowerCase(java.util.Locale.ROOT);
		return cleaned.equals("ban") || cleaned.equals("ban member") ||
			cleaned.equals("ban player") || cleaned.equals("ban user") ||
			cleaned.equals("ban from clan") || cleaned.equals("kick") ||
			cleaned.equals("kick member") || cleaned.equals("kick player") ||
			cleaned.equals("kick from clan") || cleaned.equals("expel") ||
			cleaned.equals("expel member") || cleaned.equals("expel player") ||
			cleaned.equals("remove member") || cleaned.equals("remove from clan");
	}

	/** The Bans page uses Add/Remove, not the player-menu Ban action. */
	static boolean isNativeBanEditAction(String option, int widgetId)
	{
		if (option == null || (widgetId >>> 16) != FederationBanWidgetCapture.BANS_GROUP)
			return false;
		String cleaned = net.runelite.client.util.Text.removeTags(option)
			.trim().toLowerCase(java.util.Locale.ROOT);
		return cleaned.equals("add") || cleaned.equals("remove") ||
			cleaned.equals("delete") || cleaned.equals("unban") ||
			cleaned.equals("add ban") || cleaned.equals("remove ban") ||
			cleaned.equals("ban") || cleaned.equals("confirm");
	}

	static boolean confirmedClanModeration(String message)
	{
		if (message == null) return false;
		String text=net.runelite.client.util.Text.removeTags(message)
			.toLowerCase(java.util.Locale.ROOT).trim();
		return text.matches("(?s).*\\b(?:has been|was)\\s+(?:banned|unbanned|kicked|expelled)\\s+from\\s+(?:the |your )?clan\\b.*")
			|| text.matches("(?s).*\\byou have\\s+(?:banned|unbanned|kicked|expelled)\\b.*\\b(?:clan|member)\\b.*")
			|| text.matches("(?s).*\\b(?:added|removed)\\b.*\\b(?:clan|ban)\\s+list\\b.*")
			|| text.matches("(?s).*\\b(?:clan|ban)\\s+list\\b.*\\b(?:added|removed|unbanned)\\b.*");
	}

	synchronized void reset()
	{
		chapter = "";
		baseline = null;
		candidate = null;
		stableReads = 0;
	}

	/** A successfully acknowledged central sync is our authoritative local baseline. */
	synchronized void synced(FederationBanRegistry.Snapshot snapshot)
	{
		if (snapshot == null)
		{
			reset();
			return;
		}
		chapter = snapshot.chapter;
		baseline = keys(snapshot);
		candidate = new LinkedHashSet<>(baseline);
		stableReads = 2;
	}

	private static Set<String> keys(FederationBanRegistry.Snapshot snapshot)
	{
		Set<String> values = new LinkedHashSet<>();
		for (String name : snapshot.names)
			values.add(WomDirectoryClient.normalize(name));
		return values;
	}

	/**
	 * Any confirmed change after the last sync needs a reminder. Both bans
	 * and unbans matter; never upload automatically. Require two matching
	 * reads, or four when the list briefly becomes completely empty.
	 */
	synchronized boolean observe(FederationBanRegistry.Snapshot snapshot)
	{
		if (snapshot == null || snapshot.chapter == null)
			return false;
		if (!snapshot.chapter.equals(chapter))
		{
			reset();
			chapter = snapshot.chapter;
		}
		Set<String> current = keys(snapshot);
		if (!current.equals(candidate))
		{
			candidate = current;
			stableReads = 1;
			return false;
		}
		if (++stableReads < 2)
			return false;
		if (baseline == null)
		{
			baseline = current;
			return false;
		}
		// The native widget can briefly empty during a rebuild. Only an
		// unchanged empty list held for four polls (about ten seconds) can
		// indicate removing the last ban.
		if (current.isEmpty() && !baseline.isEmpty() && stableReads < 4)
			return false;
		boolean changed = !baseline.equals(current);
		baseline = current;
		return changed;
	}
}
