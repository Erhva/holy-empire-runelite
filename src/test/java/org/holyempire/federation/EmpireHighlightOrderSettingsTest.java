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
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.Test;
import net.runelite.client.config.ConfigItem;
import static org.junit.Assert.*;

public class EmpireHighlightOrderSettingsTest
{
	private static Component named(Component root, String name)
	{
		if (name.equals(root.getName()))
		{
			return root;
		}
		if (root instanceof Container)
		{
			for (Component child : ((Container)root).getComponents())
			{
				Component found = named(child, name);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static JPanel row(String title)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.add(new JLabel(title), BorderLayout.CENTER);
		row.add(new JTextField(), BorderLayout.SOUTH);
		return row;
	}

	@Test public void configUsesInlinePlaceholderNotToggleOrDropdown() throws Exception
	{
		ConfigItem launcher = FederationConfig.class.getMethod("highlightOrderEditor")
								  .getAnnotation(ConfigItem.class);
		assertNotNull(launcher);
		assertEquals("Highlight Order", launcher.name());
		assertEquals("highlightOrderEditor", launcher.keyName());
		assertEquals("minimapDotSection", launcher.section());
		assertFalse(launcher.hidden());
		assertEquals(5, launcher.position());
		assertEquals("", new FederationConfig() {}.highlightOrderEditor());
		assertEquals(String.class,
					 FederationConfig.class.getMethod("highlightOrderEditor").getReturnType());

		ConfigItem priority = FederationConfig.class.getMethod("empireHighlightPriority")
								  .getAnnotation(ConfigItem.class);
		assertTrue(priority.hidden());
		assertEquals("empireHighlightPriority", priority.keyName());
		try
		{
			FederationConfig.class.getMethod("editHighlightOrder");
			fail("Toggle was not removed");
		}
		catch (NoSuchMethodException expected)
		{
			// There is no checkbox or modal launch action.
		}
	}

	@Test public void replacesOnlyHolyEmpireRowWithSevenVisibleInlineRows()
	{
		JPanel other = row("Other setting");
		JPanel target = row("Highlight Order");
		JPanel section = new JPanel();
		section.add(new JLabel("Empire Indicators"));
		section.add(other);
		section.add(target);
		assertTrue(EmpireHighlightOrderSettings.containsLabel(section, "Empire Indicators"));
		assertSame(target, EmpireHighlightOrderSettings.findPlaceholderRow(section));

		List<EmpireHighlightPriority> saved = new ArrayList<>();
		assertTrue(EmpireHighlightOrderSettings.replacePlaceholder(
			target, EmpireHighlightPriority.BELOW_CLAN, saved::add));
		assertNull("Text field must be gone",
				   EmpireHighlightOrderSettings.findPlaceholderRow(section));
		Component inline =
			((BorderLayout)target.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
		assertEquals(EmpireHighlightOrderSettings.EDITOR_NAME, inline.getName());
		assertEquals("1.  Party", ((JLabel)named(inline, "holyEmpirePriorityLabel:0")).getText());
		assertEquals("6.  Holy Empire",
					 ((JLabel)named(inline, "holyEmpirePriorityLabel:5")).getText());
		assertEquals("7.  Others", ((JLabel)named(inline, "holyEmpirePriorityLabel:6")).getText());
		assertTrue("Editor must fit the stock 225px settings column",
				   inline.getPreferredSize().width <= 225);

		JButton up = (JButton)named(inline, "holyEmpirePriorityMoveUp");
		up.doClick();
		assertEquals(EmpireHighlightPriority.BELOW_TEAM, saved.get(0));
		assertEquals("5.  Holy Empire",
					 ((JLabel)named(inline, "holyEmpirePriorityLabel:4")).getText());
		assertEquals("6.  Clan", ((JLabel)named(inline, "holyEmpirePriorityLabel:5")).getText());
		assertFalse(EmpireHighlightOrderSettings.replacePlaceholder(
			target, EmpireHighlightPriority.BELOW_CLAN, saved::add));
		assertNotNull("Other settings must remain native",
					  ((BorderLayout)other.getLayout()).getLayoutComponent(BorderLayout.SOUTH));
	}

	@Test public void doesNotReplaceUnrelatedOrMalformedRows()
	{
		JPanel unrelated = row("Highlight Others");
		assertFalse(EmpireHighlightOrderSettings.replacePlaceholder(
			unrelated, EmpireHighlightPriority.BELOW_CLAN, p -> {}));
		JPanel missingField = new JPanel(new BorderLayout());
		missingField.add(new JLabel("Highlight Order"), BorderLayout.CENTER);
		assertFalse(EmpireHighlightOrderSettings.replacePlaceholder(
			missingField, EmpireHighlightPriority.BELOW_CLAN, p -> {}));
		assertNull(EmpireHighlightOrderSettings.findPlaceholderRow(unrelated));
	}
}
