# AWS UI

[![CI](https://github.com/hdhensley/aws-ui/actions/workflows/ci.yml/badge.svg)](https://github.com/hdhensley/aws-ui/actions/workflows/ci.yml)
[![Build and Release](https://github.com/hdhensley/aws-ui/actions/workflows/release.yml/badge.svg)](https://github.com/hdhensley/aws-ui/actions/workflows/release.yml)
[![Latest Release](https://img.shields.io/github/v/release/hdhensley/aws-ui)](https://github.com/hdhensley/aws-ui/releases)
[![License](https://img.shields.io/github/license/hdhensley/aws-ui)](https://github.com/hdhensley/aws-ui/blob/main/LICENSE)
[![Open Issues](https://img.shields.io/github/issues/hdhensley/aws-ui)](https://github.com/hdhensley/aws-ui/issues)
[![Downloads](https://img.shields.io/github/downloads/hdhensley/aws-ui/total)](https://github.com/hdhensley/aws-ui/releases)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00)](https://adoptium.net/)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36)](https://maven.apache.org/)

Desktop Swing application for browsing AWS CodePipeline, ECS, CloudWatch Logs, and app settings.

Current capabilities:
- AWS CodePipeline monitoring
- ECS cluster and service inspection
- CloudWatch Logs browsing and filtering
- FlatLaf-based desktop theming

## Local Build

Requirements:
- JDK 21
- Maven 3.9+

Build the app:

```bash
mvn clean package
```

Run the app locally:

```bash
mvn exec:java
```

Packaging output is written to `target/`, including:
- the runnable jar
- `target/lib/` with runtime dependencies

## GitHub Releases

The repository includes a GitHub Actions workflow at [.github/workflows/release.yml](.github/workflows/release.yml).

It runs when a tag matching `v*` is pushed, for example:

```bash
git tag v1.0.1
git push origin v1.0.1
```

That workflow:
- builds Windows, macOS, and Linux installers
- signs and notarizes the macOS DMG
- creates a GitHub Release and attaches the generated artifacts

## Required GitHub Secrets

macOS signing and notarization require these repository secrets:

- `MACOS_CERTIFICATE`
- `MACOS_CERTIFICATE_PWD`
- `KEYCHAIN_PASSWORD`
- `MACOS_DEVELOPER_ID`
- `APPLE_ID`
- `APPLE_TEAM_ID`
- `APPLE_APP_PASSWORD`

Without those secrets, the macOS job will fail during signing or notarization.

## Release Notes

Release versions are taken from the Git tag name after the leading `v`.

Examples:
- `v1.0.0` -> release version `1.0.0`
- `v1.2.3` -> release version `1.2.3`