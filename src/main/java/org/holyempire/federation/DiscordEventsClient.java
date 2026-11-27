/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Fetches an identity-free public snapshot from our roster host, NEVER from
 * Discord directly. Discord OAuth/bot credentials stay in the backend.
 * The request contains no RSN, account, membership or player state.
 */
final class DiscordEventsClient
{
	static final String URL = "https://roster.holyempireosrs.cc/v2/events.json";
	static final int MAX_BYTES = 262144;
	static final long MAX_AGE_SECONDS = 86400;

	static final class Event
	{
		final String scope;
		final String chapter;
		final String id;
		final String name;
		final String description;
		final String status;
		final String location;
		final Instant start;
		final Instant end;
		final String url;
		final String imageUrl;
		final int rsvpGoing;
		final int rsvpMaybe;
		final int rsvpDeclined;
		final String myRsvp;
		final int interestedCount;

		Event(String id, String name, String description, String status, String location,
			  Instant start, Instant end, String url)
		{
			this("empire", "", id, name, description, status, location, start, end, url, "");
		}

		Event(String scope, String chapter, String id, String name, String description,
			  String status, String location, Instant start, Instant end, String url)
		{
			this(scope, chapter, id, name, description, status, location, start, end, url, "");
		}

		Event(String scope, String chapter, String id, String name, String description,
			  String status, String location, Instant start, Instant end, String url,
			  String imageUrl)
		{
			this(scope, chapter, id, name, description, status, location, start, end, url, imageUrl,
				 0, 0, 0, "");
		}

		Event(String scope, String chapter, String id, String name, String description,
			  String status, String location, Instant start, Instant end, String url,
			  String imageUrl, int going, int maybe, int declined, String yours)
		{
			this(scope, chapter, id, name, description, status, location, start, end, url, imageUrl,
				 going, maybe, declined, yours, "chapter".equals(scope) ? going + maybe : -1);
		}

		Event(String scope, String chapter, String id, String name, String description,
			  String status, String location, Instant start, Instant end, String url,
			  String imageUrl, int going, int maybe, int declined, String yours,
			  int interestedCount)
		{
			this.scope = scope;
			this.chapter = chapter;
			this.id = id;
			this.name = name;
			this.description = description;
			this.status = status;
			this.location = location;
			this.start = start;
			this.end = end;
			this.url = url;
			this.imageUrl = imageUrl;
			this.rsvpGoing = going;
			this.rsvpMaybe = maybe;
			this.rsvpDeclined = declined;
			this.myRsvp = yours;
			this.interestedCount = interestedCount;
		}
	}

	/** Apply only the authenticated user's interest to a private view. */
	static Snapshot withPersonalEmpireInterest(Snapshot snapshot, Set<String> interested)
	{
		List<Event> decorated = new ArrayList<>();
		for (Event e : snapshot.events)
		{
			if ("empire".equals(e.scope))
			{
				decorated.add(new Event(e.scope, e.chapter, e.id, e.name,
					e.description, e.status, e.location, e.start, e.end, e.url,
					e.imageUrl, e.rsvpGoing, e.rsvpMaybe, e.rsvpDeclined,
					interested.contains(e.id) ? "going" : "", e.interestedCount));
			}
			else decorated.add(e);
		}
		return new Snapshot(snapshot.generatedAt, decorated);
	}

	/**
	 * A transient authenticated lookup failure must not turn a confirmed
	 * Interested RSVP into "not interested". Carry only confirmed positive
	 * statuses for matching Empire event IDs, not a new RSVP or public count.
	 * A successful authenticated lookup always replaces this stale view.
	 */
	static Snapshot retainConfirmedEmpireInterest(Snapshot fresh, Snapshot previous)
	{
		if (previous == null)
			return fresh;
		Set<String> confirmed = new HashSet<>();
		for (Event event : previous.events)
		{
			if ("empire".equals(event.scope) && "going".equals(event.myRsvp))
				confirmed.add(event.id);
		}
		if (confirmed.isEmpty())
			return fresh;
		List<Event> decorated = new ArrayList<>();
		for (Event event : fresh.events)
		{
			if ("empire".equals(event.scope) && confirmed.contains(event.id))
			{
				decorated.add(new Event(event.scope, event.chapter, event.id,
					event.name, event.description, event.status, event.location,
					event.start, event.end, event.url, event.imageUrl,
					event.rsvpGoing, event.rsvpMaybe, event.rsvpDeclined, "going",
					event.interestedCount));
			}
			else decorated.add(event);
		}
		return new Snapshot(fresh.generatedAt, decorated);
	}

	static final class Snapshot
	{
		final long generatedAt;
		final List<Event> events;

		Snapshot(long generatedAt, List<Event> events)
		{
			this.generatedAt = generatedAt;
			this.events = Collections.unmodifiableList(new ArrayList<>(events));
		}
	}

	private DiscordEventsClient()
	{
	}

	static Snapshot fetch(OkHttpClient http) throws IOException
	{
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(8, TimeUnit.SECONDS)
								   .build();
		Request.Builder builder = new Request.Builder()
									  .url(URL)
									  .get()
									  .header("Accept", "application/json")
									  .header("User-Agent", "HolyEmpire-RuneLite-Events/1.0");
		Request request = builder.build();
		try (Response response = bounded.newCall(request).execute())
		{
			if (response.code() != 200)
			{
				throw new IOException("Events server returned HTTP " + response.code());
			}
			String type = response.header("Content-Type", "");
			if (!type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
			{
				throw new IOException("Events response was not JSON");
			}
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > MAX_BYTES)
			{
				throw new IOException("Events feed exceeds limit");
			}
			byte[] payload;
			try (InputStream input = body.byteStream())
			{
				payload = input.readNBytes(MAX_BYTES + 1);
			}
			if (payload.length > MAX_BYTES)
			{
				throw new IOException("Events feed exceeds limit");
			}
			return parse(new String(payload, java.nio.charset.StandardCharsets.UTF_8),
						 Instant.now(), null);
		}
	}

	static Snapshot parse(String json, Instant now) throws IOException
	{
		return parse(json, now, null);
	}

	static Snapshot parse(String json, Instant now, String expectedGuild) throws IOException
	{
		if (json == null || json.length() > MAX_BYTES)
		{
			throw new IOException("Events document too large");
		}
		try
		{
			JsonObject doc = new JsonParser().parse(json).getAsJsonObject();
			if (doc.get("schema_version").getAsInt() != 1 ||
				!"holy-empire-discord-events".equals(doc.get("kind").getAsString()))
			{
				throw new IOException("Unsupported Discord events feed");
			}
			String guild = doc.get("guild_id").getAsString();
			if (!snowflake(guild) || (expectedGuild != null && !expectedGuild.equals(guild)))
			{
				throw new IOException("Discord events from unexpected server");
			}
			long generatedAt = doc.get("generated_at").getAsLong();
			if (generatedAt > now.getEpochSecond() + 300 ||
				generatedAt < now.getEpochSecond() - MAX_AGE_SECONDS)
			{
				throw new IOException("Discord events feed is stale");
			}
			JsonArray rawEvents = doc.getAsJsonArray("events");
			if (rawEvents == null || rawEvents.size() > 160)
			{
				throw new IOException("Invalid Discord events count");
			}
			List<Event> events = new ArrayList<>();
			Set<String> ids = new HashSet<>();
			for (JsonElement value : rawEvents)
			{
				JsonObject item = value.getAsJsonObject();
				String id = item.get("id").getAsString();
				String name = text(item, "name", 100);
				String description = text(item, "description", 1200);
				String location = text(item, "location", 120);
				String status = item.get("status").getAsString();
				String link = item.get("url").getAsString();
				if (!snowflake(id) || !ids.add(id) || name.isEmpty() ||
					(!"scheduled".equals(status) && !"live".equals(status) &&
					 !"completed".equals(status)) ||
					!("https://discord.com/events/" + guild + "/" + id).equals(link))
				{
					throw new IOException("Invalid Discord event record");
				}
				Instant start = Instant.parse(item.get("start_time").getAsString());
				Instant end = item.get("end_time").isJsonNull()
								  ? null
								  : Instant.parse(item.get("end_time").getAsString());
				if (start.isAfter(now.plusSeconds(366L * 86400)) ||
					start.isBefore(now.minusSeconds(366L * 86400)) ||
					(end != null && end.isBefore(now.minusSeconds(366L * 86400))))
				{
					throw new IOException("Invalid Discord event date");
				}
				String cover = "";
				if (item.has("image_url"))
				{
					if (!item.get("image_url").isJsonPrimitive() ||
						!item.get("image_url").getAsJsonPrimitive().isString())
						throw new IOException("Invalid Empire scheduled-event image");
					cover = item.get("image_url").getAsString();
					if (!EventImageLoader.approvedPublicUrl(cover, id))
					{
						throw new IOException("Unexpected Empire event cover image");
					}
				}
				int interested = -1;
				if (item.has("interested"))
				{
					JsonElement count = item.get("interested");
					if (!count.isJsonPrimitive() || !count.getAsJsonPrimitive().isNumber())
					{
						throw new IOException("Invalid Empire event interested count");
					}
					interested = count.getAsInt();
					if (interested < 0 || interested > 10000000)
					{
						throw new IOException("Invalid Empire event interested count");
					}
				}
				events.add(new Event("empire", "", id, name, description, status, location, start,
									 end, link, cover, 0, 0, 0, "", interested));
			}
			return new Snapshot(generatedAt, events);
		}
		catch (IOException error)
		{
			throw error;
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid Discord events feed", error);
		}
	}

	private static boolean snowflake(String id)
	{
		return id != null && id.matches("[0-9]{5,22}");
	}

	private static String text(JsonObject obj, String key, int limit) throws IOException
	{
		String value = obj.get(key).getAsString();
		if (value.length() > limit)
		{
			throw new IOException("Invalid Discord event text length");
		}
		return value;
	}
}
