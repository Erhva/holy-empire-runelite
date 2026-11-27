/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Explicit, officer-authenticated, private attendance uploads. No public CDN writes. */
final class HolyEmpireAttendanceArchiveClient
{
	static final String PATH = "/v1/attendance/archive";
	static final int MAX_REQUEST = 400000;

	private HolyEmpireAttendanceArchiveClient()
	{
	}

	static JsonObject encode(HolyEmpireAttendanceTracker.Report report)
	{
		if (report == null || report.running)
		{
			throw new IllegalArgumentException("Finish attendance before uploading it");
		}
		return encodeAt(report, report.endedAt);
	}

	static JsonObject encodeCheckpoint(HolyEmpireAttendanceTracker.Report report, long capturedAt)
	{
		if (report == null || capturedAt < report.startedAt)
			throw new IllegalArgumentException("Invalid attendance checkpoint");
		long ended = report.running ? Math.max(report.startedAt, capturedAt) : report.endedAt;
		return encodeAt(report, ended);
	}

	private static JsonObject encodeAt(HolyEmpireAttendanceTracker.Report report, long endedAt)
	{
		JsonObject result = new JsonObject();
		result.addProperty("schema_version", 2);
		result.addProperty("report_id", report.reportId);
		result.addProperty("event_id", report.eventId);
		result.addProperty("scope", report.scope);
		result.addProperty("event_chapter", report.chapter);
		result.addProperty("title", report.title);
		result.addProperty("host", report.host);
		result.addProperty("started_at", report.startedAt / 1000);
		result.addProperty("ended_at", endedAt / 1000);
		result.addProperty("range_tiles", HolyEmpireAttendanceTracker.RADIUS_TILES);
		JsonArray entries = new JsonArray();
		for (HolyEmpireAttendanceTracker.Entry row : report.entries)
		{
			JsonObject data = new JsonObject();
			data.addProperty("rsn", row.name);
			data.addProperty("chapter", row.chapter);
			JsonArray memberships = new JsonArray();
			for (String chapter : row.chapters)
			{
				memberships.add(chapter);
			}
			data.add("chapters", memberships);
			data.addProperty("ticks", row.ticks);
			data.addProperty("first_seen", row.firstSeen / 1000);
			data.addProperty("last_seen", row.lastSeen / 1000);
			JsonArray spans = new JsonArray();
			for (long[] range : row.spans)
			{
				JsonArray span = new JsonArray();
				span.add(range[0]);
				span.add(range[1]);
				spans.add(span);
			}
			data.add("spans", spans);
			entries.add(data);
		}
		result.add("attendees", entries);
		return result;
	}

	static final class Confirmation
	{
		final String uuid;
		final int number;

		Confirmation(String uuid, int number)
		{
			this.uuid = uuid;
			this.number = number;
		}
	}

	static String save(HolyEmpireAttendanceTracker.Report report,
					   ChapterEventsClient.MemberSession session, OkHttpClient http)
		throws IOException
	{
		return saveWithNumber(report, session, http).uuid;
	}

	static Confirmation saveWithNumber(HolyEmpireAttendanceTracker.Report report,
									   ChapterEventsClient.MemberSession session, OkHttpClient http)
		throws IOException
	{
		return saveEncodedWithNumber(encode(report), session, http);
	}

	static Confirmation saveEncodedWithNumber(JsonObject body,
											  ChapterEventsClient.MemberSession session,
											  OkHttpClient http) throws IOException
	{
		if (body == null || session == null || !session.valid(session.chapter))
			throw new IOException("Verified Discord officer required to archive attendance");
		final String expectedId;
		try
		{
			expectedId = body.get("report_id").getAsString();
			if (!java.util.UUID.fromString(expectedId).toString().equals(expectedId))
				throw new IllegalArgumentException("UUID required");
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid local attendance report identifier", error);
		}
		String origin = ChapterEventsClient.origin();
		if (!ChapterEventsClient.acceptedChapterOrigin(origin))
		{
			throw new IOException("A trusted Holy Empire attendance server is required");
		}
		byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_REQUEST)
		{
			throw new IOException("Attendance report exceeds server limit");
		}
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(10, TimeUnit.SECONDS)
								   .build();
		Request request =
			new Request.Builder()
				.url(origin + PATH)
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + session.token)
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bytes))
				.build();
		try (Response response = bounded.newCall(request).execute())
		{
			if (response.code() != 200)
			{
				throw new IOException("Attendance archive returned HTTP " + response.code());
			}
			ResponseBody data = response.body();
			if (data == null || data.contentLength() > 4096)
			{
				throw new IOException("Attendance archive response unavailable");
			}
			byte[] raw;
			try (InputStream stream = data.byteStream())
			{
				raw = stream.readNBytes(4097);
			}
			if (raw.length > 4096)
			{
				throw new IOException("Invalid attendance archive response");
			}
			try
			{
				JsonObject reply = new JsonParser()
									   .parse(new String(raw, StandardCharsets.UTF_8))
									   .getAsJsonObject();
				if (!reply.get("saved").getAsBoolean() ||
					!expectedId.equals(reply.get("report_id").getAsString()))
				{
					throw new IOException("Attendance archive confirmation did not match");
				}
				int number = reply.get("number").getAsInt();
				if (number < 1 || number > Integer.MAX_VALUE)
					throw new IOException("Invalid short attendance report number");
				return new Confirmation(expectedId, number);
			}
			catch (RuntimeException error)
			{
				throw new IOException("Invalid attendance archive response", error);
			}
		}
	}
}
