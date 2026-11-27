/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HolyEmpireAttendanceRecoveryStoreTest
{
	@Rule public TemporaryFolder temp = new TemporaryFolder();

	private static HolyEmpireAttendanceTracker tracker(long now, String host)
	{
		Map<String, List<String>> roster = new LinkedHashMap<>();
		roster.put(host, Collections.singletonList("Holy Chaos"));
		roster.put("alice", Collections.singletonList("World 465"));
		HolyEmpireAttendanceTracker tracker = new HolyEmpireAttendanceTracker();
		tracker.start("manual:" + UUID.randomUUID(), "manual", "", "Practice", host, roster, now);
		tracker.observe(Set.of(host, "alice"), now + 1000);
		tracker.observe(Set.of(host, "alice"), now + 1600);
		return tracker;
	}

	private static HolyEmpireAttendanceRecoveryStore store(Path journalFolder) throws Exception
	{
		// Unchecked access is test-only. The shipped plugin exclusively uses
		// RuneLite's framework-provided rooted Filepath.
		return new HolyEmpireAttendanceRecoveryStore(
			net.runelite.client.util.Filepath.Unchecked.getRooted(journalFolder.getParent()));
	}

	@Test public void rootedRuneLiteFilepathKeepsRecoveryInsidePluginFolder() throws Exception
	{
		Path root = temp.newFolder("plugin-root").toPath();
		net.runelite.client.util.Filepath selected =
			net.runelite.client.util.Filepath.Unchecked.getRooted(root);
		HolyEmpireAttendanceRecoveryStore store = new HolyEmpireAttendanceRecoveryStore(selected);
		Assert.assertEquals(root.resolve("attendance-recovery").toRealPath(),
							Path.of(store.directory().toString()).toRealPath());
	}

	@Test public void crashCheckpointRecoversWithSameUuidAndNoExtraTime() throws Exception
	{
		Path path = temp.newFolder("attendance-recovery").toPath();
		long now = 1_800_000_000_000L;
		HolyEmpireAttendanceRecoveryStore a = store(path);
		HolyEmpireAttendanceTracker tracker = tracker(now, "erhva");
		String id = tracker.report().reportId;
		a.checkpoint(tracker.report(), now + 2000);
		HolyEmpireAttendanceRecoveryStore b = store(path);
		List<HolyEmpireAttendanceRecoveryStore.Pending> recovered = b.pendingFor("erhva");
		Assert.assertEquals(1, recovered.size());
		HolyEmpireAttendanceRecoveryStore.Pending pending = recovered.get(0);
		Assert.assertTrue(pending.wasRunning);
		Assert.assertEquals(id, pending.reportId);
		Assert.assertEquals(now / 1000 + 2, pending.payload.get("ended_at").getAsLong());
		Assert.assertEquals(2, pending.payload.getAsJsonArray("attendees").size());
		Assert.assertEquals(2, pending.payload.getAsJsonArray("attendees")
								   .get(0)
								   .getAsJsonObject()
								   .get("ticks")
								   .getAsInt());
		Assert.assertTrue(
			"No Discord credentials in journal",
			Files.readString(path.resolve("report-" + id + ".json")).indexOf("Bearer ") < 0);
		Assert.assertEquals(0, b.pendingFor("some-other-account").size());
		b.acknowledge(id);
		Assert.assertTrue(b.pendingFor("erhva").isEmpty());
	}

	@Test public void finishedCheckpointReplacesRunningAndRetainsFullEvidence() throws Exception
	{
		Path path = temp.newFolder("attendance-recovery").toPath();
		long now = 1_800_000_000_000L;
		HolyEmpireAttendanceRecoveryStore outbox = store(path);
		HolyEmpireAttendanceTracker tracker = tracker(now, "erhva");
		outbox.checkpoint(tracker.report(), now + 2000);
		HolyEmpireAttendanceTracker.Report stale = tracker.report();
		HolyEmpireAttendanceTracker.Report finished = tracker.finish(now + 4000);
		outbox.checkpoint(finished, now + 4000);
		outbox.checkpoint(stale, now + 3000); // delayed worker must not regress evidence
		List<HolyEmpireAttendanceRecoveryStore.Pending> pending = outbox.pendingFor("erhva");
		Assert.assertEquals(1, pending.size());
		Assert.assertFalse(pending.get(0).wasRunning);
		Assert.assertEquals(now / 1000 + 4, pending.get(0).payload.get("ended_at").getAsLong());
		Assert.assertEquals(finished.reportId, pending.get(0).reportId);
		Assert.assertEquals(1, Files.list(path).count());
	}

	@Test public void existingJournalFromBeforeFilepathMigrationRemainsReadable() throws Exception
	{
		Path root = temp.newFolder("old-persisted-journal").toPath();
		long now = 1_800_000_000_000L;
		HolyEmpireAttendanceTracker tracker = tracker(now, "erhva");
		String id = tracker.report().reportId;
		// Simulate existing legacy JSON already persisted by the old NIO code.
		com.google.gson.JsonObject envelope = new com.google.gson.JsonObject();
		envelope.addProperty("version", 1);
		envelope.addProperty("checkpoint_at", now + 2000);
		envelope.addProperty("was_running", true);
		envelope.add("payload", HolyEmpireAttendanceArchiveClient.encodeCheckpoint(tracker.report(),
																				   now + 2000));
		Files.writeString(root.resolve("report-" + id + ".json"), envelope.toString());
		net.runelite.client.util.Filepath target =
			net.runelite.client.util.Filepath.Unchecked.getRooted(root);
		HolyEmpireAttendanceRecoveryStore migrated = new HolyEmpireAttendanceRecoveryStore(target);
		// RuneLite plugin root contains the attendance-recovery subfolder.
		// Simulate an old journal in that unchanged subfolder.
		Path folder = root.resolve("attendance-recovery");
		Files.move(root.resolve("report-" + id + ".json"),
				   folder.resolve("report-" + id + ".json"));
		List<HolyEmpireAttendanceRecoveryStore.Pending> pending = migrated.pendingFor("erhva");
		Assert.assertEquals(1, pending.size());
		Assert.assertEquals(id, pending.get(0).reportId);
		migrated.checkpoint(tracker.finish(now + 4000), now + 4000);
		Assert.assertFalse(migrated.pendingFor("erhva").get(0).wasRunning);
		migrated.acknowledge(id);
		Assert.assertTrue(migrated.pendingFor("erhva").isEmpty());
	}

	@Test public void separateOfficersKeepSeparateLocalReports() throws Exception
	{
		Path path = temp.newFolder("attendance-recovery").toPath();
		HolyEmpireAttendanceRecoveryStore outbox = store(path);
		long now = 1_800_000_000_000L;
		HolyEmpireAttendanceTracker one = tracker(now, "erhva");
		HolyEmpireAttendanceTracker two = tracker(now, "other");
		outbox.checkpoint(one.report(), now + 2000);
		outbox.checkpoint(two.report(), now + 2000);
		Assert.assertEquals(1, outbox.pendingFor("erhva").size());
		Assert.assertEquals(1, outbox.pendingFor("other").size());
		outbox.acknowledge(one.report().reportId);
		Assert.assertEquals(1, outbox.pendingFor("other").size());
		Assert.assertTrue(outbox.pendingFor("erhva").isEmpty());
	}
}
