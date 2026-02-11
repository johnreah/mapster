#!/bin/bash
set -e

echo "Building Mapster macOS Application..."

# Clean and build
mvn clean package -DskipTests
echo "Maven build complete."

# Remove old distribution
rm -rf target/dist

# Run jpackage to create macOS app bundle
jpackage \
  --input target \
  --name Mapster \
  --main-jar mapster-1.0-SNAPSHOT.jar \
  --main-class com.johnreah.mapster.App \
  --module-path "target/jmods" \
  --add-modules javafx.controls,javafx.graphics,java.net.http \
  --type app-image \
  --dest target/dist \
  --app-version 1.0.0 \
  --vendor johnreah \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "--enable-native-access=javafx.graphics"

echo ""
echo "========================================"
echo "SUCCESS! Application created successfully"
echo "========================================"
echo ""
echo "Location: target/dist/Mapster.app"
echo "Run with: open target/dist/Mapster.app"
echo ""
echo "The Mapster.app bundle can be copied to any macOS machine."
echo "No JRE installation required - runtime is bundled."
