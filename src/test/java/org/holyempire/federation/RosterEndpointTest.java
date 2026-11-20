/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import org.junit.Test;
import static org.junit.Assert.*;

public class RosterEndpointTest
{
	@Test public void rosterUrlIsPinnedToReviewedJavaSource()
	{
		assertEquals("https://roster.holyempireosrs.cc", RosterEndpoint.baseUrl());
	}

	@Test public void rosterUrlDoesNotDependOnResourceOrRuntimeOverride()
	{
		String previous = System.getProperty("holyempire.rosterBaseUrl");
		try
		{
			System.setProperty("holyempire.rosterBaseUrl", "http://127.0.0.1:18765");
			assertEquals("https://roster.holyempireosrs.cc", RosterEndpoint.baseUrl());
			assertNull("Roster URL must not come from a resource",
					   RosterEndpoint.class.getResource("/holyempire-roster.properties"));
		}
		finally
		{
			if (previous == null)
			{
				System.clearProperty("holyempire.rosterBaseUrl");
			}
			else
			{
				System.setProperty("holyempire.rosterBaseUrl", previous);
			}
		}
	}
}
