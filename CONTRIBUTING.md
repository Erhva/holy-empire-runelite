# Contributing to Holy Empire

Thanks for helping improve the Holy Empire RuneLite plugin. This file covers developer setup; for player-facing features, see [README.md](README.md).

## Development

The project uses Java 11 and Gradle. From the repository root, run:

~~~sh
./gradlew test jar -PholyempireRosterBaseUrl=https://roster.holyempireosrs.cc
~~~

Test changes in an isolated RuneLite development client before proposing updates. Avoid committing private officer credentials, API secrets, or local development data.

## Code style

Follow [RuneLite's Java code conventions](https://github.com/runelite/runelite/wiki/Code-Conventions): use tabs for indentation and place opening braces on the next line. Keep imports organized and add a suitable license header to new source files.

## Plugin Hub submissions

The Plugin Hub pins an exact commit of this repository. Release candidates should be tested from the same commit that is submitted for review. Do not include temporary development privileges or private server configuration in public submissions.

For RuneLite development guidance, see [RuneLite](https://runelite.net/) and the [Plugin Hub repository](https://github.com/runelite/plugin-hub).
