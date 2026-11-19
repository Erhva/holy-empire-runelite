/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Instant;
import java.util.*;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationBanSessionPrivacyTest
{
	private static final Instant CAPTURED = Instant.parse("2026-11-18T08:00:00Z");

	private static WomDirectoryClient.Snapshot roster()
	{
		Map<String, List<String>> names = new HashMap<>();
		names.put("alpha", Collections.singletonList("Holy Chaos"));
		names.put("beta", Collections.singletonList("Holy Empire"));
		return new WomDirectoryClient.Snapshot(names);
	}

	private static FederationBanRegistry.Snapshot capture(String chapter)
	{
		return FederationBanRegistry.fromVisibleWidget(
			chapter, Collections.singletonList("Test Ban"), CAPTURED);
	}

	@Test
	public void aQueuedUploadMustMatchOriginalAccountChapterAndRank()
	{
		FederationSession session = new FederationSession();
		session.observe("alpha", "Holy Chaos");
		long original = session.generation();
		assertTrue(session.publish("alpha", original, roster()));
		assertTrue(FederationPlugin.currentOfficerCapture(session, "alpha", original,
														  capture("Holy Chaos"), true));
		assertFalse(FederationPlugin.currentOfficerCapture(session, "alpha", original,
														   capture("Holy Empire"), true));
		assertFalse(FederationPlugin.currentOfficerCapture(session, "alpha", original,
														   capture("Holy Chaos"), false));
		session.observe("beta", "Holy Empire");
		assertTrue(session.publish("beta", session.generation(), roster()));
		assertFalse(FederationPlugin.currentOfficerCapture(session, "alpha", original,
														   capture("Holy Chaos"), true));
		session.observe("alpha", "Holy Chaos");
		assertTrue(session.publish("alpha", session.generation(), roster()));
		assertFalse(FederationPlugin.currentOfficerCapture(session, "alpha", original,
														   capture("Holy Chaos"), true));
		session.invalidate("alpha", session.generation());
		assertFalse(FederationPlugin.currentOfficerCapture(session, "alpha", session.generation(),
														   capture("Holy Chaos"), true));
	}

	@Test
	public void temporaryPrivateBansAreClearedOnLogoutOrLossOfAccess() throws Exception
	{
		FederationPanel[] panel = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> panel[0] = new FederationPanel());
		panel[0].banRegistry().put(capture("Holy Chaos"));
		assertEquals(1, panel[0].banRegistry().total());
		panel[0].showMessage("Checking next account...");
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(0, panel[0].banRegistry().total());
		panel[0].banRegistry().put(capture("Holy Chaos"));
		panel[0].showNotVerified();
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals(0, panel[0].banRegistry().total());
		panel[0].banRegistry().put(capture("Holy Chaos"));
		panel[0].clearOfficerEvidence();
		SwingUtilities.invokeAndWait(() -> {});
		assertEquals("Rank loss must clear in-memory private bans", 0,
					 panel[0].banRegistry().total());
	}
}
