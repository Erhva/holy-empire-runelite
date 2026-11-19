package org.holyempire.federation;

import java.util.List;

/** Shared membership of any verified Holy Empire chapter, not surname/tag matching. */
final class SameChapterVisibility {
 private SameChapterVisibility(){}

 static boolean isSameChapter(List<String> localChapters,List<String> targetChapters){
  if(localChapters==null || localChapters.isEmpty() ||
     targetChapters==null || targetChapters.isEmpty())return false;
  for(String chapter:localChapters){
   if(chapter==null)continue;
   for(String target:targetChapters)
    if(WomDirectoryClient.normalize(chapter).equals(WomDirectoryClient.normalize(target)))
     return true;
  }
  return false;
 }
 static boolean showRankIcon(boolean enabled,boolean sameChapter){
  return enabled && !sameChapter;
 }
 static boolean showMenuTag(boolean enabled,boolean sameChapter,boolean allowSame){
  return enabled && (!sameChapter || allowSame);
 }
 static boolean showOverhead(boolean enabled,boolean sameChapter,boolean allowSame){
  return enabled && (!sameChapter || allowSame);
 }
 static boolean showDot(boolean enabled,boolean sameChapter,boolean allowSame){
  return enabled && (!sameChapter || allowSame);
 }
}
