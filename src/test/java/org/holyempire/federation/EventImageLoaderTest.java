/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression tests for RuneLite Plugin Hub network-origin restrictions. */
public class EventImageLoaderTest
{
	private static final String EVENT = "234567890123456789";
	private static final String HASH = "abcdef0123456789abcdef0123456789";

	@Test public void acceptsOnlyFirstPartyPublicEventCovers()
	{
		String allowed =
			"https://roster.holyempireosrs.cc/v2/event-images/" + EVENT + "/" + HASH + ".png";
		assertTrue(EventImageLoader.approvedPublicUrl(allowed, EVENT));
		assertFalse(EventImageLoader.approvedPublicUrl(
			"https://cdn.discordapp.com/guild-events/" + EVENT + "/" + HASH + ".png", EVENT));
		assertFalse(EventImageLoader.approvedPublicUrl(
			"https://roster.holyempireosrs.cc.evil.example/v2/event-images/" + EVENT + "/" + HASH +
				".png",
			EVENT));
		assertFalse(EventImageLoader.approvedPublicUrl(allowed + "?redirect=1", EVENT));
		assertFalse(EventImageLoader.approvedPublicUrl(allowed, "999999999999999999"));
	}

	@Test public void rejectsMalformedOrMissingImageOrigins()
	{
		assertFalse(EventImageLoader.approvedPublicUrl(null, EVENT));
		assertFalse(EventImageLoader.approvedPublicUrl("", EVENT));
		assertFalse(EventImageLoader.approvedPublicUrl(
			"https://roster.holyempireosrs.cc/v2/event-images/" + EVENT + "/../image.png", EVENT));
	}

	@Test public void firstPartyImageIsDecodedAsynchronouslyUsingInjectedOkHttp() throws Exception
	{
		BufferedImage original = new BufferedImage(3, 4, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		assertTrue(ImageIO.write(original, "png", stream));
		byte[] png = stream.toByteArray();
		OkHttpClient http =
			new OkHttpClient.Builder()
				.addInterceptor(chain
								-> new Response.Builder()
									   .request(chain.request())
									   .protocol(Protocol.HTTP_1_1)
									   .code(200)
									   .message("OK")
									   .body(ResponseBody.create(MediaType.parse("image/png"), png))
									   .build())
				.build();
		EventImageLoader loader = new EventImageLoader(http, () -> null);
		try
		{
			String url =
				"https://roster.holyempireosrs.cc/v2/event-images/" + EVENT + "/" + HASH + ".png";
			DiscordEventsClient.Event event = new DiscordEventsClient.Event(
				"empire", "", EVENT, "Test", "", "scheduled", "", Instant.now(),
				Instant.now().plusSeconds(1800),
				"https://discord.com/events/123456789012345678/" + EVENT, url);
			CountDownLatch finished = new CountDownLatch(1);
			AtomicReference<ImageIcon> result = new AtomicReference<>();
			loader.load(event, icon -> {
				result.set(icon);
				finished.countDown();
			});
			assertTrue("Async image callback should complete", finished.await(5, TimeUnit.SECONDS));
			assertNotNull("Image must decode successfully", result.get());
			assertEquals(3, result.get().getIconWidth());
			assertEquals(4, result.get().getIconHeight());
		}
		finally
		{
			loader.close();
		}
	}
}
