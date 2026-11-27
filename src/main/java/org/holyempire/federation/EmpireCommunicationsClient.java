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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Public read-only Empire communications + Owner-only publication API. */
final class EmpireCommunicationsClient
{
	static final String KIND = "holy-empire-communications";
	private static final int MAX_BYTES = 64_000;
	private static final String URL_PATH = "/v1/empire-comms/feed";
	private static final String POST_PATH = "/v1/empire-comms/publish";
	private static final String MANAGE_PATH = "/v1/empire-comms/manage";

	static final class Notice
	{
		final long id;
		final String kind;
		final String title;
		final String body;
		final long createdAt;
		final long expiresAt;
		final String discordUrl;
		final long updatedAt;
		final String scope;
		final String targetChapter;
		final String postedBy;
		final boolean canManage;

		Notice(long id, String kind, String title, String body, long createdAt, long expiresAt)
		{
			this(id, kind, title, body, createdAt, expiresAt, "", createdAt);
		}

		Notice(long id, String kind, String title, String body, long createdAt, long expiresAt,
			   String discordUrl, long updatedAt)
		{
			this(id, kind, title, body, createdAt, expiresAt, discordUrl, updatedAt, "empire", "");
		}

		Notice(long id, String kind, String title, String body, long createdAt, long expiresAt,
			   String discordUrl, long updatedAt, String scope, String targetChapter)
		{
			this(id, kind, title, body, createdAt, expiresAt, discordUrl, updatedAt, scope,
				 targetChapter, "Discord Member", false);
		}

		Notice(long id, String kind, String title, String body, long createdAt, long expiresAt,
			   String discordUrl, long updatedAt, String scope, String targetChapter,
			   String postedBy, boolean canManage)
		{
			this.postedBy = postedBy;
			this.canManage = canManage;
			this.scope = scope;
			this.targetChapter = targetChapter;
			this.discordUrl = discordUrl;
			this.updatedAt = updatedAt;
			this.id = id;
			this.kind = kind;
			this.title = title;
			this.body = body;
			this.createdAt = createdAt;
			this.expiresAt = expiresAt;
		}

		String fullMessage()
		{
			String audience = "all_admins".equals(scope)		  ? "[All Admins] "
							  : "chapter_command".equals(scope) ? "[Chapter Command] "
																  : "";
			return audience + title + ": " + body;
		}
	}

	static final class Snapshot
	{
		final long generatedAt;
		final long sequence;
		final List<Notice> announcements;
		final List<Notice> broadcasts;
		final Notice emergency;
		final Set<String> capabilities;
		final String etag;

		Snapshot(long generatedAt, long sequence, List<Notice> announcements,
				 List<Notice> broadcasts, Notice emergency)
		{
			this(generatedAt, sequence, announcements, broadcasts, emergency,
				 Collections.emptySet());
		}

		Snapshot(long generatedAt, long sequence, List<Notice> announcements,
				 List<Notice> broadcasts, Notice emergency, Set<String> capabilities)
		{
			this.generatedAt = generatedAt;
			this.sequence = sequence;
			this.announcements = Collections.unmodifiableList(new ArrayList<>(announcements));
			this.broadcasts = Collections.unmodifiableList(new ArrayList<>(broadcasts));
			this.emergency = emergency;
			this.capabilities = Collections.unmodifiableSet(new HashSet<>(capabilities));
			this.etag = null;
		}

		Snapshot withEtag(String value)
		{
			return new Snapshot(this, value);
		}

		private Snapshot(Snapshot original, String value)
		{
			this.generatedAt = original.generatedAt;
			this.sequence = original.sequence;
			this.announcements = original.announcements;
			this.broadcasts = original.broadcasts;
			this.emergency = original.emergency;
			this.capabilities = original.capabilities;
			this.etag = value;
		}
	}

	private EmpireCommunicationsClient()
	{
	}

	private static String origin() throws IOException
	{
		String origin = ChapterEventsClient.origin();
		if (!ChapterEventsClient.acceptedChapterOrigin(origin))
		{
			throw new IOException("Untrusted communications origin");
		}
		return origin;
	}

	private static OkHttpClient bounded(OkHttpClient http)
	{
		return http.newBuilder()
			.followRedirects(false)
			.followSslRedirects(false)
			.connectTimeout(5, TimeUnit.SECONDS)
			.readTimeout(8, TimeUnit.SECONDS)
			.build();
	}

	static Snapshot fetch(OkHttpClient http) throws IOException
	{
		return fetch(http, null, null);
	}

	static Snapshot fetch(OkHttpClient http, ChapterEventsClient.MemberSession member,
						  String chapter) throws IOException
	{
		return fetch(http, member, chapter, null);
	}

	static Snapshot fetch(OkHttpClient http, ChapterEventsClient.MemberSession member,
						  String chapter, Snapshot prior) throws IOException
	{
		Request.Builder builder =
			new Request.Builder().url(origin() + URL_PATH).header("Accept", "application/json");
		if (member != null && chapter != null && member.valid(chapter))
		{
			builder.header("Authorization", "Bearer " + member.token);
		}
		if (prior != null && prior.etag != null)
		{
			builder.header("If-None-Match", prior.etag);
		}
		try (Response response = bounded(http).newCall(builder.get().build()).execute())
		{
			String tag = response.header("ETag", "");
			if (response.code() == 304)
			{
				if (prior == null || prior.etag == null || !prior.etag.equals(tag))
				{
					throw new IOException("Unsolicited communications not-modified response");
				}
				return prior;
			}
			if (!tag.matches("\"[0-9a-f]{64}\""))
			{
				// Older deployments do not support conditional fetches.
				tag = null;
			}
			return parse(read(response), Instant.now()).withEtag(tag);
		}
	}

	static Notice publish(OkHttpClient http, ChapterEventsClient.MemberSession owner,
						  String chapter, String kind, String title, String message)
		throws IOException
	{
		return publish(http, owner, chapter, kind, title, message, "", "empire");
	}

	static Notice publish(OkHttpClient http, ChapterEventsClient.MemberSession owner,
						  String chapter, String kind, String title, String message,
						  String discordUrl) throws IOException
	{
		return publish(http, owner, chapter, kind, title, message, discordUrl, "empire");
	}

	static Notice publish(OkHttpClient http, ChapterEventsClient.MemberSession owner,
						  String chapter, String kind, String title, String message,
						  String discordUrl, String audience) throws IOException
	{
		if (!"empire".equals(audience) &&
			!("broadcast".equals(kind) &&
			  ("chapter_command".equals(audience) || "all_admins".equals(audience))))
			throw new IOException("Invalid broadcast audience");
		// Server is authoritative for publishing, including future policy
		// grants and revocations. A valid Discord bearer is still mandatory.
		if (owner == null || !owner.valid(chapter))
		{
			throw new IOException("Verified Discord session required");
		}
		if (!kind.matches("(broadcast|announcement|emergency)") || title == null ||
			title.trim().isEmpty() || title.length() > 72 || message == null ||
			message.trim().isEmpty() ||
			message.length() > ("emergency".equals(kind)	 ? 220
								: "broadcast".equals(kind) ? 220
															 : 350))
		{
			throw new IOException("Invalid communication text");
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("kind", kind);
		payload.addProperty("title", title.trim());
		payload.addProperty("body", message.trim());
		if ("broadcast".equals(kind))
			payload.addProperty("scope", audience);
		if (discordUrl != null && !discordUrl.trim().isEmpty())
		{
			if (!"announcement".equals(kind) || !isDiscordPostUrl(discordUrl.trim()))
				throw new IOException("Only direct Discord message links are allowed");
			payload.addProperty("discord_url", discordUrl.trim());
		}
		byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 1400)
		{
			throw new IOException("Communication too long");
		}
		Request request =
			new Request.Builder()
				.url(origin() + POST_PATH)
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + owner.token)
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bytes))
				.build();
		try (Response response = bounded(http).newCall(request).execute())
		{
			JsonObject result = parseObject(read(response));
			return notice(result.getAsJsonObject("published"));
		}
	}

	static void emergencyAction(OkHttpClient http, ChapterEventsClient.MemberSession owner,
								String chapter, String action) throws IOException
	{
		if (owner == null || !owner.valid(chapter) ||
			!("end".equals(action) || "cancel".equals(action)))
			throw new IOException("Verified High Lord or Emperor role required");
		JsonObject payload = new JsonObject();
		payload.addProperty("action", action);
		Request request =
			new Request.Builder()
				.url(origin() + "/v1/empire-comms/emergency")
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + owner.token)
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
										 payload.toString().getBytes(StandardCharsets.UTF_8)))
				.build();
		try (Response response = bounded(http).newCall(request).execute())
		{
			JsonObject result = parseObject(read(response));
			if (!result.has("result") || !result.get("result").isJsonObject())
				throw new IOException("Invalid emergency response");
		}
	}

	static boolean isDiscordPostUrl(String url)
	{
		if (url == null || url.length() > 220)
			return false;
		try
		{
			java.net.URI value = java.net.URI.create(url);
			return "https".equals(value.getScheme()) && "discord.com".equals(value.getHost()) &&
				value.getPort() == -1 && value.getRawQuery() == null &&
				value.getRawFragment() == null && value.getUserInfo() == null &&
				value.getRawPath() != null &&
				value.getRawPath().matches("/channels/[0-9]{15,22}/[0-9]{15,22}/[0-9]{15,22}");
		}
		catch (RuntimeException error)
		{
			return false;
		}
	}

	static void manage(OkHttpClient http, ChapterEventsClient.MemberSession owner, String chapter,
					   String action, long id, String title, String body, String discordUrl)
		throws IOException
	{
		if (owner == null || !owner.valid(chapter) || id < 1 ||
			!("edit".equals(action) || "remove".equals(action)))
			throw new IOException("Verified Owner authorization required");
		JsonObject payload = new JsonObject();
		payload.addProperty("action", action);
		payload.addProperty("id", id);
		if ("edit".equals(action))
		{
			if (title == null || title.trim().isEmpty() || title.length() > 72 || body == null ||
				body.trim().isEmpty() || body.length() > 350 ||
				(discordUrl != null && !discordUrl.trim().isEmpty() &&
				 !isDiscordPostUrl(discordUrl.trim())))
				throw new IOException("Invalid announcement changes");
			payload.addProperty("title", title.trim());
			payload.addProperty("body", body.trim());
			payload.addProperty("discord_url", discordUrl == null ? "" : discordUrl.trim());
		}
		byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 1400)
			throw new IOException("Announcement changes too large");
		Request request =
			new Request.Builder()
				.url(origin() + MANAGE_PATH)
				.header("Accept", "application/json")
				.header("Authorization", "Bearer " + owner.token)
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), bytes))
				.build();
		try (Response response = bounded(http).newCall(request).execute())
		{
			JsonObject result = parseObject(read(response));
			if (!result.has("result") || !result.get("result").isJsonObject())
				throw new IOException("Invalid announcement management reply");
		}
	}

	private static byte[] read(Response response) throws IOException
	{
		if (response.code() != 200)
		{
			String reason = "Request rejected";
			ResponseBody failure = response.body();
			if (failure != null && failure.contentLength() <= 2048)
			{
				try
				{
					JsonObject doc = parseObject(failure.bytes());
					if (doc.has("error") && doc.get("error").isJsonPrimitive())
					{
						String detail = doc.get("error").getAsString();
						if (detail.length() <= 180 && detail.matches("[A-Za-z0-9 ,.;:!?'()/-]+"))
							reason = detail;
					}
				}
				catch (Exception ignored)
				{
				}
			}
			throw new IOException("HTTP " + response.code() + ": " + reason);
		}
		if (!response.header("Content-Type", "")
				 .toLowerCase(Locale.ROOT)
				 .startsWith("application/json"))
		{
			throw new IOException("Invalid communications content type");
		}
		ResponseBody body = response.body();
		if (body == null || body.contentLength() > MAX_BYTES)
		{
			throw new IOException("Communications payload too large");
		}
		byte[] result;
		try (InputStream stream = body.byteStream())
		{
			result = stream.readNBytes(MAX_BYTES + 1);
		}
		if (result.length > MAX_BYTES)
		{
			throw new IOException("Communications payload too large");
		}
		return result;
	}

	private static JsonObject parseObject(byte[] bytes) throws IOException
	{
		try
		{
			return new JsonParser()
				.parse(new String(bytes, StandardCharsets.UTF_8))
				.getAsJsonObject();
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid Empire communications JSON", error);
		}
	}

	static Snapshot parse(byte[] bytes, Instant now) throws IOException
	{
		JsonObject doc = parseObject(bytes);
		try
		{
			if (doc.get("schema_version").getAsInt() != 1 ||
				!KIND.equals(doc.get("kind").getAsString()))
			{
				throw new IOException("Unsupported Empire communications feed");
			}
			long generated = doc.get("generated_at").getAsLong();
			long sequence = doc.get("sequence").getAsLong();
			if (sequence < 0 || sequence > Long.MAX_VALUE - 100 ||
				generated > now.getEpochSecond() + 300 || generated < now.getEpochSecond() - 300)
			{
				throw new IOException("Empire communications are stale");
			}
			List<Notice> announcements =
				list(doc.getAsJsonArray("announcements"), "announcement", 60);
			List<Notice> broadcasts = list(doc.getAsJsonArray("broadcasts"), "broadcast", 30);
			Notice emergency =
				doc.get("emergency").isJsonNull() ? null : notice(doc.getAsJsonObject("emergency"));
			if (emergency != null && (!"emergency".equals(emergency.kind) ||
									  emergency.expiresAt <= now.getEpochSecond()))
			{
				emergency = null;
			}
			Set<String> capabilities = new HashSet<>();
			if (doc.has("capabilities"))
			{
				if (!doc.get("capabilities").isJsonArray())
					throw new IOException("Invalid admin capabilities");
				JsonArray entries = doc.getAsJsonArray("capabilities");
				if (entries.size() > 32)
					throw new IOException("Too many admin capabilities");
				for (JsonElement element : entries)
				{
					if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
						throw new IOException("Invalid admin capability");
					String capability = element.getAsString();
					if (capability.length() > 45 || !capability.matches("[a-z]+(\\.[a-z]+){1,2}") ||
						!capabilities.add(capability))
						throw new IOException("Invalid or repeated admin capability");
				}
			}
			return new Snapshot(generated, sequence, announcements, broadcasts, emergency,
								capabilities);
		}
		catch (IOException error)
		{
			throw error;
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid Empire communications feed", error);
		}
	}

	private static List<Notice> list(JsonArray values, String type, int max) throws IOException
	{
		if (values == null || values.size() > max)
		{
			throw new IOException("Invalid communications list");
		}
		List<Notice> out = new ArrayList<>();
		Set<Long> ids = new HashSet<>();
		for (JsonElement element : values)
		{
			Notice n = notice(element.getAsJsonObject());
			if (!ids.add(n.id) || !(n.kind.equals(type) ||
									("broadcast".equals(type) && "emergency".equals(n.kind))))
			{
				throw new IOException("Invalid communications entry");
			}
			out.add(n);
		}
		return out;
	}

	private static Notice notice(JsonObject doc) throws IOException
	{
		try
		{
			long id = doc.get("id").getAsLong();
			String kind = doc.get("kind").getAsString();
			String title = doc.get("title").getAsString();
			String body = doc.get("body").getAsString();
			long created = doc.get("created_at").getAsLong();
			long expires =
				doc.get("expires_at").isJsonNull() ? 0 : doc.get("expires_at").getAsLong();
			if (id < 1 || !kind.matches("(broadcast|announcement|emergency)") ||
				!valid(title, 72) || !valid(body, 350) || created < 0 ||
				("emergency".equals(kind) && expires < created))
			{
				throw new IOException("Invalid communications record");
			}
			String audience = doc.has("scope") && !doc.get("scope").isJsonNull()
								  ? doc.get("scope").getAsString()
								  : "empire";
			String target = doc.has("target_chapter") && !doc.get("target_chapter").isJsonNull()
								? doc.get("target_chapter").getAsString()
								: "";
			if (!audience.matches("empire|chapter_command|all_admins") || target.length() > 80 ||
				target.contains("<") || target.contains(">"))
				throw new IOException("Invalid broadcast audience");
			String link = doc.has("discord_url") && !doc.get("discord_url").isJsonNull()
							  ? doc.get("discord_url").getAsString()
							  : "";
			long modified = doc.has("updated_at") && !doc.get("updated_at").isJsonNull()
								? doc.get("updated_at").getAsLong()
								: created;
			if (!link.isEmpty() && (!"announcement".equals(kind) || !isDiscordPostUrl(link)))
				throw new IOException("Invalid Discord post URL");
			if (modified < created)
				throw new IOException("Invalid update timestamp");
			String postedBy = doc.has("posted_by") && !doc.get("posted_by").isJsonNull()
								  ? doc.get("posted_by").getAsString()
								  : "Discord Member";
			if (postedBy.isEmpty() || postedBy.length() > 64 ||
				postedBy.chars().anyMatch(c -> c < 32 || c == 127))
				throw new IOException("Invalid Discord poster name");
			boolean canManage = doc.has("can_manage") && !doc.get("can_manage").isJsonNull() &&
								doc.get("can_manage").getAsBoolean();
			return new Notice(id, kind, title, body, created, expires, link, modified, audience,
							  target, postedBy, canManage);
		}
		catch (IOException error)
		{
			throw error;
		}
		catch (RuntimeException error)
		{
			throw new IOException("Malformed communications record", error);
		}
	}

	private static boolean valid(String text, int max)
	{
		if (text == null || text.trim().isEmpty() || text.length() > max)
		{
			return false;
		}
		for (int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if (c < 32 && c != '\n' && c != '\r' && c != '\t' || c == 127)
			{
				return false;
			}
		}
		return true;
	}
}
