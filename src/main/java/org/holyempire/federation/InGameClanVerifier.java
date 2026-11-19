package org.holyempire.federation;

import java.util.List;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;

/**
 * Cross-checks the actual primary clan settings against a configured WOM chapter.
 * Guest-clan channel membership is not sufficient. No clan roster is uploaded.
 */
final class InGameClanVerifier {
 private InGameClanVerifier() {}

 static String recognizedChapter(String name) {
  String target = WomDirectoryClient.normalize(name);
  if(target.isEmpty())return "";
  for(int i=0;i<WomDirectoryClient.CLANS.length;i++){
   if(target.equals(WomDirectoryClient.normalize(WomDirectoryClient.CLANS[i])) ||
      target.equals(WomDirectoryClient.normalize(WomDirectoryClient.CLAN_CHATS[i])))
    return WomDirectoryClient.CLANS[i];
  }
  return "";
 }

 static String matchingChapter(String clanName, String account, Iterable<String> memberNames) {
  String chapter=recognizedChapter(clanName);
  String expected=WomDirectoryClient.normalize(account);
  if(chapter.isEmpty() || expected.isEmpty() || memberNames==null)return "";
  for(String memberName:memberNames)
   if(expected.equals(WomDirectoryClient.normalize(memberName)))return chapter;
  return "";
 }

 /** Call on the RuneLite game thread only; unavailable or incomplete data denies access. */
 static String chapterFor(ClanSettings settings, String account) {
  if(settings==null)return "";
  String expected=WomDirectoryClient.normalize(account);
  if(expected.isEmpty())return "";
  try {
   String chapter=recognizedChapter(settings.getName());
   if(chapter.isEmpty())return "";
   // A name index may be unavailable during initial clan loading.
   // Keep the full native member list as an independent fallback.
   try {
    ClanMember direct=settings.findMember(account);
    if(direct!=null && expected.equals(WomDirectoryClient.normalize(direct.getName())))
     return chapter;
   } catch(RuntimeException indexUnavailable) {
    // Fall back to the actual game member list, not WOM.
   }
   List<ClanMember> members=settings.getMembers();
   if(members==null)return "";
   for(ClanMember member:members)
    if(member!=null && expected.equals(WomDirectoryClient.normalize(member.getName())))
     return chapter;
  } catch(RuntimeException unavailable) {
   // A clan roster may disappear while the game changes state.
  }
  return "";
 }
}
