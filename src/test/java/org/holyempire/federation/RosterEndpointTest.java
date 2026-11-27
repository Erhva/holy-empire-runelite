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

    @Test public void rosterUrlDoesNotUseUntrustedConfig()
    {
        assertEquals("https://roster.holyempireosrs.cc", RosterEndpoint.baseUrl());
        assertNull(RosterEndpoint.class.getResource("/holyempire-roster.properties"));
    }
}
