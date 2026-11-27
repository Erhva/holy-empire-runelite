/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FederationOfficerEnrollmentClientTest
{
	@Rule public TemporaryFolder folder = new TemporaryFolder();
	private HttpServer server;

	@Test
	public void locallyGeneratedCredentialIsNotSentAndPersistsOnlyOnApproval() throws Exception
	{
		AtomicReference<String> observedRequest = new AtomicReference<>("");
		AtomicReference<String> observedStatus = new AtomicReference<>("");
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/officer-enroll/", exchange -> {
			String request =
				new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String path = exchange.getRequestURI().getPath();
			String response;
			if (path.endsWith("/request"))
			{
				observedRequest.set(request);
				response = "{\"id\":\"0123456789abcdef0123456789abcdef\","
						   + "\"ticket\":\"a_long_private_status_ticket_0123456789abcdef\","
						   + "\"url\":\"https://discord.com/oauth2/authorize?state=sample\","
						   + "\"expires_at\":1800000000}";
			}
			else if (path.endsWith("/status"))
			{
				observedStatus.set(request);
				response = "{\"status\":\"approved\"}";
			}
			else
			{
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (java.io.OutputStream output = exchange.getResponseBody())
			{
				output.write(bytes);
			}
		});
		server.start();
		String origin = FederationBanUploadClient.OFFICIAL_ENDPOINT;
		try
		{
			Path root = folder.getRoot().toPath();
			Filepath directory = Filepath.Unchecked.getRooted(root);
			FederationOfficerCredentialStore keys = new FederationOfficerCredentialStore(directory);
			FederationOfficerEnrollmentClient client =
				new FederationOfficerEnrollmentClient(testHttp(), directory, keys, origin);
			assertEquals("https://discord.com/oauth2/authorize?state=sample",
						 client.request("Officer One", "Holy Empire"));
			assertEquals("", keys.read("Officer One"));
			JsonObject request = new JsonParser().parse(observedRequest.get()).getAsJsonObject();
			assertEquals("Holy Empire", request.get("chapter").getAsString());
			assertTrue(request.get("token_sha256").getAsString().matches("[0-9a-f]{64}"));
			assertFalse(observedRequest.get().contains("\"token\":"));
			try
			{
				client.request("Officer One", "Holy Empire");
				fail("An existing pending local request must not be replaced");
			}
			catch (java.io.IOException expected)
			{
			}
			try
			{
				client.check("Other Officer", "Holy Empire", () -> true);
				fail("Account mixup must be rejected");
			}
			catch (java.io.IOException expected)
			{
			}
			try
			{
				client.check("Officer One", "Holy Empire", () -> false);
				fail("Loss of eligibility must block enrollment");
			}
			catch (java.io.IOException expected)
			{
				assertTrue(expected.getMessage().contains("eligibility"));
			}
			assertEquals("", observedStatus.get());
			assertTrue(client.check("Officer One", "Holy Empire", () -> true).contains("approved"));
			String savedKey = keys.read("Officer One");
			assertTrue(savedKey.matches("[A-Za-z0-9_-]{43}"));
			assertEquals(request.get("token_sha256").getAsString(),
						 FederationOfficerEnrollmentClient.sha256(savedKey));
			assertFalse(observedStatus.get().contains(savedKey));
			assertFalse(Files.exists(
				root.resolve("pending-officer-" +
							 FederationOfficerEnrollmentClient.sha256("officer one") + ".json")));
		}
		finally
		{
			server.stop(0);
		}
	}

	/** Route only test HTTP traffic to the local fixture server. Production URLs remain pinned. */
	private OkHttpClient testHttp()
	{
		return new OkHttpClient.Builder().addInterceptor(chain -> {
			okhttp3.Request request = chain.request();
			okhttp3.HttpUrl local = request.url().newBuilder()
				.scheme("http").host("127.0.0.1").port(server.getAddress().getPort()).build();
			return chain.proceed(request.newBuilder().url(local).build());
		}).build();
	}

	@Test public void awaitingGameDisplaysPendingReview() throws Exception
	{
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/officer-enroll/", exchange -> {
			String path = exchange.getRequestURI().getPath();
			String response;
			if (path.endsWith("/request"))
			{
				response = "{\"id\":\"0123456789abcdef0123456789abcdef\","
						   + "\"ticket\":\"another_private_status_ticket_0123456789abcdef\","
						   + "\"url\":\"https://discord.com/oauth2/authorize?state=sample\","
						   + "\"expires_at\":1800000000}";
			}
			else if (path.endsWith("/status"))
			{
				response = "{\"status\":\"awaiting_game\"}";
			}
			else
			{
				exchange.sendResponseHeaders(404, -1);
				exchange.close();
				return;
			}
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (java.io.OutputStream output = exchange.getResponseBody())
			{
				output.write(bytes);
			}
		});
		server.start();
		String origin = FederationBanUploadClient.OFFICIAL_ENDPOINT;
		try
		{
			Filepath directory = Filepath.Unchecked.getRooted(folder.getRoot().toPath());
			FederationOfficerEnrollmentClient client = new FederationOfficerEnrollmentClient(
				testHttp(), directory, new FederationOfficerCredentialStore(directory),
				origin);
			client.request("Officer One", "Holy Empire");
			assertEquals("Pending Review", client.check("Officer One", "Holy Empire", () -> true));
		}
		finally
		{
			server.stop(0);
		}
	}

	@Test public void tokenFormatAndOfficialOriginArePinned() throws Exception
	{
		String one = FederationOfficerEnrollmentClient.randomCredential();
		String two = FederationOfficerEnrollmentClient.randomCredential();
		assertNotEquals(one, two);
		assertTrue(one.matches("[A-Za-z0-9_-]{43}"));
		assertEquals(64, FederationOfficerEnrollmentClient.sha256(one).length());
		Filepath root = Filepath.Unchecked.getRooted(folder.getRoot().toPath());
		try
		{
			new FederationOfficerEnrollmentClient(testHttp(), root,
												  new FederationOfficerCredentialStore(root),
												  "https://attacker.example");
			fail("Arbitrary enrollment servers are forbidden");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}
}
