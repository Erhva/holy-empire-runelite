/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Server-owned event attendance lease. No game input, no public CDN or local claims. */
final class HolyEmpireAttendanceSessionClient
{
	static final String PATH = "/v1/attendance/session";

	private HolyEmpireAttendanceSessionClient()
	{
	}

	static final class Rejected extends IOException
	{
		final int code;

		Rejected(int code, String message)
		{
			super(message);
			this.code = code;
		}
	}

	static final class Result
	{
		final String status;
		final String observer;
		final boolean ownSession;

		Result(String status, String observer, boolean ownSession)
		{
			this.status = status;
			this.observer = observer;
			this.ownSession = ownSession;
		}
	}

	static Result send(String action, String eventId, String scope, String chapter,
					   ChapterEventsClient.MemberSession member, OkHttpClient http)
		throws IOException
	{
		if (member == null || !member.valid(member.chapter))
			throw new Rejected(403, "Discord verification is being refreshed. "
										+ "Try again after the automatic session update.");
		if (!("start".equals(action) || "check".equals(action) || "heartbeat".equals(action) ||
			  "finish".equals(action)) ||
			!("chapter".equals(scope) || "empire".equals(scope)) || chapter == null ||
			chapter.length() > 80 || eventId == null ||
			!eventId.matches("(?:[0-9a-f]{32}|[0-9]{15,22})"))
			throw new IOException("Invalid event attendance session request");
		String origin = ChapterEventsClient.origin();
		if (!ChapterEventsClient.acceptedChapterOrigin(origin))
			throw new IOException("Trusted Holy Empire event service required");
		JsonObject payload = new JsonObject();
		payload.addProperty("action", action);
		payload.addProperty("event_id", eventId);
		payload.addProperty("scope", scope);
		payload.addProperty("chapter", chapter);
		byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(10, TimeUnit.SECONDS)
								   .build();
		Request request = new Request.Builder()
							  .url(origin + PATH)
							  .header("Accept", "application/json")
							  .header("Authorization", "Bearer " + member.token)
							  .post(RequestBody.create(MediaType.parse("application/json"), bytes))
							  .build();
		try (Response response = bounded.newCall(request).execute())
		{
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > 4096)
				throw new IOException("Invalid event attendance response");
			byte[] raw;
			try (InputStream in = body.byteStream())
			{
				raw = in.readNBytes(4097);
			}
			if (raw.length > 4096)
				throw new IOException("Event attendance response too large");
			try
			{
				JsonObject result = new JsonParser()
										.parse(new String(raw, StandardCharsets.UTF_8))
										.getAsJsonObject();
				if (response.code() != 200)
				{
					String explanation =
						result.has("error")
							? result.get("error").getAsString()
							: "Event attendance service returned HTTP " + response.code();
					if (explanation.length() > 240)
						explanation = explanation.substring(0, 240);
					throw new Rejected(response.code(), explanation);
				}
				String status = result.get("status").getAsString();
				String observer =
					result.has("observer") ? result.get("observer").getAsString() : "";
				boolean own = result.has("own_session") && result.get("own_session").getAsBoolean();
				if (!("started".equals(status) || "active".equals(status) ||
					  "finished".equals(status) || "idle".equals(status)) ||
					observer.length() > 64)
					throw new IOException("Invalid attendance server confirmation");
				return new Result(status, observer, own);
			}
			catch (Rejected rejected)
			{
				throw rejected;
			}
			catch (RuntimeException error)
			{
				throw new IOException("Invalid event attendance server response", error);
			}
		}
	}
}
