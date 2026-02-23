# Store Metadata Exporter

CLI tool and GitHub Action for extracting App Store Connect metadata and writing it to a directory.
Designed for CI/CD pipelines to track changes in app metadata over time.

## Features

- Extracts metadata for all apps in your App Store Connect account
- Supports all available locales
- Outputs structured JSON files
- Native binary available (no Java runtime required)
- Environment variable support for CI/CD integration
- Available as a GitHub Action

## Quick Start

### GitHub Action (recommended)

```yaml
name: Sync App Store Metadata

on:
  schedule:
    - cron: '0 6 * * *'
  workflow_dispatch:

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: AdguardTeam/store-metadata-exporter/store-metadata-exporter@v1
        with:
          issuer-id: ${{ secrets.ASC_ISSUER_ID }}
          key-id: ${{ secrets.ASC_KEY_ID }}
          private-key: ${{ secrets.ASC_PRIVATE_KEY }}
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
Set the required secrets (`ASC_ISSUER_ID`, `ASC_KEY_ID`, `ASC_PRIVATE_KEY`) and it will work out of the box.

## Installation

### Pre-built binary (no Java required)

```bash
# Linux
curl -sL -o store-metadata-exporter \
  "https://github.com/AdguardTeam/store-metadata-exporter/releases/latest/download/store-metadata-exporter-linux"
chmod +x store-metadata-exporter

# macOS
curl -sL -o store-metadata-exporter \
  "https://github.com/AdguardTeam/store-metadata-exporter/releases/latest/download/store-metadata-exporter-macos"
chmod +x store-metadata-exporter
```

### Fat JAR (requires Java 21+)

```bash
curl -sL -o store-metadata-exporter.jar \
  "https://github.com/AdguardTeam/store-metadata-exporter/releases/latest/download/store-metadata-exporter.jar"
```

### Build from source

```bash
# Fat JAR
mvn package -pl store-metadata-exporter -am -DskipTests
# Output: store-metadata-exporter/target/store-metadata-exporter.jar

# Native image (requires GraalVM 21+)
mvn package -pl store-metadata-exporter -am -DskipTests -Pnative
# Output: store-metadata-exporter/target/store-metadata-exporter
```

## Usage

### Command line

```bash
./store-metadata-exporter \
  --issuer-id <ISSUER_ID> \
  --key-id <KEY_ID> \
  --private-key-file /path/to/AuthKey.p8 \
  --output-dir ./output
```

### Environment variables

```bash
export ASC_ISSUER_ID="your-issuer-id"
export ASC_KEY_ID="your-key-id"
export ASC_PRIVATE_KEY="base64-encoded-key-or-pem-content"

./store-metadata-exporter --output-dir ./output
```

### CLI options

| Option | Environment Variable | Description |
|--------|---------------------|-------------|
| `--issuer-id` | `ASC_ISSUER_ID` | App Store Connect Issuer ID |
| `--key-id` | `ASC_KEY_ID` | App Store Connect Key ID |
| `--private-key-file` | - | Path to .p8 private key file |
| `--private-key` | `ASC_PRIVATE_KEY` | Private key content (Base64 or PEM) |
| `--output-dir`, `-o` | `OUTPUT_DIR` | Output directory (default: current dir) |
| `--dry-run` | - | Show what would be done without writing |
| `--verbose`, `-v` | - | Verbose output |

## GitHub Action reference

### Inputs

| Input | Required | Default | Description |
|-------|----------|---------|-------------|
| `issuer-id` | Yes | - | App Store Connect Issuer ID |
| `key-id` | Yes | - | App Store Connect Key ID |
| `private-key` | Yes | - | Private key content (.p8 file) |
| `output-dir` | No | `.` | Output directory |
| `verbose` | No | `false` | Enable verbose output |

### Outputs

| Output | Description |
|--------|-------------|
| `apps-count` | Number of apps processed |

## Output structure

```
output/
└── apps/
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
  "lastUpdated": "2026-02-23T18:00:00Z"
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
    "versionString": "1.2.3",
    "description": "App description...",
    "keywords": "keyword1, keyword2, keyword3",
    "whatsNew": "Bug fixes and improvements",
    "promotionalText": "New features!",
    "marketingUrl": "https://example.com",
    "supportUrl": "https://example.com/support"
  }
}
```

## Tracked metadata

### App Info (per locale)
- **name** — App name
- **subtitle** — App subtitle
- **privacyPolicyUrl** — Privacy policy URL
- **privacyChoicesUrl** — Privacy choices URL

### Version Info (latest live version, per locale)
- **description** — Full app description
- **keywords** — Search keywords
- **whatsNew** — Release notes
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

## Project structure

```
store-metadata-exporter/
├── appstoreconnect-api/       # App Store Connect API client (generated)
├── store-metadata-exporter/   # CLI tool and GitHub Action
│   ├── action.yml             # GitHub Action definition
│   └── src/
├── sync-metadata.yml          # Ready-to-use workflow file
└── pom.xml
```

## License

MIT
