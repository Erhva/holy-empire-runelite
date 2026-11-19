package org.holyempire.federation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class WomSnapshotCacheTest {
 @Rule public TemporaryFolder folder=new TemporaryFolder();
 private WomDirectoryClient.Snapshot fullSnapshot(){
  Map<String,List<String>> members=new HashMap<>();
  Map<String,List<WomDirectoryClient.ChapterMember>> chapters=new HashMap<>();
  for(int i=0;i<WomDirectoryClient.GROUPS.length;i++){
   String player="test"+i;
   String clan=WomDirectoryClient.CLANS[i];
   members.put(player,Collections.singletonList(clan));
   chapters.put(clan,Collections.singletonList(
     new WomDirectoryClient.ChapterMember(player,"owner")));
  }
  return new WomDirectoryClient.Snapshot(members,chapters);
 }
 @Test public void loginAgainUsesSnapshotWithoutNetwork() throws Exception {
  Path path=folder.newFile("wom-cache.json").toPath();
  AtomicInteger requests=new AtomicInteger();
  WomSnapshotCache cache=new WomSnapshotCache();
  long now=1_800_000_000_000L;
  WomSnapshotCache.Result first=cache.get(now,path,()->{
   requests.incrementAndGet();
   return fullSnapshot();
  });
  assertFalse(first.stale);
  WomSnapshotCache.Result second=cache.get(now+1000,path,()->{
   requests.incrementAndGet();
   throw new IOException("Should not make API calls on login");
  });
  assertFalse(second.stale);
  assertEquals(1,requests.get());
 }
 @Test public void equipmentRefreshBypassesFreshCacheWithoutContactingWom() throws Exception {
  Path path=folder.newFile("vexillum-cache.json").toPath();
  long now=1_800_000_000_000L;
  AtomicInteger fetches=new AtomicInteger();
  WomSnapshotCache cache=new WomSnapshotCache();
  cache.get(now,path,()->{fetches.incrementAndGet();return fullSnapshot();});
  cache.get(now+1000,path,()->{fetches.incrementAndGet();return fullSnapshot();});
  assertEquals(1,fetches.get());
  WomSnapshotCache.Result refreshed=cache.get(now+2000,path,()->{
   fetches.incrementAndGet();return fullSnapshot();
  },true);
  assertFalse(refreshed.stale);
  assertEquals("Vexillum forces one CDN refresh despite fresh local cache",
    2,fetches.get());
 }
 @Test public void restartReusesFreshDiskCache() throws Exception {
  Path path=folder.newFile("wom-cache.json").toPath();
  long now=1_800_000_000_000L;
  new WomSnapshotCache().get(now,path,this::fullSnapshot);
  WomSnapshotCache freshProcess=new WomSnapshotCache();
  WomSnapshotCache.Result result=freshProcess.get(now+3000,path,()->{
   throw new IOException("No WOM fetch permitted while cached");
  });
  assertFalse(result.stale);
  assertTrue(result.snapshot.eligible("test0"));
  assertEquals("House of 465",result.snapshot.chapters.get("House of 465").get(0).readableRank().equals("Owner")
      ?"House of 465":"missing");
 }
 @Test public void cachedNonmemberWarningHashesSurviveRestart() throws Exception {
  Path path=folder.newFile("wom-warning-cache.json").toPath();
  long now=1_800_000_000_000L;
  WomDirectoryClient.Snapshot original=fullSnapshot();
  Set<String> warningHashes=Collections.singleton(
    FederationPublicBanWarnings.hash("Non Member"));
  WomDirectoryClient.Snapshot withWarnings=new WomDirectoryClient.Snapshot(
    original.members,original.chapters,now,warningHashes,true);
  new WomSnapshotCache().get(now,path,()->withWarnings);
  WomSnapshotCache.Result restored=new WomSnapshotCache().get(now+3000,path,()->{
   throw new IOException("No network request needed on fresh cache");
  });
  assertTrue(restored.snapshot.publicBanStatusAvailable);
  assertTrue(FederationPublicBanWarnings.matches("Non Member",
    restored.snapshot.publicBanHashes,restored.snapshot.members));
 }
 @Test public void failedRefreshRetainsOldPublicRosterAndBacksOff() throws Exception {
  Path path=folder.newFile("wom-cache.json").toPath();
  long now=1_800_000_000_000L;
  WomSnapshotCache cache=new WomSnapshotCache();
  cache.get(now,path,this::fullSnapshot);
  AtomicInteger attempts=new AtomicInteger();
  WomSnapshotCache.Result stale=cache.get(now+WomSnapshotCache.FRESH_MS+1,path,()->{
   attempts.incrementAndGet();
   throw new IOException("WOM HTTP 429");
  });
  assertTrue(stale.stale);
  assertTrue(stale.snapshot.eligible("test8"));
  WomSnapshotCache.Result duringCooldown=cache.get(now+WomSnapshotCache.FRESH_MS+1000,path,()->{
   attempts.incrementAndGet();
   throw new IOException("No request during backoff");
  });
  assertTrue(duringCooldown.stale);
  assertEquals(1,attempts.get());
 }
 @Test public void honorsLongRetryAfterOnRateLimit() throws Exception {
  Path path=folder.newFile("wom-cache.json").toPath();
  long now=1_800_000_000_000L;
  WomSnapshotCache cache=new WomSnapshotCache();
  cache.get(now,path,this::fullSnapshot);
  AtomicInteger attempts=new AtomicInteger();
  long expiry=now+WomSnapshotCache.FRESH_MS+10;
  assertTrue(cache.get(expiry,path,()->{
   attempts.incrementAndGet();
   throw new WomDirectoryClient.RateLimited(4586,60L*60*1000);
  }).stale);
  assertTrue(cache.get(expiry+20L*60*1000,path,()->{
   attempts.incrementAndGet();
   throw new IOException("Retry-After still active");
  }).stale);
  assertEquals(1,attempts.get());
 }
 @Test public void excessiveStalenessIsNotAccepted() throws Exception {
  Path path=folder.newFile("wom-cache.json").toPath();
  long now=1_800_000_000_000L;
  new WomSnapshotCache().get(now,path,this::fullSnapshot);
  try{
   new WomSnapshotCache().get(now+WomSnapshotCache.MAX_STALE_MS+1,path,()->{
    throw new IOException("WOM offline");
   });
   fail("Must fail instead of using expired snapshot");
  }catch(IOException expected){assertEquals("WOM offline",expected.getMessage());}
 }
 @Test public void corruptCacheIsIgnored() throws Exception {
  Path path=folder.newFile("wom-cache.json").toPath();
  java.nio.file.Files.write(path,"{corrupt-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  WomSnapshotCache.Result current=new WomSnapshotCache().get(
    1_800_000_000_000L,path,this::fullSnapshot);
  assertTrue(current.snapshot.eligible("test15"));
 }
}
