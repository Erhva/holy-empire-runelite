/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChapterEventsClientTest
{
	private static final String CHAPTER = "Holy Chaos";
	private static final String ID = "abcdef0123456789abcdef0123456789";

	private JsonObject doc(String chapter)
	{
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", 1);
		root.addProperty("kind", "holy-empire-chapter-events");
		root.addProperty("chapter", chapter);
		JsonArray events = new JsonArray();
		JsonObject e = new JsonObject();
		e.addProperty("id", ID);
		e.addProperty("scope", "chapter");
		e.addProperty("chapter", chapter);
		e.addProperty("name", "Castle Wars Practice");
		e.addProperty("description", "Chapter-only training");
		e.addProperty("location", "World 465");
		e.addProperty("start_time", "2026-12-01T19:00:00Z");
		e.addProperty("end_time", "2026-12-01T20:00:00Z");
		e.addProperty("status", "scheduled");
		events.add(e);
		root.add("events", events);
		return root;
	}

	private void rejects(JsonObject doc) throws Exception
	{
		try
		{
			ChapterEventsClient.parseChapter(doc, CHAPTER);
			fail("Cross-chapter or invalid event must not load");
		}
		catch (IOException expected)
		{
			// A malformed or wrongly scoped response fails closed.
		}
	}

	@Test public void verifiesScopeWithoutTrustingClientSideChapterLabels() throws Exception
	{
		DiscordEventsClient.Event only =
			ChapterEventsClient.parseChapter(doc(CHAPTER), CHAPTER).get(0);
		assertEquals("chapter", only.scope);
		assertEquals(CHAPTER, only.chapter);
		assertEquals(ID, only.id);
		assertEquals("Castle Wars Practice", only.name);
		assertEquals("", only.url);
		assertEquals("scheduled", only.status);
		assertEquals(Instant.parse("2026-12-01T19:00:00Z"), only.start);

		rejects(doc("Holy Empire"));
		JsonObject forged = doc(CHAPTER);
		forged.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("chapter",
																			 "Holy Kingdom");
		rejects(forged);
		JsonObject empire = doc(CHAPTER);
		empire.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("scope", "empire");
		rejects(empire);
		JsonObject duplications = doc(CHAPTER);
		duplications.getAsJsonArray("events").add(duplications.getAsJsonArray("events").get(0));
		rejects(duplications);
	}

	@Test public void acceptsConfirmedChapterPostLinksAndRejectsExternalLinks() throws Exception
	{
		JsonObject linked = doc(CHAPTER);
		JsonObject e = linked.getAsJsonArray("events").get(0).getAsJsonObject();
		String url = "https://discord.com/channels/736564064834289774/"
					 + "1573933979667538010/167890123456789011";
		String img = "https://cdn.discordapp.com/attachments/123/456/image.png";
		e.addProperty("url", url);
		e.addProperty("image_url", img);
		DiscordEventsClient.Event item = ChapterEventsClient.parseChapter(linked, CHAPTER).get(0);
		assertEquals(url, item.url);
		assertEquals(img, item.imageUrl);
		e.addProperty("url", "https://discord.com.evil.example/channels/"
								 + "736564064834289774/1573933979667538010/167890123456789011");
		rejects(linked);
		e.addProperty("url", "https://discord.com/channels/"
								 + "293070715706671104/1573933979667538010/167890123456789011");
		rejects(linked);
		e.addProperty("url", url);
		e.addProperty("image_url", "https://untrusted.example/image.png");
		rejects(linked);
	}

	@Test public void chapterRsvpSummaryIsBoundedAndPersonalizedOnlyWhenProvided() throws Exception
	{
		JsonObject response = doc(CHAPTER);
		JsonObject entry = response.getAsJsonArray("events").get(0).getAsJsonObject();
		JsonObject rsvp = new JsonObject();
		rsvp.addProperty("going", 3);
		rsvp.addProperty("maybe", 2);
		rsvp.addProperty("declined", 1);
		rsvp.addProperty("yours", "maybe");
		entry.add("rsvp", rsvp);
		DiscordEventsClient.Event item = ChapterEventsClient.parseChapter(response, CHAPTER).get(0);
		assertEquals(3, item.rsvpGoing);
		assertEquals(2, item.rsvpMaybe);
		assertEquals(1, item.rsvpDeclined);
		assertEquals("maybe", item.myRsvp);
		rsvp.addProperty("yours", "pretending-to-be-going");
		rejects(response);
		rsvp.addProperty("yours", "going");
		rsvp.addProperty("going", -1);
		rejects(response);
		rsvp.addProperty("going", 100001);
		rejects(response);
		JsonObject old = doc(CHAPTER);
		DiscordEventsClient.Event compatible =
			ChapterEventsClient.parseChapter(old, CHAPTER).get(0);
		assertEquals(0, compatible.rsvpGoing);
		assertEquals("", compatible.myRsvp);
	}

	@Test public void productionChapterOriginIsPinned()
	{
		assertEquals(ChapterEventsClient.BASE, ChapterEventsClient.origin());
		assertEquals(ChapterEventsClient.BASE + "/v1/chapter-events/connect",
			ChapterEventsClient.connectUrl());
		assertTrue(ChapterEventsClient.acceptedChapterOrigin(ChapterEventsClient.BASE));
		assertFalse(ChapterEventsClient.acceptedChapterOrigin("http://127.0.0.1:8765"));
		assertFalse(ChapterEventsClient.acceptedChapterOrigin("https://untrusted.example"));
	}


	@Test public void memberSessionsExpireAndCannotTransferChapter()
	{
		long expires = Instant.now().getEpochSecond() + 3600;
		ChapterEventsClient.MemberSession session = new ChapterEventsClient.MemberSession(
			"validMemberCredential_abcdefghijklmnopqrstuvwxyz0123", CHAPTER, expires);
		assertTrue(session.valid(CHAPTER));
		assertFalse(session.valid("Holy Empire"));
		ChapterEventsClient.MemberSession old = new ChapterEventsClient.MemberSession(
			session.token, CHAPTER, Instant.now().getEpochSecond() + 10);
		assertFalse(old.valid(CHAPTER));
	}
}
