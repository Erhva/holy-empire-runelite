/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmpireEventsStableRefreshTest
{
	private static Component find(Component root, String name)
	{
		if (name.equals(root.getName()))
			return root;
		if (root instanceof Container)
			for (Component child : ((Container)root).getComponents())
			{
				Component result = find(child, name);
				if (result != null)
					return result;
			}
		return null;
	}

	private static final Instant START = Instant.now().plusSeconds(9000);

	private static DiscordEventsClient.Event event(String title)
	{
		Instant start = START;
		return new DiscordEventsClient.Event("empire", "", "1573933979667538011", title, "Details",
											 "scheduled", "World 465", start,
											 start.plusSeconds(3600),
											 "https://discord.com/events/293070715706671104/"
												 + "1573933979667538011");
	}

	@Test public void identicalFeedKeepsCardInstanceAndChangedFeedReplacesIt() throws Exception
	{
		FederationPanel[] view = new FederationPanel[1];
		SwingUtilities.invokeAndWait(() -> view[0] = new FederationPanel());
		try
		{
			view[0].update(
				Collections.singletonMap("tester", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			SwingUtilities.invokeAndWait(
				() -> { ((JToggleButton)find(view[0], "holyEmpireEventsTab")).doClick(); });
			DiscordEventsClient.Event one = event("First");
			view[0].displayEvents(new DiscordEventsClient.Snapshot(200, List.of(one)), () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			Component[] before = new Component[1];
			SwingUtilities.invokeAndWait(() -> {
				before[0] = find(view[0], "holyEmpireEvent:" + one.id);
				assertNotNull(before[0]);
			});
			view[0].displayEvents(new DiscordEventsClient.Snapshot(201, List.of(event("First"))),
								  () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertSame("No recreating or flickering cards on unchanged feed", before[0],
								  find(view[0], "holyEmpireEvent:" + one.id)));
			view[0].displayEvents(new DiscordEventsClient.Snapshot(202, List.of(event("Renamed"))),
								  () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				()
					-> assertNotSame("A genuinely changed card should update", before[0],
									 find(view[0], "holyEmpireEvent:" + one.id)));
		}
		finally
		{
			view[0].stopPrivateCacheTimer();
		}
	}
}
