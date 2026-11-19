package org.holyempire.federation;

import java.awt.Color;
import javax.swing.Icon;

/** A single display policy for chapter rows and global member search results. */
final class HolyEmpireRankDisplay {
 static final Color GOLD=new Color(255,215,0);
 private HolyEmpireRankDisplay() {}
 static boolean isEmperor(String playerName) {
  return "not needed".equals(WomDirectoryClient.normalize(playerName));
 }
 static String title(WomDirectoryClient.ChapterMember member) {
  return isEmperor(member.name) ? "Emperor" : member.readableRank();
 }
 static Icon icon(WomDirectoryClient.ChapterMember member) {
  return isEmperor(member.name)
    ? HolyEmpireAssets.emperorIcon()
    : HolyEmpireAssets.rankIcon(member.rank);
 }
 static Color titleColor(WomDirectoryClient.ChapterMember member) {
  return isEmperor(member.name) ? GOLD : new Color(173,188,208);
 }
}
