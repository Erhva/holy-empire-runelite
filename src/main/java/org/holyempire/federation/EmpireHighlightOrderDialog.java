/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * The native ConfigPanel doesn't offer custom item types. This reorderable
 * editor is inserted directly into Holy Empire's own settings row, moving
 * only the Holy Empire row; other categories retain RuneLite's native order.
 */
final class EmpireHighlightOrderDialog
{
	private EmpireHighlightOrderDialog()
	{
	}

	/** UI is testable without opening a native window (headless CI). */
	static JPanel editor(EmpireHighlightPriority initial, Consumer<EmpireHighlightPriority> save)
	{
		return editor(initial, save, false);
	}

	static JPanel editor(EmpireHighlightPriority initial, Consumer<EmpireHighlightPriority> save,
						 boolean compact)
	{
		JPanel content = new JPanel(new BorderLayout(0, compact ? 4 : 12));
		content.setName("holyEmpireHighlightOrderEditor");
		// Match RuneLite's stock settings panel, rather than styling
		// the other controls to resemble an Empire-specific card.
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		content.setBorder(compact ? BorderFactory.createEmptyBorder(2, 0, 2, 0)
								  : BorderFactory.createEmptyBorder(8, 10, 8, 10));

		JLabel description = new JLabel(compact ? "Top = highest priority"
												: "Higher rows take priority over lower rows.");
		if (compact)
		{
			description.setFont(description.getFont().deriveFont(11f));
		}
		description.setForeground(ColorScheme.TEXT_COLOR);
		content.add(description, BorderLayout.NORTH);

		JPanel list = new JPanel(new GridLayout(7, 1, 0, compact ? 2 : 5));
		list.setOpaque(false);
		list.setName("holyEmpirePriorityOrderList");
		content.add(list, BorderLayout.CENTER);

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		footer.setOpaque(false);
		JLabel position = new JLabel();
		position.setName("holyEmpirePriorityPosition");
		position.setForeground(ColorScheme.TEXT_COLOR);
		position.setFont(position.getFont().deriveFont(Font.BOLD));
		footer.add(position);
		content.add(footer, BorderLayout.SOUTH);

		final EmpireHighlightPriority[] selected = {EmpireHighlightPriority.selected(initial)};
		final Runnable[] render = new Runnable[1];
		render[0] = () ->
		{
			list.removeAll();
			String[] order = selected[0].order();
			for (int i = 0; i < order.length; i++)
			{
				boolean empire = i == selected[0].ordinal();
				JPanel row = new JPanel(new BorderLayout(8, 0));
				row.setName("holyEmpirePriorityRow:" + i);
				row.setOpaque(true);
				// Stock RuneLite settings appearance: one dark background,
				// ordinary white labels and subtle gray separators.
				// Only Holy Empire is emphasized, using RuneLite's accent.
				row.setBackground(ColorScheme.DARK_GRAY_COLOR);
				row.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
					BorderFactory.createEmptyBorder(compact ? 3 : 6, compact ? 4 : 9,
													compact ? 3 : 6, compact ? 4 : 9)));
				row.setPreferredSize(new Dimension(compact ? 213 : 355, compact ? 30 : 39));
				JLabel name = new JLabel((i + 1) + ".  " + order[i]);
				name.setName("holyEmpirePriorityLabel:" + i);
				name.setForeground(empire ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR);
				if (empire)
				{
					name.setFont(name.getFont().deriveFont(Font.BOLD));
				}
				row.add(name, BorderLayout.CENTER);
				if (empire)
				{
					JPanel actions = new JPanel(new GridLayout(1, 2, compact ? 2 : 5, 0));
					actions.setOpaque(false);
					if (compact)
					{
						actions.setPreferredSize(new Dimension(50, 25));
					}
					JButton up = HolyEmpireMoveArrow.button(true);
					up.setName("holyEmpirePriorityMoveUp");
					up.setToolTipText("Move Holy Empire above the preceding highlight");
					up.setEnabled(selected[0] != EmpireHighlightPriority.ABOVE_ALL);
					up.addActionListener(e -> {
						selected[0] = selected[0].moveUp();
						save.accept(selected[0]);
						render[0].run();
					});
					JButton down = HolyEmpireMoveArrow.button(false);
					down.setName("holyEmpirePriorityMoveDown");
					down.setToolTipText("Move Holy Empire below the next highlight");
					down.setEnabled(selected[0] != EmpireHighlightPriority.BELOW_OTHERS);
					down.addActionListener(e -> {
						selected[0] = selected[0].moveDown();
						save.accept(selected[0]);
						render[0].run();
					});
					actions.add(up);
					actions.add(down);
					row.add(actions, BorderLayout.EAST);
				}
				list.add(row);
			}
			position.setText(compact ? "Holy Empire: " + selected[0].positionLabel()
									 : "Holy Empire: " + selected[0].positionLabel() +
										   "  •  Changes save immediately");
			list.revalidate();
			list.repaint();
		};
		render[0].run();
		return content;
	}
}
