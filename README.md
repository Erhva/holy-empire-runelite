# Holy Empire for RuneLite

Holy Empire is a community RuneLite plugin for viewing connected clan chapter rosters and providing clan-specific player indicators and recruitment notices.

## Features

- Combined Wise Old Man chapter roster, global member search, and per-chapter member views
- Chapter-aware player menu tags, overhead names, and minimap indicators
- Configurable indicator priority, same-clan behavior, and per-chapter colors
- Clan links, chapter rank icons, and member verification based on the published roster
- Ban-list warnings during recruitment; officer-only private ban lookup and upload tools
- Ban-warning refresh when equipping a Clan vexillum

## Data and privacy

The plugin retrieves clan roster information and filtered recruitment-warning data from Holy Empire-hosted HTTPS services. These endpoints are maintained by Holy Empire and are not operated or verified by RuneLite. A logged-in player name is compared against the published roster locally to determine chapter membership. The roster and available warnings are periodically refreshed, or can be refreshed when a Clan vexillum is equipped.

Officers with the required native clan permissions can submit chapter ban information to Holy Empire services after choosing to synchronize. Private ban-directory queries are available only to authorized officers. Ordinary members receive a limited ban-warning feed that does not identify the chapter responsible for a ban.

## Development

This project uses Java 11 and Gradle. Build from the repository root:

```sh
./gradlew test jar -PholyempireRosterBaseUrl=https://roster.holyempireosrs.cc
```

Install and test in a local RuneLite development client before distributing changes. For information about RuneLite and its plugin ecosystem, see [RuneLite](https://runelite.net/) and the [Plugin Hub](https://runelite.net/plugin-hub/).

## Community

For Holy Empire membership and chapter information, visit [Holy Empire Discord](https://discord.gg/w465).

## License

The plugin source is distributed under the BSD 2-Clause License. Third-party logos and game artwork remain the property of their respective owners.
