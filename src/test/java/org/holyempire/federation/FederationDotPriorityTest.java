/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import org.junit.Test;
import static org.junit.Assert.*;

public class FederationDotPriorityTest
{
	@Test
	public void friendRetainsBuiltinDot()
	{
		assertFalse(FederationDotPriority.allowFederationDot(true, false, false, 0, 0));
	}

	@Test
	public void friendsChatRetainsBuiltinDot()
	{
		assertFalse(FederationDotPriority.allowFederationDot(false, true, false, 0, 0));
	}

	@Test
	public void clanRetainsBuiltinDot()
	{
		assertFalse(FederationDotPriority.allowFederationDot(false, false, true, 0, 0));
	}

	@Test
	public void teamRetainsBuiltinDot()
	{
		assertFalse(FederationDotPriority.allowFederationDot(false, false, false, 42, 42));
	}

	@Test
	public void noTeamDoesNotMaskOthers()
	{
		assertTrue(FederationDotPriority.allowFederationDot(false, false, false, 0, 0));
	}

	@Test
	public void unrelatedTeamEligible()
	{
		assertTrue(FederationDotPriority.allowFederationDot(false, false, false, 17, 42));
	}
}
