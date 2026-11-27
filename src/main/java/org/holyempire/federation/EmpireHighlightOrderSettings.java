/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;
import net.runelite.client.config.ConfigManager;

/**
 * RuneLite ConfigPanel only has predefined field types, so the plugin replaces
 * exclusively its own clearly labelled dummy String row with a seven-row
 * embedded Swing editor. We do not reflect into or alter RuneLite classes.
 *
 * A low-frequency EDT timer handles opening the settings, section expansion,
 * and RuneLite's rebuilds after profile changes. It never inspects other
 * plugins' config keys, cookies, game data or UI contents.
 */
final class EmpireHighlightOrderSettings
{
	static final String EDITOR_NAME = "holyEmpireHighlightOrderEditor";
	private static final String CONFIG_PANEL = "net.runelite.client.plugins.config.ConfigPanel";
	private final ConfigManager manager;
	private final Map<JPanel, Component> originals = new WeakHashMap<>();
	private Timer timer;
	private volatile boolean running;

	EmpireHighlightOrderSettings(ConfigManager manager)
	{
		this.manager = manager;
	}

	void start()
	{
		running = true;
		SwingUtilities.invokeLater(() -> {
			if (!running)
			{
				return;
			}
			timer = new Timer(700, e -> update());
			timer.setInitialDelay(0);
			timer.start();
		});
	}

	void stop()
	{
		running = false;
		SwingUtilities.invokeLater(() -> {
			if (timer != null)
			{
				timer.stop();
				timer = null;
			}
			// When the plugin is disabled, restore the stock config rows.
			for (Map.Entry<JPanel, Component> entry : originals.entrySet())
			{
				JPanel row = entry.getKey();
				if (row != null && row.getLayout() instanceof BorderLayout)
				{
					Component current =
						((BorderLayout)row.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
					if (current instanceof JComponent && EDITOR_NAME.equals(current.getName()))
					{
						row.remove(current);
						row.add(entry.getValue(), BorderLayout.SOUTH);
						row.revalidate();
						row.repaint();
					}
				}
			}
			originals.clear();
		});
	}

	private void update()
	{
		if (!running)
		{
			return;
		}
		for (Window window : Window.getWindows())
		{
			if (!window.isShowing())
			{
				continue;
			}
			Container panel = findConfigPanel(window);
			if (panel == null || !panel.isShowing() || !containsLabel(panel, "Empire Indicators"))
			{
				continue;
			}
			JPanel row = findPlaceholderRow(panel);
			if (row != null)
			{
				attach(row);
			}
		}
	}

	private void attach(JPanel row)
	{
		EmpireHighlightPriority current = EmpireHighlightPriority.selected(
			manager.getConfig(FederationConfig.class).empireHighlightPriority());
		Component previous = ((BorderLayout)row.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
		if (replacePlaceholder(row, current,
							   value
							   -> manager.setConfiguration("holyEmpireFederation",
														   "empireHighlightPriority", value)))
		{
			originals.put(row, previous);
		}
	}

	/** Exposed to tests, works on one native ConfigItem row only. */
	static boolean replacePlaceholder(JPanel row, EmpireHighlightPriority current,
									  java.util.function.Consumer<EmpireHighlightPriority> save)
	{
		if (row == null || !(row.getLayout() instanceof BorderLayout) || save == null)
		{
			return false;
		}
		BorderLayout layout = (BorderLayout)row.getLayout();
		Component title = layout.getLayoutComponent(BorderLayout.CENTER);
		Component south = layout.getLayoutComponent(BorderLayout.SOUTH);
		if (!(title instanceof JLabel) || !"Highlight Order".equals(((JLabel)title).getText()) ||
			!(south instanceof JTextComponent))
		{
			return false;
		}

		JPanel editor = EmpireHighlightOrderDialog.editor(current, save, true);
		row.remove(south);
		row.add(editor, BorderLayout.SOUTH);
		row.revalidate();
		row.repaint();
		return true;
	}

	static Container findConfigPanel(Component node)
	{
		if (node.getClass().getName().equals(CONFIG_PANEL) && node instanceof Container)
		{
			return (Container)node;
		}
		if (node instanceof Container)
		{
			for (Component child : ((Container)node).getComponents())
			{
				Container found = findConfigPanel(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	static JPanel findPlaceholderRow(Component node)
	{
		if (node instanceof JPanel)
		{
			JPanel row = (JPanel)node;
			if (row.getLayout() instanceof BorderLayout)
			{
				BorderLayout layout = (BorderLayout)row.getLayout();
				Component center = layout.getLayoutComponent(BorderLayout.CENTER);
				Component south = layout.getLayoutComponent(BorderLayout.SOUTH);
				if (center instanceof JLabel &&
					"Highlight Order".equals(((JLabel)center).getText()) &&
					south instanceof JTextComponent)
				{
					return row;
				}
			}
		}
		if (node instanceof Container)
		{
			for (Component child : ((Container)node).getComponents())
			{
				JPanel found = findPlaceholderRow(child);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	static boolean containsLabel(Component node, String name)
	{
		if (node instanceof JLabel && name.equals(((JLabel)node).getText()))
		{
			return true;
		}
		if (node instanceof Container)
		{
			for (Component child : ((Container)node).getComponents())
			{
				if (containsLabel(child, name))
				{
					return true;
				}
			}
		}
		return false;
	}
}
