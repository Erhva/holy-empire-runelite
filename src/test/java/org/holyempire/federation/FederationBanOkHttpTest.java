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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationBanOkHttpTest
{
	private HttpServer server;
	private final AtomicReference<String> capturedAuthorization = new AtomicReference<>();
	private final AtomicReference<String> capturedBody = new AtomicReference<>();
	private final AtomicInteger requestCount = new AtomicInteger();
	private volatile int responseCode = 200;
	private volatile String retryAfter = null;
	private volatile String responseBody =
		"{\"ok\":true,\"chapter\":\"Holy Chaos\",\"count\":2,\"revision\":1}";

	private FederationBanRegistry.Snapshot snapshot()
	{
		return new FederationBanRegistry.Snapshot("Holy Chaos", "2026-11-18T12:00:00Z",
												  "LIVE_WIDGET",
												  Arrays.asList("ExampleOne", "ExampleTwo"));
	}

	@Before public void start() throws IOException
	{
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/ban-sync", exchange -> {
			requestCount.incrementAndGet();
			capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			capturedBody.set(
				new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			if (retryAfter != null)
			{
				exchange.getResponseHeaders().add("Retry-After", retryAfter);
			}
			if (responseCode >= 300 && responseCode < 400)
			{
				exchange.getResponseHeaders().add("Location", "/redirect-target");
			}
			byte[] reply = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(responseCode, reply.length);
			try (java.io.OutputStream out = exchange.getResponseBody())
			{
				out.write(reply);
			}
		});
		server.createContext("/redirect-target", exchange -> {
			requestCount.incrementAndGet();
			capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			try (java.io.OutputStream out = exchange.getResponseBody())
			{
				out.write(bytes);
			}
		});
		server.start();
		System.setProperty("holyempire.testBanSyncOrigin", base());
	}

	private String base()
	{
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@After public void stop()
	{
		System.clearProperty("holyempire.testBanSyncOrigin");
		if (server != null)
		{
			server.stop(0);
		}
	}

	@Test public void injectedOkHttpUploadsExplicitCaptureOnly() throws Exception
	{
		String msg = FederationBanUploadClient.send(
			snapshot(), base(), "test_officer_credential_minimum_24_chars", new OkHttpClient());
		assertTrue(msg.contains("Synced 2 bans"));
		assertEquals("Bearer test_officer_credential_minimum_24_chars",
					 capturedAuthorization.get());
		assertTrue(capturedBody.get().contains("\"chapter\":\"Holy Chaos\""));
		assertFalse(capturedBody.get().contains("test_officer_credential"));
		assertEquals(1, requestCount.get());
	}

	@Test public void successfulSyncConfirmsRemovedBanCount() throws Exception
	{
		responseBody =
			"{\"ok\":true,\"chapter\":\"Holy Chaos\",\"count\":2," +
			"\"revision\":4,\"removed_bans\":10,\"new_bans\":[]}";
		FederationBanUploadClient.Receipt ack = FederationBanUploadClient.sendReceipt(
			snapshot(),base(),"test_officer_credential_minimum_24_chars",
			new OkHttpClient());
		assertEquals(4,ack.revision);
		assertTrue("Admins must see that previously banned players were removed",
			ack.message.contains("Removed 10"));
		assertTrue(ack.newBans.isEmpty());
	}

	@Test public void upload429ExplainsRetryAndUnchangedRegistry() throws Exception
	{
		responseCode = 429;
		retryAfter = "91";
		responseBody = "{}";
		try
		{
			FederationBanUploadClient.send(
				snapshot(), base(), "test_officer_credential_minimum_24_chars", new OkHttpClient());
			fail("Rate-limited uploads must not claim success");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("HTTP 429"));
			assertTrue(expected.getMessage().contains("2 minutes"));
			assertTrue(expected.getMessage().contains("did not change"));
		}
		assertEquals(1, requestCount.get());
	}

	@Test public void uploadRejectsCrossOriginAndSameOriginRedirects() throws Exception
	{
		responseCode = 307;
		try
		{
			FederationBanUploadClient.send(
				snapshot(), base(), "test_officer_credential_minimum_24_chars", new OkHttpClient());
			fail("Redirect must be rejected without forwarding credentials");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("307"));
		}
		assertEquals(1, requestCount.get());
	}

	@Test public void oversizedAcknowledgementIsRejected() throws Exception
	{
		responseBody = "x".repeat(2100);
		try
		{
			FederationBanUploadClient.send(
				snapshot(), base(), "test_officer_credential_minimum_24_chars", new OkHttpClient());
			fail("Oversized server response should be rejected");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("too large"));
		}
	}

	@Test public void unapprovedHttpEndpointsCannotReceiveOfficerCredential() throws Exception
	{
		try
		{
			FederationBanUploadClient.send(snapshot(), "http://127.0.0.1:8123",
										   "test_officer_credential_minimum_24_chars",
										   new OkHttpClient());
			fail("Unapproved origin should fail before network call");
		}
		catch (IllegalArgumentException expected)
		{
			assertTrue(expected.getMessage().contains("HTTPS"));
		}
		assertEquals(0, requestCount.get());
	}
}
