package com.adguard.stores.metadata.exporter.service;

import com.adguard.stores.appstoreconnect.api.*;
import com.adguard.stores.appstoreconnect.model.*;
import com.adguard.stores.appstoreconnect.ApiClient;
import com.adguard.stores.appstoreconnect.ApiException;
import com.adguard.stores.metadata.exporter.model.AppMetadata;
import com.adguard.stores.metadata.exporter.model.LocalizationMetadata;
import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class AppStoreConnectService {

    private static final String BASE_URL = "https://api.appstoreconnect.apple.com";

    private final ApiClient apiClient;
    private final String issuerId;
    private final String keyId;
    private final String privateKeyContent;

    public AppStoreConnectService(String issuerId, String keyId, String privateKeyContent) throws Exception {
        this.issuerId = issuerId;
        this.keyId = keyId;
        this.privateKeyContent = privateKeyContent;

        String jwtToken = generateJwtToken();

        this.apiClient = new ApiClient();
        this.apiClient.updateBaseUri(BASE_URL);
        this.apiClient.setRequestInterceptor(builder -> {
            builder.header("Authorization", "Bearer " + jwtToken);
        });
    }

    public List<App> fetchAllApps() throws ApiException {
        AppsApi appsApi = new AppsApi(apiClient);
        // appsGetCollection has 56 parameters + optional headers
        AppsResponse response = appsApi.appsGetCollection(
                null, null, null, null, null, null, null, null, null, null, // filters 1-10
                null, null, null, null, null, null, null, null, null, null, // fields 11-20
                null, null, null, null, null, null, null, null, null, null, // fields 21-30
                null, null, null, null, null, null, null, null, null, null, // limits 31-40
                null, null, null, null, null, null, null, null, null, null, // limits 41-50
                null, null, null, null, null, null                          // limits 51-56
        );
        return response.getData();
    }

    public AppMetadata fetchAppMetadata(String appId, String bundleId) throws ApiException {
        Map<String, LocalizationMetadata> localizationMap = new HashMap<>();
        String currentVersion = null;

        // Fetch AppInfo localizations
        var appInfos = fetchAppInfos(appId);
        for (var appInfo : appInfos) {
            var appInfoLocalizations = fetchAppInfoLocalizations(appInfo.getId());
            for (var localization : appInfoLocalizations) {
                var attrs = localization.getAttributes();
                String locale = attrs.getLocale();

                LocalizationMetadata existing = localizationMap.getOrDefault(locale,
                        LocalizationMetadata.builder().locale(locale).build());

                existing.setAppInfo(LocalizationMetadata.AppInfoData.builder()
                        .name(attrs.getName())
                        .subtitle(attrs.getSubtitle())
                        .privacyPolicyUrl(attrs.getPrivacyPolicyUrl())
                        .privacyChoicesUrl(attrs.getPrivacyChoicesUrl())
                        .build());

                localizationMap.put(locale, existing);
            }
        }

        // Fetch latest live AppStoreVersion
        var liveVersions = fetchLiveAppStoreVersions(appId);
        if (!liveVersions.isEmpty()) {
            var liveVersion = liveVersions.get(0);
            currentVersion = liveVersion.getAttributes().getVersionString();

            var versionLocalizations = fetchAppStoreVersionLocalizations(liveVersion.getId());
            for (var localization : versionLocalizations) {
                var attrs = localization.getAttributes();
                String locale = attrs.getLocale();

                LocalizationMetadata existing = localizationMap.getOrDefault(locale,
                        LocalizationMetadata.builder().locale(locale).build());

                existing.setVersion(LocalizationMetadata.VersionData.builder()
                        .versionString(currentVersion)
                        .description(attrs.getDescription())
                        .keywords(attrs.getKeywords())
                        .whatsNew(attrs.getWhatsNew())
                        .promotionalText(attrs.getPromotionalText())
                        .marketingUrl(attrs.getMarketingUrl() != null ? attrs.getMarketingUrl().toString() : null)
                        .supportUrl(attrs.getSupportUrl() != null ? attrs.getSupportUrl().toString() : null)
                        .build());

                localizationMap.put(locale, existing);
            }
        }

        return AppMetadata.builder()
                .appId(appId)
                .bundleId(bundleId)
                .currentVersion(currentVersion)
                .lastUpdated(Instant.now())
                .localizations(new ArrayList<>(localizationMap.values()))
                .build();
    }

    private List<AppInfo> fetchAppInfos(String appId) throws ApiException {
        AppsApi api = new AppsApi(apiClient);
        // appsAppInfosGetToManyRelated(id, fieldsAppInfos, fieldsApps, fieldsAgeRatingDeclarations, 
        //   fieldsAppInfoLocalizations, fieldsAppCategories, limit, include, limitAppInfoLocalizations)
        AppInfosResponse response = api.appsAppInfosGetToManyRelated(
                appId, null, null, null, null, null, null, null, null
        );
        return response.getData();
    }

    private List<AppInfoLocalization> fetchAppInfoLocalizations(String appInfoId) throws ApiException {
        AppInfosApi api = new AppInfosApi(apiClient);
        // appInfosAppInfoLocalizationsGetToManyRelated(id, filterLocale, fieldsAppInfoLocalizations, 
        //   fieldsAppInfos, limit, include)
        AppInfoLocalizationsResponse response = api.appInfosAppInfoLocalizationsGetToManyRelated(
                appInfoId, null, null, null, null, null
        );
        return response.getData();
    }

    private List<AppStoreVersion> fetchLiveAppStoreVersions(String appId) throws ApiException {
        AppsApi api = new AppsApi(apiClient);
        // appsAppStoreVersionsGetToManyRelated has 24 parameters:
        // id, filterPlatform, filterVersionString, filterAppStoreState, filterAppVersionState, filterId,
        // fieldsAppStoreVersions, fieldsApps, fieldsAgeRatingDeclarations, fieldsAppStoreVersionLocalizations,
        // fieldsBuilds, fieldsAppStoreVersionPhasedReleases, fieldsGameCenterAppVersions, fieldsRoutingAppCoverages,
        // fieldsAppStoreReviewDetails, fieldsAppStoreVersionSubmissions, fieldsAppClipDefaultExperiences,
        // fieldsAppStoreVersionExperiments, fieldsAlternativeDistributionPackages,
        // limit, include, limitAppStoreVersionLocalizations, limitAppStoreVersionExperiments, limitAppStoreVersionExperimentsV2
        AppStoreVersionsResponse response = api.appsAppStoreVersionsGetToManyRelated(
                appId,
                null,                           // filterPlatform
                null,                           // filterVersionString
                List.of("READY_FOR_SALE"),      // filterAppStoreState
                null,                           // filterAppVersionState
                null,                           // filterId
                null, null, null, null, null, null, null, null, null, null, null, null, null, // fields
                null, null, null, null, null    // limit, include, limits
        );
        return response.getData();
    }

    private List<AppStoreVersionLocalization> fetchAppStoreVersionLocalizations(String versionId) throws ApiException {
        AppStoreVersionsApi api = new AppStoreVersionsApi(apiClient);
        // appStoreVersionsAppStoreVersionLocalizationsGetToManyRelated has 12 parameters:
        // id, filterLocale, fieldsAppStoreVersionLocalizations, fieldsAppStoreVersions, fieldsAppScreenshotSets,
        // fieldsAppPreviewSets, fieldsAppKeywords, limit, include, limitAppScreenshotSets, limitAppPreviewSets, limitSearchKeywords
        AppStoreVersionLocalizationsResponse response = api.appStoreVersionsAppStoreVersionLocalizationsGetToManyRelated(
                versionId, null, null, null, null, null, null, null, null, null, null, null
        );
        return response.getData();
    }

    private String generateJwtToken() throws Exception {
        String formattedKey = formatPrivateKey(privateKeyContent);
        PrivateKey privateKey = parsePrivateKey(formattedKey);

        Instant now = Instant.now();
        Instant expiration = now.plus(20, ChronoUnit.MINUTES);

        return Jwts.builder()
                .header()
                .keyId(keyId)
                .type("JWT")
                .add("alg", "ES256")
                .and()
                .issuer(issuerId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .audience().add("appstoreconnect-v1").and()
                .signWith(privateKey, Jwts.SIG.ES256)
                .compact();
    }

    private String formatPrivateKey(String key) {
        String processed = key.replace("\\n", "\n");

        if (processed.contains("-----BEGIN")) {
            return processed;
        }

        String base64Content = processed
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < base64Content.length(); i += 64) {
            pem.append(base64Content, i, Math.min(i + 64, base64Content.length()));
            pem.append("\n");
        }
        pem.append("-----END PRIVATE KEY-----\n");
        return pem.toString();
    }

    private PrivateKey parsePrivateKey(String pemContent) throws Exception {
        // Extract base64 content from PEM
        String base64Key = pemContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(keySpec);
    }
}
