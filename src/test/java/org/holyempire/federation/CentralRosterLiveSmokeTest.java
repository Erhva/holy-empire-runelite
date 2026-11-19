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
	@Test
	public void configuredOriginPublishesCompleteVerifiableRoster() throws Exception
	{
		String base = System.getProperty("holyempire.testLiveOrigin", "");
		Assume.assumeTrue("Live origin is only tested on explicit request", !base.isEmpty());
		assertTrue("Live smoke test must use loopback or the Holy Empire public CDN",
				   base.startsWith("http://127.0.0.1:") ||
					   base.equals("https://roster.holyempireosrs.cc"));
		WomDirectoryClient.Snapshot snapshot = CentralRosterClient.fetch(base, new OkHttpClient());
		assertEquals(WomDirectoryClient.GROUPS.length, snapshot.chapters.size());
		assertTrue("At least one chapter member expected", snapshot.members.size() > 0);
		assertTrue("Six Shooters chapter must exist",
				   snapshot.chapters.containsKey("Six Shooters"));
		assertTrue("House of 465 chapter must exist",
				   snapshot.chapters.containsKey("House of 465"));
		assertTrue("Snapshot must identify its generation time", snapshot.sourceTimestampMs > 0);
		if (base.equals("https://roster.holyempireosrs.cc"))
			assertTrue("Production manifest must include verified nonmember ban warnings",
					   snapshot.publicBanStatusAvailable);
	}
}
