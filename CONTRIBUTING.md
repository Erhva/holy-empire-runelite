# Contributing to Holy Empire

Thanks for helping improve the Holy Empire RuneLite plugin. This file covers developer setup; for player-facing features, see [README.md](README.md).

## Development

The project uses Java 11 and Gradle. The production roster origin is pinned in `RosterEndpoint.java` for Plugin Hub review; test clients may override it with the JVM argument `-Dholyempire.rosterBaseUrl=https://your-test-roster.example`. The URL is never loaded from a packaged properties resource. From the repository root, run:

~~~sh
./gradlew test jar
~~~

Test changes in an isolated RuneLite development client before proposing updates. Avoid committing private officer credentials, API secrets, or local development data.

## Code style

Follow [RuneLite's Java code conventions](https://github.com/runelite/runelite/wiki/Code-Conventions): use tabs for indentation and place opening braces on the next line. Keep imports organized and add a suitable license header to new source files.

## Plugin Hub submissions

The Plugin Hub pins an exact commit of this repository. Release candidates should be tested from the same commit that is submitted for review. Do not include temporary development privileges or private server configuration in public submissions.

For RuneLite development guidance, see [RuneLite](https://runelite.net/) and the [Plugin Hub repository](https://github.com/runelite/plugin-hub).
