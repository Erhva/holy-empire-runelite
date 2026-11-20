/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

/**
 * The public roster origin is defined in audited Java source, not loaded from
 * a mutable resource or runtime configuration.
 */
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
