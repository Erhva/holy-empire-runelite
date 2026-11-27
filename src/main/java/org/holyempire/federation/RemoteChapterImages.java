/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import net.runelite.client.util.Filepath;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Public chapter art only: immutable SHA-256 URLs at the source-defined Holy Empire
 * origin. No downloaded URL, redirect, or unbounded image can influence requests.
 * File I/O, image decoding and HTTP must run on the background image worker.
 */
final class RemoteChapterImages
{
	static final int MAX_BYTES = 128 * 1024;
	static final int MAX_DIMENSION = 256;
	static final long RETRY_DELAY_MS = 60L * 60 * 1000;
	private static final byte[] PNG_SIGNATURE = {(byte)137, 80, 78, 71, 13, 10, 26, 10};

	private final OkHttpClient http;
	private final Filepath cacheDir;
	private final Map<String, Icon> verified = new ConcurrentHashMap<>();
	private final Map<String, Long> retryAfter = new HashMap<>();
	private volatile Map<String, String> active = Collections.emptyMap();

	RemoteChapterImages(OkHttpClient http, Filepath cacheDir)
	{
		this.http = http.newBuilder()
						.followRedirects(false)
						.followSslRedirects(false)
						.connectTimeout(5, TimeUnit.SECONDS)
						.readTimeout(8, TimeUnit.SECONDS)
						.build();
		this.cacheDir = cacheDir;
	}

	void useDirectory(ChapterDirectory directory)
	{
		Map<String, String> revisions = new HashMap<>();
		for (HolyEmpireChapterCatalog.Chapter chapter : directory.ordered)
		{
			if (chapter.iconSha256 != null && chapter.iconSha256.matches("[0-9a-f]{64}"))
			{
				revisions.put(chapter.name, chapter.iconSha256);
			}
		}
		active = Collections.unmodifiableMap(revisions);
	}

	Icon iconForHash(String digest)
	{
		return digest == null ? null : verified.get(digest);
	}

	/** One fetch for a given digest, even if several chapters reuse the artwork. */
	boolean loadMissing()
	{
		boolean changed = false;
		Set<String> digests = new HashSet<>(active.values());
		for (String digest : digests)
		{
			if (verified.containsKey(digest))
			{
				continue;
			}
			long now = System.currentTimeMillis();
			if (now < retryAfter.getOrDefault(digest, 0L))
			{
				continue;
			}
			try
			{
				byte[] bytes = loadDisk(digest);
				if (bytes == null)
				{
					bytes = download(digest);
					persist(digest, bytes);
				}
				verified.put(digest, decode(bytes));
				retryAfter.remove(digest);
				changed = true;
			}
			catch (Exception invalid)
			{
				retryAfter.put(digest, now + RETRY_DELAY_MS);
				org.slf4j.LoggerFactory.getLogger(RemoteChapterImages.class)
					.debug("Unable to load verified chapter artwork {}; using bundled icon",
						   digest);
			}
		}
		return changed;
	}

	static boolean isAllowedImageOrigin(URL url)
	{
		if ("https".equalsIgnoreCase(url.getProtocol()) &&
			"roster.holyempireosrs.cc".equalsIgnoreCase(url.getHost()) &&
			(url.getPort() == -1 || url.getPort() == 443))
		{
			return true;
		}
		return false;
	}

	private byte[] download(String digest) throws IOException
	{
		// The URI suffix is built locally using a validated digest, never a URL
		// obtained from a remote chapter record.
		URL url = CentralRosterClient.url(RosterEndpoint.baseUrl(), "/v2/icons/" + digest + ".png");
		if (!isAllowedImageOrigin(url))
		{
			throw new IOException("Chapter artwork must stay on the Holy Empire origin");
		}
		Request request = new Request.Builder()
							  .url(url)
							  .get()
							  .header("Accept", "image/png")
							  .header("User-Agent", "HolyEmpire-RuneLite/0.5")
							  .build();
		try (Response response = http.newCall(request).execute())
		{
			if (response.code() != 200 || response.body() == null)
			{
				throw new IOException("Chapter artwork unavailable: HTTP " + response.code());
			}
			ResponseBody body = response.body();
			if (body.contentLength() > MAX_BYTES || body.contentType() == null ||
				!"image/png".equals(body.contentType().toString().split(";")[0]))
			{
				throw new IOException("Unexpected chapter artwork type or length");
			}
			try (InputStream input = body.byteStream())
			{
				byte[] bytes = input.readNBytes(MAX_BYTES + 1);
				if (bytes.length > MAX_BYTES || !matchesDigest(bytes, digest))
				{
					throw new IOException("Chapter artwork failed integrity check");
				}
				decode(bytes); // Validate before persisting any remotely supplied image.
				return bytes;
			}
		}
	}

	private static boolean matchesDigest(byte[] bytes, String digest) throws IOException
	{
		try
		{
			byte[] actual = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder value = new StringBuilder();
			for (byte b : actual)
			{
				value.append(String.format(Locale.ROOT, "%02x", b & 255));
			}
			return value.toString().equals(digest);
		}
		catch (java.security.NoSuchAlgorithmException impossible)
		{
			throw new IOException("SHA-256 unavailable", impossible);
		}
	}

	static Icon decode(byte[] bytes) throws IOException
	{
		if (bytes.length < 33 || bytes.length > MAX_BYTES ||
			!Arrays.equals(Arrays.copyOf(bytes, PNG_SIGNATURE.length), PNG_SIGNATURE) ||
			bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R')
		{
			throw new IOException("Chapter image must be a bounded PNG");
		}
		int width = ByteBuffer.wrap(bytes, 16, 4).getInt();
		int height = ByteBuffer.wrap(bytes, 20, 4).getInt();
		if (width < 1 || width > MAX_DIMENSION || height < 1 || height > MAX_DIMENSION)
		{
			throw new IOException("Chapter image dimensions exceed limit");
		}
		BufferedImage png = ImageIO.read(new ByteArrayInputStream(bytes));
		if (png == null || png.getWidth() != width || png.getHeight() != height)
		{
			throw new IOException("Unreadable chapter PNG");
		}
		BufferedImage square = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = square.createGraphics();
		double ratio = Math.min(1.0, Math.min(32.0 / width, 32.0 / height));
		int scaledWidth = Math.max(1, (int)Math.round(width * ratio));
		int scaledHeight = Math.max(1, (int)Math.round(height * ratio));
		graphics.drawImage(png, (32 - scaledWidth) / 2, (32 - scaledHeight) / 2, scaledWidth,
						   scaledHeight, null);
		graphics.dispose();
		return new ImageIcon(square);
	}

	private byte[] loadDisk(String digest) throws IOException
	{
		Filepath path = cacheDir.joinSegment(digest + ".png");
		if (!path.isFile() || path.size() > MAX_BYTES || path.size() < 33)
		{
			return null;
		}
		try
		{
			byte[] bytes;
			try (FileChannel channel =
					 path.openFileChannel(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))
			{
				if (channel.size() > MAX_BYTES || channel.size() < 33)
				{
					return null;
				}
				ByteBuffer buffer = ByteBuffer.allocate((int)channel.size());
				while (buffer.hasRemaining())
				{
					if (channel.read(buffer) < 0)
					{
						return null;
					}
				}
				bytes = buffer.array();
			}
			if (!matchesDigest(bytes, digest))
			{
				return null;
			}
			decode(bytes);
			return bytes;
		}
		catch (IOException invalid)
		{
			return null; // Corrupt local entries cannot bypass remote integrity checks.
		}
	}

	private void persist(String digest, byte[] bytes)
	{
		try
		{
			cacheDir.createDirectories();
			Filepath target = cacheDir.joinSegment(digest + ".png");
			Filepath temporary = cacheDir.createTempFile("holy-empire-icon-", ".tmp");
			try
			{
				temporary.write(bytes, StandardOpenOption.WRITE,
								StandardOpenOption.TRUNCATE_EXISTING);
				try
				{
					temporary.moveTo(target, StandardCopyOption.ATOMIC_MOVE,
									 StandardCopyOption.REPLACE_EXISTING);
				}
				catch (AtomicMoveNotSupportedException notAtomic)
				{
					temporary.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			finally
			{
				temporary.deleteIfExists();
			}
		}
		catch (IOException notWritable)
		{
			// The display still uses the verified in-memory image if the local
			// disk cache cannot be written. Never crash the RuneLite client.
		}
	}
}
