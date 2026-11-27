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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Private, chapter-scoped event API. Never mixes officer and member tokens. */
final class ChapterEventsClient
{
	static final String BASE = FederationBanUploadClient.OFFICIAL_ENDPOINT;
	private static final int MAX_RESPONSE = 128_000;
	private static final String MIME = "application/json; charset=utf-8";

	private ChapterEventsClient()
	{
	}

	/** Chapter-event requests always use the fixed production origin. */
	static String origin()
	{
		return BASE;
	}

	static String connectUrl()
	{
		return origin() + "/v1/chapter-events/connect";
	}

	static boolean acceptedChapterOrigin(String url)
	{
		return FederationBanUploadClient.acceptedEndpoint(url);
	}

	static final class MemberSession
	{
		final String token;
		final String chapter;
		final long expiresAt;
		final boolean discordAdmin;
		final boolean empireOwner;
		final String discordRole;

		MemberSession(String token, String chapter, long expiresAt)
		{
			this(token, chapter, expiresAt, false, false);
		}

		MemberSession(String token, String chapter, long expiresAt, boolean discordAdmin)
		{
			this(token, chapter, expiresAt, discordAdmin, false);
		}

		MemberSession(String token, String chapter, long expiresAt, boolean discordAdmin,
					  boolean empireOwner)
		{
			this(token, chapter, expiresAt, discordAdmin, empireOwner, "");
		}

		MemberSession(String token, String chapter, long expiresAt, boolean discordAdmin,
					  boolean empireOwner, String discordRole)
		{
			this.discordRole = discordRole;
			this.token = token;
			this.chapter = chapter;
			this.expiresAt = expiresAt;
			this.discordAdmin = discordAdmin;
			this.empireOwner = empireOwner;
		}

		boolean valid(String chapter)
		{
			return token != null && token.length() >= 32 && this.chapter.equals(chapter) &&
				Instant.now().getEpochSecond() + 30 < expiresAt;
		}
	}

	private static JsonObject post(String path, JsonObject data, String bearer, String base,
								   OkHttpClient http) throws IOException
	{
		if (!acceptedChapterOrigin(base))
		{
			throw new IOException("Trusted chapter events HTTPS origin required");
		}
		if (!path.matches("(redeem|read|roster-read|rsvp|empire-interest)"))
		{
			throw new IOException("Unknown chapter event action");
		}
		if (("read".equals(path) || "rsvp".equals(path) || "empire-interest".equals(path)) &&
			(bearer == null || bearer.length() < 24 || bearer.length() > 256))
		{
			throw new IOException("Verified chapter credential required");
		}
		byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 4096)
		{
			throw new IOException("Chapter event request exceeds limit");
		}
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   // Personal Empire interest may query several cold Discord
								   // subscriber lists. Other API reads retain the short limit.
								   .readTimeout("read".equals(path) && data.has("event_ids")
									? 30 : 8, TimeUnit.SECONDS)
								   .build();
		Request.Builder req = new Request.Builder()
								  .url(base.replaceAll("/+$", "") + "/v1/chapter-events/" + path)
								  .header("Accept", "application/json")
								  .post(RequestBody.create(MediaType.parse(MIME), bytes));
		if ("read".equals(path) || "rsvp".equals(path) || "empire-interest".equals(path))
		{
			req.header("Authorization", "Bearer " + bearer);
		}
		try (Response response = bounded.newCall(req.build()).execute())
		{
			if (response.code() != 200)
			{
				throw new IOException("Chapter events server returned HTTP " + response.code());
			}
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > MAX_RESPONSE)
			{
				throw new IOException("Chapter events response too large");
			}
			byte[] raw;
			try (InputStream input = body.byteStream())
			{
				raw = input.readNBytes(MAX_RESPONSE + 1);
			}
			if (raw.length > MAX_RESPONSE)
			{
				throw new IOException("Chapter events response too large");
			}
			try
			{
				return new JsonParser()
					.parse(new String(raw, StandardCharsets.UTF_8))
					.getAsJsonObject();
			}
			catch (RuntimeException ex)
			{
				throw new IOException("Invalid chapter events response", ex);
			}
		}
	}

	static final class OAuthAttempt
	{
		final String id;
		final String claim;
		final String browserUrl;
		final long expiresAt;

		OAuthAttempt(String id, String claim, String browserUrl, long expiresAt)
		{
			this.id = id;
			this.claim = claim;
			this.browserUrl = browserUrl;
			this.expiresAt = expiresAt;
		}
	}

	static final class OAuthResult
	{
		final String status;
		final MemberSession member;
		final String remembered;
		final long rememberUntil;

		OAuthResult(String status, MemberSession member)
		{
			this(status, member, "", 0);
		}

		OAuthResult(String status, MemberSession member, String remembered, long until)
		{
			this.status = status;
			this.member = member;
			this.remembered = remembered;
			this.rememberUntil = until;
		}
	}

	private static JsonObject connectPost(String operation, JsonObject data, OkHttpClient http)
		throws IOException
	{
		if (!operation.matches("(start|claim|resume)") || !acceptedChapterOrigin(origin()))
		{
			throw new IOException("Trusted Holy Empire Discord origin required");
		}
		byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 500)
		{
			throw new IOException("Invalid Discord verification request");
		}
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(8, TimeUnit.SECONDS)
								   .build();
		Request request = new Request.Builder()
							  .url(origin() + "/v1/discord-connect/" + operation)
							  .header("Accept", "application/json")
							  .post(RequestBody.create(MediaType.parse(MIME), bytes))
							  .build();
		try (Response response = bounded.newCall(request).execute())
		{
			if (response.code() != 200)
			{
				throw new IOException("One-click Discord verification unavailable (HTTP " +
									  response.code() +
									  "). Reconnect in Discord if your role changed.");
			}
			ResponseBody body = response.body();
			if (body == null || body.contentLength() > 4096)
			{
				throw new IOException("Invalid Discord verification response");
			}
			byte[] raw;
			try (InputStream input = body.byteStream())
			{
				raw = input.readNBytes(4097);
			}
			if (raw.length > 4096)
			{
				throw new IOException("Discord verification response too large");
			}
			try
			{
				return new JsonParser()
					.parse(new String(raw, StandardCharsets.UTF_8))
					.getAsJsonObject();
			}
			catch (RuntimeException ex)
			{
				throw new IOException("Invalid Discord verification reply", ex);
			}
		}
	}

	static OAuthAttempt beginOAuth(OkHttpClient http) throws IOException
	{
		return beginOAuth(null, http);
	}

	static OAuthAttempt beginOAuth(String nativeChapter, OkHttpClient http) throws IOException
	{
		JsonObject request = new JsonObject();
		// The backend checks the requested scope against LIVE Discord roles.
		if (nativeChapter != null && !nativeChapter.isEmpty())
			request.addProperty("chapter", nativeChapter);
		JsonObject result = connectPost("start", request, http);
		try
		{
			String id = result.get("id").getAsString();
			String claim = result.get("claim").getAsString();
			String link = result.get("url").getAsString();
			long expires = result.get("expires_at").getAsLong();
			URI url = URI.create(link);
			if (!id.matches("[0-9a-f]{32}") || !claim.matches("[A-Za-z0-9_-]{32,128}") ||
				!"https".equals(url.getScheme()) || !"discord.com".equals(url.getHost()) ||
				!"/oauth2/authorize".equals(url.getPath()) || url.getRawQuery() == null ||
				url.getRawQuery().length() > 2048 || url.getFragment() != null ||
				url.getUserInfo() != null || url.getPort() != -1 ||
				expires <= Instant.now().getEpochSecond() ||
				expires > Instant.now().getEpochSecond() + 620)
			{
				throw new IOException("Invalid Discord verification start response");
			}
			return new OAuthAttempt(id, claim, link, expires);
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Invalid Discord verification start response", ex);
		}
	}

	static OAuthResult pollOAuth(OAuthAttempt attempt, String nativeChapter, OkHttpClient http)
		throws IOException
	{
		if (attempt == null || nativeChapter == null || nativeChapter.isEmpty() ||
			Instant.now().getEpochSecond() > attempt.expiresAt)
		{
			throw new IOException("Discord verification timed out");
		}
		JsonObject body = new JsonObject();
		body.addProperty("id", attempt.id);
		body.addProperty("claim", attempt.claim);
		JsonObject result = connectPost("claim", body, http);
		try
		{
			String status = result.get("status").getAsString();
			if ("pending".equals(status) || "expired".equals(status) || "denied".equals(status))
			{
				return new OAuthResult(status, null);
			}
			if (!"verified".equals(status))
			{
				throw new IOException("Unknown Discord verification status");
			}
			return verifiedOAuthResult(result, nativeChapter);
		}
		catch (IOException ex)
		{
			throw ex;
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Invalid Discord verification session", ex);
		}
	}

	private static OAuthResult verifiedOAuthResult(JsonObject result, String nativeChapter)
		throws IOException
	{
		try
		{
			JsonObject info = result.getAsJsonObject("session");
			String chapter = info.get("chapter").getAsString();
			String token = info.get("token").getAsString();
			long expires = info.get("expires_at").getAsLong();
			boolean isAdmin = info.has("admin") && info.get("admin").getAsBoolean();
			boolean isOwner = info.has("owner") && info.get("owner").getAsBoolean();
			if (!nativeChapter.equals(chapter) || !token.matches("[A-Za-z0-9_-]{32,128}") ||
				expires <= Instant.now().getEpochSecond() ||
				expires > Instant.now().getEpochSecond() + 3700)
			{
				throw new IOException("Discord role does not match your chapter");
			}
			String remembered =
				info.has("remember_token") && !info.get("remember_token").isJsonNull()
					? info.get("remember_token").getAsString()
					: "";
			long rememberUntil =
				info.has("remember_expires_at") && !info.get("remember_expires_at").isJsonNull()
					? info.get("remember_expires_at").getAsLong()
					: 0;
			if (!remembered.isEmpty() &&
				(!remembered.matches("[A-Za-z0-9_-]{32,128}") ||
				 rememberUntil <= Instant.now().getEpochSecond() ||
				 rememberUntil > Instant.now().getEpochSecond() + 181L * 86400))
			{
				throw new IOException("Invalid remembered Discord credential");
			}
			String role = info.has("role") && !info.get("role").isJsonNull()
							  ? info.get("role").getAsString()
							  : "";
			if (role.length() > 64 || role.contains("<") || role.contains(">") ||
				role.contains("\n") || role.contains("\r"))
				throw new IOException("Invalid Discord role label");
			return new OAuthResult(
				"verified", new MemberSession(token, chapter, expires, isAdmin, isOwner, role),
				remembered, rememberUntil);
		}
		catch (IOException ex)
		{
			throw ex;
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Invalid Discord verification session", ex);
		}
	}

	static OAuthResult resume(String remembered, String nativeChapter, OkHttpClient http)
		throws IOException
	{
		if (remembered == null || !remembered.matches("[A-Za-z0-9_-]{32,128}") ||
			nativeChapter == null || nativeChapter.isEmpty())
			throw new IOException("Invalid remembered Discord session");
		JsonObject input = new JsonObject();
		input.addProperty("remember_token", remembered);
		JsonObject result = connectPost("resume", input, http);
		if (!result.has("status") || !"verified".equals(result.get("status").getAsString()))
			throw new IOException("Discord session cannot be restored");
		return verifiedOAuthResult(result, nativeChapter);
	}

	static MemberSession redeem(String code, String nativeChapter, OkHttpClient http)
		throws IOException
	{
		JsonObject data = new JsonObject();
		data.addProperty("code", code);
		data.addProperty("chapter", nativeChapter);
		JsonObject result = post("redeem", data, null, origin(), http);
		try
		{
			String chapter = result.get("chapter").getAsString();
			String token = result.get("token").getAsString();
			long expires = result.get("expires_at").getAsLong();
			if (!nativeChapter.equals(chapter) || token.length() < 32 || token.length() > 256 ||
				expires <= Instant.now().getEpochSecond() ||
				expires > Instant.now().getEpochSecond() + 3700)
			{
				throw new IOException("Chapter event session was not scoped correctly");
			}
			return new MemberSession(token, chapter, expires);
		}
		catch (RuntimeException ex)
		{
			throw new IOException("Invalid chapter event session", ex);
		}
	}

	static List<DiscordEventsClient.Event> read(String chapter, MemberSession member,
												OkHttpClient http) throws IOException
	{
		if (member == null || !member.valid(chapter))
		{
			throw new IOException("Connect Discord for verified chapter events first");
		}
		return parseChapter(post("read", new JsonObject(), member.token, origin(), http), chapter);
	}

	/** Query only the requesting Discord-verified member's Empire event interest. */
	static Set<String> empireInterest(List<DiscordEventsClient.Event> events,
		MemberSession member, OkHttpClient http) throws IOException
	{
		if (member == null || !member.valid(member.chapter))
			return Collections.emptySet();
		JsonArray ids = new JsonArray();
		Set<String> requested = new HashSet<>();
		for (DiscordEventsClient.Event event : events)
		{
			if ("empire".equals(event.scope) && event.id.matches("[0-9]{15,22}") &&
				requested.size() < 24 && requested.add(event.id))
				ids.add(event.id);
		}
		if (ids.size() == 0) return Collections.emptySet();
		JsonObject form = new JsonObject();
		form.add("event_ids", ids);
		JsonObject doc = post("read", form, member.token, origin(), http);
		try
		{
			JsonArray returned = doc.getAsJsonArray("interested_event_ids");
			if (returned == null || returned.size() > ids.size())
				throw new IOException("Invalid Empire event interest response");
			Set<String> matches = new HashSet<>();
			for (JsonElement row : returned)
			{
				String id = row.getAsString();
				if (!requested.contains(id) || !matches.add(id))
					throw new IOException("Unexpected Empire event interest result");
			}
			return Collections.unmodifiableSet(matches);
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid Empire event interest response", error);
		}
	}

	static void rsvp(String eventId, String response, String chapter, MemberSession member,
					 OkHttpClient http) throws IOException
	{
		if (member == null || !member.valid(chapter) || eventId == null ||
			!eventId.matches("[0-9a-f]{32}") || response == null ||
			!response.matches("(going|maybe|declined|clear)"))
		{
			throw new IOException("Verified Discord chapter connection required to RSVP");
		}
		JsonObject form = new JsonObject();
		form.addProperty("event_id", eventId);
		form.addProperty("response", response);
		JsonObject result = post("rsvp", form, member.token, origin(), http);
		try
		{
			if (!eventId.equals(result.get("event_id").getAsString()) ||
				result.getAsJsonObject("rsvp") == null)
			{
				throw new IOException("RSVP confirmation mismatch");
			}
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid RSVP response", error);
		}
	}

	/**
	 * Already-rostered players read Empire/chapter events with no additional
	 * Discord code. This is a non-confidential, username-scoped convenience
	 * feed: an RSN alone is not proof of account ownership.
	 */

	static List<DiscordEventsClient.Event> readRoster(String account, OkHttpClient http)
		throws IOException
	{
		String rsn = WomDirectoryClient.normalize(account);
		if (rsn.isEmpty() || rsn.length() > 12)
		{
			throw new IOException("An eligible RuneScape account is required");
		}
		JsonObject request = new JsonObject();
		request.addProperty("rsn", rsn);
		JsonObject doc = post("roster-read", request, null, origin(), http);
		try
		{
			if (doc.get("schema_version").getAsInt() != 1 ||
				!"holy-empire-roster-events".equals(doc.get("kind").getAsString()))
			{
				throw new IOException("Unexpected roster event document");
			}
			JsonArray chapters = doc.getAsJsonArray("chapters");
			JsonArray events = doc.getAsJsonArray("events");
			if (chapters == null || chapters.size() < 1 || chapters.size() > 25 || events == null ||
				events.size() > 80)
			{
				throw new IOException("Roster event response too large");
			}
			Set<String> permitted = new HashSet<>();
			for (JsonElement value : chapters)
			{
				String chapter = value.getAsString();
				if (chapter.isEmpty() || chapter.length() > 32 || !permitted.add(chapter))
				{
					throw new IOException("Invalid roster chapter set");
				}
			}
			Map<String, JsonArray> separated = new LinkedHashMap<>();
			for (JsonElement element : events)
			{
				JsonObject entry = element.getAsJsonObject();
				String chapter = entry.get("chapter").getAsString();
				if (!permitted.contains(chapter))
				{
					throw new IOException("Event not authorized for a synced chapter");
				}
				separated.computeIfAbsent(chapter, ignored -> new JsonArray()).add(entry);
			}
			List<DiscordEventsClient.Event> combined = new ArrayList<>();
			for (Map.Entry<String, JsonArray> group : separated.entrySet())
			{
				JsonObject nativeDoc = new JsonObject();
				nativeDoc.addProperty("schema_version", 1);
				nativeDoc.addProperty("kind", "holy-empire-chapter-events");
				nativeDoc.addProperty("chapter", group.getKey());
				nativeDoc.add("events", group.getValue());
				combined.addAll(parseChapter(nativeDoc, group.getKey()));
			}
			combined.sort(Comparator.comparing((DiscordEventsClient.Event e) -> e.start)
							  .thenComparing(e -> e.id));
			return Collections.unmodifiableList(combined);
		}
		catch (IOException error)
		{
			throw error;
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid synced-roster event response", error);
		}
	}

	static List<DiscordEventsClient.Event> parseChapter(JsonObject doc, String chapter)
		throws IOException
	{
		try
		{
			if (doc.get("schema_version").getAsInt() != 1 ||
				!"holy-empire-chapter-events".equals(doc.get("kind").getAsString()) ||
				!chapter.equals(doc.get("chapter").getAsString()))
			{
				throw new IOException("Chapter events response does not match your chapter");
			}
			JsonArray entries = doc.getAsJsonArray("events");
			if (entries == null || entries.size() > 80)
			{
				throw new IOException("Chapter events list exceeds limit");
			}
			List<DiscordEventsClient.Event> events = new ArrayList<>();
			Set<String> ids = new HashSet<>();
			for (JsonElement item : entries)
			{
				JsonObject event = item.getAsJsonObject();
				String id = event.get("id").getAsString();
				String scope = event.get("scope").getAsString();
				String source = event.get("chapter").getAsString();
				String name = event.get("name").getAsString();
				String description = event.get("description").getAsString();
				String location = event.get("location").getAsString();
				String status = event.get("status").getAsString();
				Instant start = Instant.parse(event.get("start_time").getAsString());
				Instant end = Instant.parse(event.get("end_time").getAsString());
				if (!id.matches("[0-9a-f]{32}") || !ids.add(id) || !"chapter".equals(scope) ||
					!chapter.equals(source) || !("scheduled".equals(status)) || name.isEmpty() ||
					name.length() > 100 || description.length() > 1200 || location.length() > 120 ||
					!end.isAfter(start))
				{
					throw new IOException("Invalid chapter event record");
				}
				// Only a confirmed Discord bot message gets a clickable
				// chapter-post link. This URL cannot be supplied by a client.
				String discordPost = event.has("url") ? event.get("url").getAsString() : "";
				String targetGuild = "736564064834289774";
				if (!discordPost.isEmpty() &&
					!discordPost.matches("https://discord[.]com/channels/" + targetGuild +
										 "/[0-9]{15,22}/[0-9]{15,22}"))
				{
					throw new IOException("Invalid chapter announcement link");
				}
				String imageUrl =
					event.has("image_url") ? event.get("image_url").getAsString() : "";
				if (!imageUrl.isEmpty())
				{
					try
					{
						URI image = URI.create(imageUrl);
						String imagePath = image.getRawPath();
						if (!"https".equals(image.getScheme()) ||
							!"cdn.discordapp.com".equals(image.getHost()) ||
							image.getPort() != -1 || image.getUserInfo() != null ||
							image.getFragment() != null || imagePath == null ||
							!(imagePath.startsWith("/attachments/") ||
							  imagePath.startsWith("/ephemeral-attachments/")) ||
							imageUrl.length() > 2048)
						{
							throw new IOException("Invalid Discord event image link");
						}
					}
					catch (IllegalArgumentException error)
					{
						throw new IOException("Invalid event image", error);
					}
				}
				int going = 0, maybe = 0, declined = 0;
				String yours = "";
				if (event.has("rsvp") && event.get("rsvp").isJsonObject())
				{
					JsonObject rsvp = event.getAsJsonObject("rsvp");
					if (rsvp.has("going"))
						going = rsvp.get("going").getAsInt();
					if (rsvp.has("maybe"))
						maybe = rsvp.get("maybe").getAsInt();
					if (rsvp.has("declined"))
						declined = rsvp.get("declined").getAsInt();
					if (rsvp.has("yours"))
						yours = rsvp.get("yours").getAsString();
				}
				if (going < 0 || maybe < 0 || declined < 0 || going > 100000 || maybe > 100000 ||
					declined > 100000 || !yours.matches("(going|maybe|declined|)"))
				{
					throw new IOException("Invalid chapter RSVP data");
				}
				events.add(new DiscordEventsClient.Event("chapter", chapter, id, name, description,
														 status, location, start, end, discordPost,
														 imageUrl, going, maybe, declined, yours));
			}
			return Collections.unmodifiableList(events);
		}
		catch (IOException error)
		{
			throw error;
		}
		catch (RuntimeException error)
		{
			throw new IOException("Invalid chapter event document", error);
		}
	}
}
