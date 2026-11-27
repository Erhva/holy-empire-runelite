/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, checksum-verified chapter definitions received from the fixed Holy Empire origin. */
final class ChapterDirectory
{
	static final int MAX_GROUPS = 64;
	final List<HolyEmpireChapterCatalog.Chapter> ordered;
	private final Map<Integer, HolyEmpireChapterCatalog.Chapter> groupById;

	private ChapterDirectory(List<HolyEmpireChapterCatalog.Chapter> chapters)
	{
		ordered = Collections.unmodifiableList(new ArrayList<>(chapters));
		Map<Integer, HolyEmpireChapterCatalog.Chapter> byId = new LinkedHashMap<>();
		for (HolyEmpireChapterCatalog.Chapter chapter : chapters)
		{
			if (chapter.groupId > 0)
			{
				byId.put(chapter.groupId, chapter);
			}
		}
		groupById = Collections.unmodifiableMap(byId);
	}

	int groupCount()
	{
		return groupById.size();
	}

	HolyEmpireChapterCatalog.Chapter forGroup(int id)
	{
		return groupById.get(id);
	}

	static ChapterDirectory legacy()
	{
		List<HolyEmpireChapterCatalog.Chapter> result = new ArrayList<>();
		int order = 0;
		for (HolyEmpireChapterCatalog.Chapter chapter : HolyEmpireChapterCatalog.DEFAULT_ORDERED)
		{
			result.add(new HolyEmpireChapterCatalog.Chapter(
				chapter.groupId, chapter.name, chapter.clanChat, chapter.iconKey, chapter.access,
				chapter.detail, order++));
		}
		return new ChapterDirectory(result);
	}

	private static String string(JsonObject record, String field, String pattern, int max)
		throws IOException
	{
		JsonElement value = record.get(field);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
		{
			throw new IOException("Invalid chapter " + field);
		}
		String text = value.getAsString();
		if (text.length() > max || (pattern != null && !text.matches(pattern)) ||
			text.indexOf('<') >= 0 || text.indexOf('>') >= 0 || text.indexOf('&') >= 0)
		{
			throw new IOException("Unsafe chapter " + field);
		}
		for (int i = 0; i < text.length(); i++)
		{
			if (Character.isISOControl(text.charAt(i)))
			{
				throw new IOException("Invalid chapter " + field);
			}
		}
		return text;
	}

	private static int integer(JsonObject record, String field, int min, int max) throws IOException
	{
		JsonElement element = record.get(field);
		if (element == null || !element.isJsonPrimitive() || !((JsonPrimitive)element).isNumber())
		{
			throw new IOException("Invalid chapter " + field);
		}
		try
		{
			long number = element.getAsLong();
			if (number < min || number > max ||
				!Long.toString(number).equals(element.getAsString()))
			{
				throw new IOException("Invalid chapter " + field);
			}
			return (int)number;
		}
		catch (NumberFormatException | UnsupportedOperationException error)
		{
			throw new IOException("Invalid chapter " + field, error);
		}
	}

	static ChapterDirectory parse(JsonArray source, int expectedGroups) throws IOException
	{
		if (source == null || source.size() < 16 || source.size() > MAX_GROUPS + 8 ||
			expectedGroups < 16 || expectedGroups > MAX_GROUPS)
		{
			throw new IOException("Invalid chapter directory size");
		}
		List<HolyEmpireChapterCatalog.Chapter> chapters = new ArrayList<>();
		Set<Integer> ids = new HashSet<>();
		Set<String> names = new HashSet<>();
		Set<String> chats = new HashSet<>();
		Set<Integer> orders = new HashSet<>();
		for (JsonElement element : source)
		{
			if (!element.isJsonObject())
			{
				throw new IOException("Invalid chapter directory entry");
			}
			JsonObject row = element.getAsJsonObject();
			JsonElement group = row.get("group_id");
			int id = group == null || group.isJsonNull()
						 ? 0
						 : integer(row, "group_id", 1, Integer.MAX_VALUE);
			int order = integer(row, "order", 0, 255);
			String name = string(row, "name", "[A-Za-z0-9 _-]{1,32}", 32);
			String chat = string(row, "clan_chat", "[A-Za-z0-9 _-]{1,32}", 32);
			String icon = string(row, "icon_key", "[a-z0-9-]{1,32}", 32);
			String access = string(row, "access", null, 64);
			String detail = string(row, "detail", null, 64);
			if ((id > 0 && !ids.add(id)) || !orders.add(order) ||
				!names.add(WomDirectoryClient.normalize(name)) ||
				!chats.add(WomDirectoryClient.normalize(chat)))
			{
				throw new IOException("Duplicate chapter directory identity");
			}
			String iconSha256 = null;
			JsonElement image = row.get("icon_sha256");
			if (image != null && !image.isJsonNull())
			{
				iconSha256 = string(row, "icon_sha256", "[0-9a-f]{64}", 64);
			}
			chapters.add(new HolyEmpireChapterCatalog.Chapter(id, name, chat, icon, access, detail,
															  order, iconSha256));
		}
		if (ids.size() != expectedGroups)
		{
			throw new IOException("Incomplete chapter directory");
		}
		chapters.sort(Comparator.comparingInt(c -> c.order));
		return new ChapterDirectory(chapters);
	}

	JsonArray toJson()
	{
		JsonArray output = new JsonArray();
		for (HolyEmpireChapterCatalog.Chapter chapter : ordered)
		{
			JsonObject row = new JsonObject();
			if (chapter.groupId > 0)
			{
				row.addProperty("group_id", chapter.groupId);
			}
			else
			{
				row.add("group_id", JsonNull.INSTANCE);
			}
			row.addProperty("name", chapter.name);
			row.addProperty("clan_chat", chapter.clanChat);
			row.addProperty("icon_key", chapter.iconKey);
			if (chapter.iconSha256 != null)
			{
				row.addProperty("icon_sha256", chapter.iconSha256);
			}
			row.addProperty("access", chapter.access);
			row.addProperty("detail", chapter.detail);
			row.addProperty("order", chapter.order);
			output.add(row);
		}
		return output;
	}
}
