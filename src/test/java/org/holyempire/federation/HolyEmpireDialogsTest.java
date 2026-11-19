/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import org.junit.Test;
import static org.junit.Assert.*;

/** Geometry-only tests keep modal placement deterministic and headless-safe. */
public class HolyEmpireDialogsTest
{
	@Test
	public void centersWithinUsableMonitorBounds()
	{
		Point point = HolyEmpireDialogs.center(new Rectangle(0, 0, 1920, 1080),
											   new Insets(0, 0, 40, 0), new Dimension(400, 200));
		assertEquals(new Point(760, 420), point);
	}

	@Test
	public void centersOnSecondaryMonitorWithNegativeCoordinates()
	{
		Point point = HolyEmpireDialogs.center(new Rectangle(-2560, 100, 2560, 1440),
											   new Insets(30, 15, 60, 25), new Dimension(600, 240));
		assertEquals(new Point(-1585, 685), point);
	}

	@Test
	public void doesNotPositionOversizedDialogsOutsideVisibleArea()
	{
		Point point = HolyEmpireDialogs.center(
			new Rectangle(0, 0, 640, 480), new Insets(20, 10, 30, 10), new Dimension(1000, 900));
		assertEquals(new Point(10, 20), point);
	}
}
