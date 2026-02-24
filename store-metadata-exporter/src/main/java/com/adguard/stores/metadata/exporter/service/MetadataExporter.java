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

    public void export(AppMetadata appMetadata, String storeType) throws IOException {
        Path appDir = outputDir.resolve(storeType).resolve(appMetadata.getBundleId());
        Path localizationsDir = appDir.resolve("localizations");

        if (!dryRun) {
            Files.createDirectories(localizationsDir);
        }

        // Write metadata.json
        Map<String, Object> metadataJson = new LinkedHashMap<>();
        metadataJson.put("appId", appMetadata.getAppId());
        metadataJson.put("bundleId", appMetadata.getBundleId());
        metadataJson.put("currentVersion", appMetadata.getCurrentVersion());
        if (appMetadata.getVersionCreatedAt() != null) {
            metadataJson.put("versionCreatedAt", appMetadata.getVersionCreatedAt().toString());
        }

        Path metadataFile = appDir.resolve("metadata.json");
        writeJson(metadataFile, metadataJson);

        // Write localization files
        for (LocalizationMetadata localization : appMetadata.getLocalizations()) {
            Map<String, Object> localizationJson = new LinkedHashMap<>();
            localizationJson.put("locale", localization.getLocale());

            if (localization.getAppInfo() != null) {
                Map<String, Object> appInfoJson = new LinkedHashMap<>();
                putIfNotNull(appInfoJson, "name", localization.getAppInfo().getName());
                putIfNotNull(appInfoJson, "subtitle", localization.getAppInfo().getSubtitle());
                putIfNotNull(appInfoJson, "privacyPolicyUrl", localization.getAppInfo().getPrivacyPolicyUrl());
                putIfNotNull(appInfoJson, "privacyChoicesUrl", localization.getAppInfo().getPrivacyChoicesUrl());
                if (!appInfoJson.isEmpty()) {
                    localizationJson.put("appInfo", appInfoJson);
                }
            }

            if (localization.getVersion() != null) {
                Map<String, Object> versionJson = new LinkedHashMap<>();
                putIfNotNull(versionJson, "description", localization.getVersion().getDescription());
                putIfNotNull(versionJson, "keywords", localization.getVersion().getKeywords());
                putIfNotNull(versionJson, "promotionalText", localization.getVersion().getPromotionalText());
                putIfNotNull(versionJson, "marketingUrl", localization.getVersion().getMarketingUrl());
                putIfNotNull(versionJson, "supportUrl", localization.getVersion().getSupportUrl());
                if (!versionJson.isEmpty()) {
                    localizationJson.put("version", versionJson);
                }
            }

            Path localizationFile = localizationsDir.resolve(localization.getLocale() + ".json");
            writeJson(localizationFile, localizationJson);
        }
    }

    private void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
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
