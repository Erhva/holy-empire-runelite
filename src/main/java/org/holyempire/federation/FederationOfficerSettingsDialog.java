/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;

/**
 * The dialog remains open while an officer performs verification actions.
 * The private API remains responsible for all actual authorization decisions.
 */
final class FederationOfficerSettingsDialog
{
	private static final String GROUP = "holyEmpireFederation";

	private FederationOfficerSettingsDialog()
	{
	}

	static boolean validEndpoint(String endpoint)
	{
		return endpoint != null && (endpoint.trim().isEmpty() ||
									FederationBanUploadClient.acceptedEndpoint(endpoint.trim()));
	}

	/** Only the credential is editable; the HTTPS service address is fixed. */
	static JPanel credentialFields(JPasswordField token, boolean alreadyConfigured)
	{
		JPanel fields = new JPanel(new GridLayout(0, 1, 0, 5));
		fields.setBackground(HolyEmpireTheme.STEEL);
		HolyEmpireTheme.styleSearch(token);
		JLabel credentialLabel = new JLabel("Admin Credential Key (Leave Blank to Keep Existing)");
		credentialLabel.setName("holyEmpireAdminCredentialLabel");
		credentialLabel.setForeground(HolyEmpireTheme.GOLD);
		fields.add(credentialLabel);
		fields.add(token);
		JLabel status =
			new JLabel(alreadyConfigured ? "An account-specific local credential is saved."
										 : "No local credential configured");
		status.setName("holyEmpireAdminKeyStatus");
		status.setForeground(HolyEmpireTheme.MUTED);
		fields.add(status);
		return fields;
	}

	/** Testable persistent action bar: only the explicit Close button closes. */
	static JPanel actionButtons(Runnable save, Runnable request, Runnable check, Runnable review,
								Runnable close)
	{
		JPanel actions = new JPanel(new GridLayout(0, 2, 8, 8));
		actions.setOpaque(false);
		addAction(actions, "Save Key", "holyEmpireSaveAdminKey", save);
		if (request != null)
		{
			addAction(actions, "Request Verification", "holyEmpireRequestVerification", request);
			addAction(actions, "Check Status", "holyEmpireCheckVerification", check);
			addAction(actions, "Review Requests", "holyEmpireReviewVerifications", review);
		}
		addAction(actions, "Close", "holyEmpireCloseVerification", close);
		return actions;
	}

	private static void addAction(JPanel buttons, String label, String name, Runnable callback)
	{
		JButton button = HolyEmpireTheme.button(label, false);
		button.setName(name);
		button.addActionListener(event -> callback.run());
		buttons.add(button);
	}

	static void show(Component parent, ConfigManager manager,
					 FederationOfficerCredentialStore secrets, String account,
					 BooleanSupplier authorized)
	{
		show(parent, manager, secrets, account, authorized, null, null, null);
	}

	static void show(Component parent, ConfigManager manager,
					 FederationOfficerCredentialStore secrets, String account,
					 BooleanSupplier authorized,
					 BiConsumer<Component, Consumer<String>> requestVerification,
					 BiConsumer<Component, Consumer<String>> checkVerification,
					 BiConsumer<Component, Consumer<String>> reviewRequests)
	{
		if (!authorized.getAsBoolean())
		{
			return;
		}
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Admin Verification",
									 Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		JPasswordField token = new JPasswordField(26);
		token.setName("holyEmpireAdminCredentialKey");
		token.setToolTipText("Leave blank to keep the existing admin credential key.");
		JPanel fields = credentialFields(token, !secrets.read(account).isEmpty());
		JLabel keyStatus = (JLabel)fields.getComponent(2);

		JTextArea status =
			new JTextArea("Manage your officer credential or request verification.", 2, 35);
		status.setName("holyEmpireVerificationFeedback");
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		status.setEditable(false);
		status.setFocusable(false);
		status.setBackground(HolyEmpireTheme.STEEL);
		status.setForeground(HolyEmpireTheme.MUTED);
		status.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));
		Consumer<String> feedback = message ->
		{
			Runnable update = () ->
			{
				if (dialog.isDisplayable())
				{
					status.setText(message == null ? "" : message);
					status.setCaretPosition(0);
				}
			};
			if (SwingUtilities.isEventDispatchThread())
			{
				update.run();
			}
			else
			{
				SwingUtilities.invokeLater(update);
			}
		};

		Runnable save = () ->
		{
			if (!authorized.getAsBoolean())
			{
				feedback.accept("You no longer have permission to configure officer access.");
				return;
			}
			char[] password = token.getPassword();
			try
			{
				if (password.length > 0 && (password.length < 24 || password.length > 256))
				{
					feedback.accept("Officer credentials must contain 24 to 256 characters.");
					return;
				}
				try
				{
					if (password.length > 0)
					{
						secrets.save(account, password);
					}
				}
				catch (IOException error)
				{
					feedback.accept("Could not save the credential to a protected local file.");
					return;
				}
				manager.setConfiguration(GROUP, "banSyncEndpoint",
										 FederationBanUploadClient.OFFICIAL_ENDPOINT);
				manager.unsetConfiguration(GROUP, "banSyncToken");
				keyStatus.setText(secrets.read(account).isEmpty()
									  ? "No local credential configured"
									  : "An account-specific local credential is saved.");
				feedback.accept(password.length > 0 ? "Admin key saved for this RuneScape account."
													: "Existing admin key retained.");
			}
			finally
			{
				Arrays.fill(password, '\0');
				token.setText("");
			}
		};
		Runnable request = requestVerification == null ? null : () ->
		{
			if (authorized.getAsBoolean())
			{
				feedback.accept("Starting Discord verification...");
				requestVerification.accept(dialog, feedback);
			}
			else
			{
				feedback.accept("Officer access is no longer available.");
			}
		};
		Runnable check = checkVerification == null ? null : () ->
		{
			if (authorized.getAsBoolean())
			{
				feedback.accept("Checking approval status...");
				checkVerification.accept(dialog, feedback);
			}
			else
			{
				feedback.accept("Officer access is no longer available.");
			}
		};
		Runnable review = reviewRequests == null ? null : () ->
		{
			if (authorized.getAsBoolean())
			{
				feedback.accept("Loading pending officer requests...");
				reviewRequests.accept(dialog, feedback);
			}
			else
			{
				feedback.accept("Officer access is no longer available.");
			}
		};
		JPanel actions = actionButtons(save, request, check, review, dialog::dispose);

		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.setBackground(HolyEmpireTheme.STEEL);
		content.setBorder(BorderFactory.createEmptyBorder(15, 16, 15, 16));
		JLabel heading = new JLabel("HOLY EMPIRE  •  ADMIN VERIFICATION");
		heading.setForeground(HolyEmpireTheme.GOLD);
		heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD, 14f));
		JPanel top = new JPanel(new BorderLayout(0, 11));
		top.setOpaque(false);
		top.add(heading, BorderLayout.NORTH);
		top.add(fields, BorderLayout.CENTER);
		content.add(top, BorderLayout.NORTH);
		content.add(status, BorderLayout.CENTER);
		content.add(actions, BorderLayout.SOUTH);
		dialog.setContentPane(content);
		dialog.pack();
		dialog.setResizable(false);
		HolyEmpireDialogs.centerWindow(parent, dialog);
		try
		{
			dialog.setVisible(true);
		}
		finally
		{
			token.setText("");
			dialog.dispose();
		}
	}
}
