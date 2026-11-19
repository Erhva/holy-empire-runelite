/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.io.IOException;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;

/** CDN outages must not send thousands of client requests to WOM. */
public class CentralOnlyRosterPolicyTest
{
	@Test
	public void missingCdnEndpointNeverFallsBackToWom() throws Exception
	{
		for (String endpoint : new String[] {null, "", "   "})
		{
			try
			{
				WomDirectoryClient.fetchCentralOnly(endpoint, new OkHttpClient());
				fail("Unconfigured CDN endpoint must fail closed");
			}
			catch (IOException expected)
			{
				assertTrue(expected.getMessage().contains("CDN endpoint is not configured"));
			}
		}
	}

	@Test
	public void publicClientDoesNotExposeOldDirectFallbackEntrypoint()
	{
		for (java.lang.reflect.Method method : WomDirectoryClient.class.getDeclaredMethods())
		{
			assertNotEquals("fetchDirect", method.getName());
			if (method.getName().equals("fetch"))
			{
				assertEquals(1, method.getParameterCount());
				assertEquals(OkHttpClient.class, method.getParameterTypes()[0]);
			}
		}
	}
}
