/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import org.junit.Test;
import static org.junit.Assert.*;

public class SwingTextSafetyTest
{
	@Test public void serverSuppliedHtmlCannotBeInterpretedAsLabelMarkup()
	{
		String malicious = "<html><b>Player &amp; Rank</b></html>";
		JLabel label = SwingTextSafety.plainLabel(malicious);
		assertEquals(malicious, label.getText());
		assertEquals(Boolean.TRUE, label.getClientProperty("html.disable"));
		assertNull(label.getClientProperty(BasicHTML.propertyKey));
	}

	@Test public void serverSuppliedMarkupRemainsDisabledWhenTextChanges()
	{
		JLabel label = SwingTextSafety.plainLabel("");
		label.setText("<html><font color=red>Untrusted</font></html>");
		assertNull(label.getClientProperty(BasicHTML.propertyKey));
	}

	@Test public void dynamicTextIsEscapedForIntentionallyFormattedStatusLabels()
	{
		assertEquals("&lt;html&gt;&amp;&quot;&#39;&lt;/html&gt;",
					 SwingTextSafety.escapeHtml("<html>&" + (char)34 + "'</html>"));
		assertEquals("", SwingTextSafety.escapeHtml(null));
		assertEquals("World 465", SwingTextSafety.escapeHtml("World 465"));
	}
}
