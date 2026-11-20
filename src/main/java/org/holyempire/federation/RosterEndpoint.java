/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

/**
 * The production roster origin is defined in audited Java source.
 * Developer builds can select an alternative origin with a JVM property,
 * without introducing a configurable URL into packaged resources.
 */
final class RosterEndpoint
{
	private static final String ROSTER_ORIGIN = "https://roster.holyempireosrs.cc";

	private RosterEndpoint()
	{
	}

	static String baseUrl()
	{
		String override = System.getProperty("holyempire.rosterBaseUrl");
		return override == null ? ROSTER_ORIGIN : override.trim();
	}
}
