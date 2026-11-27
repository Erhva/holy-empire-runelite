/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import org.junit.Test;
import static org.junit.Assert.*;

public class FederationApplicantOneTimeBypassTest
{
	@Test public void oneApprovedRetryAllowsOnlyTheNamedApplicant()
	{
		FederationApplicantOneTimeBypass pass = new FederationApplicantOneTimeBypass();
		assertTrue(pass.arm("Erhva", 12, "Holy Chaos", "Harohad", 1000));
		assertTrue(pass.consume("erhva", 12, "Holy Chaos", "HAROHAD", 1001));
		assertFalse("Explicit approval is one-time only",
					pass.consume("Erhva", 12, "Holy Chaos", "Harohad", 1002));
	}

	@Test public void otherApplicantCannotUsePriorApproval()
	{
		FederationApplicantOneTimeBypass pass = new FederationApplicantOneTimeBypass();
		assertTrue(pass.arm("Erhva", 12, "Holy Chaos", "Harohad", 2000));
		assertFalse(pass.consume("Erhva", 12, "Holy Chaos", "Different User", 2001));
		assertFalse("A mismatched click invalidates the earlier approval",
					pass.consume("Erhva", 12, "Holy Chaos", "Harohad", 2002));
	}

	@Test public void approvalCannotTransferAcrossAccountsChaptersOrLogins()
	{
		FederationApplicantOneTimeBypass pass = new FederationApplicantOneTimeBypass();
		assertTrue(pass.arm("Erhva", 12, "Holy Chaos", "Harohad", 3000));
		assertFalse(pass.consume("Other Officer", 12, "Holy Chaos", "Harohad", 3001));
		assertTrue(pass.arm("Erhva", 12, "Holy Chaos", "Harohad", 3010));
		assertFalse(pass.consume("Erhva", 13, "Holy Chaos", "Harohad", 3011));
		assertTrue(pass.arm("Erhva", 12, "Holy Chaos", "Harohad", 3020));
		assertFalse(pass.consume("Erhva", 12, "Holy Empire", "Harohad", 3021));
	}

	@Test public void approvalExpiresAndDoesNotSurviveLogout()
	{
		FederationApplicantOneTimeBypass pass = new FederationApplicantOneTimeBypass();
		assertTrue(pass.arm("Erhva", 12, "Holy Chaos", "Harohad", 5000));
		assertFalse(pass.consume("Erhva", 12, "Holy Chaos", "Harohad",
								 5000 + FederationApplicantOneTimeBypass.VALIDITY_MILLIS));
		assertTrue(pass.arm("Erhva", 12, "Holy Chaos", "Harohad", 6000));
		pass.clear();
		assertFalse(pass.consume("Erhva", 12, "Holy Chaos", "Harohad", 6001));
	}

	@Test public void neverAcceptBlankOrInvalidApplicantApproval()
	{
		FederationApplicantOneTimeBypass pass = new FederationApplicantOneTimeBypass();
		assertFalse(pass.arm("Erhva", 12, "Holy Chaos", "Invalid#RSN", 7000));
		assertFalse(pass.arm("Erhva", 12, "Holy Chaos", "", 7000));
		assertFalse(pass.arm("", 12, "Holy Chaos", "Harohad", 7000));
		assertFalse(pass.arm("Erhva", 12, "", "Harohad", 7000));
		assertFalse(pass.consume("Erhva", 12, "Holy Chaos", "Harohad", 7001));
	}
}
