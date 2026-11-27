/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import org.junit.Assert;
import org.junit.Test;

public class HolyEmpireAttendanceReminderPolicyTest
{
	@Test public void manualRemindsAtEveryHalfHourOnlyOnce()
	{
		long start = 1_800_000_000_000L;
		Assert.assertEquals(
			0, HolyEmpireAttendanceReminderPolicy.nextManualSlot(start, start + 29 * 60_000L, 0));
		Assert.assertEquals(
			1, HolyEmpireAttendanceReminderPolicy.nextManualSlot(start, start + 30 * 60_000L, 0));
		Assert.assertEquals(
			0, HolyEmpireAttendanceReminderPolicy.nextManualSlot(start, start + 31 * 60_000L, 1));
		Assert.assertEquals(
			2, HolyEmpireAttendanceReminderPolicy.nextManualSlot(start, start + 60 * 60_000L, 1));
	}

	@Test public void eventReminderOnlyAfterThirtyMinutesAndOnlyOnce()
	{
		long end = 1_800_000_000_000L;
		Assert.assertFalse(
			HolyEmpireAttendanceReminderPolicy.eventReminderDue(end, end + 29 * 60_000L, false));
		Assert.assertTrue(
			HolyEmpireAttendanceReminderPolicy.eventReminderDue(end, end + 30 * 60_000L, false));
		Assert.assertFalse(
			HolyEmpireAttendanceReminderPolicy.eventReminderDue(end, end + 61 * 60_000L, true));
	}
}
