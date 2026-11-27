/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChapterRememberedSessionTest
{
	private static JsonObject payload(boolean owner)
	{
		long now = Instant.now().getEpochSecond();
		JsonObject inner = new JsonObject();
		inner.addProperty("chapter", "Holy Chaos");
		inner.addProperty("token", "T".repeat(48));
		inner.addProperty("expires_at", now + 3500);
		inner.addProperty("admin", owner);
		inner.addProperty("owner", owner);
		inner.addProperty("remember_token", "R".repeat(64));
		inner.addProperty("remember_expires_at", now + 180L * 86400);
		JsonObject outer = new JsonObject();
		outer.add("session", inner);
		return outer;
	}

	private static ChapterEventsClient.OAuthResult read(JsonObject json, String chapter)
		throws Exception
	{
		Method parser = ChapterEventsClient.class.getDeclaredMethod("verifiedOAuthResult",
																	JsonObject.class, String.class);
		parser.setAccessible(true);
		try
		{
			return (ChapterEventsClient.OAuthResult)parser.invoke(null, json, chapter);
		}
		catch (InvocationTargetException ex)
		{
			if (ex.getCause() instanceof IOException)
				throw (IOException)ex.getCause();
			throw ex;
		}
	}

	@Test public void normalMemberCanPersistWithoutOwnerAccess() throws Exception
	{
		ChapterEventsClient.OAuthResult result = read(payload(false), "Holy Chaos");
		assertFalse(result.member.empireOwner);
		assertFalse(result.member.discordAdmin);
		assertEquals("R".repeat(64), result.remembered);
		assertTrue(result.rememberUntil > Instant.now().getEpochSecond());
	}

	@Test public void ownerRolePersistsOnlyWhenVerified() throws Exception
	{
		ChapterEventsClient.OAuthResult result = read(payload(true), "Holy Chaos");
		assertTrue(result.member.empireOwner);
		assertTrue(result.member.discordAdmin);
	}

	@Test public void chapterMismatchAndMalformedTokenAreRejected() throws Exception
	{
		try
		{
			read(payload(true), "Holy Empire");
			fail("Chapter mismatch accepted");
		}
		catch (IOException expected)
		{
		}
		JsonObject invalid = payload(false);
		invalid.getAsJsonObject("session").addProperty("remember_token", "invalid");
		try
		{
			read(invalid, "Holy Chaos");
			fail("Malformed persisted capability accepted");
		}
		catch (IOException expected)
		{
		}
	}
}
