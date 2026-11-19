/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

/**
 * Single-use confirmation for accepting one explicitly named banned applicant.
 * The user must first acknowledge the specific ban warning, then click Accept
 * again within the short expiry. No automatic game action is ever sent.
 */
final class FederationApplicantOneTimeBypass
{
	static final long VALIDITY_MILLIS = 15_000;
	private Approval pending;

	private static final class Approval
	{
		final String account;
		final long generation;
		final String chapter;
		final String applicant;
		final long expiresAt;

		Approval(String account, long generation, String chapter, String applicant, long expiresAt)
		{
			this.account = account;
			this.generation = generation;
			this.chapter = chapter;
			this.applicant = applicant;
			this.expiresAt = expiresAt;
		}
	}

	synchronized void clear()
	{
		pending = null;
	}

	synchronized boolean arm(String account, long generation, String chapter, String applicant,
							 long now)
	{
		String valid = FederationBanRegistry.validName(applicant);
		if (valid == null || account == null || account.isEmpty() || chapter == null ||
			chapter.isEmpty())
		{
			pending = null;
			return false;
		}
		pending = new Approval(WomDirectoryClient.normalize(account), generation, chapter,
							   WomDirectoryClient.normalize(valid), now + VALIDITY_MILLIS);
		return true;
	}

	/** A mismatching retry invalidates rather than preserving the previous pass. */
	synchronized boolean consume(String account, long generation, String chapter, String applicant,
								 long now)
	{
		Approval prior = pending;
		pending = null;
		if (prior == null)
			return false;
		return now < prior.expiresAt &&
			prior.account.equals(WomDirectoryClient.normalize(account)) &&
			prior.generation == generation && prior.chapter.equals(chapter) &&
			prior.applicant.equals(WomDirectoryClient.normalize(applicant));
	}
}
