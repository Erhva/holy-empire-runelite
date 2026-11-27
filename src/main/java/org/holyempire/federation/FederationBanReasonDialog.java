/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import java.awt.Component;
import java.awt.Dimension;
import java.util.LinkedHashSet;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Centered post-sync workflow. Neither storing nor posting occurs on form creation. */
final class FederationBanReasonDialog
{
	static final class Choice
	{
		final String rsn;
		final String category;
		final String reason;
		final boolean publish;
		final boolean another;

		Choice(String rsn, String category, String reason, boolean publish, boolean another)
		{
			this.rsn = rsn;
			this.category = category;
			this.reason = reason;
			this.publish = publish;
			this.another = another;
		}
	}

	static final class Form
	{
		final JPanel panel = new JPanel();
		final JComboBox<String> player;
		final JComboBox<String> category = new JComboBox<>(
			new String[]{"Begging", "Scamming/Luring", "Botting/RWT", "Harassment", "Other"});
		final JTextArea reason = new JTextArea(4, 32);
		final JCheckBox publish = new JCheckBox(
			"Post this entry via Servo-Skull to #ban-list");
		final JLabel counter = SwingTextSafety.plainLabel("0 / 400");

		Form(FederationBanRegistry.Snapshot snapshot, FederationBanUploadClient.Receipt receipt)
		{
			LinkedHashSet<String> order = new LinkedHashSet<>(receipt.newBans);
			order.addAll(snapshot.names);
			player = new JComboBox<>(order.toArray(new String[0]));
			player.setName("holyEmpireBanReasonPlayer");
			category.setName("holyEmpireBanReasonCategory");
			// Deliberately no preselected reason category. The admin must
			// make an affirmative choice rather than saving Begging by default.
			category.setSelectedIndex(-1);
			reason.setName("holyEmpireBanReasonText");
			publish.setName("holyEmpireBanReasonPublish");
			counter.setName("holyEmpireBanReasonCount");
			publish.setSelected(false);
			panel.setName("holyEmpireBanReasonForm");
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
			panel.add(SwingTextSafety.plainLabel(receipt.message));
			panel.add(Box.createVerticalStrut(8));
			panel.add(SwingTextSafety.plainLabel(
				"Add a staff-only ban reason for any synced user (new bans first)."));
			panel.add(Box.createVerticalStrut(7));
			panel.add(SwingTextSafety.plainLabel("Banned RuneScape username"));
			panel.add(player);
			panel.add(Box.createVerticalStrut(6));
			panel.add(SwingTextSafety.plainLabel("Category"));
			panel.add(category);
			panel.add(Box.createVerticalStrut(6));
			panel.add(SwingTextSafety.plainLabel("Reason (optional)"));
			reason.setLineWrap(true);
			reason.setWrapStyleWord(true);
			JScrollPane scroll = new JScrollPane(reason);
			scroll.setPreferredSize(new Dimension(350, 95));
			panel.add(scroll);
			panel.add(counter);
			reason.getDocument().addDocumentListener(new DocumentListener()
			{
				private void update() { counter.setText(reason.getText().length() + " / 400"); }
				@Override public void insertUpdate(DocumentEvent e) { update(); }
				@Override public void removeUpdate(DocumentEvent e) { update(); }
				@Override public void changedUpdate(DocumentEvent e) { update(); }
			});
			panel.add(Box.createVerticalStrut(9));
			panel.add(publish);
			panel.add(SwingTextSafety.plainLabel(
				"Posting is optional and requires your explicit confirmation."));
		}

		Choice choice(boolean another)
		{
			String rsn = (String)player.getSelectedItem();
			String selected = (String)category.getSelectedItem();
			String entered = reason.getText().trim();
			// Clicking Save with no input is equivalent to Skip: do not
			// create a category, reason, or Discord notice for this ban.
			if (selected == null && entered.isEmpty() && !publish.isSelected())
				return null;
			if (FederationBanRegistry.validName(rsn) == null ||
				!FederationBanReasonClient.CATEGORIES.contains(selected) ||
				entered.length() > 400)
				throw new IllegalArgumentException("Choose a valid ban and a reason up to 400 characters.");
			return new Choice(rsn,selected,entered,publish.isSelected(),another);
		}
	}

	private FederationBanReasonDialog() {}

	/** Returns null if the admin skips. Never posts without two explicit actions. */
	static Choice ask(Component parent, FederationBanRegistry.Snapshot snapshot,
					  FederationBanUploadClient.Receipt receipt)
	{
		if (snapshot.names.isEmpty()) return null;
		Form form = new Form(snapshot,receipt);
		String[] choices = {"Save and finish", "Save and add another", "Skip"};
		while (true)
		{
			int selected = HolyEmpireDialogs.showOptionDialog(
				parent, form.panel, "Holy Empire Ban Reason",
				JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
				null, choices, choices[2]);
			if (selected != 0 && selected != 1) return null;
			Choice choice;
			try
			{
				choice = form.choice(selected == 1);
			}
			catch (IllegalArgumentException problem)
			{
				HolyEmpireDialogs.showMessageDialog(parent,problem.getMessage(),
					"Invalid Ban Reason",JOptionPane.WARNING_MESSAGE);
				continue; // Retain the admin's typed text and dropdown state.
			}
			if (choice == null) return null; // Blank form: no annotation or post.
			if (choice.publish)
			{
				String details = "Servo-Skull will post this entry to #ban-list.\n" +
					"Username: " + choice.rsn + "\nCategory: " + choice.category +
					"\nReason: " + (choice.reason.isEmpty() ? "Not specified" : choice.reason) +
					"\n\nPublish this record?";
				if (HolyEmpireDialogs.showConfirmDialog(parent, details,
					"Confirm Discord Publication",JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
					return null;
			}
			return choice;
		}
	}
}
