/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class RosterEndpointTest
{
	private String previous;

	@Before public void clearOverride()
	{
		previous = System.getProperty("holyempire.rosterBaseUrl");
		System.clearProperty("holyempire.rosterBaseUrl");
	}

	@After public void restoreOverride()
	{
		if (previous == null)
			System.clearProperty("holyempire.rosterBaseUrl");
		else
			System.setProperty("holyempire.rosterBaseUrl", previous);
	}

	@Test
	public void jarResourceCarriesBuildSelectedEndpoint() throws Exception
	{
		assertNotNull(RosterEndpoint.class.getResource("/holyempire-roster.properties"));
		assertEquals(System.getProperty("holyempire.expectedRosterBaseUrl", ""),
					 RosterEndpoint.baseUrl());
	}

	@Test
	public void runtimeOverrideSupportsIsolatedLocalTesting() throws Exception
	{
		System.setProperty("holyempire.rosterBaseUrl", " http://127.0.0.1:18765 ");
		assertEquals("http://127.0.0.1:18765", RosterEndpoint.baseUrl());
	}

	@Test
	public void explicitEmptyOverrideCanDisablePackagedEndpoint() throws Exception
	{
		System.setProperty("holyempire.rosterBaseUrl", "");
		assertEquals("", RosterEndpoint.baseUrl());
	}
}
