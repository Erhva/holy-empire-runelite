/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class HolyEmpireAttendanceTrackerTest
{
	private static Map<String, List<String>> roster()
	{
		Map<String, List<String>> r = new LinkedHashMap<>();
		r.put("host", Collections.singletonList("Holy Empire"));
		r.put("alice", Arrays.asList("Holy Chaos", "Holy Guard"));
		r.put("bob", Collections.singletonList("World 465"));
		return r;
	}

	@Test public void tracksEmpireMembersAcrossChaptersWithoutDuplicates()
	{
		HolyEmpireAttendanceTracker s = new HolyEmpireAttendanceTracker();
		s.start("manual:00000000-0000-0000-0000-000000000001", "manual", "", "Mass", "host",
				roster(), 1000);
		s.observe(new LinkedHashSet<>(Arrays.asList("host", "Alice", "aLICE", "stranger")), 1600);
		s.observe(new LinkedHashSet<>(Arrays.asList("bob", "alice")), 2200);
		HolyEmpireAttendanceTracker.Report result = s.finish(2800);
		Assert.assertFalse(s.active());
		Assert.assertEquals(3, result.members());
		Assert.assertEquals("Holy Chaos", result.entries.get(0).chapter);
		Assert.assertEquals(2, result.entries.get(0).ticks);
		Assert.assertEquals(1, result.entries.get(0).seconds());
		Assert.assertEquals("World 465", result.entries.get(2).chapter);
		Assert.assertEquals(2, result.entries.get(0).chapters.size());
		Assert.assertEquals(0, result.entries.get(1).seconds()); // one 600ms tick rounds down
	}

	@Test public void doesNotAccrueTimeWhileNotReceivingGameTicks()
	{
		HolyEmpireAttendanceTracker s = new HolyEmpireAttendanceTracker();
		s.start("manual:00000000-0000-0000-0000-000000000002", "manual", "", "Boss", "host",
				roster(), 1000);
		s.observe(Collections.singleton("alice"), 1600);
		s.observe(Collections.singleton("alice"), 500_000);
		Assert.assertEquals(1, s.finish(500_100).entries.get(0).seconds());
	}

	@Test public void fullEmpireRosterMayExceedAttendanceReportCap()
	{
		Map<String, List<String>> full = new LinkedHashMap<>();
		full.put("host", Collections.singletonList("Holy Empire"));
		for (int i = 0; i < 2500; i++)
		{
			full.put("member" + i,
					 Collections.singletonList(i % 2 == 0 ? "Holy Chaos" : "World 465"));
		}
		HolyEmpireAttendanceTracker s = new HolyEmpireAttendanceTracker();
		s.start("manual:00000000-0000-0000-0000-000000000777", "manual", "", "Mass", "host", full,
				1000);
		s.observe(Collections.singleton("member2499"), 1600);
		Assert.assertEquals(1, s.finish(2200).members());
	}

	@Test public void onlyVerifiedHostMayStart()
	{
		HolyEmpireAttendanceTracker s = new HolyEmpireAttendanceTracker();
		try
		{
			s.start("manual:00000000-0000-0000-0000-000000000003", "manual", "", "Boss", "outsider",
					roster(), 1);
			Assert.fail("nonmember host accepted");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	@Test public void pausedAttendanceDoesNotAccrueObservationsAndResumes()
	{
		HolyEmpireAttendanceTracker tracker = new HolyEmpireAttendanceTracker();
		tracker.start("manual:00000000-0000-0000-0000-000000000090", "manual", "", "Pause test",
					  "host", roster(), 1000);
		tracker.observe(Collections.singleton("alice"), 1600);
		tracker.pause();
		Assert.assertTrue(tracker.active());
		Assert.assertTrue(tracker.paused());
		Assert.assertTrue(tracker.report().paused);
		tracker.observe(Collections.singleton("alice"), 2200);
		Assert.assertEquals(1, tracker.report().entries.get(0).ticks);
		tracker.resume();
		Assert.assertFalse(tracker.paused());
		tracker.observe(Collections.singleton("alice"), 2800);
		Assert.assertEquals(2, tracker.report().entries.get(0).ticks);
		tracker.pause();
		HolyEmpireAttendanceTracker.Report finished = tracker.finish(3200);
		Assert.assertFalse(finished.running);
		Assert.assertFalse(finished.paused);
		Assert.assertEquals(2, finished.entries.get(0).ticks);
	}

	@Test public void attendanceMinimumDefaultsToTenAndCapsAtOneHundredTwenty()
	{
		javax.swing.SpinnerNumberModel setting = FederationPanel.attendanceMinimumModel();
		Assert.assertEquals(10, setting.getNumber().intValue());
		setting.setValue(0);
		setting.setValue(7);
		Assert.assertEquals(7, setting.getNumber().intValue());
		setting.setValue(120);
		Assert.assertEquals(120, setting.getNumber().intValue());
		try
		{
			setting.setValue(121);
			Assert.fail("120 minute cap was bypassed");
		}
		catch (IllegalArgumentException expected)
		{
		}
		try
		{
			setting.setValue(-1);
			Assert.fail("Negative minimum was accepted");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	@Test public void discordAttendanceCopyIsFencedAndKeepsChapterTables()
	{
		String original = "Recruiting\nHoly Chaos (1)\nName          | Time\nerhva         | 58:49\n";
		String fenced = FederationPanel.discordAttendanceCodeBlock(original);
		Assert.assertTrue(fenced.startsWith("```\nRecruiting\nHoly Chaos (1)"));
		Assert.assertTrue(fenced.contains("Name          | Time\nerhva         | 58:49"));
		Assert.assertTrue(fenced.endsWith("\n```"));
		Assert.assertEquals("```\n[code fence]\n```",
			FederationPanel.discordAttendanceCodeBlock("```"));
		Assert.assertEquals("", FederationPanel.discordAttendanceCodeBlock(""));
	}

	@Test public void minimumTimeUsesObservedTicksAndSeparatesReport()
	{
		HolyEmpireAttendanceTracker t = new HolyEmpireAttendanceTracker();
		t.start("manual:00000000-0000-0000-0000-000000000095", "manual", "",
			"Threshold", "host", roster(), 1000);
		for (int i = 0; i < 100; i++)
		{
			java.util.Set<String> present = new LinkedHashSet<>();
			present.add("alice");
			if (i < 99) present.add("bob");
			t.observe(present, 1600L + i * 600L);
		}
		t.pause();
		t.observe(Collections.singleton("bob"), 80000L);
		t.resume();
		HolyEmpireAttendanceTracker.Report report = t.finish(90000L);
		HolyEmpireAttendanceTracker.Entry alice = report.entries.stream()
			.filter(e -> "alice".equals(e.name)).findFirst().get();
		HolyEmpireAttendanceTracker.Entry bob = report.entries.stream()
			.filter(e -> "bob".equals(e.name)).findFirst().get();
		Assert.assertTrue(FederationPanel.meetsAttendanceMinimum(alice, 1));
		Assert.assertFalse(FederationPanel.meetsAttendanceMinimum(bob, 1));
		Assert.assertTrue(FederationPanel.meetsAttendanceMinimum(bob, 0));
		String copy = HolyEmpireAttendanceReportClient.formatLocal(report, 12, 1);
		Assert.assertTrue(copy.contains("PRESENT MEMBERS (01:00 Minimum)"));
		Assert.assertTrue(copy.contains("Total: 2 | Present: 1 | Below: 1"));
		Assert.assertTrue(copy.indexOf("alice") < copy.indexOf("BELOW MINIMUM"));
		Assert.assertTrue(copy.indexOf("bob") > copy.indexOf("BELOW MINIMUM"));
	}

	@Test public void cannotStartConcurrentSessionOrClearRunningSession()
	{
		HolyEmpireAttendanceTracker s = new HolyEmpireAttendanceTracker();
		s.start("manual:00000000-0000-0000-0000-000000000004", "manual", "", "Boss", "host",
				roster(), 1000);
		try
		{
			s.start("manual:00000000-0000-0000-0000-000000000005", "manual", "", "Other", "host",
					roster(), 1100);
			Assert.fail();
		}
		catch (IllegalStateException expected)
		{
		}
		try
		{
			s.clear();
			Assert.fail();
		}
		catch (IllegalStateException expected)
		{
		}
		s.finish(1600);
		s.clear();
		Assert.assertFalse(s.hasReport());
	}
}
