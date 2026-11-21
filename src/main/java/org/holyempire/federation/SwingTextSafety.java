/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import javax.swing.JLabel;

/**
 * External text must never be interpreted as Swing HTML.
 */
final class SwingTextSafety
{
	private SwingTextSafety()
	{
	}

	static JLabel plainLabel(String text)
	{
		JLabel label = new JLabel();
		label.putClientProperty("html.disable", Boolean.TRUE);
		label.setText(text);
		return label;
	}

	static String escapeHtml(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace(String.valueOf((char)34), "&quot;")
			.replace("'", "&#39;");
	}
}
