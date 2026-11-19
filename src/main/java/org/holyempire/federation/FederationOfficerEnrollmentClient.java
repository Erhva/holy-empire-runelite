package org.holyempire.federation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import net.runelite.client.util.Filepath;

/** Pending key is local-only. Only its SHA-256 digest is sent for enrollment. */
final class FederationOfficerEnrollmentClient {
 static final String ORIGIN=FederationBanUploadClient.OFFICIAL_ENDPOINT;
 private static final SecureRandom RANDOM=new SecureRandom();
 private final OkHttpClient http;
 private final Filepath directory;
 private final FederationOfficerCredentialStore keys;
 private final String origin;

 FederationOfficerEnrollmentClient(OkHttpClient http, Filepath directory,
     FederationOfficerCredentialStore keys){
  this(http,directory,keys,ORIGIN);
 }
 FederationOfficerEnrollmentClient(OkHttpClient http,Filepath directory,
     FederationOfficerCredentialStore keys,String origin){
  if(!FederationBanUploadClient.acceptedEndpoint(origin))
   throw new IllegalArgumentException("Untrusted verification endpoint");
  this.http=http.newBuilder().followRedirects(false).followSslRedirects(false)
    .connectTimeout(5,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).build();
  this.directory=directory;
  this.keys=keys;
  this.origin=origin.replaceAll("/+$","");
 }
 static String randomCredential(){
  byte[] random=new byte[32];
  RANDOM.nextBytes(random);
  try{return Base64.getUrlEncoder().withoutPadding().encodeToString(random);}
  finally{Arrays.fill(random,(byte)0);}
 }
 static String sha256(String input) throws IOException {
  try{
   byte[] hash=MessageDigest.getInstance("SHA-256").digest(
     input.getBytes(StandardCharsets.UTF_8));
   StringBuilder result=new StringBuilder(64);
   for(byte b:hash)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
   Arrays.fill(hash,(byte)0);
   return result.toString();
  }catch(java.security.NoSuchAlgorithmException ex){throw new IOException(ex);}
 }
 private Filepath pending(String rsn) throws IOException {
  String normalized=WomDirectoryClient.normalize(rsn);
  if(normalized.isEmpty() || normalized.length()>32)
   throw new IOException("Verified character required");
  return directory.joinSegment("pending-officer-"+sha256(normalized)+".json");
 }
 private JsonObject post(String operation,JsonObject body,String bearer) throws IOException {
  Request.Builder builder=new Request.Builder().url(
    origin+"/v1/officer-enroll/"+operation).post(RequestBody.create(
      MediaType.parse("application/json; charset=utf-8"),body.toString()));
  if(bearer!=null && !bearer.isEmpty())builder.header("Authorization","Bearer "+bearer);
  try(Response response=http.newCall(builder.build()).execute()){
   ResponseBody responseBody=response.body();
   if(responseBody==null || responseBody.contentLength()>8192)
    throw new IOException("Verification response unavailable");
   byte[] raw;
   try(java.io.InputStream input=responseBody.byteStream()){raw=input.readNBytes(8193);}
   if(raw.length>8192)throw new IOException("Verification response too large");
   if(response.code()!=200)
    throw new IOException("Verification service returned HTTP "+response.code());
   JsonObject data=new JsonParser().parse(
     new String(raw,StandardCharsets.UTF_8)).getAsJsonObject();
   return data;
  }catch(RuntimeException ex){throw new IOException("Invalid verification response",ex);}
 }
 private void savePending(String rsn,JsonObject pending) throws IOException {
  Filepath path=pending(rsn);
  if(path.isFile())throw new IOException("A verification request already exists locally");
  byte[] content=pending.toString().getBytes(StandardCharsets.UTF_8);
  directory.createDirectories();
  if(content.length>2048)throw new IOException("Verification state too large");
  Filepath temp=directory.createTempFile(".officer-request-",".tmp");
  try {
   try(FileChannel ch=temp.openFileChannel(StandardOpenOption.WRITE)){
    ByteBuffer buf=ByteBuffer.wrap(content);
    while(buf.hasRemaining())ch.write(buf);
    ch.force(true);
   }
   temp.moveTo(path);
  }finally{
   Arrays.fill(content,(byte)0);
   temp.deleteIfExists();
  }
 }
 private JsonObject readPending(String rsn) throws IOException {
  Filepath path=pending(rsn);
  if(!path.isFile() || path.size()<50 || path.size()>2048)
   throw new IOException("No pending verification for this character");
  try(FileChannel ch=path.openFileChannel(
       StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
   ByteBuffer buf=ByteBuffer.allocate((int)ch.size());
   while(buf.hasRemaining())if(ch.read(buf)<0)throw new IOException("Incomplete verification state");
   try{return new JsonParser().parse(
       new String(buf.array(),StandardCharsets.UTF_8)).getAsJsonObject();}
   catch(RuntimeException ex){throw new IOException("Invalid saved verification",ex);}
  }
 }
 String request(String rsn,String chapter) throws IOException {
  if(chapter==null || chapter.isEmpty())throw new IOException("Recognized chapter required");
  if(!keys.read(rsn).isEmpty())throw new IOException("An officer key is already configured");
  Filepath file=pending(rsn);
  if(file.isFile())throw new IOException("Check existing verification status first");
  String token=randomCredential();
  JsonObject request=new JsonObject();
  request.addProperty("rsn",rsn);
  request.addProperty("chapter",chapter);
  request.addProperty("token_sha256",sha256(token));
  JsonObject result=post("request",request,null);
  String link=result.get("url").getAsString();
  if(!link.startsWith("https://discord.com/oauth2/authorize?") ||
     link.length()>2048)throw new IOException("Unexpected Discord authorization link");
  JsonObject pending=new JsonObject();
  pending.addProperty("id",result.get("id").getAsString());
  pending.addProperty("ticket",result.get("ticket").getAsString());
  pending.addProperty("token",token);
  pending.addProperty("rsn",WomDirectoryClient.normalize(rsn));
  pending.addProperty("chapter",chapter);
  pending.addProperty("expires_at",result.get("expires_at").getAsLong());
  savePending(rsn,pending);
  return link;
 }
 /** Saves the locally generated key only after the trusted server approves it. */
 String check(String rsn,String chapter,BooleanSupplier stillEligible) throws IOException {
  if(!stillEligible.getAsBoolean())throw new IOException("Officer eligibility changed");
  JsonObject saved=readPending(rsn);
  if(!WomDirectoryClient.normalize(rsn).equals(saved.get("rsn").getAsString()) ||
     !chapter.equals(saved.get("chapter").getAsString()))
   throw new IOException("Pending verification belongs to another chapter");
  JsonObject request=new JsonObject();
  request.addProperty("id",saved.get("id").getAsString());
  request.addProperty("ticket",saved.get("ticket").getAsString());
  JsonObject result=post("status",request,null);
  String status=result.get("status").getAsString();
  if("approved".equals(status)){
   if(!stillEligible.getAsBoolean())throw new IOException("Officer eligibility changed");
   String bearer=saved.get("token").getAsString();
   keys.save(rsn,bearer.toCharArray());
   pending(rsn).deleteIfExists();
   return "Admin verification approved. Your key is active on this computer.";
  }
  if("expired".equals(status)||"rejected".equals(status)){
   pending(rsn).deleteIfExists();
  }
  return "Verification status: "+status.replace("_"," ")+".";
 }
 JsonArray list(String chapter,String approverToken) throws IOException {
  if(approverToken==null || approverToken.isEmpty())
   throw new IOException("An approved officer key is required to review requests");
  JsonObject request=new JsonObject();
  request.addProperty("chapter",chapter);
  return post("pending",request,approverToken).getAsJsonArray("requests");
 }
 String approve(String id,String rsn,String challenge,String role,
    String approverToken) throws IOException {
  if(approverToken==null || approverToken.isEmpty())
   throw new IOException("An approved officer key is required");
  JsonObject request=new JsonObject();
  request.addProperty("id",id);
  request.addProperty("observed_rsn",rsn);
  request.addProperty("challenge",challenge);
  request.addProperty("role",role);
  JsonObject result=post("approve",request,approverToken);
  if(!"approved".equals(result.get("status").getAsString()))
   throw new IOException("The server did not approve this officer");
  return "Verified "+result.get("rsn").getAsString()+" for "+
    result.get("chapter").getAsString()+".";
 }
}
