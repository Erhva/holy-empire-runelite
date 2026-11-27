/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup("holyEmpireFederation")
public interface FederationConfig extends Config {
	@ConfigSection(name = "Empire Indicators",
				   description =
					   "Default for Holy Empire dots, overhead names, and right-click CC tags.",
				   position = 0)
	String minimapDotSection = "minimapDotSection";

	@ConfigItem(keyName = "showFederationDots", name = "Show Holy Empire dots",
				description = "Highlight Holy Empire members on the minimap.", position = 0,
				section = minimapDotSection)
	default boolean
	showFederationDots()
	{
		return true;
	}

	@ConfigItem(keyName = "showOverheadNames", name = "Show overhead names",
				description = "Show Holy Empire players overhead using the indicator color; "
							  + "honors the configurable Empire highlight priority.",
				position = 1, section = minimapDotSection)
	default boolean
	showOverheadNames()
	{
		return true;
	}

	@ConfigItem(keyName = "showClanMenu", name = "Show CC in right-click menu",
				description = "Append a colored CC tag to player right-click menus.", position = 2,
				section = minimapDotSection)
	default boolean
	showClanMenu()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showClanRankIcons", name = "Show chapter rank in menu",
		description = "Display chapter rank icons in right-click menus for "
					  + "members of other chapters. Same-chapter ranks have their own option.",
		position = 3, section = minimapDotSection)
	default boolean
	showClanRankIcons()
	{
		return true;
	}

	@ConfigItem(keyName = "memberColor", name = "Indicator color",
				description = "Default color unless shared chapter colors are enabled.",
				position = 4, section = minimapDotSection)
	default Color
	memberColor()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	// Keep the original saved priority key; only its native dropdown is hidden.
	@ConfigItem(keyName = "empireHighlightPriority", name = "Empire highlight priority",
				description = "Controlled by the ordered list directly below.", hidden = true,
				position = 6, section = minimapDotSection)
	default EmpireHighlightPriority
	empireHighlightPriority()
	{
		return EmpireHighlightPriority.BELOW_CLAN;
	}

	// The RuneLite settings API has no custom control type. This stable, normally
	// harmless string row is replaced in-place by our seven-row Swing editor
	// only while this plugin's ConfigPanel is visible. No separate button or popup.
	@ConfigItem(keyName = "highlightOrderEditor", name = "Highlight Order",
				description = "Move Holy Empire higher or lower among Player Indicators.",
				position = 5, section = minimapDotSection)
	default String
	highlightOrderEditor()
	{
		return "";
	}

	@ConfigItem(keyName = "markEmpireHouseHosts", name = "Highlight HE POH Hosts",
				description =
					"Changes text color and adds a box around hosts who are members of the Empire",
				position = 7, section = minimapDotSection)
	default boolean
	markEmpireHouseHosts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pinEmpireHouseHostsFirst", name = "Order HE POH Hosts at the Top",
		description = "May cause conflicts with other advertisement plugins. Disable if needed.",
		position = 8, section = minimapDotSection)
	default boolean
	pinEmpireHouseHostsFirst()
	{
		return true;
	}

	@ConfigSection(name = "Event Settings",
				   description = "Red chat reminders and green event-start messages by default. "
								 + "Customize to use RuneLite notification options.",
				   position = 3, closedByDefault = true)
	String eventNotificationSection = "eventNotificationSection";

	@ConfigItem(keyName = "eventReminderFourHours", name = "First event reminder",
				description =
					"Red chat message by default; customize to use RuneLite notification options.",
				position = 0, section = eventNotificationSection)
	default Notification
	eventReminderFourHours()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "eventReminderFourHoursMinutes", name = "First reminder minutes before",
				description = "How many minutes before the event to send the first reminder.",
				position = 1, section = eventNotificationSection)
	@Range(min = 1, max = 10080)
	@Units(Units.MINUTES)
	default int
	eventReminderFourHoursMinutes()
	{
		return 240;
	}

	@ConfigItem(keyName = "eventReminderOneHour", name = "Second event reminder",
				description =
					"Red chat message by default; customize to use RuneLite notification options.",
				position = 2, section = eventNotificationSection)
	default Notification
	eventReminderOneHour()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "eventReminderOneHourMinutes", name = "Second reminder minutes before",
				description = "How many minutes before the event to send the second reminder.",
				position = 3, section = eventNotificationSection)
	@Range(min = 1, max = 10080)
	@Units(Units.MINUTES)
	default int
	eventReminderOneHourMinutes()
	{
		return 60;
	}

	@ConfigItem(keyName = "eventReminderFifteenMinutes", name = "Third event reminder",
				description =
					"Red chat message by default; customize to use RuneLite notification options.",
				position = 4, section = eventNotificationSection)
	default Notification
	eventReminderFifteenMinutes()
	{
		return Notification.ON;
	}

	@ConfigItem(keyName = "eventReminderFifteenMinutesMinutes",
				name = "Third reminder minutes before",
				description = "How many minutes before the event to send the third reminder.",
				position = 5, section = eventNotificationSection)
	@Range(min = 1, max = 10080)
	@Units(Units.MINUTES)
	default int
	eventReminderFifteenMinutesMinutes()
	{
		return 15;
	}

	@ConfigItem(keyName = "eventStartingNotification", name = "Event starting",
				description = "Green chat message when an Empire or chapter event starts. "
							  + "Customize to use RuneLite notification options.",
				position = 6, section = eventNotificationSection)
	default Notification
	eventStartingNotification()
	{
		return Notification.ON;
	}

	@ConfigSection(
		name = "Empire Communications",
		description =
			"Customize how broadcasts and emergency announcements appear in the RuneScape chatbox.",
		position = 4, closedByDefault = true)
	String empireCommunicationsSection = "empireCommunicationsSection";

	@ConfigItem(keyName = "broadcastChatColor", name = "Broadcast text color",
				description = "Color of ordinary Holy Empire chatbox broadcasts (blue by default).",
				position = 0, section = empireCommunicationsSection)
	default Color
	broadcastChatColor()
	{
		return EmpireCommunicationsChatMessages.DEFAULT_BROADCAST;
	}

	@ConfigItem(
		keyName = "emergencyChatColor", name = "Emergency text color",
		description = "Color of emergency Holy Empire chatbox announcements (dark red by default).",
		position = 1, section = empireCommunicationsSection)
	default Color
	emergencyChatColor()
	{
		return EmpireCommunicationsChatMessages.DEFAULT_EMERGENCY;
	}

	@ConfigItem(keyName = "receiveEmpireBroadcasts", name = "Receive normal broadcasts",
		description = "Show Holy Empire normal and chapter-targeted broadcast messages in chat.",
		position = 2, section = empireCommunicationsSection)
	default boolean receiveEmpireBroadcasts() { return true; }

	@ConfigItem(keyName = "receiveEmergencyBroadcasts", name = "Receive emergency broadcasts",
		description = "Show emergency chatbox broadcasts and the emergency ticker.",
		position = 3, section = empireCommunicationsSection)
	default boolean receiveEmergencyBroadcasts() { return true; }

	@ConfigSection(name = "Attendance",
		description = "Preferences for event attendance tracking and archived reports.",
		position = 5, closedByDefault = true)
	String attendanceSettingsSection = "attendanceSettingsSection";

	@ConfigItem(keyName = "showAttendanceArchiveHelp",
		name = "Archive help",
		description = "Show Attendance Archive Instructions after the first saved report; toggle off and on to re-enable.",
		position = 0, section = attendanceSettingsSection)
	default boolean showAttendanceArchiveHelp() { return true; }

	@ConfigItem(
		keyName = "eventHistoryDays", name = "History length (days)",
		description =
			"Show finished Empire and chapter events from the previous number of days. Default 30.",
		position = 7, section = eventNotificationSection)
	@Range(min = 1, max = 365)
	@Units("days")
	default int
	eventHistoryDays()
	{
		return 30;
	}

	@ConfigSection(name = "Chapter Indicators",
				   description =
					   "Choose which indicators appear for members in the same chapter as you.",
				   position = 1, closedByDefault = true)
	String sameChapterSection = "sameChapterSection";

	@ConfigItem(keyName = "showSameChapterOverhead", name = "Show chapter overhead names",
				description = "Show overhead names for players who share your chapter. Other "
							  + "Player Indicators categories keep priority.",
				position = 0, section = sameChapterSection)
	default boolean
	showSameChapterOverhead()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSameChapterMenu", name = "Show chapter in right-click menu",
		description = "Show the chapter name in right-click menus for members of your own chapter.",
		position = 1, section = sameChapterSection)
	default boolean
	showSameChapterMenu()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSameChapterRankIcons", name = "Show rank in right-click menu",
		description =
			"Show Holy Empire rank icons for members of your chapter. "
			+ "Respects Player Indicators Show clan chat ranks and existing native rank icons.",
		position = 2, section = sameChapterSection)
	default boolean
	showSameChapterRankIcons()
	{
		return true;
	}

	@ConfigItem(keyName = "showSameChapterDots", name = "Show chapter minimap dots",
				description = "Show Empire minimap dots for members of your chapter.", position = 3,
				section = sameChapterSection)
	default boolean
	showSameChapterDots()
	{
		return false;
	}

	@ConfigSection(name = "Advanced: Chapter Colors",
				   description = "Chapter-specific colors and optional right-click tag names.",
				   position = 2, closedByDefault = true)
	String chapterDotsSection = "chapterDotsSection";

	@ConfigItem(
		keyName = "useChapterDotColors", name = "Custom colors",
		description =
			"Apply individual colors and tag names to chapter indicators and right-click menus.",
		position = 0, section = chapterDotsSection)
	default boolean
	useChapterDotColors()
	{
		return false;
	}

	@ConfigItem(keyName = "dotColorHolyEmpire", name = "Holy Empire",
				description = "Shared indicator color for Holy Empire.", position = 1,
				section = chapterDotsSection)
	default Color
	dotColorHolyEmpire()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolyEmpire", name = "Holy Empire tag",
				description = "Custom name in right-click tags; defaults to Holy Empire.",
				position = 2, section = chapterDotsSection)
	default String
	tagNameHolyEmpire()
	{
		return "Holy Empire";
	}

	@ConfigItem(keyName = "dotColorWorld465", name = "World 465",
				description = "Shared indicator color for World 465.", position = 3,
				section = chapterDotsSection)
	default Color
	dotColorWorld465()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameWorld465", name = "World 465 tag",
				description = "Custom name in right-click tags; defaults to World 465.",
				position = 4, section = chapterDotsSection)
	default String
	tagNameWorld465()
	{
		return "World 465";
	}

	@ConfigItem(keyName = "dotColorHouseOf465", name = "House of 465",
				description = "Shared indicator color for House of 465.", position = 5,
				section = chapterDotsSection)
	default Color
	dotColorHouseOf465()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHouseOf465", name = "House of 465 tag",
				description = "Custom name in right-click tags; defaults to House of 465.",
				position = 6, section = chapterDotsSection)
	default String
	tagNameHouseOf465()
	{
		return "House of 465";
	}

	@ConfigItem(keyName = "dotColorHolyGuard", name = "Holy Guard",
				description = "Shared indicator color for Holy Guard.", position = 7,
				section = chapterDotsSection)
	default Color
	dotColorHolyGuard()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolyGuard", name = "Holy Guard tag",
				description = "Custom name in right-click tags; defaults to Holy Guard.",
				position = 8, section = chapterDotsSection)
	default String
	tagNameHolyGuard()
	{
		return "Holy Guard";
	}

	@ConfigItem(keyName = "dotColorHolyKingdom", name = "Holy Kingdom",
				description = "Shared indicator color for Holy Kingdom.", position = 9,
				section = chapterDotsSection)
	default Color
	dotColorHolyKingdom()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolyKingdom", name = "Holy Kingdom tag",
				description = "Custom name in right-click tags; defaults to Holy Kingdom.",
				position = 10, section = chapterDotsSection)
	default String
	tagNameHolyKingdom()
	{
		return "Holy Kingdom";
	}

	@ConfigItem(keyName = "dotColorSacredOrder", name = "Sacred Order",
				description = "Shared indicator color for Sacred Order.", position = 11,
				section = chapterDotsSection)
	default Color
	dotColorSacredOrder()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameSacredOrder", name = "Sacred Order tag",
				description = "Custom name in right-click tags; defaults to Sacred Order.",
				position = 12, section = chapterDotsSection)
	default String
	tagNameSacredOrder()
	{
		return "Sacred Order";
	}

	@ConfigItem(keyName = "dotColorHolyHeart", name = "Holy Heart",
				description = "Shared indicator color for Holy Heart.", position = 13,
				section = chapterDotsSection)
	default Color
	dotColorHolyHeart()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolyHeart", name = "Holy Heart tag",
				description = "Custom name in right-click tags; defaults to Holy Heart.",
				position = 14, section = chapterDotsSection)
	default String
	tagNameHolyHeart()
	{
		return "Holy Heart";
	}

	@ConfigItem(keyName = "dotColorSixShooters", name = "Six Shooters",
				description = "Shared indicator color for Six Shooters.", position = 15,
				section = chapterDotsSection)
	default Color
	dotColorSixShooters()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameSixShooters", name = "Six Shooters tag",
				description = "Custom name in right-click tags; defaults to Six Shooters.",
				position = 16, section = chapterDotsSection)
	default String
	tagNameSixShooters()
	{
		return "Six Shooters";
	}

	@ConfigItem(keyName = "dotColorCadia", name = "Cadia",
				description = "Shared indicator color for Cadia.", position = 17,
				section = chapterDotsSection)
	default Color
	dotColorCadia()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameCadia", name = "Cadia tag",
				description = "Custom name in right-click tags; defaults to Cadia.", position = 18,
				section = chapterDotsSection)
	default String
	tagNameCadia()
	{
		return "Cadia";
	}

	@ConfigItem(keyName = "dotColorHolyCow", name = "Holy Cow",
				description = "Shared indicator color for Holy Cow.", position = 19,
				section = chapterDotsSection)
	default Color
	dotColorHolyCow()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolyCow", name = "Holy Cow tag",
				description = "Custom name in right-click tags; defaults to Holy Cow.",
				position = 20, section = chapterDotsSection)
	default String
	tagNameHolyCow()
	{
		return "Holy Cow";
	}

	@ConfigItem(keyName = "dotColorHolySoul", name = "Holy Soul",
				description = "Shared indicator color for Holy Soul.", position = 21,
				section = chapterDotsSection)
	default Color
	dotColorHolySoul()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolySoul", name = "Holy Soul tag",
				description = "Custom name in right-click tags; defaults to Holy Soul.",
				position = 22, section = chapterDotsSection)
	default String
	tagNameHolySoul()
	{
		return "Holy Soul";
	}

	@ConfigItem(keyName = "dotColorHolyCalf", name = "Holy Calf",
				description = "Shared indicator color for Holy Calf.", position = 23,
				section = chapterDotsSection)
	default Color
	dotColorHolyCalf()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolyCalf", name = "Holy Calf tag",
				description = "Custom name in right-click tags; defaults to Holy Calf.",
				position = 24, section = chapterDotsSection)
	default String
	tagNameHolyCalf()
	{
		return "Holy Calf";
	}

	@ConfigItem(keyName = "dotColorHolyLights", name = "Holy Lights",
				description = "Shared indicator color for Holy Lights.", position = 25,
				section = chapterDotsSection)
	default Color
	dotColorHolyLights()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolyLights", name = "Holy Lights tag",
				description = "Custom name in right-click tags; defaults to Holy Lights.",
				position = 26, section = chapterDotsSection)
	default String
	tagNameHolyLights()
	{
		return "Holy Lights";
	}

	@ConfigItem(keyName = "dotColorHolySmokes", name = "Holy Smokes",
				description = "Shared indicator color for Holy Smokes.", position = 27,
				section = chapterDotsSection)
	default Color
	dotColorHolySmokes()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolySmokes", name = "Holy Smokes tag",
				description = "Custom name in right-click tags; defaults to Holy Smokes.",
				position = 28, section = chapterDotsSection)
	default String
	tagNameHolySmokes()
	{
		return "Holy Smokes";
	}

	@ConfigItem(keyName = "dotColorSacredBlade", name = "Sacred Blade",
				description = "Shared indicator color for Sacred Blade.", position = 29,
				section = chapterDotsSection)
	default Color
	dotColorSacredBlade()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameSacredBlade", name = "Sacred Blade tag",
				description = "Custom name in right-click tags; defaults to Sacred Blade.",
				position = 30, section = chapterDotsSection)
	default String
	tagNameSacredBlade()
	{
		return "Sacred Blade";
	}

	@ConfigItem(keyName = "dotColorUltramarines", name = "Ultramarines",
				description = "Shared indicator color for Ultramarines.", position = 31,
				section = chapterDotsSection)
	default Color
	dotColorUltramarines()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameUltramarines", name = "Ultramarines tag",
				description = "Custom name in right-click tags; defaults to Ultramarines.",
				position = 32, section = chapterDotsSection)
	default String
	tagNameUltramarines()
	{
		return "Ultramarines";
	}

	@ConfigItem(keyName = "dotColorHolyChaos", name = "Holy Chaos",
				description = "Shared indicator color for Holy Chaos.", position = 33,
				section = chapterDotsSection)
	default Color
	dotColorHolyChaos()
	{
		return FederationPalette.DEFAULT_DOT_COLOR;
	}

	@ConfigItem(keyName = "tagNameHolyChaos", name = "Holy Chaos tag",
				description = "Custom name in right-click tags; defaults to Holy Chaos.",
				position = 34, section = chapterDotsSection)
	default String
	tagNameHolyChaos()
	{
		return "Holy Chaos";
	}


	@ConfigItem(keyName = "banSyncEndpoint", name = "Protected sync endpoint",
				description = "HTTPS URL of the privately authenticated central ban registry "
							  + "(not the public WOM roster URL).",
				position = 19, hidden = true)
	default String
	banSyncEndpoint()
	{
		return FederationBanUploadClient.OFFICIAL_ENDPOINT;
	}

	@ConfigItem(keyName = "banSyncToken", name = "Chapter upload credential",
				description = "Private chapter-scoped credential. Never share with other players.",
				position = 20, secret = true, hidden = true)
	default String
	banSyncToken()
	{
		return "";
	}
}
