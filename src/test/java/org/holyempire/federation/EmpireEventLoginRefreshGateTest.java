/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import org.junit.Test;

import static org.junit.Assert.*;

public class EmpireEventLoginRefreshGateTest
{
    @Test public void calendarLoadsOncePerEligibleLoginNotPerGameTickOrRosterPoll()
    {
        EmpireEventLoginRefreshGate gate = new EmpireEventLoginRefreshGate();
        assertFalse(gate.claim("Erhva", 3L, false));
        assertFalse(gate.claim("", 3L, true));
        assertTrue("First eligible login primes Events", gate.claim("Erhva", 3L, true));
        assertFalse("A second game tick must not reload Events", gate.claim("Erhva", 3L, true));
        assertFalse("Periodic WOM sync must not reload Events", gate.claim("Erhva", 3L, true));
        assertTrue("Logging back into the same character is a new session",
            gate.claim("Erhva", 4L, true));
        assertFalse(gate.claim("Erhva", 4L, true));
    }

    @Test public void transientSubscriberFailureGetsBoundedRetries()
    {
        EmpireEventLoginRefreshGate gate = new EmpireEventLoginRefreshGate();
        assertTrue(gate.claim("Erhva", 11L, true));
        assertEquals(15, gate.interestFailed("Erhva", 11L));
        assertTrue(gate.retryPending("Erhva", 11L, 1));
        assertEquals(30, gate.interestFailed("Erhva", 11L));
        assertFalse("Old delayed retry is superseded", gate.retryPending("Erhva", 11L, 1));
        assertTrue(gate.retryPending("Erhva", 11L, 2));
        assertEquals(60, gate.interestFailed("Erhva", 11L));
        assertEquals(-1, gate.interestFailed("Erhva", 11L));
        gate.interestSucceeded("Erhva", 11L);
        assertFalse(gate.retryPending("Erhva", 11L, 3));
        assertEquals(15, gate.interestFailed("Erhva", 11L));
        assertTrue("New login starts fresh retries", gate.claim("Erhva", 12L, true));
        assertEquals(0, gate.failureCount("Erhva", 12L));
        assertFalse(gate.retryPending("Erhva", 11L, 1));
        assertEquals(-1, gate.interestFailed("Erhva", 11L));
    }

    @Test public void aDifferentCharacterCannotInheritTheOtherCharactersRefresh()
    {
        EmpireEventLoginRefreshGate gate = new EmpireEventLoginRefreshGate();
        assertTrue(gate.claim("Erhva", 8L, true));
        assertTrue(gate.claim("Another Player", 8L, true));
        assertFalse(gate.claim("Another Player", 8L, true));
        assertTrue(gate.claim("Erhva", 9L, true));
    }
}
