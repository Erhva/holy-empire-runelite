/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Success and failure feedback belongs below the Publish Announcement control. */
public class HolyEmpireAnnouncementPublicationStatusUiTest
{
	private static Component named(Component node, String id)
	{
		if (id.equals(node.getName()))
			return node;
		if (node instanceof Container)
		{
			for (Component child : ((Container)node).getComponents())
			{
				Component result = named(child, id);
				if (result != null)
					return result;
			}
		}
		return null;
	}

	private static void feedbackBelowPublish(FederationPanel panel, String message)
	{
		JButton submit = (JButton)named(panel, "holyEmpireAdminPublish:announcement");
		JTextArea result = (JTextArea)named(panel, "holyEmpireAdminPublishStatus");
		assertNotNull(submit);
		assertNotNull(result);
		assertEquals(message, result.getText());
		assertSame("Feedback must be in the same vertical container as the button",
				   submit.getParent(), result.getParent());
		java.util.List<Component> siblings = Arrays.asList(submit.getParent().getComponents());
		assertEquals("Feedback should immediately follow Publish Announcement",
					 siblings.indexOf(submit) + 1, siblings.indexOf(result));
	}

	@Test public void announcementFeedbackIsDirectlyBelowPublishButton() throws Exception
	{
		FederationPanel[] panels = {null};
		SwingUtilities.invokeAndWait(() -> {
			panels[0] = new FederationPanel();
			panels[0].setAdminPublishing((type, title, body) -> {}, () -> true, () -> {});
		});
		FederationPanel panel = panels[0];
		try
		{
			panel.update(
				Collections.singletonMap("Officer", Collections.singletonList("Holy Empire")),
				Collections.emptyMap());
			panel.setAdminEligible(true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(() -> {
				((JToggleButton)named(panel, "holyEmpireAdminControlsTab")).doClick();
				assertNotNull(named(panel, "holyEmpireAdminPublish:announcement"));
				assertNull(named(panel, "holyEmpireAdminPublishStatus"));
			});
			panel.showPublishStatus("Published announcement #1.", () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> feedbackBelowPublish(panel, "Published announcement #1."));
			panel.showPublishStatus("Publishing failed: Network error.", () -> true);
			SwingUtilities.invokeAndWait(() -> {});
			SwingUtilities.invokeAndWait(
				() -> feedbackBelowPublish(panel, "Publishing failed: Network error."));
		}
		finally
		{
			panel.stopPrivateCacheTimer();
		}
	}
}
