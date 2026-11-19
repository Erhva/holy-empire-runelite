package org.holyempire.federation;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prefix our rank icon before the player name; retain native Player Indicators icons. */
final class FederationMenuRankIcons {
 private static final Pattern OLD_INSIDE_BRACKET=Pattern.compile(
   "( <col=[0-9a-fA-F]{6}>\\[)<img=([0-9]+)> ?");

 private FederationMenuRankIcons(){}

 static String apply(String text,int iconIndex,Set<Integer> owned,boolean enabled){
  if(text==null)return null;
  String cleaned=text;
  // Remove only our prior prefix (not a native icon following it).
  while(cleaned.startsWith("<img=")){
   int end=cleaned.indexOf('>');
   if(end<0 || end>16)break;
   int index;
   try{index=Integer.parseInt(cleaned.substring(5,end));}
   catch(NumberFormatException ex){break;}
   if(!owned.contains(index))break;
   cleaned=cleaned.substring(end+1);
  }
  // Also clean up tags produced by earlier developer versions that embedded
  // our icon within [Chapter]; the icon now belongs before the player name.
  Matcher old=OLD_INSIDE_BRACKET.matcher(cleaned);
  if(old.find()){
   int prior;
   try{prior=Integer.parseInt(old.group(2));}
   catch(NumberFormatException e){prior=-1;}
   if(owned.contains(prior))
    cleaned=cleaned.substring(0,old.start())+old.group(1)+cleaned.substring(old.end());
  }
  return enabled && iconIndex>=0 ? "<img="+iconIndex+">"+cleaned : cleaned;
 }
}
