/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class HolyEmpireAttendanceArchiveClientTest
{
	@Test public void encodesObserverReportedChapterBreakdownAndStableReportId()
	{
		Map<String, List<String>> roster = new LinkedHashMap<>();
		roster.put("host", Collections.singletonList("Holy Empire"));
		roster.put("alice", Arrays.asList("Holy Chaos", "Holy Guard"));
		HolyEmpireAttendanceTracker tracker = new HolyEmpireAttendanceTracker();
		tracker.start("123456789012345678", "empire", "", "Mass event", "host", roster, 1000000);
		tracker.observe(Collections.singleton("Alice"), 1000600);
		tracker.observe(Collections.singleton("Alice"), 1001200);
		HolyEmpireAttendanceTracker.Report report = tracker.finish(1001300);
		JsonObject doc = HolyEmpireAttendanceArchiveClient.encode(report);
		Assert.assertEquals(report.reportId, doc.get("report_id").getAsString());
		Assert.assertEquals("empire", doc.get("scope").getAsString());
		Assert.assertEquals(15, doc.get("range_tiles").getAsInt());
		Assert.assertEquals(1, doc.getAsJsonArray("attendees").size());
		JsonObject entry = doc.getAsJsonArray("attendees").get(0).getAsJsonObject();
		Assert.assertEquals(2, entry.get("ticks").getAsInt());
		Assert.assertEquals("Holy Chaos", entry.get("chapter").getAsString());
		Assert.assertEquals(2, entry.getAsJsonArray("chapters").size());
		Assert.assertEquals(1000L, doc.get("started_at").getAsLong());
	}
}
