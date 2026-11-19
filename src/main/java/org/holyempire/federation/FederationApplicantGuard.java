/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.util.Text;

/** Scoped strictly to the native OSRS Clan Applicants -> Accept interaction. */
final class FederationApplicantGuard
{
	private FederationApplicantGuard()
	{
	}

	static boolean isApplicantAcceptance(String option, int paramWidget, int clickedWidget)
	{
		final int accept = InterfaceID.ClansApplicants.ACCEPT;
		final int group = InterfaceID.CLANS_APPLICANTS;
		String action = option == null ? "" : Text.removeTags(option).trim();
		// Widget-based matching catches buttons whose menu action is "Select".
		if (paramWidget == accept || clickedWidget == accept)
			return action.isEmpty() || "Accept".equalsIgnoreCase(action) ||
				"Select".equalsIgnoreCase(action) || "Continue".equalsIgnoreCase(action);
		return "Accept".equalsIgnoreCase(action) &&
			((paramWidget >>> 16) == group || (clickedWidget >>> 16) == group);
	}

	/**
	 * Use only the selected applicant's details pane (not the whole applicants
	 * list). Do not accuse somebody based on another applicant in the list.
	 */
	enum Status
	{
		BANNED,
		CLEAR,
		UNVERIFIED
	}

	static Status status(FederationBanDirectoryClient.Snapshot directory,
						 List<String> selectedDetails)
	{
		if (directory == null)
			return Status.UNVERIFIED;
		String selected = selectedApplicantName(selectedDetails);
		if (selected == null)
			return Status.UNVERIFIED;
		return FederationRecruitmentGuard.matchingChapters(directory, selected).isEmpty()
			? Status.CLEAR
			: Status.BANNED;
	}

	static String matchedBannedApplicant(FederationBanDirectoryClient.Snapshot approvedDirectory,
										 List<String> selectedDetails)
	{
		String applicant = selectedApplicantName(selectedDetails);
		if (applicant == null || approvedDirectory == null)
			return null;
		for (FederationBanDirectoryClient.Entry entry : approvedDirectory.entries)
			if (WomDirectoryClient.normalize(entry.name)
					.equals(WomDirectoryClient.normalize(applicant)))
				return entry.name;
		return null;
	}

	/**
	 * The selected applicant name must be unambiguous. Prefer a labeled name in
	 * the selected-details panel, never infer identity from the full applicants
	 * list or from descriptive game text.
	 */
	static String selectedApplicantName(List<String> selectedDetails)
	{
		if (selectedDetails == null || selectedDetails.isEmpty())
			return null;
		Set<String> labeled = new LinkedHashSet<>();
		Set<String> plain = new LinkedHashSet<>();
		for (String raw : selectedDetails)
		{
			if (raw == null)
				continue;
			String text = Text.removeTags(raw).trim();
			boolean explicit = text.matches("(?i)^(applicant|name)\\s*:.*");
			// Numeric-only unlabeled strings are normally OSRS skill levels,
			// not names. Explicit "Applicant: 123" still supports numeric RSNs.
			if (!explicit && text.matches("[0-9]+"))
				continue;
			String candidate = extractExactName(text);
			if (candidate == null)
				continue;
			if (explicit)
				labeled.add(candidate);
			else
				plain.add(candidate);
		}
		if (labeled.size() > 1)
			return null;
		if (labeled.size() == 1)
			return labeled.iterator().next();
		return plain.size() == 1 ? plain.iterator().next() : null;
	}

	/**
	 * True only when the selected detail pane supplies a plausible player
	 * name. This lets an explicit menu-target RSN be used if the pane is empty,
	 * without overriding ambiguous selected-person evidence.
	 */
	static boolean hasSelectedIdentityEvidence(List<String> selectedDetails)
	{
		if (selectedDetails == null)
			return false;
		for (String raw : selectedDetails)
		{
			if (raw == null)
				continue;
			String value = Text.removeTags(raw).trim();
			if (!value.matches("(?i)^(applicant|name)\\s*:.*") && value.matches("[0-9]+"))
				continue;
			if (extractExactName(value) != null)
				return true;
		}
		return false;
	}

	static String extractExactName(String source)
	{
		if (source == null)
			return null;
		String clean = Text.removeTags(source).trim();
		clean = clean.replaceFirst("(?i)^(?:applicant|name)\\s*:\\s*", "");
		int detail = clean.indexOf(" (level-");
		if (detail < 0)
			detail = clean.indexOf(" (skill-");
		if (detail >= 0)
			clean = clean.substring(0, detail);
		int chapter = clean.indexOf(" [");
		if (chapter >= 0)
			clean = clean.substring(0, chapter);
		clean = clean.trim();
		// UI placeholders, action labels and skill/tooltips are not player names.
		if (clean.isEmpty() || clean.matches("[-_ ]+") ||
			clean.matches("(?i)^(accept|reject|recruit|apply|info|select|refresh|back|"
						  + "invite.*|choose.*|combat.*|skill.*|clan.*|total.*|status.*|"
						  + "applicant.*|name|online|offline|none|unknown|attack|strength|"
						  + "defence|hitpoints|ranged|prayer|magic|cooking|woodcutting|fishing|"
						  + "mining|smithing|crafting|agility|thieving|farming|herblore|slayer|"
						  + "hunter|construction|firemaking|runecraft|overall|members|apply|"
						  + "invitation|skills)$"))
			return null;
		return FederationBanRegistry.validName(clean);
	}

	/**
	 * Match the normal Recruit warning behavior: never cancel an unknown or
	 * unlisted applicant. Only a confirmed ban-list match blocks acceptance.
	 */
	static boolean shouldCancelAccept(Status status)
	{
		return status == Status.BANNED;
	}

	static String unverifiedWarning()
	{
		return "HOLY EMPIRE RECRUITMENT WARNING\n\n"
			+ "The selected applicant's ban-list status has not been verified.\n\n"
			+ "Ask an authorized Owner or Deputy Owner to check the combined private "
			+ "ban list before accepting this application.";
	}

	static String bannedWarning(String applicant, List<String> chapters)
	{
		String standard = FederationRecruitmentGuard.warningText(applicant, chapters, true);
		return standard == null ? unverifiedWarning()
								: standard + "\n\nThis is an Applicants -> Accept action.";
	}
}
