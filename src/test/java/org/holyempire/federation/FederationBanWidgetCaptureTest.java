package org.holyempire.federation;

import java.lang.reflect.Proxy;
import java.time.Instant;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationBanWidgetCaptureTest {
 private static final Instant WHEN=Instant.parse("2026-11-16T16:00:00Z");
 @SuppressWarnings("unchecked")
 private static <T> T mock(Class<T> type,java.lang.reflect.InvocationHandler handler){
  return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
 }
 private static Widget widget(String text,boolean hidden,Widget[] children){
  return mock(Widget.class,(p,m,a)->{
   switch(m.getName()){
    case "getText":return text;
    case "getChildren":return children;
    case "isHidden":return hidden;
    case "toString":return "test-widget";
    default:return null;
   }
  });
 }
 private static Client client(String chapter,Widget page,Widget bans,boolean logged){
  ClanSettings settings=mock(ClanSettings.class,(p,m,a)->
    m.getName().equals("getName")?chapter:null);
  return mock(Client.class,(p,m,a)->{
   switch(m.getName()){
    case "getGameState":return logged?GameState.LOGGED_IN:GameState.LOGIN_SCREEN;
    case "getClanSettings":return settings;
    case "getWidget":
     int group=((Integer)a[0]);
     int child=((Integer)a[1]);
     if(group!=689)return null;
     return child==0?page:child==6?bans:null;
    default:return null;
   }
  });
 }
 private static void shouldReject(Client client,String substring){
  try{FederationBanWidgetCapture.read(client,WHEN);fail("Expected capture refusal");}
  catch(IllegalStateException | IllegalArgumentException ex){
   assertTrue(ex.getMessage().contains(substring));
  }
 }
 @Test public void readsOnlyCorrectOpenBansScreenAndUsesGameClanName(){
  Widget visible=widget(null,false,null);
  Widget bans=widget(null,false,new Widget[]{
   widget("<col=ff0000>Bad Guy</col>",false,null),
   widget("<col=aa>Other Person</col>",false,null),
   widget("Other Person",false,null),
   widget("",false,null)});
  FederationBanRegistry.Snapshot result=
   FederationBanWidgetCapture.read(client("Holy Guard",visible,bans,true),WHEN);
  assertEquals("Holy Guard",result.chapter);
  assertEquals(2,result.names.size());
  assertEquals("Bad Guy",result.names.get(0));
  assertEquals("LIVE_WIDGET",result.source);
 }
 @Test public void sparseBanListWidgetCanContainHundredsOfUnusedSlots(){
  Widget visible=widget(null,false,null);
  Widget[] sparse=new Widget[1200];
  sparse[2]=widget("<col=ff8c00>imhigh999</col>",false,null);
  sparse[411]=widget("ILOVEDP3",false,null);
  sparse[1199]=widget("Fahern Yama",false,null);
  FederationBanRegistry.Snapshot snapshot=
   FederationBanWidgetCapture.read(client("Holy Chaos",visible,widget(null,false,sparse),true),WHEN);
  assertEquals("Holy Chaos",snapshot.chapter);
  assertEquals(3,snapshot.names.size());
  assertEquals("imhigh999",snapshot.names.get(0));
  assertEquals("ILOVEDP3",snapshot.names.get(1));
  assertEquals("Fahern Yama",snapshot.names.get(2));
 }
 @Test public void sparseArrayCapacityDoesNotBypassActualHundredBanLimit(){
  Widget visible=widget(null,false,null);
  Widget[] slots=new Widget[1800];
  for(int i=0;i<101;i++)slots[i*9]=widget("ban"+i,false,null);
  shouldReject(client("Holy Chaos",visible,widget(null,false,slots),true),"Unexpected number");
 }
 @Test public void extremeArrayCapacityIsRejectedWithoutExtracting(){
  Widget visible=widget(null,false,null);
  Widget[] massive=new Widget[16385];
  massive[0]=widget("Banned",false,null);
  shouldReject(client("Holy Chaos",visible,widget(null,false,massive),true),"safe inspection limit");
 }
 @Test public void ignoresOffscreenOrUnknownClanInsteadOfClaimingBans(){
  Widget parent=widget(null,false,null);
  Widget populated=widget(null,false,new Widget[]{widget("A Player",false,null)});
  shouldReject(client("Another Clan",parent,populated,true),"configured");
  shouldReject(client("Holy Empire",null,populated,true),"Open your");
  shouldReject(client("Holy Empire",widget(null,true,null),populated,true),"Open your");
  shouldReject(client("Holy Empire",parent,null,true),"Open your");
  shouldReject(client("Holy Empire",parent,populated,false),"Log into");
  shouldReject(client("Holy Empire",parent,widget(null,false,null),true),"not populated");
  shouldReject(client("Holy Empire",parent,
    widget(null,false,new Widget[]{widget("",false,null)}),true),"No banned");
 }
}
