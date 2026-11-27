# Holy Empire — RuneLite Plugin

The **Holy Empire** RuneLite plugin helps members of the Holy Empire megaclan on World 465 find their chapter, recognize fellow members, and participate in clan activities across different clan chats.

## Features

- **Cross-chapter roster:** Search member names, browse chapter rosters and ranks, and open Wise Old Man and hiscores profiles.
- **Member indicators:** Optional overhead names, minimap dots, and right-click chapter labels. Configure per-chapter colors and their priority relative to RuneLite's existing indicators. Player-identification highlights are suppressed in PvP areas and PvP-type worlds.
- **Events:** View Empire-wide Discord scheduled events and chapter events synchronized by the authorized Servo-Skull Discord bot. Open event posts and RSVP pages in Discord, view your confirmed Interested status, and see event countdowns.
- **Announcements and broadcasts:** View authorized Empire communications. Officers' creation and editing capabilities depend on their verified chapter and leadership roles.
- **Attendance:** Authorized event creators and chapter officers may independently record event attendance; the private service consolidates multiple reports for the same event, accounting for overlap. The report is an observation of nearby players rather than proof of participation.
- **Shared ban warnings:** Authorized officers can synchronize their clan's native ban list, see chapter-scoped ban details, and receive warnings during recruitment. Reasons and Discord ban notices are optional, officer-controlled actions. Clearing a private reason does not unban the player or delete an earlier Discord post.
- **Discord role verification:** Connect an eligible Discord account to enable role-gated features. Server-side authorization controls privileged reads and actions.

## Getting started

After approval, install **Holy Empire** from the RuneLite Plugin Hub and open the Holy Empire sidebar. Roster membership in at least one recognized Holy Empire chapter is required for member-only features.

To join the clan, visit [discord.gg/w465](https://discord.gg/w465).

## External services and privacy

This third-party plugin is maintained by Holy Empire, not RuneLite or Jagex. Roster, event, announcement, role-verification, and ban-list features use Holy Empire-operated services. Connecting to those services exposes your IP address and may involve your RuneScape account name, chapter, and authorized Discord identity. The publicly readable Empire events feed is not personalized; private features require authenticated sessions.

**Attendance reports transmit observed RuneScape player names, chapter affiliations, and observation times to a private Holy Empire service** when an authorized officer saves a report. Only authorized officers can access protected attendance and private ban details. Please consider this data flow before using the feature.

The plugin never asks for or stores your RuneScape login password. Discord sign-in occurs through Discord's authorized verification flow; server-side access is scoped to the verified role. External services can be temporarily unavailable, in which case their dependent features will be unavailable or stale.

## Configuration and behavior

Indicators can be disabled or customized under RuneLite **Settings → Holy Empire**. The plugin does not automatically recruit, ban, unban, send chat messages, or publish Discord notices; privileged actions require an authorized officer and deliberate user confirmation.

## License

Source code is licensed under the [BSD 2-Clause License](LICENSE). RuneScape, Discord, and other third-party names, artwork, and marks remain the property of their respective owners.
