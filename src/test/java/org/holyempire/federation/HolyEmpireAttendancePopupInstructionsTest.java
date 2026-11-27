/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireAttendancePopupInstructionsTest
{
	@Test public void popupMatchesRequestedDiscordInstructions()
	{
		String text = FederationPlugin.attendanceArchiveInstructions(17);
		assertTrue(text.startsWith("Attendance saved as report 17"));
		assertTrue(text.contains("type /attendance and select report>17"));
		assertTrue(text.contains("event - Searches based on event ID's"));
		assertTrue(text.contains("chapter - Select the specific chapter attendance you would like to retrieve for the event"));
		assertTrue(text.contains("post - Select whether you would like the report posted for everyone in the channel or a private message"));
		assertTrue(text.contains("page - View a single page of the attendance report"));
	}
}
