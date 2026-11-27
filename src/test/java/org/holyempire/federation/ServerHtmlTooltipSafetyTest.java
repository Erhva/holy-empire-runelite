/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServerHtmlTooltipSafetyTest
{
	@Test
	public void serverTextCannotSpoofAnAlreadyFormattedTooltip()
	{
		String external = "<html><table width='280' border='1'>" +
			"<tr><td><b>Untrusted</b></td></tr></table></html>";
		String safe = SwingTextSafety.boxedTooltip(external);
		assertTrue(safe.startsWith("<html><table width='280'"));
		assertFalse("Never trust a server-supplied boxed HTML prefix",
			safe.contains("<b>Untrusted</b>"));
	}
}
