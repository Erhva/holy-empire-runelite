/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import org.junit.*;
import static org.junit.Assert.*;

public class FederationBanDirectoryClientTest
{
	private HttpServer server;
	private volatile int responseCode = 200;
	private volatile String body =
		"{\"schema_version\":1,\"kind\":\"ban-list\","
		+ "\"snapshots\":[{\"chapter\":\"Holy Chaos\",\"captured_at\":1795000000,\"revision\":1}],"
		+ "\"entries\":[{\"chapter\":\"Holy Chaos\",\"current_name\":\"Example One\","
		+ "\"identity_status\":\"tracked\",\"captured_at\":1795000000}]}";
	private final AtomicInteger received = new AtomicInteger();
	private final AtomicReference<String> path = new AtomicReference<>();
	private final AtomicReference<String> posted = new AtomicReference<>();
	private final AtomicReference<String> authorization = new AtomicReference<>();
	private static final String TEST_TOKEN = "private-test-officer-credential-1234567890";

	@Before public void setup() throws Exception
	{
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/ban-check", exchange -> {
			received.incrementAndGet();
			path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
			posted.set(
				new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			if (responseCode == 302)
			{
				exchange.getResponseHeaders().set("Location", "/redirect");
			}
			byte[] data = body.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(responseCode, data.length);
			try (java.io.OutputStream out = exchange.getResponseBody())
			{
				out.write(data);
			}
		});
		server.createContext("/redirect", exchange -> {
			received.incrementAndGet();
			exchange.sendResponseHeaders(200, -1);
			exchange.close();
		});
		server.start();
		System.setProperty("holyempire.testBanSyncOrigin", url());
	}

	private String url()
	{
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private FederationBanDirectoryClient.Snapshot get() throws Exception
	{
		return FederationBanDirectoryClient.fetch("Holy Chaos", url(), TEST_TOKEN,
												  new OkHttpClient());
	}

	@After public void shutdown()
	{
		System.clearProperty("holyempire.testBanSyncOrigin");
		if (server != null)
		{
			server.stop(0);
		}
	}

	@Test
	public void privatePostReturnsCompleteChapterList() throws Exception
	{
		FederationBanDirectoryClient.Snapshot snapshot = get();
		assertEquals(1, snapshot.entries.size());
		assertEquals("Example One", snapshot.entries.get(0).name);
		assertEquals("Holy Chaos", snapshot.entries.get(0).chapter);
		assertEquals(1, snapshot.chapters.get("Holy Chaos").revision);
		assertEquals("POST /v1/ban-check", path.get());
		assertEquals("Bearer " + TEST_TOKEN, authorization.get());
		com.google.gson.JsonObject request =
			new com.google.gson.JsonParser().parse(posted.get()).getAsJsonObject();
		assertEquals("Holy Chaos", request.get("chapter").getAsString());
		assertEquals("list", request.get("mode").getAsString());
		assertEquals(2, request.entrySet().size());
		assertEquals(1, received.get());
	}

	@Test
	public void noBansIsValidForSyncedEmptySet() throws Exception
	{
		body = "{\"schema_version\":1,\"kind\":\"ban-list\",\"snapshots\":[],\"entries\":[]}";
		assertTrue(get().entries.isEmpty());
	}

	@Test
	public void unapprovedProductionHostCannotReceiveToken() throws Exception
	{
		try
		{
			FederationBanDirectoryClient.fetch("Holy Chaos", "https://bad.example.org", TEST_TOKEN,
											   new OkHttpClient());
			fail("Unapproved origin must be refused");
		}
		catch (IllegalArgumentException expected)
		{
		}
		assertEquals(0, received.get());
	}

	@Test
	public void redirectsCannotForwardPrivateToken() throws Exception
	{
		responseCode = 302;
		try
		{
			get();
			fail("Redirect must be rejected");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("302"));
		}
		assertEquals(1, received.get());
	}

	@Test
	public void invalidResponseAndOversizedDataFailClosed() throws Exception
	{
		body = "{\"schema_version\":1,\"kind\":\"ban-list\",\"snapshots\":[],"
			   + "\"entries\":[{\"chapter\":\"Holy Chaos\",\"current_name\":\"Unapproved One\","
			   + "\"identity_status\":\"tracked\",\"captured_at\":1795000000}]}";
		try
		{
			get();
			fail("Unknown entry chapter snapshot must not be trusted");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("Invalid"));
		}
		body = "x".repeat(FederationBanDirectoryClient.MAX_RESPONSE + 1);
		try
		{
			get();
			fail("Oversized data must fail");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("too large"));
		}
	}
}
