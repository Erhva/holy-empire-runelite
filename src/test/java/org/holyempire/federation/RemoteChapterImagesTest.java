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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Icon;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Chapter images can change with CDN data without arbitrary URLs or repeated downloads. */
public class RemoteChapterImagesTest
{
	@Rule public TemporaryFolder temp = new TemporaryFolder();
	private HttpServer server;
	private String previousProperty;
	private final AtomicInteger requests = new AtomicInteger();

	private static String digest(byte[] bytes) throws Exception
	{
		StringBuilder hex = new StringBuilder();
		for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
		{
			hex.append(String.format(Locale.ROOT, "%02x", b & 255));
		}
		return hex.toString();
	}

	private static byte[] bundled(String key) throws IOException
	{
		try (InputStream in = RemoteChapterImagesTest.class.getResourceAsStream("/chapter-icons/" +
																				key + ".png"))
		{
			assertNotNull(in);
			return in.readAllBytes();
		}
	}

	private ChapterDirectory directoryWithDigest(String sha) throws Exception
	{
		JsonArray records = ChapterDirectory.legacy().toJson();
		records.get(0).getAsJsonObject().addProperty("icon_sha256", sha);
		return ChapterDirectory.parse(records, 16);
	}

	private void startServer(byte[] response, String type, int code) throws Exception
	{
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v2/icons/", exchange -> {
			requests.incrementAndGet();
			if (type != null)
			{
				exchange.getResponseHeaders().set("Content-Type", type);
			}
			exchange.sendResponseHeaders(code, code == 302 ? -1 : response.length);
			if (code != 302)
			{
				try (OutputStream out = exchange.getResponseBody())
				{
					out.write(response);
				}
			}
			else
			{
				exchange.close();
			}
		});
		server.start();
		previousProperty = System.getProperty("holyempire.rosterBaseUrl");
		System.setProperty("holyempire.rosterBaseUrl",
						   "http://127.0.0.1:" + server.getAddress().getPort());
	}

	private Filepath cache()
	{
		return Filepath.Unchecked.getRooted(temp.getRoot().toPath())
			.joinSegment("public-chapter-art");
	}

	@After public void cleanup()
	{
		HolyEmpireAssets.useRemoteChapterImages(null);
		if (previousProperty == null)
		{
			System.clearProperty("holyempire.rosterBaseUrl");
		}
		else
		{
			System.setProperty("holyempire.rosterBaseUrl", previousProperty);
		}
		if (server != null)
		{
			server.stop(0);
		}
	}

	@Test public void fetchesExactlyOnceAndSurvivesRestartWithoutHttp() throws Exception
	{
		byte[] png = bundled("holy-empire");
		String sha = digest(png);
		startServer(png, "image/png", 200);
		RemoteChapterImages first = new RemoteChapterImages(new OkHttpClient(), cache());
		first.useDirectory(directoryWithDigest(sha));
		assertNull(first.iconForHash(sha));
		assertTrue(first.loadMissing());
		assertEquals(1, requests.get());
		assertEquals(32, first.iconForHash(sha).getIconWidth());
		assertFalse(first.loadMissing());
		assertEquals(1, requests.get());

		RemoteChapterImages afterRestart = new RemoteChapterImages(new OkHttpClient(), cache());
		afterRestart.useDirectory(directoryWithDigest(sha));
		assertTrue(afterRestart.loadMissing());
		assertEquals(1, requests.get()); // Retrieved from verified on-disk cache.
		HolyEmpireAssets.useRemoteChapterImages(afterRestart);
		assertEquals(32, HolyEmpireAssets.chapterIcon("holy-empire", sha).getIconWidth());
	}

	@Test public void rejectsWrongDigestAndUsesBundledFallback() throws Exception
	{
		byte[] correct = bundled("holy-empire");
		byte[] substituted = bundled("holy-chaos");
		String sha = digest(correct);
		startServer(substituted, "image/png", 200);
		RemoteChapterImages images = new RemoteChapterImages(new OkHttpClient(), cache());
		images.useDirectory(directoryWithDigest(sha));
		assertFalse(images.loadMissing());
		assertNull(images.iconForHash(sha));
		HolyEmpireAssets.useRemoteChapterImages(images);
		assertEquals(32, HolyEmpireAssets.chapterIcon("holy-empire", sha).getIconWidth());
		assertEquals(1, requests.get());
		assertFalse(images.loadMissing()); // Bounded retry backoff.
		assertEquals(1, requests.get());
		assertFalse(cache().joinSegment(sha + ".png").isFile());
	}

	@Test public void refusesCrossHostRedirectAndInvalidHashes() throws Exception
	{
		byte[] png = bundled("holy-empire");
		String sha = digest(png);
		startServer(png, "image/png", 302);
		RemoteChapterImages images = new RemoteChapterImages(new OkHttpClient(), cache());
		images.useDirectory(directoryWithDigest(sha));
		assertFalse(images.loadMissing());
		assertEquals(1, requests.get());
		JsonArray directory = ChapterDirectory.legacy().toJson();
		directory.get(0).getAsJsonObject().addProperty("icon_sha256",
													   "https://other-domain.example/bad.png");
		try
		{
			ChapterDirectory.parse(directory, 16);
			fail("A chapter directory must not supply arbitrary image URLs");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("icon_sha256"));
		}
	}

	@Test public void networkHostsAreFixedToOwnDomainAndLoopbackForTests() throws Exception
	{
		assertTrue(RemoteChapterImages.isAllowedImageOrigin(
			new java.net.URL("https://roster.holyempireosrs.cc/v2/icons/test.png")));
		assertTrue(RemoteChapterImages.isAllowedImageOrigin(
			new java.net.URL("http://127.0.0.1:9000/v2/icons/test.png")));
		assertFalse(RemoteChapterImages.isAllowedImageOrigin(
			new java.net.URL("https://example.org/v2/icons/test.png")));
		assertFalse(RemoteChapterImages.isAllowedImageOrigin(
			new java.net.URL("http://roster.holyempireosrs.cc/v2/icons/test.png")));
		assertFalse(RemoteChapterImages.isAllowedImageOrigin(
			new java.net.URL("https://roster.holyempireosrs.cc:8443/v2/icons/test.png")));
	}

	@Test public void enforcesImageDimensionsAndMaximumByteSize() throws Exception
	{
		byte[] png = bundled("holy-empire");
		byte[] oversized = new byte[RemoteChapterImages.MAX_BYTES + 1];
		try
		{
			RemoteChapterImages.decode(oversized);
			fail("Oversized PNG must be rejected");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("PNG"));
		}
		byte[] dimensions = png.clone();
		dimensions[16] = 0;
		dimensions[17] = 0;
		dimensions[18] = 3;
		dimensions[19] = 0; // 768px width.
		try
		{
			RemoteChapterImages.decode(dimensions);
			fail("Oversized dimensions must be rejected before decoding");
		}
		catch (IOException expected)
		{
			assertTrue(expected.getMessage().contains("dimensions"));
		}
	}
}
