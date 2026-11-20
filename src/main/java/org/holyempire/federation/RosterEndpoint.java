/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Public endpoint packaged into the JAR; never contains credentials. */
final class RosterEndpoint
{
	private RosterEndpoint()
	{
	}

	static String baseUrl() throws IOException
	{
		String override = System.getProperty("holyempire.rosterBaseUrl");
		if (override != null)
		{
			return override.trim();
		}
		try (InputStream input =
				 RosterEndpoint.class.getResourceAsStream("/holyempire-roster.properties"))
		{
			if (input == null)
			{
				return "";
			}
			Properties properties = new Properties();
			properties.load(input);
			return properties.getProperty("baseUrl", "").trim();
		}
	}
}
