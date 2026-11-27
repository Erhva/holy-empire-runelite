package org.holyempire.federation;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationBanResyncReminderTest
{
    private static FederationBanRegistry.Snapshot snapshot(String chapter, String... names)
    {
        return new FederationBanRegistry.Snapshot(chapter, "2026-11-25T00:00:00Z",
            "LIVE_WIDGET", List.of(names));
    }

    @Test public void onlyExplicitModerationMenuActionsPromptSync()
    {
        assertTrue(FederationBanResyncReminder.isModerationAction("Ban"));
        assertTrue(FederationBanResyncReminder.isModerationAction("<col=ff0000>Expel</col>"));
        assertTrue(FederationBanResyncReminder.isModerationAction("Kick from clan"));
        assertTrue(FederationBanResyncReminder.isModerationAction("Ban player"));
        assertTrue(FederationBanResyncReminder.isModerationAction("Remove member"));
        assertFalse(FederationBanResyncReminder.isModerationAction("Ban list"));
        assertFalse(FederationBanResyncReminder.isModerationAction("Unban"));
        assertFalse(FederationBanResyncReminder.isModerationAction("Walk here"));
        assertFalse(FederationBanResyncReminder.isModerationAction("Leave clan"));
        assertFalse(FederationBanResyncReminder.isModerationAction(null));
        int banWidget = (FederationBanWidgetCapture.BANS_GROUP << 16) | 6;
        assertTrue(FederationBanResyncReminder.isNativeBanEditAction("Add",banWidget));
        assertTrue(FederationBanResyncReminder.isNativeBanEditAction("Remove",banWidget));
        assertTrue(FederationBanResyncReminder.isNativeBanEditAction("Unban",banWidget));
        assertFalse(FederationBanResyncReminder.isNativeBanEditAction("Sync Ban List",banWidget));
        assertFalse(FederationBanResyncReminder.isNativeBanEditAction("Add",673 << 16));
    }

    @Test public void onlyConfirmedClanModerationMessagesNotify()
    {
        assertTrue(FederationBanResyncReminder.confirmedClanModeration(
            "<col=ffffff>Example has been banned from the clan.</col>"));
        assertTrue(FederationBanResyncReminder.confirmedClanModeration(
            "Example was expelled from your clan."));
        assertTrue(FederationBanResyncReminder.confirmedClanModeration(
            "Example removed from the clan ban list."));
        assertTrue(FederationBanResyncReminder.confirmedClanModeration(
            "Example added to the clan ban list."));
        assertFalse(FederationBanResyncReminder.confirmedClanModeration(
            "Example has left the clan."));
        assertFalse(FederationBanResyncReminder.confirmedClanModeration(
            "Example was recruited into the clan."));
        assertFalse(FederationBanResyncReminder.confirmedClanModeration(null));
    }

    @Test public void stableNewBanNotifiesOnlyOnce()
    {
        FederationBanResyncReminder watch = new FederationBanResyncReminder();
        var empty = snapshot("Holy Chaos");
        var first = snapshot("Holy Chaos", "MY_SP00N");
        assertFalse(watch.observe(empty));
        assertFalse(watch.observe(empty));
        assertFalse(watch.observe(first));
        assertTrue(watch.observe(first));
        assertFalse(watch.observe(first));
        assertFalse(watch.observe(first));
    }

    @Test public void canonicalVariantsAndUnbansDoNotNotify()
    {
        FederationBanResyncReminder watch = new FederationBanResyncReminder();
        assertFalse(watch.observe(snapshot("Holy Empire", "MY_SP00N")));
        assertFalse(watch.observe(snapshot("Holy Empire", "MY_SP00N")));
        assertFalse(watch.observe(snapshot("Holy Empire", "my sp00n")));
        assertFalse(watch.observe(snapshot("Holy Empire", "my sp00n")));
        assertFalse(watch.observe(snapshot("Holy Empire")));
        assertFalse(watch.observe(snapshot("Holy Empire")));
    }

    @Test public void successfulSyncBaselineDetectsNewBanAndUnban()
    {
        FederationBanResyncReminder watch = new FederationBanResyncReminder();
        var synced = snapshot("Holy Guard","Player A","Player B","Player C");
        watch.synced(synced);
        var removal = snapshot("Holy Guard","Player A","Player C");
        assertFalse(watch.observe(removal));
        assertTrue("Unbanning must remind the officer to sync",watch.observe(removal));
        assertFalse(watch.observe(removal));
        watch.synced(removal);
        assertFalse("Confirming sync clears the outstanding change",watch.observe(removal));
        var added = snapshot("Holy Guard","Player A","Player C","Player D");
        assertFalse(watch.observe(added));
        assertTrue("New bans still remind the officer",watch.observe(added));
    }

    @Test public void lastBanUnbannedRequiresFourConsistentEmptyReads()
    {
        FederationBanResyncReminder watch = new FederationBanResyncReminder();
        watch.synced(snapshot("Holy Guard","Player A"));
        assertFalse(watch.observe(snapshot("Holy Guard")));
        assertFalse(watch.observe(snapshot("Holy Guard")));
        assertFalse(watch.observe(snapshot("Holy Guard")));
        assertTrue("Confirmed zero bans is also a meaningful change",
            watch.observe(snapshot("Holy Guard")));
    }

    @Test public void chapterChangeAndResetEstablishFreshBaseline()
    {
        FederationBanResyncReminder watch = new FederationBanResyncReminder();
        assertFalse(watch.observe(snapshot("Holy Empire")));
        assertFalse(watch.observe(snapshot("Holy Empire")));
        assertFalse(watch.observe(snapshot("Holy Chaos", "one")));
        assertFalse(watch.observe(snapshot("Holy Chaos", "one")));
        watch.reset();
        assertFalse(watch.observe(snapshot("Holy Chaos", "one", "two")));
        assertFalse(watch.observe(snapshot("Holy Chaos", "one", "two")));
    }

    @Test public void emptyWidgetRefillDoesNotProduceFalseNewBan()
    {
        FederationBanResyncReminder watch = new FederationBanResyncReminder();
        var before = snapshot("Sacred Blade", "one");
        var empty = snapshot("Sacred Blade");
        assertFalse(watch.observe(before));
        assertFalse(watch.observe(before));
        assertFalse(watch.observe(empty));
        assertFalse(watch.observe(empty));
        assertFalse(watch.observe(before));
        assertFalse(watch.observe(before));
    }
}
