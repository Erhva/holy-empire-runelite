/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * First-party image previews only. The event record can never select the
 * network destination. Private images require the current Discord session;
 * only the isolated test client can use its separate preview credential.
 */
final class EventImageLoader
{
	// The Discord editor accepts images up to 5 MiB; decoding remains
	// limited separately by MAX_PIXELS.
	private static final int MAX_BYTES = 5 * 1024 * 1024;
	private static final int MAX_PIXELS = 4_000_000;
	private static final int CACHE_LIMIT = 12;
	private static final String PUBLIC_ORIGIN = "https://roster.holyempireosrs.cc";
	private final OkHttpClient http;
	private final Supplier<ChapterEventsClient.MemberSession> currentMember;
	private final ThreadPoolExecutor workers;

	private static final class CachedImage
	{
		final ImageIcon icon;
		final long expiresAt;

		CachedImage(ImageIcon icon, long expiresAt)
		{
			this.icon = icon;
			this.expiresAt = expiresAt;
		}
	}

	private final Map<String, CachedImage> cache =
		new LinkedHashMap<String, CachedImage>(16, .75f, true) {
			@Override protected boolean removeEldestEntry(Map.Entry<String, CachedImage> entry)
			{
				return size() > CACHE_LIMIT;
			}
		};
	private final Map<String, List<Consumer<ImageIcon>>> pending = new HashMap<>();

	EventImageLoader(OkHttpClient http, Supplier<ChapterEventsClient.MemberSession> currentMember)
	{
		this.http = http.newBuilder()
						.followRedirects(false)
						.followSslRedirects(false)
						.connectTimeout(5, TimeUnit.SECONDS)
						.readTimeout(8, TimeUnit.SECONDS)
						.build();
		this.currentMember = currentMember;
		this.workers = new ThreadPoolExecutor(
			2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(24), task -> {
				Thread thread = new Thread(task, "holy-empire-event-image");
				thread.setDaemon(true);
				return thread;
			}, new ThreadPoolExecutor.AbortPolicy());
	}

	static boolean approvedPublicUrl(String url, String eventId)
	{
		if (url == null || eventId == null || !eventId.matches("[0-9]{5,22}"))
		{
			return false;
		}
		String suffix = "/v2/event-images/" + eventId + "/(?:a_)?[0-9a-f]{32}[.]png";
		if (url.matches(java.util.regex.Pattern.quote(PUBLIC_ORIGIN) + suffix))
		{
			return true;
		}
		return ChapterEventsClient.isDevTestMode() &&
			url.matches(java.util.regex.Pattern.quote(ChapterEventsClient.origin()) + suffix);
	}

	private static String cacheKey(String url, String bearer)
	{
		if (bearer == null)
		{
			return url;
		}
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(
				bearer.getBytes(StandardCharsets.US_ASCII));
			return url + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException failure)
		{
			throw new IllegalStateException("SHA-256 unavailable", failure);
		}
	}

	private static final class ImageSource
	{
		final String url;
		final String bearer;
		final String cacheKey;

		ImageSource(String url, String bearer, String cacheKey)
		{
			this.url = url;
			this.bearer = bearer;
			this.cacheKey = cacheKey;
		}
	}

	private ImageSource source(DiscordEventsClient.Event event) throws IOException
	{
		if ("empire".equals(event.scope))
		{
			if (!approvedPublicUrl(event.imageUrl, event.id))
			{
				throw new IOException("Public image must be on Holy Empire R2");
			}
			String bearer = null;
			if (ChapterEventsClient.isDevTestMode() &&
				event.imageUrl.startsWith(ChapterEventsClient.origin() + "/"))
			{
				bearer = ChapterEventsClient.devPreviewCredential();
			}
			return new ImageSource(event.imageUrl, bearer, cacheKey(event.imageUrl, bearer));
		}
		if (!"chapter".equals(event.scope) || !event.id.matches("[0-9a-f]{32}") ||
			event.imageUrl.isEmpty())
		{
			throw new IOException("Invalid chapter image reference");
		}
		String origin = ChapterEventsClient.origin();
		if (!ChapterEventsClient.acceptedChapterOrigin(origin))
		{
			throw new IOException("Untrusted chapter image origin");
		}
		ChapterEventsClient.MemberSession member = currentMember.get();
		String bearer;
		if (member != null && member.valid(event.chapter))
		{
			bearer = member.token;
		}
		else if (ChapterEventsClient.isDevTestMode())
		{
			bearer = ChapterEventsClient.devPreviewCredential();
		}
		else
		{
			throw new IOException("Discord membership required to load chapter image");
		}
		// The server independently checks event ID, chapter, and bearer.
		String url = origin + "/v1/chapter-events/image/" + event.id;
		return new ImageSource(url, bearer, cacheKey(url, bearer));
	}

	void load(DiscordEventsClient.Event event, Consumer<ImageIcon> completion)
	{
		ImageSource source;
		try
		{
			source = source(event);
		}
		catch (IOException failure)
		{
			SwingUtilities.invokeLater(() -> completion.accept(null));
			return;
		}
		CachedImage existing;
		synchronized (cache)
		{
			existing = cache.get(source.cacheKey);
			if (existing != null && System.nanoTime() >= existing.expiresAt)
			{
				cache.remove(source.cacheKey);
				existing = null;
			}
		}
		if (existing != null)
		{
			ImageIcon cached = existing.icon;
			SwingUtilities.invokeLater(() -> completion.accept(cached));
			return;
		}
		synchronized (pending)
		{
			if (pending.containsKey(source.cacheKey))
			{
				pending.get(source.cacheKey).add(completion);
				return;
			}
			pending.put(source.cacheKey, new ArrayList<>());
			pending.get(source.cacheKey).add(completion);
		}
		try
		{
			workers.execute(() -> {
				ImageIcon image;
				try
				{
					image = fetch(source);
				}
				catch (IOException | RuntimeException failure)
				{
					image = null;
				}
				// Private images must not remain cached indefinitely across
				// chapter moves or privilege changes. Unavailable images get
				// a short negative cache to avoid repeated Discord requests.
				long seconds = image == null ? 15 : source.bearer != null ? 30 : 900;
				synchronized (cache)
				{
					cache.put(source.cacheKey,
							  new CachedImage(image, System.nanoTime() +
														 TimeUnit.SECONDS.toNanos(seconds)));
				}
				List<Consumer<ImageIcon>> listeners;
				synchronized (pending)
				{
					listeners = pending.remove(source.cacheKey);
				}
				if (listeners != null)
				{
					ImageIcon result = image;
					SwingUtilities.invokeLater(() -> {
						for (Consumer<ImageIcon> listener : listeners)
						{
							listener.accept(result);
						}
					});
				}
			});
		}
		catch (RejectedExecutionException rejected)
		{
			List<Consumer<ImageIcon>> listeners;
			synchronized (pending)
			{
				listeners = pending.remove(source.cacheKey);
			}
			if (listeners != null)
			{
				SwingUtilities.invokeLater(() -> {
					for (Consumer<ImageIcon> listener : listeners)
					{
						listener.accept(null);
					}
				});
			}
		}
	}

	private ImageIcon fetch(ImageSource source) throws IOException
	{
		Request.Builder request = new Request.Builder()
									  .url(source.url)
									  .get()
									  .header("Accept", "image/png,image/jpeg,image/gif");
		if (source.bearer != null)
		{
			request.header("Authorization", "Bearer " + source.bearer);
		}
		try (Response response = http.newCall(request.build()).execute())
		{
			if (response.code() != 200 || response.body() == null)
			{
				return null;
			}
			ResponseBody body = response.body();
			if (body.contentLength() > MAX_BYTES || body.contentType() == null ||
				!body.contentType().toString().matches("(?i)image/(png|jpeg|gif)(;.*)?"))
			{
				return null;
			}
			byte[] raw;
			try (InputStream input = body.byteStream())
			{
				raw = input.readNBytes(MAX_BYTES + 1);
			}
			if (raw.length > MAX_BYTES)
			{
				return null;
			}
			// Use memory only; ImageIO's default factory can allocate
			// temporary files and should not perform RuneLite plugin disk IO.
			try (ImageInputStream in =
					 new MemoryCacheImageInputStream(new ByteArrayInputStream(raw)))
			{
				if (in == null)
				{
					return null;
				}
				java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
				if (!readers.hasNext())
				{
					return null;
				}
				ImageReader reader = readers.next();
				try
				{
					reader.setInput(in, true, true);
					int width = reader.getWidth(0);
					int height = reader.getHeight(0);
					if (width < 1 || height < 1 || (long)width * height > MAX_PIXELS)
					{
						return null;
					}
					BufferedImage image = reader.read(0);
					if (image == null)
					{
						return null;
					}
					double scale = Math.min(1.0, Math.min(580.0 / width, 500.0 / height));
					Image thumbnail = image.getScaledInstance(
						Math.max(1, (int)Math.round(width * scale)),
						Math.max(1, (int)Math.round(height * scale)), Image.SCALE_SMOOTH);
					return new ImageIcon(thumbnail);
				}
				finally
				{
					reader.dispose();
				}
			}
		}
	}

	void close()
	{
		workers.shutdownNow();
		synchronized (cache)
		{
			cache.clear();
		}
		synchronized (pending)
		{
			pending.clear();
		}
	}
}
