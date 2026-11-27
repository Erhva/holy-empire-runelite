/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import okhttp3.OkHttpClient;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.*;

/** Opt-in smoke check against an isolated origin or the deployed public roster CDN. */
public class CentralRosterLiveSmokeTest
{
	@Test public void configuredOriginPublishesCompleteVerifiableRoster() throws Exception
	{
		String base = System.getProperty("holyempire.testLiveOrigin", "");
		Assume.assumeTrue("Live origin is only tested on explicit request", !base.isEmpty());
		assertTrue("Live smoke test must use loopback or the Holy Empire public CDN",
				   base.startsWith("http://127.0.0.1:") ||
					   base.equals("https://roster.holyempireosrs.cc"));
		WomDirectoryClient.Snapshot snapshot = CentralRosterClient.fetch(base, new OkHttpClient());
		// The live dynamic V2 feed can add chapters without updating the
		// static 16-group fallback bundled in the RuneLite client.
		assertTrue("Legacy chapters must remain available as V2 grows",
				   snapshot.chapters.size() >= WomDirectoryClient.GROUPS.length);
		if (snapshot.chapters.size() > WomDirectoryClient.GROUPS.length)
			assertTrue("New V2 chapter Ultramarines must be discoverable",
					   snapshot.chapters.containsKey("Ultramarines"));
		assertTrue("At least one chapter member expected", snapshot.members.size() > 0);
		assertTrue("Six Shooters chapter must exist",
				   snapshot.chapters.containsKey("Six Shooters"));
		assertTrue("House of 465 chapter must exist",
				   snapshot.chapters.containsKey("House of 465"));
		assertTrue("Snapshot must identify its generation time", snapshot.sourceTimestampMs > 0);
		// The V2 public roster may use a later generation than the V1
		// ban-warning index. A V2 client deliberately fails closed for
		// mismatched warning snapshots; the roster smoke test must not
		// incorrectly require availability in that case. The dedicated
		// CentralRosterClientTest verifies warning integrity separately.
		if (base.equals("https://roster.holyempireosrs.cc") &&
			snapshot.chapters.size() == WomDirectoryClient.GROUPS.length)
		{
			assertTrue("Production V1 roster must include verified nonmember ban warnings",
					   snapshot.publicBanStatusAvailable);
		}
	}
}
