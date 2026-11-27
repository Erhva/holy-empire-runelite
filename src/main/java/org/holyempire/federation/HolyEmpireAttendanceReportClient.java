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
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Private, verified report history; no public R2 or roster endpoint. */
final class HolyEmpireAttendanceReportClient
{
	private HolyEmpireAttendanceReportClient()
	{
	}

	static final String PATH = "/v1/attendance/report";
	static final int MAX_BYTES = 400000;

	static final class Saved
	{
		final int number;
		final String text;
		final HolyEmpireAttendanceCumulative.Previous previous;

		Saved(int number, String text, JsonObject report)
		{
			this.number = number;
			this.text = text;
			this.previous = HolyEmpireAttendanceCumulative.fromServer(report, number);
		}
	}

	static Saved fetch(String eventId, ChapterEventsClient.MemberSession member, OkHttpClient http)
		throws IOException
	{
		if (member == null || !member.valid(member.chapter))
			throw new IOException("Discord authorization is refreshing. Try again shortly.");
		if (eventId == null ||
			!eventId.matches("(?:[0-9a-f]{32}|[0-9]{15,22}|manual:[0-9a-f-]{36})"))
			throw new IOException("Invalid attendance event reference");
		String origin = ChapterEventsClient.origin();
		if (!ChapterEventsClient.acceptedChapterOrigin(origin))
			throw new IOException("Trusted attendance archive required");
		JsonObject body = new JsonObject();
		body.addProperty("event_id", eventId);
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(12, TimeUnit.SECONDS)
								   .build();
		Request request =
			new Request.Builder()
				.url(origin + PATH)
				.header("Authorization", "Bearer " + member.token)
				.header("Accept", "application/json")
				.post(RequestBody.create(MediaType.parse("application/json"),
										 body.toString().getBytes(StandardCharsets.UTF_8)))
				.build();
		try (Response response = bounded.newCall(request).execute())
		{
			ResponseBody payload = response.body();
			if (payload == null || payload.contentLength() > MAX_BYTES)
				throw new IOException("Attendance report unavailable");
			byte[] raw;
			try (InputStream stream = payload.byteStream())
			{
				raw = stream.readNBytes(MAX_BYTES + 1);
			}
			if (raw.length > MAX_BYTES)
				throw new IOException("Attendance report too large");
			try
			{
				JsonObject doc = new JsonParser()
									 .parse(new String(raw, StandardCharsets.UTF_8))
									 .getAsJsonObject();
				if (response.code() != 200)
				{
					String error = doc.has("error") ? doc.get("error").getAsString()
													: "Archive HTTP " + response.code();
					throw new IOException(error.length() > 160 ? error.substring(0, 160) : error);
				}
				int number = doc.get("number").getAsInt();
				if (number < 1)
					throw new IOException("Invalid report number");
				JsonObject report = doc.getAsJsonObject("report");
				if (report == null || !eventId.equals(report.get("event_id").getAsString()))
					throw new IOException("Attendance event did not match");
				return new Saved(number, formatStored(report, number), report);
			}
			catch (IOException error)
			{
				throw error;
			}
			catch (RuntimeException error)
			{
				throw new IOException("Invalid private attendance report", error);
			}
		}
	}

	static java.util.Map<String, Integer> fetchIndex(ChapterEventsClient.MemberSession member,
													 OkHttpClient http) throws IOException
	{
		if (member == null || !member.valid(member.chapter))
			throw new IOException("Discord verification is refreshing");
		String origin = ChapterEventsClient.origin();
		if (!ChapterEventsClient.acceptedChapterOrigin(origin))
			throw new IOException("Trusted attendance history required");
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(10, TimeUnit.SECONDS)
								   .build();
		Request request = new Request.Builder()
							  .url(origin + "/v1/attendance/index")
							  .header("Authorization", "Bearer " + member.token)
							  .header("Accept", "application/json")
							  .post(RequestBody.create(MediaType.parse("application/json"),
													   "{}".getBytes(StandardCharsets.UTF_8)))
							  .build();
		try (Response response = bounded.newCall(request).execute())
		{
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > 24000)
				throw new IOException("Attendance index unavailable");
			byte[] raw;
			try (InputStream stream = body.byteStream())
			{
				raw = stream.readNBytes(24001);
			}
			if (raw.length > 24000)
				throw new IOException("Attendance index too large");
			try
			{
				JsonObject doc = new JsonParser()
									 .parse(new String(raw, StandardCharsets.UTF_8))
									 .getAsJsonObject();
				if (response.code() != 200)
					throw new IOException("Attendance index HTTP " + response.code());
				JsonArray items = doc.getAsJsonArray("reports");
				if (items == null || items.size() > 100)
					throw new IOException("Invalid attendance index");
				java.util.Map<String, Integer> result = new LinkedHashMap<>();
				for (JsonElement item : items)
				{
					JsonObject entry = item.getAsJsonObject();
					String id = entry.get("event_id").getAsString();
					int num = entry.get("number").getAsInt();
					if (!id.matches("(?:[0-9]{15,22}|[0-9a-f]{32})") || num < 1 ||
						result.putIfAbsent(id, num) != null)
						throw new IOException("Invalid attendance history number");
				}
				return result;
			}
			catch (IOException error)
			{
				throw error;
			}
			catch (RuntimeException error)
			{
				throw new IOException("Invalid private attendance index", error);
			}
		}
	}

	private static final int DEFAULT_MINIMUM_MINUTES = 10;
	private static final int NAME_COLUMN_WIDTH = 14;
	private static final String SECTION_RULE = "================================";
	private static final String CHAPTER_RULE = "------------------------------";

	private static final class AttendanceLine
	{
		final String name;
		final String chapter;
		final long ticks;

		AttendanceLine(String name, String chapter, long ticks)
		{
			this.name = name;
			this.chapter = chapter;
			this.ticks = ticks;
		}
	}

	private static boolean meetsMinimum(long ticks, int minimumMinutes)
	{
		return ticks * HolyEmpireAttendanceTracker.GAME_TICK_MILLIS >=
			(long) minimumMinutes * 60_000L;
	}

	private static void appendSection(StringBuilder text, List<AttendanceLine> rows,
		int minimumMinutes, boolean present)
	{
		text.append(present ? "PRESENT MEMBERS" : "BELOW MINIMUM");
		text.append(" (").append(String.format(java.util.Locale.ROOT,
			"%02d:%02d", minimumMinutes, 0)).append(" Minimum)\n");
		text.append(SECTION_RULE).append("\n");
		java.util.Map<String, java.util.List<AttendanceLine>> groups =
			new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (AttendanceLine row : rows)
		{
			if (meetsMinimum(row.ticks, minimumMinutes) == present)
				groups.computeIfAbsent(row.chapter, key -> new java.util.ArrayList<>()).add(row);
		}
		for (java.util.Map.Entry<String, java.util.List<AttendanceLine>> group : groups.entrySet())
		{
			java.util.List<AttendanceLine> members = group.getValue();
			members.sort(java.util.Comparator.comparing(row -> row.name,
				String.CASE_INSENSITIVE_ORDER));
			text.append("\n").append(group.getKey()).append(" (")
				.append(members.size()).append(")\n");
			text.append(CHAPTER_RULE).append("\n");
			text.append(String.format(java.util.Locale.ROOT,
				"%-" + NAME_COLUMN_WIDTH + "s| Time\n", "Name"));
			for (AttendanceLine row : members)
			{
				long seconds = row.ticks * HolyEmpireAttendanceTracker.GAME_TICK_MILLIS / 1000L;
				text.append(String.format(java.util.Locale.ROOT,
					"%-" + NAME_COLUMN_WIDTH + "s| %02d:%02d\n",
					row.name, seconds / 60, seconds % 60));
			}
		}
	}

	private static String formatReport(String title, String host,
		List<AttendanceLine> rows, int minimumMinutes)
	{
		if (minimumMinutes < 0 || minimumMinutes > 120)
			throw new IllegalArgumentException("Attendance minimum must be 0-120 minutes");
		int present = 0;
		for (AttendanceLine row : rows)
			if (meetsMinimum(row.ticks, minimumMinutes)) present++;
		StringBuilder text = new StringBuilder(title).append("\n")
			.append("Taken by: ").append(host).append("\n")
			.append("Total: ").append(rows.size()).append(" | Present: ")
			.append(present).append(" | Below: ").append(rows.size() - present)
			.append("\n\n");
		appendSection(text, rows, minimumMinutes, true);
		text.append("\n");
		appendSection(text, rows, minimumMinutes, false);
		return text.toString();
	}

	static String formatStored(JsonObject report, int number) throws IOException
	{
		try
		{
			String title = report.get("title").getAsString();
			String host = report.get("host").getAsString();
			JsonArray attendees = report.getAsJsonArray("attendees");
			if (attendees == null || attendees.size() > 2000 || title.length() > 100)
				throw new IOException("Invalid attendance report");
			java.util.List<AttendanceLine> rows = new java.util.ArrayList<>();
			for (JsonElement element : attendees)
			{
				JsonObject row = element.getAsJsonObject();
				String chapter = row.get("chapter").getAsString();
				String name = row.get("rsn").getAsString();
				long ticks = row.get("ticks").getAsLong();
				if (!name.matches("[a-z0-9 _-]{1,12}") || chapter.length() > 80 ||
					ticks < 0 || ticks > 144000)
					throw new IOException("Invalid attendance entry");
				rows.add(new AttendanceLine(name, chapter, ticks));
			}
			// Legacy archives do not retain a minimum. They use the
			// standard 10-minute reporting threshold.
			return formatReport(title, host, rows, DEFAULT_MINIMUM_MINUTES);
		}
		catch (IOException problem)
		{
			throw problem;
		}
		catch (RuntimeException problem)
		{
			throw new IOException("Malformed private report", problem);
		}
	}

	static String formatLocal(HolyEmpireAttendanceTracker.Report report, int number)
	{
		return formatLocal(report, number, DEFAULT_MINIMUM_MINUTES);
	}

	static String formatLocal(HolyEmpireAttendanceTracker.Report report, int number,
		int minimumMinutes)
	{
		if (report == null)
			return "";
		java.util.List<AttendanceLine> rows = new java.util.ArrayList<>();
		for (HolyEmpireAttendanceTracker.Entry entry : report.entries)
			rows.add(new AttendanceLine(entry.name, entry.chapter, entry.ticks));
		return formatReport(report.title, report.host, rows, minimumMinutes);
	}
}
