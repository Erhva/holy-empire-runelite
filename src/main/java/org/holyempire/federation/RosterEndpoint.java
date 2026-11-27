/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

/** Production roster origin is fixed in audited Java source. */
final class RosterEndpoint
{
	private static final String ROSTER_ORIGIN = "https://roster.holyempireosrs.cc";

	private RosterEndpoint()
	{
	}

	static String baseUrl()
	{
		return ROSTER_ORIGIN;
	}
}
