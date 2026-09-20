#!/bin/bash

set -o pipefail
adb logcat -c

buildType="${1:-debug}"
extraGradleArgs=("${@:2}")

runTests() {
    if [ "$buildType" = "release" ]; then
        ./gradlew connectedPlayReleaseAndroidTest -PtestBuildType=release "${extraGradleArgs[@]}" \
            -Pandroid.testInstrumentationRunnerArguments.notAnnotation=de.test.antennapod.IgnoreOnCi
    else
        ./gradlew connectedPlayDebugAndroidTest connectedDebugAndroidTest -PtestBuildType=debug "${extraGradleArgs[@]}" \
            -Pandroid.testInstrumentationRunnerArguments.notAnnotation=de.test.antennapod.IgnoreOnCi
    fi
}

# Retry tests to make them less flaky
if runTests || runTests || runTests; then
    echo "Tests succeeded"
else
    echo "Tests FAILED. Dumping logcat:"
    adb logcat -d > app/build/reports/androidTests/logcat.txt
    exit 1
fi

