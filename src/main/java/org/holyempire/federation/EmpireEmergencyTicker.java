/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.Timer;

/** Non-intrusive emergency marquee above all Holy Empire content pages. */
final class EmpireEmergencyTicker extends JComponent
{
	private String warning = "";
	private int offset;
	private long expiresAt;
	private final Timer timer;

	EmpireEmergencyTicker()
	{
		setName("holyEmpireEmergencyTicker");
		setOpaque(true);
		setBackground(new Color(63, 29, 36));
		setForeground(new Color(255, 205, 127));
		setFont(new Font("SansSerif", Font.BOLD, 12));
		setBorder(BorderFactory.createLineBorder(HolyEmpireTheme.CRIMSON, 2));
		setPreferredSize(new Dimension(240, 33));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 33));
		setVisible(false);
		timer = new Timer(45, e -> {
			if (!isVisible() || warning.isEmpty())
			{
				return;
			}
			if (expiresAt > 0 && System.currentTimeMillis() / 1000 >= expiresAt)
			{
				setWarning(null);
				return;
			}
			// Seamlessly wrap the repeating text, never reset from a blank screen.
			int cycle = Math.max(1, getFontMetrics(getFont()).stringWidth(warning) + 32);
			offset = (offset + 2) % cycle;
			repaint();
		});
		timer.setRepeats(true);
	}

	void setWarning(EmpireCommunicationsClient.Notice notice)
	{
		if (notice == null || !"emergency".equals(notice.kind) ||
			notice.expiresAt <= System.currentTimeMillis() / 1000)
		{
			warning = "";
			expiresAt = 0;
			setToolTipText(null);
			timer.stop();
			setVisible(false);
			repaint();
			return;
		}
		String text = "⚠ EMERGENCY · " + notice.title + " · " + notice.body + "    ";
		if (!text.equals(warning))
		{
			warning = text;
			offset = 0;
		}
		expiresAt = notice.expiresAt;
		setToolTipText(text);
		if (getAccessibleContext() != null)
			getAccessibleContext().setAccessibleDescription(text);
		setVisible(true);
		if (!timer.isRunning())
		{
			timer.start();
		}
		repaint();
	}

	String currentWarning()
	{
		return warning;
	}

	void stopTicker()
	{
		timer.stop();
	}

	@Override protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		if (warning.isEmpty())
			return;
		Graphics2D g = (Graphics2D)graphics.create();
		try
		{
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setFont(getFont());
			g.setColor(getForeground());
			FontMetrics metrics = g.getFontMetrics();
			int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
			int cycle = Math.max(1, metrics.stringWidth(warning) + 32);
			// The message copies share one periodic coordinate system. When
			// offset wraps, the pixels move just as they did on every frame.
			for (int x = -(offset % cycle); x < getWidth(); x += cycle)
			{
				g.drawString(warning, x, baseline);
			}
			g.setColor(HolyEmpireTheme.CRIMSON);
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		}
		finally
		{
			g.dispose();
		}
	}
}
