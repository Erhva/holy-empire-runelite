/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationMinimapDotTest
{
	@Test
	public void usesNativeFourByFivePixelSilhouette()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			FederationMinimapDot.draw(graphics, 8, 8, Color.MAGENTA);
		}
		finally
		{
			graphics.dispose();
		}
		int[] expected = {0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 2, 0, 2, 2, 0};
		for (int row = 0; row < 5; row++)
		{
			for (int col = 0; col < 4; col++)
			{
				int actual = image.getRGB(6 + col, 6 + row);
				int want = expected[row * 4 + col];
				if (want == 0)
				{
					assertEquals(0, actual);
				}
				if (want == 1)
				{
					assertEquals(Color.MAGENTA.getRGB(), actual);
				}
				if (want == 2)
				{
					assertEquals(Color.BLACK.getRGB(), actual);
				}
			}
		}
	}

	@Test
	public void supportsIndependentColor()
	{
		BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			FederationMinimapDot.draw(graphics, 6, 6, Color.CYAN);
		}
		finally
		{
			graphics.dispose();
		}
		assertEquals(Color.CYAN.getRGB(), image.getRGB(6, 6));
		assertEquals(0, image.getRGB(0, 0));
	}

	@Test
	public void nullColorDoesNotDraw()
	{
		BufferedImage image = new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			FederationMinimapDot.draw(graphics, 6, 6, null);
		}
		finally
		{
			graphics.dispose();
		}
		assertEquals(0, image.getRGB(6, 6));
	}
}
