/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

/** All enabled Player Indicators categories outrank Holy Empire except Highlight others. */
final class HolyEmpireOverheadPriority
{
	private HolyEmpireOverheadPriority()
	{
	}

	static boolean mayDraw(boolean partyHighlighted, boolean friendHighlighted,
						   boolean friendsChatHighlighted, boolean teamHighlighted,
						   boolean clanHighlighted)
	{
		return !(partyHighlighted || friendHighlighted || friendsChatHighlighted ||
				 teamHighlighted || clanHighlighted);
	}
}
