package org.holyempire.federation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FederationOfficerCredentialStoreTest {
 @Rule public TemporaryFolder tmp=new TemporaryFolder();
 private static final String SAMPLE="Example_officer_token_abcdefghijklmnopqrstuvwxyz0123456789";

 @Test public void localKeysAreAccountScopedAndPrivate() throws Exception {
  Path root=tmp.getRoot().toPath().resolve("officers");
  FederationOfficerCredentialStore store=new FederationOfficerCredentialStore(root);
  store.save("Erhva",SAMPLE.toCharArray());
  assertEquals(SAMPLE,store.read("ERHVA"));
  assertEquals("",store.read("AnotherUser"));
  assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ,
     PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE),
     Files.getPosixFilePermissions(root));
  try(java.util.stream.Stream<Path> paths=Files.list(root)){
   Path key=paths.findFirst().orElseThrow();
   assertFalse(key.getFileName().toString().contains("Erhva"));
   assertEquals(EnumSet.of(PosixFilePermission.OWNER_READ,
     PosixFilePermission.OWNER_WRITE),Files.getPosixFilePermissions(key));
  }
  store.clear("Erhva");
  assertEquals("",store.read("Erhva"));
 }

 @Test public void invalidTokensCannotBeSaved() throws Exception {
  Path dir=tmp.getRoot().toPath().resolve("officer-private");
  FederationOfficerCredentialStore store=new FederationOfficerCredentialStore(dir);
  try {
   store.save("Erhva","short".toCharArray());
   fail("Short credentials must not be accepted");
  }catch(java.io.IOException expected){}
  try {
   store.save("Erhva",(SAMPLE+"!").toCharArray());
   fail("Invalid bearer characters must be rejected");
  }catch(java.io.IOException expected){}
  assertFalse(Files.exists(dir));
 }

 @Test public void filesWithUnsafePermissionsAreNotRead() throws Exception {
  Path dir=tmp.getRoot().toPath().resolve("officer-private");
  FederationOfficerCredentialStore store=new FederationOfficerCredentialStore(dir);
  store.save("Erhva",SAMPLE.toCharArray());
  try(java.util.stream.Stream<Path> entries=Files.list(dir)){
   Path key=entries.findFirst().orElseThrow();
   Files.setPosixFilePermissions(key,EnumSet.of(
     PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,
     PosixFilePermission.OTHERS_READ));
   assertEquals("",store.read("Erhva"));
  }
 }
}
