/*
 * Copyright (c) 2026, Holy Empire
 * All rights reserved.
 *
 * Licensed under the BSD 2-Clause License. See LICENSE.
 */

package org.holyempire.federation;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import net.runelite.client.ui.PluginPanel;

/** Holy Empire home screen and one-level-deep chapter rosters. No nested scroll panes. */
public class FederationPanel extends PluginPanel
{
	private final FederationBanRegistry banRegistry = new FederationBanRegistry();
	private Set<String> publicBanHashes = Collections.emptySet();
	private Runnable officerSettingsOpener;
	private java.util.function.Consumer<Boolean> banDirectoryLoader;
	private volatile boolean officerSettingsRequested;
	private final JToggleButton rosterTab = HolyEmpireTheme.tab("Roster", false);
	private final JToggleButton banListTab = HolyEmpireTheme.tab("Ban List", true);
	private final JPanel navigationTabs = new JPanel(new GridBagLayout());
	private final JTextField banSearch = new JTextField();
	private final JTextField banChapterSearch = new JTextField();
	private final JLabel banChapterTitle = new JLabel();
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

	void setOfficerSettingsOpener(Runnable opener)
	{
		officerSettingsOpener = opener;
	}

	void setBanDirectoryLoader(java.util.function.Consumer<Boolean> callback)
	{
		banDirectoryLoader = callback;
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
		privateCacheTimer.stop();
		searchDebounceTimer.stop();
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
				clearPrivateBanDirectory();
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
	private final JLabel chapterTitle = new JLabel();
	private final JLabel status = new JLabel("Log in to load Holy Empire.");
	private volatile boolean verified = false;
	private boolean denied = false;
	private boolean previewUnverifiedScreen = false;
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
		empireBanner.setName("holyEmpireBrandHeader");
		top.add(empireBanner);
		rosterTab.setName("holyEmpireRosterTab");
		banListTab.setName("holyEmpireBanListTab");
		ButtonGroup group = new ButtonGroup();
		group.add(rosterTab);
		group.add(banListTab);
		rosterTab.setSelected(true);
		rosterTab.addActionListener(e -> {
			showingBans = false;
			selectedBanChapter = null;
			refresh();
		});
		banListTab.addActionListener(e -> {
			if (!officerSettingsEligible || !verified)
			{
				return;
			}
			showingBans = true;
			selectedChapter = null;
			refresh();
			requestBanDirectory(false);
		});
		navigationTabs.setName("holyEmpireNavigationTabs");
		navigationTabs.setBackground(HolyEmpireTheme.STEEL);
		navigationTabs.setBorder(BorderFactory.createEmptyBorder(3, 5, 7, 5));
		navigationTabs.add(rosterTab);
		navigationTabs.add(banListTab);
		top.add(navigationTabs);
		top.add(headerCards);
		add(top, BorderLayout.NORTH);
		privateCacheTimer = new javax.swing.Timer(30_000, e -> {
			if (banDirectory != null && !banCacheFresh())
			{
				banDirectory = null;
				banDirectoryCachedAt = 0;
				cachedBanCounts = Collections.emptyMap();
				sortedBanEntries = Collections.emptyList();
				if (verified && officerSettingsEligible)
				{
					requestBanDirectory(false);
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
		JLabel banHeader = new JLabel("Holy Empire Ban List", SwingConstants.CENTER);
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
		banChapterTitle.setFont(banChapterTitle.getFont().deriveFont(Font.BOLD, 15f));
		banChapterTitle.setForeground(HolyEmpireTheme.GOLD);
		banChapterTitle.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, HolyEmpireTheme.CRIMSON_EDGE),
			BorderFactory.createEmptyBorder(8, 3, 5, 3)));
		chapterBans.add(banChapterTitle);
		banChapterSearch.setAlignmentX(LEFT_ALIGNMENT);
		banChapterSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		chapterBans.add(banChapterSearch);
		headerCards.add(chapterBans, "banChapter");
	}

	/** UI-only preview: never grants or revokes actual membership eligibility. */
	public void setPreviewUnverifiedScreen(boolean enabled)
	{
		SwingUtilities.invokeLater(() -> {
			if (previewUnverifiedScreen == enabled)
			{
				return;
			}
			previewUnverifiedScreen = enabled;
			if (enabled)
			{
				selectedChapter = null;
			}
			refresh();
		});
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

	private void addAligned(JComponent item)
	{
		item.setAlignmentX(LEFT_ALIGNMENT);
		content.add(item);
	}

	private void heading(String text, int size)
	{
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, (float)size));
		HolyEmpireTheme.styleSection(label);
		label.setBorder(BorderFactory.createEmptyBorder(8, 3, 5, 3));
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
		JLabel rsn = new JLabel(name);
		rsn.setFont(rsn.getFont().deriveFont(Font.BOLD));
		rsn.setForeground(HolyEmpireRankDisplay.isEmperor(name) ? HolyEmpireRankDisplay.GOLD
																: HolyEmpireTheme.TEXT);
		rsn.setAlignmentX(LEFT_ALIGNMENT);
		row.add(rsn);
		for (String clan : members.getOrDefault(name, Collections.emptyList()))
		{
			WomDirectoryClient.ChapterMember member = findRank(name, clan);
			JLabel rank = new JLabel();
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
		status.setText(displayText);
		status.setToolTipText(fullText);
	}

	private void showUnavailable()
	{
		if (denied || previewUnverifiedScreen)
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

	private void addOfficerSettingsAction()
	{
		if (!officerSettingsEligible || !verified || officerSettingsOpener == null)
		{
			return;
		}
		JButton officerSettings = HolyEmpireTheme.button("Admin Verification", true);
		officerSettings.setName("holyEmpireOfficerBanSettings");
		officerSettings.setToolTipText(
			"Owner and Deputy Owner only. Verification credentials save automatically.");
		officerSettings.setAlignmentX(LEFT_ALIGNMENT);
		officerSettings.setMaximumSize(
			new Dimension(Integer.MAX_VALUE, officerSettings.getPreferredSize().height + 4));
		officerSettings.addActionListener(e -> {
			if (officerSettingsEligible && verified && officerSettingsOpener != null)
			{
				officerSettingsOpener.run();
			}
		});
		addAligned(officerSettings);
		content.add(Box.createVerticalStrut(4));
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
		heading("Holy Empire Clan Chapters", 13);
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

			JLabel chapterIcon = new JLabel(HolyEmpireAssets.chapterIcon(info.iconKey));
			chapterIcon.setName("holyEmpireChapterIcon:" + clan);
			chapter.add(chapterIcon, BorderLayout.WEST);

			JPanel lines = new JPanel();
			lines.setOpaque(false);
			lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
			JLabel title = new JLabel(clan);
			title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
			title.setAlignmentX(LEFT_ALIGNMENT);
			JLabel subtitle = new JLabel(info.subtitle());
			subtitle.setForeground(HolyEmpireTheme.MUTED);
			subtitle.setFont(subtitle.getFont().deriveFont(10f));
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
					  " chapters · " + WomDirectoryClient.CLANS.length + " WOM rosters" +
					  (cachedStale ? " · WOM cached" : "");
		setStatus(full, "<html>" + total + " players · " + HolyEmpireChapterCatalog.ORDERED.size() +
							" chapters<br>" + WomDirectoryClient.CLANS.length + " WOM rosters" +
							(cachedStale ? " · WOM cached" : "") + "</html>");
	}

	private void addRankRow(WomDirectoryClient.ChapterMember member)
	{
		JPanel row = new JPanel(new BorderLayout(5, 1));
		row.setName("holyEmpireRosterRow:" + member.name);
		HolyEmpireTheme.styleRow(row, HolyEmpireRankDisplay.isEmperor(member.name), false);
		JLabel name = new JLabel(member.name);
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setForeground(HolyEmpireRankDisplay.isEmperor(member.name) ? HolyEmpireRankDisplay.GOLD
																		: HolyEmpireTheme.TEXT);
		JLabel rank = new JLabel(HolyEmpireRankDisplay.title(member));
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
		chapterTitle.setIcon(info == null ? null : HolyEmpireAssets.chapterIcon(info.iconKey));
		chapterTitle.setIconTextGap(7);
		if (info != null && !info.hasWomRoster())
		{
			heading("Roster unavailable", 12);
			addAligned(new JLabel("Wise Old Man group not configured."));
			setStatus(clan + " · awaiting WOM group ID",
					  "<html>" + clan + "<br>Awaiting WOM group ID</html>");
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
		setStatus(clan + " · WOM rank order" + (cachedStale ? " · cached" : ""),
				  "<html>" + clan + "<br>WOM rank order" + (cachedStale ? " · cached" : "") +
					  "</html>");
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
		JLabel name = new JLabel(entry.name);
		name.setFont(name.getFont().deriveFont(Font.BOLD));
		name.setForeground(HolyEmpireTheme.TEXT);
		row.add(name, BorderLayout.WEST);
		JLabel details = new JLabel(
			(showChapter ? entry.chapter + " · " : "") +
			(entry.identityStatus.equals("tracked") ? "WOM tracked" : "Name not yet tracked"));
		details.setFont(details.getFont().deriveFont(10f));
		details.setForeground(HolyEmpireTheme.MUTED);
		row.add(details, BorderLayout.SOUTH);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
		addAligned(row);
	}

	private void showBans()
	{
		if (!officerSettingsEligible)
		{
			clearPrivateBanDirectory();
			showHome();
			return;
		}
		addOfficerSettingsAction();
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
			heading("Combined chapter ban lists", 13);
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
				chapterButton.add(new JLabel(HolyEmpireAssets.chapterIcon(info.iconKey)),
								  BorderLayout.WEST);
				JPanel desc = new JPanel();
				desc.setOpaque(false);
				desc.setLayout(new BoxLayout(desc, BoxLayout.Y_AXIS));
				JLabel name = new JLabel(chapter);
				name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
				JLabel secondary = new JLabel(snapshot == null ? "Not synchronized yet"
															   : (count + " synchronized bans"));
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
		if (!banCacheFresh())
		{
			showBans();
			return;
		}
		String chapter = selectedBanChapter;
		HolyEmpireChapterCatalog.Chapter info = HolyEmpireChapterCatalog.get(chapter);
		banChapterTitle.setText(chapter);
		banChapterTitle.setIcon(info == null ? null : HolyEmpireAssets.chapterIcon(info.iconKey));
		FederationBanDirectoryClient.Chapter capture = banDirectory.chapters.get(chapter);
		if (capture == null)
		{
			heading("Not synchronized", 12);
			addAligned(new JLabel("This chapter has not uploaded a ban list yet."));
			setStatus("No synchronized ban list for " + chapter, "Not synchronized");
			return;
		}
		List<FederationBanDirectoryClient.Entry> entries =
			filteredBans(chapter, banChapterSearch.getText());
		int total = cachedBanCounts.getOrDefault(chapter, 0);
		heading("Ban list · " + entries.size() + " / " + total, 12);
		if (entries.isEmpty())
		{
			addAligned(new JLabel("No matching bans."));
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

	private void refresh()
	{
		content.removeAll();
		boolean unverified = !verified || previewUnverifiedScreen;
		if (!officerSettingsEligible && showingBans)
		{
			clearPrivateBanDirectory();
		}
		navigationTabs.setVisible(!unverified);
		boolean showOfficerTab = officerSettingsEligible && !unverified;
		banListTab.setVisible(showOfficerTab);
		// Keep both controls mounted so existing sidebar listeners and tests retain
		// their stable components, while only the single Roster tab is centered.
		GridBagLayout tabGrid = (GridBagLayout)navigationTabs.getLayout();
		GridBagConstraints rosterLayout = new GridBagConstraints();
		rosterLayout.gridx = 0;
		rosterLayout.gridy = 0;
		rosterLayout.weightx = 1.0;
		rosterLayout.fill =
			showOfficerTab ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
		rosterLayout.anchor = GridBagConstraints.CENTER;
		rosterLayout.insets = new Insets(0, 0, 0, showOfficerTab ? 3 : 0);
		tabGrid.setConstraints(rosterTab, rosterLayout);
		GridBagConstraints banLayout = new GridBagConstraints();
		banLayout.gridx = 1;
		banLayout.gridy = 0;
		banLayout.weightx = showOfficerTab ? 1.0 : 0;
		banLayout.fill = GridBagConstraints.HORIZONTAL;
		banLayout.insets = new Insets(0, showOfficerTab ? 3 : 0, 0, 0);
		tabGrid.setConstraints(banListTab, banLayout);
		navigationTabs.revalidate();
		rosterTab.setSelected(!showingBans);
		banListTab.setSelected(showingBans);
		String selectedHeader = unverified	  ? "status"
								: showingBans ? (selectedBanChapter == null ? "bans" : "banChapter")
											  : (selectedChapter == null ? "home" : "chapter");
		((CardLayout)headerCards.getLayout()).show(headerCards, selectedHeader);
		// Keep header height limited to the active tab, rather than the deepest view.
		int headerIndex = unverified	? 1
						  : showingBans ? (selectedBanChapter == null ? 3 : 4)
										: (selectedChapter == null ? 0 : 2);
		int activeHeight = headerCards.getComponent(headerIndex).getPreferredSize().height;
		headerCards.setPreferredSize(new Dimension(0, activeHeight));
		// While membership is being checked, the main content already contains
		// the loading text. Showing the footer would duplicate that message.
		status.setVisible(verified && !denied && !previewUnverifiedScreen);
		if (unverified)
		{
			showUnavailable();
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
		content.revalidate();
		content.repaint();
		revalidate();
		repaint();
	}
}
