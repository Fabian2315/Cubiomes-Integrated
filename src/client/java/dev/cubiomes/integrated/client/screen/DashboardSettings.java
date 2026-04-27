package dev.cubiomes.integrated.client.screen;

import java.util.ArrayList;
import java.util.List;

public final class DashboardSettings {
    private static final DashboardSettings SESSION = new DashboardSettings();

    private String startSeed = "0";
    private String endSeed = "1000000";
    private String stride = "1";
    private String maxResults = "250";
    private boolean seedMapPopupEnabled = true;
    private final List<FilterSettings> filters = new ArrayList<>();

    private DashboardSettings() {
        filters.add(new FilterSettings("BIOME_AT", true, "biomeId=1, scale=4, x=0, y=0, z=0"));
        filters.add(new FilterSettings("STRUCTURE", true, "structure=VILLAGE, regionX=0, regionZ=0, minX=-512, maxX=512, minZ=-512, maxZ=512"));
        filters.add(new FilterSettings("SPAWN_TOP_BLOCK", false, "block=minecraft:grass_block, radius=0, minY=-64, maxY=320, budget=32"));
    }

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
        return SESSION;
    }

    public void save() {
        // Session-backed settings are kept in memory only.
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
