package org.holyempire.federation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import net.runelite.client.util.Filepath;

/**
 * Local-only, per-game-account officer credentials, using RuneLite's bounded
 * plugin data API. Tokens are never placed in the RuneLite syncable config.
 */
final class FederationOfficerCredentialStore {
 private final Filepath root;

 FederationOfficerCredentialStore(Filepath directory) {root=directory;}

 private static String keyFor(String account) throws IOException {
  String normalized=WomDirectoryClient.normalize(account);
  if(normalized==null || normalized.isEmpty() || normalized.length()>32)
   throw new IOException("Verified officer account required");
  try {
   byte[] digest=MessageDigest.getInstance("SHA-256")
     .digest(normalized.getBytes(StandardCharsets.UTF_8));
   StringBuilder hex=new StringBuilder(64);
   for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   return hex+".key";
  }catch(java.security.NoSuchAlgorithmException ex){
   throw new IOException("Secure credential hashing unavailable",ex);
  }
 }

 private Filepath pathFor(String account) throws IOException {
  return root.joinSegment(keyFor(account));
 }

 private static boolean valid(char[] token){
  if(token==null || token.length<24 || token.length>256)return false;
  for(char c:token)if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||
    (c>='0'&&c<='9')||c=='_'||c=='-'))return false;
  return true;
 }

 synchronized void save(String account,char[] credential) throws IOException {
  if(!valid(credential))throw new IOException("Invalid credential format or length");
  Filepath target=pathFor(account);
  root.createDirectories();
  Filepath temporary=root.createTempFile(".officer-",".tmp");
  byte[] raw=new String(credential).getBytes(StandardCharsets.UTF_8);
  try {
   // Filepath.createTempFile uses a restrictive, owner-only mode on POSIX.
   // Keep the temporary mode across the atomic rename and fsync the bytes.
   try(FileChannel out=temporary.openFileChannel(
      StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING)){
    ByteBuffer data=ByteBuffer.wrap(raw);
    while(data.hasRemaining())out.write(data);
    out.force(true);
   }
   try {
    temporary.moveTo(target,StandardCopyOption.ATOMIC_MOVE,
      StandardCopyOption.REPLACE_EXISTING);
   }catch(AtomicMoveNotSupportedException unsupported){
    temporary.moveTo(target,StandardCopyOption.REPLACE_EXISTING);
   }
  }finally{
   Arrays.fill(raw,(byte)0);
   temporary.deleteIfExists();
  }
 }

 synchronized String read(String account){
  try{
   Filepath path=pathFor(account);
   if(!path.isFile() || path.size()<24 || path.size()>256)return "";
   // A malformed or externally symlinked file may never redirect reads.
   try(FileChannel input=path.openFileChannel(
       StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
    long length=input.size();
    if(length<24 || length>256)return "";
    ByteBuffer bytes=ByteBuffer.allocate((int)length);
    while(bytes.hasRemaining())if(input.read(bytes)<0)return "";
    String token=new String(bytes.array(),StandardCharsets.UTF_8);
    return valid(token.toCharArray())?token:"";
   }
  }catch(IOException|RuntimeException ignored){return "";}
 }

 synchronized void clear(String account) throws IOException {
  pathFor(account).deleteIfExists();
 }
}
