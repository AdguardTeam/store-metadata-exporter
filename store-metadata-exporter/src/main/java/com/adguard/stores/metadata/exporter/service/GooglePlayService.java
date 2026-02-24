package com.adguard.stores.metadata.exporter.service;

import com.adguard.stores.metadata.exporter.model.AppMetadata;
import com.adguard.stores.metadata.exporter.model.LocalizationMetadata;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.AndroidPublisherScopes;
import com.google.api.services.androidpublisher.model.AppEdit;
import com.google.api.services.androidpublisher.model.Listing;
import com.google.api.services.androidpublisher.model.ListingsListResponse;
import com.google.api.services.androidpublisher.model.Track;
import com.google.api.services.androidpublisher.model.TrackRelease;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GooglePlayService {

    private final AndroidPublisher publisher;

    public GooglePlayService(String serviceAccountJson) throws IOException, GeneralSecurityException {
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                .createScoped(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER));

        this.publisher = new AndroidPublisher.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("store-metadata-exporter")
                .build();
    }

    public AppMetadata fetchAppMetadata(String packageName) throws IOException {
        // Create an edit to read data
        AppEdit edit = publisher.edits().insert(packageName, null).execute();
        String editId = edit.getId();

        try {
            List<LocalizationMetadata> localizations = new ArrayList<>();

            // Fetch listings (store page info per language)
            ListingsListResponse listingsResponse = publisher.edits().listings()
                    .list(packageName, editId).execute();

            if (listingsResponse.getListings() != null) {
                for (Listing listing : listingsResponse.getListings()) {
                    LocalizationMetadata localization = LocalizationMetadata.builder()
                            .locale(listing.getLanguage())
                            .appInfo(LocalizationMetadata.AppInfoData.builder()
                                    .name(listing.getTitle())
                                    .subtitle(listing.getShortDescription())
                                    .build())
                            .version(LocalizationMetadata.VersionData.builder()
                                    .description(listing.getFullDescription())
                                    .build())
                            .build();
                    localizations.add(localization);
                }
            }

            // Fetch current production version
            String currentVersion = null;
            Instant versionCreatedAt = null;
            try {
                Track productionTrack = publisher.edits().tracks()
                        .get(packageName, editId, "production").execute();
                if (productionTrack.getReleases() != null && !productionTrack.getReleases().isEmpty()) {
                    TrackRelease latestRelease = productionTrack.getReleases().get(0);
                    currentVersion = latestRelease.getName();
                    if (currentVersion == null && latestRelease.getVersionCodes() != null 
                            && !latestRelease.getVersionCodes().isEmpty()) {
                        currentVersion = String.valueOf(latestRelease.getVersionCodes().get(0));
                    }
                }
            } catch (IOException e) {
                // Track might not exist, ignore
            }

            return AppMetadata.builder()
                    .appId(packageName)
                    .bundleId(packageName)
                    .currentVersion(currentVersion)
                    .versionCreatedAt(versionCreatedAt)
                    .localizations(localizations)
                    .build();

        } finally {
            // Delete the edit (we're only reading, not committing changes)
            try {
                publisher.edits().delete(packageName, editId).execute();
            } catch (IOException e) {
                // Ignore cleanup errors
            }
        }
    }
}
