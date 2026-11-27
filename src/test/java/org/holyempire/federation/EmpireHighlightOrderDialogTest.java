/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmpireHighlightOrderDialogTest
{
	private static Component find(Component root, String name)
	{
		if (name.equals(root.getName()))
		{
			return root;
		}
		if (root instanceof Container)
		{
			for (Component child : ((Container)root).getComponents())
			{
				Component found = find(child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static String label(JPanel ui, int row)
	{
		JLabel item = (JLabel)find(ui, "holyEmpirePriorityLabel:" + row);
		assertNotNull(item);
		return item.getText();
	}

	@Test public void displaysNativeOrderAndSelectedEmpirePosition()
	{
		JPanel editor =
			EmpireHighlightOrderDialog.editor(EmpireHighlightPriority.BELOW_CLAN, p -> {});
		assertEquals("1.  Party", label(editor, 0));
		assertEquals("2.  Friends", label(editor, 1));
		assertEquals("3.  Friends Chat", label(editor, 2));
		assertEquals("4.  Team", label(editor, 3));
		assertEquals("5.  Clan", label(editor, 4));
		assertEquals("6.  Holy Empire", label(editor, 5));
		assertEquals("7.  Others", label(editor, 6));
		for (int i = 0; i < 7; i++)
		{
			JLabel rowLabel = (JLabel)find(editor, "holyEmpirePriorityLabel:" + i);
			assertEquals(i == 5 ? ColorScheme.BRAND_ORANGE : ColorScheme.TEXT_COLOR,
						 rowLabel.getForeground());
			javax.swing.JPanel row = (javax.swing.JPanel)find(editor, "holyEmpirePriorityRow:" + i);
			assertEquals("Every row uses RuneLite's stock settings background",
						 ColorScheme.DARK_GRAY_COLOR, row.getBackground());
			javax.swing.border.CompoundBorder border =
				(javax.swing.border.CompoundBorder)row.getBorder();
			javax.swing.border.MatteBorder line =
				(javax.swing.border.MatteBorder)border.getOutsideBorder();
			assertEquals(ColorScheme.MEDIUM_GRAY_COLOR, line.getMatteColor());
			assertEquals(0, line.getBorderInsets(row).left);
			assertEquals(1, line.getBorderInsets(row).bottom);
		}

		JLabel position = (JLabel)find(editor, "holyEmpirePriorityPosition");
		assertNotNull(position);
		assertTrue(position.getText().contains("6 of 7"));
	}

	@Test public void upAndDownSwapOnlyEmpireAndSaveAtEveryStep()
	{
		List<EmpireHighlightPriority> saved = new ArrayList<>();
		JPanel ui =
			EmpireHighlightOrderDialog.editor(EmpireHighlightPriority.BELOW_CLAN, saved::add);
		JButton up = (JButton)find(ui, "holyEmpirePriorityMoveUp");
		assertNotNull(up);
		up.doClick();
		assertEquals(EmpireHighlightPriority.BELOW_TEAM, saved.get(0));
		assertEquals("5.  Holy Empire", label(ui, 4));
		assertEquals("6.  Clan", label(ui, 5));
		assertEquals("7.  Others", label(ui, 6));
		JButton down = (JButton)find(ui, "holyEmpirePriorityMoveDown");
		assertNotNull(down);
		down.doClick();
		assertEquals(EmpireHighlightPriority.BELOW_CLAN, saved.get(1));
		assertEquals("5.  Clan", label(ui, 4));
		assertEquals("6.  Holy Empire", label(ui, 5));
	}

	@Test public void boundsDisableMovementAndPreserveNativeOrder()
	{
		JPanel first = EmpireHighlightOrderDialog.editor(EmpireHighlightPriority.ABOVE_ALL,
														 p -> fail("No top movement"));
		JButton up = (JButton)find(first, "holyEmpirePriorityMoveUp");
		assertFalse(up.isEnabled());
		assertEquals("1.  Holy Empire", label(first, 0));
		assertEquals("2.  Party", label(first, 1));

		JPanel last = EmpireHighlightOrderDialog.editor(EmpireHighlightPriority.BELOW_OTHERS,
														p -> fail("No bottom movement"));
		JButton down = (JButton)find(last, "holyEmpirePriorityMoveDown");
		assertFalse(down.isEnabled());
		assertEquals("6.  Others", label(last, 5));
		assertEquals("7.  Holy Empire", label(last, 6));
	}

	@Test public void configValueRemainsButClippedDropdownIsHidden() throws Exception
	{
		net.runelite.client.config.ConfigItem item =
			FederationConfig.class.getMethod("empireHighlightPriority")
				.getAnnotation(net.runelite.client.config.ConfigItem.class);
		assertTrue(item.hidden());
		assertEquals("empireHighlightPriority", item.keyName());
		assertEquals("Controlled by the ordered list directly below.", item.description());
	}
}
