/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Small animated card showing a single Empire achievement at a time.
 *
 * A shuffled, no-replacement queue gives every recent achievement an equal
 * chance of appearing once per cycle. The next cycle cannot start with the
 * previous cycle's last item, unless there is only one item.
 *
 * Timers repaint only this card, never the containing RuneLite sidebar.
 */
final class WomAchievementRotator extends JPanel
{
	static final int HOLD_MILLIS = 7000;
	static final int FADE_MILLIS = 280;
	private static final int TICK_MILLIS = 40;

	private enum Phase
	{
		HOLD,
		FADE_OUT,
		FADE_IN
	}

	private final Random random;
	private final List<WomAchievementsClient.Achievement> achievements = new ArrayList<>();
	private final Deque<Integer> shuffled = new ArrayDeque<>();
	private final JLabel player = SwingTextSafety.plainLabel("");
	private final JTextPane description = new JTextPane();
	private final JLabel chapter = SwingTextSafety.plainLabel("");
	private final JLabel footer = SwingTextSafety.plainLabel("Last 24 Hours");
	private final Timer timer;

	private int currentIndex = -1;
	private boolean loaded;
	private long lastAgeAuditMillis;
	private float opacity = 1f;
	private long phaseStartNanos;
	private long unmountedAtNanos;
	private Phase phase = Phase.HOLD;

	WomAchievementRotator()
	{
		this(new Random());
	}

	WomAchievementRotator(Random random)
	{
		this.random = Objects.requireNonNull(random);
		setName("holyEmpireWomAchievementRotator");
		setOpaque(true);
		setBackground(HolyEmpireTheme.PANEL);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(HolyEmpireTheme.GOLD_EDGE),
			BorderFactory.createEmptyBorder(7, 9, 6, 9)));
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setPreferredSize(new Dimension(240, 128));
		setMinimumSize(new Dimension(160, 128));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 128));

		player.setName("holyEmpireWomAchievementPlayer");
		player.setHorizontalAlignment(SwingConstants.CENTER);
		player.setForeground(HolyEmpireTheme.GOLD);
		player.setFont(player.getFont().deriveFont(Font.BOLD, 18f));
		player.setAlignmentX(Component.CENTER_ALIGNMENT);
		player.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		description.setName("holyEmpireWomAchievementDescription");
		description.setFont(description.getFont().deriveFont(Font.BOLD, 18f));
		description.setForeground(HolyEmpireTheme.TEXT);
		description.setOpaque(false);
		description.setEditable(false);
		description.setFocusable(false);
		description.setAlignmentX(Component.CENTER_ALIGNMENT);
		description.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
		description.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
		// JTextArea cannot center its lines; JTextPane uses proper
		// paragraph alignment even for wrapped achievement descriptions.
		centerAchievementDescription();

		chapter.setName("holyEmpireWomAchievementChapter");
		chapter.setForeground(HolyEmpireTheme.BLUE);
		chapter.setFont(chapter.getFont().deriveFont(Font.BOLD, 16f));
		chapter.setHorizontalAlignment(SwingConstants.CENTER);
		chapter.setAlignmentX(Component.CENTER_ALIGNMENT);
		chapter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 23));

		footer.setName("holyEmpireWomAchievementFooter");
		footer.setForeground(HolyEmpireTheme.MUTED);
		footer.setFont(footer.getFont().deriveFont(Font.ITALIC, 12f));
		footer.setHorizontalAlignment(SwingConstants.CENTER);
		footer.setAlignmentX(Component.CENTER_ALIGNMENT);
		footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 17));

		add(player);
		add(Box.createVerticalStrut(2));
		add(description);
		add(Box.createVerticalStrut(2));
		add(chapter);
		add(Box.createVerticalStrut(2));
		add(footer);

		timer = new Timer(TICK_MILLIS, event -> tick(System.nanoTime()));
		timer.setCoalesce(true);
		timer.setRepeats(true);
		phaseStartNanos = System.nanoTime();
		showEmpty("Loading collective WOM achievements...");
	}

	/**
	 * Updating the cache preserves the current item when still eligible.
	 * Unchanged responses do not interrupt or restart the animation.
	 */
	void setAchievements(List<WomAchievementsClient.Achievement> recent)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> setAchievements(recent));
			return;
		}
		Objects.requireNonNull(recent);
		// Keep the rolling 24-hour window accurate even if the last server
		// response is cached or a network outage delays further updates.
		long wallSeconds = System.currentTimeMillis() / 1000;
		List<WomAchievementsClient.Achievement> eligible = new ArrayList<>();
		for (WomAchievementsClient.Achievement item : recent)
		{
			if (item != null && item.createdAt >= wallSeconds - 86400 &&
				item.createdAt <= wallSeconds + 300)
				eligible.add(item);
		}
		boolean firstLoad = !loaded;
		loaded = true;
		if (!firstLoad && achievements.equals(eligible))
			return;
		WomAchievementsClient.Achievement previous = current();
		achievements.clear();
		achievements.addAll(eligible);
		shuffled.clear();
		currentIndex = previous == null ? -1 : achievements.indexOf(previous);
		if (achievements.isEmpty())
		{
			currentIndex = -1;
			showEmpty("No recently tracked WOM achievements.");
		}
		else if (currentIndex >= 0)
		{
			// A modified feed starts a fresh permutation, but need not flash
			// the user or restart the currently displayed achievement.
			List<Integer> bag = new ArrayList<>();
			for (int i = 0; i < achievements.size(); i++)
				if (i != currentIndex)
					bag.add(i);
			Collections.shuffle(bag, random);
			shuffled.addAll(bag);
			showCurrent();
		}
		else
		{
			advance();
		}
		phase = Phase.HOLD;
		opacity = 1f;
		phaseStartNanos = System.nanoTime();
		if (unmountedAtNanos != 0)
			unmountedAtNanos = phaseStartNanos;
		adjustTimer();
		repaint();
	}

	void showUnavailable()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(this::showUnavailable);
			return;
		}
		if (achievements.isEmpty())
			showEmpty("Achievement updates temporarily unavailable.");
	}

	WomAchievementsClient.Achievement current()
	{
		return currentIndex >= 0 && currentIndex < achievements.size()
			? achievements.get(currentIndex)
			: null;
	}

	int totalAchievements()
	{
		return achievements.size();
	}

	/**
	 * Deterministic package-private advance for non-visual shuffle regression
	 * testing. The ordinary UI only invokes this during a fade transition.
	 */
	void advance()
	{
		if (achievements.isEmpty())
		{
			currentIndex = -1;
			showEmpty("No recently tracked WOM achievements.");
			return;
		}
		if (shuffled.isEmpty())
		{
			List<Integer> order = new ArrayList<>();
			for (int i = 0; i < achievements.size(); i++)
				order.add(i);
			Collections.shuffle(order, random);
			if (order.size() > 1 && order.get(0) == currentIndex)
				Collections.swap(order, 0, 1);
			shuffled.addAll(order);
		}
		currentIndex = shuffled.removeFirst();
		showCurrent();
	}

	private void centerAchievementDescription()
	{
		SimpleAttributeSet centered = new SimpleAttributeSet();
		StyleConstants.setAlignment(centered, StyleConstants.ALIGN_CENTER);
		description.setParagraphAttributes(centered, true);
		// JTextPane uses styled-document character attributes when painting,
		// not just the component foreground. Explicitly apply the light theme
		// color to the newly set text to avoid black-on-dark achievements.
		SimpleAttributeSet lightText = new SimpleAttributeSet();
		StyleConstants.setForeground(lightText, HolyEmpireTheme.TEXT);
		description.getStyledDocument().setCharacterAttributes(
			0, description.getDocument().getLength(), lightText, false);
	}

	private void showCurrent()
	{
		WomAchievementsClient.Achievement entry = current();
		if (entry == null)
			return;
		player.setText(entry.player);
		description.setText(entry.description);
		centerAchievementDescription();
		description.setCaretPosition(0);
		chapter.setText(entry.chapter);
		// The date filters the source feed, but never appears in this card.
		String accessible = entry.player + " achieved " + entry.description + " — " + entry.chapter;
		setToolTipText(accessible);
		getAccessibleContext().setAccessibleDescription(accessible);
	}

	private void showEmpty(String message)
	{
		player.setText("Holy Empire Achievements");
		description.setText(message);
		centerAchievementDescription();
		description.setCaretPosition(0);
		chapter.setText("");
		setToolTipText(message);
	}

	void tick(long now)
	{
		long wallMillis = System.currentTimeMillis();
		if (wallMillis - lastAgeAuditMillis >= 60_000)
		{
			lastAgeAuditMillis = wallMillis;
			long cutoff = wallMillis / 1000 - 86400;
			List<WomAchievementsClient.Achievement> eligible = new ArrayList<>();
			for (WomAchievementsClient.Achievement item : achievements)
				if (item.createdAt >= cutoff)
					eligible.add(item);
			if (eligible.size() != achievements.size())
			{
				setAchievements(eligible);
				return;
			}
		}
		if (achievements.size() < 2)
			return;
		long elapsed = Math.max(0, (now - phaseStartNanos) / 1_000_000L);
		switch (phase)
		{
		case HOLD:
			if (elapsed >= HOLD_MILLIS)
			{
				phase = Phase.FADE_OUT;
				phaseStartNanos = now;
			}
			break;
		case FADE_OUT:
			opacity = Math.max(0f, 1f - (float)elapsed / FADE_MILLIS);
			if (elapsed >= FADE_MILLIS)
			{
				advance();
				phase = Phase.FADE_IN;
				opacity = 0f;
				phaseStartNanos = now;
			}
			repaint();
			break;
		case FADE_IN:
			opacity = Math.min(1f, (float)elapsed / FADE_MILLIS);
			if (elapsed >= FADE_MILLIS)
			{
				phase = Phase.HOLD;
				opacity = 1f;
				phaseStartNanos = now;
			}
			repaint();
			break;
		default:
			break;
		}
	}

	private void adjustTimer()
	{
		if (isDisplayable() && achievements.size() > 1)
		{
			if (!timer.isRunning())
			{
				// Rebuilding the sidebar unmounts and re-adds this component.
				// Never restart the 7-second hold period on each remount.
				timer.start();
			}
		}
		else
			timer.stop();
	}

	@Override public void addNotify()
	{
		super.addNotify();
		if (unmountedAtNanos != 0)
		{
			phaseStartNanos += Math.max(0, System.nanoTime() - unmountedAtNanos);
			unmountedAtNanos = 0;
		}
		adjustTimer();
	}

	@Override public void removeNotify()
	{
		timer.stop();
		if (unmountedAtNanos == 0)
			unmountedAtNanos = System.nanoTime();
		super.removeNotify();
	}

	void stopAnimation()
	{
		timer.stop();
	}

	boolean animationRunning()
	{
		return timer.isRunning();
	}

	@Override protected void paintChildren(Graphics graphics)
	{
		if (opacity >= 1f)
		{
			super.paintChildren(graphics);
			return;
		}
		// Fade only the player, achievement and chapter. The time-window
		// footer labels the whole carousel, so it must remain fully opaque
		// during both transitions instead of flashing with each item.
		Graphics2D fade = (Graphics2D)graphics.create();
		try
		{
			fade.clipRect(0, 0, getWidth(), Math.max(0, footer.getY()));
			fade.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
			super.paintChildren(fade);
		}
		finally
		{
			fade.dispose();
		}
		if (footer.isVisible() && footer.getWidth() > 0 && footer.getHeight() > 0)
		{
			Graphics2D fixed = (Graphics2D)graphics.create(footer.getX(), footer.getY(),
														   footer.getWidth(), footer.getHeight());
			try
			{
				footer.paint(fixed);
			}
			finally
			{
				fixed.dispose();
			}
		}
	}
}
