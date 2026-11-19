package org.holyempire.federation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Local-only, per-game-account officer credentials. Not RuneLite ConfigManager:
 * cloud-synchronized RuneLite profiles must never contain bearer tokens.
 */
final class FederationOfficerCredentialStore {
 private static final Set<PosixFilePermission> DIR_PERMS=EnumSet.of(
   PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,
   PosixFilePermission.OWNER_EXECUTE);
 private static final Set<PosixFilePermission> FILE_PERMS=EnumSet.of(
   PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE);
 private final Path root;

 FederationOfficerCredentialStore(Path directory){root=directory;}
 static FederationOfficerCredentialStore local(){
  return new FederationOfficerCredentialStore(
    Paths.get(System.getProperty("user.home"),".runelite","holy-empire-officers"));
 }

 private static String keyFor(String account) throws IOException {
  String normalized=WomDirectoryClient.normalize(account);
  if(normalized==null || normalized.isEmpty() || normalized.length()>32)
   throw new IOException("Verified officer account required");
  try {
   byte[] digest=MessageDigest.getInstance("SHA-256")
     .digest(normalized.getBytes(StandardCharsets.UTF_8));
   StringBuilder hex=new StringBuilder();
   for(byte b:digest)hex.append(String.format("%02x",b&0xff));
   return hex.toString()+".key";
  }catch(java.security.NoSuchAlgorithmException ex){
   throw new IOException("Secure credential hashing unavailable",ex);
  }
 }

 private Path pathFor(String account) throws IOException {
  return root.resolve(keyFor(account));
 }

 private static void setMode(Path path,Set<PosixFilePermission> permissions)
   throws IOException {
  try {Files.setPosixFilePermissions(path,permissions);}
  catch(UnsupportedOperationException windows){
   // Windows user profile ACLs protect per-user files without POSIX modes.
   // Do not weaken Unix permissions or make a global secret store.
  }
 }

 private void prepare() throws IOException {
  Path parent=root.getParent();
  if(parent!=null && Files.isSymbolicLink(parent))
   throw new IOException("Unsafe private credential parent directory");
  if(Files.isSymbolicLink(root))
   throw new IOException("Unsafe credential directory");
  Files.createDirectories(root);
  if(Files.isSymbolicLink(root))
   throw new IOException("Unsafe credential directory");
  setMode(root,DIR_PERMS);
 }

 synchronized void save(String account,char[] credential) throws IOException {
  if(credential==null || credential.length<24 || credential.length>256)
   throw new IOException("Invalid credential length");
  for(char c:credential) {
   if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||
        (c>='0'&&c<='9')||c=='_'||c=='-'))
    throw new IOException("Invalid credential format");
  }
  Path target=pathFor(account);
  prepare();
  if(Files.isSymbolicLink(target))
   throw new IOException("Unsafe credential file");
  Path temporary=Files.createTempFile(root,".officer-",".tmp");
  byte[] raw=new String(credential).getBytes(StandardCharsets.UTF_8);
  try {
   setMode(temporary,FILE_PERMS);
   try(FileChannel out=FileChannel.open(temporary,StandardOpenOption.WRITE,
      StandardOpenOption.TRUNCATE_EXISTING)){
    ByteBuffer bytes=ByteBuffer.wrap(raw);
    while(bytes.hasRemaining())out.write(bytes);
    out.force(true);
   }
   try {
    Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,
       StandardCopyOption.REPLACE_EXISTING);
   } catch(java.nio.file.AtomicMoveNotSupportedException ex) {
    Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
   }
   setMode(target,FILE_PERMS);
  } finally {
   Arrays.fill(raw,(byte)0);
   Files.deleteIfExists(temporary);
  }
 }

 synchronized String read(String account){
  try {
   Path path=pathFor(account);
   if(Files.isSymbolicLink(root) || Files.isSymbolicLink(path) ||
      !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) ||
      Files.size(path)>256)return "";
   try {
    Set<PosixFilePermission> perms=Files.getPosixFilePermissions(path);
    if(!FILE_PERMS.containsAll(perms))return "";
   } catch(UnsupportedOperationException windows){
    // Windows restricts access using NTFS account ACLs.
   }
   String token=Files.readString(path,StandardCharsets.UTF_8);
   if(token.length()<24 || token.length()>256)return "";
   for(int i=0;i<token.length();i++){
    char c=token.charAt(i);
    if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||
      (c>='0'&&c<='9')||c=='_'||c=='-'))return "";
   }
   return token;
  }catch(IOException|RuntimeException ignored){return "";}
 }

 synchronized void clear(String account) throws IOException {
  Path target=pathFor(account);
  if(Files.isSymbolicLink(target))throw new IOException("Unsafe credential file");
  Files.deleteIfExists(target);
 }
}
