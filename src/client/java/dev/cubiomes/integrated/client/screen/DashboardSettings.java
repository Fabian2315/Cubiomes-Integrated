package dev.cubiomes.integrated.client.screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.cubiomes.integrated.CubiomesIntegratedMod;

public final class DashboardSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SETTINGS_FILE = "cubiomes_dashboard_settings.json";

    private String startSeed = "0";
    private String endSeed = "1000000";
    private String stride = "1";
    private String maxResults = "250";
    private boolean seedMapPopupEnabled = true;
    private final List<FilterSettings> filters = new ArrayList<>();

    public static class FilterSettings {
        public String type; // BIOME_AT, STRUCTURE, SPAWN_TOP_BLOCK
        public boolean enabled;
        public String data;

        public FilterSettings(String type, boolean enabled, String data) {
            this.type = type;
            this.enabled = enabled;
            this.data = data;
        }
    }

    public static DashboardSettings load() {
        Path configDir = getConfigDir();
        Path settingsFile = configDir.resolve(SETTINGS_FILE);

        if (!Files.exists(settingsFile)) {
            return new DashboardSettings();
        }

        try {
            String json = Files.readString(settingsFile);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null) {
                return new DashboardSettings();
            }

            DashboardSettings settings = new DashboardSettings();
            if (obj.has("startSeed")) {
                settings.startSeed = obj.get("startSeed").getAsString();
            }
            if (obj.has("endSeed")) {
                settings.endSeed = obj.get("endSeed").getAsString();
            }
            if (obj.has("stride")) {
                settings.stride = obj.get("stride").getAsString();
            }
            if (obj.has("maxResults")) {
                settings.maxResults = obj.get("maxResults").getAsString();
            }
            if (obj.has("seedMapPopupEnabled")) {
                settings.seedMapPopupEnabled = obj.get("seedMapPopupEnabled").getAsBoolean();
            }
            if (obj.has("filters")) {
                JsonArray filtersArray = obj.getAsJsonArray("filters");
                for (JsonElement element : filtersArray) {
                    JsonObject filterObj = element.getAsJsonObject();
                    FilterSettings filter = new FilterSettings(
                        filterObj.get("type").getAsString(),
                        filterObj.get("enabled").getAsBoolean(),
                        filterObj.get("data").getAsString()
                    );
                    settings.filters.add(filter);
                }
            }

            return settings;
        } catch (IOException | RuntimeException ex) {
            CubiomesIntegratedMod.LOGGER.error("Failed to load dashboard settings", ex);
            return new DashboardSettings();
        }
    }

    public void save() {
        Path configDir = getConfigDir();
        Path settingsFile = configDir.resolve(SETTINGS_FILE);

        try {
            Files.createDirectories(configDir);

            JsonObject obj = new JsonObject();
            obj.addProperty("startSeed", startSeed);
            obj.addProperty("endSeed", endSeed);
            obj.addProperty("stride", stride);
            obj.addProperty("maxResults", maxResults);
            obj.addProperty("seedMapPopupEnabled", seedMapPopupEnabled);

            JsonArray filtersArray = new JsonArray();
            for (FilterSettings filter : filters) {
                JsonObject filterObj = new JsonObject();
                filterObj.addProperty("type", filter.type);
                filterObj.addProperty("enabled", filter.enabled);
                filterObj.addProperty("data", filter.data);
                filtersArray.add(filterObj);
            }
            obj.add("filters", filtersArray);

            String json = GSON.toJson(obj);
            Files.writeString(settingsFile, json);
        } catch (IOException ex) {
            CubiomesIntegratedMod.LOGGER.error("Failed to save dashboard settings", ex);
        }
    }

    private static Path getConfigDir() {
        return Path.of(System.getProperty("user.home"), ".cubiomes_integrated");
    }

    // Getters
    public String getStartSeed() {
        return startSeed;
    }

    public void setStartSeed(String startSeed) {
        this.startSeed = startSeed;
    }

    public String getEndSeed() {
        return endSeed;
    }

    public void setEndSeed(String endSeed) {
        this.endSeed = endSeed;
    }

    public String getStride() {
        return stride;
    }

    public void setStride(String stride) {
        this.stride = stride;
    }

    public String getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(String maxResults) {
        this.maxResults = maxResults;
    }

    public boolean isSeedMapPopupEnabled() {
        return seedMapPopupEnabled;
    }

    public void setSeedMapPopupEnabled(boolean seedMapPopupEnabled) {
        this.seedMapPopupEnabled = seedMapPopupEnabled;
    }

    public List<FilterSettings> getFilters() {
        return filters;
    }
}
