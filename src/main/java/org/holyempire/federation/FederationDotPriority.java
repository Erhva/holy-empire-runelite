package org.holyempire.federation;

/** The core game minimap's special categories outrank additional Federation dots. */
final class FederationDotPriority {
 private FederationDotPriority(){}
 static boolean allowFederationDot(boolean isFriend, boolean friendsChat,
                                   boolean clanMember, int localTeam, int playerTeam) {
  return !isFriend && !friendsChat && !clanMember
      && (localTeam==0 || localTeam!=playerTeam);
 }
}
