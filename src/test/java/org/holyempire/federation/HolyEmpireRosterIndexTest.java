package org.holyempire.federation;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireRosterIndexTest {
 private Map<String,List<String>> sample(){
  Map<String,List<String>> roster=new HashMap<>();
  roster.put("alpha",Arrays.asList("Holy Empire","Cadia"));
  roster.put("beta",Collections.singletonList("Holy Chaos"));
  roster.put("gamma",Collections.singletonList("World 465"));
  return roster;
 }
 @Test public void globalSearchFindsPlayersAcrossCCs(){
  assertEquals(Collections.singletonList("gamma"),HolyEmpireRosterIndex.search(sample(),"GAM"));
 }
 @Test public void globalSearchOnlyMatchesPlayerNames(){
  assertEquals(Collections.emptyList(),HolyEmpireRosterIndex.search(sample(),"CaDiA"));
  assertEquals(Collections.emptyList(),HolyEmpireRosterIndex.search(sample(),"World 465"));
  assertEquals(Collections.emptyList(),HolyEmpireRosterIndex.search(sample(),"Holy"));
  Map<String,List<String>> names=sample();
  names.put("holy spartan",Collections.singletonList("Cadia"));
  assertEquals(Collections.singletonList("holy spartan"),HolyEmpireRosterIndex.search(names,"holy"));
 }
 @Test public void emptyQuerySearchesWholeRoster(){
  assertEquals(Arrays.asList("alpha","beta","gamma"),HolyEmpireRosterIndex.search(sample(),""));
 }
 @Test public void eachCCContainsOnlyItsOwnMembers(){
  assertEquals(Collections.singletonList("alpha"),HolyEmpireRosterIndex.inClan(sample(),"Holy Empire"));
  assertEquals(Collections.singletonList("alpha"),HolyEmpireRosterIndex.inClan(sample(),"Cadia"));
  assertEquals(Collections.emptyList(),HolyEmpireRosterIndex.inClan(sample(),"Holy Soul"));
 }
 @Test public void aPlayerInMultipleChaptersStillAppearsOnce(){
  assertEquals(Collections.singletonList("alpha"),HolyEmpireRosterIndex.search(sample(),"alpha"));
  assertEquals(Collections.emptyList(),HolyEmpireRosterIndex.search(sample(),"Holy Empire"));
 }

}
