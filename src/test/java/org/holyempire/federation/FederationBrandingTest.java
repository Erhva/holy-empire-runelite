/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationBrandingTest
{
	@Test
	public void federationDotDefaultMatchesUserIconBlue()
	{
		Color color = FederationPalette.DEFAULT_DOT_COLOR;
		assertEquals(new Color(152, 200, 248), color);
	}

	@Test
	public void toolbarIconResourceIsPackaged() throws Exception
	{
		try (InputStream input = getClass().getResourceAsStream("/holy-empire-icon.png"))
		{
			assertNotNull(input);
			BufferedImage image = ImageIO.read(input);
			assertNotNull(image);
			assertTrue(image.getWidth() >= 16);
			assertTrue(image.getHeight() >= 32);
			Color sample = new Color(image.getRGB(3, 4), true);
			assertTrue(Math.abs(sample.getRed() - 152) <= 5);
			assertTrue(Math.abs(sample.getGreen() - 200) <= 5);
			assertTrue(Math.abs(sample.getBlue() - 248) <= 5);
		}
	}
}
