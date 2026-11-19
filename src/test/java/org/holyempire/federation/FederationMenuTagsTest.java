/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationMenuTagsTest
{
	private static final Color BLUE = new Color(152, 200, 248);
	private final List<String> chaos = Collections.singletonList("Holy Chaos");

	@Test
	public void addsBlueCCAfterExistingPlayerIndicatorsColor()
	{
		String old = "<col=00ff00>Player</col> (level-126)";
		assertEquals(old + " <col=98c8f8>[Holy Chaos]</col>",
					 FederationMenuTags.apply(old, chaos, BLUE, true));
	}

	@Test
	public void uncheckingRemovesExistingTag()
	{
		String old = "Player <col=98c8f8>[Holy Chaos]</col>";
		assertEquals("Player", FederationMenuTags.apply(old, chaos, BLUE, false));
	}

	@Test
	public void checkedReapplicationDoesNotDuplicate()
	{
		String old = "Player <col=98c8f8>[Holy Chaos]</col>";
		assertEquals(old, FederationMenuTags.apply(old, chaos, BLUE, true));
	}

	@Test
	public void userChangingColorRefreshesTag()
	{
		String old = "Player <col=98c8f8>[Holy Chaos]</col>";
		assertEquals("Player <col=ff0000>[Holy Chaos]</col>",
					 FederationMenuTags.apply(old, chaos, Color.RED, true));
	}

	@Test
	public void removesOldHEPrefix()
	{
		String old = "Player [HE: Holy Chaos]";
		assertEquals("Player <col=98c8f8>[Holy Chaos]</col>",
					 FederationMenuTags.apply(old, chaos, BLUE, true));
	}

	@Test
	public void removesAnIconInsideAnExistingTagBeforeRecoloringOrDisabling()
	{
		String current = "Player <col=98c8f8>[<img=90> Holy Chaos]</col>";
		assertEquals("Player <col=ff0000>[Holy Chaos]</col>",
					 FederationMenuTags.apply(current, chaos, Color.RED, true));
		assertEquals("Player", FederationMenuTags.apply(current, chaos, BLUE, false));
	}

	@Test
	public void multipleChaptersStayOneTag()
	{
		assertEquals("Player <col=98c8f8>[Holy Chaos, Holy Empire]</col>",
					 FederationMenuTags.apply("Player", Arrays.asList("Holy Chaos", "Holy Empire"),
											  BLUE, true));
	}

	@Test
	public void noChapterLeavesTargetAlone()
	{
		assertEquals("Player",
					 FederationMenuTags.apply("Player", Collections.emptyList(), BLUE, true));
	}
}
