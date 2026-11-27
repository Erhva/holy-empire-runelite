/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generation-checked session: WOM roster determines membership; native clan authorizes officer
 * operations.
 */
final class FederationSession
{
	private String account = "";
	private String nativeChapter = "";
	private long generation;
	private Map<String, List<String>> roster = Collections.emptyMap();
	private Map<String, List<WomDirectoryClient.ChapterMember>> chapters = Collections.emptyMap();
	private Map<String, WomDirectoryClient.ChapterMember> rankIndex = Collections.emptyMap();
	private java.util.Set<String> publicBanHashes = Collections.emptySet();
	private boolean publicBanStatusAvailable;
	private boolean eligible;

	synchronized boolean observe(String name, String chapter)
	{
		String next = WomDirectoryClient.normalize(name);
		String nextChapter = next.isEmpty() ? "" : InGameClanVerifier.recognizedChapter(chapter);
		if (next.equals(account) && nextChapter.equals(nativeChapter))
		{
			return false;
		}
		account = next;
		nativeChapter = nextChapter;
		generation++;
		clear();
		return true;
	}

	private void clear()
	{
		eligible = false;
		roster = Collections.emptyMap();
		chapters = Collections.emptyMap();
		rankIndex = Collections.emptyMap();
		publicBanHashes = Collections.emptySet();
		publicBanStatusAvailable = false;
	}

	synchronized String account()
	{
		return account;
	}

	synchronized String nativeChapter()
	{
		return nativeChapter;
	}

	synchronized long generation()
	{
		return generation;
	}

	synchronized boolean publish(String expected, long expectedGeneration,
								 WomDirectoryClient.Snapshot data)
	{
		if (expectedGeneration != generation || !account.equals(expected))
		{
			return false;
		}
		HolyEmpireChapterCatalog.update(data.directory);
		List<String> chaptersForAccount = data.members.get(account);
		eligible = chaptersForAccount != null && !chaptersForAccount.isEmpty();
		roster = eligible ? data.members : Collections.emptyMap();
		chapters = eligible ? data.chapters : Collections.emptyMap();
		publicBanHashes = eligible ? data.publicBanHashes : Collections.emptySet();
		publicBanStatusAvailable = eligible && data.publicBanStatusAvailable;
		if (eligible)
		{
			Map<String, WomDirectoryClient.ChapterMember> index = new HashMap<>();
			for (HolyEmpireChapterCatalog.Chapter chapter : HolyEmpireChapterCatalog.ORDERED)
			{
				for (WomDirectoryClient.ChapterMember member :
					 chapters.getOrDefault(chapter.name, Collections.emptyList()))
				{
					index.putIfAbsent(WomDirectoryClient.normalize(member.name), member);
				}
			}
			rankIndex = Collections.unmodifiableMap(index);
		}
		else
		{
			rankIndex = Collections.emptyMap();
		}
		return true;
	}

	synchronized boolean invalidate(String expected, long expectedGeneration)
	{
		if (expectedGeneration != generation || !account.equals(expected))
		{
			return false;
		}
		clear();
		return true;
	}

	synchronized Map<String, List<WomDirectoryClient.ChapterMember>> chaptersFor(String current)
	{
		return eligible && account.equals(WomDirectoryClient.normalize(current))
			? chapters
			: Collections.emptyMap();
	}

	synchronized WomDirectoryClient.ChapterMember rankFor(String name)
	{
		return eligible ? rankIndex.get(WomDirectoryClient.normalize(name)) : null;
	}

	synchronized boolean isEligible()
	{
		return eligible;
	}

	synchronized boolean publicBanWarningAvailable(String current)
	{
		return eligible && account.equals(WomDirectoryClient.normalize(current)) &&
			publicBanStatusAvailable;
	}

	synchronized boolean publicBanWarningMatch(String current, String name)
	{
		return eligible && account.equals(WomDirectoryClient.normalize(current)) &&
			FederationPublicBanWarnings.matches(name, publicBanHashes, roster);
	}

	synchronized java.util.Set<String> publicBanHashesFor(String current)
	{
		return eligible && account.equals(WomDirectoryClient.normalize(current))
			? publicBanHashes
			: Collections.emptySet();
	}

	synchronized boolean eligibleFor(String expected, long expectedGeneration)
	{
		return eligible && generation == expectedGeneration && account.equals(expected);
	}

	synchronized Map<String, List<String>> rosterFor(String current)
	{
		return eligible && account.equals(WomDirectoryClient.normalize(current))
			? roster
			: Collections.emptyMap();
	}
}
