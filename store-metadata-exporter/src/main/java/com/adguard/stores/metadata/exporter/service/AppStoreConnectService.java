package com.adguard.stores.metadata.exporter.service;

import com.adguard.stores.appstoreconnect.api.*;
import com.adguard.stores.appstoreconnect.model.*;
import com.adguard.stores.appstoreconnect.ApiClient;
import com.adguard.stores.appstoreconnect.ApiException;
import com.adguard.stores.metadata.exporter.model.AppMetadata;
import com.adguard.stores.metadata.exporter.model.LocalizationMetadata;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.nio.charset.StandardCharsets;

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
                        .description(attrs.getDescription())
                        .keywords(attrs.getKeywords())
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
        long iat = now.getEpochSecond();
        long exp = now.plus(20, ChronoUnit.MINUTES).getEpochSecond();

        // Build header
        String header = "{\"alg\":\"ES256\",\"kid\":\"" + keyId + "\",\"typ\":\"JWT\"}";
        
        // Build payload
        String payload = "{\"iss\":\"" + issuerId + "\",\"iat\":" + iat + ",\"exp\":" + exp + ",\"aud\":\"appstoreconnect-v1\"}";
        
        // Base64url encode header and payload
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String encodedHeader = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        
        // Create signature input
        String signatureInput = encodedHeader + "." + encodedPayload;
        
        // Sign with ES256 (ECDSA with SHA-256)
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(signatureInput.getBytes(StandardCharsets.UTF_8));
        byte[] derSignature = signature.sign();
        
        // Convert DER signature to JWS format (R || S, each 32 bytes)
        byte[] jwsSignature = derToJws(derSignature);
        String encodedSignature = encoder.encodeToString(jwsSignature);
        
        return signatureInput + "." + encodedSignature;
    }

    /**
     * Convert DER-encoded ECDSA signature to JWS format (R || S).
     * DER format: 0x30 [total-length] 0x02 [r-length] [r] 0x02 [s-length] [s]
     * JWS format: [r (32 bytes)] [s (32 bytes)]
     */
    private byte[] derToJws(byte[] der) {
        int offset = 2; // Skip 0x30 and total length
        
        // Parse R
        if (der[offset] != 0x02) throw new IllegalArgumentException("Invalid DER signature");
        offset++;
        int rLength = der[offset++] & 0xFF;
        byte[] r = new byte[rLength];
        System.arraycopy(der, offset, r, 0, rLength);
        offset += rLength;
        
        // Parse S
        if (der[offset] != 0x02) throw new IllegalArgumentException("Invalid DER signature");
        offset++;
        int sLength = der[offset++] & 0xFF;
        byte[] s = new byte[sLength];
        System.arraycopy(der, offset, s, 0, sLength);
        
        // Create JWS signature (each component padded/trimmed to 32 bytes for P-256)
        byte[] jwsSignature = new byte[64];
        copyWithPadding(r, jwsSignature, 0, 32);
        copyWithPadding(s, jwsSignature, 32, 32);
        
        return jwsSignature;
    }

    private void copyWithPadding(byte[] src, byte[] dest, int destOffset, int length) {
        if (src.length == length) {
            System.arraycopy(src, 0, dest, destOffset, length);
        } else if (src.length < length) {
            // Pad with leading zeros
            System.arraycopy(src, 0, dest, destOffset + (length - src.length), src.length);
        } else {
            // Trim leading zeros (src is longer, typically has leading 0x00)
            System.arraycopy(src, src.length - length, dest, destOffset, length);
        }
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
