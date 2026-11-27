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
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import java.util.zip.GZIPOutputStream;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CentralRosterClientTest
{
	private HttpServer server;
	private String manifest;
	private byte[] compressed;
	private String digest;
	private String warningDigest;
	private byte[] warningCompressed;
	private long now;

	private static byte[] gzip(byte[] bytes) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream zip = new GZIPOutputStream(out))
		{
			zip.write(bytes);
		}
		return out.toByteArray();
	}

	private static String hex(byte[] bytes)
	{
		StringBuilder text = new StringBuilder();
		for (byte b : bytes)
		{
			text.append(String.format("%02x", b & 255));
		}
		return text.toString();
	}

	private void makeFixture(long timestamp, int count) throws Exception
	{
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", 1);
		root.addProperty("generated_at", timestamp);
		root.addProperty("group_count", count);
		JsonArray groups = new JsonArray();
		for (int i = 0; i < count; i++)
		{
			JsonObject group = new JsonObject();
			group.addProperty("id", WomDirectoryClient.GROUPS[i]);
			JsonArray records = new JsonArray();
			JsonObject member = new JsonObject();
			JsonObject player = new JsonObject();
			player.addProperty("displayName", "test" + i);
			member.add("player", player);
			member.addProperty("role", "owner");
			records.add(member);
			group.add("memberships", records);
			groups.add(group);
		}
		root.add("groups", groups);
		compressed = gzip(root.toString().getBytes(StandardCharsets.UTF_8));
		digest = hex(MessageDigest.getInstance("SHA-256").digest(compressed));
		JsonObject header = new JsonObject();
		header.addProperty("schema_version", 1);
		header.addProperty("generated_at", timestamp);
		header.addProperty("group_count", count);
		header.addProperty("path", "snapshots/" + digest + ".json.gz");
		header.addProperty("sha256", digest);
		header.addProperty("size", compressed.length);
		manifest = header.toString();
	}

	@Before public void setUp() throws Exception
	{
		now = System.currentTimeMillis() / 1000;
		makeFixture(now, WomDirectoryClient.GROUPS.length);
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/manifest.json", ex -> {
			byte[] bytes = manifest.getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, bytes.length);
			try (OutputStream out = ex.getResponseBody())
			{
				out.write(bytes);
			}
		});
		server.createContext("/v1/snapshots/", ex -> {
			byte[] body = warningDigest != null &&
								  ex.getRequestURI().getPath().equals("/v1/snapshots/" +
																	  warningDigest + ".json.gz")
							  ? warningCompressed
							  : compressed;
			ex.sendResponseHeaders(200, body.length);
			try (OutputStream out = ex.getResponseBody())
			{
				out.write(body);
			}
		});
		server.start();
	}

	private String base()
	{
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@After public void stop()
	{
		if (server != null)
		{
			server.stop(0);
		}
	}

	private void addWarningFixture(String... names) throws Exception
	{
		JsonObject feed = new JsonObject();
		feed.addProperty("schema_version", 1);
		feed.addProperty("kind", "nonmember-ban-warnings");
		feed.addProperty("generated_at", now);
		feed.addProperty("roster_sha256", digest);
		JsonArray hashes = new JsonArray();
		for (String name : names)
		{
			hashes.add(FederationPublicBanWarnings.hash(name));
		}
		feed.add("digests", hashes);
		warningCompressed = gzip(feed.toString().getBytes(StandardCharsets.UTF_8));
		warningDigest = hex(MessageDigest.getInstance("SHA-256").digest(warningCompressed));
		JsonObject header = new JsonParser().parse(manifest).getAsJsonObject();
		header.addProperty("warning_sha256", warningDigest);
		header.addProperty("warning_path", "snapshots/" + warningDigest + ".json.gz");
		header.addProperty("warning_size", warningCompressed.length);
		manifest = header.toString();
	}

	@Test
	public void verifiedWarningFeedNeverWarnsRegularMemberAboutRosteredNames() throws Exception
	{
		addWarningFixture("Outside", "test0");
		WomDirectoryClient.Snapshot snapshot =
			CentralRosterClient.fetch(base(), new OkHttpClient());
		assertTrue(snapshot.publicBanStatusAvailable);
		assertTrue(FederationPublicBanWarnings.matches("Outside", snapshot.publicBanHashes,
													   snapshot.members));
		assertFalse(FederationPublicBanWarnings.matches("test0", snapshot.publicBanHashes,
														snapshot.members));
	}

	@Test public void brokenWarningFeedDoesNotInvalidateThePublicRoster() throws Exception
	{
		addWarningFixture("Outside");
		warningCompressed[warningCompressed.length - 1] ^= 1;
		WomDirectoryClient.Snapshot snapshot =
			CentralRosterClient.fetch(base(), new OkHttpClient());
		assertFalse(snapshot.publicBanStatusAvailable);
		assertTrue(snapshot.eligible("test0"));
	}

	@Test public void fetchesAllGroupsFromOneManifest() throws Exception
	{
		WomDirectoryClient.Snapshot snapshot =
			CentralRosterClient.fetch(base(), new OkHttpClient());
		assertTrue(snapshot.eligible("test0"));
		assertTrue(snapshot.eligible("test15"));
		assertEquals(16, snapshot.chapters.size());
		assertEquals(now * 1000, snapshot.sourceTimestampMs);
	}

	@Test public void checksumMismatchRejectsSnapshot() throws Exception
	{
		compressed[compressed.length - 1] ^= 1;
		try
		{
			CentralRosterClient.fetch(base(), new OkHttpClient());
			fail("Expected checksum mismatch");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("integrity"));
		}
	}

	@Test public void oldManifestIsRejected() throws Exception
	{
		makeFixture(now - 2 * 24 * 3600, WomDirectoryClient.GROUPS.length);
		try
		{
			CentralRosterClient.fetch(base(), new OkHttpClient());
			fail("Must reject stale manifest");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("old"));
		}
	}

	@Test public void incompleteRosterIsRejected() throws Exception
	{
		makeFixture(now, 15);
		try
		{
			CentralRosterClient.fetch(base(), new OkHttpClient());
			fail("Missing group must fail");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("schema"));
		}
	}

	@Test public void nonlocalHttpIsRejected() throws Exception
	{
		try
		{
			CentralRosterClient.fetch("http://192.168.140.13:8765", new OkHttpClient());
			fail("HTTPS required");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("HTTPS"));
		}
	}

	@Test public void invalidBaseCredentialsAreRejected() throws Exception
	{
		try
		{
			CentralRosterClient.fetch("https://user:password@example.org", new OkHttpClient());
			fail("URL credentials rejected");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("URL"));
		}
	}
}
