/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.JButton;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireMoveArrowTest
{
	private BufferedImage render(JButton button)
	{
		Icon icon = button.getIcon();
		BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(),
												BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		icon.paintIcon(button, g, 0, 0);
		g.dispose();
		return image;
	}

	@Test public void drawsActualUpAndDownArrowsInsteadOfMissingFontGlyphs()
	{
		JButton up = HolyEmpireMoveArrow.button(true);
		JButton down = HolyEmpireMoveArrow.button(false);
		assertEquals("", up.getText());
		assertEquals("", down.getText());
		assertNotNull(up.getIcon());
		assertNotNull(down.getIcon());
		assertEquals(14, up.getIcon().getIconWidth());
		BufferedImage u = render(up);
		BufferedImage d = render(down);
		int upNonTransparent = 0;
		int downNonTransparent = 0;
		int differences = 0;
		for (int y = 0; y < 14; y++)
		{
			for (int x = 0; x < 14; x++)
			{
				int a = u.getRGB(x, y);
				int b = d.getRGB(x, y);
				if ((a >>> 24) != 0)
					upNonTransparent++;
				if ((b >>> 24) != 0)
					downNonTransparent++;
				if (a != b)
					differences++;
			}
		}
		assertTrue(upNonTransparent > 15);
		assertTrue(downNonTransparent > 15);
		assertTrue("Up and down arrows must differ visibly", differences > 10);
		down.setEnabled(false);
		assertNotEquals("Disabled arrow must visibly change color", d.getRGB(7, 11),
						render(down).getRGB(7, 11));
	}
}
