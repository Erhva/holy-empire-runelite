/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordEventsClientTest
{
	private static final String GUILD = "123456789012345678";
	private static final String ID = "234567890123456789";
	private static final Instant NOW = Instant.parse("2026-11-21T20:00:00Z");

	private static JsonObject event()
	{
		JsonObject e = new JsonObject();
		e.addProperty("id", ID);
		e.addProperty("name", "<b>Boss Mass</b>");
		e.addProperty("description", "Barrows & prayer potions");
		e.addProperty("status", "scheduled");
		e.addProperty("location", "World 465");
		e.addProperty("start_time", NOW.plusSeconds(3600).toString());
		e.add("end_time", JsonNull.INSTANCE);
		e.addProperty("url", "https://discord.com/events/" + GUILD + "/" + ID);
		return e;
	}

	private static JsonObject doc()
	{
		JsonObject result = new JsonObject();
		result.addProperty("schema_version", 1);
		result.addProperty("kind", "holy-empire-discord-events");
		result.addProperty("guild_id", GUILD);
		result.addProperty("generated_at", NOW.getEpochSecond());
		JsonArray list = new JsonArray();
		list.add(event());
		result.add("events", list);
		return result;
	}

	private static void rejects(JsonObject json) throws Exception
	{
		try
		{
			DiscordEventsClient.parse(json.toString(), NOW);
			fail("Expected malicious or stale feed rejection");
		}
		catch (IOException expected)
		{
			// Fail closed on invalid server data.
		}
	}

	@Test public void parsesPublicEventAndAllowsOnlyFixedDiscordRSVPLink() throws Exception
	{
		DiscordEventsClient.Snapshot data = DiscordEventsClient.parse(doc().toString(), NOW);
		assertEquals(1, data.events.size());
		DiscordEventsClient.Event event = data.events.get(0);
		assertEquals("<b>Boss Mass</b>", event.name);
		assertEquals("World 465", event.location);
		assertEquals("scheduled", event.status);
		assertEquals(NOW.plusSeconds(3600), event.start);
		assertEquals("https://discord.com/events/" + GUILD + "/" + ID, event.url);
		assertTrue(DiscordEventsClient.URL.startsWith("https://roster.holyempireosrs.cc/"));
		try
		{
			data.events.add(event);
			fail("Event list must be immutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// UI may read events but cannot mutate the fetched snapshot.
		}
	}

	@Test public void empireInterestedCountUsesDiscordAggregateOnly() throws Exception
	{
		JsonObject known = doc();
		known.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("interested", 42);
		assertEquals(
			42, DiscordEventsClient.parse(known.toString(), NOW).events.get(0).interestedCount);
		assertEquals(
			-1, DiscordEventsClient.parse(doc().toString(), NOW).events.get(0).interestedCount);
		JsonObject negative = doc();
		negative.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("interested", -2);
		rejects(negative);
		JsonObject falseCount = doc();
		falseCount.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("interested",
																				 false);
		rejects(falseCount);
		JsonObject overLimit = doc();
		overLimit.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("interested",
																				10000001);
		rejects(overLimit);
	}

	@Test public void empireEventCoverIsDisplayedOnlyFromMatchingDiscordCdnPath() throws Exception
	{
		String hash = "abcdef0123456789abcdef0123456789";
		String url = "https://roster.holyempireosrs.cc/v2/event-images/" + ID + "/" + hash + ".png";
		JsonObject approved = doc();
		approved.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("image_url", url);
		DiscordEventsClient.Event item =
			DiscordEventsClient.parse(approved.toString(), NOW).events.get(0);
		assertEquals(url, item.imageUrl);
		assertTrue(EventImageLoader.approvedPublicUrl(item.imageUrl, ID));
		for (String invalid :
			 new String[] {"https://evil.example/guild-events/" + ID + "/" + hash + ".png",
						   "https://roster.holyempireosrs.cc/v2/event-images/999999999999999999/" +
							   hash + ".png",
						   url + "?redirect=1"})
		{
			JsonObject forged = doc();
			forged.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("image_url",
																				 invalid);
			rejects(forged);
		}
		assertFalse(EventImageLoader.approvedPublicUrl(
			"https://cdn.discordapp.com/guild-events/" + ID + "/" + hash + ".png", ID));
	}

	@Test public void rejectsPhishingLinksDuplicateIDsAndWrongServer() throws Exception
	{
		JsonObject phishing = doc();
		phishing.getAsJsonArray("events").get(0).getAsJsonObject().addProperty(
			"url", "https://discord.com.evil.example/events/" + GUILD + "/" + ID);
		rejects(phishing);
		JsonObject anotherServer = doc();
		anotherServer.getAsJsonArray("events").get(0).getAsJsonObject().addProperty(
			"url", "https://discord.com/events/999999999999999999/" + ID);
		rejects(anotherServer);
		JsonObject duplicate = doc();
		duplicate.getAsJsonArray("events").add(event());
		rejects(duplicate);
	}

	@Test public void testCalendarRequiresExactlyTheDiscordTestGuild() throws Exception
	{
		JsonObject wrongGuild = doc();
		try
		{
			DiscordEventsClient.parse(wrongGuild.toString(), NOW, "293070715706671104");
			fail("Developer calendar accepted an unrelated Discord guild");
		}
		catch (IOException expected)
		{
			// Strong environment pinning blocks wrong-server Test events.
		}
		assertEquals(1, DiscordEventsClient.parse(wrongGuild.toString(), NOW, GUILD).events.size());
	}

	@Test public void rejectsInvalidDatesStaleSnapshotsAndOversizedText() throws Exception
	{
		JsonObject stale = doc();
		stale.addProperty("generated_at", NOW.minusSeconds(86401).getEpochSecond());
		rejects(stale);
		JsonObject oversized = doc();
		oversized.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("description",
																				"x".repeat(1201));
		rejects(oversized);
		JsonObject badStart = doc();
		badStart.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("start_time",
																			   "not-a-date");
		rejects(badStart);
	}

	@Test public void supportsEmptyCalendarAndLiveEvent() throws Exception
	{
		JsonObject empty = doc();
		empty.add("events", new JsonArray());
		assertTrue(DiscordEventsClient.parse(empty.toString(), NOW).events.isEmpty());
		JsonObject live = doc();
		live.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("status", "live");
		assertEquals("live", DiscordEventsClient.parse(live.toString(), NOW).events.get(0).status);
	}
}
