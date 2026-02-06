# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**mapster** is a Java 17 project built with Maven. Package: `com.johnreah.mapster`.

## Build Commands

- `mvn clean verify` — full build + tests
- `mvn test` — run all tests
- `mvn test -Dtest=AppTest` — run a single test class
- `mvn test -Dtest=AppTest#appShouldInstantiate` — run a single test method
- `mvn compile` — compile only (no tests)
- `mvn package` — build JAR to `target/`
- `mvn javafx:run` — run the JavaFX application

## Project Structure

Standard Maven layout:
- `src/main/java/` — application source (`com.johnreah.mapster`)
- `src/main/resources/` — application resources
- `src/test/java/` — test source (`com.johnreah.mapster`)
- `src/test/resources/` — test resources

## Test Framework

JUnit 5 (Jupiter) with maven-surefire-plugin.
