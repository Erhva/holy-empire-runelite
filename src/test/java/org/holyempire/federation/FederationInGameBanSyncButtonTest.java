package org.holyempire.federation;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationInGameBanSyncButtonTest {
 @SuppressWarnings("unchecked")
 static <T> T fake(Class<T> type, java.lang.reflect.InvocationHandler invoke){
  return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},invoke);
 }
 static class WidgetState {
  List<Widget> children=new ArrayList<>();
  List<WidgetState> childStates=new ArrayList<>();
  Widget root;
  String text;
  String name;
  boolean hidden;
  JavaScriptCallback click;

  WidgetState(){
   root=fake(Widget.class,(p,m,args)->{
    if(m.getName().equals("getChildren"))return children.toArray(new Widget[0]);
    if(m.getName().equals("createChild")){
     WidgetState child=new WidgetState(false);
     children.add(child.root);
     childStates.add(child);
     return child.root;
    }
    if(m.getName().equals("isHidden"))return hidden;
    if(m.getName().equals("toString"))return "fake-root";
    return null;
   });
  }
  WidgetState(boolean simple){
   root=fake(Widget.class,(p,m,args)->{
    switch(m.getName()){
     case "setName":name=(String)args[0];return root;
     case "getName":return name;
     case "setText":text=(String)args[0];return root;
     case "getText":return text;
     case "setOnOpListener":click=(JavaScriptCallback)((Object[])args[0])[0];return null;
     case "isSelfHidden":return hidden;
     case "setHidden":hidden=(Boolean)args[0];return root;
     case "isHidden":return hidden;
     case "setHasListener":return root;
     case "toString":return "fake-button";
     default: return null;
    }
   });
  }
 }
 static Client client(String chapter,ClanRank rank,WidgetState widgets,boolean logged){
  Player player=fake(Player.class,(p,m,a)->
   m.getName().equals("getName")?"ClanAdmin":null);
  ClanMember member=fake(ClanMember.class,(p,m,a)->
   m.getName().equals("getRank")?rank:null);
  ClanSettings settings=fake(ClanSettings.class,(p,m,a)->{
   if(m.getName().equals("getName"))return chapter;
   if(m.getName().equals("findMember"))return member;
   return null;
  });
  return fake(Client.class,(p,m,a)->{
   switch(m.getName()){
    case "getGameState":return logged?GameState.LOGGED_IN:GameState.LOGIN_SCREEN;
    case "getLocalPlayer":return player;
    case "getClanSettings":return settings;
    case "getWidget":
     int packed=a.length==1?(Integer)a[0]:
       (((Integer)a[0]<<16)|(Integer)a[1]);
     return (packed>>>16)==689 &&
       ((packed&65535)==2 || (packed&65535)==6)?widgets.root:null;
    default:return null;
   }
  });
 }
 @Test public void onlyDeputyOwnerAndOwnerCanUseBansSync(){
  WidgetState w=new WidgetState();
  assertFalse(FederationInGameBanSyncButton.isOwnerOrDeputy(
    client("Holy Chaos",ClanRank.ADMINISTRATOR,w,true)));
  assertTrue(FederationInGameBanSyncButton.isOwnerOrDeputy(
    client("Holy Chaos",ClanRank.DEPUTY_OWNER,w,true)));
  assertTrue(FederationInGameBanSyncButton.isOwnerOrDeputy(
    client("Holy Chaos",ClanRank.OWNER,w,true)));
  assertFalse(FederationInGameBanSyncButton.isOwnerOrDeputy(
    client("Holy Chaos",new ClanRank(5),w,true)));
  assertFalse(FederationInGameBanSyncButton.isOwnerOrDeputy(
    client("Other Clan",ClanRank.OWNER,w,true)));
  assertFalse(FederationInGameBanSyncButton.isOwnerOrDeputy(
    client("Holy Chaos",ClanRank.OWNER,w,false)));
 }
 @Test public void administratorCannotCreateButtonOrRequestSync(){
  AtomicInteger calls=new AtomicInteger();
  WidgetState w=new WidgetState();
  Client administrator=client("Holy Chaos",ClanRank.ADMINISTRATOR,w,true);
  FederationInGameBanSyncButton button=new FederationInGameBanSyncButton(calls::incrementAndGet);
  button.update(administrator);
  assertEquals(0,w.children.size());
  assertEquals(0,calls.get());
  button.update(client("Holy Chaos",ClanRank.DEPUTY_OWNER,w,true));
  assertEquals(9,w.children.size());
  button.update(administrator);
  assertTrue(w.children.get(8).isHidden());
  assertEquals(0,calls.get());
 }
 @Test public void singleButtonDoesNotMultiplyOrTriggerWithoutClick(){
  AtomicInteger syncCalls=new AtomicInteger();
  WidgetState w=new WidgetState();
  Client admin=client("Holy Chaos",ClanRank.DEPUTY_OWNER,w,true);
  FederationInGameBanSyncButton button=new FederationInGameBanSyncButton(syncCalls::incrementAndGet);
  button.update(admin);
  assertEquals(9,w.children.size());
  Widget displayed=w.children.get(8);
  assertEquals("The action tooltip should not show internal widget IDs","",displayed.getName());
  assertTrue(displayed.getText().contains("Sync Ban List"));
  assertEquals(0,syncCalls.get());
  assertNotNull("Native onOp callback must be registered",w.childStates.get(8).click);
  w.childStates.get(8).click.run(null);
  assertEquals(1,syncCalls.get());
  button.update(admin);
  assertEquals(9,w.children.size());
  button.clear(admin);
  assertTrue(displayed.isHidden());
 }
 @Test public void uploadPayloadHasOnlyChapterAndCapturedNamesAndNoTokens(){
  FederationBanRegistry.Snapshot capture=new FederationBanRegistry.Snapshot(
    "Holy Chaos","2026-11-16T18:00:00Z","LIVE_WIDGET",
    java.util.Arrays.asList("imhigh999","ILOVEDP3","Fahern Yama"));
  String body=FederationBanUploadClient.payload(capture);
  assertTrue(body.contains("\"schema_version\":1"));
  assertTrue(body.contains("\"chapter\":\"Holy Chaos\""));
  assertTrue(body.contains("\"imhigh999\""));
  assertFalse(body.contains("Authorization"));
  assertFalse(body.contains("token"));
  assertFalse(FederationBanUploadClient.acceptedEndpoint("http://192.168.140.13:8777"));
  assertFalse(FederationBanUploadClient.acceptedEndpoint("https://evil.org@other.net/"));
  assertTrue(FederationBanUploadClient.acceptedEndpoint("https://bans.holyempireosrs.cc"));
 }
}
