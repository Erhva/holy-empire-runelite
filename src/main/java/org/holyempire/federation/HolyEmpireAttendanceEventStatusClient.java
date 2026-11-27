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
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Private aggregate status, never exposes other observers' identities. */
final class HolyEmpireAttendanceEventStatusClient
{
	static final class State
	{
		final String status;
		final int activeCount;
		final long scheduledEnd;
		final long endedAt;
		final String scope;
		final String chapter;
		final String title;
		final String location;
		final String url;
		final long startsAt;

		State(String status, int count, long end, long ended)
		{
			this(status, count, end, ended, "", "", "", "", "", 0L);
		}

		State(String status, int count, long end, long ended, String scope, String chapter,
			  String title, String location, String url, long starts)
		{
			this.status = status;
			this.activeCount = count;
			this.scheduledEnd = end;
			this.endedAt = ended;
			this.scope = scope;
			this.chapter = chapter;
			this.title = title;
			this.location = location;
			this.url = url;
			this.startsAt = starts;
		}

		boolean trackerActive()
		{
			return activeCount > 0;
		}
	}

	private HolyEmpireAttendanceEventStatusClient()
	{
	}

	static Map<String, State> fetch(List<String> ids, ChapterEventsClient.MemberSession member,
									OkHttpClient client) throws IOException
	{
		if (member == null || !member.valid(member.chapter))
			throw new IOException("Verified Discord session required");
		String origin = ChapterEventsClient.origin();
		if (!ChapterEventsClient.acceptedChapterOrigin(origin))
			throw new IOException("Trusted Holy Empire event service required");
		if (ids == null || ids.size() > 80)
			throw new IOException("Invalid event status request");
		JsonArray events = new JsonArray();
		for (String id : ids)
		{
			if (id == null || !id.matches("(?:[0-9]{15,22}|[0-9a-f]{32})"))
				throw new IOException("Invalid event ID");
			events.add(id);
		}
		JsonObject request = new JsonObject();
		request.add("events", events);
		OkHttpClient bounded = client.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(10, TimeUnit.SECONDS)
								   .build();
		Request post =
			new Request.Builder()
				.url(origin + "/v1/attendance/states")
				.header("Authorization", "Bearer " + member.token)
				.header("Accept", "application/json")
				.post(RequestBody.create(MediaType.parse("application/json"),
										 request.toString().getBytes(StandardCharsets.UTF_8)))
				.build();
		try (Response response = bounded.newCall(post).execute())
		{
			if (response.code() != 200)
				throw new IOException("Event status HTTP " + response.code());
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > 16000)
				throw new IOException("Invalid attendance status response");
			byte[] raw;
			try (InputStream stream = body.byteStream())
			{
				raw = stream.readNBytes(16001);
			}
			if (raw.length > 16000)
				throw new IOException("Attendance status too large");
			try
			{
				JsonObject root = new JsonParser()
									  .parse(new String(raw, StandardCharsets.UTF_8))
									  .getAsJsonObject();
				JsonObject result = root.getAsJsonObject("events");
				if (result == null || result.entrySet().size() > 80)
					throw new IOException("Invalid event status map");
				Map<String, State> map = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> item : result.entrySet())
				{
					String id = item.getKey();
					if (!id.matches("(?:[0-9]{15,22}|[0-9a-f]{32})") || !ids.contains(id))
						throw new IOException("Unexpected event status");
					JsonObject event = item.getValue().getAsJsonObject();
					String status = event.get("status").getAsString();
					int count = event.get("active_count").getAsInt();
					long scheduled = event.get("scheduled_end_at").getAsLong();
					long ended = event.get("ended_at").getAsLong();
					if (!("ongoing".equals(status) || "ended".equals(status) ||
						  "pending".equals(status) || "idle".equals(status)) ||
						count < 0 || count > 2000 || scheduled < 0 || ended < 0)
						throw new IOException("Invalid event state");
					String scope = event.has("scope") ? event.get("scope").getAsString() : "";
					String chapter = event.has("chapter") ? event.get("chapter").getAsString() : "";
					String title = event.has("name") ? event.get("name").getAsString() : "";
					String location =
						event.has("location") ? event.get("location").getAsString() : "";
					String url = event.has("url") ? event.get("url").getAsString() : "";
					long starts = event.has("starts_at") ? event.get("starts_at").getAsLong() : 0L;
					if (title.length() > 100 || chapter.length() > 80 || location.length() > 120 ||
						url.length() > 220 || starts < 0 ||
						!(scope.isEmpty() || "chapter".equals(scope) || "empire".equals(scope)))
						throw new IOException("Invalid event metadata");
					map.put(id, new State(status, count, scheduled, ended, scope, chapter, title,
										  location, url, starts));
				}
				return map;
			}
			catch (IOException error)
			{
				throw error;
			}
			catch (RuntimeException error)
			{
				throw new IOException("Malformed attendance status", error);
			}
		}
	}
}
