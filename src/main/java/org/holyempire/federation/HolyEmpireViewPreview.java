/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

/**
 * Purely visual developer rank previews. Never authorize a private request,
 * access protected data, or publish based on any value in this enum.
 *
 * Chapter Owner is Primarch; Chapter Master is not a separate rank.
 * Applicant/Recruit are represented by Member once on the approved roster.
 */
public enum HolyEmpireViewPreview
{
	LIVE("Actual account (no preview)", false, false, false, false, false, false, false),
	OUTSIDE("Outside Holy Empire", true, false, false, false, false, false, false),
	MEMBER("Member", false, false, false, false, false, false, false),
	CHAPTER_ADMIN("Chapter Admin", false, true, false, false, false, false, false),
	DEPUTY_OWNER("Deputy Owner", false, true, true, true, false, false, false),
	PRIMARCH("Primarch / Chapter Owner", false, true, true, true, true, false, false),
	HIGH_LORD("High Lord", false, true, true, true, true, true, false),
	EMPEROR("Emperor", false, true, true, true, true, true, true);

	private final String label;
	private final boolean outsider;
	private final boolean globalBanSearch;
	private final boolean chapterBanLists;
	private final boolean announce;
	private final boolean editAll;
	private final boolean broadcasts;
	private final boolean fullAccess;

	HolyEmpireViewPreview(String label, boolean outsider, boolean globalBanSearch,
						  boolean chapterBanLists, boolean announce, boolean editAll,
						  boolean broadcasts, boolean fullAccess)
	{
		this.label = label;
		this.outsider = outsider;
		this.globalBanSearch = globalBanSearch;
		this.chapterBanLists = chapterBanLists;
		this.announce = announce;
		this.editAll = editAll;
		this.broadcasts = broadcasts;
		this.fullAccess = fullAccess;
	}

	public boolean isPreview()
	{
		return this != LIVE;
	}

	public boolean showsNonMember()
	{
		return outsider;
	}

	public boolean showsBanList()
	{
		return globalBanSearch;
	}

	public boolean canSearchGlobalBans()
	{
		return globalBanSearch;
	}

	public boolean canViewChapterBanLists()
	{
		return chapterBanLists;
	}

	public boolean canSyncBans()
	{
		return chapterBanLists;
	}

	public boolean canCreateAnnouncements()
	{
		return announce;
	}

	public boolean canEditAllAnnouncements()
	{
		return editAll;
	}

	public boolean showsEmpireControls()
	{
		return announce;
	}

	public boolean canBroadcast()
	{
		return broadcasts;
	}

	public boolean hasFullAccess()
	{
		return fullAccess;
	}

	@Override public String toString()
	{
		return label;
	}
}
