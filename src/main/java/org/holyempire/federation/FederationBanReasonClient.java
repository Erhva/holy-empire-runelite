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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Admin-only, explicitly requested reason metadata and Servo-Skull publication. */
final class FederationBanReasonClient
{
    static final String CHANNEL_ID = "1391781923365453945";
    static final String GUILD_ID = "736564064834289774";
    static final Set<String> CATEGORIES = Collections.unmodifiableSet(
        new HashSet<>(Arrays.asList(
            "Begging", "Scamming/Luring", "Botting/RWT", "Harassment", "Other")));

    static final class Result
    {
        final String name;
        final boolean published;
        final String discordUrl;
        final String message;

        Result(String name, boolean published, String discordUrl, String message)
        {
            this.name = name;
            this.published = published;
            this.discordUrl = discordUrl;
            this.message = message;
        }
    }

    private FederationBanReasonClient() {}

    /** Clear an officer-authorized private reason without changing the ban. */
    static Result clear(String chapter, String rsn, int revision, String endpoint,
                        String bearer, OkHttpClient http) throws IOException
    {
        if (!FederationBanUploadClient.acceptedEndpoint(endpoint) ||
            bearer == null || bearer.length() < 24 || bearer.length() > 256 ||
            FederationBanRegistry.officialChapter(chapter) == null ||
            FederationBanRegistry.validName(rsn) == null || revision < 1)
            throw new IOException("Approved officer access is required to clear a ban reason.");
        JsonObject data = new JsonObject();
        data.addProperty("action", "clear_reason");
        data.addProperty("chapter", chapter);
        data.addProperty("rsn", rsn);
        data.addProperty("revision", revision);
        OkHttpClient bounded = http.newBuilder().followRedirects(false)
            .followSslRedirects(false).connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS).build();
        Request request = new Request.Builder()
            .url(endpoint.replaceAll("/+$", "") + "/v1/ban-sync")
            .header("Authorization", "Bearer " + bearer)
            .header("Accept", "application/json")
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                data.toString().getBytes(StandardCharsets.UTF_8))).build();
        try (Response response = bounded.newCall(request).execute())
        {
            if (response.code() != 200)
                throw new IOException("Could not clear ban reason (HTTP " +
                    response.code() + "). Refresh the private ban list and retry.");
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > 4096)
                throw new IOException("Invalid reason-clear acknowledgement.");
            byte[] raw;
            try (InputStream stream = body.byteStream())
            {
                raw = stream.readNBytes(4097);
            }
            if (raw.length > 4096)
                throw new IOException("Invalid reason-clear acknowledgement.");
            try
            {
                JsonObject doc = new JsonParser().parse(
                    new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!doc.get("ok").getAsBoolean() ||
                    !doc.get("cleared").getAsBoolean() ||
                    !chapter.equals(doc.get("chapter").getAsString()) ||
                    !WomDirectoryClient.normalize(rsn).equals(
                        WomDirectoryClient.normalize(doc.get("rsn").getAsString())))
                    throw new IOException("Reason-clear response mismatch.");
                boolean posted = doc.get("discord_posted").getAsBoolean();
                return new Result(rsn, posted, "",
                    "Saved ban reason and category cleared. The ban remains active." +
                    (posted ? " Any earlier Servo-Skull post in #ban-list remains unchanged."
                        : ""));
            }
            catch (RuntimeException bad)
            {
                throw new IOException("Invalid reason-clear response.", bad);
            }
        }
    }

    static JsonObject payload(String chapter, String rsn, String category,
                              String reason, int revision, boolean publish)
    {
        if (FederationBanRegistry.officialChapter(chapter) == null ||
            FederationBanRegistry.validName(rsn) == null ||
            !CATEGORIES.contains(category) || reason == null || reason.length() > 400 ||
            revision < 1 || reason.chars().anyMatch(c -> (c < 32 && c != '\n' &&
                c != '\t') || c == 127))
        {
            throw new IllegalArgumentException("Invalid ban reason or category.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("action", "annotate");
        body.addProperty("chapter", chapter);
        body.addProperty("rsn", rsn);
        body.addProperty("category", category);
        body.addProperty("reason", reason);
        body.addProperty("revision", revision);
        body.addProperty("publish", publish);
        return body;
    }

    static Result send(String chapter, String rsn, String category, String reason,
                       int revision, boolean publish, String endpoint, String bearer,
                       OkHttpClient http) throws IOException
    {
        if (!FederationBanUploadClient.acceptedEndpoint(endpoint) ||
            bearer == null || bearer.length() < 24 || bearer.length() > 256)
            throw new IOException("Approved officer access is required to save a ban reason.");
        byte[] requestBytes = payload(chapter,rsn,category,reason,revision,publish)
            .toString().getBytes(StandardCharsets.UTF_8);
        if (requestBytes.length > 4096)
            throw new IOException("Ban reason request exceeds safe limit.");
        OkHttpClient bounded = http.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build();
        Request request = new Request.Builder()
            .url(endpoint.replaceAll("/+$", "") + "/v1/ban-sync")
            .header("Authorization", "Bearer " + bearer)
            .header("Accept", "application/json")
            .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                                     requestBytes))
            .build();
        try (Response response = bounded.newCall(request).execute())
        {
            if (response.code() != 200)
                throw new IOException("Ban reason was not saved (HTTP " +
                    response.code() + "). Please refresh the ban list and retry.");
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > 4096)
                throw new IOException("Invalid ban reason acknowledgement.");
            byte[] raw;
            try (InputStream stream = body.byteStream())
            {
                raw = stream.readNBytes(4097);
            }
            if (raw.length > 4096)
                throw new IOException("Ban reason acknowledgement exceeds safe limit.");
            try
            {
                JsonObject doc = new JsonParser()
                    .parse(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!doc.get("ok").getAsBoolean() || !doc.get("saved").getAsBoolean() ||
                    !chapter.equals(doc.get("chapter").getAsString()) ||
                    !category.equals(doc.get("category").getAsString()))
                    throw new IOException("Ban reason acknowledgement mismatch.");
                String confirmed = doc.get("rsn").getAsString();
                if (FederationBanRegistry.validName(confirmed) == null)
                    throw new IOException("Unrecognized banned name in acknowledgement.");
                boolean posted = doc.get("published").getAsBoolean();
                String url = posted ? doc.get("discord_url").getAsString() : "";
                if (posted && !url.matches(
                    "https://discord\\.com/channels/" + GUILD_ID + "/" +
                    CHANNEL_ID + "/[0-9]{15,22}"))
                    throw new IOException("Invalid Servo-Skull message destination.");
                String message = posted
                    ? "Ban reason saved. Servo-Skull posted to #ban-list."
                    : publish
                      ? "Ban reason saved privately. Servo-Skull could not confirm a Discord post."
                      : "Ban reason saved privately. Nothing was posted to Discord.";
                return new Result(confirmed,posted,url,message);
            }
            catch (RuntimeException bad)
            {
                throw new IOException("Invalid ban reason response.",bad);
            }
        }
    }
}
