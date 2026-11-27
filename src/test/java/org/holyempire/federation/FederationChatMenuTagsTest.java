/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationChatMenuTagsTest
{
	private static final Color BLUE = FederationPalette.DEFAULT_DOT_COLOR;

	private static Map<String, List<String>> roster()
	{
		Map<String, List<String>> directory = new HashMap<>();
		directory.put("erhva", Collections.singletonList("Holy Chaos"));
		directory.put("holy host", Arrays.asList("Holy Empire", "Holy Guard"));
		directory.put("other", Collections.singletonList("World 465"));
		return directory;
	}

	@Test public void onlyDecoratesRealPlayerMenuActionsInChatWidgets()
	{
		for (int group :
			 new int[] {InterfaceID.CHATBOX, InterfaceID.PM_CHAT, InterfaceID.CHATCHANNEL_CURRENT})
		{
			assertTrue(FederationChatMenuTags.isChatPlayerMenu(MenuAction.CC_OP, (group << 16) | 1,
															   "Lookup"));
			assertTrue(FederationChatMenuTags.isChatPlayerMenu(MenuAction.CC_OP_LOW_PRIORITY,
															   (group << 16) | 10, "Lookup"));
		}
		assertFalse(FederationChatMenuTags.isChatPlayerMenu(MenuAction.CC_OP,
															InterfaceID.BANKMAIN << 16, "Message"));
		assertFalse(FederationChatMenuTags.isChatPlayerMenu(MenuAction.RUNELITE,
															InterfaceID.CHATBOX << 16, "Message"));
		assertFalse(FederationChatMenuTags.isChatPlayerMenu(MenuAction.CC_OP,
															InterfaceID.CHATBOX << 16, "Copy"));
		assertFalse(FederationChatMenuTags.isChatPlayerMenu(MenuAction.CC_OP,
															InterfaceID.CHATBOX << 16, "Cancel"));
	}

	@Test public void nativeSocialActionsMayIdentifyAnInformationalRow()
	{
		for (int group : new int[] {
			InterfaceID.CHATBOX, InterfaceID.PM_CHAT, InterfaceID.CHATCHANNEL_CURRENT})
		{
			for (String action : new String[] {"Add friend", "Remove friend",
				"Message", "Reply", "Add ignore", "Remove ignore", "Lookup"})
			{
				assertTrue(FederationChatMenuTags.isChatPlayerIdentityAction(
					MenuAction.CC_OP, (group << 16) | 1, action));
			}
		}
		assertFalse(FederationChatMenuTags.isChatPlayerIdentityAction(
			MenuAction.RUNELITE, InterfaceID.CHATBOX << 16, "Add friend"));
		assertFalse(FederationChatMenuTags.isChatPlayerIdentityAction(
			MenuAction.CC_OP, InterfaceID.BANKMAIN << 16, "Add friend"));
	}

	@Test public void neverMutatesNativeChatFriendIgnoreOrMessagingTargets()
	{
		for (int group :
			 new int[] {InterfaceID.CHATBOX, InterfaceID.PM_CHAT, InterfaceID.CHATCHANNEL_CURRENT})
		{
			for (String action :
				 new String[] {"Add friend", "Remove friend", "Add ignore", "Remove ignore",
							   "Message", "Reply", "Report", "Kick", "Ban"})
			{
				assertFalse("Native action target must remain an exact RSN: " + action,
							FederationChatMenuTags.isChatPlayerMenu(MenuAction.CC_OP,
																	(group << 16) | 1, action));
				assertFalse(FederationChatMenuTags.isChatPlayerMenu(MenuAction.CC_OP_LOW_PRIORITY,
																	(group << 16) | 1, action));
			}
			assertTrue(FederationChatMenuTags.isChatPlayerMenu(MenuAction.CC_OP_LOW_PRIORITY,
															   (group << 16) | 1, "Lookup"));
		}
	}

	@Test public void exactRosterMatchIgnoresOnlyNativeFormatting()
	{
		Map<String, List<String>> members = roster();
		assertEquals("erhva",
					 FederationChatMenuTags.rosterName("<col=ffffff>Erhva</col>", members));
		assertEquals("holy host", FederationChatMenuTags.rosterName(
									  "<img=4><col=ffffff>Holy\u00a0Host</col>", members));
		assertNull(FederationChatMenuTags.rosterName("<col=ffffff>Erhva alt</col>", members));
		assertNull(FederationChatMenuTags.rosterName("Add friend", members));
		assertNull(
			FederationChatMenuTags.rosterName("Erhva <col=98c8f8>[World 465]</col>", members));
	}

	@Test public void chapterTagRemainsIdempotentWhenMenusRebuild()
	{
		Map<String, List<String>> members = roster();
		String original = "<img=9><col=ffffff>Holy Host</col>";
		String member = FederationChatMenuTags.rosterName(original, members);
		assertEquals("holy host", member);
		String decorated = FederationMenuTags.apply(original, members.get(member), BLUE, true);
		assertEquals("<img=9><col=ffffff>Holy Host</col> "
						 + "<col=98c8f8>[Holy Empire, Holy Guard]</col>",
					 decorated);
		assertEquals("holy host", FederationChatMenuTags.rosterName(decorated, members));
		assertEquals(decorated,
					 FederationMenuTags.apply(decorated, members.get(member), BLUE, true));
		assertTrue("Existing native icon is still intact", decorated.startsWith("<img=9>"));
	}

	@Test public void sameChapterMenuVisibilityAndNativeIconRulesAreReused()
	{
		assertTrue(SameChapterVisibility.showMenuTag(false, true, true));
		assertTrue(SameChapterVisibility.showMenuTag(true, false, false));
		assertFalse(SameChapterVisibility.showRankIcon(true, true, true, true, true));
		assertFalse(SameChapterVisibility.showRankIcon(true, true, true, false, false));
	}
}
