/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** V2 chapters are remote data, never additional hardcoded plugin group IDs or URLs. */
public class DynamicChapterDirectoryTest
{
	@Rule public TemporaryFolder temp = new TemporaryFolder();
	private HttpServer server;

	@After public void tearDown()
	{
		HolyEmpireChapterCatalog.update(ChapterDirectory.legacy());
		if (server != null)
		{
			server.stop(0);
		}
	}

	private JsonArray directory()
	{
		JsonArray chapters = ChapterDirectory.legacy().toJson();
		JsonObject added = new JsonObject();
		added.addProperty("group_id", 314159);
		added.addProperty("name", "New Legion");
		added.addProperty("clan_chat", "Legion CC");
		added.addProperty("icon_key", "new-legion");
		added.addProperty("access", "P2P only");
		added.addProperty("detail", "New chapter");
		added.addProperty("order", 17);
		chapters.add(added);
		return chapters;
	}

	private static byte[] gzip(String value) throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(bytes))
		{
			out.write(value.getBytes(StandardCharsets.UTF_8));
		}
		return bytes.toByteArray();
	}

	private static String sha256(byte[] data) throws Exception
	{
		byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
		StringBuilder text = new StringBuilder();
		for (byte b : digest)
		{
			text.append(String.format(Locale.ROOT, "%02x", b & 255));
		}
		return text.toString();
	}

	private String serveDirectory(JsonArray chapters) throws Exception
	{
		return serveDirectory(chapters, false);
	}

	private String serveDirectory(JsonArray chapters, boolean emptyNewChapter) throws Exception
	{
		long timestamp = System.currentTimeMillis() / 1000;
		JsonObject payload = new JsonObject();
		payload.addProperty("schema_version", 2);
		payload.addProperty("generated_at", timestamp);
		payload.addProperty("group_count", 17);
		payload.add("chapters", chapters);
		JsonArray groups = new JsonArray();
		for (HolyEmpireChapterCatalog.Chapter chapter :
			 ChapterDirectory.parse(chapters, 17).ordered)
		{
			if (!chapter.hasWomRoster())
			{
				continue;
			}
			JsonObject group = new JsonObject();
			group.addProperty("id", chapter.groupId);
			group.addProperty("clan", chapter.name);
			JsonArray memberships = new JsonArray();
			JsonObject row = new JsonObject();
			JsonObject player = new JsonObject();
			player.addProperty("displayName",
							   chapter.name.equals("New Legion") ? "LegionUser" : "LegacyUser");
			row.add("player", player);
			row.addProperty("role", "owner");
			if (!emptyNewChapter || !chapter.name.equals("New Legion"))
			{
				memberships.add(row);
			}
			group.add("memberships", memberships);
			groups.add(group);
		}
		payload.add("groups", groups);
		byte[] compressed = gzip(payload.toString());
		String digest = sha256(compressed);
		JsonObject manifest = new JsonObject();
		manifest.addProperty("schema_version", 2);
		manifest.addProperty("generated_at", timestamp);
		manifest.addProperty("group_count", 17);
		manifest.addProperty("path", "snapshots/" + digest + ".json.gz");
		manifest.addProperty("sha256", digest);
		manifest.addProperty("size", compressed.length);
		byte[] header = manifest.toString().getBytes(StandardCharsets.UTF_8);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v2/manifest.json", exchange -> {
			exchange.sendResponseHeaders(200, header.length);
			try (OutputStream out = exchange.getResponseBody())
			{
				out.write(header);
			}
		});
		server.createContext("/v2/snapshots/", exchange -> {
			exchange.sendResponseHeaders(200, compressed.length);
			try (OutputStream out = exchange.getResponseBody())
			{
				out.write(compressed);
			}
		});
		server.start();
		return "https://roster.holyempireosrs.cc";
	}

	/** Redirect only injected test-client traffic to the local fixture. */
	private OkHttpClient fixtureHttp()
	{
		return new OkHttpClient.Builder().addInterceptor(chain -> {
			okhttp3.HttpUrl url = chain.request().url().newBuilder()
				.scheme("http").host("127.0.0.1").port(server.getAddress().getPort()).build();
			return chain.proceed(chain.request().newBuilder().url(url).build());
		}).build();
	}

	@Test public void newChapterAndClanChatWorkWithoutCodeChange() throws Exception
	{
		String base = serveDirectory(directory());
		WomDirectoryClient.Snapshot result = CentralRosterClient.fetch(base, fixtureHttp());
		assertEquals(17, result.directory.groupCount());
		assertEquals(17, result.chapters.size());
		assertEquals("New Legion", result.members.get("legionuser").get(0));
		FederationSession session = new FederationSession();
		session.observe("LegionUser", "");
		assertTrue(session.publish("legionuser", session.generation(), result));
		assertTrue(session.isEligible());
		assertNotNull(HolyEmpireChapterCatalog.get("New Legion"));
		assertEquals("New Legion", InGameClanVerifier.recognizedChapter("Legion CC"));
		assertEquals("New Legion", InGameClanVerifier.recognizedChapter("New Legion"));
		assertNull(HolyEmpireChapterCatalog.get("Not A Chapter"));
	}

	@Test public void emptyNewChapterStillAppearsAndHasNoVerifiedMembers() throws Exception
	{
		WomDirectoryClient.Snapshot result =
			CentralRosterClient.fetch(serveDirectory(directory(), true), fixtureHttp());
		assertEquals(17, result.directory.groupCount());
		assertEquals(17, result.chapters.size());
		assertTrue(result.chapters.containsKey("New Legion"));
		assertTrue(result.chapters.get("New Legion").isEmpty());
		assertFalse(result.members.containsKey("legionuser"));
		FederationSession member = new FederationSession();
		member.observe("LegacyUser", "");
		assertTrue(member.publish("legacyuser", member.generation(), result));
		assertTrue(member.isEligible());
		assertNotNull(HolyEmpireChapterCatalog.get("New Legion"));
		assertEquals("New Legion", InGameClanVerifier.recognizedChapter("Legion CC"));
	}

	@Test public void newGroupSurvivesLocalCacheRestart() throws Exception
	{
		String base = serveDirectory(directory());
		WomDirectoryClient.Snapshot result = CentralRosterClient.fetch(base, fixtureHttp());
		Filepath path =
			Filepath.Unchecked.getRooted(temp.getRoot().toPath()).joinSegment("dynamic-cache.json");
		long now = System.currentTimeMillis();
		new WomSnapshotCache().get(now, path, () -> result);
		WomSnapshotCache.Result restored = new WomSnapshotCache().get(now + 1000, path, () -> {
			throw new IOException("The fresh cache must avoid an HTTP request");
		});
		assertEquals(17, restored.snapshot.directory.groupCount());
		assertEquals("New Legion", restored.snapshot.members.get("legionuser").get(0));
	}

	@Test public void rejectsInjectedUrlsAndDuplicateGroups() throws Exception
	{
		JsonArray chapters = directory();
		chapters.get(chapters.size() - 1)
			.getAsJsonObject()
			.addProperty("icon_key", "https://elsewhere.example");
		try
		{
			ChapterDirectory.parse(chapters, 17);
			fail("Never load URLs from a remote chapter directory");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("chapter"));
		}

		chapters = directory();
		chapters.get(chapters.size() - 1).getAsJsonObject().addProperty("group_id", 4586);
		try
		{
			ChapterDirectory.parse(chapters, 17);
			fail("Duplicate WOM identities must fail closed");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("Duplicate"));
		}
	}
}
