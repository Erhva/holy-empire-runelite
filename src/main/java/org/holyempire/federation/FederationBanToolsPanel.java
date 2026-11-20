/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.util.Filepath;

/** Opt-in, local ban-list capture/import/export and pre-recruit lookup. */
final class FederationBanToolsPanel extends JPanel
{
	interface Capture
	{
		void request(Consumer<FederationBanRegistry.Snapshot> success, Consumer<String> failed);
	}
	private final FederationBanRegistry registry;
	private final Capture capture;
	private final JButton captureButton = HolyEmpireTheme.button("Capture open Bans", true);
	private final JButton exportButton = HolyEmpireTheme.button("Export CSV", true);
	private final JButton importButton = HolyEmpireTheme.button("Import CSV", true);
	private final JTextField candidate = new JTextField(18);
	private final JLabel summary = new JLabel();
	private final JTextArea feedback = new JTextArea();
	private final JTextArea warning = new JTextArea();

	FederationBanToolsPanel(FederationBanRegistry registry, Capture capture)
	{
		this.registry = registry;
		this.capture = capture;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(HolyEmpireTheme.STEEL);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 1, HolyEmpireTheme.GOLD_EDGE),
			BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		JLabel instruction = new JLabel("In RuneScape: your clan → Settings → Bans");
		instruction.setForeground(HolyEmpireTheme.GOLD);
		add(instruction);
		add(Box.createVerticalStrut(6));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
		buttons.setOpaque(false);
		buttons.add(captureButton);
		buttons.add(exportButton);
		buttons.add(importButton);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
		add(buttons);
		summary.setName("holyEmpireBanSummary");
		summary.setForeground(HolyEmpireTheme.MUTED);
		add(summary);
		feedback.setName("holyEmpireBanFeedback");
		feedback.setOpaque(false);
		feedback.setForeground(HolyEmpireTheme.MUTED);
		feedback.setEditable(false);
		feedback.setLineWrap(true);
		feedback.setWrapStyleWord(true);
		feedback.setRows(3);
		feedback.setColumns(33);
		feedback.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		add(feedback);
		add(Box.createVerticalStrut(7));
		JLabel lookup = new JLabel("Check a potential recruit against loaded ban lists:");
		lookup.setForeground(HolyEmpireTheme.GOLD);
		add(lookup);
		candidate.setName("holyEmpireBanCandidate");
		HolyEmpireTheme.styleSearch(candidate);
		candidate.putClientProperty("JTextField.placeholderText", "RuneScape name");
		candidate.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		add(candidate);
		warning.setName("holyEmpireBanWarning");
		warning.setOpaque(false);
		warning.setEditable(false);
		warning.setLineWrap(true);
		warning.setWrapStyleWord(true);
		warning.setRows(3);
		warning.setColumns(32);
		warning.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
		add(warning);
		add(Box.createVerticalStrut(8));
		JLabel note = new JLabel("Local snapshots only; imported lists require staff review.");
		note.setForeground(HolyEmpireTheme.MUTED);
		add(note);
		captureButton.addActionListener(e -> capture());
		exportButton.addActionListener(e -> exportCsv());
		importButton.addActionListener(e -> importCsv());
		candidate.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e)
			{
				updateWarning();
			}
			public void removeUpdate(DocumentEvent e)
			{
				updateWarning();
			}
			public void changedUpdate(DocumentEvent e)
			{
				updateWarning();
			}
		});
		refresh();
	}

	void capture()
	{
		if (capture == null)
		{
			message("Capture is unavailable in this test session.");
			return;
		}
		captureButton.setEnabled(false);
		capture.request(s
						-> SwingUtilities.invokeLater(() -> {
			registry.put(s);
			captureButton.setEnabled(true);
			message("Captured " + s.names.size() + " banned name(s) from " + s.chapter + " at " +
					s.timestamp + ". Review, then export.");
			refresh();
		}),
						reason -> SwingUtilities.invokeLater(() -> {
							captureButton.setEnabled(true);
							message("Capture refused: " + reason);
							refresh();
						}));
	}

	void refresh()
	{
		summary.setText("Loaded: " + registry.total() + " names across " + registry.chapterCount() +
						" chapter snapshot(s)");
		exportButton.setEnabled(registry.total() > 0);
		updateWarning();
	}

	void message(String text)
	{
		feedback.setText(text);
	}

	void updateWarning()
	{
		String name = candidate.getText().trim();
		if (name.isEmpty())
		{
			warning.setText("Enter a name to check. No captured list means no clearance.");
			warning.setForeground(Color.LIGHT_GRAY);
			return;
		}
		List<String> matches = registry.matches(name);
		if (matches.isEmpty())
		{
			warning.setText("No match in " + registry.chapterCount() +
							" loaded chapter list(s). Other or older bans may still exist.");
			warning.setForeground(Color.LIGHT_GRAY);
		}
		else
		{
			warning.setText("WARNING: " + name + " appears on the ban list of " +
							String.join("; ", matches) +
							". Verify with chapter staff before recruiting.");
			warning.setForeground(new Color(240, 100, 100));
		}
	}

	private void exportCsv()
	{
		try
		{
			List<Filepath> selected = new Filepath.Chooser()
										  .setIsSave()
										  .setAcceptsFiles()
										  .setDialogTitle("Export Holy Empire Ban Lists")
										  .setDefaultExtension("csv")
										  .setFileName("holy-empire-ban-lists.csv")
										  .addExtensionFilter("CSV files", "csv")
										  .showDialog(this);
			if (selected.isEmpty())
			{
				return;
			}
			selected.get(0).write(registry.toCsv(), StandardOpenOption.CREATE,
								  StandardOpenOption.TRUNCATE_EXISTING);
			message("Exported " + registry.total() + " name(s) to " +
					selected.get(0).getFileName());
		}
		catch (IOException | RuntimeException ex)
		{
			message("CSV export failed: " + ex.getMessage());
		}
	}

	private void importCsv()
	{
		try
		{
			List<Filepath> selected = new Filepath.Chooser()
										  .setIsOpen()
										  .setAcceptsFiles()
										  .setDialogTitle("Import Staff Ban List CSV")
										  .addExtensionFilter("CSV files", "csv")
										  .showDialog(this);
			if (selected.isEmpty())
			{
				return;
			}
			Filepath path = selected.get(0);
			if (path.size() > 256_000)
			{
				message("CSV exceeds 256KB safety limit.");
				return;
			}
			StringBuilder data = new StringBuilder();
			try (BufferedReader input = path.openBufferedReader())
			{
				String line;
				int count = 0;
				while ((line = input.readLine()) != null)
				{
					if (++count > 1705)
					{
						message("Too many CSV rows.");
						return;
					}
					data.append(line).append('\n');
				}
			}
			int confirm = HolyEmpireDialogs.showConfirmDialog(
				this,
				"Importing a file replaces local snapshots for chapters in that file. "
					+ "Only import administrator-reviewed exports. Continue?",
				"Import Chapter Ban Lists", JOptionPane.YES_NO_OPTION);
			if (confirm != JOptionPane.YES_OPTION)
			{
				return;
			}
			int entries = registry.importCsv(data.toString());
			message("Imported " + entries + " banned name(s) from staff-provided CSV. "
					+ "These are labeled IMPORTED_FILE until verified against the game.");
			refresh();
		}
		catch (IOException | RuntimeException ex)
		{
			message("Import refused: " + ex.getMessage());
		}
	}
}
