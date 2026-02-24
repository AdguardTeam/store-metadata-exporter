package com.adguard.stores.metadata.exporter.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AppMetadata {
    private String appId;
    private String bundleId;
    private String currentVersion;
    private Instant versionCreatedAt;
    private List<LocalizationMetadata> localizations;
}
