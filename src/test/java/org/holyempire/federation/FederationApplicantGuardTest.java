package org.holyempire.federation;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationApplicantGuardTest {
 private static final int ACCEPT=InterfaceID.ClansApplicants.ACCEPT;
 private static final int OTHER=InterfaceID.ClansApplicants.REJECT;
 private FederationBanDirectoryClient.Snapshot directory(){
  Map<String,FederationBanDirectoryClient.Chapter> chapters=new LinkedHashMap<>();
  chapters.put("Holy Chaos",new FederationBanDirectoryClient.Chapter("Holy Chaos",1795000000,1));
  chapters.put("Holy Empire",new FederationBanDirectoryClient.Chapter("Holy Empire",1795000001,2));
  return new FederationBanDirectoryClient.Snapshot(Arrays.asList(
   new FederationBanDirectoryClient.Entry("Holy Chaos","Harohad","unresolved",1795000000),
   new FederationBanDirectoryClient.Entry("Holy Empire","Banned One","tracked",1795000001)),
   chapters);
 }
 @Test public void detectsNativeApplicantsAcceptAndNotOtherAcceptButtons(){
  assertTrue(FederationApplicantGuard.isApplicantAcceptance("Accept",ACCEPT,0));
  assertTrue(FederationApplicantGuard.isApplicantAcceptance("Select",0,ACCEPT));
  assertTrue(FederationApplicantGuard.isApplicantAcceptance(
    "<col=fff>Accept</col>",OTHER,0));
  assertFalse(FederationApplicantGuard.isApplicantAcceptance("Accept",0,0));
  assertFalse(FederationApplicantGuard.isApplicantAcceptance("Select",OTHER,0));
  assertFalse(FederationApplicantGuard.isApplicantAcceptance("Reject",OTHER,0));
  assertFalse(FederationApplicantGuard.isApplicantAcceptance("Reject",ACCEPT,0));
  assertFalse(FederationApplicantGuard.isApplicantAcceptance("Recruit",0,0));
  assertFalse(FederationApplicantGuard.isApplicantAcceptance(
    "Accept",InterfaceID.ClansApplicants.ACCEPT+65536,0));
 }
 @Test public void matchesOnlySelectedDetailsNotUnrelatedApplicants(){
  assertEquals("Harohad",FederationApplicantGuard.matchedBannedApplicant(
     directory(),Arrays.asList("Applicant: <col=ffee00>Harohad</col>","Combat level: 126")));
  assertEquals("Harohad",FederationApplicantGuard.matchedBannedApplicant(
     directory(),Collections.singletonList("Harohad (level-126)")));
  assertNull("A non-banned applicant is not falsely accused",
    FederationApplicantGuard.matchedBannedApplicant(
      directory(),Collections.singletonList("Safe User")));
  assertNull("No directory means no private leakage",
    FederationApplicantGuard.matchedBannedApplicant(
      null,Collections.singletonList("Harohad")));
  assertNull("Do not match a different person's name embedded in a sentence",
    FederationApplicantGuard.matchedBannedApplicant(
      directory(),Collections.singletonList("A player named Harohad applied")));
  assertNull("Ambiguous selected details must not pick the wrong applicant",
    FederationApplicantGuard.matchedBannedApplicant(
      directory(),Arrays.asList("Harohad","Banned One")));
 }
 @Test public void clearKnownBannedAndUnknownStatusAreDistinguished(){
  FederationBanDirectoryClient.Snapshot approved=directory();
  assertEquals(FederationApplicantGuard.Status.BANNED,
    FederationApplicantGuard.status(
       approved,Collections.singletonList("Applicant: Harohad")));
  assertEquals(FederationApplicantGuard.Status.CLEAR,
    FederationApplicantGuard.status(
       approved,Arrays.asList("Applicant: Safe Player","Combat level: 100")));
  assertEquals(FederationApplicantGuard.Status.UNVERIFIED,
    FederationApplicantGuard.status(
       approved,Collections.singletonList("Applicant name unavailable")));
  assertEquals(FederationApplicantGuard.Status.UNVERIFIED,
    FederationApplicantGuard.status(
       null,Collections.singletonList("Applicant: Harohad")));
  assertEquals(FederationApplicantGuard.Status.UNVERIFIED,
    FederationApplicantGuard.status(approved,
      Arrays.asList("Harohad","Safe Player")));
 }

 @Test public void onlyConfirmedBanCancelsApplicantsAccept(){
  assertTrue(FederationApplicantGuard.shouldCancelAccept(
    FederationApplicantGuard.Status.BANNED));
  assertFalse("A verified nonmatch must not cause a popup or cancellation",
    FederationApplicantGuard.shouldCancelAccept(
    FederationApplicantGuard.Status.CLEAR));
  assertFalse("An unresolved name must not interrupt ordinary applicants",
    FederationApplicantGuard.shouldCancelAccept(
    FederationApplicantGuard.Status.UNVERIFIED));
 }

 @Test public void emptyApplicantsScreenDoesNotWarnOrBlock(){
  FederationBanDirectoryClient.Snapshot approved=directory();
  List<String> emptyScreen=Arrays.asList("Choose an applicant", "---",
    "Skill total: ---","Invite applications","Back","Refresh","...");
  assertNull(FederationApplicantGuard.selectedApplicantName(emptyScreen));
  assertFalse(FederationApplicantGuard.hasSelectedIdentityEvidence(emptyScreen));
  assertTrue(FederationApplicantGuard.hasSelectedIdentityEvidence(
    Arrays.asList("Harohad","99","Skill total: 1820")));
  assertNull(FederationApplicantGuard.matchedBannedApplicant(
    approved,emptyScreen));
  assertFalse(FederationApplicantGuard.shouldCancelAccept(
    FederationApplicantGuard.status(approved,emptyScreen)));
  assertFalse(FederationApplicantGuard.shouldCancelAccept(
    FederationApplicantGuard.status(approved,
      Collections.singletonList("Applicant: Safe Person"))));
  assertTrue(FederationApplicantGuard.shouldCancelAccept(
    FederationApplicantGuard.status(approved,
      Collections.singletonList("Applicant: Harohad"))));
 }

 @Test public void selectedNameDetectionPrefersExplicitNameNotOtherFields(){
  assertEquals("Harohad",FederationApplicantGuard.selectedApplicantName(
    Arrays.asList("Applicant: Harohad","Combat level: 126",
      "Total level: 1850")));
  assertEquals("Harohad",FederationApplicantGuard.selectedApplicantName(
    Arrays.asList("Harohad", "Combat level: 126")));
  assertEquals("Harohad",FederationApplicantGuard.selectedApplicantName(
    Arrays.asList("Harohad","99","84","72","---","Skill total: 2050")));
  assertEquals("123",FederationApplicantGuard.selectedApplicantName(
    Arrays.asList("Applicant: 123","99","82")));
  assertNull(FederationApplicantGuard.selectedApplicantName(
    Arrays.asList("99","84","72","---","Skill total: 2050")));
  assertNull(FederationApplicantGuard.selectedApplicantName(
    Arrays.asList("Harohad","Banned One")));
  assertNull(FederationApplicantGuard.selectedApplicantName(
    Arrays.asList("Applicant: Harohad","Applicant: Banned One")));
  assertNull(FederationApplicantGuard.selectedApplicantName(
    Collections.singletonList("Accept")));
 }

 @Test public void warnsWithoutLeakingPrivateNamesWhenSelectedNameUnknown(){
  String generic=FederationApplicantGuard.unverifiedWarning();
  assertTrue(generic.contains("not been verified"));
  assertTrue(generic.contains("Owner or Deputy Owner"));
  assertFalse(generic.contains("Harohad"));
  String matched=FederationApplicantGuard.bannedWarning(
    "Harohad",Collections.singletonList("Holy Chaos"));
  assertTrue(matched.contains("Harohad"));
  assertTrue(matched.contains("Holy Chaos"));
  assertTrue(matched.contains("Applicants -> Accept"));
 }
}
