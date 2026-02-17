# Publishing to GitHub Maven Packages

This document explains how to publish your Continuum project modules to GitHub Maven Packages.

## Overview

All modules in the Continuum project have been configured to publish to GitHub Maven Packages. The following modules are now set up for publishing:

- `continuum-api-server`
- `continuum-commons`
- `continuum-avro-schemas`
- `continuum-base`
- `continuum-knime-base`
- `continuum-worker`
- `continuum-message-bridge`
- `workers:continuum-base-worker`

## Prerequisites

1. **GitHub Personal Access Token (PAT)**
   - Navigate to GitHub Settings → Developer settings → Personal access tokens → Tokens (classic)
   - Create a new token with at least `write:packages` and `read:packages` scopes
   - Store this token securely

2. **GitHub Repository**
   - You already have the repository: `https://github.com/roushan65/Continuum`

## Configuration

### Method 1: Environment Variables (Recommended for CI/CD)

Set the following environment variables before running the publish command:

```bash
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=your-personal-access-token
```

### Method 2: Gradle Properties (Local Development)

Create or edit `~/.gradle/gradle.properties`:

```properties
GITHUB_ACTOR=your-github-username
GITHUB_TOKEN=your-personal-access-token
```

### Method 3: GitHub Actions (Automated)

In your GitHub Actions workflow, use the automatically provided `GITHUB_TOKEN`:

```yaml
env:
  GITHUB_ACTOR: ${{ github.actor }}
  GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

## Publishing Commands

### Publish a Single Module

```bash
./gradlew :continuum-api-server:publish
```

### Publish All Modules

```bash
./gradlew publish
```

### Publish Specific Modules

```bash
./gradlew :continuum-commons:publish :continuum-avro-schemas:publish
```

### Publish with Gradle Wrapper (Linux/Mac)

```bash
./gradlew publish
```

### Publish with Gradle Wrapper (Windows)

```cmd
gradlew.bat publish
```

## Publishing Details

Each module is configured to publish to GitHub Packages:

### GitHub Packages
- **Name**: GitHubPackages
- **URL**: `https://maven.pkg.github.com/roushan65/Continuum`
- **Credentials**: GitHub Actor + Token
- **Group IDs**: 
  - `com.continuum.core` (api-server, commons, avro-schemas, worker, message-bridge)
  - `com.continuum.base` (base module)
  - `com.continuum.knime` (knime-base module)
  - `com.continuum.app.worker.base` (continuum-base-worker)


## Build Information

Each published artifact includes:

- **Group**: Module-specific (see above)
- **Artifact ID**: Auto-generated from module name
- **Version**: `1.0.0`
- **POM Information**:
  - Name: Module name
  - Description: Module description
  - URL: `https://github.com/roushan65/Continuum`

## Verification

After publishing, verify the packages are available:

1. Navigate to your GitHub repository
2. Go to Packages section
3. You should see the published packages with version `1.0.0`

Or use Maven to check:

```bash
curl -H "Authorization: Bearer YOUR_TOKEN" \
  https://maven.pkg.github.com/roushan65/Continuum/com/continuum/core/continuum-api-server/1.0.0/continuum-api-server-1.0.0.pom
```

## Using Published Packages as Dependencies

In other projects, add GitHub Packages as a repository in your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/roushan65/Continuum")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("com.continuum.core:continuum-commons:1.0.0")
    implementation("com.continuum.core:continuum-avro-schemas:1.0.0")
}
```

## Troubleshooting

### Authentication Failures
- Verify your GitHub token has `write:packages` and `read:packages` scopes
- Ensure environment variables are correctly set: `echo $GITHUB_ACTOR` and `echo $GITHUB_TOKEN`
- Token may have expired; generate a new one

### Build Failures
- Run `./gradlew clean` to clear build cache
- Check Java version: `java -version` (should be Java 21+)
- Review Gradle output for specific error messages

### Network Issues
- Verify internet connection
- Check if GitHub is accessible
- Try publishing a single module first to isolate issues

### Module Not Found
- Ensure all dependencies are resolved: `./gradlew dependencies`
- Run `./gradlew build` first to validate the build

## Additional Notes

- Version numbers should be updated in each module's `build.gradle.kts` when preparing releases
- Consider using semantic versioning (e.g., 1.0.0, 1.0.1, 1.1.0, 2.0.0)
- GitHub Packages requires authentication for all access (even public repositories)
- For private consumption, consider CI/CD automation to avoid manual token management

## References

- [GitHub Maven Packages Documentation](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
- [Gradle Publishing Documentation](https://docs.gradle.org/current/userguide/publishing_maven.html)

