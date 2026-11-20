/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** Classpath-only art: rendering the sidebar never performs HTTP requests. */
final class HolyEmpireAssets
{
	private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
	private static final Icon MISSING =
		new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

	private HolyEmpireAssets()
	{
	}

	private static Icon load(String path)
	{
		return CACHE.computeIfAbsent(path, key -> {
			try (InputStream in = HolyEmpireAssets.class.getResourceAsStream(key))
			{
				if (in == null)
				{
					return MISSING;
				}
				BufferedImage png = ImageIO.read(in);
				if (png == null)
				{
					return MISSING;
				}
				if (key.startsWith("/chapter-icons/"))
				{
					BufferedImage square = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
					Graphics2D graphics = square.createGraphics();
					double ratio =
						Math.min(1.0, Math.min(32.0 / png.getWidth(), 32.0 / png.getHeight()));
					int width = Math.max(1, (int)Math.round(png.getWidth() * ratio));
					int height = Math.max(1, (int)Math.round(png.getHeight() * ratio));
					graphics.drawImage(png, (32 - width) / 2, (32 - height) / 2, width, height,
									   null);
					graphics.dispose();
					png = square;
				}
				return new ImageIcon(png);
			}
			catch (IOException e)
			{
				return MISSING;
			}
		});
	}

	static Icon chapterIcon(String key)
	{
		return load("/chapter-icons/" + key + ".png");
	}

	/** Bundled icon artwork; neither sidebar rendering nor loading needs a network request. */
	static Icon socialIcon(String name)
	{
		if (!"discord".equals(name) && !"clanfinder".equals(name))
		{
			return null;
		}
		Icon source = load("/social-icons/" + name + ".png");
		if (source == MISSING || !(source instanceof ImageIcon))
		{
			return null;
		}
		BufferedImage scaled = new BufferedImage(22, 22, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphic = scaled.createGraphics();
		graphic.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
								 java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphic.drawImage(((ImageIcon)source).getImage(), 0, 0, 22, 22, null);
		graphic.dispose();
		return new ImageIcon(scaled);
	}

	private static final class EmperorBadge
	{
		static final Icon ICON = create();

		private static Icon create()
		{
			Icon original = load("/rank-icons/emperor-not-needed.png");
			if (original == MISSING)
			{
				return null;
			}
			BufferedImage small = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
			Graphics2D graphics = small.createGraphics();
			graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
									  java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(((ImageIcon)original).getImage(), 0, 0, 18, 18, null);
			graphics.dispose();
			return new ImageIcon(small);
		}
	}

	static Icon emperorIcon()
	{
		return EmperorBadge.ICON;
	}

	static Icon rankIcon(String rawRank)
	{
		if (rawRank == null || rawRank.trim().isEmpty())
		{
			return null;
		}
		String slug = rawRank.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-");
		if (slug.isEmpty() || slug.equals("unranked"))
		{
			return null;
		}
		Icon icon = load("/rank-icons/" + slug + ".png");
		return icon == MISSING ? null : icon;
	}
}
