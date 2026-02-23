package com.adguard.stores.metadata.exporter;

import com.adguard.stores.metadata.exporter.service.AppStoreConnectService;
import com.adguard.stores.metadata.exporter.service.MetadataExporter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@Command(
        name = "store-metadata-exporter",
        mixinStandardHelpOptions = true,
        version = "1.0",
        description = "Extract App Store Connect metadata and write to a directory"
)
public class MetadataExporterApp implements Callable<Integer> {

    @Option(names = {"--issuer-id"}, description = "App Store Connect Issuer ID", 
            defaultValue = "${ASC_ISSUER_ID}")
    private String issuerId;

    @Option(names = {"--key-id"}, description = "App Store Connect Key ID", 
            defaultValue = "${ASC_KEY_ID}")
    private String keyId;

    @Option(names = {"--private-key-file"}, description = "Path to .p8 private key file")
    private File privateKeyFile;

    @Option(names = {"--private-key"}, description = "Private key content (Base64 or PEM)", 
            defaultValue = "${ASC_PRIVATE_KEY}")
    private String privateKeyContent;

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
        if (!validateOptions()) {
            return 1;
        }

        String privateKey = resolvePrivateKey();
        if (privateKey == null) {
            System.err.println("Error: Private key not provided. Use --private-key-file or --private-key/ASC_PRIVATE_KEY");
            return 1;
        }

        if (verbose) {
            System.out.println("Issuer ID: " + issuerId);
            System.out.println("Key ID: " + keyId);
            System.out.println("Output directory: " + outputDir.getAbsolutePath());
            System.out.println("Dry run: " + dryRun);
        }

        try {
            AppStoreConnectService ascService = new AppStoreConnectService(issuerId, keyId, privateKey);
            MetadataExporter exporter = new MetadataExporter(outputDir.toPath(), dryRun, verbose);

            System.out.println("Fetching apps from App Store Connect...");
            var apps = ascService.fetchAllApps();
            System.out.println("Found " + apps.size() + " apps");

            for (var app : apps) {
                String bundleId = app.getAttributes().getBundleId();
                String appId = app.getId();
                System.out.println("Processing: " + bundleId);

                var appMetadata = ascService.fetchAppMetadata(appId, bundleId);
                exporter.export(appMetadata);
            }

            System.out.println("Done!");
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private boolean validateOptions() {
        if (issuerId == null || issuerId.isBlank() || issuerId.startsWith("${")) {
            System.err.println("Error: Issuer ID not provided. Use --issuer-id or set ASC_ISSUER_ID env variable");
            return false;
        }
        if (keyId == null || keyId.isBlank() || keyId.startsWith("${")) {
            System.err.println("Error: Key ID not provided. Use --key-id or set ASC_KEY_ID env variable");
            return false;
        }
        return true;
    }

    private String resolvePrivateKey() throws Exception {
        if (privateKeyFile != null && privateKeyFile.exists()) {
            return Files.readString(privateKeyFile.toPath());
        }
        if (privateKeyContent != null && !privateKeyContent.isBlank() && !privateKeyContent.startsWith("${")) {
            return privateKeyContent;
        }
        return null;
    }
}
