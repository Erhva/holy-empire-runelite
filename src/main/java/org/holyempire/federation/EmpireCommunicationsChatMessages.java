/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.QueuedMessage;

/** Owner-approved text rendered only as a local game chatbox notification. */
final class EmpireCommunicationsChatMessages
{
	static final Color DEFAULT_BROADCAST = new Color(80, 170, 255);
	static final Color DEFAULT_EMERGENCY = new Color(216, 48, 48);

	private EmpireCommunicationsChatMessages()
	{
	}

	static Color colorFor(EmpireCommunicationsClient.Notice notice, Color broadcast,
						  Color emergency)
	{
		boolean urgent = "emergency".equals(notice.kind);
		Color selected = urgent ? emergency : broadcast;
		return selected != null ? selected : urgent ? DEFAULT_EMERGENCY : DEFAULT_BROADCAST;
	}

	static QueuedMessage build(EmpireCommunicationsClient.Notice notice, Color broadcast,
							   Color emergency)
	{
		String message =
			("emergency".equals(notice.kind) ? "[HOLY EMPIRE EMERGENCY] " : "[HOLY EMPIRE] ") +
			notice.fullMessage();
		// Client messages use RuneLite's chat markup, so escape all
		// user-authored characters before adding the colored span.
		String escaped = SwingTextSafety.escapeHtml(message);
		String formatted = new ChatMessageBuilder()
							   .append(colorFor(notice, broadcast, emergency), escaped)
							   .build();
		return QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.value(message)
			.runeLiteFormattedMessage(formatted)
			.build();
	}
}
