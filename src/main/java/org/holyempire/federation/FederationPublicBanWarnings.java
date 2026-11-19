package org.holyempire.federation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Advisory for non-officers: hashes of names banned but absent from every roster. */
final class FederationPublicBanWarnings {
 private FederationPublicBanWarnings(){}

 static String hash(String name){
  if(FederationBanRegistry.validName(name)==null)return "";
  String normalized=WomDirectoryClient.normalize(name);
  try{
   byte[] data=MessageDigest.getInstance("SHA-256").digest(
     normalized.getBytes(StandardCharsets.UTF_8));
   StringBuilder result=new StringBuilder(64);
   for(byte b:data)result.append(String.format(Locale.ROOT,"%02x",b&255));
   return result.toString();
  }catch(NoSuchAlgorithmException error){
   throw new IllegalStateException("SHA-256 unavailable",error);
  }
 }

 static boolean matches(String name,Set<String> publicHashes,
     Map<String,List<String>> roster){
  String normalized=WomDirectoryClient.normalize(name);
  if(normalized.isEmpty() || roster.containsKey(normalized))return false;
  return publicHashes.contains(hash(name));
 }

 /** A successful private officer fetch may warn even when the name is rostered. */
 static boolean privateVisible(boolean authorized,boolean hasPrivateSnapshot,
     String name,List<String> privateMatches,Map<String,List<String>> roster){
  return authorized && hasPrivateSnapshot && !privateMatches.isEmpty();
 }

 /** Public, non-key-member text for the native Recruit pop-up. */
 static String recruitPopupWarningText(String name){
  String player=FederationBanRegistry.validName(name);
  if(player==null)return null;
  return "HOLY EMPIRE BAN-LIST WARNING\n\n"+
    player+" appears on a chapter ban-list\n\n"+
    "Consult an admin before recruiting";
 }

 static String publicWarningText(String name){
  String valid=FederationBanRegistry.validName(name);
  if(valid==null)return null;
  return "HOLY EMPIRE BAN-LIST WARNING\n\n"+valid+
    " appears on a synchronized ban list.\n\n"+
    "Consult an authorized Owner or Deputy Owner before recruiting.";
 }
}
