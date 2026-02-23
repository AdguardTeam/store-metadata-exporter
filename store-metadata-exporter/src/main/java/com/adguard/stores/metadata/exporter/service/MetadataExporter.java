package com.adguard.stores.metadata.exporter.service;

import com.adguard.stores.metadata.exporter.model.AppMetadata;
import com.adguard.stores.metadata.exporter.model.LocalizationMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MetadataExporter {

    private final Path outputDir;
    private final boolean dryRun;
    private final boolean verbose;
    private final ObjectMapper objectMapper;

    public MetadataExporter(Path outputDir, boolean dryRun, boolean verbose) {
        this.outputDir = outputDir;
        this.dryRun = dryRun;
        this.verbose = verbose;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void export(AppMetadata appMetadata) throws IOException {
        Path appDir = outputDir.resolve("apps").resolve(appMetadata.getBundleId());
        Path localizationsDir = appDir.resolve("localizations");

        if (!dryRun) {
            Files.createDirectories(localizationsDir);
        }

        // Write metadata.json
        Map<String, Object> metadataJson = new LinkedHashMap<>();
        metadataJson.put("appId", appMetadata.getAppId());
        metadataJson.put("bundleId", appMetadata.getBundleId());
        metadataJson.put("currentVersion", appMetadata.getCurrentVersion());
        metadataJson.put("lastUpdated", appMetadata.getLastUpdated().toString());

        Path metadataFile = appDir.resolve("metadata.json");
        writeJson(metadataFile, metadataJson);

        // Write localization files
        for (LocalizationMetadata localization : appMetadata.getLocalizations()) {
            Map<String, Object> localizationJson = new LinkedHashMap<>();
            localizationJson.put("locale", localization.getLocale());

            if (localization.getAppInfo() != null) {
                Map<String, Object> appInfoJson = new LinkedHashMap<>();
                appInfoJson.put("name", localization.getAppInfo().getName());
                appInfoJson.put("subtitle", localization.getAppInfo().getSubtitle());
                appInfoJson.put("privacyPolicyUrl", localization.getAppInfo().getPrivacyPolicyUrl());
                appInfoJson.put("privacyChoicesUrl", localization.getAppInfo().getPrivacyChoicesUrl());
                localizationJson.put("appInfo", appInfoJson);
            }

            if (localization.getVersion() != null) {
                Map<String, Object> versionJson = new LinkedHashMap<>();
                versionJson.put("versionString", localization.getVersion().getVersionString());
                versionJson.put("description", localization.getVersion().getDescription());
                versionJson.put("keywords", localization.getVersion().getKeywords());
                versionJson.put("whatsNew", localization.getVersion().getWhatsNew());
                versionJson.put("promotionalText", localization.getVersion().getPromotionalText());
                versionJson.put("marketingUrl", localization.getVersion().getMarketingUrl());
                versionJson.put("supportUrl", localization.getVersion().getSupportUrl());
                localizationJson.put("version", versionJson);
            }

            Path localizationFile = localizationsDir.resolve(localization.getLocale() + ".json");
            writeJson(localizationFile, localizationJson);
        }
    }

    private void writeJson(Path file, Object data) throws IOException {
        String json = objectMapper.writeValueAsString(data);

        if (dryRun) {
            System.out.println("[DRY RUN] Would write: " + file);
            if (verbose) {
                System.out.println(json);
            }
        } else {
            Files.writeString(file, json);
            if (verbose) {
                System.out.println("Wrote: " + file);
            }
        }
    }
}
