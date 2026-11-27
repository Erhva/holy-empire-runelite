/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */
package org.holyempire.federation;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class FederationBanReasonClientTest
{
    private HttpServer server;
    private final AtomicReference<String> received = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile String reply = "{\"ok\":true,\"saved\":true,\"published\":false,"
        + "\"chapter\":\"Holy Chaos\",\"rsn\":\"Banned Test\",\"category\":\"Harassment\"}";

    private String base()
    {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Before public void start() throws Exception
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/ban-sync", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            received.set(new String(exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8));
            byte[] response = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,response.length);
            try (java.io.OutputStream out = exchange.getResponseBody())
            {
                out.write(response);
            }
        });
        server.start();
        System.setProperty("holyempire.testBanSyncOrigin",base());
    }

    @After public void stop()
    {
        System.clearProperty("holyempire.testBanSyncOrigin");
        if (server != null) server.stop(0);
    }

    private FederationBanRegistry.Snapshot snapshot()
    {
        return FederationBanRegistry.fromVisibleWidget("Holy Chaos",
            Arrays.asList("Banned Test","Other Ban","New Ban"),Instant.now());
    }

    @Test public void formListsNewBansFirstAndNeverPostsByDefault()
    {
        FederationBanUploadClient.Receipt receipt =
            new FederationBanUploadClient.Receipt("Synced 3 bans.",4,
                Arrays.asList("New Ban"));
        FederationBanReasonDialog.Form form =
            new FederationBanReasonDialog.Form(snapshot(),receipt);
        assertEquals("New Ban",form.player.getSelectedItem());
        assertFalse("No posting without an explicit officer click",form.publish.isSelected());
        assertEquals("Category must be blank by default", -1, form.category.getSelectedIndex());
        assertNull("Saving an untouched reason form must not create an entry",
            form.choice(false));
        assertEquals(5,form.category.getItemCount());
        assertEquals("Begging",form.category.getItemAt(0));
        assertEquals("Scamming/Luring",form.category.getItemAt(1));
        assertEquals("Botting/RWT",form.category.getItemAt(2));
        assertEquals("Harassment",form.category.getItemAt(3));
        assertEquals("Other",form.category.getItemAt(4));
        form.category.setSelectedItem("Harassment");
        form.reason.setText("Disruptive behavior");
        FederationBanReasonDialog.Choice choice = form.choice(true);
        assertEquals("New Ban",choice.rsn);
        assertEquals("Harassment",choice.category);
        assertEquals("Disruptive behavior",choice.reason);
        assertTrue(choice.another);
        assertFalse(choice.publish);
    }

    @Test public void payloadAcceptsOptionalReasonAndRejectsInvalidCategory()
    {
        JsonObject doc = FederationBanReasonClient.payload(
            "Holy Chaos","Banned Test","Other","",2,false);
        assertFalse(doc.get("publish").getAsBoolean());
        assertEquals("",doc.get("reason").getAsString());
        assertEquals(2,doc.get("revision").getAsInt());
        try
        {
            FederationBanReasonClient.payload(
                "Holy Chaos","Banned Test","Unapproved Category","",2,true);
            fail("Unknown categories must never leave the client");
        }
        catch (IllegalArgumentException expected) {}
        try
        {
            FederationBanReasonClient.payload(
                "Holy Chaos","Banned Test","Other","x".repeat(401),2,true);
            fail("Overlong reason must be rejected");
        }
        catch (IllegalArgumentException expected) {}
    }

    @Test public void savingPrivatelyDoesNotClaimDiscordPublication() throws Exception
    {
        FederationBanReasonClient.Result result = FederationBanReasonClient.send(
            "Holy Chaos","Banned Test","Harassment","Disruptive behavior",4,
            false,base(),"approved_officer_key_minimum_24_characters",new OkHttpClient());
        assertFalse(result.published);
        assertTrue(result.message.contains("Nothing was posted"));
        assertTrue(received.get().contains("\"action\":\"annotate\""));
        assertTrue(received.get().contains("\"publish\":false"));
        assertEquals("Bearer approved_officer_key_minimum_24_characters",
            authorization.get());
    }

    @Test public void clearReasonSendsOnlyAuthenticatedClearAction() throws Exception
    {
        reply = "{\"ok\":true,\"cleared\":true,\"chapter\":\"Holy Chaos\"," +
            "\"rsn\":\"Banned Test\",\"discord_posted\":false}";
        FederationBanReasonClient.Result result = FederationBanReasonClient.clear(
            "Holy Chaos","Banned Test",4,base(),
            "approved_officer_key_minimum_24_characters",new OkHttpClient());
        assertTrue(result.message.contains("cleared"));
        assertTrue(result.message.contains("remains active"));
        assertTrue(received.get().contains("\"action\":\"clear_reason\""));
        assertTrue(received.get().contains("\"revision\":4"));
        assertFalse(received.get().contains("\"publish\""));
        assertEquals("Bearer approved_officer_key_minimum_24_characters",
            authorization.get());
    }

    @Test public void previouslyPostedNoticeIsNotClaimedDeleted() throws Exception
    {
        reply = "{\"ok\":true,\"cleared\":true,\"chapter\":\"Holy Chaos\"," +
            "\"rsn\":\"Banned Test\",\"discord_posted\":true}";
        FederationBanReasonClient.Result result = FederationBanReasonClient.clear(
            "Holy Chaos","Banned Test",4,base(),
            "approved_officer_key_minimum_24_characters",new OkHttpClient());
        assertTrue(result.message.contains("remains unchanged"));
    }

    @Test public void explicitDiscordPublicationChecksFixedChannel() throws Exception
    {
        reply = "{\"ok\":true,\"saved\":true,\"published\":true,"
            + "\"chapter\":\"Holy Chaos\",\"rsn\":\"Banned Test\","
            + "\"category\":\"Harassment\","
            + "\"discord_url\":\"https://discord.com/channels/736564064834289774/"
            + "1391781923365453945/1599999999999999999\"}";
        FederationBanReasonClient.Result result = FederationBanReasonClient.send(
            "Holy Chaos","Banned Test","Harassment","Disruptive behavior",4,
            true,base(),"approved_officer_key_minimum_24_characters",new OkHttpClient());
        assertTrue(result.published);
        assertTrue(result.discordUrl.contains("/1391781923365453945/"));
        assertTrue(received.get().contains("\"publish\":true"));
    }
}
