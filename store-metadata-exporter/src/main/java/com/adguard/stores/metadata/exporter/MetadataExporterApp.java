package com.adguard.stores.metadata.exporter;

import com.adguard.stores.metadata.exporter.service.AppStoreConnectService;
import com.adguard.stores.metadata.exporter.service.GooglePlayService;
import com.adguard.stores.metadata.exporter.service.MetadataExporter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(
        name = "store-metadata-exporter",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Extract App Store Connect and Google Play metadata and write to a directory"
)
public class MetadataExporterApp implements Callable<Integer> {

    // App Store Connect options
    @Option(names = {"--asc-issuer-id"}, description = "App Store Connect Issuer ID", 
            defaultValue = "${ASC_ISSUER_ID}")
    private String ascIssuerId;

    @Option(names = {"--asc-key-id"}, description = "App Store Connect Key ID", 
            defaultValue = "${ASC_KEY_ID}")
    private String ascKeyId;

    @Option(names = {"--asc-private-key-file"}, description = "Path to App Store Connect .p8 private key file")
    private File ascPrivateKeyFile;

    @Option(names = {"--asc-private-key"}, description = "App Store Connect private key content (Base64 or PEM)", 
            defaultValue = "${ASC_PRIVATE_KEY}")
    private String ascPrivateKeyContent;

    // Google Play options
    @Option(names = {"--gp-service-account-file"}, description = "Path to Google Play service account JSON file")
    private File gpServiceAccountFile;

    @Option(names = {"--gp-service-account"}, description = "Google Play service account JSON content",
            defaultValue = "${GP_SERVICE_ACCOUNT}")
    private String gpServiceAccountContent;

    @Option(names = {"--gp-package-names"}, description = "Google Play package names (comma-separated)",
            defaultValue = "${GP_PACKAGE_NAMES}")
    private String gpPackageNames;

    @Option(names = {"--gp-package-names-file"}, description = "Path to file with Google Play package names (one per line)",
            defaultValue = "${GP_PACKAGE_NAMES_FILE}")
    private String gpPackageNamesFile;

    private static final String DEFAULT_PACKAGE_NAMES_FILE = "gp-packages.txt";

    // Common options
    @Option(names = {"--output-dir", "-o"}, description = "Output directory for metadata files", 
            defaultValue = "${OUTPUT_DIR:-.}")
    private File outputDir;

    @Option(names = {"--dry-run"}, description = "Show what would be done without writing files")
    private boolean dryRun;

    @Option(names = {"--verbose", "-v"}, description = "Verbose output")
    private boolean verbose;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MetadataExporterApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        boolean hasAscCredentials = hasAppStoreConnectCredentials();
        boolean hasGpCredentials = hasGooglePlayCredentials();

        if (!hasAscCredentials && !hasGpCredentials) {
            System.err.println("Error: No store credentials provided.");
            System.err.println("Provide App Store Connect credentials (ASC_ISSUER_ID, ASC_KEY_ID, ASC_PRIVATE_KEY)");
            System.err.println("and/or Google Play credentials:");
            System.err.println("  - GP_SERVICE_ACCOUNT or --gp-service-account-file");
            System.err.println("  - GP_PACKAGE_NAMES or --gp-package-names or gp-packages.txt file");
            return 1;
        }

        if (verbose) {
            System.out.println("Output directory: " + outputDir.getAbsolutePath());
            System.out.println("Dry run: " + dryRun);
            System.out.println("App Store Connect: " + (hasAscCredentials ? "enabled" : "disabled"));
            System.out.println("Google Play: " + (hasGpCredentials ? "enabled" : "disabled"));
        }

        MetadataExporter exporter = new MetadataExporter(outputDir.toPath(), dryRun, verbose);
        int totalApps = 0;

        // Process App Store Connect
        if (hasAscCredentials) {
            totalApps += processAppStoreConnect(exporter);
        }

        // Process Google Play
        if (hasGpCredentials) {
            totalApps += processGooglePlay(exporter);
        }

        System.out.println("Done! Processed " + totalApps + " apps total.");
        return 0;
    }

    private int processAppStoreConnect(MetadataExporter exporter) {
        try {
            String privateKey = resolveAscPrivateKey();
            if (privateKey == null) {
                System.err.println("Warning: App Store Connect private key not found, skipping.");
                return 0;
            }

            AppStoreConnectService ascService = new AppStoreConnectService(ascIssuerId, ascKeyId, privateKey);

            System.out.println("Fetching apps from App Store Connect...");
            var apps = ascService.fetchAllApps();
            System.out.println("Found " + apps.size() + " apps in App Store Connect");

            for (var app : apps) {
                String bundleId = app.getAttributes().getBundleId();
                String appId = app.getId();
                System.out.println("Processing (App Store): " + bundleId);

                var appMetadata = ascService.fetchAppMetadata(appId, bundleId);
                exporter.export(appMetadata, "appstore");
            }

            return apps.size();

        } catch (Exception e) {
            System.err.println("Error processing App Store Connect: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    private int processGooglePlay(MetadataExporter exporter) {
        try {
            String serviceAccount = resolveGpServiceAccount();
            if (serviceAccount == null) {
                System.err.println("Warning: Google Play service account not found, skipping.");
                return 0;
            }

            List<String> packageNames = parsePackageNames();
            if (packageNames.isEmpty()) {
                System.err.println("Warning: No Google Play package names provided, skipping.");
                return 0;
            }

            GooglePlayService gpService = new GooglePlayService(serviceAccount);

            System.out.println("Processing " + packageNames.size() + " apps from Google Play...");

            int processed = 0;
            for (String packageName : packageNames) {
                try {
                    System.out.println("Processing (Google Play): " + packageName);
                    var appMetadata = gpService.fetchAppMetadata(packageName);
                    exporter.export(appMetadata, "googleplay");
                    processed++;
                } catch (Exception e) {
                    System.err.println("Error processing " + packageName + ": " + e.getMessage());
                    if (verbose) {
                        e.printStackTrace();
                    }
                }
            }

            return processed;

        } catch (Exception e) {
            System.err.println("Error processing Google Play: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    private boolean hasAppStoreConnectCredentials() {
        return isValidValue(ascIssuerId) && isValidValue(ascKeyId) 
                && (isValidValue(ascPrivateKeyContent) || (ascPrivateKeyFile != null && ascPrivateKeyFile.exists()));
    }

    private boolean hasGooglePlayCredentials() {
        return (isValidValue(gpServiceAccountContent) || (gpServiceAccountFile != null && gpServiceAccountFile.exists()))
                && hasPackageNames();
    }

    private boolean hasPackageNames() {
        if (isValidValue(gpPackageNames)) {
            return true;
        }
        if (isValidValue(gpPackageNamesFile)) {
            return new File(gpPackageNamesFile).exists();
        }
        // Check for default file in current directory
        return new File(DEFAULT_PACKAGE_NAMES_FILE).exists();
    }

    private boolean isValidValue(String value) {
        return value != null && !value.isBlank() && !value.startsWith("${");
    }

    private String resolveAscPrivateKey() throws Exception {
        if (ascPrivateKeyFile != null && ascPrivateKeyFile.exists()) {
            return Files.readString(ascPrivateKeyFile.toPath());
        }
        if (isValidValue(ascPrivateKeyContent)) {
            return ascPrivateKeyContent;
        }
        return null;
    }

    private String resolveGpServiceAccount() throws Exception {
        if (gpServiceAccountFile != null && gpServiceAccountFile.exists()) {
            return Files.readString(gpServiceAccountFile.toPath());
        }
        if (isValidValue(gpServiceAccountContent)) {
            return gpServiceAccountContent;
        }
        return null;
    }

    private List<String> parsePackageNames() throws Exception {
        // Priority: 1) CLI/env comma-separated, 2) explicit file, 3) default file
        if (isValidValue(gpPackageNames)) {
            return Stream.of(gpPackageNames.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        File packageFile = null;
        if (isValidValue(gpPackageNamesFile)) {
            packageFile = new File(gpPackageNamesFile);
        } else {
            File defaultFile = new File(DEFAULT_PACKAGE_NAMES_FILE);
            if (defaultFile.exists()) {
                packageFile = defaultFile;
            }
        }

        if (packageFile != null && packageFile.exists()) {
            if (verbose) {
                System.out.println("Reading package names from: " + packageFile.getAbsolutePath());
            }
            return Files.readAllLines(packageFile.toPath()).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .toList();
        }

        return List.of();
    }
}
