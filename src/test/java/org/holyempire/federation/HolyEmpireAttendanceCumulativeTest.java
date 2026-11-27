package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class HolyEmpireAttendanceCumulativeTest
{
    private static final String EVENT="123456789012345678";
    private static Map<String,List<String>> roster()
    {
        Map<String,List<String>> r=new LinkedHashMap<>();
        r.put("host",Collections.singletonList("Holy Empire"));
        r.put("alice",Collections.singletonList("Holy Chaos"));
        r.put("bob",Collections.singletonList("World 465"));
        return r;
    }

    private static JsonObject previous(String event,long lastSeen)
    {
        JsonObject report=new JsonObject();
        report.addProperty("event_id",event);
        report.addProperty("source_sessions",2);
        JsonArray members=new JsonArray();
        JsonObject row=new JsonObject();
        row.addProperty("rsn","alice");
        row.addProperty("chapter","Holy Chaos");
        row.addProperty("ticks",100);
        row.addProperty("last_seen",lastSeen);
        members.add(row);
        report.add("attendees",members);
        return report;
    }

    @Test public void laterTrackingSessionAddsPriorTimeWithoutChangingLocalUpload()
    {
        HolyEmpireAttendanceTracker tracker=new HolyEmpireAttendanceTracker();
        tracker.start(EVENT,"empire","","Event","host",roster(),20000);
        tracker.observe(Collections.singleton("alice"),20600);
        tracker.observe(Collections.singleton("alice"),21200);
        tracker.observe(Collections.singleton("bob"),21800);
        HolyEmpireAttendanceTracker.Report local=tracker.report();
        HolyEmpireAttendanceCumulative.Previous saved=
            HolyEmpireAttendanceCumulative.fromServer(previous(EVENT,10),9);
        List<HolyEmpireAttendanceCumulative.Member> combined=
            HolyEmpireAttendanceCumulative.forDisplay(saved,local);
        Assert.assertEquals(2,combined.size());
        Assert.assertEquals("alice",combined.get(0).name);
        Assert.assertEquals(102L,combined.get(0).ticks);
        Assert.assertEquals("bob",combined.get(1).name);
        Assert.assertEquals(1L,combined.get(1).ticks);
        Assert.assertEquals(2L,local.entries.get(0).ticks);
        Assert.assertEquals(9,saved.number);
        Assert.assertEquals(2,saved.sourceSessions);
    }

    @Test public void overlappingObservationIsNotAddedTwice()
    {
        HolyEmpireAttendanceTracker tracker=new HolyEmpireAttendanceTracker();
        tracker.start(EVENT,"empire","","Event","host",roster(),20000);
        tracker.observe(Collections.singleton("alice"),20600);
        HolyEmpireAttendanceCumulative.Previous saved=
            HolyEmpireAttendanceCumulative.fromServer(previous(EVENT,24),9);
        Assert.assertEquals(100L,
            HolyEmpireAttendanceCumulative.forDisplay(saved,tracker.report()).get(0).ticks);
    }

    @Test public void priorAttendanceNeverCrossesEventOrManualBoundary()
    {
        HolyEmpireAttendanceTracker tracker=new HolyEmpireAttendanceTracker();
        tracker.start("123456789012345679","empire","","Another event","host",roster(),20000);
        tracker.observe(Collections.singleton("bob"),20600);
        HolyEmpireAttendanceCumulative.Previous saved=
            HolyEmpireAttendanceCumulative.fromServer(previous(EVENT,10),9);
        List<HolyEmpireAttendanceCumulative.Member> rows=
            HolyEmpireAttendanceCumulative.forDisplay(saved,tracker.report());
        Assert.assertEquals(1,rows.size());
        Assert.assertEquals("bob",rows.get(0).name);
        Assert.assertEquals(1,rows.get(0).ticks);
    }
}