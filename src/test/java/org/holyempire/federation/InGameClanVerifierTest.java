package org.holyempire.federation;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import org.junit.Test;
import static org.junit.Assert.*;

public class InGameClanVerifierTest {
 private ClanMember member(String name){
  return (ClanMember)Proxy.newProxyInstance(ClanMember.class.getClassLoader(),
   new Class[]{ClanMember.class},(proxy,method,args)->{
    if("getName".equals(method.getName()))return name;
    throw new UnsupportedOperationException(method.getName());
   });
 }
 private ClanSettings settings(String name, boolean direct, String... names){
  final List<ClanMember> roster=Arrays.stream(names).map(this::member).collect(Collectors.toList());
  return (ClanSettings)Proxy.newProxyInstance(ClanSettings.class.getClassLoader(),
   new Class[]{ClanSettings.class},(proxy,method,args)->{
    switch(method.getName()){
     case "getName":return name;
     case "getMembers":return roster;
     case "findMember":return direct && !roster.isEmpty() ? roster.get(0) : null;
     default:throw new UnsupportedOperationException(method.getName());
    }
   });
 }
 @Test public void allSixteenWomChapterNamesAndClanChatNamesResolve(){
  assertEquals(16,WomDirectoryClient.CLANS.length);
  for(int i=0;i<WomDirectoryClient.CLANS.length;i++){
   assertEquals(WomDirectoryClient.CLANS[i],
     InGameClanVerifier.recognizedChapter(WomDirectoryClient.CLANS[i]));
   assertEquals(WomDirectoryClient.CLANS[i],
     InGameClanVerifier.recognizedChapter(WomDirectoryClient.CLAN_CHATS[i]));
  }
  assertEquals("",InGameClanVerifier.recognizedChapter("unrelated"));
  assertEquals("",InGameClanVerifier.recognizedChapter(null));
 }
 @Test public void requiresAccountInTheActualPrimaryClanMembers(){
  assertEquals("Holy Empire",InGameClanVerifier.chapterFor(
    settings("Holy Empire",true,"Alpha","Beta"),"alpha"));
  assertEquals("",InGameClanVerifier.chapterFor(
    settings("Holy Empire",true,"Beta"),"alpha"));
  assertEquals("",InGameClanVerifier.chapterFor(
    settings("Unrelated Clan",true,"Alpha"),"alpha"));
  assertEquals("",InGameClanVerifier.chapterFor(null,"alpha"));
 }
 @Test public void fullRosterFallbackHandlesRenamedOrWhitespaceNormalizations(){
  assertEquals("Holy Chaos",InGameClanVerifier.chapterFor(
    settings("Holy Chaos",false,"Other","Alpha\u00a0Name")," alpha name "));
  assertEquals("",InGameClanVerifier.chapterFor(
    settings("Holy Chaos",false,"Other"),"alpha name"));
 }
 @Test public void matchingChapterIsNotEquivalentToGuestPresence(){
  assertEquals("",InGameClanVerifier.matchingChapter("Some Other Clan","Alpha",
    Collections.singletonList("Alpha")));
  assertEquals("",InGameClanVerifier.matchingChapter("Holy Empire","Alpha",
    Collections.singletonList("Other")));
  assertEquals("Sacred Blade",InGameClanVerifier.matchingChapter("Empire","Alpha",
    Collections.singletonList("Alpha")));
  assertEquals("",InGameClanVerifier.matchingChapter("Holy Empire","Alpha",null));
 }
 @Test public void unavailableIndexedLookupFallsBackToNativeRoster() {
  final List<ClanMember> members=Collections.singletonList(member("Alpha"));
  ClanSettings unavailableIndex=(ClanSettings)Proxy.newProxyInstance(
   ClanSettings.class.getClassLoader(),new Class[]{ClanSettings.class},
   (proxy,method,args)->{
    if("getName".equals(method.getName()))return "Holy Empire";
    if("findMember".equals(method.getName()))
     throw new IllegalStateException("Index not loaded yet");
    if("getMembers".equals(method.getName()))return members;
    throw new UnsupportedOperationException(method.getName());
   });
  assertEquals("Holy Empire",InGameClanVerifier.chapterFor(unavailableIndex,"alpha"));
 }
 @Test public void unavailableNativeMemberListFailsClosed() {
  ClanSettings unavailable=(ClanSettings)Proxy.newProxyInstance(
   ClanSettings.class.getClassLoader(),new Class[]{ClanSettings.class},
   (proxy,method,args)->{
    if("getName".equals(method.getName()))return "Holy Empire";
    if("findMember".equals(method.getName()))return null;
    if("getMembers".equals(method.getName()))return null;
    throw new UnsupportedOperationException(method.getName());
   });
  assertEquals("",InGameClanVerifier.chapterFor(unavailable,"alpha"));
 }

}
