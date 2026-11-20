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
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;

/**
 * The dialog remains open while an officer performs verification actions.
 * The private API remains responsible for all actual authorization decisions.
 */
final class FederationOfficerSettingsDialog
{
	private FederationOfficerSettingsDialog()
	{
	}

	static boolean validEndpoint(String endpoint)
	{
		return endpoint != null && (endpoint.trim().isEmpty() ||
									FederationBanUploadClient.acceptedEndpoint(endpoint.trim()));
	}

	/** Testable persistent action bar: only the explicit Close button closes. */
	static JPanel actionButtons(Runnable request, Runnable check, Runnable review, Runnable close)
	{
		JPanel actions = new JPanel(new GridLayout(0, 2, 8, 8));
		actions.setOpaque(false);
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
		// Fixed-height scrollable feedback avoids clipping wrapped messages at 2x UI scaling.
		JTextArea status = new JTextArea(
			secrets.read(account).isEmpty()
				? "Request verification using Discord. Once approved, your admin "
					  + "credential is saved automatically for this RuneScape character."
				: "An admin credential is stored on this computer for your RuneScape "
					  + "character. It remains valid until an administrator revokes it.",
			4, 38);
		status.setName("holyEmpireVerificationFeedback");
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		status.setEditable(false);
		status.setFocusable(false);
		status.setBackground(HolyEmpireTheme.STEEL);
		status.setForeground(HolyEmpireTheme.MUTED);
		status.setBorder(BorderFactory.createEmptyBorder(5, 3, 5, 3));
		JScrollPane feedbackScroll =
			new JScrollPane(status, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
							JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		feedbackScroll.setBorder(BorderFactory.createEmptyBorder());
		feedbackScroll.setOpaque(false);
		feedbackScroll.getViewport().setBackground(HolyEmpireTheme.STEEL);
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
		JPanel actions = actionButtons(request, check, review, dialog::dispose);

		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.setBackground(HolyEmpireTheme.STEEL);
		content.setBorder(BorderFactory.createEmptyBorder(15, 16, 15, 16));
		JLabel heading = new JLabel("HOLY EMPIRE  •  ADMIN VERIFICATION");
		heading.setForeground(HolyEmpireTheme.GOLD);
		heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD, 14f));
		content.add(heading, BorderLayout.NORTH);
		content.add(feedbackScroll, BorderLayout.CENTER);
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
			dialog.dispose();
		}
	}
}
