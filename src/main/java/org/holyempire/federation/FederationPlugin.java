package org.holyempire.federation;
import com.google.inject.Provides;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import net.runelite.client.game.ChatIconManager;
import java.util.Map;
import java.util.concurrent.*;
import javax.inject.Inject;
import okhttp3.OkHttpClient;
import net.runelite.client.callback.ClientThread;
import java.time.Instant;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.api.*;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name="Holy Empire",internalName="holy-empire",description="Combined WOM clan rosters and player indicators")
public class FederationPlugin extends Plugin {
 @Inject private Client client;
 @Inject private OverlayManager overlayManager;
 @Inject private ClientToolbar toolbar;
 @Inject private ClientThread clientThread;
 @Inject private ChatIconManager chatIconManager;
 private final Map<String,Integer> customRankIconIds=new HashMap<>();
 private final Set<Integer> renderedRankIconIndexes=new HashSet<>();
 @Inject private FederationMinimapOverlay overlay;
 @Inject private HolyEmpireNamesOverlay namesOverlay;
 @Inject private FederationApplicantOverlay applicantOverlay;
 @Inject private FederationConfig config;
 @Inject private ConfigManager configManager;
 @Inject private OkHttpClient httpClient;
 private volatile boolean officerAuthorized;
 private final FederationSession session=new FederationSession();
 private final FederationOfficerCredentialStore officerSecrets=
   FederationOfficerCredentialStore.local();
 private java.util.concurrent.ScheduledExecutorService scheduler;
 private FederationPanel panel;
 private NavigationButton button;
 private FederationInGameBanSyncButton inGameBanSyncButton;
 private volatile boolean banUploadInProgress;
 private long lastBanSyncDiagnosticMillis;
 private final Map<String,Long> recruitmentAlertTimes=new HashMap<>();
 private final FederationApplicantOneTimeBypass applicantBypass=
   new FederationApplicantOneTimeBypass();
 private final ClanVexillumEquipTrigger vexillumRefreshTrigger=
   new ClanVexillumEquipTrigger();
 private boolean pendingVexillumBanRefresh;
 @Provides FederationConfig provideConfig(net.runelite.client.config.ConfigManager c){
  return c.getConfig(FederationConfig.class);
 }
 @Override protected void startUp(){
   // The old syncable ConfigManager key must never retain a bearer secret.
   if(config.banSyncToken()!=null && !config.banSyncToken().isEmpty())
    configManager.unsetConfiguration("holyEmpireFederation","banSyncToken");
   overlayManager.add(overlay);
   overlayManager.add(namesOverlay);
   overlayManager.add(applicantOverlay);
   panel=new FederationPanel();
   panel.setOfficerSettingsOpener(()->{
    // A dialog opened for one officer must not save after an account change,
    // even if the new account is also an officer.
    final String account=session.account();
    final long generation=session.generation();
    FederationOfficerSettingsDialog.show(panel,configManager,
      officerSecrets,account,
      ()->officerAuthorized && session.eligibleFor(account,generation));
   });
   panel.setBanDirectoryLoader(this::loadCombinedBans);
   panel.setBanCapture((success,failed)->clientThread.invoke(()->{
    try{
     if(!session.isEligible())throw new IllegalStateException(
       "Verified Holy Empire membership is required.");
     FederationBanRegistry.Snapshot snapshot=
       FederationBanWidgetCapture.read(client,Instant.now());
     success.accept(snapshot);
    }catch(RuntimeException ex){
     failed.accept(ex.getMessage()==null?"Ban-list capture unavailable":ex.getMessage());
    }
   }));
   inGameBanSyncButton=new FederationInGameBanSyncButton(this::syncBanListFromGame);
   java.awt.image.BufferedImage sourceIcon;
   try (java.io.InputStream stream=getClass().getResourceAsStream("/holy-empire-icon.png")) {
    if(stream==null)throw new IllegalStateException("Federation icon missing");
    sourceIcon=javax.imageio.ImageIO.read(stream);
   } catch(java.io.IOException e){
    throw new IllegalStateException("Failed to load Federation icon",e);
   }
   java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(16,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);
   java.awt.Graphics2D graphics=image.createGraphics();
   graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
       java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
   graphics.drawImage(sourceIcon,0,0,16,32,null);
   graphics.dispose();
   button=NavigationButton.builder().tooltip("Holy Empire")
      .icon(image).priority(7).panel(panel).build();
   toolbar.addNavigation(button);
   scheduler=Executors.newSingleThreadScheduledExecutor(task->{
    Thread thread=new Thread(task,"holy-empire-wom-sync");thread.setDaemon(true);return thread;});
   // The persistent one-hour WOM cache ensures login events never repeat a
   // full download while a fresh snapshot exists.
   scheduler.scheduleWithFixedDelay(this::sync,0,20,TimeUnit.MINUTES);
 }
 @Override protected void shutDown(){
   if(inGameBanSyncButton!=null)inGameBanSyncButton.clear(client);
   inGameBanSyncButton=null;
   overlayManager.remove(overlay);
   overlayManager.remove(namesOverlay);
   overlayManager.remove(applicantOverlay);
   if(button!=null)toolbar.removeNavigation(button);
   if(scheduler!=null)scheduler.shutdownNow();
   session.observe("", "");
   officerAuthorized=false;
   if(panel!=null){
    panel.setOfficerSettingsEligible(false);
    panel.stopPrivateCacheTimer();
   }
   customRankIconIds.clear();
   renderedRankIconIndexes.clear();
   recruitmentAlertTimes.clear();
   applicantBypass.clear();
   vexillumRefreshTrigger.reset();
   pendingVexillumBanRefresh=false;
   if(panel!=null)panel.showMessage("Holy Empire disconnected.");
 }
 private String currentAccount(){return session.account();}
 @Subscribe public void onGameTick(GameTick event){
  Player local=client.getLocalPlayer();
  String name=client.getGameState()==GameState.LOGGED_IN && local!=null ?
     WomDirectoryClient.normalize(local.getName()) : "";
  String nativeChapter=name.isEmpty() ? "" :
    InGameClanVerifier.chapterFor(client.getClanSettings(),name);
  if(session.observe(name,nativeChapter)){
   applicantBypass.clear();
   final long observedGeneration=session.generation();
   if(panel!=null)
    panel.showMessage("Checking Holy Empire membership...",
      ()->session.generation()==observedGeneration);
   if(scheduler!=null && !scheduler.isShutdown() && !name.isEmpty())
    scheduler.execute(this::sync);
  }
  // Officer controls are presentational only. Protected uploads still require
  // a chapter-scoped server token; no client-provided rank authorizes a write.
  boolean permitted=session.isEligible() &&
    !session.nativeChapter().isEmpty() &&
    session.rosterFor(name).getOrDefault(name,java.util.Collections.emptyList())
      .contains(session.nativeChapter()) &&
    FederationInGameBanSyncButton.isOwnerOrDeputy(client);
  boolean wasOfficer=officerAuthorized;
  officerAuthorized=permitted;
  if(!permitted)applicantBypass.clear();
  if(panel!=null){
   panel.setOfficerSettingsEligible(permitted);
   if(wasOfficer && !permitted)panel.clearOfficerEvidence();
  }
  queuePendingVexillumRefresh();
 }
 /** Only equipment transitions can request a Vexillum-triggered refresh. */
 @Subscribe public void onItemContainerChanged(ItemContainerChanged event){
  if(!vexillumRefreshTrigger.onContainerChanged(event.getContainerId(),
      event.getItemContainer(),System.currentTimeMillis()))return;
  if(client.getGameState()!=GameState.LOGGED_IN)return;
  pendingVexillumBanRefresh=true;
  queuePendingVexillumRefresh();
 }
 private void queuePendingVexillumRefresh(){
  if(!pendingVexillumBanRefresh ||
     client.getGameState()!=GameState.LOGGED_IN ||
     session.account().isEmpty() ||
     scheduler==null || scheduler.isShutdown())return;
  pendingVexillumBanRefresh=false;
  if(officerAuthorized && panel!=null)
   panel.refreshPrivateBansOnVexillumEquip();
  // Uses the public CDN only; does not rescan WOM groups or game ban menus.
  scheduler.execute(()->sync(true));
 }
 @Subscribe public void onGameStateChanged(GameStateChanged event){
  if(event.getGameState()==GameState.STARTING)
   renderedRankIconIndexes.clear(); // RuneLite recalculates registered icon positions.
  if(event.getGameState()!=GameState.LOGGED_IN){
   applicantBypass.clear();
   session.observe("", "");
   officerAuthorized=false;
   vexillumRefreshTrigger.reset();
   pendingVexillumBanRefresh=false;
   if(panel!=null)panel.setOfficerSettingsEligible(false);
   final long loggedOutGeneration=session.generation();
   if(panel!=null)panel.showMessage("Log in with an eligible Holy Empire account.",
     ()->session.generation()==loggedOutGeneration);
  }
 }
 /** Warn on the native OSRS clan Recruit action; never block an in-game action. */
 @Subscribe public void onMenuOptionClicked(MenuOptionClicked event){
  if(client.getGameState()!=GameState.LOGGED_IN || !session.isEligible())return;
  if(FederationApplicantGuard.isApplicantAcceptance(
       event.getMenuOption(),event.getParam1(),event.getWidgetId())){
   // IDs only, never log the applicant's name, ban matches, or credentials.
   org.slf4j.LoggerFactory.getLogger(FederationPlugin.class)
     .info("Holy Empire native Applicants acceptance event observed");
   warnApplicantAcceptance(event);
   return;
  }
  MenuEntry menu=event.getMenuEntry();
  Player player=menu==null?null:menu.getPlayer();
  String name=FederationRecruitmentGuard.recruitmentTarget(
    event.getMenuOption(),player==null?null:player.getName(),event.getMenuTarget());
  if(name==null)return;
  final String account=session.account();
  final long generation=session.generation();
  boolean approved=officerAuthorized &&
     FederationInGameBanSyncButton.isOwnerOrDeputy(client);
  FederationBanDirectoryClient.Snapshot snapshot=approved && panel!=null?
    panel.freshRecruitmentBanDirectory():null;
  List<String> chapters=FederationRecruitmentGuard.matchingChapters(snapshot,name);
  boolean nonmemberBanned=session.publicBanWarningMatch(account,name);
  String message=!chapters.isEmpty()?
    FederationRecruitmentGuard.recruitPopupWarningText(name,chapters):
    nonmemberBanned?FederationPublicBanWarnings.recruitPopupWarningText(name):
    FederationRecruitmentGuard.warningText(name,chapters,
      snapshot!=null || session.publicBanWarningAvailable(account));
  if(message==null)return;
  long now=System.currentTimeMillis();
  String key=WomDirectoryClient.normalize(name);
  Long previous=recruitmentAlertTimes.get(key);
  if(previous!=null && now-previous<5000)return;
  recruitmentAlertTimes.put(key,now);
  if(recruitmentAlertTimes.size()>50)recruitmentAlertTimes.clear();
  final boolean includesPrivateData=!chapters.isEmpty();
  javax.swing.SwingUtilities.invokeLater(()->{
   if(!session.eligibleFor(account,generation) || panel==null)return;
   if(includesPrivateData && (!officerAuthorized ||
      panel.freshRecruitmentBanDirectory()==null))return;
   HolyEmpireDialogs.showMessageDialog(panel,message,
     "Holy Empire Recruitment Warning",javax.swing.JOptionPane.WARNING_MESSAGE);
  });
  if(approved && snapshot==null && panel!=null)panel.preloadBanDirectory();
 }
 /** Read only selected applicant details; never scan all list rows as the
  * identity of the selected applicant. */
 private static void selectedApplicantText(Widget widget,List<String> fields,int depth){
  if(widget==null || widget.isHidden() || depth>7 || fields.size()>250)return;
  if(widget.getText()!=null && !widget.getText().isBlank())
   fields.add(widget.getText());
  Widget[] children=widget.getChildren();
  if(children!=null)for(Widget child:children)
   selectedApplicantText(child,fields,depth+1);
 }

 /** Selection data is used for verification; a whole applicant list would
  * cause false accusations against unrelated selected applicants. */
 List<String> selectedApplicantFields(){
  List<String> selected=new java.util.ArrayList<>();
  try{
   selectedApplicantText(client.getWidget(InterfaceID.ClansApplicants.INFO_CONTAINER),
     selected,0);
   selectedApplicantText(client.getWidget(InterfaceID.ClansApplicants.INFO),
     selected,0);
  }catch(RuntimeException unavailable){
   // Native applicants widget can disappear while client updates.
  }
  return selected;
 }
 FederationBanDirectoryClient.Snapshot officerDirectoryForApplicants(){
  return client.getGameState()==GameState.LOGGED_IN && officerAuthorized &&
    session.isEligible() && panel!=null ? panel.freshRecruitmentBanDirectory():null;
 }

 private void warnApplicantAcceptance(MenuOptionClicked event){
  // Match the tested Recruit behavior: a verified officer sees a warning only
  // for a confirmed name in the private ban directory, never for an empty
  // Applicants screen or an unrelated applicant.
  final String account=session.account();
  final long generation=session.generation();
  final boolean authorized=officerAuthorized &&
    FederationInGameBanSyncButton.isOwnerOrDeputy(client);
  final FederationBanDirectoryClient.Snapshot bans=authorized && panel!=null?
    panel.freshRecruitmentBanDirectory():null;
  final List<String> selected=selectedApplicantFields();
  final String menuName=FederationApplicantGuard.extractExactName(
    event.getMenuTarget());
  String identified=FederationApplicantGuard.selectedApplicantName(selected);
  if(identified==null &&
      !FederationApplicantGuard.hasSelectedIdentityEvidence(selected))
   identified=menuName;

  if(bans==null){
   if(authorized && panel!=null)panel.preloadBanDirectory();
   // Same unavailable-status advice as ordinary Recruit, but only if a
   // real applicant name was available. No popup for an empty screen.
   final String message=session.publicBanWarningMatch(account,identified)?
     FederationPublicBanWarnings.publicWarningText(identified):
     FederationRecruitmentGuard.warningText(
       identified,java.util.Collections.emptyList(),
       session.publicBanWarningAvailable(account));
   if(message!=null)javax.swing.SwingUtilities.invokeLater(()->{
    if(panel!=null && session.eligibleFor(account,generation))
     HolyEmpireDialogs.showMessageDialog(panel,message,
       "Holy Empire Recruitment Warning",
       javax.swing.JOptionPane.WARNING_MESSAGE);
   });
   return;
  }

  if(!FederationApplicantGuard.hasSelectedIdentityEvidence(selected) &&
     menuName!=null)selected.add(menuName);
  final FederationApplicantGuard.Status status=
    FederationApplicantGuard.status(bans,selected);
  if(!FederationApplicantGuard.shouldCancelAccept(status))return;
  final String confirmed=FederationApplicantGuard.matchedBannedApplicant(
    bans,selected);
  if(confirmed==null)return;
  final List<String> chapters=FederationRecruitmentGuard.matchingChapters(
    bans,confirmed);
  if(chapters.isEmpty())return;
  // Consume an explicit, single-use approval ONLY for this still-banned
  // applicant in the same verified account, chapter and login generation.
  final String chapter=session.nativeChapter();
  if(applicantBypass.consume(account,generation,chapter,confirmed,
      System.currentTimeMillis())){
   return; // second click: user chose Allow Once after seeing the warning
  }
  event.consume(); // first click: cancel native Accept before game action
  final String message=FederationRecruitmentGuard.warningText(
    confirmed,chapters,true)+
    "\n\nThe invitation was NOT sent.\n"+
    "You may allow this one banned applicant after reviewing the warning.\n"+
    "Choose Allow Once, then click Accept again within 15 seconds.";
  javax.swing.SwingUtilities.invokeLater(()->{
   if(panel==null || !session.eligibleFor(account,generation) ||
      !officerAuthorized || !chapter.equals(session.nativeChapter()))
    return;
   // A stale or revoked officer snapshot must never arm an override.
   FederationBanDirectoryClient.Snapshot current=
     panel.freshRecruitmentBanDirectory();
   if(current==null || FederationRecruitmentGuard.matchingChapters(
      current,confirmed).isEmpty())return;
   Object[] options={"Keep Blocked","Allow Once"};
   int choice=HolyEmpireDialogs.showOptionDialog(panel,message,
      "Holy Empire — Banned Applicant",
      javax.swing.JOptionPane.DEFAULT_OPTION,
      javax.swing.JOptionPane.WARNING_MESSAGE,null,options,options[0]);
   if(choice==1 && session.eligibleFor(account,generation) &&
      officerAuthorized && chapter.equals(session.nativeChapter()) &&
      panel.freshRecruitmentBanDirectory()!=null){
    applicantBypass.arm(account,generation,chapter,confirmed,
      System.currentTimeMillis());
   }
  });
 }
 private void sync(){sync(false);}
 private void sync(boolean force){
   final String before=session.account();
   final long generation=session.generation();
   if(before.isEmpty()){
    if(session.invalidate(before,generation) && panel!=null)
     panel.showMessage("Log in with an eligible Holy Empire account.",
       ()->session.generation()==generation);
    return;
   }
   try {
    WomDirectoryClient.Snapshot snapshot=
      force?WomDirectoryClient.fetchFreshForVexillum(httpClient):WomDirectoryClient.fetch(httpClient);
    if(!session.publish(before,generation,snapshot))return;
    if(panel!=null) {
     if(session.isEligible())panel.update(session.rosterFor(before),
        session.chaptersFor(before),WomDirectoryClient.isUsingStaleCache(),
        ()->session.eligibleFor(before,generation),
        session.publicBanHashesFor(before));
     else panel.showNotVerified(()->session.generation()==generation &&
        !session.isEligible());
    }
   } catch(Exception error) {
    org.slf4j.LoggerFactory.getLogger(FederationPlugin.class)
      .warn("WOM unavailable; no complete cached roster: {}",error.toString());
    if(session.invalidate(before,generation) && panel!=null)
     panel.showMessage("Roster temporarily unavailable. Will retry automatically.",
       ()->session.generation()==generation);
   }
 }
 Map<String,List<String>> members(){
  return session.rosterFor(currentAccount());
 }
 /** One local client-thread registration per distinct rank. No network lookups. */
 private int menuRankIcon(String playerName) {
  if(!config.showClanRankIcons() || client.getModIcons()==null)return -1;
  WomDirectoryClient.ChapterMember member=session.rankFor(playerName);
  if(member==null)return -1;
  Icon icon=HolyEmpireRankDisplay.icon(member);
  if(!(icon instanceof ImageIcon))return -1;
  String key=HolyEmpireRankDisplay.isEmperor(member.name)?"emperor":
    WomDirectoryClient.normalize(member.rank);
  try {
   Integer id=customRankIconIds.get(key);
   if(id==null){
    BufferedImage image=new BufferedImage(13,13,BufferedImage.TYPE_INT_ARGB);
    Graphics2D g=image.createGraphics();
    g.drawImage(((ImageIcon)icon).getImage(),0,0,13,13,null);
    g.dispose();
    id=chatIconManager.registerChatIcon(image);
    customRankIconIds.put(key,id);
   }
   int index=chatIconManager.chatIconIndex(id);
   if(index>=0)renderedRankIconIndexes.add(index);
   return index;
  } catch(RuntimeException failure) {
   return -1; // Never interrupt menu construction for a missing icon.
  }
 }
 /** Fetch one short-lived, in-memory combined directory for the current officer. */
 private void loadCombinedBans(boolean force){
  if(!officerAuthorized || !session.isEligible() ||
     scheduler==null || scheduler.isShutdown())return;
  final String account=session.account();
  final long generation=session.generation();
  final String chapter=session.nativeChapter();
  final java.util.function.BooleanSupplier authorized=()->
    officerAuthorized && session.eligibleFor(account,generation) &&
    chapter.equals(session.nativeChapter());
  if(!authorized.getAsBoolean())return;
  final String endpoint=config.banSyncEndpoint();
  final String token=officerSecrets.read(account);
  if(!FederationBanUploadClient.acceptedEndpoint(endpoint) ||
     token.isEmpty()){
   if(panel!=null)panel.failBanDirectory(
     "Configure an approved admin credential key under Ban List > Admin Verification.",
     authorized);
   return;
  }
  scheduler.execute(()->{
   if(!authorized.getAsBoolean())return;
   try{
    FederationBanDirectoryClient.Snapshot bans=
      FederationBanDirectoryClient.fetch(chapter,endpoint,token,httpClient);
    if(panel!=null)panel.displayBanDirectory(bans,authorized);
   }catch(Exception error){
    // Only report a generic network status; never log officer credentials or ban names.
    if(panel!=null)panel.failBanDirectory(
      "Could not retrieve synchronized bans. Check the private connection and officer access.",
      authorized);
   }
  });
 }

 /** Pure session gate for a queued officer upload; never trust old logins. */
 static boolean currentOfficerCapture(FederationSession state,String account,long generation,
     FederationBanRegistry.Snapshot snapshot,boolean approved){
  return approved && state.eligibleFor(account,generation) &&
    state.nativeChapter().equals(snapshot.chapter);
 }

 /** User clicked the in-game ban-list Sync button; capture on game thread, POST off-thread. */
 private void syncBanListFromGame(){
  if(banUploadInProgress)return;
  final String activeAccount=session.account();
  final long activeGeneration=session.generation();
  if(!session.isEligible() || activeAccount.isEmpty()){
   banSyncMessage("Verified Holy Empire membership is required before synchronizing bans.");
   return;
  }
  // Both native Owner/Deputy rank and matching verified membership are required.
  if(!FederationInGameBanSyncButton.isOwnerOrDeputy(client)){
   banSyncMessage("Only verified Deputy Owners and Owners of the active chapter can sync bans.");
   return;
  }
  final FederationBanRegistry.Snapshot snapshot;
  try{
   snapshot=FederationBanWidgetCapture.read(client,java.time.Instant.now());
  }catch(RuntimeException ex){
   banSyncMessage("Capture failed: "+ex.getMessage());
   return;
  }
  // A capture must belong to the very chapter verified for this login.
  if(!currentOfficerCapture(session,activeAccount,activeGeneration,snapshot,
      officerAuthorized)){
   banSyncMessage("Ban capture was canceled because the verified chapter or account changed.");
   return;
  }
  // The local preview is temporary; the private server independently authorizes uploads.
  if(panel!=null)panel.banRegistry().put(snapshot);
  String endpoint=config.banSyncEndpoint();
  String token=officerSecrets.read(activeAccount);
  if(!FederationBanUploadClient.acceptedEndpoint(endpoint) ||
      token==null || token.trim().length()<24){
   banSyncMessage("Captured "+snapshot.names.size()+" banned account(s) from "+
     snapshot.chapter+" locally.\n\nCentral ban synchronization is not available yet; "+
     "nothing was uploaded. The local capture is temporary and will be lost "+
     "when RuneLite restarts.");
   return;
  }
  if(scheduler==null || scheduler.isShutdown()){
   banSyncMessage("Ban list captured locally. Upload is temporarily unavailable; nothing was uploaded.");
   return;
  }
  banUploadInProgress=true;
  scheduler.execute(()->{
   String result;
   try {
    if(!currentOfficerCapture(session,activeAccount,activeGeneration,snapshot,
         officerAuthorized))return;
    result=FederationBanUploadClient.send(snapshot,endpoint,token.trim(),httpClient);
   }catch(Exception ex){
    // No credential, URL or banned account names in logs.
    result="Ban sync failed: "+ex.getMessage();
   }finally{
    banUploadInProgress=false;
   }
   banSyncMessage(result,()->currentOfficerCapture(session,activeAccount,
     activeGeneration,snapshot,officerAuthorized));
  });
 }
 private void banSyncMessage(String message){banSyncMessage(message,()->true);}
 private void banSyncMessage(String message,java.util.function.BooleanSupplier stillCurrent){
  javax.swing.SwingUtilities.invokeLater(()->{
   if(panel!=null && stillCurrent.getAsBoolean())
    HolyEmpireDialogs.showMessageDialog(panel,message,
      "Holy Empire Ban Sync",javax.swing.JOptionPane.INFORMATION_MESSAGE);
  });
 }

 // Player Indicators decorates menus at default priority. Apply the CC tag last.
 @Subscribe(priority=-1.0f) public void onClientTick(ClientTick e){
   if(inGameBanSyncButton!=null)
    inGameBanSyncButton.update(client);
   // Opt-in diagnostic in the isolated developer launcher only. Log no
   // player names, ban-list entries or upload secrets.
   if(Boolean.getBoolean("holyempire.debugBanSync")){
    net.runelite.api.widgets.Widget frame=client.getWidget(net.runelite.api.gameval.InterfaceID.ClansBanned.FRAME);
    if(frame!=null && !frame.isHidden()){
     long now=System.currentTimeMillis();
     if(now-lastBanSyncDiagnosticMillis>=15000){
      lastBanSyncDiagnosticMillis=now;
      net.runelite.api.clan.ClanSettings settings=client.getClanSettings();
      net.runelite.api.clan.ClanRank own=FederationInGameBanSyncButton.ownRank(client);
      org.slf4j.LoggerFactory.getLogger(FederationPlugin.class).info(
       "Ban-sync UI check: frame=visible, bansWidgetVisible={}, clanSettingsPresent={}, ownRank={}, ownerOrDeputy={}, womMembershipReady={}",
       client.getWidget(FederationInGameBanSyncButton.BANS_GROUP,
        FederationInGameBanSyncButton.CONTENTS)!=null,
       settings!=null,own==null?"unknown":own.getRank(),
       FederationInGameBanSyncButton.isOwnerOrDeputy(client),session.isEligible());
     }
    }
   }
   if(client.isMenuOpen())return;
   // A matching banned player gets a visible warning in the native Recruit
   // menu itself, before the click. Private details never reach non-officers.
   FederationBanDirectoryClient.Snapshot recruitBans=
     officerAuthorized && panel!=null?panel.freshRecruitmentBanDirectory():null;
   for(MenuEntry menu:client.getMenuEntries()){
    Player candidate=menu.getPlayer();
    String name=FederationRecruitmentGuard.recruitmentTarget(
      menu.getOption(),candidate==null?null:candidate.getName(),menu.getTarget());
    if(name==null)continue;
    boolean privateMatch=!FederationRecruitmentGuard.matchingChapters(
      recruitBans,name).isEmpty();
    if(!privateMatch && !session.publicBanWarningMatch(currentAccount(),name))
      continue;
    String target=menu.getTarget();
    if(target!=null && !target.contains("[BAN-LIST WARNING]"))
     menu.setTarget(target+" <col=ff5555>[BAN-LIST WARNING]</col>");
   }
   Map<String,List<String>> lookup=members();
   if(lookup.isEmpty())return;
   for(MenuEntry entry:client.getMenuEntries()){
     Player p=entry.getPlayer();
     if(p==null || p.getName()==null)continue;
     List<String> clans=lookup.get(WomDirectoryClient.normalize(p.getName()));
     if(clans==null || clans.isEmpty())continue;
     boolean same=SameChapterVisibility.isSameChapter(
       lookup.get(currentAccount()),clans);
     boolean showTag=SameChapterVisibility.showMenuTag(config.showClanMenu(),
       same,config.showSameChapterMenu());
     boolean showRank=SameChapterVisibility.showRankIcon(config.showClanRankIcons(),same);
     String old=entry.getTarget();
     String updated=FederationMenuTags.apply(old,clans,
       ChapterIndicatorColors.menuColor(config,clans),showTag);
     updated=FederationMenuRankIcons.apply(updated,
       showRank?menuRankIcon(p.getName()):-1,renderedRankIconIndexes,showRank);
     if(updated!=null && !updated.equals(old))entry.setTarget(updated);
   }
 }
}
