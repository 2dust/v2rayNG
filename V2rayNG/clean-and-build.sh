#!/bin/bash

echo "Cleaning Gradle cache and rebuilding project..."

# Clean Gradle cache
./gradlew clean

# Clean build cache
./gradlew cleanBuildCache

# Remove .gradle directory in project
rm -rf .gradle

# Remove build directories
rm -rf app/build
rm -rf build

# Sync project
./gradlew --refresh-dependencies

echo "Clean and rebuild completed. Try building your project now."
