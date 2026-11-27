/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Rank simulations test UI only: they must never create privileges. */
public class HolyEmpireRankViewPreviewTest
{
	private static Component find(Component root, String id)
	{
		if (id.equals(root.getName()))
			return root;
		if (root instanceof Container)
			for (Component child : ((Container)root).getComponents())
			{
				Component result = find(child, id);
				if (result != null)
					return result;
			}
		return null;
	}

	private static JLabel label(Component root, String value)
	{
		if (root instanceof JLabel && value.equals(((JLabel)root).getText()))
			return (JLabel)root;
		if (root instanceof Container)
			for (Component child : ((Container)root).getComponents())
			{
				JLabel result = label(child, value);
				if (result != null)
					return result;
			}
		return null;
	}

	private static FederationPanel panel() throws Exception
	{
		FederationPanel[] ref = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			ref[0] = new FederationPanel();
			// Deliberately supply an owner claim to prove preview still disables writes.
			ref[0].setAdminPublishing(
				(kind, title, body) -> fail("Preview must not publish"), () -> true, () -> {});
			ref[0].setAnnouncementActions(
				(kind, title, body, url)
					-> fail("Preview must not publish"),
				(action, id, title, body, url) -> fail("Preview must not manage"));
			ref[0].setEmergencyActions(action -> fail("Preview must not end emergency"));
		});
		ref[0].update(Collections.singletonMap("member", Collections.singletonList("Holy Empire")),
					  Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		return ref[0];
	}

	@Test public void rolesChangeVisibleTabsButNotLiveAuthorization() throws Exception
	{
		FederationPanel p = panel();
		try
		{
			for (HolyEmpireViewPreview role : HolyEmpireViewPreview.values())
			{
				p.setViewPreview(role);
				SwingUtilities.invokeAndWait(() -> {});
				if (role == HolyEmpireViewPreview.LIVE)
					continue;
				final HolyEmpireViewPreview current = role;
				SwingUtilities.invokeAndWait(() -> {
					JLabel watermark = (JLabel)find(p, "holyEmpireDeveloperViewPreview");
					assertNotNull(watermark);
					assertTrue(watermark.isVisible());
					assertTrue(watermark.getText().contains(current.toString()));
					JToggleButton ban = (JToggleButton)find(p, "holyEmpireBanListTab");
					JToggleButton admin = (JToggleButton)find(p, "holyEmpireAdminControlsTab");
					assertEquals("Global ban search visibility: " + current,
								 current.canSearchGlobalBans(), ban.isVisible());
					assertEquals("Announcements controls visibility: " + current,
								 current.canCreateAnnouncements(), admin.isVisible());
					if (current.showsNonMember())
					{
						assertNotNull("Outside Empire sees join screen",
									  find(p, "holyEmpireAccessDenied"));
					}
					else if (current.canSearchGlobalBans())
					{
						ban.doClick();
						assertNull("Preview never displays private ban chapters",
								   find(p, "holyEmpireBanChapter:Holy Chaos"));
						if (current.canViewChapterBanLists())
						{
							JButton refresh = (JButton)find(p, "holyEmpireBanRefresh");
							assertNotNull(refresh);
							assertFalse(refresh.isEnabled());
							assertNotNull("Full officer views include ban sync",
										  find(p, "holyEmpirePreviewSyncPermissions"));
						}
						else
						{
							assertEquals(HolyEmpireViewPreview.CHAPTER_ADMIN, current);
							assertNotNull(find(p, "holyEmpirePreviewGlobalBanSearch"));
							assertNull(find(p, "holyEmpireBanRefresh"));
							assertNull(find(p, "holyEmpirePreviewSyncPermissions"));
						}
					}
					if (current.canCreateAnnouncements())
					{
						admin.doClick();
						JToggleButton broadcasts =
							(JToggleButton)find(p, "holyEmpireAdminBroadcastsSubtab");
						assertEquals("Broadcast tab visible only for High Lords and above",
									 current.canBroadcast(), broadcasts.isVisible());
						JToggleButton announcements =
							(JToggleButton)find(p, "holyEmpireAdminAnnouncementsSubtab");
						announcements.doClick();
						JButton publish = (JButton)find(p, "holyEmpireAdminPublish:announcement");
						assertNotNull(publish);
						assertFalse("Preview may not publish announcements", publish.isEnabled());
						if (current.canBroadcast())
						{
							broadcasts.doClick();
							JButton submit = (JButton)find(p, "holyEmpireAdminPublish:broadcast");
							assertNotNull(submit);
							assertFalse("Preview may not send broadcasts", submit.isEnabled());
						}
						else
							assertNull(find(p, "holyEmpireAdminPublish:broadcast"));
					}
				});
			}
		}
		finally
		{
			p.stopPrivateCacheTimer();
		}
	}

	@Test public void canonicalRankPreviewPolicy() throws Exception
	{
		assertEquals(8, HolyEmpireViewPreview.values().length);
		for (HolyEmpireViewPreview role : HolyEmpireViewPreview.values())
		{
			assertFalse(role.name().equals("CHAPTER_MASTER"));
			assertFalse(role.name().equals("CHAPTER_OWNER"));
			assertFalse(role.name().equals("APPLICANT"));
			assertFalse(role.name().equals("RECRUIT"));
		}
		HolyEmpireViewPreview chapterAdmin = HolyEmpireViewPreview.CHAPTER_ADMIN;
		assertTrue(chapterAdmin.canSearchGlobalBans());
		assertFalse(chapterAdmin.canViewChapterBanLists());
		assertFalse(chapterAdmin.canSyncBans());
		assertFalse(chapterAdmin.canCreateAnnouncements());
		HolyEmpireViewPreview deputy = HolyEmpireViewPreview.DEPUTY_OWNER;
		assertTrue(deputy.canSyncBans());
		assertTrue(deputy.canCreateAnnouncements());
		assertFalse(deputy.canEditAllAnnouncements());
		assertFalse(deputy.canBroadcast());
		HolyEmpireViewPreview primarch = HolyEmpireViewPreview.PRIMARCH;
		assertTrue(primarch.toString().contains("Chapter Owner"));
		assertTrue(primarch.canSyncBans());
		assertTrue(primarch.canCreateAnnouncements());
		assertTrue(primarch.canEditAllAnnouncements());
		assertFalse(primarch.canBroadcast());
		assertTrue(HolyEmpireViewPreview.HIGH_LORD.canBroadcast());
		assertTrue(HolyEmpireViewPreview.EMPEROR.hasFullAccess());
		assertEquals("Only the real account and the seven rank previews remain", 8,
					 HolyEmpireViewPreview.values().length);
		for (HolyEmpireViewPreview mode : HolyEmpireViewPreview.values())
			assertFalse(mode.toString().contains("Your account"));
		assertFalse(HolyEmpireViewPreview.LIVE.isPreview());
	}

	@Test public void adminOpensAnnouncementsAndIncompleteFormHasNoBanner() throws Exception
	{
		FederationPanel p = panel();
		try
		{
			p.setAdminEligible(true);
			p.setBroadcastEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(
					((JToggleButton)find(p, "holyEmpireAdminAnnouncementsSubtab")).isSelected());
				assertNotNull(find(p, "holyEmpireAdminPublish:announcement"));
				((JButton)find(p, "holyEmpireAdminPublish:announcement")).doClick();
				assertNull("An empty title/body should not leave an error message",
						   find(p, "holyEmpireAdminPublishStatus"));
				((JToggleButton)find(p, "holyEmpireAdminBroadcastsSubtab")).doClick();
				assertNotNull(find(p, "holyEmpireAdminPublish:broadcast"));
				((JToggleButton)find(p, "holyEmpireAnnouncementsTab")).doClick();
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertTrue(
					"Admin Controls remembers the last clicked Broadcasts tab",
					((JToggleButton)find(p, "holyEmpireAdminBroadcastsSubtab")).isSelected());
				assertNotNull(find(p, "holyEmpireAdminPublish:broadcast"));
				assertNull(find(p, "holyEmpireAdminPublish:announcement"));
			});
		}
		finally
		{
			p.stopPrivateCacheTimer();
		}
	}

	@Test public void adminTabsAreSeparateAndTypographyIsLarger() throws Exception
	{
		FederationPanel p = panel();
		try
		{
			p.setAdminEligible(true);
			p.setBroadcastEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				assertEquals(20f, ((JLabel)find(p, "holyEmpireHeader")).getFont().getSize2D(), 0f);
				assertEquals(
					12f, ((JLabel)find(p, "holyEmpireImperialMotto")).getFont().getSize2D(), 0f);
				assertNotNull(label(p, "Chapters of the Holy Empire"));
				((JToggleButton)find(p, "holyEmpireAnnouncementsTab")).doClick();
				JLabel achievements = (JLabel)find(p, "holyEmpireAchievementsHeading");
				assertEquals("Today's Achievements", achievements.getText());
				assertEquals(javax.swing.SwingConstants.CENTER,
							 achievements.getHorizontalAlignment());
				assertEquals(Integer.MAX_VALUE, achievements.getMaximumSize().width);
				JLabel announcementHeading = (JLabel)find(p, "holyEmpireAnnouncementsHeading");
				assertEquals("Announcements", announcementHeading.getText());
				assertEquals(javax.swing.SwingConstants.CENTER,
							 announcementHeading.getHorizontalAlignment());
				assertEquals(Integer.MAX_VALUE, announcementHeading.getMaximumSize().width);
				assertEquals(18f, announcementHeading.getFont().getSize2D(), 0f);
				((JToggleButton)find(p, "holyEmpireEventsTab")).doClick();
				JLabel eventHeading = (JLabel)find(p, "holyEmpireEventsHeading");
				assertEquals("Events", eventHeading.getText());
				assertEquals(javax.swing.SwingConstants.CENTER,
							 eventHeading.getHorizontalAlignment());
				assertEquals(Integer.MAX_VALUE, eventHeading.getMaximumSize().width);
				assertEquals(17f, eventHeading.getFont().getSize2D(), 0f);
				((JToggleButton)find(p, "holyEmpireAdminControlsTab")).doClick();
				assertNotNull(find(p, "holyEmpireAdminBroadcastsSubtab"));
				assertNotNull(find(p, "holyEmpireAdminAnnouncementsSubtab"));
				assertTrue(
					"Announcements must be selected on entering Admin Controls",
					((JToggleButton)find(p, "holyEmpireAdminAnnouncementsSubtab")).isSelected());
				Container tabs =
					((JToggleButton)find(p, "holyEmpireAdminAnnouncementsSubtab")).getParent();
				assertEquals(0,
							 tabs.getComponentZOrder(find(p, "holyEmpireAdminAttendanceSubtab")));
				assertEquals(
					1, tabs.getComponentZOrder(find(p, "holyEmpireAdminAnnouncementsSubtab")));
				assertEquals(2,
							 tabs.getComponentZOrder(find(p, "holyEmpireAdminBroadcastsSubtab")));
				assertNotNull(find(p, "holyEmpireAdminPublish:announcement"));
				assertNull("Announcement tab must not display broadcast form",
						   find(p, "holyEmpireAdminPublish:broadcast"));
				((JToggleButton)find(p, "holyEmpireAdminBroadcastsSubtab")).doClick();
				assertEquals(16f, label(p, "Chatbox Broadcast").getFont().getSize2D(), 0f);
				assertNotNull(find(p, "holyEmpireAdminPublish:broadcast"));
				assertNull("Broadcast tab must not display announcement form",
						   find(p, "holyEmpireAdminPublish:announcement"));
				((JToggleButton)find(p, "holyEmpireAdminAnnouncementsSubtab")).doClick();
				assertEquals(16f, label(p, "Post Announcement").getFont().getSize2D(), 0f);
				assertNull(find(p, "holyEmpireManageAnnouncementsHeading"));
			});
			p.setOfficerSettingsEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)find(p, "holyEmpireBanListTab")).doClick();
				JLabel banHeading = (JLabel)find(p, "holyEmpireBanListHeader");
				assertEquals("Chapter Ban-Lists", banHeading.getText());
				assertEquals(18f, banHeading.getFont().getSize2D(), 0f);
				assertEquals(
					12f, ((JLabel)find(p, "holyEmpireRestrictedSubtitle")).getFont().getSize2D(),
					0f);
			});
		}
		finally
		{
			p.stopPrivateCacheTimer();
		}
	}
}
