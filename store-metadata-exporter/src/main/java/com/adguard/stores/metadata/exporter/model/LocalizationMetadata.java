package com.adguard.stores.metadata.exporter.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocalizationMetadata {
    private String locale;
    private AppInfoData appInfo;
    private VersionData version;

    @Data
    @Builder
    public static class AppInfoData {
        private String name;
        private String subtitle;
        private String privacyPolicyUrl;
        private String privacyChoicesUrl;
    }

    @Data
    @Builder
    public static class VersionData {
        private String versionString;
        private String description;
        private String keywords;
        private String whatsNew;
        private String promotionalText;
        private String marketingUrl;
        private String supportUrl;
    }
}
