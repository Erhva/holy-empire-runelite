package org.holyempire.federation;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetPositionMode;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

/**
 * Adds a native-looking, client-only button to the actual ClansBanned.FRAME.
 * The capture/transmission action still needs independent server authorization.
 */
final class FederationInGameBanSyncButton {
 static final int BANS_GROUP=InterfaceID.CLANS_BANNED;
 static final int FRAME=InterfaceID.ClansBanned.FRAME;
 static final int CONTENTS=FederationBanWidgetCapture.BANS_CONTAINER_CHILD;


 private final Runnable requestSync;
 private final List<Widget> parts=new ArrayList<>();
 private final List<Integer> baseSprites=new ArrayList<>();
 private Widget parent;
 private Widget textWidget;
 private boolean hovered;

 FederationInGameBanSyncButton(Runnable requestSync){this.requestSync=requestSync;}

 static ClanRank ownRank(Client client){
  if(client==null || client.getGameState()!=GameState.LOGGED_IN ||
    client.getLocalPlayer()==null || client.getLocalPlayer().getName()==null)return null;
  String me=WomDirectoryClient.normalize(client.getLocalPlayer().getName());
  ClanSettings settings=client.getClanSettings();
  if(settings!=null && FederationBanRegistry.officialChapter(settings.getName())!=null){
   ClanMember member=settings.findMember(client.getLocalPlayer().getName());
   if(member==null && settings.getMembers()!=null){
    for(ClanMember candidate:settings.getMembers()){
     if(candidate!=null && me.equals(WomDirectoryClient.normalize(candidate.getName()))){
      member=candidate;break;
     }
    }
   }
   if(member!=null)return member.getRank();
  }
  // The live clan channel may populate before ClanSettings is available.
  // Never use the separate guest-clan channel for authorizing actions.
  ClanChannel channel=client.getClanChannel();
  if(channel!=null && FederationBanRegistry.officialChapter(channel.getName())!=null){
   ClanChannelMember member=channel.findMember(client.getLocalPlayer().getName());
   if(member==null && channel.getMembers()!=null){
    for(ClanChannelMember candidate:channel.getMembers()){
     if(candidate!=null && me.equals(WomDirectoryClient.normalize(candidate.getName()))){
      member=candidate;break;
     }
    }
   }
   if(member!=null)return member.getRank();
  }
  return null;
 }
 static boolean isOwnerOrDeputy(Client client){
  ClanRank rank=ownRank(client);
  return rank!=null && (rank.getRank()==ClanRank.OWNER.getRank() ||
    rank.getRank()==ClanRank.DEPUTY_OWNER.getRank());
 }

 void update(Client client){
  // ClansBanned frame is child 2; child 0 is NOT the visible frame.
  Widget frame=client.getWidget(FRAME);
  Widget entries=client.getWidget(BANS_GROUP,CONTENTS);
  if(frame==null || frame.isHidden() || entries==null || entries.isHidden()
    || !isOwnerOrDeputy(client)){
   clear(client);
   return;
  }

  if(frame==parent && textWidget!=null && isStillChild(frame,textWidget)){
   showParts(true);
   return;
  }
  // Reopening Bans recreates the native frame and invalidates dynamic widgets.
  parts.clear();
  baseSprites.clear();
  parent=frame;
  textWidget=null;

  // Same 9-part in-game stone button style as the WOM Sync Group plugin.
  addSprite(SpriteID.V2StoneButton.TOP_LEFT,6,6,9,9);
  addSprite(SpriteID.V2StoneButton.TOP_RIGHT,128,6,9,9);
  addSprite(SpriteID.V2StoneButton.BOTTOM_LEFT,6,20,9,9);
  addSprite(SpriteID.V2StoneButton.BOTTOM_RIGHT,128,20,9,9);
  addSprite(SpriteID.V2StoneButton.LEFT,6,15,9,5);
  addSprite(SpriteID.V2StoneButton.TOP,15,6,113,9);
  addSprite(SpriteID.V2StoneButton.RIGHT,128,15,9,5);
  addSprite(SpriteID.V2StoneButton.BOTTOM,15,20,113,9);
  Widget label=frame.createChild(-1,WidgetType.TEXT);
  // Native widget action text is already "Sync Ban List". A nonempty
  // widget name becomes the right-click/hover target and leaks internal IDs.
  label.setName("");
  label.setOriginalX(6);
  label.setOriginalY(6);
  label.setOriginalWidth(131);
  label.setOriginalHeight(23);
  label.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
  label.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
  label.setXTextAlignment(WidgetTextAlignment.CENTER);
  label.setYTextAlignment(WidgetTextAlignment.CENTER);
  label.setFontId(FontID.PLAIN_11);
  label.setTextShadowed(true);
  label.setText("<col=ffffff>Sync Ban List</col>");
  label.setAction(0,"Sync Ban List");
  label.setOnMouseOverListener((JavaScriptCallback) event->hover(true));
  label.setOnMouseLeaveListener((JavaScriptCallback) event->hover(false));
  label.setOnOpListener((JavaScriptCallback) event->{
   if(isOwnerOrDeputy(client))requestSync.run();
  });
  label.setHasListener(true);
  label.revalidate();
  parts.add(label);
  textWidget=label;
  hovered=false;
 }

 private void addSprite(int sprite,int x,int y,int width,int height){
  Widget part=parent.createChild(-1,WidgetType.GRAPHIC);
  part.setSpriteId(sprite);
  part.setOriginalX(x);
  part.setOriginalY(y);
  part.setOriginalWidth(width);
  part.setOriginalHeight(height);
  part.setXPositionMode(WidgetPositionMode.ABSOLUTE_LEFT);
  part.setYPositionMode(WidgetPositionMode.ABSOLUTE_TOP);
  part.revalidate();
  parts.add(part);
  baseSprites.add(sprite);
 }
 private void hover(boolean active){
  if(hovered==active)return;
  hovered=active;
  for(int i=0;i<baseSprites.size();i++){
   Widget part=parts.get(i);
   part.setSpriteId(baseSprites.get(i)+(active?8:0));
   part.revalidate();
  }
  if(textWidget!=null)
   textWidget.setText(active?"<col=ffdf77>Sync Ban List</col>":
     "<col=ffffff>Sync Ban List</col>");
 }
 private static boolean isStillChild(Widget root,Widget child){
  Widget[] children=root.getChildren();
  if(children==null)return false;
  for(Widget entry:children)if(entry==child)return true;
  return false;
 }
 private void showParts(boolean visible){
  for(Widget part:parts){
   if(part!=null && part.isSelfHidden()==visible)
    part.setHidden(!visible);
  }
 }
 void clear(Client client){
  if(parent!=null && textWidget!=null && isStillChild(parent,textWidget)){
   showParts(false);
  }else{
   // Old interface no longer holds the widgets.
   parts.clear();
   baseSprites.clear();
   textWidget=null;
   parent=null;
   hovered=false;
  }
 }
}
