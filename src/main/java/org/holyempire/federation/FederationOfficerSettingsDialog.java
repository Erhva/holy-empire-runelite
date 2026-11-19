package org.holyempire.federation;

import java.awt.Component;
import java.io.IOException;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import net.runelite.client.config.ConfigManager;

/**
 * Local-only officer configuration UI. Its visibility is a convenience, never
 * an authentication credential or a substitute for private API verification.
 */
final class FederationOfficerSettingsDialog {
 private static final String GROUP="holyEmpireFederation";
 private FederationOfficerSettingsDialog(){}

 static boolean validEndpoint(String endpoint){
  return endpoint!=null && (endpoint.trim().isEmpty() ||
    FederationBanUploadClient.acceptedEndpoint(endpoint.trim()));
 }

 /** Only the credential is editable; the HTTPS service address is fixed. */
 static JPanel credentialFields(JPasswordField token,boolean alreadyConfigured){
  JPanel fields=new JPanel(new GridLayout(0,1,0,5));
  fields.setBackground(HolyEmpireTheme.STEEL);
  HolyEmpireTheme.styleSearch(token);
  JLabel credentialLabel=new JLabel("Admin Credential Key (Leave Blank to Keep Existing)");
  credentialLabel.setName("holyEmpireAdminCredentialLabel");
  credentialLabel.setForeground(HolyEmpireTheme.GOLD);
  fields.add(credentialLabel);
  fields.add(token);
  JLabel status=new JLabel(alreadyConfigured?
    "An account-specific local credential is saved.":"No local credential configured");
  status.setForeground(HolyEmpireTheme.MUTED);
  fields.add(status);
  return fields;
 }

 static void show(Component parent,ConfigManager manager,
                  FederationOfficerCredentialStore secrets,String account,
                  BooleanSupplier authorized){
  if(!authorized.getAsBoolean())return;
  JPasswordField token=new JPasswordField(26);
  token.setName("holyEmpireAdminCredentialKey");
  token.setToolTipText("Leave blank to keep the existing admin credential key.");
  JPanel fields=credentialFields(token,!secrets.read(account).isEmpty());
  try {
   int choice=HolyEmpireDialogs.showConfirmDialog(parent,fields,"Admin Verification",
      JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
   if(choice!=JOptionPane.OK_OPTION || !authorized.getAsBoolean())return;
   char[] password=token.getPassword();
   try {
    if(password.length>0 && (password.length<24 || password.length>256)){
     HolyEmpireDialogs.showMessageDialog(parent,"Officer credentials must contain 24 to 256 characters.",
       "Invalid credential",JOptionPane.WARNING_MESSAGE);
     return;
    }
    if(!authorized.getAsBoolean())return;
    try {
     if(password.length>0)secrets.save(account,password);
    }catch(IOException error){
     HolyEmpireDialogs.showMessageDialog(parent,
       "The officer credential could not be stored in a protected local file.",
       "Credential storage unavailable",JOptionPane.WARNING_MESSAGE);
     return;
    }
    // The service endpoint is fixed; never accept it as an editable dialog value.
    manager.setConfiguration(GROUP,"banSyncEndpoint",
      FederationBanUploadClient.OFFICIAL_ENDPOINT);
    // Avoid RuneLite cloud/profile synchronization of bearer credentials.
    manager.unsetConfiguration(GROUP,"banSyncToken");
   } finally {
    Arrays.fill(password,'\0');
   }
  } finally {
   token.setText("");
  }
 }
}
