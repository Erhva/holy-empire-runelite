/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 */
package org.holyempire.federation;

import java.awt.Color;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.QueuedMessage;

/** Default chat-only event notifications, independent of focus or tray settings. */
final class EmpireEventChatMessages
{
	private static final Color REMINDER_RED = new Color(0xff0000);
	private static final Color START_GREEN = new Color(0x00cc00);

	private EmpireEventChatMessages()
	{
	}

	static QueuedMessage build(EmpireEventReminders.Alert alert)
	{
		String colored = new ChatMessageBuilder()
							 .append(alert.starting ? START_GREEN : REMINDER_RED, alert.text)
							 .build();
		return QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.value(alert.text)
			.runeLiteFormattedMessage(colored)
			.build();
	}
}
