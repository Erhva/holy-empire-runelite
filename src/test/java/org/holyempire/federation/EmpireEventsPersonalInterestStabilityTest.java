package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

/** Background Discord OAuth renewal must not cause the Interested icons to blink. */
public class EmpireEventsPersonalInterestStabilityTest
{
    private static final String ID = "1573933979667538011";
    private static final String OTHER = "1573933979667538012";
    private static final Instant START = Instant.now().plusSeconds(7200);

    private static Component named(Component root, String name)
    {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container)
            for (Component child : ((Container)root).getComponents())
            {
                Component result = named(child, name);
                if (result != null) return result;
            }
        return null;
    }

    private static DiscordEventsClient.Event event(String id, String rsvp)
    {
        return new DiscordEventsClient.Event("empire", "", id, "Event " + id, "",
            "scheduled", "World 465", START, START.plusSeconds(3600),
            "https://discord.com/events/736564064834289774/" + id,
            "", 0, 0, 0, rsvp, 42);
    }

    private static DiscordEventsClient.Snapshot publicFeed()
    {
        return new DiscordEventsClient.Snapshot(123,
            List.of(event(ID, ""), event(OTHER, "")));
    }

    private static DiscordEventsClient.Snapshot marked()
    {
        return DiscordEventsClient.withPersonalEmpireInterest(
            publicFeed(), Set.of(ID));
    }

    private static void flush() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static void assertInterested(FederationPanel panel, boolean interested)
        throws Exception
    {
        flush();
        SwingUtilities.invokeAndWait(() -> {
            JLabel badge = (JLabel)named(panel, "holyEmpireEventPersonalRsvp:" + ID);
            JLabel bell = (JLabel)named(panel, "holyEmpireEventDiscordBell:" + ID);
            JLabel other = (JLabel)named(panel, "holyEmpireEventPersonalRsvp:" + OTHER);
            assertNotNull(badge);
            assertNotNull(bell);
            assertNotNull(other);
            assertEquals(interested ? "✓" : "", badge.getText());
            assertEquals("", other.getText());
            javax.swing.border.LineBorder border =
                (javax.swing.border.LineBorder)bell.getBorder();
            assertEquals(interested ? HolyEmpireTheme.RSVP_GOING_EDGE
                : HolyEmpireTheme.SILVER_EDGE, border.getLineColor());
        });
    }

    @Test public void sameDiscordAuthorityAndTransientFailureKeepConfirmedIndicators()
        throws Exception
    {
        FederationPanel panel = new FederationPanel();
        try
        {
            panel.update(Map.of("Tester", List.of("Holy Empire")), Map.of());
            flush();
            SwingUtilities.invokeAndWait(() ->
                ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
            panel.displayEvents(marked(), () -> true);
            assertInterested(panel, true);

            // OAuth bearer rotated but member/chapter/roles are unchanged.
            panel.chapterEventsConnected(() -> true, true);
            assertInterested(panel, true);

            // Public feed succeeded but a transient private API request failed.
            panel.displayEvents(publicFeed(), () -> true, true);
            assertInterested(panel, true);

            // A successful authenticated response with no subscription
            // must remove the old green mark.
            panel.displayEvents(publicFeed(), () -> true, false);
            assertInterested(panel, false);
        }
        finally { panel.stopPrivateCacheTimer(); }
    }

    @Test public void newDiscordAuthorityCannotInheritEarlierSubscribers() throws Exception
    {
        FederationPanel panel = new FederationPanel();
        try
        {
            panel.update(Map.of("Tester", List.of("Holy Empire")), Map.of());
            flush();
            SwingUtilities.invokeAndWait(() ->
                ((JToggleButton)named(panel, "holyEmpireEventsTab")).doClick());
            panel.displayEvents(marked(), () -> true);
            assertInterested(panel, true);
            panel.chapterEventsConnected(() -> true);
            assertInterested(panel, false);
            panel.displayEvents(publicFeed(), () -> true, true);
            assertInterested(panel, false);
        }
        finally { panel.stopPrivateCacheTimer(); }
    }

    @Test public void transientFailureOnlyRetainsKnownMatchingEventIds()
    {
        DiscordEventsClient.Snapshot stale = marked();
        DiscordEventsClient.Snapshot newPublic =
            new DiscordEventsClient.Snapshot(555, List.of(event(OTHER, "")));
        DiscordEventsClient.Snapshot merged =
            DiscordEventsClient.retainConfirmedEmpireInterest(newPublic, stale);
        assertEquals(1, merged.events.size());
        assertEquals(OTHER, merged.events.get(0).id);
        assertEquals("", merged.events.get(0).myRsvp);
        assertEquals(555, merged.generatedAt);
        assertEquals("", DiscordEventsClient.retainConfirmedEmpireInterest(
            newPublic, null).events.get(0).myRsvp);
    }
}
