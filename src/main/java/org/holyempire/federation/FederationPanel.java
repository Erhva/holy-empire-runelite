/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.PluginPanel;

/** Holy Empire home screen and one-level-deep chapter rosters. No nested scroll panes. */
public class FederationPanel extends PluginPanel
{
	private EventImageLoader eventImageLoader;

	void setEventImageLoader(EventImageLoader loader)
	{
		eventImageLoader = loader;
	}

	void closeEventImageLoader()
	{
		if (eventImageLoader != null)
		{
			eventImageLoader.close();
			eventImageLoader = null;
		}
	}

	private final FederationBanRegistry banRegistry = new FederationBanRegistry();
	private Set<String> publicBanHashes = Collections.emptySet();
	private Runnable officerSettingsOpener;
	private java.util.function.Consumer<Boolean> banDirectoryLoader;
	private volatile boolean officerSettingsRequested;
	private final JToggleButton rosterTab = HolyEmpireTheme.tab("Roster", false);
	private final JToggleButton eventsTab = HolyEmpireTheme.tab("Events", false);
	private final JToggleButton banListTab = HolyEmpireTheme.tab("Ban List", true);
	private final JToggleButton announcementsTab = HolyEmpireTheme.tab("Announcements", false);
	private final JToggleButton adminTab = HolyEmpireTheme.tab("Admin Controls", true);
	private final EmpireEmergencyTicker emergencyTicker = new EmpireEmergencyTicker();
	// Membership is proven by the active game's roster sync, not rank previews.
	private volatile boolean membershipRequested;
	private boolean confirmedMember;
	private EmpireCommunicationsClient.Snapshot communications;
	private WomAchievementsClient.Snapshot womAchievements;
	private final WomAchievementRotator womRotator = new WomAchievementRotator();
	private String womAchievementsError;
	private boolean showingAnnouncements;
	private boolean showingAdmin;

	private enum AdminSubtab
	{
		ATTENDANCE,
		ANNOUNCEMENTS,
		BROADCASTS
	}

	// Session-local only: never persist a Test View or sensitive role choice.
	private AdminSubtab lastAdminSubtab = AdminSubtab.ATTENDANCE;
	private boolean showingAdminAnnouncements;
	private final JToggleButton adminBroadcastsTab = HolyEmpireTheme.tab("Broadcasts", true);
	private final JToggleButton adminAnnouncementsTab = HolyEmpireTheme.tab("Announcements", true);
	private final JToggleButton adminAttendanceTab = HolyEmpireTheme.tab("Attendance", true);
	private final JPanel adminSections = new JPanel();
	private boolean showingAdminAttendance = true;
	private boolean attendanceEligible;
	private volatile boolean attendanceEligibilityRequested;
	private HolyEmpireAttendanceTracker.Report attendanceReport;
	private String attendanceArchiveStatus;
	private String attendanceCopyStatus;
	private String attendanceTitleError;
	private boolean attendanceSaved;
	private Point attendanceArchiveScrollPosition;
	private int attendanceNumber;
	private HolyEmpireAttendanceCumulative.Previous attendancePrior;
	private DiscordEventsClient.Event trackedAttendanceEvent;
	@FunctionalInterface
	interface AttendanceHistoryAction {
		void copy(String eventId);
	}
	private AttendanceHistoryAction attendanceHistoryAction;
	private Runnable attendanceHistoryLoader;
	private java.util.Map<String, Integer> eventAttendanceNumbers =
		java.util.Collections.emptyMap();
	private final JLabel announcementCounter = SwingTextSafety.plainLabel("0 / 350");
	private final JTextField attendanceTitle = new JTextField();
	private final JSpinner attendanceMinimumMinutes = new JSpinner(attendanceMinimumModel());
	private boolean attendanceMinimumInitialized;
	@FunctionalInterface
	interface AttendanceStartAction {
		void start(String id, String scope, String chapter, String title);
	}
	private AttendanceStartAction attendanceStart;
	private Runnable attendanceStop;
	private Runnable attendanceSave;
	private Runnable attendancePause;

	void setAttendanceHistoryAction(AttendanceHistoryAction action)
	{
		attendanceHistoryAction = action;
	}

	void setAttendanceHistoryLoader(Runnable loader)
	{
		attendanceHistoryLoader = loader;
	}

	void displayAttendanceHistoryNumbers(java.util.Map<String, Integer> numbers)
	{
		final java.util.Map<String, Integer> safe = new java.util.LinkedHashMap<>(numbers);
		SwingUtilities.invokeLater(() -> {
			eventAttendanceNumbers = java.util.Collections.unmodifiableMap(safe);
			if (showingEvents && showingEventHistory)
				refresh();
		});
	}

	void displayAttendancePrior(String reportId, HolyEmpireAttendanceCumulative.Previous prior)
	{
		if (prior == null) return;
		SwingUtilities.invokeLater(() -> {
			if (attendanceReport != null && attendanceReport.running &&
				attendanceReport.reportId.equals(reportId) &&
				attendanceReport.eventId.equals(prior.eventId))
			{
				attendancePrior = prior;
				attendanceNumber = prior.number;
				if (showingEvents || (showingAdmin && showingAdminAttendance))
					refresh();
			}
		});
	}

	void setAttendanceNumber(String reportId, int number)
	{
		SwingUtilities.invokeLater(() -> {
			if (attendanceReport != null && attendanceReport.reportId.equals(reportId))
			{
				attendanceNumber = number;
				if (showingEvents || (showingAdmin && showingAdminAttendance))
					refresh();
			}
		});
	}

	long attendanceEventEndMillis(String id)
	{
		if (trackedAttendanceEvent != null && id.equals(trackedAttendanceEvent.id) &&
			trackedAttendanceEvent.end != null)
			return trackedAttendanceEvent.end.toEpochMilli();
		for (DiscordEventsClient.Event event :
			 eventFeed == null ? java.util.Collections.<DiscordEventsClient.Event>emptyList()
							   : eventFeed.events)
			if (event.id.equals(id) && event.end != null)
				return event.end.toEpochMilli();
		for (DiscordEventsClient.Event event : chapterEventFeed)
			if (event.id.equals(id) && event.end != null)
				return event.end.toEpochMilli();
		return 0L;
	}

	static String discordAttendanceCodeBlock(String text)
	{
		if (text == null || text.isEmpty())
			return "";
		String table = text.replace("```", "[code fence]").stripTrailing();
		return "```\n" + table + "\n```";
	}

	private void copyAttendanceList(String report)
	{
		copyAttendanceText(discordAttendanceCodeBlock(report));
	}

	private void copyAttendanceText(String text)
	{
		if (text == null || text.isEmpty())
			return;
		try
		{
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new java.awt.datatransfer.StringSelection(text), null);
			attendanceCopyStatus = "Attendance copied to clipboard.";
		}
		catch (java.awt.HeadlessException | IllegalStateException error)
		{
			attendanceCopyStatus = "Clipboard unavailable in this desktop session.";
		}
		if (showingEvents || (showingAdmin && showingAdminAttendance))
			refresh();
	}

	void copyArchivedAttendanceText(String content)
	{
		SwingUtilities.invokeLater(() -> copyAttendanceList(content));
	}

	void setAttendanceHistoryStatus(String message)
	{
		SwingUtilities.invokeLater(() -> {
			attendanceArchiveStatus = message;
			if (showingEvents)
				refresh();
		});
	}

	void setAttendanceActions(AttendanceStartAction start, Runnable stop, Runnable save)
	{
		setAttendanceActions(start, stop, save, null);
	}

	void setAttendanceActions(AttendanceStartAction start, Runnable stop, Runnable save,
							  Runnable pause)
	{
		attendanceStart = start;
		attendanceStop = stop;
		attendanceSave = save;
		attendancePause = pause;
	}

	private void rememberAttendanceEventScrollPosition()
	{
		if (!showingEvents) return;
		JViewport currentViewport = (JViewport)SwingUtilities.getAncestorOfClass(
			JViewport.class, this);
		if (currentViewport != null)
			attendanceArchiveScrollPosition = new Point(currentViewport.getViewPosition());
	}

	void attendanceArchivedAndReset(boolean returnToEvents)
	{
		SwingUtilities.invokeLater(() -> {
			attendanceReport = null;
			attendanceSaved = false;
			attendanceNumber = 0;
			attendancePrior = null;
			trackedAttendanceEvent = null;
			attendanceArchiveStatus = null;
			attendanceCopyStatus = null;
			attendanceTitleError = null;
			attendanceMinimumMinutes.setValue(10);
			if (returnToEvents)
			{
				showingEvents = true;
				showingEventHistory = false;
				showingAdmin = false;
				showingAnnouncements = false;
			}
			refresh();
			final Point keep = attendanceArchiveScrollPosition;
			attendanceArchiveScrollPosition = null;
			if (keep != null && returnToEvents)
			{
				// Restore after the new event cards have completed layout.
				SwingUtilities.invokeLater(() -> {
					JViewport viewport = (JViewport)SwingUtilities.getAncestorOfClass(
						JViewport.class, this);
					if (viewport == null || !showingEvents)
						return;
					viewport.revalidate();
					int maxY = Math.max(0,
						viewport.getViewSize().height - viewport.getExtentSize().height);
					viewport.setViewPosition(new Point(keep.x,
						Math.max(0, Math.min(keep.y, maxY))));
				});
			}
		});
	}

	void displayAttendance(HolyEmpireAttendanceTracker.Report report, String message, boolean saved)
	{
		SwingUtilities.invokeLater(() -> {
			if (report == null || attendanceReport == null ||
				!report.reportId.equals(attendanceReport.reportId))
			{
				attendanceNumber = 0;
				attendancePrior = null;
			}
			boolean newManual =
				report != null && report.running && "manual".equals(report.scope) &&
				(attendanceReport == null || !report.reportId.equals(attendanceReport.reportId));
			attendanceReport = report;
			attendanceArchiveStatus = message;
			attendanceSaved = saved;
			if (newManual)
			{
				attendanceTitle.setText("");
				attendanceTitleError = null;
				attendanceCopyStatus = null;
			}
			if ((showingAdmin && showingAdminAttendance) || showingEvents)
				refresh();
		});
	}

	void setAttendanceEligible(boolean permitted)
	{
		if (attendanceEligibilityRequested == permitted)
			return;
		attendanceEligibilityRequested = permitted;
		SwingUtilities.invokeLater(() -> {
			if (attendanceEligible == attendanceEligibilityRequested)
				return;
			attendanceEligible = attendanceEligibilityRequested;
			if (!attendanceEligible && showingAdminAttendance)
			{
				showingAdminAttendance = false;
				if (!viewingEmpireOwner())
				{
					showingAdmin = false;
					showingAnnouncements = true;
				}
			}
			if (verified)
				refresh();
		});
	}

	private volatile boolean adminEligibleRequested;
	private boolean adminEligible;
	private String communicationsError;
	private String publishStatus;
	@FunctionalInterface
	interface PublishAction {
		void send(String kind, String title, String body);
	}
	private PublishAction publishAction;
	@FunctionalInterface
	interface ScopedBroadcastAction {
		void send(String kind, String title, String body, String audience);
	}
	private ScopedBroadcastAction scopedBroadcastAction;
	private final JComboBox<String> broadcastAudience =
		new JComboBox<>(new String[] {"Chapter Command", "All Admins", "Empire-Wide"});
	@FunctionalInterface
	interface PublishWithLinkAction {
		void send(String kind, String title, String body, String url);
	}
	@FunctionalInterface
	interface ManageAnnouncementAction {
		void send(String action, long id, String title, String body, String url);
	}
	private PublishWithLinkAction publishWithLinkAction;
	private ManageAnnouncementAction manageAnnouncementAction;
	private java.util.function.Consumer<String> emergencyAction;
	// Separate announcement powers from broadcast and emergency powers.
	private java.util.function.BooleanSupplier ownerSessionReady;
	private java.util.function.BooleanSupplier broadcastSessionReady;
	private Runnable ownerDiscordConnect;
	private final JTextField broadcastTitle = new JTextField("Holy Empire");
	private final JTextArea broadcastBody = new JTextArea(3, 22);
	private final JLabel broadcastCounter = new JLabel("0 / 200 characters");
	private final JTextField announcementTitle = new JTextField();
	private final JTextArea announcementBody = new JTextArea(5, 22);
	private final JTextField announcementLink = new JTextField();
	private final JTextField emergencyTitle = new JTextField("Emergency Announcement");
	private final JTextArea emergencyBody = new JTextArea(3, 22);
	private final JLabel emergencyCounter = new JLabel("0 / 200 characters");
	private boolean showingEvents;
	private boolean showingEventHistory;
	private Runnable eventsLoader;
	private DiscordEventsClient.Snapshot eventFeed;
	private final java.util.Map<String, DiscordEventsClient.Event> attendanceKnownEvents =
		new java.util.LinkedHashMap<>();
	private volatile java.util.List<String> attendanceStatusIds = java.util.Collections.emptyList();
	private java.util
		.Map<String, HolyEmpireAttendanceEventStatusClient.State> attendanceServerStates =
		java.util.Collections.emptyMap();
	private long attendanceStatusesFetchedAt;

	java.util.List<String> attendanceVisibleEventIds()
	{
		return attendanceStatusIds;
	}

	void displayAttendanceServerStates(
		java.util.Map<String, HolyEmpireAttendanceEventStatusClient.State> states)
	{
		final java.util.Map<String, HolyEmpireAttendanceEventStatusClient.State> safe =
			new java.util.LinkedHashMap<>(states);
		SwingUtilities.invokeLater(() -> {
			attendanceServerStates = java.util.Collections.unmodifiableMap(safe);
			attendanceStatusesFetchedAt = System.currentTimeMillis();
			java.util.List<DiscordEventsClient.Event> discovered = new java.util.ArrayList<>();
			for (java.util.Map.Entry<String, HolyEmpireAttendanceEventStatusClient.State> item :
				 safe.entrySet())
			{
				HolyEmpireAttendanceEventStatusClient.State state = item.getValue();
				DiscordEventsClient.Event previous = attendanceKnownEvents.get(item.getKey());
				if ((state.trackerActive() || state.endedAt > 0) && !state.scope.isEmpty() &&
					!state.title.isEmpty() && state.startsAt > 0 &&
					(previous == null || previous.end == null && state.endedAt > 0))
				{
					java.time.Instant started = java.time.Instant.ofEpochSecond(state.startsAt);
					long effective = state.endedAt > 0		  ? state.endedAt
									 : state.scheduledEnd > 0 ? state.scheduledEnd
															  : state.startsAt + 86400;
					discovered.add(new DiscordEventsClient.Event(
						state.scope, state.chapter, item.getKey(), state.title, "",
						state.trackerActive() ? "active" : "completed", state.location, started,
						java.time.Instant.ofEpochSecond(Math.max(effective, state.startsAt + 1)),
						state.url));
				}
			}
			if (!discovered.isEmpty())
				rememberEvents(discovered);
			if (showingEvents)
				refresh();
		});
	}

	private boolean trackingEvent(String id)
	{
		HolyEmpireAttendanceEventStatusClient.State state = attendanceServerStates.get(id);
		return (state != null && state.trackerActive() &&
				System.currentTimeMillis() - attendanceStatusesFetchedAt < 120_000L) ||
			(attendanceReport != null && attendanceReport.running &&
			 id.equals(attendanceReport.eventId));
	}

	private void rememberEvents(java.util.List<DiscordEventsClient.Event> events)
	{
		for (DiscordEventsClient.Event event : events)
		{
			attendanceKnownEvents.put(event.id, event);
		}
		while (attendanceKnownEvents.size() > 180)
		{
			String first = attendanceKnownEvents.keySet().iterator().next();
			attendanceKnownEvents.remove(first);
		}
		java.util.LinkedHashSet<String> ids =
			new java.util.LinkedHashSet<>(attendanceKnownEvents.keySet());
		java.util.List<String> recent = new java.util.ArrayList<>(ids);
		if (recent.size() > 80)
			recent = recent.subList(recent.size() - 80, recent.size());
		attendanceStatusIds =
			java.util.Collections.unmodifiableList(new java.util.ArrayList<>(recent));
	}

	private void supplementRememberedEvents(java.util.List<DiscordEventsClient.Event> entries)
	{
		java.util.Set<String> displayed = new java.util.HashSet<>();
		for (DiscordEventsClient.Event e : entries)
			displayed.add(e.id);
		java.time.Instant cutoff = java.time.Instant.now().minusSeconds(31L * 86400L);
		for (DiscordEventsClient.Event e : attendanceKnownEvents.values())
		{
			if (displayed.contains(e.id) || (e.end == null && !trackingEvent(e.id)) ||
				(e.end != null && e.end.isBefore(cutoff)))
				continue;
			if ("chapter".equals(e.scope) &&
				!(eventMemberConnected &&
				  ("*".equals(activeEventChapter) || activeEventChapter.equals(e.chapter))))
				continue;
			entries.add(e);
		}
	}

	private int eventHistoryDays = 30;
	private long eventFetchedAt;
	private long eventLastAttemptAt;
	private boolean eventsLoading;
	private String eventsError;
	private final Map<JLabel, DiscordEventsClient.Event> activeEventClocks = new LinkedHashMap<>();
	private final Map<String, CachedEventCard> eventCardCache = new LinkedHashMap<>();
	private final java.util.LinkedHashSet<String> collapsedEventIds = new java.util.LinkedHashSet<>();
	private java.util.function.Consumer<String> saveCollapsedEvents = value -> {};

	void setEventCollapsePersistence(String saved, java.util.function.Consumer<String> onSave)
	{
		collapsedEventIds.clear();
		if (saved != null)
			for (String entry : saved.split(","))
				if (entry.matches("[A-Za-z0-9:_-]{1,96}") && collapsedEventIds.size() < 200)
					collapsedEventIds.add(entry);
		saveCollapsedEvents = onSave == null ? value -> {} : onSave;
		eventCardCache.clear();
	}

	private void toggleCollapsedEvent(String eventKey)
	{
		if (!collapsedEventIds.remove(eventKey))
		{
			if (collapsedEventIds.size() >= 200)
				collapsedEventIds.remove(collapsedEventIds.iterator().next());
			collapsedEventIds.add(eventKey);
		}
		saveCollapsedEvents.accept(String.join(",", collapsedEventIds));
	}

	/** Measure wrapped event text at the available width without blank rows. */
	private static void fitEventText(JTextArea area, int width)
	{
		int usable = Math.max(140, width);
		area.setRows(0);
		area.setColumns(0);
		area.setPreferredSize(null);
		area.setSize(usable, 32767);
		int height = Math.max(area.getFontMetrics(area.getFont()).getHeight(),
			area.getPreferredSize().height);
		area.setPreferredSize(new Dimension(usable, height));
		area.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		area.revalidate();
	}

	private static void fitEventCardHeight(JPanel card)
	{
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			card.getPreferredSize().height));
		card.revalidate();
		if (card.getParent() != null)
			card.getParent().revalidate();
		card.repaint();
	}


	private static final class CachedEventCard
	{
		final JPanel panel;
		final JLabel time;
		final List<Object> signature;

		CachedEventCard(JPanel panel, JLabel time, List<Object> signature)
		{
			this.panel = panel;
			this.time = time;
			this.signature = signature;
		}
	}

	private static List<Object> eventSignature(DiscordEventsClient.Event event)
	{
		return Arrays.asList(event.scope, event.chapter, event.id, event.name, event.description,
							 event.status, event.location, event.start, event.end, event.url,
							 event.imageUrl, event.rsvpGoing, event.rsvpMaybe, event.rsvpDeclined,
							 event.myRsvp, event.interestedCount);
	}

	/**
	 * In-game Discord event interest status. The bell is purely informational:
	 * the adjacent Event Details / RSVP button remains the only RSVP action.
	 */
	private static JLabel empireEventBellStatus(String eventId, String myRsvp, int buttonHeight)
	{
		boolean interested = "going".equals(myRsvp);
		JLabel badge = SwingTextSafety.plainLabel("");
		badge.setName("holyEmpireEventDiscordBell:" + eventId);
		badge.setIcon(new EmpireEventBellIcon(interested));
		badge.setHorizontalAlignment(SwingConstants.CENTER);
		badge.setVerticalAlignment(SwingConstants.CENTER);
		badge.setOpaque(true);
		badge.setBackground(HolyEmpireTheme.STEEL);
		badge.setBorder(BorderFactory.createLineBorder(
			interested ? HolyEmpireTheme.RSVP_GOING_EDGE : HolyEmpireTheme.SILVER_EDGE,
			interested ? 2 : 1));
		badge.setPreferredSize(new Dimension(34, Math.max(28, buttonHeight)));
		badge.setToolTipText(interested ? "Discord: You marked Interested"
			: "Discord: Interested status not confirmed");
		badge.getAccessibleContext().setAccessibleName(
			interested ? "Discord event: Interested" : "Discord event: Interest not confirmed");
		return badge;
	}

	/** Resolution-independent outline of Discord's notification bell. */
	private static final class EmpireEventBellIcon implements Icon
	{
		private final Color color;

		private EmpireEventBellIcon(boolean interested)
		{
			color = interested ? HolyEmpireTheme.RSVP_GOING_EDGE : HolyEmpireTheme.MUTED;
		}

		@Override public int getIconWidth() { return 20; }
		@Override public int getIconHeight() { return 20; }

		@Override public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D)graphics.create();
			try
			{
				g.translate(x, y);
				g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
					java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(color);
				g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND,
					BasicStroke.JOIN_ROUND));
				java.awt.geom.Path2D bell = new java.awt.geom.Path2D.Double();
				bell.moveTo(4, 14);
				bell.curveTo(5.5, 12, 6, 11, 6, 8);
				bell.curveTo(6, 4.7, 7.5, 3.5, 10, 3.5);
				bell.curveTo(12.5, 3.5, 14, 4.7, 14, 8);
				bell.curveTo(14, 11, 14.5, 12, 16, 14);
				g.draw(bell);
				g.drawLine(3, 15, 17, 15);
				g.drawArc(8, 15, 4, 3, 180, 180);
			}
			finally { g.dispose(); }
		}
	}

	static String personalEventRsvpMark(String status)
	{
		if ("going".equals(status)) return "✓";
		if ("maybe".equals(status)) return "?";
		if ("declined".equals(status)) return "✕";
		return "";
	}

	private static void stylePersonalEventRsvp(JLabel badge, String status)
	{
		badge.setText(personalEventRsvpMark(status));
		badge.setForeground("going".equals(status) ? HolyEmpireTheme.RSVP_GOING_EDGE :
			"declined".equals(status) ? HolyEmpireTheme.RSVP_DECLINED_EDGE :
			HolyEmpireTheme.GOLD);
		badge.setToolTipText("going".equals(status) ? "Your RSVP: Going" :
			"maybe".equals(status) ? "Your RSVP: Maybe" :
			"declined".equals(status) ? "Your RSVP: Not Attending" : null);
	}

	private static boolean rsvpOnlyFeedChange(List<DiscordEventsClient.Event> before,
		List<DiscordEventsClient.Event> after)
	{
		if (before.size() != after.size()) return false;
		for (int i = 0; i < before.size(); i++)
			if (!eventSignature(before.get(i)).subList(0, 11)
				.equals(eventSignature(after.get(i)).subList(0, 11))) return false;
		return true;
	}

	private static <T extends JComponent> T findRsvpControl(JComponent parent,
		String name, Class<T> type)
	{
		if (name.equals(parent.getName()) && type.isInstance(parent)) return type.cast(parent);
		for (java.awt.Component child : parent.getComponents())
			if (child instanceof JComponent)
			{
				T found = findRsvpControl((JComponent)child, name, type);
				if (found != null) return found;
			}
		return null;
	}

	private boolean updateRsvpCardsWithoutReload(List<DiscordEventsClient.Event> events)
	{
		for (DiscordEventsClient.Event e : events)
		{
			CachedEventCard old = eventCardCache.get("next:chapter:" + e.id);
			// Finished events may remain in the feed without a visible card.
			if (old == null) continue;
			if (old.signature.size() < 16) return false;
			JLabel interested = findRsvpControl(old.panel,
				"holyEmpireEventInterested:" + e.id, JLabel.class);
			JButton button = findRsvpControl(old.panel,
				"holyEmpireEventRsvpOpen:" + e.id, JButton.class);
			if (interested == null || button == null) return false;
			interested.setText("Interested: " +
				(e.interestedCount >= 0 ? Integer.toString(e.interestedCount) : "—"));
			String status = e.myRsvp.isEmpty() ? "" :
				" · " + ("declined".equals(e.myRsvp) ? "Can't Go" :
				"maybe".equals(e.myRsvp) ? "Maybe" : "Going");
			String arrow = button.getText().endsWith("▲") ? " ▲" : " ▼";
			button.setText("RSVP" + status + arrow);
			button.setBorder(HolyEmpireTheme.button("RSVP", false).getBorder());
			if ("going".equals(e.myRsvp))
				HolyEmpireTheme.accentBorder(button, HolyEmpireTheme.RSVP_GOING_EDGE);
			else if ("declined".equals(e.myRsvp))
				HolyEmpireTheme.accentBorder(button, HolyEmpireTheme.RSVP_DECLINED_EDGE);
			JLabel badge = findRsvpControl(old.panel,
				"holyEmpireEventPersonalRsvp:" + e.id, JLabel.class);
			if (badge == null) return false;
			stylePersonalEventRsvp(badge, e.myRsvp);
			badge.setVisible(collapsedEventIds.contains("chapter:" + e.id) &&
				!badge.getText().isEmpty());
			JPanel choices = findRsvpControl(old.panel,
				"holyEmpireEventRsvpOptions:" + e.id, JPanel.class);
			if (choices != null)
				for (java.awt.Component child : choices.getComponents())
					child.setEnabled(true);
			List<Object> fresh = eventSignature(e);
			for (int j = 11; j < 16; j++) old.signature.set(j, fresh.get(j));
			old.panel.repaint();
		}
		return true;
	}

	private static boolean sameEvents(List<DiscordEventsClient.Event> a,
									  List<DiscordEventsClient.Event> b)
	{
		if (a.size() != b.size())
			return false;
		for (int i = 0; i < a.size(); i++)
			if (!eventSignature(a.get(i)).equals(eventSignature(b.get(i))))
				return false;
		return true;
	}

	private String activeEventChapter = "";
	private boolean eventMemberConnected;
	private final JTextField chapterEventCode = new JTextField();
	private boolean chapterEventsLoading;
	private long chapterEventsLastAttemptAt;
	private String chapterEventsError;
	private java.util.List<DiscordEventsClient.Event> chapterEventFeed = Collections.emptyList();
	private java.util.function.Consumer<String> chapterCodeRedeemer;
	private Runnable chapterEventsLoader;
	private java.util.function.BiConsumer<String, String> chapterRsvpHandler;
	private Runnable discordConnectAction;
	private java.util.function.BooleanSupplier chapterRsvpAuthorized;
	private String rsvpResultMessage;
	private final JPanel navigationTabs = new JPanel(new GridBagLayout());
	private final JButton discordVerifyButton = HolyEmpireTheme.button("Verify Discord", true);
	private java.util.function.BooleanSupplier discordSessionReady;
	private final JTextField banSearch = new JTextField();
	private java.util.function.BiConsumer<FederationBanDirectoryClient.Entry, Integer>
		banReasonClearAction;
	private java.util.function.Predicate<FederationBanDirectoryClient.Entry>
		banReasonClearAuthorized = entry -> false;
	private final JTextField banChapterSearch = new JTextField();
	private final JLabel banChapterTitle = SwingTextSafety.plainLabel("");
	private boolean showingBans = false;
	private String selectedBanChapter = null;
	private volatile FederationBanDirectoryClient.Snapshot banDirectory = null;
	private volatile long banDirectoryCachedAt = 0;
	private boolean banDirectoryLoading = false;
	private String banDirectoryError = null;
	private final javax.swing.Timer privateCacheTimer;
	private final javax.swing.Timer searchDebounceTimer =
		new javax.swing.Timer(175, e -> refresh());
	private Map<String, Integer> cachedBanCounts = Collections.emptyMap();
	private List<FederationBanDirectoryClient.Entry> sortedBanEntries = Collections.emptyList();
	private volatile boolean officerSettingsEligible;
	// Global search-only must not result in downloading private chapter lists.
	private volatile boolean globalBanSearchEligible;
	private String adminVerificationStatus;

	void showAdminVerificationStatus(String message,
									 java.util.function.BooleanSupplier stillAuthorized)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillAuthorized.getAsBoolean() || !officerSettingsEligible || !verified)
			{
				return;
			}
			adminVerificationStatus = message;
			refresh();
		});
	}

	void setOfficerSettingsOpener(Runnable opener)
	{
		officerSettingsOpener = opener;
	}

	void setAdminPublishing(PublishAction action, java.util.function.BooleanSupplier authorized,
							Runnable connect)
	{
		setAdminPublishing(action, authorized, authorized, connect);
	}

	void setAdminPublishing(PublishAction action,
							java.util.function.BooleanSupplier announcementsAuthorized,
							java.util.function.BooleanSupplier broadcastsAuthorized,
							Runnable connect)
	{
		publishAction = action;
		ownerSessionReady = announcementsAuthorized;
		broadcastSessionReady = broadcastsAuthorized;
		ownerDiscordConnect = connect;
	}

	void setBroadcastScopePublisher(ScopedBroadcastAction publisher)
	{
		scopedBroadcastAction = publisher;
	}

	void setAnnouncementActions(PublishWithLinkAction publish, ManageAnnouncementAction manage)
	{
		publishWithLinkAction = publish;
		manageAnnouncementAction = manage;
	}

	void setEmergencyActions(java.util.function.Consumer<String> handler)
	{
		emergencyAction = handler;
	}

	private boolean viewingMember()
	{
		return verified;
	}

	private boolean viewingOfficer()
	{
		return officerSettingsEligible || globalBanSearchEligible;
	}

	private boolean viewingEmpireOwner()
	{
		return adminEligible;
	}

	private boolean viewingAttendance()
	{
		return attendanceEligible;
	}

	private boolean viewingEventAttendance()
	{
		// Creators may take attendance subject to server-side chapter authorization.
		return verified && discordSessionReady != null && discordSessionReady.getAsBoolean();
	}

	private boolean viewingChapterBanLists()
	{
		return officerSettingsEligible;
	}

	private boolean viewingBroadcasts()
	{
		return broadcastEligible;
	}

	private volatile boolean broadcastEligible;
	private volatile boolean broadcastEligibleRequested;

	void setBroadcastEligible(boolean enabled)
	{
		broadcastEligibleRequested = enabled;
		SwingUtilities.invokeLater(() -> {
			if (broadcastEligible == broadcastEligibleRequested)
				return;
			broadcastEligible = broadcastEligibleRequested;
			if (!broadcastEligible && showingAdmin && !showingAdminAnnouncements &&
				!showingAdminAttendance)
				showingAdminAnnouncements = true;
			if (verified)
				refresh();
		});
	}

	void setAdminEligible(boolean enabled)
	{
		adminEligibleRequested = enabled;
		SwingUtilities.invokeLater(() -> {
			if (adminEligible == adminEligibleRequested)
				return;
			adminEligible = adminEligibleRequested;
			if (!adminEligible && showingAdmin && !attendanceEligible)
			{
				showingAdmin = false;
				showingAnnouncements = true;
				announcementsTab.setSelected(true);
			}
			if (!adminEligible)
				publishStatus = null;
			refresh();
		});
	}

	/** Purge old chapter/admin-only notices before a different RSN takes over. */
	void clearTargetedBroadcasts()
	{
		SwingUtilities.invokeLater(() -> {
			if (communications == null)
				return;
			List<EmpireCommunicationsClient.Notice> publicNotes = new ArrayList<>();
			for (EmpireCommunicationsClient.Notice notice : communications.broadcasts)
				if ("empire".equals(notice.scope))
					publicNotes.add(notice);
			communications = new EmpireCommunicationsClient.Snapshot(
				communications.generatedAt, communications.sequence, communications.announcements,
				publicNotes, communications.emergency);
			if (showingAnnouncements)
				refresh();
		});
	}

	void setMembershipConfirmed(boolean isEligible)
	{
		if (membershipRequested == isEligible)
			return;
		membershipRequested = isEligible;
		SwingUtilities.invokeLater(() -> {
			if (membershipRequested != isEligible)
				return;
			confirmedMember = isEligible;
			updateEmergencyTickerVisibility();
		});
	}

	private boolean emergencyTickerEnabled = true;

	void setEmergencyTickerEnabled(boolean enabled)
	{
		SwingUtilities.invokeLater(() -> {
			if (emergencyTickerEnabled == enabled) return;
			emergencyTickerEnabled = enabled;
			updateEmergencyTickerVisibility();
		});
	}

	private void updateEmergencyTickerVisibility()
	{
		boolean canShow = emergencyTickerEnabled && confirmedMember && verified && !denied;
		emergencyTicker.setWarning(canShow && communications != null ? communications.emergency
																	 : null);
	}

	void displayCommunications(EmpireCommunicationsClient.Snapshot news,
							   java.util.function.BooleanSupplier stillCurrent)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillCurrent.getAsBoolean())
				return;
			long previous = communications == null ? -1 : communications.sequence;
			boolean recovered = communicationsError != null;
			communications = news;
			communicationsError = null;
			updateEmergencyTickerVisibility();
			// An outage banner must disappear after a successful poll,
			// even if the announcement sequence has not advanced.
			if ((showingAnnouncements || showingAdmin) && (previous != news.sequence || recovered))
				refresh();
		});
	}

	void displayWomAchievements(WomAchievementsClient.Snapshot feed,
								java.util.function.BooleanSupplier stillCurrent)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillCurrent.getAsBoolean())
				return;
			womAchievements = feed;
			womAchievementsError = null;
			// Animate only the single persistent achievement card. Polling never
			// reconstructs or flashes the Announcements page or its cards.
			womRotator.setAchievements(feed.entries);
		});
	}

	void failWomAchievements(java.util.function.BooleanSupplier stillCurrent)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillCurrent.getAsBoolean())
				return;
			womAchievementsError = "Achievement updates temporarily unavailable";
			womRotator.showUnavailable();
		});
	}

	void failCommunications(String error, java.util.function.BooleanSupplier stillCurrent)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillCurrent.getAsBoolean())
				return;
			communicationsError = error;
			if (showingAnnouncements)
				refresh();
		});
	}

	void showPublishStatus(String message, java.util.function.BooleanSupplier stillCurrent)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillCurrent.getAsBoolean() || !verified)
				return;
			publishStatus = message;
			if (showingAdmin || showingAnnouncements)
				refresh();
		});
	}

	void setEventHistoryDays(int days)
	{
		int bounded = Math.max(1, Math.min(365, days));
		SwingUtilities.invokeLater(() -> {
			if (eventHistoryDays != bounded)
			{
				eventHistoryDays = bounded;
				if (verified && showingEvents)
				{
					refresh();
				}
			}
		});
	}

	void setEventsLoader(Runnable loader)
	{
		eventsLoader = loader;
	}

	void setChapterEventsHandlers(java.util.function.Consumer<String> redeem, Runnable load)
	{
		chapterCodeRedeemer = redeem;
		chapterEventsLoader = load;
	}

	void setDiscordConnector(Runnable connector)
	{
		discordConnectAction = connector;
	}

	void setDiscordAuthorization(java.util.function.BooleanSupplier ready)
	{
		discordSessionReady = ready;
		SwingUtilities.invokeLater(this::refresh);
	}

	void beginLoginSession()
	{
		SwingUtilities.invokeLater(() -> {
			showingAnnouncements = true;
			showingEvents = false;
			showingEventHistory = false;
			showingAdmin = false;
			showingBans = false;
			selectedChapter = null;
			selectedBanChapter = null;
			publishStatus = null;
			rsvpResultMessage = null;
			refresh();
		});
	}

	void setChapterRsvpHandler(java.util.function.BiConsumer<String, String> submit,
							   java.util.function.BooleanSupplier authorized)
	{
		chapterRsvpHandler = submit;
		chapterRsvpAuthorized = authorized;
	}

	/**
	 * A failed Verify Discord click is a modal error, independent of the
	 * selected sidebar tab. Never publish verification failures into RSVP
	 * status text; no credentials or connection details are displayed.
	 */
	void showDiscordVerificationError(String message,
									  java.util.function.BooleanSupplier stillAuthorized)
	{
		if (message == null || message.isEmpty())
			return;
		SwingUtilities.invokeLater(() -> {
			if (!verified || !stillAuthorized.getAsBoolean())
				return;
			if (java.awt.GraphicsEnvironment.isHeadless())
				return;
			JTextArea text = readableText(message);
			text.setColumns(34);
			text.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
			javax.swing.JOptionPane.showMessageDialog(this, text, "Verify Discord",
													  javax.swing.JOptionPane.ERROR_MESSAGE);
		});
	}

	void rsvpResult(String message, java.util.function.BooleanSupplier stillAuthorized)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillAuthorized.getAsBoolean() || !verified)
			{
				return;
			}
			// A successful one-time Discord verification is reflected by
			// permissions and the hidden Verify button; it is not an RSVP
			// status, and a verbose confirmation crowds the Events page.
			if (message != null && message.startsWith("Discord verified as "))
				return;
			rsvpResultMessage = message;
			if (showingEvents && (message == null ||
				(!message.startsWith("Your RSVP was saved.") &&
				 !message.startsWith("Saving RSVP"))))
				refresh();
		});
	}

	void setChapterEventsScope(String chapter)
	{
		final String verifiedChapter = chapter == null ? "" : chapter;
		SwingUtilities.invokeLater(() -> {
			if (activeEventChapter.equals(verifiedChapter))
			{
				return;
			}
			activeEventChapter = verifiedChapter;
			clearChapterEventAccess();
			if (verified && showingEvents)
			{
				refresh();
			}
		});
	}

	private void clearChapterEventAccess()
	{
		// Roster synchronization is enough to request events from the backend.
		eventMemberConnected = !activeEventChapter.isEmpty();
		chapterEventsLoading = false;
		chapterEventsLastAttemptAt = 0;
		chapterEventsError = null;
		chapterEventFeed = Collections.emptyList();
		if (eventFeed != null)
			eventFeed = DiscordEventsClient.withPersonalEmpireInterest(
				eventFeed, Collections.emptySet());
		rsvpResultMessage = null;
	}

	void chapterEventsExpired(java.util.function.BooleanSupplier stillAuthorized)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillAuthorized.getAsBoolean())
			{
				return;
			}
			clearChapterEventAccess();
			chapterEventsError = "Discord verification is required again.";
			// Role loss must reveal the single global verification entry point.
			if (verified)
				refresh();
		});
	}

	void chapterEventsConnected(java.util.function.BooleanSupplier stillAuthorized)
	{
		chapterEventsConnected(stillAuthorized, false);
	}

	/** Keep confirmed event interest on a silent renewal of the same authority. */
	void chapterEventsConnected(java.util.function.BooleanSupplier stillAuthorized,
									 boolean preservePersonalInterest)
	{
		SwingUtilities.invokeLater(() -> {
			if (verified && stillAuthorized.getAsBoolean())
			{
				eventMemberConnected = true;
				// Initial verification must clear any prior Discord identity.
				// Periodic silent renewal of the same authority must NOT erase
				// a previously confirmed Interested RSVP while the HTTP poll runs.
				if (!preservePersonalInterest && eventFeed != null)
					eventFeed = DiscordEventsClient.withPersonalEmpireInterest(
						eventFeed, Collections.emptySet());
				chapterEventsError = null;
				if (rsvpResultMessage != null &&
					rsvpResultMessage.startsWith("Discord verified as "))
					rsvpResultMessage = null;
				// The single global Verify Discord button disappears regardless
				// of which page was open when OAuth/silent resume completed.
				refresh();
				if (showingEvents)
					requestChapterEvents();
			}
		});
	}

	private void requestChapterEvents()
	{
		if (!verified || !eventMemberConnected || activeEventChapter.isEmpty() ||
			chapterEventsLoading || chapterEventsLoader == null)
		{
			return;
		}
		chapterEventsLoading = true;
		chapterEventsLastAttemptAt = System.currentTimeMillis();
		chapterEventsError = null;
		if (showingEvents && chapterEventFeed.isEmpty())
			refresh();
		chapterEventsLoader.run();
	}

	void displayChapterEvents(java.util.List<DiscordEventsClient.Event> events,
							  java.util.function.BooleanSupplier stillAuthorized)
	{
		SwingUtilities.invokeLater(() -> {
			if (!verified || !stillAuthorized.getAsBoolean())
			{
				return;
			}
			boolean loadingFinished = chapterEventsLoading;
			chapterEventsLoading = false;
			chapterEventsError = null;
			boolean changed = !sameEvents(chapterEventFeed, events);
			// Even submitting the same RSVP must clear the temporary
			// "Saving" label and re-enable options without rebuilding Events.
			boolean inPlace = showingEvents && !events.isEmpty() &&
				rsvpOnlyFeedChange(chapterEventFeed, events) &&
				updateRsvpCardsWithoutReload(events);
			chapterEventFeed = Collections.unmodifiableList(new ArrayList<>(events));
			rememberEvents(events);
			// Even identical/empty results must clear a previously rendered spinner.
			if (showingEvents && !inPlace && (changed || loadingFinished))
				refresh();
		});
	}

	void failChapterEvents(String reason, java.util.function.BooleanSupplier stillAuthorized)
	{
		SwingUtilities.invokeLater(() -> {
			if (!verified || !stillAuthorized.getAsBoolean())
			{
				return;
			}
			chapterEventsLoading = false;
			chapterEventsError = reason;
			if (showingEvents)
			{
				refresh();
			}
		});
	}

	private void clearEvents()
	{
		eventCardCache.clear();
		attendanceKnownEvents.clear();
		attendanceServerStates = java.util.Collections.emptyMap();
		attendanceStatusesFetchedAt = 0L;
		attendanceStatusIds = java.util.Collections.emptyList();
		showingEvents = false;
		eventsLoading = false;
		eventsError = null;
		eventFeed = null;
		eventFetchedAt = 0;
		eventLastAttemptAt = 0;
		activeEventChapter = "";
		clearChapterEventAccess();
	}

	private void requestEvents(boolean force)
	{
		if (!verified || eventsLoading || eventsLoader == null)
		{
			return;
		}
		if (!force && eventFeed != null && System.currentTimeMillis() - eventFetchedAt < 5 * 60_000)
		{
			return;
		}
		eventsLoading = true;
		eventLastAttemptAt = System.currentTimeMillis();
		eventsError = null;
		// Keep existing event cards visible during background refresh.
		if (eventFeed == null)
			refresh();
		eventsLoader.run();
	}

	void displayEvents(DiscordEventsClient.Snapshot snapshot,
					   java.util.function.BooleanSupplier stillAuthorized)
	{
		displayEvents(snapshot, stillAuthorized, false);
	}

	/** Keep last-confirmed positive interest if the fresh private lookup failed. */
	void displayEvents(DiscordEventsClient.Snapshot snapshot,
					   java.util.function.BooleanSupplier stillAuthorized,
					   boolean lookupTemporarilyUnavailable)
	{
		SwingUtilities.invokeLater(() -> {
			if (!verified || !stillAuthorized.getAsBoolean())
			{
				return;
			}
			boolean loadingFinished = eventsLoading;
			DiscordEventsClient.Snapshot next = lookupTemporarilyUnavailable
				? DiscordEventsClient.retainConfirmedEmpireInterest(snapshot, eventFeed)
				: snapshot;
			boolean changed = eventFeed == null || !sameEvents(eventFeed.events, next.events);
			eventFeed = next;
			rememberEvents(next.events);
			eventFetchedAt = System.currentTimeMillis();
			eventsLoading = false;
			eventsError = null;
			// Loading is UI state, even when the returned Empire feed is identical.
			if (showingEvents && (changed || loadingFinished))
				refresh();
		});
	}

	void failEvents(String error, java.util.function.BooleanSupplier stillAuthorized)
	{
		SwingUtilities.invokeLater(() -> {
			if (!verified || !stillAuthorized.getAsBoolean())
			{
				return;
			}
			eventsLoading = false;
			eventsError = error;
			if (showingEvents)
			{
				refresh();
			}
		});
	}

	void setBanDirectoryLoader(java.util.function.Consumer<Boolean> callback)
	{
		banDirectoryLoader = callback;
	}

	/** Clear only current-chapter private reasons with independently verified officer access. */
	void setBanReasonClearAction(
		java.util.function.BiConsumer<FederationBanDirectoryClient.Entry, Integer> action,
		java.util.function.Predicate<FederationBanDirectoryClient.Entry> authorized)
	{
		banReasonClearAction = action;
		banReasonClearAuthorized = authorized == null ? entry -> false : authorized;
	}

	private boolean banCacheFresh()
	{
		return banDirectory != null && System.currentTimeMillis() - banDirectoryCachedAt <
										   FederationBanDirectoryClient.CACHE_MILLIS;
	}

	private void requestBanDirectory(boolean force)
	{
		if (!officerSettingsEligible || !verified || banDirectoryLoader == null ||
			banDirectoryLoading)
		{
			return;
		}
		if (!force && banCacheFresh())
		{
			return;
		}
		banDirectory = null;
		banDirectoryCachedAt = 0;
		cachedBanCounts = Collections.emptyMap();
		sortedBanEntries = Collections.emptyList();
		banDirectoryError = null;
		banDirectoryLoading = true;
		refresh();
		banDirectoryLoader.accept(force);
	}

	/** Always clear private names before a new account may use this panel. */
	private void clearPrivateBanDirectory()
	{
		showingBans = false;
		selectedBanChapter = null;
		banDirectory = null;
		banDirectoryCachedAt = 0;
		banDirectoryLoading = false;
		banDirectoryError = null;
		cachedBanCounts = Collections.emptyMap();
		sortedBanEntries = Collections.emptyList();
		banSearch.setText("");
		banChapterSearch.setText("");
	}

	void displayBanDirectory(FederationBanDirectoryClient.Snapshot snapshot,
							 java.util.function.BooleanSupplier stillAuthorized)
	{
		SwingUtilities.invokeLater(() -> {
			if (!verified || !officerSettingsEligible || !stillAuthorized.getAsBoolean())
			{
				return;
			}
			banDirectory = snapshot;
			Map<String, Integer> counts = new HashMap<>();
			List<FederationBanDirectoryClient.Entry> sorted = new ArrayList<>(snapshot.entries);
			sorted.sort(Comparator
							.comparing((FederationBanDirectoryClient.Entry e)
										   -> WomDirectoryClient.normalize(e.name))
							.thenComparing(e -> e.chapter));
			for (FederationBanDirectoryClient.Entry entry : sorted)
			{
				counts.merge(entry.chapter, 1, Integer::sum);
			}
			cachedBanCounts = Collections.unmodifiableMap(counts);
			sortedBanEntries = Collections.unmodifiableList(sorted);
			banDirectoryCachedAt = System.currentTimeMillis();
			banDirectoryError = null;
			banDirectoryLoading = false;
			refresh();
		});
	}

	void failBanDirectory(String message, java.util.function.BooleanSupplier stillAuthorized)
	{
		SwingUtilities.invokeLater(() -> {
			if (!verified || !officerSettingsEligible || !stillAuthorized.getAsBoolean())
			{
				return;
			}
			banDirectory = null;
			banDirectoryCachedAt = 0;
			banDirectoryLoading = false;
			cachedBanCounts = Collections.emptyMap();
			sortedBanEntries = Collections.emptyList();
			banDirectoryError = message;
			refresh();
		});
	}

	FederationBanDirectoryClient.Snapshot freshRecruitmentBanDirectory()
	{
		if (!verified || !officerSettingsEligible || !banCacheFresh())
		{
			return null;
		}
		return banDirectory;
	}

	void preloadBanDirectory()
	{
		SwingUtilities.invokeLater(() -> requestBanDirectory(false));
	}

	/** An equipment event can bypass the private cache TTL, but not officer auth. */
	void refreshPrivateBansOnVexillumEquip()
	{
		SwingUtilities.invokeLater(() -> requestBanDirectory(true));
	}

	void stopPrivateCacheTimer()
	{
		emergencyTicker.stopTicker();
		womRotator.stopAnimation();
		privateCacheTimer.stop();
		searchDebounceTimer.stop();
	}

	void setGlobalBanSearchEligible(boolean eligible)
	{
		SwingUtilities.invokeLater(() -> {
			if (globalBanSearchEligible == eligible)
				return;
			globalBanSearchEligible = eligible;
			if (!eligible && !officerSettingsEligible && showingBans)
			{
				clearPrivateBanDirectory();
				showingAnnouncements = true;
			}
			if (verified)
				refresh();
		});
	}

	void setOfficerSettingsEligible(boolean eligible)
	{
		if (officerSettingsRequested == eligible)
		{
			return;
		}
		officerSettingsRequested = eligible;
		SwingUtilities.invokeLater(() -> {
			// An older queued grant must not restore officer controls after rank loss.
			if (officerSettingsEligible == officerSettingsRequested)
			{
				return;
			}
			officerSettingsEligible = officerSettingsRequested;
			if (!officerSettingsEligible)
			{
				boolean wasViewingBans = showingBans;
				adminVerificationStatus = null;
				clearPrivateBanDirectory();
				// Retain the search-only tab if that narrower role remains.
				if (wasViewingBans && !globalBanSearchEligible)
					showingAnnouncements = true;
			}
			if (verified)
			{
				refresh();
			}
			if (officerSettingsEligible && verified)
			{
				requestBanDirectory(false);
			}
		});
	}

	FederationBanRegistry banRegistry()
	{
		return banRegistry;
	}

	void clearOfficerEvidence()
	{
		SwingUtilities.invokeLater(() -> {
			adminVerificationStatus = null;
			banRegistry.clear();
			clearPrivateBanDirectory();
			if (verified)
			{
				refresh();
			}
		});
	}

	private static final int SEARCH_PREVIEW_LIMIT = 120;
	private final JTextField globalSearch = new JTextField();
	private final JTextField chapterSearch = new JTextField();
	private final JPanel content = new JPanel();
	private final JPanel headerCards = new JPanel(new CardLayout());
	private final JLabel chapterTitle = SwingTextSafety.plainLabel("");
	private final JLabel status = new JLabel("Log in to load Holy Empire.");
	private volatile boolean verified = false;
	private boolean denied = false;
	private static final String DENIAL_MESSAGE =
		"Only verified members of the Holy Empire may access this plugin.";
	private static final String JOIN_INSTRUCTIONS =
		"The link above takes you to our discord. Follow the steps and create a ticket to join "
		+ "one of our chapters. Access requires membership in your in-game clan and the matching "
		+ "WOM roster. WOM sync may take 6 hours or more.";
	private static final String JOIN_URL = "https://discord.gg/w465";
	private static final String CLANFINDER_URL = "https://osrsclanfinder.com/clans/holy-empire";

	private Map<String, List<String>> members = Collections.emptyMap();
	private Map<String, List<WomDirectoryClient.ChapterMember>> chapters = Collections.emptyMap();
	private Map<String, Map<String, WomDirectoryClient.ChapterMember>> chapterRankIndex =
		Collections.emptyMap();
	private String emptyMessage = "Log in to load Holy Empire.";
	private boolean cachedStale = false;
	private String selectedChapter = null;

	public FederationPanel()
	{
		searchDebounceTimer.setRepeats(false);
		setLayout(new BorderLayout(0, 7));
		setBackground(HolyEmpireTheme.STEEL);
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(HolyEmpireTheme.STEEL);
		add(content, BorderLayout.CENTER);
		status.setForeground(HolyEmpireTheme.MUTED);
		status.setBackground(HolyEmpireTheme.STEEL);
		status.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, HolyEmpireTheme.GOLD_EDGE),
			BorderFactory.createEmptyBorder(4, 6, 3, 4)));
		add(status, BorderLayout.SOUTH);
		DocumentListener refreshOnSearch = new DocumentListener() {
			public void insertUpdate(DocumentEvent e)
			{
				queueSearchRefresh();
			}
			public void removeUpdate(DocumentEvent e)
			{
				queueSearchRefresh();
			}
			public void changedUpdate(DocumentEvent e)
			{
				queueSearchRefresh();
			}
		};
		globalSearch.setName("holyEmpireGlobalSearch");
		globalSearch.putClientProperty("JTextField.placeholderText", "Search player names");
		globalSearch.getDocument().addDocumentListener(refreshOnSearch);
		chapterSearch.setName("holyEmpireChapterSearch");
		chapterSearch.putClientProperty("JTextField.placeholderText",
										"Search this chapter's roster");
		chapterSearch.getDocument().addDocumentListener(refreshOnSearch);
		banSearch.setName("holyEmpireBanGlobalSearch");
		banSearch.putClientProperty("JTextField.placeholderText", "Search all synchronized bans");
		banSearch.getDocument().addDocumentListener(refreshOnSearch);
		banChapterSearch.setName("holyEmpireBanChapterSearch");
		banChapterSearch.putClientProperty("JTextField.placeholderText",
										   "Search this chapter's bans");
		banChapterSearch.getDocument().addDocumentListener(refreshOnSearch);
		HolyEmpireTheme.styleSearch(globalSearch);
		HolyEmpireTheme.styleSearch(chapterSearch);
		HolyEmpireTheme.styleSearch(banSearch);
		HolyEmpireTheme.styleSearch(banChapterSearch);
		HolyEmpireTheme.styleSearch(broadcastTitle);
		HolyEmpireTheme.styleSearch(announcementTitle);
		HolyEmpireTheme.styleSearch(announcementLink);
		announcementLink.setName("holyEmpireAdminAnnouncementDiscordLink");
		announcementLink.putClientProperty("JTextField.placeholderText",
										   "Optional Discord post link");
		((javax.swing.text.AbstractDocument)announcementBody.getDocument())
			.setDocumentFilter(new javax.swing.text.DocumentFilter() {
				private void apply(javax.swing.text.DocumentFilter.FilterBypass fb, int offset,
								   int length, String insert, javax.swing.text.AttributeSet attrs)
					throws javax.swing.text.BadLocationException
				{
					String incoming = insert == null ? "" : insert;
					int space = Math.max(0, 350 - (fb.getDocument().getLength() - length));
					if (incoming.length() > space)
						incoming = incoming.substring(0, space);
					fb.replace(offset, length, incoming, attrs);
				}
				@Override
				public void insertString(javax.swing.text.DocumentFilter.FilterBypass fb,
										 int offset, String text,
										 javax.swing.text.AttributeSet attrs)
					throws javax.swing.text.BadLocationException
				{
					apply(fb, offset, 0, text, attrs);
				}
				@Override
				public void replace(javax.swing.text.DocumentFilter.FilterBypass fb, int offset,
									int length, String text, javax.swing.text.AttributeSet attrs)
					throws javax.swing.text.BadLocationException
				{
					apply(fb, offset, length, text, attrs);
				}
			});
		announcementCounter.setName("holyEmpireAnnouncementCharacterCounter");
		announcementCounter.setForeground(HolyEmpireTheme.MUTED);
		announcementCounter.setHorizontalAlignment(SwingConstants.RIGHT);
		announcementBody.getDocument().addDocumentListener(
			new javax.swing.event.DocumentListener() {
				void count()
				{
					int size = announcementBody.getDocument().getLength();
					announcementCounter.setText(size + " / 350 characters" +
												(size >= 350 ? " · limit reached" : ""));
					announcementCounter.setForeground(size >= 350 ? HolyEmpireTheme.GOLD
																  : HolyEmpireTheme.MUTED);
				}
				@Override public void insertUpdate(javax.swing.event.DocumentEvent e)
				{
					count();
				}
				@Override public void removeUpdate(javax.swing.event.DocumentEvent e)
				{
					count();
				}
				@Override public void changedUpdate(javax.swing.event.DocumentEvent e)
				{
					count();
				}
			});
		enforcePublisherBodyLimit(broadcastBody, 200);
		enforcePublisherBodyLimit(emergencyBody, 200);
		watchPublisherLength(broadcastBody, broadcastCounter, 200);
		watchPublisherLength(emergencyBody, emergencyCounter, 200);
		HolyEmpireTheme.styleSearch(emergencyTitle);
		for (JTextArea editor : new JTextArea[] {broadcastBody, announcementBody, emergencyBody})
		{
			editor.setLineWrap(true);
			editor.setWrapStyleWord(true);
			editor.setBackground(HolyEmpireTheme.PANEL);
			editor.setForeground(HolyEmpireTheme.TEXT);
			editor.setCaretColor(HolyEmpireTheme.BLUE);
			editor.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(HolyEmpireTheme.GOLD_EDGE),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		}
		buildHeaders();
		headerCards.setName("holyEmpireHeaderCards");
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(HolyEmpireTheme.STEEL);
		top.setName("holyEmpireTop");
		top.add(buildSocialLinks());
		JLabel empireName = new JLabel("Holy Empire", SwingConstants.CENTER);
		empireName.setName("holyEmpireHeader");
		JPanel empireBanner = HolyEmpireTheme.banner(empireName, "For the Emperor!", false);
		// Increase only the permanent Empire brand, not shared banner styles
		// used for the Ban List or restricted-member screens.
		empireName.setFont(empireName.getFont().deriveFont(Font.BOLD, 20f));
		empireName.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, empireName.getPreferredSize().height));
		empireBanner.setName("holyEmpireBrandHeader");
		top.add(empireBanner);
		emergencyTicker.setAlignmentX(Component.CENTER_ALIGNMENT);
		top.add(emergencyTicker);
		rosterTab.setName("holyEmpireRosterTab");
		eventsTab.setName("holyEmpireEventsTab");
		banListTab.setName("holyEmpireBanListTab");
		announcementsTab.setName("holyEmpireAnnouncementsTab");
		announcementsTab.setToolTipText("Empire Announcements");
		adminTab.setName("holyEmpireAdminControlsTab");
		adminTab.setToolTipText("Verified officer administration and attendance");
		discordVerifyButton.setName("holyEmpireDiscordVerify");
		discordVerifyButton.setToolTipText("Verify your Discord membership and roles once.");
		discordVerifyButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
		discordVerifyButton.setRequestFocusEnabled(false);
		discordVerifyButton.addActionListener(e -> {
			if (discordConnectAction != null && verified &&
				(discordSessionReady == null || !discordSessionReady.getAsBoolean()))
				discordConnectAction.run();
		});
		ButtonGroup group = new ButtonGroup();
		group.add(rosterTab);
		group.add(eventsTab);
		group.add(banListTab);
		group.add(announcementsTab);
		group.add(adminTab);
		adminSections.setOpaque(false);
		adminSections.setLayout(new BoxLayout(adminSections, BoxLayout.Y_AXIS));
		adminSections.setName("holyEmpireAdminSubtabs");
		adminBroadcastsTab.setName("holyEmpireAdminBroadcastsSubtab");
		adminAnnouncementsTab.setName("holyEmpireAdminAnnouncementsSubtab");
		adminAttendanceTab.setName("holyEmpireAdminAttendanceSubtab");
		ButtonGroup adminGroup = new ButtonGroup();
		adminGroup.add(adminBroadcastsTab);
		adminGroup.add(adminAnnouncementsTab);
		adminGroup.add(adminAttendanceTab);
		adminAttendanceTab.setSelected(true);
		adminBroadcastsTab.addActionListener(e -> {
			if (!viewingBroadcasts())
				return;
			showingAdminAnnouncements = false;
			showingAdminAttendance = false;
			lastAdminSubtab = AdminSubtab.BROADCASTS;
			publishStatus = null;
			refresh();
		});
		adminAnnouncementsTab.addActionListener(e -> {
			showingAdminAnnouncements = true;
			showingAdminAttendance = false;
			lastAdminSubtab = AdminSubtab.ANNOUNCEMENTS;
			publishStatus = null;
			refresh();
		});
		adminAttendanceTab.addActionListener(e -> {
			if (!viewingAttendance())
				return;
			showingAdminAttendance = true;
			showingAdminAnnouncements = false;
			lastAdminSubtab = AdminSubtab.ATTENDANCE;
			publishStatus = null;
			refresh();
		});
		// Stable preferred order: Attendance, Announcements, Broadcasts.
		// Invisible buttons remain in the component tree but BoxLayout
		// excludes them from spacing.
		for (JToggleButton subtab :
			 new JToggleButton[] {adminAttendanceTab, adminAnnouncementsTab, adminBroadcastsTab})
		{
			subtab.setAlignmentX(LEFT_ALIGNMENT);
			subtab.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
			adminSections.add(subtab);
		}
		rosterTab.setSelected(true);
		rosterTab.addActionListener(e -> {
			showingEvents = false;
			showingEventHistory = false;
			showingBans = false;
			showingAnnouncements = false;
			showingAdmin = false;
			selectedBanChapter = null;
			refresh();
		});
		eventsTab.addActionListener(e -> {
			showingEvents = true;
			showingEventHistory = false;
			showingBans = false;
			showingAnnouncements = false;
			showingAdmin = false;
			selectedChapter = null;
			selectedBanChapter = null;
			refresh();
			requestEvents(false);
			requestChapterEvents();
		});
		banListTab.addActionListener(e -> {
			if (!viewingOfficer() || !viewingMember())
			{
				return;
			}
			showingBans = true;
			showingEvents = false;
			showingEventHistory = false;
			showingAnnouncements = false;
			showingAdmin = false;
			selectedChapter = null;
			refresh();
			requestBanDirectory(false);
		});
		announcementsTab.addActionListener(e -> {
			showingAnnouncements = true;
			showingAdmin = false;
			showingEvents = false;
			showingBans = false;
			showingEventHistory = false;
			selectedChapter = null;
			selectedBanChapter = null;
			refresh();
		});
		adminTab.addActionListener(e -> {
			if ((!viewingEmpireOwner() && !viewingAttendance()) || !viewingMember())
				return;
			// Default to Attendance the first time; thereafter restore
			// the most recently clicked admin subtab within this session.
			boolean canAttendance = viewingAttendance();
			boolean canAnnouncements = viewingEmpireOwner();
			boolean canBroadcasts = canAnnouncements && viewingBroadcasts();
			AdminSubtab selected = lastAdminSubtab;
			if ((selected == AdminSubtab.ATTENDANCE && !canAttendance) ||
				(selected == AdminSubtab.ANNOUNCEMENTS && !canAnnouncements) ||
				(selected == AdminSubtab.BROADCASTS && !canBroadcasts))
				selected = canAttendance	  ? AdminSubtab.ATTENDANCE
						   : canAnnouncements ? AdminSubtab.ANNOUNCEMENTS
											  : AdminSubtab.BROADCASTS;
			showingAdminAttendance = selected == AdminSubtab.ATTENDANCE;
			showingAdminAnnouncements = selected == AdminSubtab.ANNOUNCEMENTS;
			publishStatus = null;
			showingAdmin = true;
			showingAnnouncements = false;
			showingEvents = false;
			showingBans = false;
			showingEventHistory = false;
			selectedChapter = null;
			selectedBanChapter = null;
			refresh();
		});
		navigationTabs.setName("holyEmpireNavigationTabs");
		navigationTabs.setBackground(HolyEmpireTheme.STEEL);
		navigationTabs.setBorder(BorderFactory.createEmptyBorder(3, 5, 7, 5));
		navigationTabs.add(rosterTab);
		navigationTabs.add(eventsTab);
		navigationTabs.add(banListTab);
		navigationTabs.add(announcementsTab);
		navigationTabs.add(adminTab);
		navigationTabs.add(discordVerifyButton);
		top.add(navigationTabs);
		top.add(headerCards);
		add(top, BorderLayout.NORTH);
		privateCacheTimer = new javax.swing.Timer(30_000, e -> {
			// One-hour bearer expiry can occur without a panel navigation;
			// update the verification control without rebuilding the page.
			boolean showVerify =
				verified && discordConnectAction != null &&
				(discordSessionReady == null || !discordSessionReady.getAsBoolean());
			if (discordVerifyButton.isVisible() != showVerify)
			{
				discordVerifyButton.setVisible(showVerify);
				navigationTabs.revalidate();
				navigationTabs.repaint();
			}
			if (banDirectory != null && !banCacheFresh())
			{
				banDirectory = null;
				banDirectoryCachedAt = 0;
				cachedBanCounts = Collections.emptyMap();
				sortedBanEntries = Collections.emptyList();
				if (verified && officerSettingsEligible )
				{
					requestBanDirectory(false);
				}
			}
			// A Discord bot announcement becomes visible in a member's Events
			// tab without having to leave and re-open it. No constant polling
			// when the tab is hidden or when Discord membership is unverified.
			if (verified && showingEvents)
			{
				java.time.Instant now = java.time.Instant.now();
				for (Map.Entry<JLabel, DiscordEventsClient.Event> clock :
					 activeEventClocks.entrySet())
				{
					DiscordEventsClient.Event ev = clock.getValue();
					String label = trackingEvent(ev.id)
									   ? "ONGOING · TRACKING"
									   : EventTimeDisplay.label(ev.start, ev.end, now,
																java.time.ZoneId.systemDefault());
					if (!label.equals(clock.getKey().getText()))
					{
						clock.getKey().setText(label);
					}
					// The countdown must unlock the attendance control at one
					// hour even if no new Discord payload arrives.
					if (viewingAttendance() && attendanceStart != null)
					{
						CachedEventCard cached = eventCardCache.get(
							"next:" + ev.scope + ":" + ev.id);
						JButton attendance = cached == null ? null : findRsvpControl(cached.panel,
							"holyEmpireEventAttendance:" + ev.id, JButton.class);
						if (attendance != null && !trackingEvent(ev.id))
						{
							boolean eligible = eventAttendanceWindow(ev.start, ev.end, now);
							attendance.setEnabled(eligible &&
								(attendanceReport == null || attendanceSaved));
							attendance.setToolTipText(!eligible
								? "Attendance tracking becomes available 1 hour before the event starts."
								: "Observe Holy Empire members within 15 tiles of the attendance host; all chapters count");
						}
					}
				}
				if (System.currentTimeMillis() - eventLastAttemptAt >= 300_000)
				{
					requestEvents(false);
				}
				if (eventMemberConnected &&
					System.currentTimeMillis() - chapterEventsLastAttemptAt >= 120_000)
				{
					requestChapterEvents();
				}
			}
		});
		privateCacheTimer.start();
		refresh();
	}

	/** Debounce only large datasets to reduce Swing reconstruction under rapid typing. */
	private void queueSearchRefresh()
	{
		int banCount = banDirectory == null ? 0 : banDirectory.entries.size();
		if (members.size() + banCount >= 250)
		{
			searchDebounceTimer.restart();
		}
		else
		{
			searchDebounceTimer.stop();
			refresh();
		}
	}

	/** Always-visible shortcut row above the home, chapter and denied headers. */
	private JPanel buildSocialLinks()
	{
		JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		links.setName("holyEmpireSocialLinks");
		links.setOpaque(false);
		links.setBorder(BorderFactory.createEmptyBorder(2, 2, 3, 4));
		links.add(linkButton("holyEmpireDiscordLink", "Discord",
							 HolyEmpireAssets.socialIcon("discord"), JOIN_URL));
		links.add(linkButton("holyEmpireClanFinderLink", "ClanFinder",
							 HolyEmpireAssets.socialIcon("clanfinder"), CLANFINDER_URL));
		return links;
	}

	private static JButton linkButton(String name, String label, Icon icon, String url)
	{
		JButton button = new JButton() {
			@Override protected void paintComponent(Graphics graphics)
			{
				// RuneLite's Swing LAF paints a highlighted JButton's *content area*
				// only, leaving its padded edges dark. Fill the entire 31x31 square
				// behind the icon before the look-and-feel draws the icon itself.
				if (Boolean.TRUE.equals(getClientProperty("holyEmpireHover")))
				{
					Graphics2D surface = (Graphics2D)graphics.create();
					try
					{
						surface.setColor(HolyEmpireTheme.HOVER);
						surface.fillRect(0, 0, getWidth(), getHeight());
					}
					finally
					{
						surface.dispose();
					}
				}
				super.paintComponent(graphics);
			}
		};
		button.setName(name);
		button.setIcon(icon);
		button.setText(icon == null ? label : "");
		button.setToolTipText(label + " — " + url);
		button.getAccessibleContext().setAccessibleName(label + " link");
		button.putClientProperty("holyEmpireLinkURL", url);
		button.setFocusPainted(false);
		button.setFocusable(false);
		button.setRolloverEnabled(true);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		button.putClientProperty("holyEmpireHover", Boolean.FALSE);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(HolyEmpireTheme.GOLD_EDGE),
			BorderFactory.createEmptyBorder(3, 3, 3, 3)));
		// Explicit hover styling works with RuneLite's dark Swing look-and-feel.
		button.addMouseListener(new java.awt.event.MouseAdapter() {
			private void hover(boolean active)
			{
				button.putClientProperty("holyEmpireHover", active);
				button.setBackground(active ? HolyEmpireTheme.HOVER : HolyEmpireTheme.STEEL);
				button.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(active ? HolyEmpireTheme.GOLD
														  : HolyEmpireTheme.GOLD_EDGE),
					BorderFactory.createEmptyBorder(3, 3, 3, 3)));
				button.repaint();
			}
			@Override public void mouseEntered(java.awt.event.MouseEvent e)
			{
				hover(true);
			}
			@Override public void mouseExited(java.awt.event.MouseEvent e)
			{
				hover(false);
			}
		});
		button.setPreferredSize(new Dimension(31, 31));
		button.setMinimumSize(new Dimension(31, 31));
		button.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(url));
		return button;
	}

	private void buildHeaders()
	{
		// The persistent imperial banner lives above navigation, so these cards
		// contain only view-specific content and do not duplicate the header.
		JPanel home = new JPanel();
		home.setLayout(new BoxLayout(home, BoxLayout.Y_AXIS));
		home.setBackground(HolyEmpireTheme.STEEL);
		globalSearch.setAlignmentX(Component.CENTER_ALIGNMENT);
		globalSearch.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, globalSearch.getPreferredSize().height));
		home.add(Box.createVerticalStrut(7));
		home.add(globalSearch);
		headerCards.add(home, "home");
		JPanel gatedHeader = new JPanel();
		gatedHeader.setOpaque(false);
		gatedHeader.setPreferredSize(new Dimension(0, 0));
		headerCards.add(gatedHeader, "status");

		JPanel chapter = new JPanel();
		chapter.setLayout(new BoxLayout(chapter, BoxLayout.Y_AXIS));
		chapter.setBackground(HolyEmpireTheme.STEEL);
		JButton back = HolyEmpireTheme.button("← Back to chapters", false);
		back.setName("holyEmpireChapterBack");
		back.setAlignmentX(LEFT_ALIGNMENT);
		back.setHorizontalAlignment(SwingConstants.LEFT);
		back.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		back.addActionListener(e -> {
			selectedChapter = null;
			refresh();
		});
		chapter.add(back);
		chapterTitle.setAlignmentX(LEFT_ALIGNMENT);
		chapterTitle.setFont(chapterTitle.getFont().deriveFont(Font.BOLD, 15f));
		chapterTitle.setForeground(HolyEmpireTheme.GOLD);
		chapterTitle.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, HolyEmpireTheme.GOLD_EDGE),
			BorderFactory.createEmptyBorder(8, 3, 5, 3)));
		chapter.add(chapterTitle);
		chapterSearch.setAlignmentX(LEFT_ALIGNMENT);
		chapterSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		chapter.add(chapterSearch);
		headerCards.add(chapter, "chapter");
		JPanel banHome = new JPanel();
		banHome.setLayout(new BoxLayout(banHome, BoxLayout.Y_AXIS));
		banHome.setBackground(HolyEmpireTheme.STEEL);
		JLabel banHeader = new JLabel("Chapter Ban-Lists", SwingConstants.CENTER);
		banHeader.setName("holyEmpireBanListHeader");
		banSearch.setAlignmentX(Component.CENTER_ALIGNMENT);
		banSearch.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, banSearch.getPreferredSize().height));
		banHome.add(HolyEmpireTheme.banner(banHeader, "Restricted Chapter Records", true));
		banHome.add(Box.createVerticalStrut(7));
		banHome.add(banSearch);
		headerCards.add(banHome, "bans");
		JPanel chapterBans = new JPanel();
		chapterBans.setLayout(new BoxLayout(chapterBans, BoxLayout.Y_AXIS));
		chapterBans.setBackground(HolyEmpireTheme.STEEL);
		JButton backBans = HolyEmpireTheme.button("← Back to all bans", true);
		backBans.setName("holyEmpireBanChapterBack");
		backBans.setAlignmentX(LEFT_ALIGNMENT);
		backBans.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		backBans.addActionListener(e -> {
			selectedBanChapter = null;
			refresh();
		});
		chapterBans.add(backBans);
		banChapterTitle.setAlignmentX(LEFT_ALIGNMENT);
		banChapterTitle.setFont(banChapterTitle.getFont().deriveFont(Font.BOLD, 17f));
		banChapterTitle.setForeground(HolyEmpireTheme.GOLD);
		banChapterTitle.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, HolyEmpireTheme.CRIMSON_EDGE),
			BorderFactory.createEmptyBorder(8, 3, 5, 3)));
		chapterBans.add(banChapterTitle);
		banChapterSearch.setAlignmentX(LEFT_ALIGNMENT);
		banChapterSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		chapterBans.add(banChapterSearch);
		headerCards.add(chapterBans, "banChapter");
		JPanel eventsHeader = new JPanel();
		eventsHeader.setOpaque(false);
		eventsHeader.setPreferredSize(new Dimension(0, 0));
		headerCards.add(eventsHeader, "events");
	}

	public void showMessage(String message)
	{
		showMessage(message, () -> true);
	}

	public void showMessage(String message, java.util.function.BooleanSupplier stillCurrent)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillCurrent.getAsBoolean())
			{
				return;
			}
			verified = false;
			denied = false;
			banRegistry.clear();
			clearPrivateBanDirectory();
			clearEvents();
			publicBanHashes = Collections.emptySet();
			members = Collections.emptyMap();
			chapters = Collections.emptyMap();
			chapterRankIndex = Collections.emptyMap();
			cachedStale = false;
			emptyMessage = message;
			selectedChapter = null;
			refresh();
		});
	}

	/** Called only after a complete WOM snapshot proves the active account is absent. */
	public void showNotVerified()
	{
		showNotVerified(() -> true);
	}

	public void showNotVerified(java.util.function.BooleanSupplier stillCurrent)
	{
		SwingUtilities.invokeLater(() -> {
			if (!stillCurrent.getAsBoolean())
			{
				return;
			}
			verified = false;
			denied = true;
			banRegistry.clear();
			clearPrivateBanDirectory();
			clearEvents();
			publicBanHashes = Collections.emptySet();
			members = Collections.emptyMap();
			chapters = Collections.emptyMap();
			chapterRankIndex = Collections.emptyMap();
			cachedStale = false;
			selectedChapter = null;
			refresh();
		});
	}

	public void update(Map<String, List<String>> roster,
					   Map<String, List<WomDirectoryClient.ChapterMember>> chapterRosters)
	{
		update(roster, chapterRosters, false);
	}

	public void update(Map<String, List<String>> roster,
					   Map<String, List<WomDirectoryClient.ChapterMember>> chapterRosters,
					   boolean stale)
	{
		update(roster, chapterRosters, stale, () -> true);
	}

	public void update(Map<String, List<String>> roster,
					   Map<String, List<WomDirectoryClient.ChapterMember>> chapterRosters,
					   boolean stale, java.util.function.BooleanSupplier stillAuthorized)
	{
		update(roster, chapterRosters, stale, stillAuthorized, Collections.emptySet());
	}

	void update(Map<String, List<String>> roster,
				Map<String, List<WomDirectoryClient.ChapterMember>> chapterRosters, boolean stale,
				java.util.function.BooleanSupplier stillAuthorized, Set<String> publicWarningHashes)
	{
		Set<String> safeHashes = Collections.unmodifiableSet(new HashSet<>(publicWarningHashes));
		SwingUtilities.invokeLater(() -> {
			if (!stillAuthorized.getAsBoolean())
			{
				return;
			}
			verified = true;
			denied = false;
			members = new HashMap<>(roster);
			chapters = new HashMap<>(chapterRosters);
			chapterRankIndex = HolyEmpireChapterRankIndex.build(chapters);
			publicBanHashes = safeHashes;
			cachedStale = stale;
			emptyMessage = "No roster loaded";
			refresh();
			if (officerSettingsEligible)
			{
				requestBanDirectory(false);
			}
		});
	}

	/** Refresh the displayed chapter images once the background worker finishes. */
	void refreshChapterImages(java.util.function.BooleanSupplier stillCurrent)
	{
		SwingUtilities.invokeLater(() -> {
			if (verified && stillCurrent.getAsBoolean())
			{
				refresh();
			}
		});
	}

	private void addAligned(JComponent item)
	{
		item.setAlignmentX(LEFT_ALIGNMENT);
		content.add(item);
	}

	private JTextArea readableText(String message)
	{
		JTextArea area = new JTextArea(message);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setColumns(20);
		// Allow longer wrapped notices to grow instead of silently clipping
		// them at the old nine-line maximum on narrower RuneLite sidebars.
		int lines = Math.min(30, Math.max(2, (message.length() + 15) / 16));
		area.setRows(lines);
		area.setMaximumSize(new Dimension(
			Integer.MAX_VALUE, area.getFontMetrics(area.getFont()).getHeight() * (lines + 1)));
		area.setAlignmentX(LEFT_ALIGNMENT);
		area.setForeground(HolyEmpireTheme.MUTED);
		return area;
	}

	private void heading(String text, int size)
	{
		JLabel label = SwingTextSafety.plainLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, (float)size));
		HolyEmpireTheme.styleSection(label);
		label.setBorder(BorderFactory.createEmptyBorder(8, 3, 5, 3));
		addAligned(label);
	}

	/** Center section text within the entire RuneLite sidebar width. */
	private void centeredHeading(String text, int size, String name)
	{
		JLabel label = SwingTextSafety.plainLabel(text);
		label.setName(name);
		label.setFont(label.getFont().deriveFont(Font.BOLD, (float)size));
		HolyEmpireTheme.styleSection(label);
		label.setBorder(BorderFactory.createEmptyBorder(8, 3, 5, 3));
		label.setHorizontalAlignment(SwingConstants.CENTER);
		// In BoxLayout, CENTER on a label with only preferred text width
		// would have no visible effect. Stretch the label across its row.
		label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height));
		addAligned(label);
	}

	/** Rank details come from the same verified chapter rosters as chapter rows. */
	private WomDirectoryClient.ChapterMember findRank(String player, String clan)
	{
		return HolyEmpireChapterRankIndex.find(chapterRankIndex, clan, player);
	}

	private void addGlobalResult(String name)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setName("holyEmpireGlobalRow:" + name);
		HolyEmpireTheme.styleRow(row, HolyEmpireRankDisplay.isEmperor(name), false);
		JLabel rsn = SwingTextSafety.plainLabel(name);
		rsn.setFont(rsn.getFont().deriveFont(Font.BOLD));
		rsn.setForeground(HolyEmpireRankDisplay.isEmperor(name) ? HolyEmpireRankDisplay.GOLD
																: HolyEmpireTheme.TEXT);
		rsn.setAlignmentX(LEFT_ALIGNMENT);
		row.add(rsn);
		for (String clan : members.getOrDefault(name, Collections.emptyList()))
		{
			WomDirectoryClient.ChapterMember member = findRank(name, clan);
			JLabel rank = SwingTextSafety.plainLabel("");
			rank.setName("holyEmpireGlobalRank:" + name + ":" + clan);
			rank.setAlignmentX(LEFT_ALIGNMENT);
			if (member != null)
			{
				rank.setText(clan + " · " + HolyEmpireRankDisplay.title(member));
				rank.setIcon(HolyEmpireRankDisplay.icon(member));
				rank.setIconTextGap(5);
				rank.setForeground(HolyEmpireRankDisplay.titleColor(member));
			}
			else if (HolyEmpireRankDisplay.isEmperor(name))
			{
				rank.setText(clan + " · Emperor");
				rank.setIcon(HolyEmpireAssets.emperorIcon());
				rank.setForeground(HolyEmpireRankDisplay.GOLD);
			}
			else
			{
				rank.setText(clan + " · Rank unavailable");
				rank.setForeground(new Color(165, 165, 165));
			}
			rank.setFont(rank.getFont().deriveFont(11f));
			row.add(rank);
		}
		row.setMaximumSize(new Dimension(
			Integer.MAX_VALUE,
			Math.max(49, 27 + members.getOrDefault(name, Collections.emptyList()).size() * 23)));
		addAligned(row);
	}

	private void setStatus(String fullText, String displayText)
	{
		status.putClientProperty("html.disable", Boolean.TRUE);
		status.setText(displayText);
		status.setToolTipText("<html>" + SwingTextSafety.escapeHtml(fullText) + "</html>");
	}

	/** Only call with trusted markup and escaped dynamic content. */
	private void setHtmlStatus(String fullText, String safeHtml)
	{
		status.putClientProperty("html.disable", Boolean.FALSE);
		status.setText(safeHtml);
		status.setToolTipText("<html>" + SwingTextSafety.escapeHtml(fullText) + "</html>");
	}

	private void showUnavailable()
	{
		if (denied)
		{
			JTextArea notice = new JTextArea(DENIAL_MESSAGE);
			notice.setName("holyEmpireAccessDenied");
			notice.setEditable(false);
			notice.setFocusable(false);
			notice.setOpaque(false);
			notice.setLineWrap(true);
			notice.setWrapStyleWord(true);
			notice.setFont(notice.getFont().deriveFont(Font.BOLD, 14f));
			notice.setBorder(BorderFactory.createEmptyBorder(18, 7, 16, 7));
			notice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
			addAligned(notice);
			JButton join = HolyEmpireTheme.button("Join The Empire Here", false);
			join.setName("holyEmpireJoinHere");
			join.setToolTipText(JOIN_URL);
			join.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(JOIN_URL));
			// A full-width row allows the button to remain centered on narrow sidebars.
			JPanel joinRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
			joinRow.setName("holyEmpireJoinButtonRow");
			joinRow.setOpaque(false);
			joinRow.add(join);
			joinRow.setMaximumSize(
				new Dimension(Integer.MAX_VALUE, join.getPreferredSize().height + 6));
			addAligned(joinRow);

			JTextArea instructions = new JTextArea(JOIN_INSTRUCTIONS);
			instructions.setName("holyEmpireJoinInstructions");
			instructions.setEditable(false);
			instructions.setFocusable(false);
			instructions.setOpaque(false);
			instructions.setLineWrap(true);
			instructions.setWrapStyleWord(true);
			instructions.setFont(instructions.getFont().deriveFont(12f));
			instructions.setColumns(22);
			instructions.setRows(12);
			instructions.setBorder(BorderFactory.createEmptyBorder(12, 7, 8, 7));
			instructions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
			instructions.setToolTipText(JOIN_INSTRUCTIONS);
			addAligned(instructions);
			setStatus("", "");
		}
		else
		{
			JTextArea notice = new JTextArea(emptyMessage);
			notice.setEditable(false);
			notice.setFocusable(false);
			notice.setOpaque(false);
			notice.setLineWrap(true);
			notice.setWrapStyleWord(true);
			notice.setBorder(BorderFactory.createEmptyBorder(12, 7, 8, 7));
			notice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
			addAligned(notice);
			setStatus(emptyMessage, emptyMessage);
		}
	}

	private void showHome()
	{
		String query = WomDirectoryClient.normalize(globalSearch.getText());
		String target = globalSearch.getText().trim();
		List<String> privateMatches =
			officerSettingsEligible && banCacheFresh()
				? FederationRecruitmentGuard.matchingChapters(banDirectory, target)
				: Collections.emptyList();
		boolean rostered = members.containsKey(WomDirectoryClient.normalize(target));
		List<String> localMatches =
			rostered ? Collections.emptyList() : banRegistry.matches(target);
		boolean publicMatch = FederationPublicBanWarnings.matches(target, publicBanHashes, members);
		String warningText =
			!privateMatches.isEmpty()
				? FederationRecruitmentGuard.warningText(target, privateMatches, true)
			: !localMatches.isEmpty()
				? FederationRecruitmentGuard.warningText(target, localMatches, true)
			: publicMatch ? FederationPublicBanWarnings.publicWarningText(target)
						  : null;
		if (warningText != null)
		{
			JTextArea warning = new JTextArea(warningText);
			warning.setName("holyEmpireRecruitWarning");
			warning.setEditable(false);
			warning.setFocusable(false);
			warning.setOpaque(false);
			warning.setLineWrap(true);
			warning.setWrapStyleWord(true);
			warning.setForeground(new Color(240, 100, 100));
			warning.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
			warning.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));
			addAligned(warning);
		}
		if (!query.isEmpty() && !members.isEmpty())
		{
			List<String> matches = HolyEmpireRosterIndex.search(members, query);
			heading("Global results · " + matches.size(), 12);
			for (int i = 0; i < Math.min(matches.size(), SEARCH_PREVIEW_LIMIT); i++)
			{
				addGlobalResult(matches.get(i));
			}
			if (matches.isEmpty())
			{
				addAligned(new JLabel("No matching members or chapters."));
			}
			if (matches.size() > SEARCH_PREVIEW_LIMIT)
			{
				addAligned(
					new JLabel("Showing " + SEARCH_PREVIEW_LIMIT + "; refine search for more."));
			}
		}
		heading("Chapters of the Holy Empire", 13);
		for (HolyEmpireChapterCatalog.Chapter info : HolyEmpireChapterCatalog.ORDERED)
		{
			final String clan = info.name;
			List<WomDirectoryClient.ChapterMember> roster =
				chapters.getOrDefault(clan, Collections.emptyList());
			JButton chapter = HolyEmpireTheme.button("", false);
			chapter.setName("holyEmpireChapter:" + clan);
			chapter.setLayout(new BorderLayout(7, 0));
			chapter.setFocusPainted(false);
			chapter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
			chapter.setPreferredSize(new Dimension(210, 58));
			chapter.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(HolyEmpireTheme.GOLD_EDGE),
				BorderFactory.createEmptyBorder(6, 7, 6, 7)));

			JLabel chapterIcon =
				new JLabel(HolyEmpireAssets.chapterIcon(info.iconKey, info.iconSha256));
			chapterIcon.setName("holyEmpireChapterIcon:" + clan);
			chapter.add(chapterIcon, BorderLayout.WEST);

			JPanel lines = new JPanel();
			lines.setOpaque(false);
			lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
			JLabel title = SwingTextSafety.plainLabel(clan);
			title.setName("holyEmpireChapterTitle:" + clan);
			title.setFont(
				title.getFont().deriveFont(Font.BOLD, HolyEmpireTheme.CHAPTER_NAME_FONT_SIZE_PT));
			title.setAlignmentX(LEFT_ALIGNMENT);
			JLabel subtitle = SwingTextSafety.plainLabel(info.subtitle());
			subtitle.setName("holyEmpireChapterDescription:" + clan);
			subtitle.setForeground(HolyEmpireTheme.MUTED);
			subtitle.setFont(subtitle.getFont().deriveFont(14f));
			subtitle.setAlignmentX(LEFT_ALIGNMENT);
			lines.add(title);
			lines.add(Box.createVerticalStrut(3));
			lines.add(subtitle);
			chapter.add(lines, BorderLayout.CENTER);

			JLabel count = new JLabel(info.hasWomRoster() ? Integer.toString(roster.size()) : "—");
			count.setForeground(HolyEmpireTheme.GOLD);
			count.setName("holyEmpireChapterCount:" + clan);
			chapter.add(count, BorderLayout.EAST);
			if (!info.hasWomRoster())
			{
				chapter.setToolTipText("No Wise Old Man group configured for " + clan);
			}
			else
			{
				chapter.setToolTipText(clan + " · " + roster.size() + " members");
			}
			chapter.addActionListener(e -> {
				selectedChapter = clan;
				chapterSearch.setText("");
				refresh();
			});
			addAligned(chapter);
			content.add(Box.createVerticalStrut(5));
		}
		String total = String.format(Locale.ROOT, "%,d", members.size());
		String full = total + " players · " + HolyEmpireChapterCatalog.ORDERED.size() +
					  " chapters · " + chapters.size() + " WOM rosters" +
					  (cachedStale ? " · WOM cached" : "");
		setHtmlStatus(full, "<html>" + total + " players · " +
								HolyEmpireChapterCatalog.ORDERED.size() + " chapters<br>" +
								chapters.size() + " WOM rosters" +
								(cachedStale ? " · WOM cached" : "") + "</html>");
	}

	private void addRankRow(WomDirectoryClient.ChapterMember member)
	{
		JPanel row = new JPanel(new BorderLayout(5, 1));
		row.setName("holyEmpireRosterRow:" + member.name);
		HolyEmpireTheme.styleRow(row, HolyEmpireRankDisplay.isEmperor(member.name), false);
		JLabel name = SwingTextSafety.plainLabel(member.name);
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setForeground(HolyEmpireRankDisplay.isEmperor(member.name) ? HolyEmpireRankDisplay.GOLD
																		: HolyEmpireTheme.TEXT);
		JLabel rank = SwingTextSafety.plainLabel(HolyEmpireRankDisplay.title(member));
		rank.setName("holyEmpireRank:" + member.name);
		rank.setIcon(HolyEmpireRankDisplay.icon(member));
		rank.setIconTextGap(4);
		rank.setForeground(HolyEmpireRankDisplay.titleColor(member));
		row.add(name, BorderLayout.WEST);
		row.add(rank, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
		addAligned(row);
	}

	private void showChapter()
	{
		String clan = selectedChapter;
		HolyEmpireChapterCatalog.Chapter info = HolyEmpireChapterCatalog.get(clan);
		chapterTitle.setText(clan);
		chapterTitle.setIcon(
			info == null ? null : HolyEmpireAssets.chapterIcon(info.iconKey, info.iconSha256));
		chapterTitle.setIconTextGap(7);
		if (info != null && !info.hasWomRoster())
		{
			heading("Roster unavailable", 12);
			addAligned(new JLabel("Wise Old Man group not configured."));
			setHtmlStatus(clan + " · awaiting WOM group ID",
						  "<html>" + SwingTextSafety.escapeHtml(clan) +
							  "<br>Awaiting WOM group ID</html>");
			return;
		}
		List<WomDirectoryClient.ChapterMember> all =
			chapters.getOrDefault(clan, Collections.emptyList());
		List<WomDirectoryClient.ChapterMember> sorted = new ArrayList<>(all);
		// Also reorder central/cached rosters that were produced before this override.
		sorted.sort(Comparator.comparingInt(m -> HolyEmpireRankDisplay.isEmperor(m.name) ? 0 : 1));
		List<WomDirectoryClient.ChapterMember> filtered =
			HolyEmpireRosterIndex.searchChapter(sorted, chapterSearch.getText());
		heading("Roster · " + filtered.size() + " / " + all.size(), 12);
		if (filtered.isEmpty())
		{
			addAligned(new JLabel("No matching members."));
		}
		for (WomDirectoryClient.ChapterMember member : filtered)
		{
			addRankRow(member);
		}
		setHtmlStatus(clan + " · WOM rank order" + (cachedStale ? " · cached" : ""),
					  "<html>" + SwingTextSafety.escapeHtml(clan) + "<br>WOM rank order" +
						  (cachedStale ? " · cached" : "") + "</html>");
	}

	private static boolean banNameMatches(String name, String query)
	{
		return WomDirectoryClient.normalize(name).contains(WomDirectoryClient.normalize(query));
	}

	private List<FederationBanDirectoryClient.Entry> filteredBans(String chapter, String query)
	{
		if (banDirectory == null)
		{
			return Collections.emptyList();
		}
		List<FederationBanDirectoryClient.Entry> selected = new ArrayList<>();
		for (FederationBanDirectoryClient.Entry entry : sortedBanEntries)
		{
			if ((chapter == null || chapter.equals(entry.chapter)) &&
				banNameMatches(entry.name, query))
			{
				selected.add(entry);
			}
		}
		// Cached entries are sorted once, not on every search keystroke.
		return selected;
	}

	private void addBanEntry(FederationBanDirectoryClient.Entry entry, boolean showChapter)
	{
		JPanel row = new JPanel(new BorderLayout(4, 1));
		row.setName("holyEmpireBanEntry");
		HolyEmpireTheme.styleRow(row, false, true);
		JLabel name = SwingTextSafety.plainLabel(entry.name);
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setForeground(HolyEmpireTheme.TEXT);
		row.add(name, BorderLayout.WEST);
		JLabel details = SwingTextSafety.plainLabel(
			(showChapter ? entry.chapter + " · " : "") +
			(entry.identityStatus.equals("tracked") ? "WOM tracked" : "Name not yet tracked"));
		details.setFont(details.getFont().deriveFont(10f));
		details.setForeground(HolyEmpireTheme.MUTED);
		JPanel metadata = new JPanel();
		metadata.setOpaque(false);
		metadata.setLayout(new BoxLayout(metadata, BoxLayout.Y_AXIS));
		metadata.add(details);
		if (!entry.category.isEmpty())
		{
			String summary = entry.reason.replace('\n', ' ').replace('\t',' ').trim();
			if (summary.length() > 95)
				summary = summary.substring(0, 95) + "…";
			JLabel explanation = SwingTextSafety.plainLabel(
				"Reason: " + entry.category +
				(entry.reason.isEmpty() ? "" : " — " + summary));
			explanation.setName("holyEmpireBanReason:" + entry.name);
			explanation.setForeground(HolyEmpireTheme.GOLD);
			explanation.setFont(explanation.getFont().deriveFont(11f));
			explanation.setToolTipText("Category: " + entry.category +
				"\nReason: " + (entry.reason.isEmpty() ? "Not specified" : entry.reason) +
				(entry.discordPosted ? "\nPosted by Servo-Skull in #ban-list" :
				 "\nPrivate staff record"));
			metadata.add(explanation);
		}
		row.add(metadata, BorderLayout.SOUTH);
		// Only the matching chapter's currently authorized officer may clear.
		// The entry stays banned; this only removes private annotation fields.
		if (verified && officerSettingsEligible &&
			banDirectory != null && banReasonClearAction != null &&
			!entry.category.isEmpty() && banReasonClearAuthorized.test(entry))
		{
			FederationBanDirectoryClient.Chapter chapter =
				banDirectory.chapters.get(entry.chapter);
			if (chapter != null)
			{
				JButton clear = HolyEmpireTheme.button("Clear", false);
				clear.setName("holyEmpireClearBanReason:" + entry.chapter + ":" + entry.name);
				clear.setFont(clear.getFont().deriveFont(10f));
				clear.setToolTipText("Clear saved category and reason. Does not unban this player.");
				clear.addActionListener(e -> banReasonClearAction.accept(entry, chapter.revision));
				row.add(clear, BorderLayout.EAST);
			}
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			entry.category.isEmpty() ? 46 : 70));
		addAligned(row);
	}

	private static String eventDate(java.time.Instant date)
	{
		return java.time.format.DateTimeFormatter
			.ofPattern("EEE, MMM d · h:mm a z", Locale.getDefault())
			.withZone(java.time.ZoneId.systemDefault())
			.format(date);
	}

	/** Mix only public Empire events with the privately authenticated native chapter. */
	private void showEvents()
	{
		activeEventClocks.clear();
		centeredHeading(showingEventHistory ? "Event History" : "Events", 17,
						"holyEmpireEventsHeading");
		JButton historyButton = HolyEmpireTheme.button(
			showingEventHistory ? "Back to Upcoming Events" : "Event History", false);
		historyButton.setName(showingEventHistory ? "holyEmpireEventsHistoryBack"
												  : "holyEmpireEventsHistoryOpen");
		historyButton.setRequestFocusEnabled(false);
		historyButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		historyButton.addActionListener(e -> {
			showingEventHistory = !showingEventHistory;
			if (showingEventHistory && attendanceHistoryLoader != null)
				attendanceHistoryLoader.run();
			refresh();
		});
		addAligned(historyButton);
		content.add(Box.createVerticalStrut(6));
		if (showingEventHistory)
		{
			showEventHistory();
			return;
		}
		JButton refreshButton = HolyEmpireTheme.button("Refresh Events", false);
		refreshButton.setName("holyEmpireEventsRefresh");
		// Clicking must not steal keyboard focus and scroll the viewport to a
		// replacement component when the Events panel is rebuilt.
		refreshButton.setRequestFocusEnabled(false);
		refreshButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		refreshButton.addActionListener(e -> {
			requestEvents(true);
			requestChapterEvents();
		});
		addAligned(refreshButton);
		content.add(Box.createVerticalStrut(6));
		// Manual attendance belongs exclusively to Admin Controls, not
		// the public Events tab, whether tracking or already finished.
		if (attendanceReport != null && !"manual".equals(attendanceReport.scope) &&
			(viewingAttendance() || viewingEventAttendance()))
		{
			heading("Attendance", 14);
			showAttendanceReport();
			content.add(Box.createVerticalStrut(10));
		}

		if (eventsLoading)
		{
			JLabel loading = SwingTextSafety.plainLabel("Loading Empire events...");
			loading.setName("holyEmpireEventsLoading");
			addAligned(loading);
		}
		else if (eventsError != null)
		{
			JLabel failure =
				SwingTextSafety.plainLabel("Empire events unavailable. Use Refresh to retry.");
			failure.setName("holyEmpireEventsError");
			addAligned(failure);
		}
		else if (eventFeed == null)
		{
			addAligned(SwingTextSafety.plainLabel("Empire calendar not loaded."));
		}

		if (activeEventChapter.isEmpty())
		{
			addAligned(SwingTextSafety.plainLabel(
				"Join your recognized chapter to view private chapter events."));
		}
		else
		{
			if (chapterEventsLoading)
			{
				JLabel loading = SwingTextSafety.plainLabel("Loading your chapter events...");
				loading.setName("holyEmpireChapterEventsLoading");
				addAligned(loading);
			}
			if (chapterEventsError != null)
			{
				JTextArea error = readableText(chapterEventsError);
				error.setName("holyEmpireChapterEventsError");
				addAligned(error);
			}
			// Chapter events are still authored exclusively via Discord.
			// A Discord-verified member session permits only a personal RSVP.
		}
		if (!activeEventChapter.isEmpty() && chapterRsvpAuthorized != null)
		{
			if (rsvpResultMessage != null &&
				!rsvpResultMessage.startsWith("Authorize Holy Empire in Discord"))
			{
				JTextArea result = readableText(rsvpResultMessage);
				result.setName("holyEmpireRsvpStatus");
				result.setForeground(HolyEmpireTheme.MUTED);
				addAligned(result);
			}
		}
		content.add(Box.createVerticalStrut(6));

		List<DiscordEventsClient.Event> entries = new ArrayList<>();
		if (eventFeed != null)
		{
			// Discord's official scheduled-event list is always Empire-wide.
			for (DiscordEventsClient.Event event : eventFeed.events)
			{
				if ("empire".equals(event.scope))
				{
					entries.add(event);
				}
			}
		}
		if (eventMemberConnected)
		{
			for (DiscordEventsClient.Event event : chapterEventFeed)
			{
				if ("chapter".equals(event.scope) &&
					("*".equals(activeEventChapter) || activeEventChapter.equals(event.chapter)))
				{
					entries.add(event);
				}
			}
		}
		supplementRememberedEvents(entries);
		java.time.Instant now = java.time.Instant.now();
		List<DiscordEventsClient.Event> upcoming = EventHistory.current(entries, now);
		for (DiscordEventsClient.Event event : entries)
		{
			if (trackingEvent(event.id) &&
				upcoming.stream().noneMatch(item -> item.id.equals(event.id)))
				upcoming.add(event);
		}
		if (attendanceReport != null && attendanceReport.running &&
			trackedAttendanceEvent != null &&
			attendanceReport.eventId.equals(trackedAttendanceEvent.id) &&
			upcoming.stream().noneMatch(e -> e.id.equals(trackedAttendanceEvent.id)))
			upcoming.add(trackedAttendanceEvent);
		List<DiscordEventsClient.Event> history = EventHistory.past(entries, now, eventHistoryDays);
		history.removeIf(event -> trackingEvent(event.id));
		if (upcoming.isEmpty() && !eventsLoading && !chapterEventsLoading)
		{
			addAligned(SwingTextSafety.plainLabel("No upcoming events loaded."));
		}
		for (DiscordEventsClient.Event event : upcoming)
		{
			addEventCard(event, false);
		}
		setStatus("Displayed upcoming Empire and chapter events", upcoming.size() + " upcoming");
	}

	private void showEventHistory()
	{
		JTextArea window =
			readableText("Past " + eventHistoryDays + (eventHistoryDays == 1 ? " day" : " days") +
						 " · Change under Holy Empire settings");
		window.setName("holyEmpireEventHistoryWindow");
		addAligned(window);
		content.add(Box.createVerticalStrut(6));
		List<DiscordEventsClient.Event> entries = new ArrayList<>();
		if (eventFeed != null)
		{
			for (DiscordEventsClient.Event event : eventFeed.events)
			{
				if ("empire".equals(event.scope))
				{
					entries.add(event);
				}
			}
		}
		if (eventMemberConnected)
		{
			for (DiscordEventsClient.Event event : chapterEventFeed)
			{
				if ("chapter".equals(event.scope) &&
					("*".equals(activeEventChapter) || activeEventChapter.equals(event.chapter)))
				{
					entries.add(event);
				}
			}
		}
		supplementRememberedEvents(entries);
		List<DiscordEventsClient.Event> history =
			EventHistory.past(entries, java.time.Instant.now(), eventHistoryDays);
		history.removeIf(event -> trackingEvent(event.id));
		if (history.isEmpty() && !eventsLoading && !chapterEventsLoading)
		{
			JLabel empty = SwingTextSafety.plainLabel("No events in this history window.");
			empty.setName("holyEmpireEventHistoryEmpty");
			addAligned(empty);
		}
		else if (history.isEmpty())
		{
			addAligned(SwingTextSafety.plainLabel("Loading past events..."));
		}
		for (DiscordEventsClient.Event event : history)
		{
			addEventCard(event, true);
		}
		setStatus("Displayed Empire and chapter history", history.size() + " historical events");
	}

	/** Fit an event image across the card's content area without distortion. */
	static ImageIcon fitEventCardImage(ImageIcon source, int targetWidth)
	{
		if (source == null || source.getIconWidth() < 1 || source.getIconHeight() < 1)
			return null;
		int width = Math.max(1, Math.min(580, targetWidth));
		int height = Math.max(
			1, Math.round(source.getIconHeight() * (width / (float)source.getIconWidth())));
		Image resized = source.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
		return new ImageIcon(resized);
	}

	static boolean eventAttendanceWindow(
		java.time.Instant start, java.time.Instant end, java.time.Instant now)
	{
		return start != null && now != null &&
			!now.isBefore(start.minusSeconds(3600)) &&
			(end == null || now.isBefore(end));
	}

	private void addEventCard(DiscordEventsClient.Event event, boolean historical)
	{
		String cacheKey = (historical ? "past:" : "next:") + event.scope + ":" + event.id;
		String collapseKey = event.scope + ":" + event.id;
		boolean initiallyCollapsed = collapsedEventIds.contains(collapseKey);
		List<Object> signature = new ArrayList<>(eventSignature(event));
		// RSVP permission is part of the card's visible state even when
		// its Discord event payload is unchanged.
		signature.add(!historical && "chapter".equals(event.scope) &&
					  chapterRsvpAuthorized != null && chapterRsvpHandler != null &&
					  chapterRsvpAuthorized.getAsBoolean());
		signature.add(!historical && viewingAttendance());
		signature.add(attendanceReport == null ? "" : attendanceReport.eventId);
		signature.add(attendanceReport != null && attendanceReport.running);
		signature.add(attendanceServerStates.containsKey(event.id)
						  ? attendanceServerStates.get(event.id).activeCount
						  : 0);
		signature.add(attendanceSaved);
		signature.add(historical ? eventAttendanceNumbers.get(event.id) : null);
		CachedEventCard old = eventCardCache.get(cacheKey);
		if (old != null && old.signature.equals(signature))
		{
			if (!historical)
				activeEventClocks.put(old.time, event);
			addAligned(old.panel);
			content.add(Box.createVerticalStrut(8));
			return;
		}
		JPanel card = new JPanel();
		card.setName((historical ? "holyEmpireHistoryEvent:" : "holyEmpireEvent:") + event.id);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setOpaque(true);
		card.setBackground(HolyEmpireTheme.PANEL);
		boolean chapter = "chapter".equals(event.scope);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(chapter ? HolyEmpireTheme.SILVER_EDGE
												   : HolyEmpireTheme.GOLD_EDGE),
			BorderFactory.createEmptyBorder(7, 8, 7, 8)));
		JLabel scope = SwingTextSafety.plainLabel(chapter ? "CHAPTER EVENT" : "EMPIRE EVENT");
		scope.setName("holyEmpireEventScope:" + event.id);
		scope.setForeground(chapter ? HolyEmpireTheme.SILVER : HolyEmpireTheme.GOLD);
		scope.setFont(scope.getFont().deriveFont(Font.BOLD, 13f));
		scope.setAlignmentX(LEFT_ALIGNMENT);
		JPanel scopeStack = new JPanel();
		scopeStack.setLayout(new BoxLayout(scopeStack, BoxLayout.Y_AXIS));
		scopeStack.setOpaque(false);
		scopeStack.add(scope);
		int headerTextHeight = scope.getPreferredSize().height;
		if (chapter)
		{
			JLabel chapterHeading = SwingTextSafety.plainLabel(event.chapter);
			chapterHeading.setName("holyEmpireEventChapterHeading:" + event.id);
			chapterHeading.setForeground(HolyEmpireTheme.SILVER);
			chapterHeading.setFont(chapterHeading.getFont().deriveFont(Font.BOLD, 12f));
			chapterHeading.setAlignmentX(LEFT_ALIGNMENT);
			scopeStack.add(chapterHeading);
			headerTextHeight += chapterHeading.getPreferredSize().height;
		}
		// Separate the text and right-hand controls to prevent overlap.
		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setOpaque(false);
		headerRow.setAlignmentX(LEFT_ALIGNMENT);
		JButton collapse = HolyEmpireTheme.button(initiallyCollapsed ? "Expand ▾" : "Collapse ▴", false);
		collapse.setName("holyEmpireEventCollapse:" + event.id);
		collapse.setToolTipText("Toggle this event's details. Your choice is remembered between sessions.");
		collapse.setRequestFocusEnabled(false);
		collapse.setFont(collapse.getFont().deriveFont(10f));
		JPanel collapseRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		collapseRow.setOpaque(false);
		collapseRow.add(collapse);
		headerRow.add(scopeStack, BorderLayout.CENTER);
		headerRow.add(collapseRow, BorderLayout.EAST);
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			Math.max(headerTextHeight, collapse.getPreferredSize().height) + 3));
		JLabel interested = SwingTextSafety.plainLabel(
			"Interested: " +
			(event.interestedCount >= 0 ? Integer.toString(event.interestedCount) : "—"));
		interested.setName("holyEmpireEventInterested:" + event.id);
		interested.setToolTipText(chapter ? "Going and Maybe RSVPs combined"
										  : "Discord scheduled-event subscribers");
		interested.setForeground(chapter ? HolyEmpireTheme.SILVER : HolyEmpireTheme.GOLD);
		interested.setFont(interested.getFont().deriveFont(Font.PLAIN, 12f));
		interested.setHorizontalAlignment(SwingConstants.RIGHT);
		card.add(headerRow);
		JTextArea name = readableText(event.name);
		name.setName("holyEmpireEventName:" + event.id);
		name.setForeground(chapter ? HolyEmpireTheme.SILVER : HolyEmpireTheme.GOLD);
		name.setToolTipText(null);
		name.setFont(name.getFont().deriveFont(Font.BOLD, 18f));
		int titleWidth = Math.max(140, content.getWidth() > 0 ? content.getWidth() - 35 : 210);
		fitEventText(name, titleWidth);
		name.setAlignmentX(LEFT_ALIGNMENT);
		card.add(name);
		JLabel compactDate = SwingTextSafety.plainLabel(eventDate(event.start));
		compactDate.setName("holyEmpireEventCompactDate:" + event.id);
		compactDate.setForeground(HolyEmpireTheme.MUTED);
		compactDate.setAlignmentX(LEFT_ALIGNMENT);
		compactDate.setVisible(initiallyCollapsed);
		JLabel rsvpMark = SwingTextSafety.plainLabel("");
		rsvpMark.setName("holyEmpireEventPersonalRsvp:" + event.id);
		rsvpMark.setFont(rsvpMark.getFont().deriveFont(Font.BOLD, 18f));
		stylePersonalEventRsvp(rsvpMark,
			"empire".equals(event.scope) && !"going".equals(event.myRsvp)
				? "" : event.myRsvp);
		rsvpMark.setVisible(initiallyCollapsed && !rsvpMark.getText().isEmpty());
		rsvpMark.setPreferredSize(new Dimension(25, 25));
		rsvpMark.setHorizontalAlignment(SwingConstants.RIGHT);
		JPanel compactFooter = new JPanel(new BorderLayout(8, 0));
		compactFooter.setName("holyEmpireEventCompactFooter:" + event.id);
		compactFooter.setAlignmentX(LEFT_ALIGNMENT);
		compactFooter.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		compactFooter.setOpaque(false);
		compactFooter.add(compactDate, BorderLayout.CENTER);
		compactFooter.add(rsvpMark, BorderLayout.EAST);
		compactFooter.setVisible(initiallyCollapsed);
		card.add(compactFooter);
		JPanel details = new JPanel();
		details.setName("holyEmpireEventExpanded:" + event.id);
		details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
		details.setBackground(HolyEmpireTheme.PANEL);
		details.setOpaque(false);
		details.setAlignmentX(LEFT_ALIGNMENT);
		// Keep the image between the title and the event details. Use a
		// full-width centered JLabel so a resized image stays centered.
		if (!historical && !event.imageUrl.isEmpty())
		{
			JLabel preview = SwingTextSafety.plainLabel("Loading event image...");
			preview.setName("holyEmpireEventImagePreview:" + event.id);
			preview.setForeground(HolyEmpireTheme.MUTED);
			preview.setFont(preview.getFont().deriveFont(preview.getFont().getSize2D() + 2f));
			preview.setAlignmentX(LEFT_ALIGNMENT);
			preview.setHorizontalAlignment(SwingConstants.CENTER);
			preview.setMaximumSize(
				new Dimension(Integer.MAX_VALUE, preview.getPreferredSize().height));
			details.add(Box.createVerticalStrut(5));
			details.add(preview);
			if (eventImageLoader != null)
			{
				final ImageIcon[] original = {null};
				final int[] fittedWidth = {0};
				Runnable fit = () ->
				{
					if (original[0] == null)
						return;
					Insets margin = card.getInsets();
					int usable = card.getWidth() - margin.left - margin.right;
					if (usable < 40)
					{
						// First layout may not have completed when an image
						// is already cached. A componentResized event will
						// apply the exact card width once Swing lays it out.
						usable =
							Math.max(180, content.getWidth() - margin.left - margin.right - 10);
					}
					usable = Math.min(580, Math.max(1, usable));
					if (usable == fittedWidth[0])
						return;
					ImageIcon scaled = fitEventCardImage(original[0], usable);
					if (scaled == null)
						return;
					fittedWidth[0] = usable;
					preview.setIcon(scaled);
					preview.setText("");
					preview.setMaximumSize(
						new Dimension(Integer.MAX_VALUE, scaled.getIconHeight()));
					preview.setPreferredSize(new Dimension(usable, scaled.getIconHeight()));
					preview.revalidate();
					fitEventCardHeight(card);
					card.revalidate();
					card.repaint();
				};
				card.addComponentListener(new java.awt.event.ComponentAdapter() {
					@Override public void componentResized(java.awt.event.ComponentEvent event)
					{
						fit.run();
					}
				});
				eventImageLoader.load(event, icon -> {
					if (icon != null)
					{
						original[0] = icon;
						fit.run();
					}
					else
					{
						preview.setText("Image unavailable");
						preview.setToolTipText(SwingTextSafety.boxedTooltip(
							"Image preview unavailable. Open the Discord post to view it."));
						preview.setMaximumSize(
							new Dimension(Integer.MAX_VALUE, preview.getPreferredSize().height));
						preview.revalidate();
						card.revalidate();
					}
				});
			}
		}
		boolean inAttendance = trackingEvent(event.id);
		String when = inAttendance ? "ONGOING · TRACKING"
					  : historical
						  ? eventDate(event.start)
						  : EventTimeDisplay.label(event.start, event.end, java.time.Instant.now(),
												   java.time.ZoneId.systemDefault());
		JLabel time = SwingTextSafety.plainLabel(when);
		time.setName("holyEmpireEventTime:" + event.id);
		time.setForeground("HAPPENING NOW".equals(when) ? HolyEmpireTheme.GOLD
														  : HolyEmpireTheme.MUTED);
		time.setFont(time.getFont().deriveFont(time.getFont().getSize2D() + 2f));
		time.setAlignmentX(LEFT_ALIGNMENT);
		details.add(time);
		if (!historical)
		{
			activeEventClocks.put(time, event);
		}
		if (!event.location.isEmpty())
		{
			JTextArea location = readableText("Location: " + event.location);
			location.setName("holyEmpireEventLocation:" + event.id);
			location.setForeground(HolyEmpireTheme.MUTED);
			location.setFont(location.getFont().deriveFont(location.getFont().getSize2D() + 2f));
			// JTextArea defaults can indent text compared to JLabel.
			// Clear both margin and border so Location and date share x=0.
			location.setMargin(new java.awt.Insets(0, 0, 0, 0));
			location.setBorder(BorderFactory.createEmptyBorder());
			fitEventText(location, titleWidth);
			location.setAlignmentX(LEFT_ALIGNMENT);
			details.add(location);
		}
		if (!event.description.isEmpty())
		{
			// All event text stays visible; the user can collapse the card.
			JTextArea description = new JTextArea(event.description);
			description.setName("holyEmpireEventDescription:" + event.id);
			description.setEditable(false);
			description.setToolTipText(null);
			description.setFocusable(false);
			description.setOpaque(false);
			description.setLineWrap(true);
			description.setWrapStyleWord(true);
			description.setFont(
				description.getFont().deriveFont(description.getFont().getSize2D() + 2f));
			fitEventText(description, titleWidth);
			description.setForeground(HolyEmpireTheme.TEXT);
			description.setAlignmentX(LEFT_ALIGNMENT);
			details.add(description);
		}
		// Keep the RSVP total on its own full-width row ABOVE actions, not
		// beside potentially long chapter names.
		if (!historical)
		{
			JPanel countRow = new JPanel(new BorderLayout());
			countRow.setOpaque(false);
			countRow.setAlignmentX(LEFT_ALIGNMENT);
			countRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
			countRow.add(interested, BorderLayout.EAST);
			details.add(Box.createVerticalStrut(5));
			details.add(countRow);
		}
		if (!event.url.isEmpty())
		{
			JButton link = HolyEmpireTheme.button(
				chapter ? "View Discord Post" : "Event Details / RSVP", false);
			link.setName((chapter ? "holyEmpireEventDiscordPost:" : "holyEmpireEventRSVP:") +
						 event.id);
			link.putClientProperty("holyEmpireEventURL", event.url);
			link.setToolTipText(chapter ? "Open the announcement in its chapter's Discord channel"
										: "Open the official Discord scheduled event to RSVP");
			link.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(event.url));
			details.add(Box.createVerticalStrut(5));
			if (chapter)
			{
				JPanel centered = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
				centered.setName("holyEmpireEventDiscordPostRow:" + event.id);
				centered.setOpaque(false);
				centered.setAlignmentX(LEFT_ALIGNMENT);
				centered.setMaximumSize(
					new Dimension(Integer.MAX_VALUE, link.getPreferredSize().height));
				centered.add(link);
				details.add(centered);
			}
			else
			{
				// Keep the official Discord RSVP link and personal bell status
				// side-by-side. The status box never sends an RSVP.
				JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
				actionRow.setName("holyEmpireEventDiscordActionRow:" + event.id);
				actionRow.setOpaque(false);
				actionRow.setAlignmentX(LEFT_ALIGNMENT);
				actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
					link.getPreferredSize().height));
				actionRow.add(link);
				actionRow.add(empireEventBellStatus(
					event.id, event.myRsvp, link.getPreferredSize().height));
				details.add(actionRow);
			}
		}
		if (chapter && !historical && !event.url.isEmpty())
		{
			boolean authenticated = chapterRsvpAuthorized != null &&
									chapterRsvpAuthorized.getAsBoolean() &&
									chapterRsvpHandler != null;
			String currentRsvp = event.myRsvp.isEmpty()
									 ? ""
									 : " · " + ("declined".equals(event.myRsvp) ? "Can't Go"
												: "maybe".equals(event.myRsvp)  ? "Maybe"
																				  : "Going");
			String collapsedLabel = "RSVP" + currentRsvp;
			JButton toggle = HolyEmpireTheme.button(collapsedLabel + " ▼", false);
			toggle.setName("holyEmpireEventRsvpOpen:" + event.id);
			if ("going".equals(event.myRsvp))
			{
				HolyEmpireTheme.accentBorder(toggle, HolyEmpireTheme.RSVP_GOING_EDGE);
			}
			else if ("declined".equals(event.myRsvp))
			{
				HolyEmpireTheme.accentBorder(toggle, HolyEmpireTheme.RSVP_DECLINED_EDGE);
			}
			toggle.setToolTipText("View chapter RSVP choices");
			toggle.setAlignmentX(LEFT_ALIGNMENT);
			toggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
			details.add(Box.createVerticalStrut(6));
			details.add(toggle);
			JPanel actions = new JPanel(new java.awt.GridLayout(2, 2, 3, 3));
			actions.setName("holyEmpireEventRsvpOptions:" + event.id);
			actions.setOpaque(false);
			String[] choices = {"going", "maybe", "declined", "clear"};
			String[] labels = {"Going", "Maybe", "Can't Go", "Clear RSVP"};
			for (int i = 0; i < choices.length; i++)
			{
				final String choice = choices[i];
				JButton action = HolyEmpireTheme.button(labels[i], false);
				action.setName("holyEmpireEventRsvp:" + choice + ":" + event.id);
				action.setEnabled(authenticated);
				action.setToolTipText(authenticated
										  ? "Save your chapter RSVP to Discord and RuneLite"
										  : "Connect your Discord chapter account to RSVP");
				action.addActionListener(e -> {
					if (chapterRsvpHandler != null && chapterRsvpAuthorized != null &&
						chapterRsvpAuthorized.getAsBoolean())
					{
						rsvpResultMessage = "Saving RSVP...";
						for (java.awt.Component item : actions.getComponents())
							item.setEnabled(false);
						toggle.setText("RSVP · Saving...");
						chapterRsvpHandler.accept(event.id, choice);
					}
				});
				actions.add(action);
			}
			actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
			actions.setAlignmentX(LEFT_ALIGNMENT);
			actions.setVisible(false);
			toggle.addActionListener(e -> {
				boolean expanded = !actions.isVisible();
				actions.setVisible(expanded);
				toggle.setText(toggle.getText().replaceFirst(" [▲▼]$", "") +
					(expanded ? " ▲" : " ▼"));
				fitEventCardHeight(card);
				card.revalidate();
				card.repaint();
			});
			details.add(actions);
		}
		// No new event attendance session may start until one hour before its
		// scheduled start. An already running tracker may always be stopped.
		boolean trackingThisEvent = attendanceReport != null && attendanceReport.running &&
			event.id.equals(attendanceReport.eventId);
		boolean attendanceWindow = eventAttendanceWindow(
			event.start, event.end, java.time.Instant.now());
		if (!historical && viewingAttendance() && attendanceStart != null)
		{
			boolean trackingThis = attendanceReport != null && attendanceReport.running &&
								   event.id.equals(attendanceReport.eventId);
			JButton attendance = HolyEmpireTheme.button(
				trackingThis ? "Stop Attendance" : "Track Attendance", false);
			attendance.setName("holyEmpireEventAttendance:" + event.id);
			attendance.setAlignmentX(LEFT_ALIGNMENT);
			attendance.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
			attendance.setEnabled((trackingThis || attendanceWindow) &&
								  (trackingThis || attendanceReport == null || attendanceSaved));
			attendance.setToolTipText(!trackingThis && !attendanceWindow
				? "Attendance tracking becomes available 1 hour before the event starts."
				: "Observe Holy Empire members within 15 tiles of the attendance host; all chapters count");
			attendance.addActionListener(e -> {

				if (trackingThis)
				{
					if (attendanceStop != null)
					{
						rememberAttendanceEventScrollPosition();
						attendanceStop.run();
					}
				}
				else
				{
					// Headless tests cannot display the production modal dialog.
					if (java.awt.GraphicsEnvironment.isHeadless())
					{
						attendanceMinimumMinutes.setValue(10);
						trackedAttendanceEvent = event;
						attendanceStart.start(event.id, event.scope, event.chapter, event.name);
						return;
					}
					JSpinner minimumInput = new JSpinner(attendanceMinimumModel());
					minimumInput.setName("holyEmpireEventAttendanceMinimumDialog");
					JPanel prompt = new JPanel(new BorderLayout(0, 6));
					prompt.add(SwingTextSafety.plainLabel(
						"Minimum observed attendance (0-120 minutes):"), BorderLayout.NORTH);
					prompt.add(minimumInput, BorderLayout.CENTER);
					if (JOptionPane.showConfirmDialog(this, prompt,
						"Start Event Attendance", JOptionPane.OK_CANCEL_OPTION,
						JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION)
						return;
					try { minimumInput.commitEdit(); }
					catch (java.text.ParseException invalid) { return; }
					int minutes = ((Number)minimumInput.getValue()).intValue();
					if (minutes < 0 || minutes > 120) return;
					attendanceMinimumMinutes.setValue(minutes);
					trackedAttendanceEvent = event;
					attendanceStart.start(event.id, event.scope, event.chapter, event.name);
				}
			});
			details.add(Box.createVerticalStrut(5));
			details.add(attendance);
		}
		if (historical && attendanceHistoryAction != null &&
			(viewingAttendance() || viewingEventAttendance()))
		{
			Integer number = eventAttendanceNumbers.get(event.id);
			if (number != null)
			{
				JLabel label = SwingTextSafety.plainLabel("Attendance #" + number);
				label.setName("holyEmpireEventAttendanceNumber:" + event.id);
				label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() + 2f));
				label.setForeground(HolyEmpireTheme.GOLD);
				label.setAlignmentX(LEFT_ALIGNMENT);
				details.add(label);
			}
			JButton copyHistory = HolyEmpireTheme.button("Copy Event Attendance", false);
			copyHistory.setName("holyEmpireEventCopyAttendance:" + event.id);
			copyHistory.setAlignmentX(LEFT_ALIGNMENT);
			copyHistory.addActionListener(e -> attendanceHistoryAction.copy(event.id));
			details.add(Box.createVerticalStrut(5));
			details.add(copyHistory);
		}
		// Refit the cards when RuneLite's sidebar is resized.
		final int[] lastTextWidth = {-1};
		card.addComponentListener(new java.awt.event.ComponentAdapter()
		{
			@Override public void componentResized(java.awt.event.ComponentEvent e)
			{
				Insets insets = card.getInsets();
				int width = card.getWidth() - insets.left - insets.right;
				if (width < 100 || Math.abs(width - lastTextWidth[0]) < 2)
					return;
				lastTextWidth[0] = width;
				fitEventText(name, width);
				for (java.awt.Component child : details.getComponents())
					if (child instanceof JTextArea)
						fitEventText((JTextArea)child, width);
				fitEventCardHeight(card);
			}
		});
		// The expanded panel can grow when Discord imagery loads or RSVP opens.
		details.setVisible(!initiallyCollapsed);
		card.add(details);
		collapse.addActionListener(e -> {
			toggleCollapsedEvent(collapseKey);
			boolean collapsed = collapsedEventIds.contains(collapseKey);
			details.setVisible(!collapsed);
			compactDate.setVisible(collapsed);
			compactFooter.setVisible(collapsed);
			rsvpMark.setVisible(collapsed && !rsvpMark.getText().isEmpty());
			collapse.setText(collapsed ? "Expand ▾" : "Collapse ▴");
			fitEventCardHeight(card);
		});
		fitEventCardHeight(card);
		eventCardCache.put(cacheKey, new CachedEventCard(card, time, signature));
		if (eventCardCache.size() > 200)
		{
			String oldest = eventCardCache.keySet().iterator().next();
			eventCardCache.remove(oldest);
		}
		addAligned(card);
		content.add(Box.createVerticalStrut(8));
	}

	private void showAnnouncements()
	{
		// The achievement spotlight leads the page; announcements follow it.
		// Keep it visible while the official notices load or are unavailable.
		showWomAchievements();
		centeredHeading("Announcements", 18, "holyEmpireAnnouncementsHeading");
		if (publishStatus != null && !publishStatus.isEmpty() && discordSessionReady != null &&
			discordSessionReady.getAsBoolean())
		{
			JTextArea feedback = readableText(publishStatus);
			feedback.setName("holyEmpireAnnouncementManageStatus");
			feedback.setForeground(HolyEmpireTheme.GOLD);
			addAligned(feedback);
		}
		if (communicationsError != null)
		{
			JTextArea unavailable =
				readableText("Feed unavailable. Existing announcements may be outdated.");
			unavailable.setName("holyEmpireAnnouncementsFeedUnavailable");
			unavailable.setForeground(HolyEmpireTheme.CRIMSON);
			addAligned(unavailable);
		}
		if (communications == null)
		{
			String message = communicationsError == null
								 ? "Loading announcements..."
								 : "Empire announcements are temporarily unavailable.";
			addAligned(readableText(message));
			setStatus(message, "Announcements unavailable");
			return;
		}
		if (communications.announcements.isEmpty())
		{
			addAligned(SwingTextSafety.plainLabel("No Empire announcements yet."));
		}
		for (EmpireCommunicationsClient.Notice notice : communications.announcements)
		{
			JPanel card = new JPanel();
			card.setName("holyEmpireAnnouncement:" + notice.id);
			card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
			card.setOpaque(true);
			card.setBackground(HolyEmpireTheme.PANEL);
			card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(HolyEmpireTheme.GOLD_EDGE),
				BorderFactory.createEmptyBorder(7, 8, 7, 8)));
			JTextArea title = new JTextArea(notice.title);
			title.setName("holyEmpireAnnouncementTitle:" + notice.id);
			title.setEditable(false);
			title.setFocusable(false);
			title.setOpaque(false);
			title.setLineWrap(true);
			title.setWrapStyleWord(true);
			title.setMargin(new Insets(0, 0, 0, 0));
			title.setBorder(BorderFactory.createEmptyBorder());
			title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
			title.setForeground(HolyEmpireTheme.GOLD);
			title.setToolTipText(SwingTextSafety.boxedTooltip(notice.title));
			int titleWidth = Math.max(150, content.getWidth() > 0 ? content.getWidth() - 38 : 185);
			int titlePixels = title.getFontMetrics(title.getFont()).stringWidth(notice.title);
			int titleRows = Math.max(2, (titlePixels + titleWidth - 1) / titleWidth + 2);
			title.setRows(titleRows);
			title.setColumns(22);
			title.setMaximumSize(new Dimension(Integer.MAX_VALUE,
				title.getFontMetrics(title.getFont()).getHeight() * titleRows + 10));
			title.setAlignmentX(LEFT_ALIGNMENT);
			card.add(title);
			String date = java.time.format.DateTimeFormatter
							  .ofPattern("MMM d, yyyy · h:mm a z", Locale.getDefault())
							  .withZone(java.time.ZoneId.systemDefault())
							  .format(java.time.Instant.ofEpochSecond(notice.createdAt));
			JLabel when = SwingTextSafety.plainLabel(date);
			when.setForeground(HolyEmpireTheme.MUTED);
			when.setAlignmentX(LEFT_ALIGNMENT);
			card.add(when);
			JTextArea message = new JTextArea(notice.body);
			message.setName("holyEmpireAnnouncementBody:" + notice.id);
			message.setToolTipText(SwingTextSafety.boxedTooltip(notice.body));
			message.setEditable(false);
			message.setFocusable(false);
			message.setOpaque(false);
			message.setLineWrap(true);
			message.setWrapStyleWord(true);
			message.setForeground(HolyEmpireTheme.TEXT);
			message.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
			message.setAlignmentX(LEFT_ALIGNMENT);
			int visibleChars = 22;
			int wrappedLines = 0;
			for (String line : notice.body.split("\n", -1))
				wrappedLines += Math.max(1, (line.length() + visibleChars - 1) / visibleChars);
			message.setRows(Math.max(2, wrappedLines + 3));
			message.setColumns(22);
			message.setMaximumSize(new Dimension(Integer.MAX_VALUE,
				message.getPreferredSize().height + 12));
			card.add(message);
			if (!notice.discordUrl.isEmpty() &&
				EmpireCommunicationsClient.isDiscordPostUrl(notice.discordUrl))
			{
				JButton open = HolyEmpireTheme.button("View Discord Post", false);
				open.setName("holyEmpireAnnouncementDiscordPost:" + notice.id);
				open.setAlignmentX(LEFT_ALIGNMENT);
				open.addActionListener(
					e -> net.runelite.client.util.LinkBrowser.browse(notice.discordUrl));
				card.add(open);
			}
			JLabel author = SwingTextSafety.plainLabel("Posted by " + notice.postedBy);
			author.setName("holyEmpireAnnouncementPoster:" + notice.id);
			author.setFont(author.getFont().deriveFont(Font.PLAIN, 10f));
			author.setForeground(HolyEmpireTheme.MUTED);
			author.setAlignmentX(LEFT_ALIGNMENT);
			author.setBorder(BorderFactory.createEmptyBorder(5, 0, 1, 0));
			card.add(author);
			if (notice.canManage && discordSessionReady != null &&
				discordSessionReady.getAsBoolean())
				addInlineAnnouncementControls(card, notice);
			card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
			addAligned(card);
			content.add(Box.createVerticalStrut(7));
		}
		setStatus("Empire announcements", "Announcements · " + communications.announcements.size());
	}

	private void addInlineAnnouncementControls(JPanel card,
											   EmpireCommunicationsClient.Notice notice)
	{
		JPanel controls = new JPanel();
		controls.setName("holyEmpireAnnouncementControls:" + notice.id);
		controls.setBackground(HolyEmpireTheme.STEEL);
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setAlignmentX(LEFT_ALIGNMENT);
		controls.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(HolyEmpireTheme.GOLD_EDGE),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));
		JLabel heading = SwingTextSafety.plainLabel("Manage Announcement");
		heading.setForeground(HolyEmpireTheme.GOLD);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 12f));
		heading.setAlignmentX(LEFT_ALIGNMENT);
		controls.add(heading);
		JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 5, 0));
		buttons.setOpaque(false);
		JButton edit = HolyEmpireTheme.button("Edit", false);
		edit.setName("holyEmpireAnnouncementEdit:" + notice.id);
		edit.addActionListener(e -> editAnnouncement(notice));
		JButton remove = HolyEmpireTheme.button("Remove", true);
		remove.setName("holyEmpireAnnouncementRemove:" + notice.id);
		remove.addActionListener(e -> removeAnnouncement(notice));
		buttons.add(edit);
		buttons.add(remove);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		controls.add(buttons);
		card.add(Box.createVerticalStrut(6));
		card.add(controls);
	}

	private void showWomAchievements()
	{
		JLabel heading = SwingTextSafety.plainLabel("Today\u0027s Achievements");
		heading.setName("holyEmpireAchievementsHeading");
		heading.setForeground(HolyEmpireTheme.GOLD);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
		heading.setBorder(BorderFactory.createEmptyBorder(7, 3, 5, 3));
		heading.setHorizontalAlignment(SwingConstants.CENTER);
		heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, heading.getPreferredSize().height));
		addAligned(heading);
		// The footer still labels the rolling 24-hour time window.
		addAligned(womRotator);
		content.add(Box.createVerticalStrut(9));
	}

	private static void enforcePublisherBodyLimit(JTextArea field, int cap)
	{
		((javax.swing.text.AbstractDocument)field.getDocument()).setDocumentFilter(
			new javax.swing.text.DocumentFilter() {
				@Override public void insertString(FilterBypass fb, int offset, String text,
					javax.swing.text.AttributeSet attrs) throws javax.swing.text.BadLocationException
				{
					replace(fb, offset, 0, text, attrs);
				}
				@Override public void replace(FilterBypass fb, int offset, int length,
					String text, javax.swing.text.AttributeSet attrs)
					throws javax.swing.text.BadLocationException
				{
					String insert = text == null ? "" : text;
					int space = Math.max(0, cap - fb.getDocument().getLength() + length);
					fb.replace(offset, length, insert.substring(0, Math.min(space, insert.length())), attrs);
				}
			});
	}

	private static void watchPublisherLength(JTextArea field, JLabel counter, int limit)
	{
		counter.setHorizontalAlignment(SwingConstants.RIGHT);
		counter.setForeground(HolyEmpireTheme.MUTED);
		field.getDocument().addDocumentListener(new DocumentListener() {
			private void update()
			{
				int count = field.getDocument().getLength();
				counter.setText(count + " / " + limit + " characters" +
					(count == limit ? " · limit reached" : ""));
				counter.setForeground(count == limit ? HolyEmpireTheme.GOLD : HolyEmpireTheme.MUTED);
			}
			@Override public void insertUpdate(DocumentEvent e) { update(); }
			@Override public void removeUpdate(DocumentEvent e) { update(); }
			@Override public void changedUpdate(DocumentEvent e) { update(); }
		});
	}

	private void addPublisher(String heading, String kind, JTextField title, JTextArea message,
							  String submitLabel)
	{
		JLabel label = SwingTextSafety.plainLabel(heading);
		label.setForeground("emergency".equals(kind) ? HolyEmpireTheme.CRIMSON
													   : HolyEmpireTheme.GOLD);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
		label.setAlignmentX(LEFT_ALIGNMENT);
		addAligned(label);
		title.setName("holyEmpireAdminTitle:" + kind);
		title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		title.setAlignmentX(LEFT_ALIGNMENT);
		addAligned(title);
		message.setName("holyEmpireAdminBody:" + kind);
		message.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, "announcement".equals(kind) ? 130 : 87));
		message.setAlignmentX(LEFT_ALIGNMENT);
		{
			// Every editor scrolls inside a bounded area.
			JPanel editor = new JPanel(new BorderLayout(0, 2));
			editor.setOpaque(false);
			editor.setAlignmentX(LEFT_ALIGNMENT);
			editor.setMaximumSize(new Dimension(Integer.MAX_VALUE, "announcement".equals(kind) ? 170 : 135));
			JScrollPane bounded = new JScrollPane(message, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
												  JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			int editorHeight = "announcement".equals(kind) ? 130 : 96;
			bounded.setPreferredSize(new Dimension(230, editorHeight));
			bounded.setMaximumSize(new Dimension(Integer.MAX_VALUE, editorHeight));
			editor.add(bounded, BorderLayout.CENTER);
			editor.add("announcement".equals(kind) ? announcementCounter :
				"broadcast".equals(kind) ? broadcastCounter : emergencyCounter, BorderLayout.SOUTH);
			addAligned(editor);
		}
		if ("broadcast".equals(kind))
		{
			JLabel recipients = SwingTextSafety.plainLabel("Target Recipients:");
			recipients.setName("holyEmpireBroadcastAudienceLabel");
			recipients.setFont(recipients.getFont().deriveFont(Font.BOLD, 16f));
			recipients.setForeground(HolyEmpireTheme.GOLD);
			recipients.setAlignmentX(LEFT_ALIGNMENT);
			recipients.setBorder(BorderFactory.createEmptyBorder(8, 2, 6, 2));
			addAligned(recipients);
			broadcastAudience.setName("holyEmpireBroadcastAudience");
			broadcastAudience.setToolTipText(
				"Chapter Command: all verified Primarchs and Chapter Masters; "
				+ "All Admins: all chapter administrators; Empire-Wide: all members");
			broadcastAudience.setFont(broadcastAudience.getFont().deriveFont(Font.BOLD, 16f));
			broadcastAudience.setBackground(HolyEmpireTheme.SELECTED);
			broadcastAudience.setForeground(HolyEmpireTheme.TEXT);
			broadcastAudience.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(HolyEmpireTheme.GOLD_EDGE, 2),
				BorderFactory.createEmptyBorder(5, 5, 5, 5)));
			broadcastAudience.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
			broadcastAudience.setAlignmentX(LEFT_ALIGNMENT);
			addAligned(broadcastAudience);
			content.add(Box.createVerticalStrut(6));
		}
		if ("announcement".equals(kind))
		{
			announcementLink.setAlignmentX(LEFT_ALIGNMENT);
			announcementLink.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
			addAligned(announcementLink);
		}
		JButton submit = HolyEmpireTheme.button(submitLabel, "emergency".equals(kind));
		submit.setName("holyEmpireAdminPublish:" + kind);
		submit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
		submit.setAlignmentX(LEFT_ALIGNMENT);
		java.util.function.BooleanSupplier canPublish =
			"announcement".equals(kind) ? ownerSessionReady : broadcastSessionReady;
		submit.setEnabled(canPublish != null && canPublish.getAsBoolean() &&
						  publishAction != null);
		submit.addActionListener(e -> {
			if (publishAction == null || canPublish == null ||
				!canPublish.getAsBoolean())
				return;
			String subject = title.getText().trim();
			String text = message.getText().trim();
			if (subject.isEmpty() || text.isEmpty())
			{
				// Do not leave a persistent validation banner above the tabs.
				if (publishStatus != null)
				{
					publishStatus = null;
					refresh();
				}
				return;
			}
			int limit = "announcement".equals(kind) ? 350 : 200;
			if (text.length() > limit || subject.length() > 72)
			{
				publishStatus = (subject.length() > 72
									 ? "Title exceeds 72 characters."
									 : "Announcement exceeds the " + limit + "-character limit.");
				refresh();
				return;
			}
			if ("emergency".equals(kind) &&
				JOptionPane.showConfirmDialog(
					this,
					"Send an EMERGENCY warning and chatbox broadcast to all "
						+ "online Holy Empire plugin users?",
					"Confirm Empire Emergency", JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
			{
				return;
			}
			publishStatus = "Sending " + heading.toLowerCase(Locale.ROOT) + "...";
			refresh();
			if ("announcement".equals(kind) && publishWithLinkAction != null)
				publishWithLinkAction.send(kind, subject, text, announcementLink.getText().trim());
			else if ("broadcast".equals(kind) && scopedBroadcastAction != null)
				scopedBroadcastAction.send(
					kind, subject, text,
					new String[] {"chapter_command", "all_admins",
								  "empire"}[Math.max(0, broadcastAudience.getSelectedIndex())]);
			else
				publishAction.send(kind, subject, text);
		});
		addAligned(submit);
		// Publication feedback belongs beside the action that produced it,
		// not above the Admin Controls tabs.
		if ("announcement".equals(kind))
			showAdminPublishFeedback();
		content.add(Box.createVerticalStrut(16));
	}

	private void editAnnouncement(EmpireCommunicationsClient.Notice notice)
	{
		if (manageAnnouncementAction == null || !notice.canManage ||
			discordSessionReady == null || !discordSessionReady.getAsBoolean())
			return;
		JTextField title = new JTextField(notice.title);
		JTextArea body = new JTextArea(notice.body, 6, 24);
		body.setLineWrap(true);
		body.setWrapStyleWord(true);
		JTextField url = new JTextField(notice.discordUrl);
		JPanel fields = new JPanel();
		fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
		fields.add(SwingTextSafety.plainLabel("Announcement title"));
		fields.add(title);
		fields.add(SwingTextSafety.plainLabel("Message"));
		fields.add(new JScrollPane(body));
		fields.add(SwingTextSafety.plainLabel("Discord message link (optional)"));
		fields.add(url);
		int result =
			JOptionPane.showConfirmDialog(this, fields, "Edit Empire Announcement",
										  JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION)
			return;
		String subject = title.getText().trim();
		String message = body.getText().trim();
		String link = url.getText().trim();
		if (subject.isEmpty() || message.isEmpty() ||
			(!link.isEmpty() && !EmpireCommunicationsClient.isDiscordPostUrl(link)))
		{
			showPublishStatus("Enter a title and message, plus a valid Discord post link.",
							  () -> true);
			return;
		}
		publishStatus = "Updating announcement...";
		refresh();
		manageAnnouncementAction.send("edit", notice.id, subject, message, link);
	}

	private void removeAnnouncement(EmpireCommunicationsClient.Notice notice)
	{
		if (manageAnnouncementAction == null || !notice.canManage ||
			discordSessionReady == null || !discordSessionReady.getAsBoolean())
			return;
		if (JOptionPane.showConfirmDialog(
				this, "Remove announcement: " + notice.title + " for every plugin user?",
				"Confirm Announcement Removal", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
			return;
		publishStatus = "Removing announcement...";
		refresh();
		manageAnnouncementAction.send("remove", notice.id, "", "", "");
	}

	private void showEmergencyManagement()
	{
		if (communications == null || communications.emergency == null)
			return;
		heading("Active Emergency", 16);
		JTextArea warning =
			readableText(communications.emergency.title + ": " + communications.emergency.body);
		warning.setName("holyEmpireActiveEmergency");
		warning.setForeground(HolyEmpireTheme.CRIMSON);
		addAligned(warning);
		// Full-width vertically stacked actions avoid truncated text in RuneLite's
		// narrow sidebar, even when large fonts or scaling are enabled.
		JPanel actions = new JPanel();
		actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
		actions.setOpaque(false);
		actions.setAlignmentX(LEFT_ALIGNMENT);
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
		boolean enabled = broadcastSessionReady != null &&
						  broadcastSessionReady.getAsBoolean() && emergencyAction != null;
		JButton end = HolyEmpireTheme.button("End Emergency", true);
		end.setName("holyEmpireEndEmergency");
		end.setEnabled(enabled);
		end.addActionListener(e -> {
			if (JOptionPane.showConfirmDialog(
					this, "End this emergency and broadcast that it is over?", "End Emergency",
					JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
				return;
			publishStatus = "Ending emergency and notifying members...";
			refresh();
			emergencyAction.accept("end");
		});
		JButton cancel = HolyEmpireTheme.button("Cancel Quietly", false);
		cancel.setName("holyEmpireCancelEmergency");
		cancel.setEnabled(enabled);
		cancel.addActionListener(e -> {
			if (JOptionPane.showConfirmDialog(
					this, "Cancel this emergency without notifying members?", "Cancel Emergency",
					JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
				return;
			publishStatus = "Quietly cancelling emergency...";
			refresh();
			emergencyAction.accept("cancel");
		});
		end.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		cancel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
		end.setAlignmentX(LEFT_ALIGNMENT);
		cancel.setAlignmentX(LEFT_ALIGNMENT);
		actions.add(end);
		actions.add(Box.createVerticalStrut(6));
		actions.add(cancel);
		addAligned(actions);
		content.add(Box.createVerticalStrut(12));
	}

	private void showAnnouncementManagement()
	{
		JLabel heading = SwingTextSafety.plainLabel("Manage Announcements");
		heading.setName("holyEmpireManageAnnouncementsHeading");
		heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
		heading.setForeground(HolyEmpireTheme.GOLD);
		heading.setHorizontalAlignment(SwingConstants.CENTER);
		heading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
		heading.setBorder(BorderFactory.createEmptyBorder(9, 3, 5, 3));
		addAligned(heading);
		if (communications == null || communications.announcements.isEmpty())
		{
			addAligned(SwingTextSafety.plainLabel("No announcements to edit or remove."));
			return;
		}
		boolean ready = ownerSessionReady != null &&
						ownerSessionReady.getAsBoolean() && manageAnnouncementAction != null;
		for (EmpireCommunicationsClient.Notice notice : communications.announcements)
		{
			JPanel group = new JPanel();
			group.setOpaque(false);
			group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
			group.setAlignmentX(LEFT_ALIGNMENT);
			group.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
			JLabel name = SwingTextSafety.plainLabel(notice.title);
			name.setForeground(HolyEmpireTheme.TEXT);
			name.setAlignmentX(CENTER_ALIGNMENT);
			name.setHorizontalAlignment(SwingConstants.CENTER);
			name.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
			group.add(name);
			JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 5, 0));
			buttons.setOpaque(false);
			JButton edit = HolyEmpireTheme.button("Edit", false);
			edit.setName("holyEmpireAnnouncementEdit:" + notice.id);
			edit.setEnabled(ready);
			edit.addActionListener(e -> editAnnouncement(notice));
			JButton remove = HolyEmpireTheme.button("Remove", false);
			remove.setName("holyEmpireAnnouncementRemove:" + notice.id);
			remove.setEnabled(ready);
			remove.addActionListener(e -> removeAnnouncement(notice));
			buttons.add(edit);
			buttons.add(remove);
			group.add(buttons);
			addAligned(group);
			content.add(Box.createVerticalStrut(7));
		}
	}

	static boolean meetsAttendanceMinimum(HolyEmpireAttendanceTracker.Entry entry, int minutes)
	{
		return entry.ticks * HolyEmpireAttendanceTracker.GAME_TICK_MILLIS >= (long)Math.max(0, minutes) * 60_000L;
	}

	static SpinnerNumberModel attendanceMinimumModel()
	{
		// SpinnerNumberModel normally permits out-of-range setValue calls.
		// Validate the model itself, including typed editor submissions.
		return new SpinnerNumberModel(10, 0, 120, 1)
		{
			@Override
			public void setValue(Object value)
			{
				if (!(value instanceof Number))
					throw new IllegalArgumentException("Attendance minutes must be numeric");
				double parsed = ((Number) value).doubleValue();
				if (!Double.isFinite(parsed) || parsed < 0 || parsed > 120 ||
					parsed != Math.rint(parsed))
					throw new IllegalArgumentException("Attendance minutes must be between 0 and 120");
				super.setValue((int)parsed);
			}
		};
	}

	private int attendanceMinimum()
	{
		try
		{
			attendanceMinimumMinutes.commitEdit();
		}
		catch (java.text.ParseException invalid)
		{
			// Keep the last valid value rather than accepting an invalid duration.
		}
		return ((Number)attendanceMinimumMinutes.getValue()).intValue();
	}

	private void showAttendanceMinimum()
	{
		if (!attendanceMinimumInitialized)
		{
			attendanceMinimumInitialized = true;
			attendanceMinimumMinutes.addChangeListener(e -> { if (attendanceReport != null) refresh(); });
		}
		JLabel title = SwingTextSafety.plainLabel("Minimum time present (minutes)");
		title.setName("holyEmpireAttendanceMinimumLabel");
		addAligned(title);
		attendanceMinimumMinutes.setName("holyEmpireAttendanceMinimumMinutes");
		attendanceMinimumMinutes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		attendanceMinimumMinutes.setToolTipText("Enter any whole number from 0 to 120 minutes. Zero disables the requirement; only observed game ticks count.");
		addAligned(attendanceMinimumMinutes);
	}

	private void showAttendanceControls()
	{
		heading("Event Attendance", 18);
		JTextArea note =
			readableText("Manual proximity tracking · 15 tiles · all verified chapters");
		note.setForeground(HolyEmpireTheme.MUTED);
		note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 55));
		addAligned(note);
		content.add(Box.createVerticalStrut(7));
		showAttendanceMinimum();
		content.add(Box.createVerticalStrut(7));
		if (attendanceReport == null || !attendanceReport.running)
		{
			heading("New attendance session", 13);
			attendanceTitle.setName("holyEmpireAttendanceManualTitle");
			attendanceTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
			addAligned(attendanceTitle);
			JButton begin = HolyEmpireTheme.button("Start Attendance", false);
			begin.setName("holyEmpireAttendanceStartManual");
			begin.setEnabled(attendanceStart != null && viewingAttendance() && (attendanceReport == null || !attendanceReport.running));
			begin.addActionListener(e -> {
				String title = attendanceTitle.getText().trim();
				if (title.isEmpty())
				{
					attendanceTitleError = "Please name the event before starting attendance.";
					refresh();
					return;
				}
				if (attendanceStart != null)
				{
					attendanceTitleError = null;
					attendanceStart.start("manual:" + java.util.UUID.randomUUID(), "manual", "",
										  title);
				}
			});
			addAligned(begin);
			if (attendanceTitleError != null)
			{
				JTextArea warning = readableText(attendanceTitleError);
				warning.setName("holyEmpireAttendanceNameError");
				warning.setForeground(HolyEmpireTheme.CRIMSON);
				addAligned(warning);
			}
		}
		showAttendanceReport();
	}

	private void showAttendanceReport()
	{
		HolyEmpireAttendanceTracker.Report report = attendanceReport;
		if (report == null)
		{
			// No report yet: place any start error below the start control.
			if (attendanceArchiveStatus != null && !attendanceArchiveStatus.isEmpty())
				addAttendanceArchiveFeedback();
			return;
		}
		content.add(Box.createVerticalStrut(8));
		heading(report.title, 14);
		int minimum = attendanceMinimum();
		HolyEmpireAttendanceCumulative.Previous prior =
			attendancePrior != null && attendancePrior.eventId.equals(report.eventId)
				? attendancePrior : null;
		java.util.List<HolyEmpireAttendanceCumulative.Member> members =
			HolyEmpireAttendanceCumulative.forDisplay(prior, report);
		int qualified = 0;
		long totalSeconds = 0;
		for (HolyEmpireAttendanceCumulative.Member item : members)
		{
			if (item.meets(minimum)) qualified++;
			totalSeconds += item.seconds();
		}
		if (prior != null)
		{
			JTextArea continuing = readableText("Continuing attendance #" +
				prior.number + " · " + prior.sourceSessions +
				" archived session(s) + current observations. Final totals merge on Discord.");
			continuing.setName("holyEmpireAttendanceCumulative");
			continuing.setForeground(HolyEmpireTheme.SILVER);
			continuing.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
			addAligned(continuing);
		}
		JTextArea summary =
			readableText((report.running	? report.paused ? "PAUSED" : "TRACKING"
						  : attendanceSaved ? "SAVED"
											: archiveStatusLabel(attendanceArchiveStatus)) +
						 " · " + qualified + " met minimum · " + (members.size() - qualified) + " below minimum · " +
						 totalSeconds + " combined observed seconds");
		summary.setName("holyEmpireAttendanceSummary");
		summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
		addAligned(summary);
		JButton action = HolyEmpireTheme.button(
			report.running ? "Stop and Archive Attendance" : "Save Attendance Report", false);
		// Don't let Swing move the scroll pane when the clicked button
		// disappears after a successful asynchronous archive.
		action.setRequestFocusEnabled(false);
		action.setName(report.running ? "holyEmpireAttendanceStop" : "holyEmpireAttendanceSave");
		// An archived report remains selectable: the Save action returns an
		// explicit "already archived" success rather than appearing broken.
		action.setEnabled(report.running ? attendanceStop != null
										 : attendanceSave != null &&
											   (viewingAttendance() || viewingEventAttendance()));
		action.addActionListener(e -> {
			rememberAttendanceEventScrollPosition();
			if (report.running && attendanceStop != null)
				attendanceStop.run();
			else if (!report.running && attendanceSave != null)
				attendanceSave.run();
		});
		addAligned(action);
		if (report.running && attendancePause != null)
		{
			JButton pause = HolyEmpireTheme.button(
				report.paused ? "Resume Attendance" : "Pause Attendance", false);
			pause.setName(report.paused ? "holyEmpireAttendanceResume"
										: "holyEmpireAttendancePause");
			pause.setEnabled(attendancePause != null);
			pause.addActionListener(e -> attendancePause.run());
			addAligned(pause);
		}
		// Save/automatic-retry feedback appears immediately under Save,
		// or under the active controls before the member list.
		if (attendanceArchiveStatus != null && !attendanceArchiveStatus.isEmpty())
			addAttendanceArchiveFeedback();
		int shown = 0;
		for (int section = 0; section < 2; section++)
		{
			boolean met = section == 0;
			int count = met ? qualified : members.size() - qualified;
			heading((met ? "Met minimum" : "Below minimum") + " (" + count + ")", 13);
			String chapterName = "";
			for (HolyEmpireAttendanceCumulative.Member entry : members)
			{
				if (entry.meets(minimum) != met) continue;
				if (shown++ >= 100)
				{
					addAligned(SwingTextSafety.plainLabel("More attendees included in the saved report."));
					break;
				}
				if (!chapterName.equals(entry.chapter))
				{
					chapterName = entry.chapter;
					heading(chapterName, 12);
				}
				JLabel line = SwingTextSafety.plainLabel(entry.name + " · " +
					entry.seconds() / 60 + "m " + entry.seconds() % 60 + "s");
				line.setName("holyEmpireAttendanceMember:" + entry.name);
				addAligned(line);
			}
			if (shown >= 100) break;
		}
		if (!report.running)
		{
			JLabel id = SwingTextSafety.plainLabel(attendanceNumber > 0
													   ? "Attendance #" + attendanceNumber
													   : "Attendance report pending archive");
			id.setName("holyEmpireAttendanceReportId");
			id.setForeground(HolyEmpireTheme.MUTED);
			addAligned(id);
			if (attendanceNumber > 0)
			{
				JButton copyId = HolyEmpireTheme.button("Copy Report Number", false);
				copyId.setName("holyEmpireAttendanceCopyReportNumber");
				copyId.addActionListener(
					e -> copyAttendanceText(Integer.toString(attendanceNumber)));
				addAligned(copyId);
			}
			JButton copyList = HolyEmpireTheme.button(
					prior == null ? "Copy List" : "Copy Current Session", false);
			copyList.setName("holyEmpireAttendanceCopyList");
			copyList.addActionListener(
				e
				-> copyAttendanceList(
					HolyEmpireAttendanceReportClient.formatLocal(report, attendanceNumber, attendanceMinimum())));
			addAligned(copyList);
			if (attendanceCopyStatus != null && !attendanceCopyStatus.isEmpty())
			{
				JTextArea copied = readableText(attendanceCopyStatus);
				copied.setName("holyEmpireAttendanceCopyStatus");
				copied.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
				addAligned(copied);
			}
		}
	}

	static String archiveStatusLabel(String feedback)
	{
		// Do not call a successful local checkpoint a Discord archive.
		if (feedback != null)
		{
			String text = feedback.toLowerCase(Locale.ROOT);
			if (text.contains("queued for the private discord archive") ||
				text.contains("saving to the private discord archive") ||
				text.contains("automatic archive retry"))
				return "QUEUED FOR ARCHIVE";
			if (text.contains("could not be saved locally") ||
				text.contains("could not persist attendance locally"))
				return "SAVE FAILED";
			if (text.contains("saved locally"))
				return "SAVED LOCALLY · AWAITING ARCHIVE";
		}
		return "NOT YET ARCHIVED";
	}

	private void addAttendanceArchiveFeedback()
	{
		JTextArea feedback = readableText(attendanceArchiveStatus);
		feedback.setName("holyEmpireAttendanceStatus");
		feedback.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		addAligned(feedback);
	}

	private void showAdminPublishFeedback()
	{
		if (publishStatus == null || publishStatus.isEmpty())
			return;
		JTextArea feedback = readableText(publishStatus);
		feedback.setName("holyEmpireAdminPublishStatus");
		feedback.setForeground(HolyEmpireTheme.SILVER);
		feedback.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
		addAligned(feedback);
	}

	private void showAdminControls()
	{
		if (!viewingEmpireOwner() && !viewingAttendance())
		{
			showingAdmin = false;
			showHome();
			return;
		}
		heading("Admin Controls", 17);
		adminSections.setAlignmentX(LEFT_ALIGNMENT);
		boolean attendanceAllowed = viewingAttendance();
		boolean announcementAllowed = viewingEmpireOwner();
		boolean broadcastsAllowed = announcementAllowed && viewingBroadcasts();
		// BoxLayout excludes hidden components from its preferred height.
		// GridLayout allocated rows even for invisible tabs and left a huge
		// gap above the sole Attendance control during authorization refreshes.
		// Keep section controls present, but do not reserve space for hidden tabs.
		adminAttendanceTab.setVisible(attendanceAllowed);
		adminAnnouncementsTab.setVisible(announcementAllowed);
		adminBroadcastsTab.setVisible(broadcastsAllowed);
		int available = (attendanceAllowed ? 1 : 0) + (announcementAllowed ? 1 : 0) +
						(broadcastsAllowed ? 1 : 0);
		adminSections.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, available * 38 + Math.max(0, available - 1) * 6));
		if (showingAdminAttendance && !attendanceAllowed)
		{
			showingAdminAttendance = false;
			showingAdminAnnouncements = announcementAllowed;
		}
		if (!showingAdminAttendance && showingAdminAnnouncements && !announcementAllowed)
		{
			showingAdminAttendance = attendanceAllowed;
			showingAdminAnnouncements = false;
		}
		if (!showingAdminAttendance && !showingAdminAnnouncements && !broadcastsAllowed)
		{
			showingAdminAnnouncements = announcementAllowed;
			showingAdminAttendance = !announcementAllowed && attendanceAllowed;
		}
		adminBroadcastsTab.setSelected(broadcastsAllowed && !showingAdminAttendance &&
									   !showingAdminAnnouncements);
		adminAnnouncementsTab.setSelected(announcementAllowed && !showingAdminAttendance &&
										  showingAdminAnnouncements);
		adminAttendanceTab.setSelected(attendanceAllowed && showingAdminAttendance);
		addAligned(adminSections);
		content.add(Box.createVerticalStrut(11));
		if (showingAdminAttendance)
		{
			showAttendanceControls();
		}
		else if (showingAdminAnnouncements)
		{
			heading("Announcements", 18);
			addPublisher("Post Announcement", "announcement", announcementTitle, announcementBody,
						 "Publish Announcement");
		}
		else
		{
			heading("Broadcasts", 18);
			addPublisher("Chatbox Broadcast", "broadcast", broadcastTitle, broadcastBody,
						 "Broadcast to Plugin Users");
			addPublisher("Emergency Announcement", "emergency", emergencyTitle, emergencyBody,
						 "Send Emergency Warning");
			showEmergencyManagement();
			// Preserve broadcast and emergency result messages on this subtab.
			showAdminPublishFeedback();
		}
		setStatus("Verified officer communications", showingAdminAttendance ? "Attendance controls"
													 : showingAdminAnnouncements
														 ? "Announcement controls"
														 : "Broadcast controls");
	}

	private void showBans()
	{
		if (!viewingOfficer())
		{
			clearPrivateBanDirectory();
			showHome();
			return;
		}
		if (!viewingChapterBanLists())
		{
			heading("Global Ban Search", 17);
			JTextField query = new JTextField();
			query.setName("holyEmpireGlobalBanSearch");
			query.putClientProperty("JTextField.placeholderText", "Search the global ban list");
			query.setEnabled(false);
			HolyEmpireTheme.styleSearch(query);
			query.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
			addAligned(query);
			addAligned(readableText("Chapter Admins can search the global ban index. Individual "
									+ "chapter ban lists, full exports, and ban synchronization "
									+ "are restricted. A server-authorized search-only connection "
									+ "is required; individual chapter records are never loaded."));
			setStatus("Chapter Admin",
					  "Global ban search only");
			return;
		}
		JButton refreshBans = HolyEmpireTheme.button(
			banDirectoryLoading ? "Loading bans..." : "Refresh ban list", true);
		refreshBans.setName("holyEmpireBanRefresh");
		refreshBans.setEnabled(!banDirectoryLoading);
		refreshBans.setAlignmentX(LEFT_ALIGNMENT);
		refreshBans.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		refreshBans.addActionListener(e -> requestBanDirectory(true));
		addAligned(refreshBans);
		content.add(Box.createVerticalStrut(5));
		if (!banCacheFresh())
		{
			addAligned(new JLabel(banDirectoryLoading ? "Loading authorized ban lists..."
													  : "Private ban list not loaded."));
			if (banDirectoryError != null)
			{
				JTextArea error = new JTextArea(banDirectoryError);
				error.setEditable(false);
				error.setOpaque(false);
				error.setLineWrap(true);
				error.setWrapStyleWord(true);
				error.setForeground(new Color(220, 155, 130));
				error.setMaximumSize(new Dimension(Integer.MAX_VALUE, 85));
				addAligned(error);
			}
			setStatus("Authorized officer access required", "Private ban list unavailable");
			return;
		}
		String search = banSearch.getText().trim();
		if (!search.isEmpty())
		{
			List<FederationBanDirectoryClient.Entry> matches = filteredBans(null, search);
			heading("Combined results · " + matches.size(), 12);
			if (matches.isEmpty())
			{
				addAligned(new JLabel("No matches in synchronized ban lists."));
			}
			for (int i = 0; i < Math.min(matches.size(), SEARCH_PREVIEW_LIMIT); i++)
			{
				addBanEntry(matches.get(i), true);
			}
			if (matches.size() > SEARCH_PREVIEW_LIMIT)
			{
				addAligned(
					new JLabel("Showing " + SEARCH_PREVIEW_LIMIT + "; refine search for more."));
			}
		}
		else
		{
			heading("Combined chapter ban lists", 15);
			for (HolyEmpireChapterCatalog.Chapter info : HolyEmpireChapterCatalog.ORDERED)
			{
				final String chapter = info.name;
				FederationBanDirectoryClient.Chapter snapshot = banDirectory.chapters.get(chapter);
				int count = cachedBanCounts.getOrDefault(chapter, 0);
				JButton chapterButton = HolyEmpireTheme.button("", true);
				chapterButton.setName("holyEmpireBanChapter:" + chapter);
				chapterButton.setLayout(new BorderLayout(7, 0));
				chapterButton.setFocusPainted(false);
				chapterButton.setPreferredSize(new Dimension(210, 58));
				chapterButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
				chapterButton.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(HolyEmpireTheme.GOLD_EDGE),
					BorderFactory.createEmptyBorder(6, 7, 6, 7)));
				chapterButton.add(
					new JLabel(HolyEmpireAssets.chapterIcon(info.iconKey, info.iconSha256)),
					BorderLayout.WEST);
				JPanel desc = new JPanel();
				desc.setOpaque(false);
				desc.setLayout(new BoxLayout(desc, BoxLayout.Y_AXIS));
				JLabel name = SwingTextSafety.plainLabel(chapter);
				name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
				JLabel secondary = new JLabel(snapshot == null ? "Not synchronized yet"
											  : count == 0	   ? "Synchronized - No Bans"
															   : (count + " synchronized bans"));
				secondary.setName("holyEmpireBanChapterStatus:" + chapter);
				secondary.setFont(secondary.getFont().deriveFont(10f));
				secondary.setForeground(HolyEmpireTheme.MUTED);
				name.setForeground(HolyEmpireTheme.TEXT);
				desc.add(name);
				desc.add(secondary);
				chapterButton.add(desc, BorderLayout.CENTER);
				JLabel total = new JLabel(snapshot == null ? "—" : Integer.toString(count));
				total.setName("holyEmpireBanChapterCount:" + chapter);
				total.setForeground(HolyEmpireTheme.GOLD);
				chapterButton.add(total, BorderLayout.EAST);
				chapterButton.addActionListener(e -> {
					selectedBanChapter = chapter;
					banChapterSearch.setText("");
					refresh();
				});
				addAligned(chapterButton);
				content.add(Box.createVerticalStrut(5));
			}
		}
		String full = banDirectory.entries.size() + " entries from " +
					  banDirectory.chapters.size() +
					  " synchronized chapters. Private cache expires in 5 minutes.";
		setStatus(full, banDirectory.entries.size() + " synchronized bans · " +
							banDirectory.chapters.size() + " chapters");
	}

	private void showBanChapter()
	{
		if (!viewingChapterBanLists())
		{
			selectedBanChapter = null;
			showBans();
			return;
		}
		if (!banCacheFresh())
		{
			showBans();
			return;
		}
		String chapter = selectedBanChapter;
		HolyEmpireChapterCatalog.Chapter info = HolyEmpireChapterCatalog.get(chapter);
		banChapterTitle.setText(chapter);
		banChapterTitle.setIcon(
			info == null ? null : HolyEmpireAssets.chapterIcon(info.iconKey, info.iconSha256));
		FederationBanDirectoryClient.Chapter capture = banDirectory.chapters.get(chapter);
		if (capture == null)
		{
			heading("Not synchronized", 12);
			addAligned(readableText("This chapter has not uploaded a ban list yet."));
			setStatus("No synchronized ban list for " + chapter, "Not synchronized");
			return;
		}
		List<FederationBanDirectoryClient.Entry> entries =
			filteredBans(chapter, banChapterSearch.getText());
		int total = cachedBanCounts.getOrDefault(chapter, 0);
		heading("Ban list · " + entries.size() + " / " + total, 12);
		if (entries.isEmpty())
		{
			addAligned(new JLabel(total == 0 && banChapterSearch.getText().isEmpty()
									  ? "Synchronized - No Bans"
									  : "No matching bans."));
		}
		for (int i = 0; i < Math.min(entries.size(), SEARCH_PREVIEW_LIMIT); i++)
		{
			addBanEntry(entries.get(i), false);
		}
		if (entries.size() > SEARCH_PREVIEW_LIMIT)
		{
			addAligned(new JLabel("Showing " + SEARCH_PREVIEW_LIMIT + "; refine search for more."));
		}
		setStatus(chapter + " · revision " + capture.revision + " · authorized private cache",
				  chapter + " · " + total + " bans");
	}

	/** Give all Holy Empire tooltips a consistent, boxed multiline format. */
	private static void formatHolyEmpireTooltips(java.awt.Component root)
	{
		if (root instanceof JComponent)
		{
			JComponent control = (JComponent)root;
			String tooltip = control.getToolTipText();
			if (tooltip != null && !tooltip.isEmpty() &&
				!tooltip.startsWith("<html><table width='280'"))
				control.setToolTipText(SwingTextSafety.boxedTooltip(tooltip));
		}
		if (root instanceof java.awt.Container)
			for (java.awt.Component child : ((java.awt.Container)root).getComponents())
				formatHolyEmpireTooltips(child);
	}

	private void refresh()
	{
		// The Events page is rebuilt when a request starts and when either feed
		// completes. Preserve the user's viewport between those rebuilds.
		// This is deliberately scoped to Events; switching tabs can scroll
		// normally, and the user can still scroll while requests are running.
		final JViewport viewport =
			showingEvents ? (JViewport)SwingUtilities.getAncestorOfClass(JViewport.class, this)
						  : null;
		final Point eventScrollPosition = attendanceArchiveScrollPosition != null && showingEvents
			? new Point(attendanceArchiveScrollPosition)
			: viewport == null ? null : viewport.getViewPosition();
		content.removeAll();
		updateEmergencyTickerVisibility();
		boolean unverified = !viewingMember();
		if (!viewingChapterBanLists() && banDirectory != null)
		{
			clearPrivateBanDirectory();
			if (viewingOfficer())
				showingBans = true;
		}
		if (!viewingOfficer() && showingBans)
		{
			clearPrivateBanDirectory();
		}
		navigationTabs.setVisible(!unverified);
		boolean showOfficerTab = viewingOfficer() && !unverified;
		boolean showOwnerTab = (viewingEmpireOwner() || viewingAttendance()) && !unverified;
		banListTab.setVisible(showOfficerTab);
		adminTab.setVisible(showOwnerTab);
		if (!showOfficerTab && showingBans)
		{
			showingBans = false;
			selectedBanChapter = null;
			showingAnnouncements = true;
		}
		if (!showOwnerTab && showingAdmin)
		{
			showingAdmin = false;
			showingAnnouncements = true;
		}
		// Announcement first, then Events | Roster, then Admin | Ban List.
		GridBagLayout tabGrid = (GridBagLayout)navigationTabs.getLayout();
		GridBagConstraints announcementsLayout = new GridBagConstraints();
		announcementsLayout.gridx = 0;
		announcementsLayout.gridy = 0;
		announcementsLayout.gridwidth = 2;
		announcementsLayout.weightx = 1.0;
		announcementsLayout.fill = GridBagConstraints.HORIZONTAL;
		announcementsLayout.insets = new Insets(0, 0, 3, 0);
		tabGrid.setConstraints(announcementsTab, announcementsLayout);
		JToggleButton[] primary = {eventsTab, rosterTab};
		for (int i = 0; i < primary.length; i++)
		{
			GridBagConstraints g = new GridBagConstraints();
			g.gridx = i;
			g.gridy = 1;
			g.weightx = 1.0;
			g.fill = GridBagConstraints.HORIZONTAL;
			g.insets = new Insets(0, i == 0 ? 0 : 2, 2, i == 0 ? 2 : 0);
			tabGrid.setConstraints(primary[i], g);
		}
		GridBagConstraints adminLayout = new GridBagConstraints();
		adminLayout.gridx = 0;
		adminLayout.gridy = 2;
		adminLayout.weightx = 1.0;
		adminLayout.fill = GridBagConstraints.HORIZONTAL;
		adminLayout.insets = new Insets(1, 0, 2, 2);
		tabGrid.setConstraints(adminTab, adminLayout);
		GridBagConstraints bansLayout = new GridBagConstraints();
		bansLayout.gridx = 1;
		bansLayout.gridy = 2;
		bansLayout.weightx = 1.0;
		bansLayout.fill = GridBagConstraints.HORIZONTAL;
		bansLayout.insets = new Insets(1, 2, 2, 0);
		tabGrid.setConstraints(banListTab, bansLayout);
		// The sole verification control is located below every navigation tab.
		boolean showVerify = !unverified && discordConnectAction != null &&
							 (discordSessionReady == null || !discordSessionReady.getAsBoolean());
		discordVerifyButton.setVisible(showVerify);
		GridBagConstraints verificationLayout = new GridBagConstraints();
		verificationLayout.gridx = 0;
		verificationLayout.gridy = 3;
		verificationLayout.gridwidth = 2;
		verificationLayout.weightx = 1.0;
		verificationLayout.fill = GridBagConstraints.HORIZONTAL;
		verificationLayout.insets = new Insets(2, 0, 0, 0);
		tabGrid.setConstraints(discordVerifyButton, verificationLayout);
		navigationTabs.revalidate();
		rosterTab.setSelected(!showingBans && !showingEvents && !showingAnnouncements &&
							  !showingAdmin);
		eventsTab.setSelected(showingEvents);
		announcementsTab.setSelected(showingAnnouncements);
		banListTab.setSelected(showingBans);
		adminTab.setSelected(showingAdmin);
		String selectedHeader = unverified												? "status"
								: showingEvents || showingAnnouncements || showingAdmin ? "events"
								: showingBans ? (selectedBanChapter == null ? "bans" : "banChapter")
								: selectedChapter == null ? "home"
														  : "chapter";
		((CardLayout)headerCards.getLayout()).show(headerCards, selectedHeader);
		// Keep header height limited to the active tab, rather than the deepest view.
		int headerIndex = unverified												? 1
						  : (showingEvents || showingAnnouncements || showingAdmin) ? 5
						  : showingBans				? (selectedBanChapter == null ? 3 : 4)
						  : selectedChapter == null ? 0
													: 2;
		int activeHeight = headerCards.getComponent(headerIndex).getPreferredSize().height;
		headerCards.setPreferredSize(new Dimension(0, activeHeight));
		// While membership is being checked, the main content already contains
		// the loading text. Showing the footer would duplicate that message.
		status.setVisible(viewingMember() && !denied  );
		if (unverified)
		{
			showUnavailable();
		}
		else if (showingEvents)
		{
			showEvents();
		}
		else if (showingAnnouncements)
		{
			showAnnouncements();
		}
		else if (showingAdmin)
		{
			showAdminControls();
		}
		else if (showingBans && selectedBanChapter != null)
		{
			showBanChapter();
		}
		else if (showingBans)
		{
			showBans();
		}
		else if (selectedChapter == null)
		{
			showHome();
		}
		else
		{
			showChapter();
		}
		formatHolyEmpireTooltips(this);
		content.revalidate();
		content.repaint();
		revalidate();
		repaint();
		if (eventScrollPosition != null)
		{
			SwingUtilities.invokeLater(() -> {
				if (!showingEvents || !isDescendingFromViewport(viewport))
				{
					return;
				}
				int maxX =
					Math.max(0, viewport.getViewSize().width - viewport.getExtentSize().width);
				int maxY =
					Math.max(0, viewport.getViewSize().height - viewport.getExtentSize().height);
				viewport.setViewPosition(new Point(Math.min(eventScrollPosition.x, maxX),
												   Math.min(eventScrollPosition.y, maxY)));
			});
		}
	}

	private boolean isDescendingFromViewport(JViewport viewport)
	{
		return SwingUtilities.getAncestorOfClass(JViewport.class, this) == viewport;
	}
}
