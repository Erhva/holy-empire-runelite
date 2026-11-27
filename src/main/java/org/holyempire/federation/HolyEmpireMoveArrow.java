/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.BasicStroke;
import java.awt.Cursor;
import javax.swing.BorderFactory;
import net.runelite.client.ui.ColorScheme;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.SwingConstants;

/** Vector-drawn arrow icons avoid missing glyphs in RuneLite's custom font. */
final class HolyEmpireMoveArrow implements Icon
{
	private static final int SIZE = 14;
	private final boolean up;

	private HolyEmpireMoveArrow(boolean up)
	{
		this.up = up;
	}

	static JButton button(boolean up)
	{
		JButton button = new JButton();
		button.setIcon(new HolyEmpireMoveArrow(up));
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.TEXT_COLOR);
		button.setBorder(BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR));
		button.setOpaque(true);
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setVerticalAlignment(SwingConstants.CENTER);
		button.setIconTextGap(0);
		button.setFocusable(false);
		return button;
	}

	@Override public int getIconWidth()
	{
		return SIZE;
	}

	@Override public int getIconHeight()
	{
		return SIZE;
	}

	@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
	{
		Graphics2D g = (Graphics2D)graphics.create();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(component.isEnabled() ? ColorScheme.TEXT_COLOR
											 : ColorScheme.CONTROL_COLOR.darker());
			int cx = x + SIZE / 2;
			if (up)
			{
				g.drawLine(cx, y + 3, cx, y + 11);
				g.drawLine(cx, y + 3, x + 3, y + 7);
				g.drawLine(cx, y + 3, x + 11, y + 7);
			}
			else
			{
				g.drawLine(cx, y + 3, cx, y + 11);
				g.drawLine(x + 3, y + 7, cx, y + 11);
				g.drawLine(x + 11, y + 7, cx, y + 11);
			}
		}
		finally
		{
			g.dispose();
		}
	}
}
