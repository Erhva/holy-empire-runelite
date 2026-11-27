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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmpireCommunicationsClientTest
{
	private static JsonObject notice(long id, String kind, long now)
	{
		JsonObject item = new JsonObject();
		item.addProperty("id", id);
		item.addProperty("kind", kind);
		item.addProperty("title", "Holy Empire");
		item.addProperty("body", "<script>alert('not real')</script>");
		item.addProperty("created_at", now);
		if ("emergency".equals(kind))
			item.addProperty("expires_at", now + 7200);
		else
			item.add("expires_at", JsonNull.INSTANCE);
		return item;
	}

	private static JsonObject feed(long now)
	{
		JsonObject doc = new JsonObject();
		doc.addProperty("schema_version", 1);
		doc.addProperty("kind", EmpireCommunicationsClient.KIND);
		doc.addProperty("generated_at", now);
		doc.addProperty("sequence", 3);
		JsonArray announcements = new JsonArray();
		announcements.add(notice(1, "announcement", now));
		doc.add("announcements", announcements);
		JsonArray broadcasts = new JsonArray();
		broadcasts.add(notice(2, "broadcast", now));
		broadcasts.add(notice(3, "emergency", now));
		doc.add("broadcasts", broadcasts);
		doc.add("emergency", notice(3, "emergency", now));
		return doc;
	}

	private static EmpireCommunicationsClient.Snapshot parse(JsonObject doc, long now)
		throws Exception
	{
		return EmpireCommunicationsClient.parse(doc.toString().getBytes(StandardCharsets.UTF_8),
												Instant.ofEpochSecond(now));
	}

	@Test
	public void parsesBroadcastAnnouncementsAndEmergencyWithoutHtmlRendering() throws Exception
	{
		long now = 1800000000L;
		EmpireCommunicationsClient.Snapshot result = parse(feed(now), now);
		assertEquals(3, result.sequence);
		assertEquals(1, result.announcements.size());
		assertEquals(2, result.broadcasts.size());
		assertEquals("emergency", result.emergency.kind);
		assertTrue(result.announcements.get(0).body.contains("<script>"));
		EmpireCommunicationsInbox inbox = new EmpireCommunicationsInbox();
		List<EmpireCommunicationsClient.Notice> first =
			inbox.newMessages(result, Instant.ofEpochSecond(now));
		assertEquals(2, first.size());
		assertEquals(2, first.get(0).id);
		assertEquals(3, first.get(1).id);
		assertTrue(inbox.newMessages(result, Instant.ofEpochSecond(now)).isEmpty());
	}

	@Test public void verifiedAdminCapabilitiesAreReadFromServerFeed() throws Exception
	{
		long now = 1800000000L;
		JsonObject doc = feed(now);
		assertTrue(parse(doc, now).capabilities.isEmpty());
		JsonArray allowed = new JsonArray();
		allowed.add("announcements.publish");
		allowed.add("bans.search.global");
		doc.add("capabilities", allowed);
		EmpireCommunicationsClient.Snapshot result = parse(doc, now);
		assertTrue(result.capabilities.contains("announcements.publish"));
		assertTrue(result.capabilities.contains("bans.search.global"));
		assertFalse(result.capabilities.contains("broadcasts.publish"));

		allowed.add("announcements.publish");
		try
		{
			parse(doc, now);
			fail("Duplicate capability must be rejected");
		}
		catch (java.io.IOException expected)
		{
		}
		doc.addProperty("capabilities", "owner");
		try
		{
			parse(doc, now);
			fail("Capability string must be rejected");
		}
		catch (java.io.IOException expected)
		{
		}
	}

	@Test public void currentEmergencyIsReplayedOnEveryNewLoginOnly()
	{
		long now = 1800000000L;
		EmpireCommunicationsClient.Notice warning = new EmpireCommunicationsClient.Notice(
			41, "emergency", "Alert", "Stay safe", now - 1200, now + 2400);
		EmpireCommunicationsClient.Snapshot feed = new EmpireCommunicationsClient.Snapshot(
			now, 41, java.util.List.of(), java.util.List.of(warning), warning);
		EmpireCommunicationsInbox inbox = new EmpireCommunicationsInbox();
		assertTrue("Old alerts are not delivered until login",
				   inbox.newMessages(feed, Instant.ofEpochSecond(now)).isEmpty());
		inbox.onLogin();
		assertEquals(41, inbox.newMessages(feed, Instant.ofEpochSecond(now)).get(0).id);
		assertTrue(inbox.newMessages(feed, Instant.ofEpochSecond(now)).isEmpty());
		inbox.onLogin();
		assertEquals("A fresh login should display the active emergency again", 1,
					 inbox.newMessages(feed, Instant.ofEpochSecond(now)).size());
		inbox.onLogin();
		EmpireCommunicationsClient.Snapshot gone = new EmpireCommunicationsClient.Snapshot(
			now, 42, java.util.List.of(), java.util.List.of(), null);
		assertTrue("Cancelled emergencies cannot replay",
				   inbox.newMessages(gone, Instant.ofEpochSecond(now)).isEmpty());
	}

	@Test public void rejectsInvalidSchemaAndReplayAndDoesNotDeliverStaleAlerts() throws Exception
	{
		long now = 1800000000L;
		JsonObject doc = feed(now);
		doc.addProperty("kind", "untrusted");
		try
		{
			parse(doc, now);
			fail("must reject");
		}
		catch (java.io.IOException expected)
		{
		}
		doc = feed(now);
		doc.getAsJsonArray("broadcasts").get(0).getAsJsonObject().addProperty("body", "\u0007");
		try
		{
			parse(doc, now);
			fail("must reject control text");
		}
		catch (java.io.IOException expected)
		{
		}
		doc = feed(now - 600);
		try
		{
			parse(doc, now);
			fail("stale snapshot");
		}
		catch (java.io.IOException expected)
		{
		}
		EmpireCommunicationsClient.Snapshot valid = parse(feed(now), now);
		EmpireCommunicationsInbox inbox = new EmpireCommunicationsInbox();
		assertTrue("Old broadcasts are not replayed in chat",
				   inbox.newMessages(valid, Instant.ofEpochSecond(now + 151)).isEmpty());
	}
}
