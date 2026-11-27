/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Deliver at most once per client session, never replay stale broadcasts. */
final class EmpireCommunicationsInbox
{
	private long highestReceivedId;
	private long emergencyShownAtLogin = -1;
	private boolean emergencyReplayPending;

	synchronized void onLogin()
	{
		// Replays only the currently active emergency, not historical broadcasts.
		emergencyReplayPending = true;
		emergencyShownAtLogin = -1;
	}

	synchronized List<EmpireCommunicationsClient.Notice>
	newMessages(EmpireCommunicationsClient.Snapshot feed, Instant now)
	{
		if (feed == null || now == null)
		{
			return Collections.emptyList();
		}
		List<EmpireCommunicationsClient.Notice> result = new ArrayList<>();
		long highest = highestReceivedId;
		List<EmpireCommunicationsClient.Notice> ordered = new ArrayList<>(feed.broadcasts);
		ordered.sort(Comparator.comparingLong(n -> n.id));
		for (EmpireCommunicationsClient.Notice note : ordered)
		{
			highest = Math.max(highest, note.id);
			if (note.id <= highestReceivedId || note.createdAt < now.getEpochSecond() - 150 ||
				note.createdAt > now.getEpochSecond() + 120)
			{
				continue;
			}
			result.add(note);
		}
		highestReceivedId = highest;
		EmpireCommunicationsClient.Notice emergency = feed.emergency;
		if (emergencyReplayPending && emergency != null &&
			emergency.expiresAt > now.getEpochSecond() && emergency.id != emergencyShownAtLogin &&
			result.stream().noneMatch(n -> n.id == emergency.id))
		{
			result.add(emergency);
			emergencyShownAtLogin = emergency.id;
		}
		else if (emergencyReplayPending && emergency != null &&
				 result.stream().anyMatch(n -> n.id == emergency.id))
		{
			emergencyShownAtLogin = emergency.id;
		}
		emergencyReplayPending = false;
		return result;
	}
}
