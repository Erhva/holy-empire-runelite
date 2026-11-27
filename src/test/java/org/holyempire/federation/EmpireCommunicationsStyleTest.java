/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmpireCommunicationsStyleTest
{
	private static EmpireCommunicationsClient.Notice notice(String kind)
	{
		long now = Instant.now().getEpochSecond();
		return new EmpireCommunicationsClient.Notice(41, kind, "Test", "Example message", now,
													 now + 7200);
	}

	@Test public void defaultsUseBlueAndDarkerRed()
	{
		FederationConfig settings = new FederationConfig() {};
		assertEquals(new Color(80, 170, 255), settings.broadcastChatColor());
		assertEquals(new Color(216, 48, 48), settings.emergencyChatColor());
		assertEquals(settings.broadcastChatColor(),
					 EmpireCommunicationsChatMessages.colorFor(notice("broadcast"), null, null));
		assertEquals(settings.emergencyChatColor(),
					 EmpireCommunicationsChatMessages.colorFor(notice("emergency"), null, null));
	}

	@Test public void eachChatTypeRespectsItsIndependentConfiguration()
	{
		Color blue = new Color(17, 35, 201);
		Color red = new Color(150, 12, 44);
		assertEquals(blue,
					 EmpireCommunicationsChatMessages.colorFor(notice("broadcast"), blue, red));
		assertEquals(red,
					 EmpireCommunicationsChatMessages.colorFor(notice("emergency"), blue, red));
	}

	@Test public void repeatedMarqueePaintDoesNotProduceADeadScreenOrNeedReset()
	{
		EmpireEmergencyTicker ticker = new EmpireEmergencyTicker();
		ticker.setSize(240, 33);
		ticker.setWarning(notice("emergency"));
		BufferedImage image = new BufferedImage(240, 33, BufferedImage.TYPE_INT_RGB);
		// The ticker can paint a full frame at a narrow width even when its
		// message is much wider than the viewport.
		ticker.paint(image.getGraphics());
		assertTrue(ticker.isVisible());
		assertTrue(ticker.currentWarning().contains("EMERGENCY"));
		ticker.stopTicker();
	}
}
