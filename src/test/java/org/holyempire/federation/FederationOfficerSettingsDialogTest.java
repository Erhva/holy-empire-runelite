/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import javax.swing.JButton;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationOfficerSettingsDialogTest
{
	@Test
	public void privateSyncEndpointValidationRequiresHttps()
	{
		assertTrue(FederationOfficerSettingsDialog.validEndpoint(""));
		assertTrue(FederationOfficerSettingsDialog.validEndpoint("   "));
		assertTrue(FederationOfficerSettingsDialog.validEndpoint("https://bans.holyempireosrs.cc"));
		assertFalse(
			FederationOfficerSettingsDialog.validEndpoint("https://moderation.example.org"));
		assertFalse(FederationOfficerSettingsDialog.validEndpoint(
			"https://bans.holyempireosrs.cc.evil.example"));
		assertFalse(FederationOfficerSettingsDialog.validEndpoint("http://moderation.example.org"));
		assertFalse(
			FederationOfficerSettingsDialog.validEndpoint("https://user@moderation.example.org"));
		assertFalse(FederationOfficerSettingsDialog.validEndpoint(
			"https://moderation.example.org/?token=value"));
	}

	@Test
	public void verificationActionsRemainAvailableUntilExplicitClose() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			AtomicInteger requests = new AtomicInteger();
			AtomicInteger statuses = new AtomicInteger();
			AtomicInteger reviews = new AtomicInteger();
			AtomicInteger closes = new AtomicInteger();
			JPanel actions = FederationOfficerSettingsDialog.actionButtons(
				requests::incrementAndGet, statuses::incrementAndGet, reviews::incrementAndGet,
				closes::incrementAndGet);
			assertEquals(4, actions.getComponentCount());
			assertEquals("holyEmpireRequestVerification", actions.getComponent(0).getName());
			assertEquals("holyEmpireCheckVerification", actions.getComponent(1).getName());
			assertEquals("holyEmpireReviewVerifications", actions.getComponent(2).getName());
			assertEquals("holyEmpireCloseVerification", actions.getComponent(3).getName());
			((JButton)actions.getComponent(0)).doClick();
			((JButton)actions.getComponent(1)).doClick();
			((JButton)actions.getComponent(2)).doClick();
			((JButton)actions.getComponent(0)).doClick();
			assertEquals(2, requests.get());
			assertEquals(1, statuses.get());
			assertEquals(1, reviews.get());
			assertEquals(0, closes.get());
			assertTrue(((JButton)actions.getComponent(0)).isEnabled());
			((JButton)actions.getComponent(3)).doClick();
			assertEquals(1, closes.get());
		});
	}

	@Test
	public void manualCredentialControlsAreNotDisplayed() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			JPanel actions = FederationOfficerSettingsDialog.actionButtons(
				() -> {}, () -> {}, () -> {}, () -> {});
			for (java.awt.Component component : actions.getComponents())
			{
				assertNotEquals("holyEmpireSaveAdminKey", component.getName());
				assertNotEquals("Save Key", ((JButton)component).getText());
			}
			// Credential issuance and storage remain automatic after remote approval.
			assertEquals(4, actions.getComponentCount());
		});
	}
}
