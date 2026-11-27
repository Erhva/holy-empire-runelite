/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

/** Pure reminder scheduling; never sends game actions or network requests. */
final class HolyEmpireAttendanceReminderPolicy
{
	static final long HALF_HOUR = 30L * 60_000L;

	private HolyEmpireAttendanceReminderPolicy()
	{
	}

	static long nextManualSlot(long startedAt, long now, long lastDelivered)
	{
		if (startedAt <= 0 || now < startedAt + HALF_HOUR)
			return 0;
		long slot = (now - startedAt) / HALF_HOUR;
		return slot > lastDelivered ? slot : 0;
	}

	static boolean eventReminderDue(long scheduledEnd, long now, boolean delivered)
	{
		return scheduledEnd > 0 && !delivered && now >= scheduledEnd + HALF_HOUR;
	}
}
