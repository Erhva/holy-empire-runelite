package org.holyempire.federation;

import java.awt.Component;
import java.awt.Container;
import java.util.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HolyEmpireSidebarUiTest {
 private static Component named(Container parent,String name) {
  for(Component child:parent.getComponents()){
   if(name.equals(child.getName()))return child;
   if(child instanceof Container){
    Component found=named((Container)child,name);
    if(found!=null)return found;
   }
  }
  return null;
 }
 @Test public void sidebarRendersAllChapterIconsAndRosterRankIcons() throws Exception {
  final FederationPanel[] p=new FederationPanel[1];
  SwingUtilities.invokeAndWait(()->p[0]=new FederationPanel());
  Map<String,java.util.List<String>> combined=Collections.singletonMap(
     "captainplayer",Collections.singletonList("Holy Chaos"));
  Map<String,java.util.List<WomDirectoryClient.ChapterMember>> rosters=new HashMap<>();
  rosters.put("Holy Chaos",Arrays.asList(
    new WomDirectoryClient.ChapterMember("OwnerPlayer","owner"),
    new WomDirectoryClient.ChapterMember("CaptainPlayer","captain"),
    new WomDirectoryClient.ChapterMember("GuestPlayer","unranked")));
  p[0].update(combined,rosters);
  SwingUtilities.invokeAndWait(()->{});
  SwingUtilities.invokeAndWait(()->{
   for(HolyEmpireChapterCatalog.Chapter chapter:HolyEmpireChapterCatalog.ORDERED){
    JButton button=(JButton)named(p[0],"holyEmpireChapter:"+chapter.name);
    assertNotNull("Missing "+chapter.name,button);
    JLabel icon=(JLabel)named(button,"holyEmpireChapterIcon:"+chapter.name);
    assertNotNull("Missing chapter art "+chapter.name,icon);
    assertNotNull(icon.getIcon());
    assertEquals(32,icon.getIcon().getIconWidth());
   }
   ((JButton)named(p[0],"holyEmpireChapter:Ultramarines")).doClick();
   assertNotNull(named(p[0],"holyEmpireChapterBack"));
   assertNull(named(p[0],"holyEmpireRank:OwnerPlayer"));
   ((JButton)named(p[0],"holyEmpireChapterBack")).doClick();
   ((JButton)named(p[0],"holyEmpireChapter:Holy Chaos")).doClick();
   JLabel owner=(JLabel)named(p[0],"holyEmpireRank:OwnerPlayer");
   JLabel captain=(JLabel)named(p[0],"holyEmpireRank:CaptainPlayer");
   JLabel unranked=(JLabel)named(p[0],"holyEmpireRank:GuestPlayer");
   assertNotNull(owner);
   assertNotNull(captain);
   assertNotNull(unranked);
   assertNotNull(owner.getIcon());
   assertNotNull(captain.getIcon());
   assertNull(unranked.getIcon());
   assertEquals("Captain",captain.getText());
  });
 }
}
