#!/usr/bin/env bash
./gradlew check --info &&
./gradlew publish --info &&
./gradlew closeAndReleaseRepository --info
