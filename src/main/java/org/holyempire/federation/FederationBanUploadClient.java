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
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** One explicit chapter-admin upload; no automatic player collection or retries. */
final class FederationBanUploadClient
{
	static final String OFFICIAL_ENDPOINT = "https://bans.holyempireosrs.cc";

	private FederationBanUploadClient()
	{
	}

	static boolean acceptedEndpoint(String raw)
	{
		try
		{
			URI uri = URI.create(raw);
			if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null ||
				uri.getRawQuery() != null)
				return false;
			String path = uri.getRawPath();
			if (path != null && !path.isEmpty() && !path.equals("/"))
				return false;
			if ("https".equalsIgnoreCase(uri.getScheme()) &&
				"bans.holyempireosrs.cc".equalsIgnoreCase(uri.getHost()) && uri.getPort() == -1)
				return true;
			// Ephemeral loopback endpoint only in isolated tests, never remote HTTP.
			String test = System.getProperty("holyempire.testBanSyncOrigin", "");
			return "http".equalsIgnoreCase(uri.getScheme()) &&
				"127.0.0.1".equals(uri.getHost()) && raw.equals(test);
		}
		catch (RuntimeException ex)
		{
			return false;
		}
	}

	static String payload(FederationBanRegistry.Snapshot capture)
	{
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", 1);
		root.addProperty("chapter", capture.chapter);
		root.addProperty("captured_at", Instant.parse(capture.timestamp).getEpochSecond());
		JsonArray names = new JsonArray();
		for (String name : capture.names)
			names.add(name);
		root.add("bans", names);
		return root.toString();
	}

	static String send(FederationBanRegistry.Snapshot capture, String base, String token,
					   OkHttpClient http) throws Exception
	{
		if (!acceptedEndpoint(base))
			throw new IllegalArgumentException(
				"Configure an HTTPS private ban-sync endpoint first.");
		if (token == null || token.length() < 24 || token.length() > 256)
			throw new IllegalArgumentException("A chapter administrator upload token is required.");
		URI origin = URI.create(base);
		String normalized = origin.toString().replaceAll("/+$", "");
		byte[] body = payload(capture).getBytes(StandardCharsets.UTF_8);
		if (body.length > 16000)
			throw new IllegalArgumentException("Ban payload too large.");
		// A bearer credential must never be forwarded to a redirected hostname.
		// This derived client inherits RuneLite's TLS and proxy configuration.
		OkHttpClient bounded = http.newBuilder()
								   .followRedirects(false)
								   .followSslRedirects(false)
								   .connectTimeout(5, TimeUnit.SECONDS)
								   .readTimeout(7, TimeUnit.SECONDS)
								   .build();
		Request request =
			new Request.Builder()
				.url(normalized + "/v1/ban-sync")
				.header("Authorization", "Bearer " + token)
				.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body))
				.build();
		try (Response response = bounded.newCall(request).execute())
		{
			if (response.code() == 429)
			{
				String retry = response.header("Retry-After", "");
				String detail = "Please try again shortly.";
				try
				{
					long seconds = Long.parseLong(retry.trim());
					if (seconds > 0 && seconds <= 86400)
					{
						long minutes = (seconds + 59) / 60;
						detail = seconds < 60 ? "Try again in " + seconds + " seconds."
											  : "Try again in about " + minutes +
													(minutes == 1 ? " minute." : " minutes.");
					}
				}
				catch (NumberFormatException ignored)
				{
				}
				throw new java.io.IOException(
					"Ban sync is temporarily rate-limited (HTTP 429). "
					+ "This upload did not change the central ban list. " + detail);
			}
			if (response.code() != 200)
				throw new java.io.IOException("Ban-sync server refused upload (HTTP " +
											  response.code() + ").");
			ResponseBody responseBody = response.body();
			if (responseBody == null || responseBody.contentLength() > 2048)
				throw new java.io.IOException("Ban-sync acknowledgement missing or too large.");
			byte[] data;
			try (InputStream stream = responseBody.byteStream())
			{
				data = stream.readNBytes(2049);
			}
			if (data.length > 2048)
				throw new java.io.IOException("Ban-sync acknowledgement too large.");
			JsonObject result =
				new JsonParser().parse(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
			if (!result.has("ok") || !result.get("ok").getAsBoolean() ||
				!capture.chapter.equals(result.get("chapter").getAsString()) ||
				result.get("count").getAsInt() != capture.names.size())
				throw new java.io.IOException("Ban-sync acknowledgement did not match capture.");
			return "Synced " + capture.names.size() + " bans for " + capture.chapter +
				" (revision " + result.get("revision").getAsInt() + ").";
		}
	}
}
