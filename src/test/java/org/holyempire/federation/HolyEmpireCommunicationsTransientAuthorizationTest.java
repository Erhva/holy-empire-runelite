/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import org.junit.Test;
import static org.junit.Assert.*;

/** A temporary communications outage is not an authorization revocation. */
public class HolyEmpireCommunicationsTransientAuthorizationTest
{
	@Test public void onlyAuthorizationDenialOrDifferentSessionClearsCapabilities()
	{
		assertFalse(FederationPlugin.revokeCapabilitiesAfterCommunicationsFailure(
			"SocketTimeoutException", true));
		assertFalse(FederationPlugin.revokeCapabilitiesAfterCommunicationsFailure(
			"HTTP 502: Bad Gateway", true));
		assertFalse(FederationPlugin.revokeCapabilitiesAfterCommunicationsFailure(
			"Communications are stale", true));
		assertTrue(FederationPlugin.revokeCapabilitiesAfterCommunicationsFailure(
			"HTTP 403: Forbidden", true));
		assertTrue(FederationPlugin.revokeCapabilitiesAfterCommunicationsFailure(
			"HTTP 401: Authorization required", true));
		assertTrue(FederationPlugin.revokeCapabilitiesAfterCommunicationsFailure(
			"HTTP 410: Session expired", true));
		assertTrue(FederationPlugin.revokeCapabilitiesAfterCommunicationsFailure(
			"SocketTimeoutException", false));
	}
}
