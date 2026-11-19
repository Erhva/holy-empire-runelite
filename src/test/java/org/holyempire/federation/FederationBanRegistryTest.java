/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.time.Instant;
import java.util.*;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationBanRegistryTest
{
	private static final Instant TIME = Instant.parse("2026-11-16T16:00:00Z");

	private static FederationBanRegistry.Snapshot sample(String chapter, String... names)
	{
		return FederationBanRegistry.fromVisibleWidget(chapter, Arrays.asList(names), TIME);
	}

	private static void bad(Runnable work)
	{
		try
		{
			work.run();
			fail("Expected rejection");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	@Test
	public void capturesAndMatchesExactNormalizedNamesNotSubstrings()
	{
		FederationBanRegistry store = new FederationBanRegistry();
		FederationBanRegistry.Snapshot one =
			sample("hOly chaos", " Not Needed ", "Other Player", "other player", "",
				   "<col>Widget</col>", "Bad_Player");
		assertEquals("Holy Chaos", one.chapter);
		assertEquals(3, one.names.size());
		store.put(one);
		store.put(sample("Holy Guard", "other player"));
		assertEquals(4, store.total());
		assertEquals(2, store.chapterCount());
		assertEquals(2, store.matches("  OTHER     PLAYER ").size());
		assertEquals(1, store.matches("not needed").size());
		assertTrue(store.matches("other").isEmpty());
		assertTrue(store.matches("not needed 2").isEmpty());
		assertTrue(store.matches("=HYPERLINK").isEmpty());
		assertTrue(store.matches(null).isEmpty());
	}

	@Test
	public void widgetCaptureRejectsUnknownAndEmptyWithoutOverwriting()
	{
		FederationBanRegistry store = new FederationBanRegistry();
		store.put(sample("Holy Empire", "FirstUser"));
		bad(()
				-> FederationBanRegistry.fromVisibleWidget(
					"Wrong Clan", Collections.singletonList("Banned"), TIME));
		bad(()
				-> FederationBanRegistry.fromVisibleWidget(
					"Holy Empire", Arrays.asList("", "<img=3>", "not a valid rsn 222222222"),
					TIME));
		List<String> excessive = new ArrayList<>();
		for (int i = 0; i < 101; i++)
			excessive.add("rsn" + i);
		bad(() -> FederationBanRegistry.fromVisibleWidget("Holy Empire", excessive, TIME));
		assertEquals(1, store.total());
		assertEquals(1, store.matches("FirstUser").size());
		assertNull(FederationBanRegistry.validName("bad,name"));
		assertNull(FederationBanRegistry.validName("\\nLine"));
	}

	@Test
	public void exportImportRetainsChapterMetadataAndRejectsPartialUpdates()
	{
		FederationBanRegistry original = new FederationBanRegistry();
		original.put(sample("Holy Empire", "Banned One", "Banned Two"));
		original.put(sample("Holy Guard", "Banned One"));
		String csv = original.toCsv();
		assertTrue(csv.startsWith(FederationBanRegistry.CSV_HEADER + "\n"));
		assertTrue(csv.contains("Holy Guard,Banned One,"));
		FederationBanRegistry other = new FederationBanRegistry();
		assertEquals(3, other.importCsv(csv));
		assertEquals(2, other.chapterCount());
		assertEquals(2, other.matches("banned one").size());
		assertEquals("IMPORTED_FILE", other.all().get(0).source);
		String previous = other.toCsv();
		bad(()
				-> other.importCsv(FederationBanRegistry.CSV_HEADER +
								   "\nHoly Empire,One,2026-11-16T16:00:00Z,LIVE_WIDGET\n"
								   + "Fake Chapter,Other,2026-11-16T16:00:00Z,LIVE_WIDGET\n"));
		assertEquals(previous, other.toCsv());
		bad(()
				-> other.importCsv(FederationBanRegistry.CSV_HEADER +
								   "\nHoly Empire,=bad,2026-11-16T16:00:00Z,LIVE_WIDGET\n"));
		bad(()
				-> other.importCsv(FederationBanRegistry.CSV_HEADER +
								   "\nHoly Empire,One,2026-11-16T16:00:00Z,LIVE_WIDGET\n"
								   + "Holy Empire,ONE,2026-11-16T16:00:00Z,LIVE_WIDGET\n"));
		bad(()
				-> other.importCsv(FederationBanRegistry.CSV_HEADER +
								   "\nHoly Empire,One,not-a-date,LIVE_WIDGET\n"));
		assertEquals(previous, other.toCsv());
	}

	@Test
	public void toolsShowWarningAndCaptureOnlyOnExplicitClick() throws Exception
	{
		FederationBanRegistry registry = new FederationBanRegistry();
		final boolean[] invoked = {false};
		FederationBanToolsPanel[] panel = {null};
		SwingUtilities.invokeAndWait(() -> {
			panel[0] = new FederationBanToolsPanel(registry, (success, failed) -> {
				invoked[0] = true;
				success.accept(sample("Holy Heart", "Candidate"));
			});
			assertFalse(invoked[0]);
			JTextField input = (JTextField)find(panel[0], "holyEmpireBanCandidate");
			assertNotNull(input);
			input.setText("Candidate");
			JTextArea warning = (JTextArea)find(panel[0], "holyEmpireBanWarning");
			assertTrue(warning.getText().contains("No match"));
			panel[0].capture();
		});
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			assertTrue(invoked[0]);
			assertEquals(1, registry.total());
			JTextArea warning = (JTextArea)find(panel[0], "holyEmpireBanWarning");
			assertTrue(warning.getText().contains("WARNING"));
			assertTrue(warning.getText().contains("Holy Heart"));
		});
	}

	@Test
	public void globalSearchShowsWarningOnlyForExactBanMatches() throws Exception
	{
		FederationPanel[] p = {null};
		SwingUtilities.invokeAndWait(() -> p[0] = new FederationPanel());
		p[0].banRegistry().put(sample("Holy Chaos", "New Recruit"));
		p[0].update(Collections.singletonMap("member", Collections.singletonList("Holy Empire")),
					Collections.emptyMap());
		SwingUtilities.invokeAndWait(() -> {});
		SwingUtilities.invokeAndWait(() -> {
			JTextField search = (JTextField)find(p[0], "holyEmpireGlobalSearch");
			search.setText("New Recruit");
			JTextArea warning = (JTextArea)find(p[0], "holyEmpireRecruitWarning");
			assertNotNull(warning);
			assertTrue(warning.getText().contains("Holy Chaos"));
			search.setText("New");
			assertNull(find(p[0], "holyEmpireRecruitWarning"));
			assertNull(find(p[0], "holyEmpireBanTools"));
		});
	}

	private static java.awt.Component find(java.awt.Container root, String key)
	{
		for (java.awt.Component c : root.getComponents())
		{
			if (key.equals(c.getName()))
				return c;
			if (c instanceof java.awt.Container)
			{
				java.awt.Component nested = find((java.awt.Container)c, key);
				if (nested != null)
					return nested;
			}
		}
		return null;
	}
}
