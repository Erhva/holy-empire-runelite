package org.holyempire.federation;

import javax.swing.JLabel;
import javax.swing.JButton;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationOfficerSettingsDialogTest {
 @Test public void privateSyncEndpointValidationRequiresHttps(){
  assertTrue(FederationOfficerSettingsDialog.validEndpoint(""));
  assertTrue(FederationOfficerSettingsDialog.validEndpoint("   "));
  assertTrue(FederationOfficerSettingsDialog.validEndpoint("https://bans.holyempireosrs.cc"));
  assertFalse(FederationOfficerSettingsDialog.validEndpoint("https://moderation.example.org"));
  assertFalse(FederationOfficerSettingsDialog.validEndpoint("https://bans.holyempireosrs.cc.evil.example"));
  assertFalse(FederationOfficerSettingsDialog.validEndpoint("http://moderation.example.org"));
  assertFalse(FederationOfficerSettingsDialog.validEndpoint("https://user@moderation.example.org"));
  assertFalse(FederationOfficerSettingsDialog.validEndpoint("https://moderation.example.org/?token=value"));
 }

 @Test public void verificationActionsRemainAvailableUntilExplicitClose() throws Exception {
  SwingUtilities.invokeAndWait(()->{
   AtomicInteger saves=new AtomicInteger();
   AtomicInteger requests=new AtomicInteger();
   AtomicInteger statuses=new AtomicInteger();
   AtomicInteger reviews=new AtomicInteger();
   AtomicInteger closes=new AtomicInteger();
   JPanel actions=FederationOfficerSettingsDialog.actionButtons(
     saves::incrementAndGet,requests::incrementAndGet,statuses::incrementAndGet,
     reviews::incrementAndGet,closes::incrementAndGet);
   assertEquals(5,actions.getComponentCount());
   assertEquals("holyEmpireRequestVerification",actions.getComponent(1).getName());
   assertEquals("holyEmpireCheckVerification",actions.getComponent(2).getName());
   assertEquals("holyEmpireReviewVerifications",actions.getComponent(3).getName());
   ((JButton)actions.getComponent(1)).doClick();
   ((JButton)actions.getComponent(2)).doClick();
   ((JButton)actions.getComponent(3)).doClick();
   ((JButton)actions.getComponent(1)).doClick();
   ((JButton)actions.getComponent(0)).doClick();
   assertEquals(2,requests.get());
   assertEquals(1,statuses.get());
   assertEquals(1,reviews.get());
   assertEquals(1,saves.get());
   assertEquals(0,closes.get());
   assertTrue(((JButton)actions.getComponent(1)).isEnabled());
   ((JButton)actions.getComponent(4)).doClick();
   assertEquals(1,closes.get());
  });
 }

 @Test public void adminVerificationShowsOnlyCredentialAndNoEditableEndpoint() throws Exception {
  SwingUtilities.invokeAndWait(()->{
   JPasswordField key=new JPasswordField(26);
   JPanel fields=FederationOfficerSettingsDialog.credentialFields(key,false);
   assertEquals("No HTTP address field or link",3,fields.getComponentCount());
   JLabel label=(JLabel)fields.getComponent(0);
   assertEquals("Admin Credential Key (Leave Blank to Keep Existing)",label.getText());
   assertSame(key,fields.getComponent(1));
   assertTrue(key.isEditable());
   assertEquals("No local credential configured",((JLabel)fields.getComponent(2)).getText());
   assertEquals("An account-specific local credential is saved.",
     ((JLabel)FederationOfficerSettingsDialog.credentialFields(
       new JPasswordField(26),true).getComponent(2)).getText());
  });
 }
}
