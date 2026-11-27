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

	/**
	 * Boxed, narrow Swing HTML tooltips for every Holy Empire control.
	 * Escape source text before rendering; never interpret Discord-supplied markup.
	 */
	static String boxedTooltip(String source)
	{
		if (source == null || source.isEmpty()) return null;
		String value = source;
		if (value.startsWith("<html>") && value.endsWith("</html>"))
		{
			value = value.substring(6, value.length() - 7)
				.replaceAll("(?i)<br\\s*/?>", "\\n")
				.replaceAll("<[^>]+>", "")
				.replace("&lt;", "<").replace("&gt;", ">")
				.replace("&quot;", String.valueOf((char)34)).replace("&#39;", String.valueOf((char)39))
				.replace("&amp;", "&");
		}
		StringBuilder body = new StringBuilder();
		String limited = value.length() > 1100 ? value.substring(0, 1100) + "…" : value;
		for (String line : limited.split("\\R", -1))
		{
			String remaining = line;
			while (remaining.length() > 43)
			{
				int cut = remaining.lastIndexOf(' ', 43);
				if (cut < 18) cut = 43;
				body.append(escapeHtml(remaining.substring(0, cut).stripTrailing())).append("<br>");
				remaining = remaining.substring(cut).stripLeading();
			}
			body.append(escapeHtml(remaining)).append("<br>");
		}
		return "<html><table width='280' border='1' cellspacing='0' cellpadding='6'>" +
			"<tr><td>" + body + "</td></tr></table></html>";
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
