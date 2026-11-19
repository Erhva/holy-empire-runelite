package org.holyempire.federation;

import java.awt.Color;
import java.util.List;

/** Deterministic per-chapter indicator colors; older blue settings remain the fallback. */
final class ChapterIndicatorColors {
 private ChapterIndicatorColors(){}
 static String primaryChapter(List<String> clans) {
  if(clans==null)return null;
  for(HolyEmpireChapterCatalog.Chapter chapter:HolyEmpireChapterCatalog.ORDERED)
   if(clans.contains(chapter.name))return chapter.name;
  return null;
 }
 static Color dotColor(FederationConfig config,List<String> clans){
  if(!config.useChapterDotColors())return config.memberColor();
  String chapter=primaryChapter(clans);
  if(chapter==null)return config.memberColor();
  switch(chapter){
   case "Holy Empire":return config.dotColorHolyEmpire();
   case "World 465":return config.dotColorWorld465();
   case "House of 465":return config.dotColorHouseOf465();
   case "Holy Guard":return config.dotColorHolyGuard();
   case "Holy Kingdom":return config.dotColorHolyKingdom();
   case "Sacred Order":return config.dotColorSacredOrder();
   case "Holy Heart":return config.dotColorHolyHeart();
   case "Six Shooters":return config.dotColorSixShooters();
   case "Cadia":return config.dotColorCadia();
   case "Holy Cow":return config.dotColorHolyCow();
   case "Holy Soul":return config.dotColorHolySoul();
   case "Holy Calf":return config.dotColorHolyCalf();
   case "Holy Lights":return config.dotColorHolyLights();
   case "Holy Smokes":return config.dotColorHolySmokes();
   case "Sacred Blade":return config.dotColorSacredBlade();
   case "Ultramarines":return config.dotColorUltramarines();
   case "Holy Chaos":return config.dotColorHolyChaos();
   default:return config.memberColor();
  }
 }
 /** All enabled chapter indicators use the same configured color. */
 static Color overheadColor(FederationConfig config,List<String> clans){
  return dotColor(config,clans);
 }
 static Color menuColor(FederationConfig config,List<String> clans){
  return dotColor(config,clans);
 }
}
