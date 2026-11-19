package org.holyempire.federation;

import java.util.*;
import javax.swing.Icon;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireChapterCatalogTest {
 private static final String[] EXPECTED={
  "Holy Empire","World 465","House of 465","Holy Guard","Holy Kingdom",
  "Sacred Order","Holy Heart","Six Shooters","Cadia","Holy Cow",
  "Holy Soul","Holy Calf","Holy Lights","Holy Smokes","Sacred Blade",
  "Ultramarines","Holy Chaos"
 };
 @Test public void orderedChapterCardsAreUniqueAndComplete() {
  List<String> actual=new ArrayList<>();
  Set<String> unique=new HashSet<>();
  for(HolyEmpireChapterCatalog.Chapter chapter:HolyEmpireChapterCatalog.ORDERED){
   actual.add(chapter.name);
   assertTrue("Duplicate chapter "+chapter.name,unique.add(chapter.name));
   assertFalse(chapter.subtitle().isEmpty());
   assertNotNull("Missing classpath icon "+chapter.iconKey,
      getClass().getResource("/chapter-icons/"+chapter.iconKey+".png"));
   assertEquals(32,HolyEmpireAssets.chapterIcon(chapter.iconKey).getIconWidth());
  }
  assertArrayEquals(EXPECTED,actual.toArray(new String[0]));
 }
 @Test public void rosterSourceMappingIsUnchanged() {
  Set<String> supplied=new HashSet<>(Arrays.asList(WomDirectoryClient.CLANS));
  for(HolyEmpireChapterCatalog.Chapter chapter:HolyEmpireChapterCatalog.ORDERED)
   assertEquals(!chapter.name.equals("Ultramarines"),chapter.hasWomRoster());
  assertEquals(16,supplied.size());
  assertNull(HolyEmpireChapterCatalog.get("Unknown"));
  assertEquals("F2P friendly",HolyEmpireChapterCatalog.get("Ultramarines").subtitle());
  assertEquals("P2P only · EU/UK clan",HolyEmpireChapterCatalog.get("House of 465").subtitle());
  assertEquals("P2P only · PvP clan",HolyEmpireChapterCatalog.get("Holy Guard").subtitle());
 }
 @Test public void properOsrsRankImagesAreBundled() {
  for(String role:Arrays.asList("owner","deputy_owner","tzkal","vanguard",
     "brigadier","legend","supervisor","bronze","captain")){
   Icon icon=HolyEmpireAssets.rankIcon(role);
   assertNotNull("Missing rank "+role,icon);
   assertTrue("Unexpected rank size "+role,icon.getIconWidth()>0);
  }
  assertNull(HolyEmpireAssets.rankIcon("unranked"));
  assertNull(HolyEmpireAssets.rankIcon("custom_rank_with_no_known_icon"));
 }
}
