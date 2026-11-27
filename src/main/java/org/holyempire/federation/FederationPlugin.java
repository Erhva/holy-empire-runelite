/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;
import com.google.inject.Provides;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import okhttp3.OkHttpClient;
import java.time.Instant;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = "Holy Empire", internalName = "holy-empire",
				  legacyDataDirectory = "holy-empire-officers",
				  description = "The Official Plugin for the Holy Empire Megaclan on W465.")
public class FederationPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private OverlayManager overlayManager;
	@Inject private ClientToolbar toolbar;
	@Inject private ChatIconManager chatIconManager;
	@Inject private Notifier notifier;
	@Inject private net.runelite.client.chat.ChatMessageManager chatMessageManager;
	@Inject private ClientThread clientThread;
	private final EmpireEventReminders eventReminders = new EmpireEventReminders();
	private final EmpireCommunicationsInbox communicationsInbox = new EmpireCommunicationsInbox();
	private final HolyEmpireAttendanceTracker attendanceTracker = new HolyEmpireAttendanceTracker();
	private volatile boolean attendanceSaved;
	private volatile String attendanceFeedback;
	private int attendanceUiTicks;
	private final java.util.concurrent.atomic.AtomicBoolean attendanceStartPending =
		new java.util.concurrent.atomic.AtomicBoolean(false);
	private int attendanceLeaseFailures;
	private volatile long attendanceEventEndMillis;
	private volatile boolean attendanceEndReminderSent;
	private volatile long attendanceManualReminderSlot;
	private HolyEmpireAttendanceRecoveryStore attendanceRecovery;
	private java.util.concurrent.ScheduledExecutorService attendanceRecoveryWorker;
	private final java.util.concurrent.atomic.AtomicBoolean attendanceCheckpointPending =
		new java.util.concurrent.atomic.AtomicBoolean(false);
	private volatile long attendanceUploadRetryAfter;

	private java.util.concurrent.ScheduledExecutorService communicationsWorker;
	// On a CDN/backend outage, avoid synchronized retry storms across clients.
	// These fields are used by the single communications worker.
	private int communicationsFailures;
	private long communicationsRetryAfter;
	// Achievement downloads must never queue behind broadcast / emergency polls.
	private java.util.concurrent.ScheduledExecutorService achievementWorker;
	private net.runelite.client.util.Filepath achievementCachePath;
	private boolean achievementCacheLoaded;
	private final Map<String, Integer> customRankIconIds = new HashMap<>();
	private final Set<Integer> renderedRankIconIndexes = new HashSet<>();
	// Associate previously emitted labels with the canonical WOM chapters.
	// This lets live settings changes replace old tags without duplicates.
	private final Map<String, Set<String>> priorMenuTagLabels = new HashMap<>();
	@Inject private FederationMinimapOverlay overlay;
	@Inject private HolyEmpireNamesOverlay namesOverlay;
	@Inject private FederationApplicantOverlay applicantOverlay;
	@Inject private HolyEmpireHouseAdvertisementOverlay houseAdvertisementOverlay;
	@Inject private FederationConfig config;
	@Inject private ConfigManager configManager;
	@Inject private OkHttpClient httpClient;
	private volatile boolean officerAuthorized;
	// This one-hour member bearer stays in process memory, never in RuneLite
	// syncable settings, the public roster or the officer credential store.
	private volatile ChapterEventsClient.MemberSession chapterEventMemberSession;
	// A capability snapshot is bound to one live Discord session token.
	// On reconnect, stale snapshots never authorize the next session.
	private volatile Set<String> liveAdminCapabilities = java.util.Collections.emptySet();
	private volatile String liveAdminSessionToken = "";
	private EmpireCommunicationsClient.Snapshot previousCommsSnapshot;
	private String previousCommsToken = "";
	private final java.util.concurrent.atomic.AtomicBoolean discordVerificationInProgress =
		new java.util.concurrent.atomic.AtomicBoolean(false);
	private volatile java.util.concurrent.ScheduledFuture<?> discordPollFuture;
	private final FederationSession session = new FederationSession();
	private final EmpireEventLoginRefreshGate eventLoginRefresh =
		new EmpireEventLoginRefreshGate();
	private java.util.concurrent.ScheduledFuture<?> attendanceRefreshAfterEvents;
	// A world hop briefly clears the native session but does not constitute a
	// new login for sidebar navigation. Reset on real login screen/RSN change.
	private String lastUiCharacter = "";
	private boolean newUiLogin = true;
	private boolean worldHopInProgress;
	private FederationOfficerCredentialStore officerSecrets;
	// Local per-RSN, per-environment rotating Discord credential; never ConfigManager.
	private FederationOfficerCredentialStore rememberedDiscord;
	private final java.util.concurrent.atomic.AtomicBoolean discordRestoreInProgress =
		new java.util.concurrent.atomic.AtomicBoolean(false);
	private volatile long discordRestoreRetryAt;
	private String lastVerifiedRankAccount = "";
	private Integer verifiedClanRank;
	private FederationOfficerEnrollmentClient enrollmentClient;
	private java.util.concurrent.ScheduledExecutorService scheduler;
	private java.util.concurrent.ExecutorService chapterImageWorker;
	private RemoteChapterImages chapterImages;
	private FederationPanel panel;
	private NavigationButton button;
	private EmpireHighlightOrderSettings inlineHighlightSettings;
	private FederationInGameBanSyncButton inGameBanSyncButton;
	private volatile boolean banUploadInProgress;
	private final FederationBanResyncReminder banResyncReminder = new FederationBanResyncReminder();
	private long nextBanResyncCheckMillis;
	private long lastNativeModerationReminderAt;
	private final Map<String, Long> recruitmentAlertTimes = new HashMap<>();
	private final FederationApplicantOneTimeBypass applicantBypass =
		new FederationApplicantOneTimeBypass();
	private final ClanVexillumEquipTrigger vexillumRefreshTrigger = new ClanVexillumEquipTrigger();
	private boolean pendingVexillumBanRefresh;

	@Provides FederationConfig provideConfig(net.runelite.client.config.ConfigManager c)
	{
		return c.getConfig(FederationConfig.class);
	}

	@Override protected void startUp() throws Exception
	{
		// RuneLite migrates the legacy local key directory on first access.
		// Neither the public roster cache nor private keys can escape this root.
		net.runelite.client.util.Filepath pluginData = getPluginDirectory();
		pluginData.createDirectories();
		officerSecrets = new FederationOfficerCredentialStore(pluginData);
		rememberedDiscord = new FederationOfficerCredentialStore(
			pluginData.joinSegment("discord-remembered"));
		enrollmentClient =
			new FederationOfficerEnrollmentClient(httpClient, pluginData, officerSecrets);
		WomDirectoryClient.usePluginCache(pluginData.joinSegment("wom-rosters.json"));
		attendanceRecovery = new HolyEmpireAttendanceRecoveryStore(pluginData);
		attendanceRecoveryWorker = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread worker = new Thread(task, "holy-empire-attendance-recovery");
			worker.setDaemon(true);
			return worker;
		});
		attendanceRecoveryWorker.scheduleWithFixedDelay(this::retryPendingAttendance, 20, 60,
														TimeUnit.SECONDS);
		achievementCachePath = pluginData.joinSegment("wom-achievements.json");
		achievementCacheLoaded = false;
		chapterImages =
			new RemoteChapterImages(httpClient, pluginData.joinSegment("chapter-image-cache"));
		HolyEmpireAssets.useRemoteChapterImages(chapterImages);
		chapterImageWorker = Executors.newSingleThreadExecutor(task -> {
			Thread thread = new Thread(task, "holy-empire-chapter-images");
			thread.setDaemon(true);
			return thread;
		});
		// The old syncable ConfigManager key must never retain a bearer secret.
		if (config.banSyncToken() != null && !config.banSyncToken().isEmpty())
		{
			configManager.unsetConfiguration("holyEmpireFederation", "banSyncToken");
		}
		overlayManager.add(overlay);
		overlayManager.add(namesOverlay);
		overlayManager.add(applicantOverlay);
		overlayManager.add(houseAdvertisementOverlay);
		panel = new FederationPanel();
		panel.setEventCollapsePersistence(
			configManager.getConfiguration("holyEmpireFederation", "collapsedEventIds"),
			value -> configManager.setConfiguration("holyEmpireFederation", "collapsedEventIds", value));
		panel.setEventImageLoader(
			new EventImageLoader(httpClient, () -> chapterEventMemberSession));
		panel.setEventHistoryDays(config.eventHistoryDays());
		panel.setEmergencyTickerEnabled(config.receiveEmergencyBroadcasts());
		inlineHighlightSettings = new EmpireHighlightOrderSettings(configManager);
		inlineHighlightSettings.start();
		panel.setOfficerSettingsOpener(() -> {
			final String account = session.account();
			final long generation = session.generation();
			if (!officerAuthorized || !session.eligibleFor(account, generation))
			{
				return;
			}
			beginDiscordOAuth(null, true);
		});
		panel.setBanDirectoryLoader(this::loadCombinedBans);
		panel.setBanReasonClearAction(this::clearSavedBanReason, this::canClearSavedBanReason);
		panel.setEventsLoader(this::loadDiscordEvents);
		panel.setChapterEventsHandlers(this::redeemChapterEventCode,
									   this::loadPrivateChapterEvents);
		panel.setChapterRsvpHandler(this::submitChapterRsvp, this::chapterRsvpAuthorized);
		panel.setDiscordConnector(
			() -> beginDiscordOAuth(message -> {
				if (panel == null || message == null || message.isEmpty() ||
					message.startsWith("Authorize Holy Empire in Discord") ||
					message.startsWith("Discord verified") ||
					message.startsWith("Empire Owner verified") ||
					(message.startsWith("Discord role ") && message.contains(" verified for ")))
					return;
				panel.showDiscordVerificationError(message, session::isEligible);
			}));
		panel.setDiscordAuthorization(this::chapterRsvpAuthorized);
		panel.setAdminPublishing(this::publishEmpireNotice, this::announcementSessionReady,
								 this::broadcastSessionReady, this::beginOwnerVerification);
		panel.setAnnouncementActions(this::publishLinkedEmpireNotice,
									 this::manageEmpireAnnouncement);
		panel.setBroadcastScopePublisher(this::publishScopedEmpireNotice);
		panel.setEmergencyActions(this::manageEmpireEmergency);
		panel.setAttendanceActions(this::startAttendance, this::stopAttendance,
								   this::saveAttendance, this::pauseAttendance);
		panel.setAttendanceHistoryAction(this::copyArchivedAttendance);
		panel.setAttendanceHistoryLoader(this::loadArchivedAttendanceNumbers);

		inGameBanSyncButton = new FederationInGameBanSyncButton(this::syncBanListFromGame);
		java.awt.image.BufferedImage sourceIcon;
		try (java.io.InputStream stream = getClass().getResourceAsStream("/holy-empire-icon.png"))
		{
			if (stream == null)
			{
				throw new IllegalStateException("Federation icon missing");
			}
			sourceIcon = javax.imageio.ImageIO.read(stream);
		}
		catch (java.io.IOException e)
		{
			throw new IllegalStateException("Failed to load Federation icon", e);
		}
		java.awt.image.BufferedImage image =
			new java.awt.image.BufferedImage(16, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
								  java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(sourceIcon, 0, 0, 16, 32, null);
		graphics.dispose();
		button = NavigationButton.builder()
					 .tooltip("Holy Empire")
					 .icon(image)
					 .priority(7)
					 .panel(panel)
					 .build();
		toolbar.addNavigation(button);
		scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "holy-empire-wom-sync");
			thread.setDaemon(true);
			return thread;
		});
		// The persistent thirty-minute WOM cache ensures login events never repeat a
		// full download while a fresh snapshot exists.
		scheduler.scheduleWithFixedDelay(this::sync, 0, 15, TimeUnit.MINUTES);
		// Poll the authorized feeds independently of whether Events is open.
		// Local one-minute checks give precise reminders without minute-by-minute
		// network calls; polls are paced to limit backend usage.
		scheduler.scheduleWithFixedDelay(this::refreshReminderFeeds, 1, 5, TimeUnit.MINUTES);
		scheduler.scheduleWithFixedDelay(this::checkEventReminderTimes, 1, 1, TimeUnit.MINUTES);
		// One ownership heartbeat every 40 seconds, never per game tick.
		scheduler.scheduleWithFixedDelay(this::renewAttendanceLease, 40, 40, TimeUnit.SECONDS);
		scheduler.scheduleWithFixedDelay(this::pollAttendanceEventStates, 12, 45, TimeUnit.SECONDS);
		communicationsWorker = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread worker = new Thread(task, "holy-empire-communications");
			worker.setDaemon(true);
			return worker;
		});
		communicationsWorker.scheduleWithFixedDelay(
			this::pollEmpireCommunications,
			java.util.concurrent.ThreadLocalRandom.current().nextInt(2, 17), 15, TimeUnit.SECONDS);
		// Every plugin reads the same cached 24-hour achievement feed.
		// Only the server-side collector contacts WOM (at most twice hourly).
		achievementWorker = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread worker = new Thread(task, "holy-empire-achievements");
			worker.setDaemon(true);
			return worker;
		});
		// Prime the spotlight immediately, concurrently with communications.
		// The first pass also restores a recent validated local snapshot.
		achievementWorker.scheduleWithFixedDelay(this::pollWomAchievements, 0, 5, TimeUnit.MINUTES);
		// Recheck successful connections approximately every 15 minutes;
		// transient Discord outages are retried automatically within a minute.
		scheduler.scheduleWithFixedDelay(this::restoreRememberedDiscord, 1, 1, TimeUnit.MINUTES);
	}

	private EmpireEventReminders.Slot[] configuredReminders()
	{
		return new EmpireEventReminders.Slot[] {
			new EmpireEventReminders.Slot(config.eventReminderFourHoursMinutes(),
										  config.eventReminderFourHours()),
			new EmpireEventReminders.Slot(config.eventReminderOneHourMinutes(),
										  config.eventReminderOneHour()),
			new EmpireEventReminders.Slot(config.eventReminderFifteenMinutesMinutes(),
										  config.eventReminderFifteenMinutes())};
	}

	private boolean anyEventReminderEnabled()
	{
		if (config.eventStartingNotification().isEnabled())
		{
			return true;
		}
		for (EmpireEventReminders.Slot slot : configuredReminders())
		{
			if (slot.notification != null && slot.notification.isEnabled())
			{
				return true;
			}
		}
		return false;
	}

	/** Invoked on the existing daemon worker; no polling on the game thread. */
	private void refreshReminderFeeds()
	{
		if (!session.isEligible() || !anyEventReminderEnabled())
		{
			eventReminders.clearFeeds();
			return;
		}
		String account = session.account();
		long generation = session.generation();
		try
		{
			DiscordEventsClient.Snapshot events = DiscordEventsClient.fetch(httpClient);
			if (session.eligibleFor(account, generation))
			{
				eventReminders.publish(account, true, events.events);
			}
		}
		catch (Exception unavailable)
		{
			// A failed feed must not trigger stale or canceled event reminders.
			eventReminders.clearFeed(true);
		}
		if (!session.eligibleFor(account, generation))
		{
			return;
		}
		try
		{
			List<DiscordEventsClient.Event> events =
				ChapterEventsClient.readRoster(account, httpClient);
			if (session.eligibleFor(account, generation))
			{
				eventReminders.publish(account, false, events);
			}
		}
		catch (Exception unavailable)
		{
			eventReminders.clearFeed(false);
		}
		if (session.eligibleFor(account, generation))
		{
			checkEventReminderTimes();
		}
	}

	private void checkEventReminderTimes()
	{
		checkAttendanceReminderTimes();
		if (!session.isEligible())
		{
			eventReminders.clearFeeds();
			return;
		}
		final String account = session.account();
		final long generation = session.generation();
		for (EmpireEventReminders.Alert alert : eventReminders.due(
				 account, Instant.now(), config.eventStartingNotification(), configuredReminders()))
		{
			clientThread.invoke(() -> {
				if (session.eligibleFor(account, generation) && alert.notification.isEnabled())
				{
					if (alert.notification.isOverride())
					{
						// User explicitly customized this notification; honor
						// their selected RuneLite channels and focus behavior.
						notifier.notify(alert.notification, alert.text);
					}
					else
					{
						// Defaults always go into the in-game chatbox, even
						// while RuneLite is focused or desktop alerts are off.
						chatMessageManager.queue(EmpireEventChatMessages.build(alert));
					}
				}
			});
		}
	}

	private void checkAttendanceReminderTimes()
	{
		HolyEmpireAttendanceTracker.Report report = attendanceTracker.report();
		if (report == null || !report.running || !session.isEligible())
			return;
		long now = System.currentTimeMillis();
		String notice = null;
		if ("manual".equals(report.scope))
		{
			long slot = HolyEmpireAttendanceReminderPolicy.nextManualSlot(
				report.startedAt, now, attendanceManualReminderSlot);
			if (slot > 0)
			{
				attendanceManualReminderSlot = slot;
				notice = "Holy Empire attendance for " + report.title + " is still running (" +
						 (slot * 30) + " minutes). "
						 + "Stop tracking when the event is complete.";
			}
		}
		else if (HolyEmpireAttendanceReminderPolicy.eventReminderDue(attendanceEventEndMillis, now,
																	 attendanceEndReminderSent))
		{
			attendanceEndReminderSent = true;
			notice = "The scheduled event " + report.title +
					 " ended 30 minutes ago. Your Holy Empire attendance tracker "
					 + "is still running; stop it when finished.";
		}
		if (notice == null)
			return;
		final String message = notice;
		final String account = session.account();
		final long generation = session.generation();
		clientThread.invoke(() -> {
			HolyEmpireAttendanceTracker.Report current = attendanceTracker.report();
			if (!session.eligibleFor(account, generation) || current == null || !current.running ||
				!current.reportId.equals(report.reportId))
				return;
			// Default behavior is always a game-chat reminder; no popup or
			// browser OAuth is required for routine attendance tracking.
			String formatted = new net.runelite.client.chat.ChatMessageBuilder()
								   .append(java.awt.Color.RED, message)
								   .build();
			chatMessageManager.queue(net.runelite.client.chat.QueuedMessage.builder()
										 .type(net.runelite.api.ChatMessageType.GAMEMESSAGE)
										 .value(message)
										 .runeLiteFormattedMessage(formatted)
										 .build());
		});
	}

	private void loadArchivedAttendanceNumbers()
	{
		if (!session.isEligible() || scheduler == null || scheduler.isShutdown())
			return;
		final ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		if (member == null || !member.valid(member.chapter))
			return;
		final String account = session.account();
		final long generation = session.generation();
		scheduler.execute(() -> {
			try
			{
				java.util.Map<String, Integer> index =
					HolyEmpireAttendanceReportClient.fetchIndex(member, httpClient);
				if (session.eligibleFor(account, generation) && panel != null)
					panel.displayAttendanceHistoryNumbers(index);
			}
			catch (Exception problem)
			{
				if (session.eligibleFor(account, generation) && panel != null)
					panel.setAttendanceHistoryStatus(
						"Attendance report numbers unavailable. Use Refresh to retry.");
			}
		});
	}

	private void copyArchivedAttendance(String eventId)
	{
		if (!session.isEligible() || scheduler == null || scheduler.isShutdown())
			return;
		final ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		if (member == null || !member.valid(member.chapter))
		{
			if (panel != null)
				panel.setAttendanceHistoryStatus(
					"Discord verification is refreshing. Try again shortly.");
			return;
		}
		final String account = session.account();
		final long generation = session.generation();
		if (panel != null)
			panel.setAttendanceHistoryStatus("Loading saved attendance...");
		scheduler.execute(() -> {
			try
			{
				HolyEmpireAttendanceReportClient.Saved saved =
					HolyEmpireAttendanceReportClient.fetch(eventId, member, httpClient);
				if (session.eligibleFor(account, generation) && panel != null)
					panel.copyArchivedAttendanceText(saved.text);
			}
			catch (Exception problem)
			{
				if (session.eligibleFor(account, generation) && panel != null)
					panel.setAttendanceHistoryStatus(
						"Event attendance unavailable: " +
						(problem.getMessage() == null ? "Try again later." : problem.getMessage()));
			}
		});
	}

	/** Public, account-independent events request. No Jagex/Discord identity is sent. */
	private boolean ownerPageAllowed()
	{
		if (!session.isEligible())
			return false;
		String account = WomDirectoryClient.normalize(session.account());
		if ("erhva".equals(account) || "not needed".equals(account) || "ærhva".equals(account))
		{
			// Display exception only. Server-owned Discord identities are
			// separately allowlisted before any publishing.
			return true;
		}
		net.runelite.api.clan.ClanRank rank = FederationInGameBanSyncButton.ownRank(client);
		return rank != null && rank.getRank() == net.runelite.api.clan.ClanRank.OWNER.getRank();
	}

	private boolean empireOwnerSessionReady()
	{
		ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		return ownerPageAllowed() && member != null && member.empireOwner &&
			member.valid(member.chapter);
	}

	private boolean liveAdminCapability(String capability)
	{
		ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		return session.isEligible() && member != null && member.valid(rsvpChapter()) &&
			member.token.equals(liveAdminSessionToken) &&
			liveAdminCapabilities.contains(capability);
	}

	private boolean announcementSessionReady()
	{
		return liveAdminCapability("announcements.publish");
	}

	private boolean broadcastSessionReady()
	{
		return liveAdminCapability("broadcasts.publish");
	}

	private boolean globalBanSearchSessionReady()
	{
		return liveAdminCapability("bans.search.global");
	}

	private void beginOwnerVerification()
	{
		if (!ownerPageAllowed())
			return;
		if (announcementSessionReady() && broadcastSessionReady())
		{
			if (panel != null)
				panel.showPublishStatus(
					"Discord verified. Your server-authorized publishing permissions are active.",
					this::ownerPageAllowed);
			return;
		}
		beginDiscordOAuth(message -> {
			if (panel != null)
			{
				panel.showPublishStatus(message, this::ownerPageAllowed);
			}
		});
	}

	private void publishEmpireNotice(String kind, String title, String message)
	{
		publishLinkedEmpireNotice(kind, title, message, "");
	}

	private void publishLinkedEmpireNotice(String kind, String title, String message,
										   String discordUrl)
	{
		publishScopedEmpireNotice(kind, title, message, discordUrl, "empire");
	}

	private void publishScopedEmpireNotice(String kind, String title, String message,
										   String audience)
	{
		publishScopedEmpireNotice(kind, title, message, "", audience);
	}

	private void publishScopedEmpireNotice(String kind, String title, String message,
										   String discordUrl, String audience)
	{
		final boolean announcement = "announcement".equals(kind);
		final java.util.function.BooleanSupplier permission =
			announcement ? this::announcementSessionReady : this::broadcastSessionReady;
		if (!permission.getAsBoolean() || communicationsWorker == null ||
			communicationsWorker.isShutdown())
		{
			if (panel != null)
				panel.showPublishStatus("Your verified rank cannot publish this notice.",
										session::isEligible);
			return;
		}
		final ChapterEventsClient.MemberSession owner = chapterEventMemberSession;
		final String chapter = owner.chapter;
		final String account = session.account();
		final long generation = session.generation();
		final java.util.function.BooleanSupplier stillCurrent =
			() -> session.eligibleFor(account, generation) && permission.getAsBoolean();
		communicationsWorker.execute(() -> {
			if (!stillCurrent.getAsBoolean())
				return;
			try
			{
				EmpireCommunicationsClient.Notice posted = EmpireCommunicationsClient.publish(
					httpClient, owner, chapter, kind, title, message, discordUrl, audience);
				if (stillCurrent.getAsBoolean())
				{
					if (panel != null)
					{
						panel.showPublishStatus("Published " + posted.kind + " #" + posted.id + ".",
												stillCurrent);
					}
					pollEmpireCommunications();
				}
			}
			catch (Exception error)
			{
				if (panel != null)
				{
					panel.showPublishStatus("Publishing failed: " +
												communicationsErrorDetail(error),
											() -> session.eligibleFor(account, generation));
				}
			}
		});
	}

	private void manageEmpireEmergency(String action)
	{
		if (!broadcastSessionReady() || communicationsWorker == null ||
			communicationsWorker.isShutdown())
			return;
		final ChapterEventsClient.MemberSession owner = chapterEventMemberSession;
		final String chapter = owner.chapter;
		final String account = session.account();
		final long generation = session.generation();
		communicationsWorker.execute(() -> {
			if (!session.eligibleFor(account, generation) || !broadcastSessionReady())
				return;
			try
			{
				EmpireCommunicationsClient.emergencyAction(httpClient, owner, chapter, action);
				if (session.eligibleFor(account, generation))
				{
					if (panel != null)
						panel.showPublishStatus("end".equals(action)
													? "Emergency ended; members have been notified."
													: "Emergency quietly cancelled.",
												() -> session.eligibleFor(account, generation));
					pollEmpireCommunications();
				}
			}
			catch (Exception error)
			{
				if (panel != null)
					panel.showPublishStatus("Emergency action failed: " +
												communicationsErrorDetail(error),
											() -> session.eligibleFor(account, generation));
			}
		});
	}

	private static String communicationsErrorDetail(Exception error)
	{
		String message = error.getMessage();
		if (message == null || message.isEmpty() || message.length() > 200 ||
			!message.matches("[A-Za-z0-9 ,.;:!?'()/-]+"))
			return "Network error. Please try again.";
		return message;
	}

	private void manageEmpireAnnouncement(String action, long id, String title, String message,
										  String discordUrl)
	{
		if (!announcementSessionReady() || communicationsWorker == null ||
			communicationsWorker.isShutdown())
			return;
		final ChapterEventsClient.MemberSession owner = chapterEventMemberSession;
		final String chapter = owner.chapter;
		final String account = session.account();
		final long generation = session.generation();
		communicationsWorker.execute(() -> {
			if (!session.eligibleFor(account, generation) || !announcementSessionReady())
				return;
			try
			{
				EmpireCommunicationsClient.manage(httpClient, owner, chapter, action, id, title,
												  message, discordUrl);
				if (session.eligibleFor(account, generation))
				{
					if (panel != null)
						panel.showPublishStatus("remove".equals(action) ? "Announcement removed."
																		  : "Announcement updated.",
												() -> session.eligibleFor(account, generation));
					pollEmpireCommunications();
				}
			}
			catch (Exception error)
			{
				if (panel != null)
					panel.showPublishStatus("Announcement change failed: " +
												communicationsErrorDetail(error),
											() -> session.eligibleFor(account, generation));
			}
		});
	}

	private void pollWomAchievements()
	{
		// The first load should not wait for a remote HTTP request. The
		// snapshot is public and time-validated, never used for permissions.
		if (!achievementCacheLoaded)
		{
			achievementCacheLoaded = true;
			try
			{
				WomAchievementsClient.Snapshot cached =
					WomAchievementsClient.readCached(achievementCachePath);
				if (cached != null && panel != null)
					panel.displayWomAchievements(cached, () -> true);
			}
			catch (Exception ignored)
			{
				// Corrupt or stale cached news is simply ignored.
			}
		}
		try
		{
			WomAchievementsClient.Snapshot feed =
				WomAchievementsClient.fetch(httpClient, achievementCachePath);
			if (panel != null)
				panel.displayWomAchievements(feed, () -> true);
		}
		catch (Exception error)
		{
			if (panel != null)
				panel.failWomAchievements(() -> true);
		}
	}

	/**
	 * A temporary Empire feed outage does not revoke permissions previously
	 * granted for the same still-live Discord bearer. The server rechecks
	 * every write. Explicit authorization denial, expired sessions and
	 * identity changes still fail closed.
	 */
	static boolean revokeCapabilitiesAfterCommunicationsFailure(String message,
																boolean sameVerifiedSession)
	{
		return !sameVerifiedSession || (message != null && (message.startsWith("HTTP 401:") ||
															message.startsWith("HTTP 403:") ||
															message.startsWith("HTTP 410:")));
	}

	/**
	 * Carry server-issued capabilities only across a successful silent
	 * renewal of the same remembered Discord identity and role. The
	 * backend still verifies the new bearer on every privileged write.
	 */
	static boolean sameRenewedDiscordAuthority(ChapterEventsClient.MemberSession oldMember,
											   ChapterEventsClient.MemberSession renewed)
	{
		return oldMember != null && renewed != null && oldMember.valid(oldMember.chapter) &&
			renewed.valid(renewed.chapter) && oldMember.chapter.equals(renewed.chapter) &&
			oldMember.discordAdmin == renewed.discordAdmin &&
			oldMember.empireOwner == renewed.empireOwner &&
			java.util.Objects.equals(oldMember.discordRole, renewed.discordRole);
	}

	static boolean currentCommunicationsRequest(ChapterEventsClient.MemberSession requested,
												String requestedChapter,
												ChapterEventsClient.MemberSession current,
												String currentChapter)
	{
		return requested == current && java.util.Objects.equals(requestedChapter, currentChapter);
	}

	private void pollEmpireCommunications()
	{
		if (System.currentTimeMillis() < communicationsRetryAfter)
		{
			return;
		}
		// The emergency notice is only fetched for a currently logged-in,
		// roster-confirmed Holy Empire member. Rank previews cannot bypass it.
		// The game thread updates FederationSession on logout, login and
		// account switch. Never read Client game state from this HTTP worker.
		if (!session.isEligible())
		{
			if (panel != null)
				panel.setMembershipConfirmed(false);
			return;
		}
		final String account = session.account();
		final long generation = session.generation();
		final java.util.function.BooleanSupplier stillConfirmed =
			() -> session.eligibleFor(account, generation);
		final ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		final String chapter = rsvpChapter();
		try
		{
			String token = (member != null && member.valid(chapter)) ? member.token : "";
			EmpireCommunicationsClient.Snapshot previous =
				previousCommsToken.equals(token) ? previousCommsSnapshot : null;
			EmpireCommunicationsClient.Snapshot snapshot =
				EmpireCommunicationsClient.fetch(httpClient, member, chapter, previous);
			// A silent Discord renewal may finish while this HTTP request
			// is in flight. A response for the OLD bearer must never clear
			// or overwrite the NEW bearer's verified capabilities.
			if (!stillConfirmed.getAsBoolean() ||
				!currentCommunicationsRequest(member, chapter, chapterEventMemberSession,
											  rsvpChapter()))
				return;
			previousCommsSnapshot = snapshot;
			previousCommsToken = token;
			communicationsFailures = 0;
			communicationsRetryAfter = 0;
			if (member != null && member == chapterEventMemberSession && member.valid(chapter))
			{
				liveAdminCapabilities = snapshot.capabilities;
				liveAdminSessionToken = member.token;
			}
			else
			{
				liveAdminCapabilities = java.util.Collections.emptySet();
				liveAdminSessionToken = "";
			}
			if (panel != null)
			{
				panel.displayCommunications(snapshot, stillConfirmed);
				clientThread.invoke(() -> {
					if (stillConfirmed.getAsBoolean() && panel != null)
					{
						panel.setAdminEligible(announcementSessionReady());
						panel.setBroadcastEligible(broadcastSessionReady());
						panel.setAttendanceEligible(attendanceAuthority());
						panel.setGlobalBanSearchEligible(globalBanSearchSessionReady());
					}
				});
			}
			List<EmpireCommunicationsClient.Notice> newMessages =
				communicationsInbox.newMessages(snapshot, Instant.now());
			for (EmpireCommunicationsClient.Notice item : newMessages)
			{
				if ("emergency".equals(item.kind) && !config.receiveEmergencyBroadcasts())
					continue;
				if ("broadcast".equals(item.kind) && !config.receiveEmpireBroadcasts())
					continue;
				clientThread.invoke(() -> {
					if (stillConfirmed.getAsBoolean())
					{
						chatMessageManager.queue(EmpireCommunicationsChatMessages.build(
							item, config.broadcastChatColor(), config.emergencyChatColor()));
					}
				});
			}
		}
		catch (Exception unavailable)
		{
			// A 403 or network exception from an old bearer must not revoke a
			// different, newly rotated bearer. Fresh requests are queued by
			// the renewal handler below.
			if (!stillConfirmed.getAsBoolean() ||
				!currentCommunicationsRequest(member, chapter, chapterEventMemberSession,
											  rsvpChapter()))
				return;
			// Retry at 30s, 60s, and then at most 120s while unreachable.
			// This is a backoff on transport failure, not admin authorization.
			communicationsFailures = Math.min(3, communicationsFailures + 1);
			long backoff = Math.min(120_000L, 15_000L * (1L << communicationsFailures));
			communicationsRetryAfter =
				System.currentTimeMillis() + backoff +
				java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 5001);
			ChapterEventsClient.MemberSession authenticated = chapterEventMemberSession;
			boolean sameVerifiedSession = authenticated != null &&
										  authenticated.valid(rsvpChapter()) &&
										  authenticated.token.equals(liveAdminSessionToken);
			boolean revoke = revokeCapabilitiesAfterCommunicationsFailure(unavailable.getMessage(),
																		  sameVerifiedSession);
			if (revoke)
			{
				liveAdminCapabilities = java.util.Collections.emptySet();
				liveAdminSessionToken = "";
				previousCommsSnapshot = null;
				previousCommsToken = "";
			}
			if (panel != null)
			{
				panel.failCommunications("Empire communications unavailable", stillConfirmed);
				if (revoke)
				{
					clientThread.invoke(() -> {
						if (stillConfirmed.getAsBoolean() && panel != null)
						{
							panel.setAdminEligible(false);
							panel.setBroadcastEligible(false);
							panel.setAttendanceEligible(false);
							panel.setGlobalBanSearchEligible(false);
						}
					});
				}
			}
		}
	}

	/**
	 * Event cards are populated on the Swing thread. Schedule the initial
	 * attendance-state read only after that update, so the backend sees the
	 * current event IDs rather than an empty list from the prior session.
	 */
	private void refreshAttendanceAfterEventLoad(
		java.util.function.BooleanSupplier stillCurrent,
		ChapterEventsClient.MemberSession verified)
	{
		if (verified == null || !verified.valid(verified.chapter))
			return;
		javax.swing.SwingUtilities.invokeLater(() -> {
			if (!stillCurrent.getAsBoolean() || verified != chapterEventMemberSession ||
				scheduler == null || scheduler.isShutdown())
				return;
			// Empire and chapter feeds can finish together on login. A brief
			// debounce polls both sets of event IDs once instead of producing
			// multiple network reads and repeated UI redraws.
			if (attendanceRefreshAfterEvents != null &&
				!attendanceRefreshAfterEvents.isDone())
				attendanceRefreshAfterEvents.cancel(false);
			attendanceRefreshAfterEvents = scheduler.schedule(
				this::pollAttendanceEventStates, 750, TimeUnit.MILLISECONDS);
		});
	}

	private void loadDiscordEvents()
	{
		if (scheduler == null || scheduler.isShutdown() || !session.isEligible())
		{
			if (panel != null)
			{
				panel.failEvents(
					"Events unavailable until your Holy Empire roster session is ready.",
					() -> true);
			}
			return;
		}
		final String account = session.account();
		final long generation = session.generation();
		scheduler.execute(() -> {
			java.util.function.BooleanSupplier stillMember =
				() -> session.eligibleFor(account, generation);
			try
			{
				DiscordEventsClient.Snapshot feed = DiscordEventsClient.fetch(httpClient);
				if (stillMember.getAsBoolean())
				{
					eventReminders.publish(account, true, feed.events);
					checkEventReminderTimes();
				}
				// Personal RSVPs are requested only with a verified Discord bearer;
				// never include them in the public feed or reminder cache.
				DiscordEventsClient.Snapshot personal = feed;
				ChapterEventsClient.MemberSession connected = chapterEventMemberSession;
				boolean lookupTemporarilyUnavailable = false;
				if (connected != null && connected.valid(connected.chapter))
				{
					try
					{
						java.util.Set<String> interested =
							ChapterEventsClient.empireInterest(feed.events, connected, httpClient);
						if (stillMember.getAsBoolean() && connected == chapterEventMemberSession)
						{
							personal = DiscordEventsClient.withPersonalEmpireInterest(feed, interested);
							eventLoginRefresh.interestSucceeded(account, generation);
						}
					}
					catch (java.io.IOException problem)
					{
						// A temporary HTTP error must not erase a last-confirmed
						// Interest result for this still-authorized Discord session.
						lookupTemporarilyUnavailable = true;
						// This used to silently leave every personal checkmark blank
						// after the cold-cache request timed out at login. Retry
						// automatically, but never treat failure as "Interested".
						if (stillMember.getAsBoolean() && connected == chapterEventMemberSession)
						{
							int delay = eventLoginRefresh.interestFailed(account, generation);
							if (delay > 0 && scheduler != null && !scheduler.isShutdown())
							{
								int attempt = eventLoginRefresh.failureCount(account, generation);
								org.slf4j.LoggerFactory.getLogger(FederationPlugin.class)
									.warn("Discord event interest unavailable ({}); retry in {}s",
										problem.getClass().getSimpleName(), delay);
								scheduler.schedule(() -> {
									if (stillMember.getAsBoolean() &&
										connected == chapterEventMemberSession &&
										eventLoginRefresh.retryPending(account, generation, attempt))
										loadDiscordEvents();
								}, delay, TimeUnit.SECONDS);
							}
							else
							{
								org.slf4j.LoggerFactory.getLogger(FederationPlugin.class)
									.warn("Discord event interest unavailable after retries ({})",
										problem.getClass().getSimpleName());
							}
						}
					}
				}
				// A response started for a previous Discord bearer must not
				// replace the new session's event indicators.
				if (connected != chapterEventMemberSession)
					return;
				if (panel != null)
				{
					panel.displayEvents(personal, stillMember,
						lookupTemporarilyUnavailable && connected != null &&
						connected.valid(connected.chapter));
					refreshAttendanceAfterEventLoad(stillMember, connected);
				}
			}
			catch (Exception error)
			{
				if (panel != null)
				{
					panel.failEvents("Events feed unavailable (" +
										 error.getClass().getSimpleName() + ")",
									 stillMember);
				}
			}
		});
	}

	private String eventViewerChapter()
	{
		// An eligible account sees the chapters returned by the roster-scoped
		// backend; the isolated Test backend only returns its sandbox chapter.
		return session.isEligible() ? "*" : "";
	}

	private void stopDiscordVerification()
	{
		java.util.concurrent.ScheduledFuture<?> pending = discordPollFuture;
		discordPollFuture = null;
		if (pending != null)
		{
			pending.cancel(false);
		}
		discordVerificationInProgress.set(false);
	}

	private void discordVerificationMessage(String message,
											java.util.function.BooleanSupplier current,
											java.util.function.Consumer<String> feedback)
	{
		if (panel != null)
		{
			panel.rsvpResult(message, current);
		}
		if (feedback != null)
		{
			enrollmentMessage(message, feedback);
		}
	}

	static long discordRenewalDelay(ChapterEventsClient.MemberSession member)
	{
		// A verified elevated Discord session lasts only ten minutes on the
		// server. Schedule the silent rotate with ample margin, not at 14m.
		if (member == null)
			return 60_000L;
		boolean elevated =
			member.discordAdmin || member.empireOwner || !member.discordRole.isEmpty();
		long remaining = member.expiresAt * 1000L - System.currentTimeMillis();
		long desired = elevated ? 4 * 60_000L : 14 * 60_000L;
		return Math.max(15_000L, Math.min(desired, Math.max(15_000L, remaining / 2)));
	}

	private void restoreRememberedDiscord()
	{
		if (rememberedDiscord == null || !session.isEligible() ||
			discordVerificationInProgress.get() ||
			System.currentTimeMillis() < discordRestoreRetryAt ||
			!discordRestoreInProgress.compareAndSet(false, true))
			return;
		String account = session.account();
		long generation = session.generation();
		String chapter = rsvpChapter();
		boolean restored = false;
		try
		{
			if (chapter == null || chapter.isEmpty())
				return;
			String saved = rememberedDiscord.read(account);
			if (saved.isEmpty())
				return;
			ChapterEventsClient.OAuthResult connected =
				ChapterEventsClient.resume(saved, chapter, httpClient);
			if (!session.eligibleFor(account, generation) || !chapter.equals(rsvpChapter()) ||
				discordVerificationInProgress.get())
				return;
			if (connected.member == null || connected.remembered.isEmpty())
				throw new java.io.IOException("Missing rotated Discord credential");
			rememberedDiscord.save(account, connected.remembered.toCharArray());
			restored = true;
			ChapterEventsClient.MemberSession priorMember = chapterEventMemberSession;
			boolean sameAuthority = sameRenewedDiscordAuthority(priorMember, connected.member);
			boolean keepCapabilities = sameAuthority &&
									   priorMember.token.equals(liveAdminSessionToken);
			if (keepCapabilities)
			{
				// Same account, same chapter and same Discord role:
				// avoid a menu flicker until the fresh feed is received.
				liveAdminSessionToken = connected.member.token;
			}
			else
			{
				// Role / chapter change, expiration or unknown prior bearer.
				// Discard the old authority immediately.
				liveAdminCapabilities = java.util.Collections.emptySet();
				liveAdminSessionToken = "";
			}
			chapterEventMemberSession = connected.member;
			previousCommsSnapshot = null;
			previousCommsToken = "";
			communicationsRetryAfter = 0;
			// Refresh capabilities immediately instead of waiting up to
			// fifteen seconds for the next scheduled communications poll.
			if (communicationsWorker != null && !communicationsWorker.isShutdown())
				communicationsWorker.execute(this::pollEmpireCommunications);
			if (panel != null)
				panel.chapterEventsConnected(
					() -> session.eligibleFor(account, generation), sameAuthority);
			// Populate both feeds immediately after silent Discord restoration,
			// even when Announcements is the current landing tab.
			loadDiscordEvents();
			loadPrivateChapterEvents();
		}
		catch (java.io.IOException problem)
		{
			if (session.eligibleFor(account, generation))
			{
				// A definitive Discord role revocation requires a fresh OAuth.
				// Timeouts/5xx must not deauthorize a still-valid in-memory
				// bearer or discard the securely remembered credential.
				boolean revoked =
					problem.getMessage() != null && problem.getMessage().contains("(HTTP 403)");
				if (revoked)
				{
					chapterEventMemberSession = null;
					try
					{
						rememberedDiscord.clear(account);
					}
					catch (java.io.IOException ignored)
					{
					}
					if (panel != null)
						panel.chapterEventsExpired(() -> session.eligibleFor(account, generation));
				}
			}
		}
		finally
		{
			ChapterEventsClient.MemberSession role = chapterEventMemberSession;
			boolean elevated = role != null && (role.discordAdmin || role.empireOwner ||
												!role.discordRole.isEmpty());
			// Even after intermittent connectivity loss, keep the current
			// bearer until it actually expires; a background retry restores
			// the verified role without requiring another OAuth browser popup.
			discordRestoreRetryAt =
				System.currentTimeMillis() + (restored ? discordRenewalDelay(role) : 45_000L);
			discordRestoreInProgress.set(false);
		}
	}

	private void beginDiscordOAuth(java.util.function.Consumer<String> feedback)
	{
		beginDiscordOAuth(feedback, false);
	}

	private void beginDiscordOAuth(java.util.function.Consumer<String> feedback,
								   boolean requireAdminRole)
	{
		if (scheduler == null || scheduler.isShutdown() || !session.isEligible())
		{
			if (feedback != null)
				feedback.accept("Log in to an eligible Holy Empire character.");
			return;
		}
		final String chapter = rsvpChapter();
		final String account = session.account();
		final long generation = session.generation();
		if (chapter == null || chapter.isEmpty())
		{
			if (feedback != null)
				feedback.accept("A recognized in-game chapter is required.");
			return;
		}
		java.util.function.BooleanSupplier current =
			() -> session.eligibleFor(account, generation) && chapter.equals(rsvpChapter());
		if (requireAdminRole && !officerAuthorized)
		{
			return;
		}
		java.util.function.Consumer<String> resultSink = requireAdminRole ? msg ->
		{
			if (panel != null)
			{
				panel.showAdminVerificationStatus(
					msg, () -> current.getAsBoolean() && officerAuthorized);
			}
		} : feedback;
		if (!discordVerificationInProgress.compareAndSet(false, true))
		{
			discordVerificationMessage("Discord verification is already in progress.", current,
									   resultSink);
			return;
		}
		scheduler.execute(() -> {
			try
			{
				ChapterEventsClient.OAuthAttempt request =
					ChapterEventsClient.beginOAuth(chapter, httpClient);
				if (!current.getAsBoolean())
				{
					stopDiscordVerification();
					return;
				}
				javax.swing.SwingUtilities.invokeLater(() -> {
					if (current.getAsBoolean())
					{
						net.runelite.client.util.LinkBrowser.browse(request.browserUrl);
					}
				});
				discordVerificationMessage(
					"Authorize Holy Empire in Discord; RuneLite will connect automatically.",
					current, resultSink);
				java.util.concurrent.atomic.AtomicInteger failures =
					new java.util.concurrent.atomic.AtomicInteger();
				discordPollFuture = scheduler.scheduleWithFixedDelay(() -> {
					if (!current.getAsBoolean() ||
						java.time.Instant.now().getEpochSecond() >= request.expiresAt)
					{
						stopDiscordVerification();
						if (current.getAsBoolean())
						{
							discordVerificationMessage(
								"Discord verification expired. Use Verify Discord to try again.",
								current, resultSink);
						}
						return;
					}
					try
					{
						ChapterEventsClient.OAuthResult status =
							ChapterEventsClient.pollOAuth(request, chapter, httpClient);
						if ("pending".equals(status.status))
						{
							failures.set(0);
							return;
						}
						stopDiscordVerification();
						if ("verified".equals(status.status) && status.member != null &&
							current.getAsBoolean())
						{
							if (rememberedDiscord != null && !status.remembered.isEmpty())
							{
								try
								{
									rememberedDiscord.save(account,
														   status.remembered.toCharArray());
								}
								catch (java.io.IOException ignored)
								{
									discordVerificationMessage(
										"Discord connected; this device could not save the "
											+ "remembered login.",
										current, resultSink);
								}
							}
							chapterEventMemberSession = status.member;
							discordRestoreRetryAt =
								System.currentTimeMillis() + discordRenewalDelay(status.member);
							if (panel != null)
								panel.chapterEventsConnected(current);
							loadDiscordEvents();
							String role = !status.member.discordRole.isEmpty()
											  ? status.member.discordRole
										  : status.member.discordAdmin ? "chapter administrator"
																	   : "chapter member";
							String message =
								requireAdminRole
									? status.member.discordAdmin
										  ? "Discord role " + role + " verified for " + chapter +
												". Sensitive officer permissions still require "
												+ "an approved RuneScape-bound credential."
										  : "Your Discord account is a chapter member, but it "
												+ "does not have the administrator role."
								: feedback != null
									? "Discord verified as " + role +
										  ". Publishing permissions follow your server-approved "
										  + "capabilities and are checked independently."
									: null;
							// Membership/RSVP authorization is represented by
							// the removed Verify button, not a persistent
							// confirmation banner in the Events list.
							if (message != null)
								discordVerificationMessage(message, current, resultSink);
							loadPrivateChapterEvents();
							return;
						}
						discordVerificationMessage(
							"Discord role verification was denied or expired.", current,
							resultSink);
					}
					catch (Exception error)
					{
						if (failures.incrementAndGet() >= 3)
						{
							stopDiscordVerification();
							discordVerificationMessage(
								"Discord connection unavailable. Use Verify Discord to retry.",
								current, resultSink);
						}
					}
				}, 1, 2, TimeUnit.SECONDS);
			}
			catch (Exception error)
			{
				stopDiscordVerification();
				discordVerificationMessage(
					error.getMessage() != null && error.getMessage().contains("HTTP 404")
						? "Discord verification unavailable (HTTP 404): production verification "
							  + "endpoint is not enabled."
						: "Discord verification unavailable. Use Verify Discord to retry.",
					current, resultSink);
			}
		});
	}

	private boolean attendanceAuthority()
	{
		// The live server grants or revokes attendance.manage. Do not
		// duplicate its role matrix in client code: roles can change without
		// changing the Plugin Hub commit.
		return liveAdminCapability("attendance.manage");
	}

	private void showAttendanceStatus(String message)
	{
		attendanceFeedback = message;
		if (panel != null)
		{
			panel.displayAttendance(attendanceTracker.report(), message, attendanceSaved);
		}
	}

	private boolean attendanceMayOperate(String scope)
	{
		// The same server-issued capability is mandatory for manual and
		// event-scoped attendance. A verified ordinary member cannot start
		// tracking by invoking a hidden UI callback.
		if (!attendanceAuthority())
			return false;
		if ("manual".equals(scope))
			return true;
		ChapterEventsClient.MemberSession verified = chapterEventMemberSession;
		return verified != null && verified.valid(verified.chapter) &&
			("chapter".equals(scope) || "empire".equals(scope));
	}

	private void startAttendance(String eventId, String scope, String chapter, String title)
	{
		if (!attendanceMayOperate(scope))
		{
			showAttendanceStatus("Discord is verified only for recognized members. "
								 + "Connect a verified Discord session to take event attendance.");
			return;
		}
		HolyEmpireAttendanceTracker.Report previous = attendanceTracker.report();
		if (previous != null && previous.running)
		{
			showAttendanceStatus("Attendance is already running for " + previous.title +
								 ". Stop it first.");
			return;
		}
		if ("manual".equals(scope))
		{
			clientThread.invoke(() -> beginLocalAttendance(eventId, scope, chapter, title, false));
			return;
		}
		if (scheduler == null || scheduler.isShutdown() ||
			!attendanceStartPending.compareAndSet(false, true))
		{
			showAttendanceStatus("An attendance request is already in progress.");
			return;
		}
		final ChapterEventsClient.MemberSession verified = chapterEventMemberSession;
		final long generation = session.generation();
		final String account = session.account();
		showAttendanceStatus("Checking event attendance permissions...");
		scheduler.execute(() -> {
			boolean accepted = false;
			try
			{
				HolyEmpireAttendanceSessionClient.Result started =
					HolyEmpireAttendanceSessionClient.send("start", eventId, scope, chapter,
														   verified, httpClient);
				if (!started.ownSession ||
					!("started".equals(started.status) || "active".equals(started.status)))
					throw new java.io.IOException("Attendance session could not be claimed");
				accepted = true;
				if (!session.eligibleFor(account, generation))
					throw new java.io.IOException(
						"RuneScape session changed during attendance authorization");
				clientThread.invoke(
					() -> beginLocalAttendance(eventId, scope, chapter, title, true));
				accepted = false; // Local client-thread callback now owns cleanup.
			}
			catch (Exception error)
			{
				showAttendanceStatus(error.getMessage() == null
										 ? "Attendance authorization is unavailable; try again."
										 : error.getMessage());
			}
			finally
			{
				if (accepted)
				{
					try
					{
						HolyEmpireAttendanceSessionClient.send("finish", eventId, scope, chapter,
															   verified, httpClient);
					}
					catch (Exception ignored)
					{
					}
				}
				attendanceStartPending.set(false);
			}
		});
	}

	private void beginLocalAttendance(String eventId, String scope, String chapter, String title,
									  boolean serverClaimed)
	{
		try
		{
			String account = session.account();
			if (client.getGameState() != GameState.LOGGED_IN || !attendanceMayOperate(scope))
				throw new IllegalStateException(
					"A verified, logged-in Holy Empire member is required");
			HolyEmpireAttendanceTracker.Report previous = attendanceTracker.report();
			if (previous != null && previous.running)
				throw new IllegalStateException("Stop the previous attendance session first");
			// Finishing attendance is the completion boundary. An upload
			// outage must not prevent the next session, provided the previous
			// immutable report is durably retained in the local recovery outbox.
			if (previous != null && !attendanceSaved)
			{
				if (attendanceRecovery == null)
					throw new IllegalStateException(
						"Previous attendance has not been saved locally. "
						+ "Save it before starting a new session.");
				try
				{
					attendanceRecovery.checkpoint(previous, System.currentTimeMillis());
				}
				catch (java.io.IOException error)
				{
					throw new IllegalStateException(
						"Previous attendance could not be saved locally. "
						+ "Retry saving before starting another session.");
				}
				if (attendanceRecoveryWorker != null && !attendanceRecoveryWorker.isShutdown())
					attendanceRecoveryWorker.execute(this::retryPendingAttendance);
			}
			if (previous != null)
				attendanceTracker.clear();
			attendanceTracker.start(eventId, scope, chapter, title, account,
									session.rosterFor(account), System.currentTimeMillis());
			attendanceSaved = false;
			try
			{
				if (attendanceRecovery != null)
					attendanceRecovery.checkpoint(attendanceTracker.report(),
												  System.currentTimeMillis());
			}
			catch (java.io.IOException error)
			{
				showAttendanceStatus(
					"Local attendance recovery unavailable; "
					+ "tracking is still active, but this report is not crash-protected.");
			}
			attendanceUiTicks = 0;
			attendanceLeaseFailures = 0;
			attendanceEventEndMillis = "manual".equals(scope) || panel == null
										   ? 0L
										   : panel.attendanceEventEndMillis(eventId);
			attendanceEndReminderSent = false;
			attendanceManualReminderSlot = 0L;
			showAttendanceStatus("Recording nearby rostered members across all chapters.");
			if (!"manual".equals(scope) && scheduler != null && !scheduler.isShutdown())
			{
				// Load existing event totals only for display. A new session
				// keeps its own UUID and uploads only newly observed ticks.
				final String newReportId = attendanceTracker.report().reportId;
				final long generation = session.generation();
				final ChapterEventsClient.MemberSession verified = chapterEventMemberSession;
				scheduler.execute(() -> {
					try
					{
						HolyEmpireAttendanceReportClient.Saved saved =
							HolyEmpireAttendanceReportClient.fetch(eventId, verified, httpClient);
						if (session.eligibleFor(account, generation) && panel != null)
							panel.displayAttendancePrior(newReportId, saved.previous);
					}
					catch (Exception noEarlierAttendance)
					{
						// New events have no archived report yet. Do not
						// interrupt ongoing proximity tracking.
					}
				});
			}
		}
		catch (IllegalArgumentException | IllegalStateException error)
		{
			showAttendanceStatus(error.getMessage());
			if (serverClaimed && scheduler != null && !scheduler.isShutdown())
			{
				final ChapterEventsClient.MemberSession member = chapterEventMemberSession;
				scheduler.execute(() -> {
					try
					{
						HolyEmpireAttendanceSessionClient.send("finish", eventId, scope, chapter,
															   member, httpClient);
					}
					catch (Exception ignored)
					{
					}
				});
			}
		}
	}

	private void pollAttendanceEventStates()
	{
		if (panel == null || !session.isEligible())
			return;
		java.util.List<String> ids = panel.attendanceVisibleEventIds();
		if (ids == null)
			return; // Also fetch active events outside the Discord feed.
		ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		if (member == null || !member.valid(member.chapter))
			return;
		String account = session.account();
		long generation = session.generation();
		try
		{
			java.util.Map<String, HolyEmpireAttendanceEventStatusClient.State> states =
				HolyEmpireAttendanceEventStatusClient.fetch(ids, member, httpClient);
			if (session.eligibleFor(account, generation) && panel != null)
				panel.displayAttendanceServerStates(states);
		}
		catch (Exception error)
		{
			// A failing background status read is not grounds to revoke the
			// user's otherwise valid Discord verification or stop tracking.
		}
	}

	private void renewAttendanceLease()
	{
		HolyEmpireAttendanceTracker.Report report = attendanceTracker.report();
		if (report == null || !report.running || "manual".equals(report.scope))
			return;
		try
		{
			HolyEmpireAttendanceSessionClient.Result result =
				HolyEmpireAttendanceSessionClient.send("heartbeat", report.eventId, report.scope,
													   report.chapter, chapterEventMemberSession,
													   httpClient);
			if (!"active".equals(result.status) || !result.ownSession)
				throw new HolyEmpireAttendanceSessionClient.Rejected(
					409, "Your attendance session is no longer active.");
			attendanceLeaseFailures = 0;
		}
		catch (Exception error)
		{
			boolean lost = error instanceof HolyEmpireAttendanceSessionClient.Rejected &&
						   (((HolyEmpireAttendanceSessionClient.Rejected)error).code == 409 ||
							((HolyEmpireAttendanceSessionClient.Rejected)error).code == 403);
			if (lost || ++attendanceLeaseFailures >= 3)
			{
				String message = lost ? "Attendance authorization changed. Tracking stopped; "
											+ "your recorded report is still available."
									  : "Attendance server unavailable; tracking stopped to "
											+ "protect the recorded report.";
				clientThread.invoke(() -> {
					HolyEmpireAttendanceTracker.Report current = attendanceTracker.report();
					if (current != null && current.running &&
						current.reportId.equals(report.reportId))
					{
						attendanceTracker.finish(System.currentTimeMillis());
						showAttendanceStatus(message);
					}
				});
			}
		}
	}

	private void pauseAttendance()
	{
		clientThread.invoke(() -> {
			HolyEmpireAttendanceTracker.Report current = attendanceTracker.report();
			if (current == null || !current.running)
				return;
			if (attendanceTracker.paused())
			{
				attendanceTracker.resume();
				showAttendanceStatus("Attendance resumed. Proximity tracking is active.");
			}
			else
			{
				attendanceTracker.pause();
				checkpointAttendanceOnWorker(attendanceTracker.report());
				showAttendanceStatus("Attendance paused. No observation time is "
									 + "counted until tracking resumes.");
			}
		});
	}

	private void stopAttendance()
	{
		clientThread.invoke(() -> {
			if (!attendanceTracker.active())
				return;
			HolyEmpireAttendanceTracker.Report completed =
				attendanceTracker.finish(System.currentTimeMillis());
			attendanceSaved = false;
			try
			{
				if (attendanceRecovery != null)
					attendanceRecovery.checkpoint(completed, System.currentTimeMillis());
			}
			catch (java.io.IOException error)
			{
				showAttendanceStatus("Could not persist attendance locally; "
									 + "keep RuneLite open and retry archiving.");
			}
			showAttendanceStatus("Attendance stopped. Saving to the private Discord archive...");
			if (!"manual".equals(completed.scope) && scheduler != null && !scheduler.isShutdown())
			{
				final ChapterEventsClient.MemberSession member = chapterEventMemberSession;
				scheduler.execute(() -> {
					try
					{
						HolyEmpireAttendanceSessionClient.send("finish", completed.eventId,
															   completed.scope, completed.chapter,
															   member, httpClient);
					}
					catch (Exception ignored)
					{
					}
				});
			}
			saveAttendance();
		});
	}

	private void saveAttendance()
	{
		HolyEmpireAttendanceTracker.Report report = attendanceTracker.report();
		if (report == null || report.running || attendanceRecoveryWorker == null ||
			attendanceRecoveryWorker.isShutdown())
		{
			showAttendanceStatus("Finish attendance before saving the report.");
			return;
		}
		if (attendanceSaved)
		{
			showAttendanceStatus("Success: this attendance report is already archived" +
								 (panel != null ? " in Discord." : "."));
			return;
		}
		// Persist the immutable report before any network attempt; this
		// survives a failed upload, RuneLite restart or browser authorization.
		try
		{
			if (attendanceRecovery == null)
				throw new java.io.IOException("Attendance recovery unavailable");
			attendanceRecovery.checkpoint(report, System.currentTimeMillis());
		}
		catch (java.io.IOException error)
		{
			showAttendanceStatus("Attendance could not be saved locally. "
								 +
								 "Keep RuneLite running and retry; the report is still in memory.");
			return;
		}
		showAttendanceStatus("Attendance queued for the private Discord archive.");
		attendanceRecoveryWorker.execute(this::retryPendingAttendance);
	}

	static String attendanceArchiveInstructions(int number)
	{
		return "Attendance saved as report " + number +
			"\n\nIn Holy Empire Discord, type /attendance and select report>" + number +
			"\n\nAdditional commands:" +
			"\nevent - Searches based on event ID's" +
			"\nchapter - Select the specific chapter attendance you would like to retrieve for the event" +
			"\npost - Select whether you would like the report posted for everyone in the channel or a private message" +
			"\npage - View a single page of the attendance report";
	}

	private void showFirstAttendanceArchiveHelp(int number)
	{
		if (!config.showAttendanceArchiveHelp() ||
			Boolean.parseBoolean(configManager.getConfiguration(
				"holyEmpireFederation", "attendanceArchiveHelpShown")))
			return;
		configManager.setConfiguration("holyEmpireFederation", "attendanceArchiveHelpShown", true);
		javax.swing.SwingUtilities.invokeLater(() -> {
			if (panel == null || java.awt.GraphicsEnvironment.isHeadless()) return;
			javax.swing.JPanel instructions = new javax.swing.JPanel(new java.awt.BorderLayout(0, 12));
			javax.swing.JTextArea text = new javax.swing.JTextArea(attendanceArchiveInstructions(number));
			text.setEditable(false);
			text.setLineWrap(true);
			text.setWrapStyleWord(true);
			text.setOpaque(false);
			text.setColumns(48);
			text.setRows(12);
			javax.swing.JScrollPane instructionsScroll = new javax.swing.JScrollPane(text);
			instructionsScroll.setBorder(null);
			instructionsScroll.setPreferredSize(new java.awt.Dimension(540, 330));
			instructions.add(instructionsScroll, java.awt.BorderLayout.CENTER);
			javax.swing.JCheckBox hide = new javax.swing.JCheckBox("Don't show this again");
			instructions.add(hide, java.awt.BorderLayout.SOUTH);
			javax.swing.JOptionPane option = new javax.swing.JOptionPane(instructions,
				javax.swing.JOptionPane.INFORMATION_MESSAGE);
			javax.swing.JDialog dialog = option.createDialog(panel,
				"Retrieving Attendance from Discord");
			java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(panel);
			java.awt.GraphicsConfiguration screen = owner == null
				? java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
					.getDefaultScreenDevice().getDefaultConfiguration()
				: owner.getGraphicsConfiguration();
			java.awt.Rectangle bounds = screen.getBounds();
			dialog.setLocation(bounds.x + Math.max(0,(bounds.width-dialog.getWidth())/2),
				bounds.y + Math.max(0,(bounds.height-dialog.getHeight())/2));
			dialog.setVisible(true);
			dialog.dispose();
			if (hide.isSelected())
				configManager.setConfiguration("holyEmpireFederation",
					"showAttendanceArchiveHelp", false);
		});
	}

	private void checkpointAttendanceOnWorker(HolyEmpireAttendanceTracker.Report report)
	{
		if (report == null || attendanceRecovery == null || attendanceRecoveryWorker == null ||
			attendanceRecoveryWorker.isShutdown() ||
			!attendanceCheckpointPending.compareAndSet(false, true))
			return;
		attendanceRecoveryWorker.execute(() -> {
			try
			{
				attendanceRecovery.checkpoint(report, System.currentTimeMillis());
			}
			catch (java.io.IOException ignored)
			{
				// Next scheduled checkpoint/stop retries. Never write names,
				// tokens or private attendance bodies into RuneLite logs.
			}
			finally
			{
				attendanceCheckpointPending.set(false);
			}
		});
	}

	private void retryPendingAttendance()
	{
		if (attendanceRecovery == null || System.currentTimeMillis() < attendanceUploadRetryAfter)
			return;
		final ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		if (!session.isEligible() || member == null || !member.valid(member.chapter))
			return;
		final String account = session.account();
		final long generation = session.generation();
		try
		{
			java.util.List<HolyEmpireAttendanceRecoveryStore.Pending> records =
				attendanceRecovery.pendingFor(account);
			HolyEmpireAttendanceTracker.Report current = attendanceTracker.report();
			String activeId = current != null && current.running ? current.reportId : "";
			int attempts = 0;
			for (HolyEmpireAttendanceRecoveryStore.Pending pending : records)
			{
				if (!session.eligibleFor(account, generation) || attempts >= 6)
					break;
				if (pending.reportId.equals(activeId))
					continue;
				attempts++;
				try
				{
					HolyEmpireAttendanceArchiveClient.Confirmation confirmation =
						HolyEmpireAttendanceArchiveClient.saveEncodedWithNumber(pending.payload,
																				member, httpClient);
					if (!pending.reportId.equals(confirmation.uuid))
						throw new java.io.IOException("Attendance archive ID mismatch");
					attendanceRecovery.acknowledge(pending.reportId);
					current = attendanceTracker.report();
					if (current != null && current.reportId.equals(confirmation.uuid) &&
						!current.running)
					{
						attendanceSaved = true;
						if (panel != null)
							panel.setAttendanceNumber(confirmation.uuid, confirmation.number);
						showAttendanceStatus("Attendance archived for Discord. "
											 + "Report #" + confirmation.number);

						final String savedId = confirmation.uuid;
						final boolean eventStarted = !"manual".equals(current.scope);
						clientThread.invoke(() -> {
							HolyEmpireAttendanceTracker.Report latest = attendanceTracker.report();
							if (latest != null && !latest.running && savedId.equals(latest.reportId))
							{
								attendanceTracker.clear();
								attendanceSaved = false;
								if (panel != null)
									panel.attendanceArchivedAndReset(eventStarted);
							}
						});
						showFirstAttendanceArchiveHelp(confirmation.number);
					}
					else if (panel != null && (current == null || !current.running))
					{
						showAttendanceStatus("Recovered attendance uploaded: report #" +
											 confirmation.number + ".");
					}
				}
				catch (Exception upload)
				{
					attendanceUploadRetryAfter = System.currentTimeMillis() + 60_000L;
					current = attendanceTracker.report();
					if (current != null && current.reportId.equals(pending.reportId))
						showAttendanceStatus("Attendance is saved locally. "
											 + "Automatic archive retry is scheduled.");
					break; // Don't hammer the Discord backend during outages.
				}
			}
		}
		catch (java.io.IOException ignored)
		{
			attendanceUploadRetryAfter = System.currentTimeMillis() + 60_000L;
		}
	}

	private void observeAttendance(String name, Player local)
	{
		HolyEmpireAttendanceTracker.Report report = attendanceTracker.report();
		if (report == null)
			return;
		if (!name.isEmpty() && !name.equals(report.host))
		{
			if (attendanceTracker.active())
				attendanceTracker.finish(System.currentTimeMillis());
			if (attendanceRecovery != null)
			{
				try
				{
					attendanceRecovery.checkpoint(attendanceTracker.report(),
												  System.currentTimeMillis());
				}
				catch (java.io.IOException ignored)
				{
				}
			}
			attendanceTracker.clear();
			attendanceSaved = false;
			showAttendanceStatus("The previous account's attendance was kept "
								 + "in its private local outbox for later automatic upload.");
			return;
		}
		if (!attendanceTracker.active() || attendanceTracker.paused() || local == null ||
			client.getGameState() != GameState.LOGGED_IN ||
			!session.eligibleFor(report.host, session.generation()))
		{
			return;
		}
		net.runelite.api.coords.WorldPoint origin = local.getWorldLocation();
		if (origin == null)
			return;
		Set<String> nearby = new HashSet<>();
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player == null || player.getName() == null || player.getWorldLocation() == null)
				continue;
			net.runelite.api.coords.WorldPoint point = player.getWorldLocation();
			if (origin.getPlane() != point.getPlane() ||
				origin.distanceTo(point) > HolyEmpireAttendanceTracker.RADIUS_TILES)
				continue;
			nearby.add(player.getName());
		}
		nearby.add(local.getName());
		attendanceTracker.observe(nearby, System.currentTimeMillis());
		if (++attendanceUiTicks >= 15)
		{
			attendanceUiTicks = 0;
			HolyEmpireAttendanceTracker.Report checkpoint = attendanceTracker.report();
			checkpointAttendanceOnWorker(checkpoint);
			if (panel != null)
				panel.displayAttendance(checkpoint, attendanceFeedback, attendanceSaved);
		}
	}

	private String rsvpChapter()
	{
		// Discord's verified session is chapter-scoped by the server.
		// On multi-chapter accounts it need not match the active native CC.
		ChapterEventsClient.MemberSession verified = chapterEventMemberSession;
		if (verified != null && verified.valid(verified.chapter))
		{
			return verified.chapter;
		}
		return session.nativeChapter();
	}

	private boolean chapterRsvpAuthorized()
	{
		String chapter = rsvpChapter();
		ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		return session.isEligible() && chapter != null && !chapter.isEmpty() && member != null &&
			member.valid(chapter);
	}

	private void submitChapterRsvp(String eventId, String response)
	{
		if (scheduler == null || scheduler.isShutdown() || !chapterRsvpAuthorized())
		{
			if (panel != null)
			{
				panel.rsvpResult("Connect Discord before saving an RSVP.", () -> true);
			}
			return;
		}
		final String account = session.account();
		final long generation = session.generation();
		final String chapter = rsvpChapter();
		final ChapterEventsClient.MemberSession member = chapterEventMemberSession;
		java.util.function.BooleanSupplier current =
			() -> session.eligibleFor(account, generation) && chapter.equals(rsvpChapter());
		scheduler.execute(() -> {
			try
			{
				ChapterEventsClient.rsvp(eventId, response, chapter, member, httpClient);
				if (current.getAsBoolean())
				{
					if (panel != null)
						panel.rsvpResult("Your RSVP was saved.", current);
					loadPrivateChapterEvents();
				}
			}
			catch (Exception ex)
			{
				if (panel != null)
				{
					panel.rsvpResult(
						"RSVP could not be saved. Check your Discord connection and retry.",
						current);
				}
			}
		});
	}

	private void redeemChapterEventCode(String code)
	{
		if (scheduler == null || scheduler.isShutdown() || !session.isEligible())
		{
			return;
		}
		final String account = session.account();
		final String chapter = rsvpChapter();
		final long generation = session.generation();
		if (chapter == null || chapter.isEmpty())
		{
			return;
		}
		scheduler.execute(() -> {
			java.util.function.BooleanSupplier current =
				() -> session.eligibleFor(account, generation) && chapter.equals(rsvpChapter());
			try
			{
				ChapterEventsClient.MemberSession verified =
					ChapterEventsClient.redeem(code, chapter, httpClient);
				if (current.getAsBoolean() && panel != null)
				{
					chapterEventMemberSession = verified;
					panel.chapterEventsConnected(current);
				}
			}
			catch (Exception ex)
			{
				if (panel != null)
				{
					panel.failChapterEvents(
						"Chapter verification failed; check the one-time code and "
							+ "Discord chapter role.",
						current);
				}
			}
		});
	}

	private void loadPrivateChapterEvents()
	{
		if (scheduler == null || scheduler.isShutdown() || !session.isEligible())
		{
			if (panel != null)
			{
				panel.failChapterEvents(
					"Chapter events unavailable until your Holy Empire roster session is ready.",
					() -> true);
			}
			return;
		}
		final String account = session.account();
		final String chapterScope = eventViewerChapter();
		final long generation = session.generation();
		java.util.function.BooleanSupplier current = ()
			-> session.eligibleFor(account, generation) &&
				   chapterScope.equals(eventViewerChapter());
		scheduler.execute(() -> {
			try
			{
				ChapterEventsClient.MemberSession member = chapterEventMemberSession;
				String nativeScope = rsvpChapter();
				List<DiscordEventsClient.Event> events;
				if (member != null && nativeScope != null && member.valid(nativeScope))
				{
					// Private production chapter events are derived from the redeemed
					// Discord session, not an unverified RuneScape name.
					events = ChapterEventsClient.read(nativeScope, member, httpClient);
				}
				else
				{
					if (panel != null)
						panel.failChapterEvents("Verify Discord to view private chapter events.",
												current);
					return;
				}
				if (current.getAsBoolean())
				{
					eventReminders.publish(account, false, events);
					checkEventReminderTimes();
				}
				if (panel != null)
				{
					panel.displayChapterEvents(events, current);
					refreshAttendanceAfterEventLoad(current, member);
				}
			}
			catch (Exception error)
			{
				if (panel != null)
				{
					panel.failChapterEvents(error.getMessage() != null &&
													error.getMessage().contains("HTTP 404")
												? "Chapter event sync is not yet enabled on the "
													  + "production API (HTTP 404)."
												: "Synced chapter events unavailable. Try Refresh.",
											current);
				}
			}
		});
	}

	private boolean officerStillEligible(String account, long generation, String chapter)
	{
		return officerAuthorized && session.eligibleFor(account, generation) &&
			chapter.equals(session.nativeChapter());
	}

	private void enrollmentMessage(String message, java.util.function.Consumer<String> feedback)
	{
		if (feedback != null)
		{
			feedback.accept(message);
		}
	}

	private void requestEnrollment(String account, long generation,
								   java.util.function.Consumer<String> feedback)
	{
		String chapter = session.nativeChapter();
		if (!officerStillEligible(account, generation, chapter))
		{
			return;
		}
		scheduler.execute(() -> {
			try
			{
				String url = enrollmentClient.request(account, chapter);
				javax.swing.SwingUtilities.invokeLater(() -> {
					if (!officerStillEligible(account, generation, chapter))
					{
						return;
					}
					net.runelite.client.util.LinkBrowser.browse(url);
					enrollmentMessage(
						"Complete Discord sign-in in your browser, then\n"
							+ "send the displayed one-time code to a trusted officer\n"
							+ "in an in-game private message. Return here to check status.",
						feedback);
				});
			}
			catch (Exception ex)
			{
				enrollmentMessage(ex.getMessage(), feedback);
			}
		});
	}

	private void checkEnrollment(String account, long generation,
								 java.util.function.Consumer<String> feedback)
	{
		String chapter = session.nativeChapter();
		if (!officerStillEligible(account, generation, chapter))
		{
			return;
		}
		scheduler.execute(() -> {
			try
			{
				String result = enrollmentClient.check(
					account, chapter, () -> officerStillEligible(account, generation, chapter));
				enrollmentMessage(result, feedback);
			}
			catch (Exception ex)
			{
				enrollmentMessage(ex.getMessage(), feedback);
			}
		});
	}

	private void reviewEnrollment(String account, long generation, java.awt.Component owner,
								  java.util.function.Consumer<String> feedback)
	{
		String chapter = session.nativeChapter();
		if (!officerStillEligible(account, generation, chapter))
		{
			enrollmentMessage(
				"Only approved Owners and Deputy Owners may review or approve officer requests.",
				feedback);
			return;
		}
		scheduler.execute(() -> {
			try
			{
				com.google.gson.JsonArray queue =
					enrollmentClient.list("*", officerSecrets.read(account));
				javax.swing.SwingUtilities.invokeLater(() -> {
					if (!officerStillEligible(account, generation, chapter))
					{
						return;
					}
					if (queue.size() == 0)
					{
						enrollmentMessage(
							"There are no pending verifications across Holy Empire chapters.",
							feedback);
						return;
					}
					java.util.LinkedHashMap<String, com.google.gson.JsonObject> options =
						new java.util.LinkedHashMap<>();
					for (com.google.gson.JsonElement entry : queue)
					{
						com.google.gson.JsonObject request = entry.getAsJsonObject();
						String rsn = request.get("rsn").getAsString();
						String targetChapter = request.get("chapter").getAsString();
						String discordName = request.has("discord_username") &&
													 !request.get("discord_username").isJsonNull()
												 ? request.get("discord_username").getAsString()
												 : "Discord linked";
						String key = targetChapter + " • " + rsn + " • " + discordName;
						if (options.containsKey(key))
						{
							key += " — " + request.get("id").getAsString();
						}
						options.put(key, request);
					}
					String selected = (String)javax.swing.JOptionPane.showInputDialog(
						owner, "Select the officer whose RuneScape identity you will verify.",
						"Review Admin Requests", javax.swing.JOptionPane.QUESTION_MESSAGE, null,
						options.keySet().toArray(new String[0]),
						options.keySet().iterator().next());
					if (selected == null)
					{
						return;
					}
					com.google.gson.JsonObject selectedRequest = options.get(selected);
					String rsn = selectedRequest.get("rsn").getAsString();
					String targetChapter = selectedRequest.get("chapter").getAsString();
					String code = javax.swing.JOptionPane.showInputDialog(
						owner, "Confirm a PRIVATE IN-GAME MESSAGE from " + rsn + " in " +
								   targetChapter +
								   ".\\nEnter the code from your private Discord notification, "
								   + "or from the applicant if notifications are unavailable.");
					if (code == null)
					{
						return;
					}
					String role = (String)javax.swing.JOptionPane.showInputDialog(
						owner, "Independently confirm this officer's current chapter rank.",
						"Verify Chapter Rank", javax.swing.JOptionPane.QUESTION_MESSAGE, null,
						new String[] {"Deputy Owner", "Owner"}, "Deputy Owner");
					if (role == null)
					{
						return;
					}
					String confirmation = "I personally verified an in-game private message from " +
										  rsn + "\nand confirmed their current " + role +
										  " rank in " + targetChapter + ".\n"
										  + "Authorize this officer's Admin Credential Key?";
					int confirmed = javax.swing.JOptionPane.showConfirmDialog(
						owner, confirmation, "Confirm Officer Identity",
						javax.swing.JOptionPane.YES_NO_OPTION,
						javax.swing.JOptionPane.WARNING_MESSAGE);
					if (confirmed != javax.swing.JOptionPane.YES_OPTION)
					{
						return;
					}
					String rank = "Owner".equals(role) ? "owner" : "deputy_owner";
					scheduler.execute(() -> {
						try
						{
							if (!officerStillEligible(account, generation, chapter))
							{
								return;
							}
							enrollmentMessage(enrollmentClient.approve(
												  selectedRequest.get("id").getAsString(), rsn,
												  code.trim(), rank, officerSecrets.read(account)),
											  feedback);
						}
						catch (Exception ex)
						{
							enrollmentMessage(ex.getMessage(), feedback);
						}
					});
				});
			}
			catch (Exception ex)
			{
				enrollmentMessage(ex.getMessage(), feedback);
			}
		});
	}

	@Override protected void shutDown()
	{
		if (attendanceRecovery != null)
		{
			try
			{
				HolyEmpireAttendanceTracker.Report pending = attendanceTracker.report();
				if (pending != null && (pending.running || !attendanceSaved))
					attendanceRecovery.checkpoint(pending, System.currentTimeMillis());
			}
			catch (java.io.IOException ignored)
			{
			}
		}
		if (attendanceRecoveryWorker != null)
		{
			attendanceRecoveryWorker.shutdownNow();
			attendanceRecoveryWorker = null;
		}
		if (inlineHighlightSettings != null)
		{
			inlineHighlightSettings.stop();
			inlineHighlightSettings = null;
		}
		if (panel != null)
		{
			panel.closeEventImageLoader();
		}
		if (inGameBanSyncButton != null)
		{
			inGameBanSyncButton.clear(client);
		}
		inGameBanSyncButton = null;
		overlayManager.remove(overlay);
		overlayManager.remove(namesOverlay);
		overlayManager.remove(applicantOverlay);
		overlayManager.remove(houseAdvertisementOverlay);
		if (button != null)
		{
			toolbar.removeNavigation(button);
		}
		if (scheduler != null)
		{
			scheduler.shutdownNow();
		}
		if (chapterImageWorker != null)
		{
			chapterImageWorker.shutdownNow();
		}
		HolyEmpireAssets.useRemoteChapterImages(null);
		stopDiscordVerification();
		if (communicationsWorker != null)
		{
			communicationsWorker.shutdownNow();
			communicationsWorker = null;
		}
		if (achievementWorker != null)
		{
			achievementWorker.shutdownNow();
			achievementWorker = null;
		}
		achievementCachePath = null;
		achievementCacheLoaded = false;
		communicationsFailures = 0;
		communicationsRetryAfter = 0;
		liveAdminCapabilities = java.util.Collections.emptySet();
		liveAdminSessionToken = "";
		previousCommsSnapshot = null;
		previousCommsToken = "";
		chapterEventMemberSession = null;
		discordRestoreRetryAt = 0;
		session.observe("", "");
		eventReminders.clearFeeds();
		officerAuthorized = false;
		if (panel != null)
		{
			panel.setOfficerSettingsEligible(false);
			panel.setAdminEligible(false);
			panel.setBroadcastEligible(false);
			panel.setAttendanceEligible(false);
			panel.setGlobalBanSearchEligible(false);
			panel.stopPrivateCacheTimer();
		}
		customRankIconIds.clear();
		renderedRankIconIndexes.clear();
		priorMenuTagLabels.clear();
		recruitmentAlertTimes.clear();
		applicantBypass.clear();
		vexillumRefreshTrigger.reset();
		pendingVexillumBanRefresh = false;
		if (panel != null)
		{
			panel.showMessage("Holy Empire disconnected.");
		}
	}

	private String currentAccount()
	{
		return session.account();
	}

	@Subscribe public void onConfigChanged(ConfigChanged event)
	{
		if (panel != null && "holyEmpireFederation".equals(event.getGroup()) &&
			"receiveEmergencyBroadcasts".equals(event.getKey()))
			panel.setEmergencyTickerEnabled(config.receiveEmergencyBroadcasts());
		if ("holyEmpireFederation".equals(event.getGroup()) &&
			"showAttendanceArchiveHelp".equals(event.getKey()) &&
			config.showAttendanceArchiveHelp())
			configManager.unsetConfiguration("holyEmpireFederation", "attendanceArchiveHelpShown");
		if (panel != null && "holyEmpireFederation".equals(event.getGroup()) &&
			"eventHistoryDays".equals(event.getKey()))
		{
			panel.setEventHistoryDays(config.eventHistoryDays());
		}
	}

	@Subscribe public void onGameTick(GameTick event)
	{
		Player local = client.getLocalPlayer();
		String name = client.getGameState() == GameState.LOGGED_IN && local != null
						  ? WomDirectoryClient.normalize(local.getName())
						  : "";
		String nativeChapter =
			name.isEmpty() ? "" : InGameClanVerifier.chapterFor(client.getClanSettings(), name);
		// The native clan settings may be briefly null during a world hop.
		if (!name.isEmpty() && name.equals(session.account()) && nativeChapter.isEmpty() &&
			client.getClanSettings() == null)
			nativeChapter = session.nativeChapter();
		if (session.observe(name, nativeChapter))
		{
			banResyncReminder.reset();
			if (!name.isEmpty())
			{
				communicationsInbox.onLogin();
				if ((newUiLogin || !name.equals(lastUiCharacter)) && panel != null)
				{
					if (!name.equals(lastUiCharacter))
						panel.clearTargetedBroadcasts();
					panel.beginLoginSession();
				}
				lastUiCharacter = name;
				newUiLogin = false;
			}
			discordRestoreRetryAt = 0;
			stopDiscordVerification();
			chapterEventMemberSession = null;
			if (panel != null)
			{
				panel.setChapterEventsScope("");
			}
			applicantBypass.clear();
			final long observedGeneration = session.generation();
			if (panel != null)
			{
				panel.showMessage("Checking Holy Empire membership...",
								  () -> session.generation() == observedGeneration);
			}
			if (scheduler != null && !scheduler.isShutdown() && !name.isEmpty())
			{
				scheduler.execute(this::sync);
			}
		}
		if (client.getGameState() == GameState.LOGGED_IN && chapterEventMemberSession != null &&
			!name.isEmpty() && !nativeChapter.isEmpty() && client.getClanSettings() != null)
		{
			net.runelite.api.clan.ClanRank observed = FederationInGameBanSyncButton.ownRank(client);
			if (observed != null)
			{
				int ordinal = observed.getRank();
				if (!name.equals(lastVerifiedRankAccount) || verifiedClanRank == null)
				{
					lastVerifiedRankAccount = name;
					verifiedClanRank = ordinal;
				}
				else if (verifiedClanRank != ordinal)
				{
					verifiedClanRank = ordinal;
					// A confirmed in-game rank change ends the persisted
					// Discord authorization for this RuneScape character.
					chapterEventMemberSession = null;
					liveAdminCapabilities = java.util.Collections.emptySet();
					liveAdminSessionToken = "";
					final String changedAccount = name;
					if (rememberedDiscord != null)
					{
						try
						{
							rememberedDiscord.clear(changedAccount);
						}
						catch (java.io.IOException ignored)
						{
						}
					}
					if (panel != null)
						panel.chapterEventsExpired(
							() -> session.isEligible() && changedAccount.equals(session.account()));
				}
			}
		}
		// Officer controls are presentational only. Protected uploads still require
		// a chapter-scoped server token; no client-provided rank authorizes a write.
		boolean permitted = session.isEligible() && !session.nativeChapter().isEmpty() &&
							session.rosterFor(name)
								.getOrDefault(name, java.util.Collections.emptyList())
								.contains(session.nativeChapter()) &&
							FederationInGameBanSyncButton.isOwnerOrDeputy(client);
		boolean wasOfficer = officerAuthorized;
		officerAuthorized = permitted;
		if (!permitted)
		{
			applicantBypass.clear();
		}
		if (panel != null)
		{
			// Only *confirmed* Discord admin/owner roles unlock admin panels;
			// native in-game rank and the server credential remain independent gates.
			panel.setMembershipConfirmed(client.getGameState() == GameState.LOGGED_IN &&
										 session.isEligible());
			panel.setAdminEligible(announcementSessionReady());
			panel.setBroadcastEligible(broadcastSessionReady());
			panel.setAttendanceEligible(attendanceAuthority());
			panel.setOfficerSettingsEligible(permitted && liveAdminCapability("bans.sync"));
			panel.setGlobalBanSearchEligible(globalBanSearchSessionReady());
			if (wasOfficer && !permitted)
			{
				panel.clearOfficerEvidence();
			}
		}
		observeAttendance(name, local);
		queuePendingVexillumRefresh();
	}

	/** Only equipment transitions can request a Vexillum-triggered refresh. */
	@Subscribe public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!vexillumRefreshTrigger.onContainerChanged(
				event.getContainerId(), event.getItemContainer(), System.currentTimeMillis()))
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		pendingVexillumBanRefresh = true;
		queuePendingVexillumRefresh();
	}

	private void queuePendingVexillumRefresh()
	{
		if (!pendingVexillumBanRefresh || client.getGameState() != GameState.LOGGED_IN ||
			session.account().isEmpty() || scheduler == null || scheduler.isShutdown())
		{
			return;
		}
		pendingVexillumBanRefresh = false;
		if (officerAuthorized && panel != null)
		{
			panel.refreshPrivateBansOnVexillumEquip();
		}
		// Uses the public CDN only; does not rescan WOM groups or game ban menus.
		scheduler.execute(() -> sync(true));
	}

	@Subscribe public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.HOPPING)
			worldHopInProgress = true;
		if (event.getGameState() == GameState.LOGIN_SCREEN && !worldHopInProgress)
			newUiLogin = true;
		if (event.getGameState() == GameState.LOGGED_IN)
			worldHopInProgress = false;
		else if (panel != null && !worldHopInProgress && event.getGameState() != GameState.LOADING)
			panel.setMembershipConfirmed(false);
		if (event.getGameState() == GameState.STARTING)
		{
			renderedRankIconIndexes.clear();
		} // RuneLite recalculates registered icon positions.
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			// A world hop temporarily enters LOADING/HOPPING: do not revoke
			// verified Discord state or increment the character generation.
			if (worldHopInProgress || event.getGameState() == GameState.LOADING ||
				event.getGameState() == GameState.HOPPING)
				return;
			applicantBypass.clear();
			chapterEventMemberSession = null;
			banResyncReminder.reset();
			session.observe("", "");
			eventReminders.clearFeeds();
			officerAuthorized = false;
			vexillumRefreshTrigger.reset();
			pendingVexillumBanRefresh = false;
			if (panel != null)
			{
				panel.setOfficerSettingsEligible(false);
			}
			final long loggedOutGeneration = session.generation();
			if (panel != null)
			{
				panel.showMessage("Log in with an eligible Holy Empire account.",
								  () -> session.generation() == loggedOutGeneration);
			}
		}
	}

	/** In-game chat stays visible even with desktop focus notifications suppressed. */
	private void promptBanSync(String description)
	{
		long now=System.currentTimeMillis();
		if (now-lastNativeModerationReminderAt<15000) return;
		lastNativeModerationReminderAt=now;
		String message="Holy Empire: "+description+
			" Open Clans > Settings > Bans and choose Sync Ban List.";
		notifier.notify(message);
		chatMessageManager.queue(net.runelite.client.chat.QueuedMessage.builder()
			.type(net.runelite.api.ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage("<col=ff0000>"+message+"</col>").build());
	}

	@Subscribe public void onChatMessage(net.runelite.api.events.ChatMessage event)
	{
		if ((event.getType()!=net.runelite.api.ChatMessageType.GAMEMESSAGE &&
			event.getType()!=net.runelite.api.ChatMessageType.ENGINE) ||
			client.getGameState()!=GameState.LOGGED_IN || !session.isEligible() ||
			!FederationInGameBanSyncButton.isOwnerOrDeputy(client))
			return;
		if (FederationBanResyncReminder.confirmedClanModeration(event.getMessage()))
			promptBanSync("Clan moderation confirmed.");
	}

	/** Warn on the native OSRS clan Recruit action; never block an in-game action. */
	@Subscribe public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !session.isEligible())
		{
			return;
		}
		// RuneLite has no definitive ban/expulsion-completed event.
		// Prompt after an officer chooses an action, not after an ordinary departure.
		// Jagex's Bans page uses "Add" and "Remove", rather than the
		// player-menu "Ban" label. Warn for both workflows; sync remains a
		// deliberate click by an authorized owner or deputy.
		if ((FederationBanResyncReminder.isModerationAction(event.getMenuOption()) ||
			FederationBanResyncReminder.isNativeBanEditAction(
				event.getMenuOption(), event.getWidgetId())) &&
			// Own-rank check supports the native clan channel when the
			// ClanSettings object is temporarily unavailable.
			FederationInGameBanSyncButton.isOwnerOrDeputy(client))
		{
			promptBanSync("After changing the clan ban list, re-sync the shared list.");
		}
		if (FederationApplicantGuard.isApplicantAcceptance(event.getMenuOption(), event.getParam1(),
														   event.getWidgetId()))
		{
			// IDs only, never log the applicant's name, ban matches, or credentials.
			org.slf4j.LoggerFactory.getLogger(FederationPlugin.class)
				.info("Holy Empire native Applicants acceptance event observed");
			warnApplicantAcceptance(event);
			return;
		}
		MenuEntry menu = event.getMenuEntry();
		Player player = menu == null ? null : menu.getPlayer();
		String name = FederationRecruitmentGuard.recruitmentTarget(
			event.getMenuOption(), player == null ? null : player.getName(), event.getMenuTarget());
		if (name == null)
		{
			return;
		}
		final String account = session.account();
		final long generation = session.generation();
		boolean approved =
			officerAuthorized && FederationInGameBanSyncButton.isOwnerOrDeputy(client);
		FederationBanDirectoryClient.Snapshot snapshot =
			approved && panel != null ? panel.freshRecruitmentBanDirectory() : null;
		List<String> chapters = FederationRecruitmentGuard.matchingChapters(snapshot, name);
		boolean nonmemberBanned = session.publicBanWarningMatch(account, name);
		String message =
			!chapters.isEmpty() ? FederationRecruitmentGuard.recruitPopupWarningText(name, chapters)
			: nonmemberBanned	? FederationPublicBanWarnings.recruitPopupWarningText(name)
								: FederationRecruitmentGuard.warningText(
									  name, chapters,
									  snapshot != null || session.publicBanWarningAvailable(account));
		if (message == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		String key = WomDirectoryClient.normalize(name);
		Long previous = recruitmentAlertTimes.get(key);
		if (previous != null && now - previous < 5000)
		{
			return;
		}
		recruitmentAlertTimes.put(key, now);
		if (recruitmentAlertTimes.size() > 50)
		{
			recruitmentAlertTimes.clear();
		}
		final boolean includesPrivateData = !chapters.isEmpty();
		javax.swing.SwingUtilities.invokeLater(() -> {
			if (!session.eligibleFor(account, generation) || panel == null)
			{
				return;
			}
			if (includesPrivateData &&
				(!officerAuthorized || panel.freshRecruitmentBanDirectory() == null))
			{
				return;
			}
			HolyEmpireDialogs.showMessageDialog(panel, message, "Holy Empire Recruitment Warning",
												javax.swing.JOptionPane.WARNING_MESSAGE);
		});
		if (approved && snapshot == null && panel != null)
		{
			panel.preloadBanDirectory();
		}
	}

	/**
	 * Read only selected applicant details; never scan all list rows as the
	 * identity of the selected applicant.
	 */
	private static void selectedApplicantText(Widget widget, List<String> fields, int depth)
	{
		if (widget == null || widget.isHidden() || depth > 7 || fields.size() > 250)
		{
			return;
		}
		if (widget.getText() != null && !widget.getText().isBlank())
		{
			fields.add(widget.getText());
		}
		Widget[] children = widget.getChildren();
		if (children != null)
		{
			for (Widget child : children)
			{
				selectedApplicantText(child, fields, depth + 1);
			}
		}
	}

	/**
	 * Selection data is used for verification; a whole applicant list would
	 * cause false accusations against unrelated selected applicants.
	 */
	List<String> selectedApplicantFields()
	{
		List<String> selected = new java.util.ArrayList<>();
		try
		{
			selectedApplicantText(client.getWidget(InterfaceID.ClansApplicants.INFO_CONTAINER),
								  selected, 0);
			selectedApplicantText(client.getWidget(InterfaceID.ClansApplicants.INFO), selected, 0);
		}
		catch (RuntimeException unavailable)
		{
			// Native applicants widget can disappear while client updates.
		}
		return selected;
	}

	FederationBanDirectoryClient.Snapshot officerDirectoryForApplicants()
	{
		return client.getGameState() == GameState.LOGGED_IN && officerAuthorized &&
				session.isEligible() && panel != null
			? panel.freshRecruitmentBanDirectory()
			: null;
	}

	private void warnApplicantAcceptance(MenuOptionClicked event)
	{
		// Match the tested Recruit behavior: a verified officer sees a warning only
		// for a confirmed name in the private ban directory, never for an empty
		// Applicants screen or an unrelated applicant.
		final String account = session.account();
		final long generation = session.generation();
		final boolean authorized =
			officerAuthorized && FederationInGameBanSyncButton.isOwnerOrDeputy(client);
		final FederationBanDirectoryClient.Snapshot bans =
			authorized && panel != null ? panel.freshRecruitmentBanDirectory() : null;
		final List<String> selected = selectedApplicantFields();
		final String menuName = FederationApplicantGuard.extractExactName(event.getMenuTarget());
		String identified = FederationApplicantGuard.selectedApplicantName(selected);
		if (identified == null && !FederationApplicantGuard.hasSelectedIdentityEvidence(selected))
		{
			identified = menuName;
		}

		if (bans == null)
		{
			if (authorized && panel != null)
			{
				panel.preloadBanDirectory();
			}
			// Same unavailable-status advice as ordinary Recruit, but only if a
			// real applicant name was available. No popup for an empty screen.
			final String message = session.publicBanWarningMatch(account, identified)
									   ? FederationPublicBanWarnings.publicWarningText(identified)
									   : FederationRecruitmentGuard.warningText(
											 identified, java.util.Collections.emptyList(),
											 session.publicBanWarningAvailable(account));
			if (message != null)
			{
				javax.swing.SwingUtilities.invokeLater(() -> {
					if (panel != null && session.eligibleFor(account, generation))
					{
						HolyEmpireDialogs.showMessageDialog(
							panel, message, "Holy Empire Recruitment Warning",
							javax.swing.JOptionPane.WARNING_MESSAGE);
					}
				});
			}
			return;
		}

		if (!FederationApplicantGuard.hasSelectedIdentityEvidence(selected) && menuName != null)
		{
			selected.add(menuName);
		}
		final FederationApplicantGuard.Status status =
			FederationApplicantGuard.status(bans, selected);
		if (!FederationApplicantGuard.shouldCancelAccept(status))
		{
			return;
		}
		final String confirmed = FederationApplicantGuard.matchedBannedApplicant(bans, selected);
		if (confirmed == null)
		{
			return;
		}
		final List<String> chapters = FederationRecruitmentGuard.matchingChapters(bans, confirmed);
		if (chapters.isEmpty())
		{
			return;
		}
		// Consume an explicit, single-use approval ONLY for this still-banned
		// applicant in the same verified account, chapter and login generation.
		final String chapter = session.nativeChapter();
		if (applicantBypass.consume(account, generation, chapter, confirmed,
									System.currentTimeMillis()))
		{
			return; // second click: user chose Allow Once after seeing the warning
		}
		event.consume(); // first click: cancel native Accept before game action
		final String message =
			FederationRecruitmentGuard.warningText(confirmed, chapters, true) +
			"\n\nThe invitation was NOT sent.\n"
			+ "You may allow this one banned applicant after reviewing the warning.\n"
			+ "Choose Allow Once, then click Accept again within 15 seconds.";
		javax.swing.SwingUtilities.invokeLater(() -> {
			if (panel == null || !session.eligibleFor(account, generation) || !officerAuthorized ||
				!chapter.equals(session.nativeChapter()))
			{
				return;
			}
			// A stale or revoked officer snapshot must never arm an override.
			FederationBanDirectoryClient.Snapshot current = panel.freshRecruitmentBanDirectory();
			if (current == null ||
				FederationRecruitmentGuard.matchingChapters(current, confirmed).isEmpty())
			{
				return;
			}
			Object[] options = {"Keep Blocked", "Allow Once"};
			int choice = HolyEmpireDialogs.showOptionDialog(
				panel, message, "Holy Empire — Banned Applicant",
				javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE,
				null, options, options[0]);
			if (choice == 1 && session.eligibleFor(account, generation) && officerAuthorized &&
				chapter.equals(session.nativeChapter()) &&
				panel.freshRecruitmentBanDirectory() != null)
			{
				applicantBypass.arm(account, generation, chapter, confirmed,
									System.currentTimeMillis());
			}
		});
	}

	private void sync()
	{
		sync(false);
	}

	private void sync(boolean force)
	{
		final String before = session.account();
		final long generation = session.generation();
		if (before.isEmpty())
		{
			if (session.invalidate(before, generation) && panel != null)
			{
				panel.showMessage("Log in with an eligible Holy Empire account.",
								  () -> session.generation() == generation);
			}
			return;
		}
		try
		{
			WomDirectoryClient.Snapshot snapshot =
				force ? WomDirectoryClient.fetchFreshForVexillum(httpClient)
					  : WomDirectoryClient.fetch(httpClient);
			if (!session.publish(before, generation, snapshot))
			{
				return;
			}
			if (session.isEligible() && chapterImages != null && chapterImageWorker != null &&
				!chapterImageWorker.isShutdown())
			{
				// Only validated image SHA-256 digests in this complete roster
				// can trigger an HTTP request. Never block RuneLite's EDT.
				chapterImages.useDirectory(snapshot.directory);
				chapterImageWorker.execute(() -> {
					boolean loaded = chapterImages.loadMissing();
					if (loaded && panel != null)
					{
						panel.refreshChapterImages(() -> session.eligibleFor(before, generation));
					}
				});
			}
			if (panel != null)
			{
				if (session.isEligible())
				{
					panel.setChapterEventsScope(eventViewerChapter());
					panel.update(session.rosterFor(before), session.chaptersFor(before),
								 WomDirectoryClient.isUsingStaleCache(),
								 ()
									 -> session.eligibleFor(before, generation),
								 session.publicBanHashesFor(before));
				}
				else
				{
					panel.showNotVerified(
						() -> session.generation() == generation && !session.isEligible());
				}
			}
			if (session.eligibleFor(before, generation))
			{
				// Login must prime the Events and attendance feeds without a
				// manual Refresh click. Do not repeat the public fetch on each
				// game tick or periodic WOM cache synchronization.
				boolean firstEventLoad =
					eventLoginRefresh.claim(before, generation, true);
				restoreRememberedDiscord();
				// Successful Discord restoration queues the personalized
				// fetch itself. Otherwise preload the public Empire calendar;
				// the personal checkmark is fetched after Discord reconnects.
				ChapterEventsClient.MemberSession member = chapterEventMemberSession;
				if (firstEventLoad && (member == null || !member.valid(member.chapter)))
					loadDiscordEvents();
			}
		}
		catch (Exception error)
		{
			org.slf4j.LoggerFactory.getLogger(FederationPlugin.class)
				.warn("WOM unavailable; no complete cached roster: {}", error.toString());
			if (session.invalidate(before, generation) && panel != null)
			{
				panel.showMessage("Roster temporarily unavailable. Will retry automatically.",
								  () -> session.generation() == generation);
			}
		}
	}

	Map<String, List<String>> members()
	{
		return session.rosterFor(currentAccount());
	}

	/** One local client-thread registration per distinct rank. No network lookups. */
	private int menuRankIcon(String playerName)
	{
		if (client.getModIcons() == null)
		{
			return -1;
		}
		WomDirectoryClient.ChapterMember member = session.rankFor(playerName);
		if (member == null)
		{
			return -1;
		}
		Icon icon = HolyEmpireRankDisplay.icon(member);
		if (!(icon instanceof ImageIcon))
		{
			return -1;
		}
		String key = HolyEmpireRankDisplay.isEmperor(member.name)
						 ? "emperor"
						 : WomDirectoryClient.normalize(member.rank);
		try
		{
			Integer id = customRankIconIds.get(key);
			if (id == null)
			{
				BufferedImage image = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = image.createGraphics();
				g.drawImage(((ImageIcon)icon).getImage(), 0, 0, 13, 13, null);
				g.dispose();
				id = chatIconManager.registerChatIcon(image);
				customRankIconIds.put(key, id);
			}
			int index = chatIconManager.chatIconIndex(id);
			if (index >= 0)
			{
				renderedRankIconIndexes.add(index);
			}
			return index;
		}
		catch (RuntimeException failure)
		{
			return -1; // Never interrupt menu construction for a missing icon.
		}
	}

	/** Fetch one short-lived, in-memory combined directory for the current officer. */
	private void loadCombinedBans(boolean force)
	{
		if (!officerAuthorized || !session.isEligible() || scheduler == null ||
			scheduler.isShutdown())
		{
			return;
		}
		final String account = session.account();
		final long generation = session.generation();
		final String chapter = session.nativeChapter();
		final java.util.function.BooleanSupplier authorized = ()
			-> officerAuthorized && session.eligibleFor(account, generation) &&
				   chapter.equals(session.nativeChapter());
		if (!authorized.getAsBoolean())
		{
			return;
		}
		final String endpoint = config.banSyncEndpoint();
		final String token = officerSecrets.read(account);
		if (!FederationBanUploadClient.acceptedEndpoint(endpoint) || token.isEmpty())
		{
			if (panel != null)
			{
				panel.failBanDirectory("An approved RuneScape-bound officer credential is required "
										   + "for the private ban list.",
									   authorized);
			}
			return;
		}
		scheduler.execute(() -> {
			if (!authorized.getAsBoolean())
			{
				return;
			}
			try
			{
				FederationBanDirectoryClient.Snapshot bans =
					FederationBanDirectoryClient.fetch(chapter, endpoint, token, httpClient);
				if (panel != null)
				{
					panel.displayBanDirectory(bans, authorized);
				}
			}
			catch (Exception error)
			{
				// Only report a generic network status; never log officer credentials or ban names.
				if (panel != null)
				{
					panel.failBanDirectory("Could not retrieve synchronized bans. Check the "
											   + "private connection and officer access.",
										   authorized);
				}
			}
		});
	}

	/** Pure session gate for a queued officer upload; never trust old logins. */
	static boolean currentOfficerCapture(FederationSession state, String account, long generation,
										 FederationBanRegistry.Snapshot snapshot, boolean approved)
	{
		return approved && state.eligibleFor(account, generation) &&
			state.nativeChapter().equals(snapshot.chapter);
	}

	/** User clicked the in-game ban-list Sync button; capture on game thread, POST off-thread. */
	private void syncBanListFromGame()
	{
		if (banUploadInProgress)
		{
			return;
		}
		final String activeAccount = session.account();
		final long activeGeneration = session.generation();
		if (!session.isEligible() || activeAccount.isEmpty())
		{
			banSyncMessage(
				"Verified Holy Empire membership is required before synchronizing bans.");
			return;
		}
		// Both native Owner/Deputy rank and matching verified membership are required.
		if (!FederationInGameBanSyncButton.isOwnerOrDeputy(client))
		{
			banSyncMessage(
				"Only verified Deputy Owners and Owners of the active chapter can sync bans.");
			return;
		}
		final FederationBanRegistry.Snapshot snapshot;
		try
		{
			snapshot = FederationBanWidgetCapture.read(client, java.time.Instant.now());
		}
		catch (RuntimeException ex)
		{
			banSyncMessage("Capture failed: " + ex.getMessage());
			return;
		}
		// A capture must belong to the very chapter verified for this login.
		if (!currentOfficerCapture(session, activeAccount, activeGeneration, snapshot,
								   officerAuthorized))
		{
			banSyncMessage(
				"Ban capture was canceled because the verified chapter or account changed.");
			return;
		}
		// The local preview is temporary; the private server independently authorizes uploads.
		if (panel != null)
		{
			panel.banRegistry().put(snapshot);
		}
		String endpoint = config.banSyncEndpoint();
		String token = officerSecrets.read(activeAccount);
		if (!FederationBanUploadClient.acceptedEndpoint(endpoint) || token == null ||
			token.trim().length() < 24)
		{
			banSyncMessage(
				"Captured " + snapshot.names.size() + " banned account(s) from " +
				snapshot.chapter + " locally.\n\nCentral ban synchronization is not available yet; "
				+ "nothing was uploaded. The local capture is temporary and will be lost "
				+ "when RuneLite restarts.");
			return;
		}
		if (scheduler == null || scheduler.isShutdown())
		{
			banSyncMessage("Ban list captured locally. Upload is temporarily unavailable; "
						   + "nothing was uploaded.");
			return;
		}
		banUploadInProgress = true;
		scheduler.execute(() -> {
			String result;
			FederationBanUploadClient.Receipt receipt = null;
			try
			{
				if (!currentOfficerCapture(session, activeAccount, activeGeneration, snapshot,
										   officerAuthorized))
				{
					return;
				}
				receipt = FederationBanUploadClient.sendReceipt(
					snapshot, endpoint, token.trim(), httpClient);
				result = receipt.message;
			}
			catch (Exception ex)
			{
				// No credential, URL or banned account names in logs.
				result = "Ban sync failed: " + ex.getMessage();
			}
			finally
			{
				banUploadInProgress = false;
			}
			FederationBanUploadClient.Receipt successful = receipt;
			if (successful != null && currentOfficerCapture(session,activeAccount,
				activeGeneration,snapshot,officerAuthorized))
			{
				// Acknowledged full replacement is authoritative; never leave
				// stale removed names in the private directory until a reason
				// happens to be saved. Establish a new reminder baseline too.
				banResyncReminder.synced(snapshot);
				loadCombinedBans(true);
			}
			if (successful == null || snapshot.names.isEmpty())
			{
				banSyncMessage(result, () -> currentOfficerCapture(session,activeAccount,
					activeGeneration,snapshot,officerAuthorized));
				return;
			}
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (panel != null && currentOfficerCapture(session,activeAccount,
					activeGeneration,snapshot,officerAuthorized))
					promptForBanReason(snapshot,successful,endpoint,activeAccount,
						activeGeneration);
			});
		});
	}

	private volatile boolean banReasonClearInProgress;

	private boolean canClearSavedBanReason(FederationBanDirectoryClient.Entry entry)
	{
		return entry != null && officerAuthorized && session.isEligible() &&
			!banReasonClearInProgress && liveAdminCapability("bans.sync") &&
			entry.chapter.equals(session.nativeChapter()) &&
			(!entry.category.isEmpty() || !entry.reason.isEmpty());
	}

	/** Clear an existing annotation only; the underlying clan ban is untouched. */
	private void clearSavedBanReason(FederationBanDirectoryClient.Entry entry, int revision)
	{
		if (panel == null || !canClearSavedBanReason(entry) ||
			scheduler == null || scheduler.isShutdown())
			return;
		String account = session.account();
		long generation = session.generation();
		String endpoint = config.banSyncEndpoint();
		String bearer = officerSecrets.read(account);
		if (!FederationBanUploadClient.acceptedEndpoint(endpoint) ||
			bearer == null || bearer.length() < 24)
		{
			banSyncMessage("Approved officer access is needed to clear a saved reason.");
			return;
		}
		String warning = "Clear the saved category and reason for " + entry.name +
			" in " + entry.chapter + "?\n\n" +
			"The player remains banned. This will not alter the native CC ban list." +
			(entry.discordPosted
				? "\n\nA previously published Servo-Skull notice in #ban-list " +
				  "will remain unchanged."
				: "\n\nNothing will be posted to Discord.");
		if (HolyEmpireDialogs.showConfirmDialog(panel, warning,
			"Clear Saved Ban Reason", javax.swing.JOptionPane.YES_NO_OPTION,
			javax.swing.JOptionPane.WARNING_MESSAGE) != javax.swing.JOptionPane.YES_OPTION)
			return;
		if (!canClearSavedBanReason(entry) || !session.eligibleFor(account, generation))
			return;
		banReasonClearInProgress = true;
		scheduler.execute(() -> {
			String result;
			boolean cleared = false;
			try
			{
				if (!session.eligibleFor(account, generation) ||
					!entry.chapter.equals(session.nativeChapter()) ||
					!officerAuthorized)
					return;
				FederationBanReasonClient.Result response =
					FederationBanReasonClient.clear(entry.chapter, entry.name, revision,
						endpoint, bearer, httpClient);
				result = response.message;
				cleared = true;
			}
			catch (Exception error)
			{
				result = "Ban reason was not cleared: " + error.getMessage();
			}
			finally
			{
				banReasonClearInProgress = false;
			}
			final String message = result;
			final boolean succeeded = cleared;
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (panel == null || !session.eligibleFor(account, generation) ||
					!entry.chapter.equals(session.nativeChapter()) || !officerAuthorized)
					return;
				if (succeeded) loadCombinedBans(true);
				HolyEmpireDialogs.showMessageDialog(panel, message,
					"Clear Saved Ban Reason", succeeded
						? javax.swing.JOptionPane.INFORMATION_MESSAGE
						: javax.swing.JOptionPane.WARNING_MESSAGE);
			});
		});
	}

	/** Post-sync annotations never occur without an explicit officer action. */
	private void promptForBanReason(FederationBanRegistry.Snapshot snapshot,
			FederationBanUploadClient.Receipt receipt, String endpoint,
			String account, long generation)
	{
		if (panel == null || scheduler == null || scheduler.isShutdown() ||
			!currentOfficerCapture(session,account,generation,snapshot,officerAuthorized))
			return;
		FederationBanReasonDialog.Choice selected =
			FederationBanReasonDialog.ask(panel,snapshot,receipt);
		if (selected == null)
			return;
		String credential = officerSecrets.read(account);
		if (credential == null || credential.length() < 24)
		{
			banSyncMessage("Officer authorization expired. Reason was not saved.");
			return;
		}
		scheduler.execute(() -> {
			String outcome;
			boolean saved = false;
			try
			{
				if (!currentOfficerCapture(session,account,generation,snapshot,officerAuthorized))
					return;
				FederationBanReasonClient.Result response =
					FederationBanReasonClient.send(snapshot.chapter,selected.rsn,
						selected.category,selected.reason,receipt.revision,
						selected.publish,endpoint,credential,httpClient);
				outcome = response.message;
				saved = true;
			}
			catch (Exception error)
			{
				outcome = "Ban reason was not saved: " + error.getMessage();
			}
			final String message = outcome;
			final boolean reasonSaved = saved;
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (panel == null || !currentOfficerCapture(session,account,generation,
					snapshot,officerAuthorized))
					return;
				HolyEmpireDialogs.showMessageDialog(panel,message,"Holy Empire Ban Reason",
						reasonSaved ? javax.swing.JOptionPane.INFORMATION_MESSAGE
							: javax.swing.JOptionPane.WARNING_MESSAGE);
				if (reasonSaved)
					loadCombinedBans(true);
				if (selected.another)
					promptForBanReason(snapshot,receipt,endpoint,account,generation);
			});
		});
	}

	private void banSyncMessage(String message)
	{
		banSyncMessage(message, () -> true);
	}

	private void banSyncMessage(String message, java.util.function.BooleanSupplier stillCurrent)
	{
		javax.swing.SwingUtilities.invokeLater(() -> {
			if (panel != null && stillCurrent.getAsBoolean())
			{
				HolyEmpireDialogs.showMessageDialog(panel, message, "Holy Empire Ban Sync",
													javax.swing.JOptionPane.INFORMATION_MESSAGE);
			}
		});
	}

	/**
	 * Chatbox menus do not carry a world Player reference. Decorate native
	 * chat-player actions when each entry is created so the label is visible
	 * even when the right-click menu opens on the same client tick.
	 */
	@Subscribe(priority = -1.0f) public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !session.isEligible() ||
			HolyEmpirePvpPolicy.suppressPlayerIndicators(client))
		{
			return;
		}
		decorateChatMenuEntry(event.getMenuEntry(), members());
	}

	/** Affiliation as a separate row; keep native social targets untouched. */
	@Subscribe(priority = -1.0f) public void onMenuOpened(MenuOpened event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || !session.isEligible() ||
			HolyEmpirePvpPolicy.suppressPlayerIndicators(client))
		{
			return;
		}
		Map<String, List<String>> lookup = members();
		if (lookup.isEmpty())
		{
			return;
		}
		for (MenuEntry entry : event.getMenuEntries())
		{
			if (!FederationChatMenuTags.isChatPlayerIdentityAction(
				entry.getType(), entry.getParam1(), entry.getOption()))
			{
				continue;
			}
			String key = FederationChatMenuTags.rosterName(
				entry.getTarget(), lookup, priorMenuTagLabels);
			if (key == null)
			{
				continue;
			}
			List<String> chapters = lookup.get(key);
			boolean same = SameChapterVisibility.isSameChapter(lookup.get(currentAccount()), chapters);
			boolean showTag = SameChapterVisibility.showMenuTag(
				config.showClanMenu(), same, config.showSameChapterMenu());
			String rankSetting = configManager.getConfiguration("playerindicators", "showClanChatRanks");
			boolean nativeRank = FederationMenuRankIcons.hasNativeRankIcon(
				entry.getTarget(), renderedRankIconIndexes);
			boolean showRank = SameChapterVisibility.showRankIcon(
				config.showClanRankIcons(), same, config.showSameChapterRankIcons(),
				!"false".equalsIgnoreCase(rankSetting), nativeRank);
			if (!showTag && !showRank)
			{
				return;
			}
			String target = chapterMenuTag(entry.getTarget(), chapters, showTag);
			target = FederationMenuRankIcons.apply(target,
				showRank ? menuRankIcon(key) : -1, renderedRankIconIndexes, showRank);
			client.getMenu().createMenuEntry(-1)
				.setOption("Holy Empire")
				.setTarget(target)
				.setType(net.runelite.api.MenuAction.RUNELITE)
				.setDeprioritized(true)
				.onClick(clicked -> { });
			return;
		}
	}

	private String chapterMenuTag(String existing, List<String> chapters, boolean enabled)
	{
		String canonical = String.join(", ", chapters).replaceAll("[<>\\r\\n]", "");
		String display = ChapterTagNames.affiliation(config, chapters);
		Set<String> known =
			priorMenuTagLabels.computeIfAbsent(canonical, ignored -> new HashSet<>());
		if (known.size() >= 64)
		{
			known.clear();
		}
		known.add(canonical);
		known.add(display);
		return FederationMenuTags.apply(existing, chapters,
										ChapterIndicatorColors.menuColor(config, chapters), enabled,
										display, known);
	}

	private void decorateChatMenuEntry(MenuEntry entry, Map<String, List<String>> lookup)
	{
		if (entry == null || lookup == null || lookup.isEmpty() ||
			!FederationChatMenuTags.isChatPlayerMenu(entry.getType(), entry.getParam1(),
													 entry.getOption()))
		{
			return;
		}
		String old = entry.getTarget();
		String key = FederationChatMenuTags.rosterName(old, lookup, priorMenuTagLabels);
		if (key == null)
		{
			return;
		}
		List<String> chapters = lookup.get(key);
		boolean same = SameChapterVisibility.isSameChapter(lookup.get(currentAccount()), chapters);
		boolean showTag = SameChapterVisibility.showMenuTag(config.showClanMenu(), same,
															config.showSameChapterMenu());
		String rankOption = configManager.getConfiguration("playerindicators", "showClanChatRanks");
		boolean nativeRank =
			FederationMenuRankIcons.hasNativeRankIcon(old, renderedRankIconIndexes);
		boolean showRank = SameChapterVisibility.showRankIcon(
			config.showClanRankIcons(), same, config.showSameChapterRankIcons(),
			!"false".equalsIgnoreCase(rankOption), nativeRank);

		String updated = chapterMenuTag(old, chapters, showTag);
		updated = FederationMenuRankIcons.apply(updated, showRank ? menuRankIcon(key) : -1,
												renderedRankIconIndexes, showRank);
		if (updated != null && !updated.equals(old))
		{
			entry.setTarget(updated);
		}
	}

	/** Observe only the native visible Bans page, without uploading the ban list. */
	private void checkForNewNativeBans()
	{
		long now = System.currentTimeMillis();
		if (now < nextBanResyncCheckMillis)
			return;
		nextBanResyncCheckMillis = now + 2500;
		if (client.getGameState() != GameState.LOGGED_IN ||
			!session.isEligible() ||
			!FederationInGameBanSyncButton.isOwnerOrDeputy(client))
		{
			banResyncReminder.reset();
			return;
		}
		net.runelite.api.widgets.Widget frame =
			client.getWidget(FederationInGameBanSyncButton.FRAME);
		if (frame == null || frame.isHidden())
			return;
		try
		{
			FederationBanRegistry.Snapshot observed =
				FederationBanWidgetCapture.read(client, java.time.Instant.now());
			if (banResyncReminder.observe(observed))
				promptBanSync("A new clan ban was detected.");
		}
		catch (RuntimeException unavailable)
		{
			// An empty/rebuilding widget is not evidence of a new ban.
		}
	}

	// Player Indicators decorates menus at default priority. Apply the CC tag last.
	@Subscribe(priority = -1.0f) public void onClientTick(ClientTick e)
	{
		if (inGameBanSyncButton != null)
		{
			inGameBanSyncButton.update(client);
		}
		checkForNewNativeBans();
		if (client.isMenuOpen())
		{
			return;
		}
		// A matching banned player gets a visible warning in the native Recruit
		// menu itself, before the click. Private details never reach non-officers.
		FederationBanDirectoryClient.Snapshot recruitBans =
			officerAuthorized && panel != null ? panel.freshRecruitmentBanDirectory() : null;
		for (MenuEntry menu : client.getMenu().getMenuEntries())
		{
			Player candidate = menu.getPlayer();
			String name = FederationRecruitmentGuard.recruitmentTarget(
				menu.getOption(), candidate == null ? null : candidate.getName(), menu.getTarget());
			if (name == null)
			{
				continue;
			}
			boolean privateMatch =
				!FederationRecruitmentGuard.matchingChapters(recruitBans, name).isEmpty();
			if (!privateMatch && !session.publicBanWarningMatch(currentAccount(), name))
			{
				continue;
			}
			String target = menu.getTarget();
			if (target != null && !target.contains("[BAN-LIST WARNING]"))
			{
				menu.setTarget(target + " <col=ff5555>[BAN-LIST WARNING]</col>");
			}
		}
		// Suppress custom chapter tags and rank markers in all PvP areas.
		// Recruitment ban warnings above are unaffected.
		if (HolyEmpirePvpPolicy.suppressPlayerIndicators(client))
		{
			return;
		}
		Map<String, List<String>> lookup = members();
		if (lookup.isEmpty())
		{
			return;
		}
		// Honor the core Player Indicators option without changing its state.
		// A missing override means its standard default, enabled.
		String playerIndicatorsRankSetting =
			configManager.getConfiguration("playerindicators", "showClanChatRanks");
		boolean playerIndicatorsRanksEnabled =
			!"false".equalsIgnoreCase(playerIndicatorsRankSetting);
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			// Existing world-player menus carry Player objects, chat menus do
			// not. Keep the original world-menu path and cover chat separately.
			decorateChatMenuEntry(entry, lookup);
			Player p = entry.getPlayer();
			if (p == null || p.getName() == null)
			{
				continue;
			}
			List<String> clans = lookup.get(WomDirectoryClient.normalize(p.getName()));
			if (clans == null || clans.isEmpty())
			{
				continue;
			}
			boolean same = SameChapterVisibility.isSameChapter(lookup.get(currentAccount()), clans);
			boolean showTag = SameChapterVisibility.showMenuTag(config.showClanMenu(), same,
																config.showSameChapterMenu());
			String old = entry.getTarget();
			// Player Indicators runs first. Never replace its existing clan-chat
			// (or friends-chat) icon with a Holy Empire same-chapter rank icon.
			boolean nativeRank =
				FederationMenuRankIcons.hasNativeRankIcon(old, renderedRankIconIndexes);
			boolean showRank = SameChapterVisibility.showRankIcon(
				config.showClanRankIcons(), same, config.showSameChapterRankIcons(),
				playerIndicatorsRanksEnabled, nativeRank);
			String updated = chapterMenuTag(old, clans, showTag);
			updated =
				FederationMenuRankIcons.apply(updated, showRank ? menuRankIcon(p.getName()) : -1,
											  renderedRankIconIndexes, showRank);
			if (updated != null && !updated.equals(old))
			{
				entry.setTarget(updated);
			}
		}
	}
}
