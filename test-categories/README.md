# :test-categories

This module provides JUnit categories that are used to group the JVM unit tests of all modules.

`IntegrationTest` marks a test class that exercises real persistence (the SQLite database via `PodDBAdapter`) or wires several real production components together. Tests without a category are unit tests.
Annotate a test class with `@Category(IntegrationTest.class)` to mark it.
The dependency on this module is added to every module by `common.gradle`.

Run one group with `-PtestGroup=unit` or `-PtestGroup=integration`, for example `./gradlew testPlayDebugUnitTest -PtestGroup=integration`. Without the property, all JVM tests run.

The instrumented tests in `:app` (`src/androidTest`) are not grouped this way. `HttpDownloaderTest`, `PlaybackServiceMediaPlayerTest` and `PlaybackServiceTaskManagerTest` there are service-level tests rather than UI tests.
