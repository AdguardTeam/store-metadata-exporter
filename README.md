# Store Metadata Exporter

CLI tool and GitHub Action for extracting App Store Connect and Google Play metadata and writing it to a directory.
Designed for CI/CD pipelines to track changes in app metadata over time.

## Features

- Extracts metadata from **App Store Connect** and **Google Play**
- Supports all available locales
- Outputs structured JSON files
- Environment variable support for CI/CD integration
- Available as a GitHub Action
- Supports using one or both stores (credentials are optional per store)

## Quick Start

### GitHub Action (recommended)

```yaml
name: Sync Store Metadata

on:
  schedule:
    - cron: '0 6 * * *'
  workflow_dispatch:

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: AdguardTeam/store-metadata-exporter@v1
        with:
          # App Store Connect (optional)
          asc-issuer-id: ${{ secrets.ASC_ISSUER_ID }}
          asc-key-id: ${{ secrets.ASC_KEY_ID }}
          asc-private-key: ${{ secrets.ASC_PRIVATE_KEY }}
          # Google Play (optional)
          gp-service-account: ${{ secrets.GP_SERVICE_ACCOUNT }}
          gp-package-names: 'com.example.app1,com.example.app2'
          output-dir: .

      - name: Commit changes
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add -A
          git diff --staged --quiet || git commit -m "Update metadata"
          git push
```

### Standalone workflow file

Copy [sync-metadata.yml](sync-metadata.yml) to `.github/workflows/` in your repository.  
Set the required secrets and it will work out of the box.

## Installation

### Download JAR (requires Java 17+)

```bash
curl -sL -o store-metadata-exporter.jar \
  "https://github.com/AdguardTeam/store-metadata-exporter/releases/latest/download/store-metadata-exporter.jar"
```

### Build from source

```bash
mvn package -pl store-metadata-exporter -am -DskipTests
# Output: store-metadata-exporter/target/store-metadata-exporter.jar
```

## Usage

### Command line

```bash
# App Store Connect only
java -jar store-metadata-exporter.jar \
  --asc-issuer-id <ISSUER_ID> \
  --asc-key-id <KEY_ID> \
  --asc-private-key-file /path/to/AuthKey.p8 \
  --output-dir ./output

# Google Play only
java -jar store-metadata-exporter.jar \
  --gp-service-account-file /path/to/service-account.json \
  --gp-package-names "com.example.app1,com.example.app2" \
  --output-dir ./output

# Both stores
java -jar store-metadata-exporter.jar \
  --asc-issuer-id <ISSUER_ID> \
  --asc-key-id <KEY_ID> \
  --asc-private-key-file /path/to/AuthKey.p8 \
  --gp-service-account-file /path/to/service-account.json \
  --gp-package-names "com.example.app1,com.example.app2" \
  --output-dir ./output
```

### Environment variables

```bash
# App Store Connect
export ASC_ISSUER_ID="your-issuer-id"
export ASC_KEY_ID="your-key-id"
export ASC_PRIVATE_KEY="base64-encoded-key-or-pem-content"

# Google Play
export GP_SERVICE_ACCOUNT='{"type":"service_account",...}'
export GP_PACKAGE_NAMES="com.example.app1,com.example.app2"

java -jar store-metadata-exporter.jar --output-dir ./output
```

### CLI options

#### App Store Connect

| Option | Environment Variable | Description |
|--------|---------------------|-------------|
| `--asc-issuer-id` | `ASC_ISSUER_ID` | App Store Connect Issuer ID |
| `--asc-key-id` | `ASC_KEY_ID` | App Store Connect Key ID |
| `--asc-private-key-file` | - | Path to .p8 private key file |
| `--asc-private-key` | `ASC_PRIVATE_KEY` | Private key content (Base64 or PEM) |

#### Google Play

| Option | Environment Variable | Description |
|--------|---------------------|-------------|
| `--gp-service-account-file` | - | Path to service account JSON file |
| `--gp-service-account` | `GP_SERVICE_ACCOUNT` | Service account JSON content |
| `--gp-package-names` | `GP_PACKAGE_NAMES` | Package names (comma-separated) |
| `--gp-package-names-file` | `GP_PACKAGE_NAMES_FILE` | Path to file with package names (one per line) |

> **Note:** If no package names are provided via CLI/env, the tool will look for `gp-packages.txt` in the current directory.

#### Common

| Option | Environment Variable | Description |
|--------|---------------------|-------------|
| `--output-dir`, `-o` | `OUTPUT_DIR` | Output directory (default: current dir) |
| `--dry-run` | - | Show what would be done without writing |
| `--verbose`, `-v` | - | Verbose output |

## GitHub Action reference

### Inputs

#### App Store Connect (optional if using Google Play only)

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `asc-issuer-id` | No | - | App Store Connect Issuer ID |
| `asc-key-id` | No | - | App Store Connect Key ID |
| `asc-private-key` | No | - | Private key content (.p8 file) |

#### Google Play (optional if using App Store Connect only)

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `gp-service-account` | No | - | Service account JSON content |
| `gp-package-names` | No | - | Package names (comma-separated) |
| `gp-package-names-file` | No | `gp-packages.txt` | Path to file with package names (one per line) |

#### Common

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `output-dir` | No | `.` | Output directory |
| `verbose` | No | `false` | Enable verbose output |

### Outputs

| Output | Description |
|--------|-------------|
| `apps-count` | Number of apps processed |

## Output structure

```
output/
├── appstore/
│   └── com.example.myapp/
│       ├── metadata.json
│       └── localizations/
│           ├── en-US.json
│           ├── de-DE.json
│           └── ...
└── googleplay/
    └── com.example.myapp/
        ├── metadata.json
        └── localizations/
            ├── en-US.json
            ├── de-DE.json
            └── ...
```

### metadata.json

```json
{
  "appId": "1234567890",
  "bundleId": "com.example.myapp",
  "currentVersion": "1.2.3",
  "versionCreatedAt": "2026-01-15T10:30:00Z",
  "versionReleasedAt": "2026-01-20T08:00:00Z"
}
```

### Localization file (e.g. en-US.json)

```json
{
  "locale": "en-US",
  "appInfo": {
    "name": "My App",
    "subtitle": "Best app ever",
    "privacyPolicyUrl": "https://example.com/privacy",
    "privacyChoicesUrl": null
  },
  "version": {
    "description": "App description...",
    "keywords": "keyword1, keyword2, keyword3",
    "promotionalText": "New features!",
    "marketingUrl": "https://example.com",
    "supportUrl": "https://example.com/support"
  }
}
```

## Tracked metadata

### App metadata
- **appId** — App Store ID or Google Play package name
- **bundleId** — Bundle identifier
- **currentVersion** — Current live version string
- **versionCreatedAt** — When the version was created (App Store Connect only)
- **versionReleasedAt** — Earliest release date / when the version was released (App Store Connect only)

### App Info (per locale)
- **name** — App name
- **subtitle** — App subtitle
- **privacyPolicyUrl** — Privacy policy URL
- **privacyChoicesUrl** — Privacy choices URL

### Version Info (latest live version, per locale)
- **description** — Full app description
- **keywords** — Search keywords
- **promotionalText** — Promotional text
- **marketingUrl** — Marketing URL
- **supportUrl** — Support URL

## App Store Connect setup

1. Go to [App Store Connect - Users and Access - Keys](https://appstoreconnect.apple.com/access/api)
2. Create a new API key with **App Manager** or **Admin** role
3. Download the `.p8` file and note the **Key ID**
4. Copy the **Issuer ID** from the top of the page

### Required secrets for GitHub Actions

| Secret | Description |
|--------|-------------|
| `ASC_ISSUER_ID` | Issuer ID from App Store Connect |
| `ASC_KEY_ID` | Key ID of your API key |
| `ASC_PRIVATE_KEY` | Contents of .p8 file (including BEGIN/END lines) |

## Google Play setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create or select a project linked to your Google Play Console
3. Enable the **Google Play Android Developer API**
4. Go to **IAM & Admin > Service Accounts** and create a new service account
5. Download the JSON key file
6. In [Google Play Console - Users and Permissions](https://play.google.com/console/users-and-permissions), invite the service account email with **View app information** permission

### Required secrets for GitHub Actions

| Secret | Description |
|--------|-------------|
| `GP_SERVICE_ACCOUNT` | Contents of service account JSON file |
| `GP_PACKAGE_NAMES` | Comma-separated list of package names (e.g., `com.example.app1,com.example.app2`) |

> **Note:** Unlike App Store Connect, Google Play API doesn't provide a "list all apps" endpoint. You must explicitly specify the package names you want to export.

### Using gp-packages.txt file

As an alternative to `GP_PACKAGE_NAMES`, you can create a `gp-packages.txt` file in your repository root:

```
# Google Play packages to export
com.example.app1
com.example.app2
com.example.app3
```

The file supports:
- One package name per line
- Comments starting with `#`
- Empty lines (ignored)

Path to the file of the above format may be specified via `GP_PACKAGE_NAMES_FILE` environment variable.

## Project structure

```
store-metadata-exporter/
├── action.yml                 # GitHub Action definition
├── appstoreconnect-api/       # App Store Connect API client (generated)
├── store-metadata-exporter/   # CLI tool
│   └── src/
├── sync-metadata.yml          # Ready-to-use workflow file
└── pom.xml
```

## License

MIT
