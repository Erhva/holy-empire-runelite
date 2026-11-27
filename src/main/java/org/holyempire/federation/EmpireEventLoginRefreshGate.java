/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

/**
 * Exactly one initial calendar fetch for each roster-confirmed RuneScape
 * session. The game tick and the periodic WOM sync may both observe the
 * same character, but only a new session generation starts another fetch.
 */
final class EmpireEventLoginRefreshGate
{
    private String account = "";
    private long generation = Long.MIN_VALUE;
    private int interestFailures;

    synchronized boolean claim(String currentAccount, long currentGeneration, boolean eligible)
    {
        if (!eligible || currentAccount == null || currentAccount.isEmpty())
        {
            return false;
        }
        if (currentGeneration == generation && currentAccount.equals(account))
        {
            return false;
        }
        account = currentAccount;
        generation = currentGeneration;
        interestFailures = 0;
        return true;
    }

    /** Returns a bounded retry delay in seconds, or -1 after three failures. */
    synchronized int interestFailed(String currentAccount, long currentGeneration)
    {
        if (!sameSession(currentAccount, currentGeneration) || interestFailures >= 3)
        {
            return -1;
        }
        interestFailures++;
        return interestFailures == 1 ? 15 : interestFailures == 2 ? 30 : 60;
    }

    synchronized boolean retryPending(String currentAccount, long currentGeneration,
                                      int failedAttempt)
    {
        return sameSession(currentAccount, currentGeneration) &&
            interestFailures == failedAttempt && failedAttempt > 0;
    }

    synchronized int failureCount(String currentAccount, long currentGeneration)
    {
        return sameSession(currentAccount, currentGeneration) ? interestFailures : 0;
    }

    synchronized void interestSucceeded(String currentAccount, long currentGeneration)
    {
        if (sameSession(currentAccount, currentGeneration))
        {
            interestFailures = 0;
        }
    }

    private boolean sameSession(String currentAccount, long currentGeneration)
    {
        return generation == currentGeneration && account.equals(currentAccount);
    }
}
