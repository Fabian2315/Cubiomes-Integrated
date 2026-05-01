package dev.cubiomes.integrated.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import dev.cubiomes.integrated.client.world.MinecraftTerrainVerifier;
import dev.cubiomes.integrated.nativebridge.NativeCubiomes;
import dev.cubiomes.integrated.nativebridge.NativeCubiomes.StructureType;
import dev.cubiomes.integrated.search.SearchConfig;
import dev.cubiomes.integrated.search.SearchProgress;
import dev.cubiomes.integrated.search.SearchResult;
import dev.cubiomes.integrated.search.SeedSearcher;
import dev.cubiomes.integrated.search.filter.BiomeFilter;
import dev.cubiomes.integrated.search.filter.StructureFilter;
import dev.cubiomes.integrated.search.filter.TerrainFilter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class CubiomesDashboardScreen extends Screen {
    private static final int FILTER_LIST_X = 16;
    private static final int FILTER_LIST_Y = 168;
    private static final int FILTER_ROW_HEIGHT = 11;
    private static final int FILTER_ROWS_VISIBLE = 10;
    private static final int RESULTS_X = 430;
    private static final int RESULTS_Y = 168;
    private static final int RESULTS_ROW_HEIGHT = 11;
    private static final int RESULTS_VISIBLE = 14;

    private enum FilterType {
        BIOME_AT,
        STRUCTURE,
        SPAWN_TOP_BLOCK
    }

    private interface EditableFilter {
        FilterType type();

        boolean enabled();

        void setEnabled(boolean enabled);

        String summary();

        String toEditorString();

        void applyEditorString(String editorString);
    }

    private final SeedSearcher searcher = new SeedSearcher();
    private final MinecraftTerrainVerifier terrainVerifier = new MinecraftTerrainVerifier();
    private final Screen parent;
    private final DashboardSettings settings;

    private TextFieldWidget startSeedField;
    private TextFieldWidget endSeedField;
    private TextFieldWidget strideField;
    private TextFieldWidget maxResultsField;
    private TextFieldWidget selectedFilterEditorField;

    private CompletableFuture<List<SearchResult>> inFlight;
    private final List<EditableFilter> filters = new ArrayList<>();
    private final List<SearchResult> results = new ArrayList<>();
    private SearchProgress progress = new SearchProgress(0, 0, 0, 0);
    private int selectedFilterIndex = -1;
    private int selectedResultIndex = -1;
    private boolean seedMapPopupEnabled = true;
    private String statusText = "Ready";

    public CubiomesDashboardScreen(Text title) {
        this(title, null);
    }

    public CubiomesDashboardScreen(Text title, Screen parent) {
        super(title);
        this.parent = parent;
        this.settings = DashboardSettings.load();
    }

    @Override
    protected void init() {
        int left = 16;
        int top = 28;

        startSeedField = addField(left + 64, top, 110, "0");
        endSeedField = addField(left + 240, top, 110, "1000000");
        strideField = addField(left + 64, top + 24, 50, "1");
        maxResultsField = addField(left + 240, top + 24, 70, "250");
        selectedFilterEditorField = addField(left + 100, top + 56, 420, "");
        selectedFilterEditorField.setMaxLength(2048);

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Biome"), b -> addFilter(new BiomeAtFilter())).dimensions(left, top + 84, 92, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Structure"), b -> addFilter(new StructureFilterEntry())).dimensions(left + 96, top + 84, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Top Block"), b -> addFilter(new SpawnTopBlockFilter())).dimensions(left + 206, top + 84, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Toggle"), b -> toggleSelectedFilter()).dimensions(left + 320, top + 84, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), b -> removeSelectedFilter()).dimensions(left + 394, top + 84, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Up"), b -> moveSelectedFilter(-1)).dimensions(left + 468, top + 84, 42, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Down"), b -> moveSelectedFilter(1)).dimensions(left + 514, top + 84, 56, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Apply Selected"), b -> applySelectedFilterEdits()).dimensions(left + 526, top + 56, 112, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Start Search"), b -> startSearch()).dimensions(width - 246, 24, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> cancelSearch()).dimensions(width - 130, 24, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy Seed"), b -> copySelectedSeedToClipboard()).dimensions(width - 246, 50, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Open Seed Map"), b -> openSeedMapForSelection()).dimensions(width - 140, 50, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(seedMapPopupButtonLabel(), this::toggleSeedMapPopup).dimensions(width - 246, 76, 102, 20).build());

        loadSettingsIntoUI();
    }

    private TextFieldWidget addField(int x, int y, int width, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.empty());
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void startSearch() {
        cancelSearch();
        applySelectedFilterEdits();
        results.clear();
        selectedResultIndex = -1;
        progress = new SearchProgress(0, 0, 0, 0);

        SearchConfig config = buildConfig();
        statusText = config.requiresMinecraftHandoff()
            ? "Search started: cubiomes prefilter + Minecraft handoff"
            : "Search started: cubiomes-only pipeline";

        inFlight = searcher.start(
            config,
            terrainVerifier,
            p -> progress = p,
            result -> {
                results.add(result);
                if (selectedResultIndex < 0) {
                    selectedResultIndex = 0;
                }
            }
        );
    }

    private SearchConfig buildConfig() {
        long startSeed = parseLong(startSeedField.getText(), 0L);
        long endSeed = parseLong(endSeedField.getText(), startSeed + 1_000_000L);
        int stride = Math.max(1, parseInt(strideField.getText(), 1));
        int maxResults = Math.max(1, parseInt(maxResultsField.getText(), 250));

        List<BiomeFilter> biomeFilters = new ArrayList<>();
        List<StructureFilter> structureFilters = new ArrayList<>();
        List<TerrainFilter> terrainFilters = new ArrayList<>();

        for (EditableFilter filter : filters) {
            if (filter instanceof BiomeAtFilter biome) {
                biomeFilters.add(new BiomeFilter(
                    biome.enabled,
                    biome.biomeId,
                    biome.scale,
                    biome.x,
                    biome.y,
                    biome.z
                ));
                continue;
            }

            if (filter instanceof StructureFilterEntry structure) {
                structureFilters.add(new StructureFilter(
                    structure.enabled,
                    structure.structureType,
                    structure.regionX,
                    structure.regionZ,
                    structure.minX,
                    structure.maxX,
                    structure.minZ,
                    structure.maxZ
                ));
                continue;
            }

            if (filter instanceof SpawnTopBlockFilter spawnTop) {
                terrainFilters.add(new TerrainFilter(
                    spawnTop.enabled,
                    spawnTop.blockId,
                    spawnTop.spawnRadius,
                    spawnTop.minTopY,
                    spawnTop.maxTopY,
                    true,
                    Math.max(1, spawnTop.javaVerificationBudget)
                ));
            }
        }

        return new SearchConfig(
            startSeed,
            Math.max(startSeed + 1, endSeed),
            stride,
            maxResults,
            NativeCubiomes.mcVersion1211(),
            0,
            structureFilters,
            biomeFilters,
            terrainFilters
        );
    }

    private void cancelSearch() {
        searcher.cancel();
        if (inFlight != null) {
            inFlight.cancel(true);
            inFlight = null;
        }
        statusText = "Search cancelled";
    }

    private void copySelectedSeedToClipboard() {
        if (client == null || results.isEmpty()) {
            statusText = "No seeds found yet";
            return;
        }
        SearchResult result = getSelectedResult();
        if (result == null) {
            statusText = "No seed selected";
            return;
        }
        client.keyboard.setClipboard(Long.toString(result.seed()));
        statusText = "Seed " + result.seed() + " copied to clipboard";
    }

    private void openSeedMapForSelection() {
        SearchResult result = getSelectedResult();
        if (result == null || client == null) {
            return;
        }

        String url = SeedMapViewer.buildSeedMapUrl(result.seed());
        if (seedMapPopupEnabled) {
            client.setScreen(new SeedMapConfirmScreen(this, url));
            return;
        }

        if (SeedMapViewer.openInBrowser(url)) {
            statusText = "Opened seed map for seed " + result.seed();
        } else {
            statusText = "Failed to open browser for seed map";
        }
    }

    private SearchResult getSelectedResult() {
        if (results.isEmpty()) {
            return null;
        }
        if (selectedResultIndex < 0 || selectedResultIndex >= results.size()) {
            selectedResultIndex = 0;
        }
        return results.get(selectedResultIndex);
    }

    private void addFilter(EditableFilter filter) {
        filters.add(filter);
        selectFilter(filters.size() - 1);
        statusText = "Added filter: " + typeLabel(filter.type());
    }

    private void toggleSelectedFilter() {
        EditableFilter filter = getSelectedFilter();
        if (filter == null) {
            return;
        }
        filter.setEnabled(!filter.enabled());
        statusText = "Filter " + typeLabel(filter.type()) + " is now " + (filter.enabled() ? "ON" : "OFF");
    }

    private void removeSelectedFilter() {
        EditableFilter filter = getSelectedFilter();
        if (filter == null) {
            return;
        }
        filters.remove(selectedFilterIndex);
        if (filters.isEmpty()) {
            selectedFilterIndex = -1;
            selectedFilterEditorField.setText("");
        } else {
            selectFilter(Math.min(selectedFilterIndex, filters.size() - 1));
        }
        statusText = "Removed filter: " + typeLabel(filter.type());
    }

    private void moveSelectedFilter(int delta) {
        EditableFilter filter = getSelectedFilter();
        if (filter == null) {
            return;
        }

        int target = selectedFilterIndex + delta;
        if (target < 0 || target >= filters.size()) {
            return;
        }

        filters.set(selectedFilterIndex, filters.get(target));
        filters.set(target, filter);
        selectFilter(target);
        statusText = "Reordered filters";
    }

    private void applySelectedFilterEdits() {
        EditableFilter filter = getSelectedFilter();
        if (filter == null) {
            return;
        }

        try {
            filter.applyEditorString(selectedFilterEditorField.getText());
            selectedFilterEditorField.setText(filter.toEditorString());
            statusText = "Updated filter: " + typeLabel(filter.type());
        } catch (IllegalArgumentException exception) {
            statusText = "Invalid filter format: " + exception.getMessage();
        }
    }

    private EditableFilter getSelectedFilter() {
        if (selectedFilterIndex < 0 || selectedFilterIndex >= filters.size()) {
            return null;
        }
        return filters.get(selectedFilterIndex);
    }

    private void selectFilter(int index) {
        if (index < 0 || index >= filters.size()) {
            return;
        }
        selectedFilterIndex = index;
        selectedFilterEditorField.setText(filters.get(index).toEditorString());
    }

    private void initializeDefaultFilters() {
        filters.add(new BiomeAtFilter());
        filters.add(new StructureFilterEntry());
        SpawnTopBlockFilter spawnTop = new SpawnTopBlockFilter();
        spawnTop.enabled = false;
        filters.add(spawnTop);
    }

    private static Map<String, String> parseEditorMap(String editorString) {
        Map<String, String> values = new HashMap<>();
        for (String part : editorString.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex == trimmed.length() - 1) {
                throw new IllegalArgumentException("use key=value pairs");
            }

            String key = trimmed.substring(0, equalsIndex).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(equalsIndex + 1).trim();
            values.put(key, value);
        }
        return values;
    }

    private static int requiredInt(Map<String, String> values, String key, int fallback) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return parseInt(raw, fallback);
    }

    private static String requiredString(Map<String, String> values, String key, String fallback) {
        String raw = values.get(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw;
    }

    private static String typeLabel(FilterType type) {
        return switch (type) {
            case BIOME_AT -> "Biome";
            case STRUCTURE -> "Structure";
            case SPAWN_TOP_BLOCK -> "Spawn Top Block";
        };
    }

    private Text seedMapPopupButtonLabel() {
        return Text.literal(seedMapPopupEnabled ? "Popup: ON" : "Popup: OFF");
    }

    private void toggleSeedMapPopup(ButtonWidget button) {
        seedMapPopupEnabled = !seedMapPopupEnabled;
        button.setMessage(seedMapPopupButtonLabel());
    }

    @Override
    public void close() {
        cancelSearch();
        searcher.close();
        saveSettingsFromUI();
        settings.save();
        if (client != null && parent != null) {
            client.setScreen(parent);
            return;
        }

        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int left = 16;

        context.drawText(textRenderer, Text.literal("Cubiomes Integrated Dashboard"), left, 8, 0xFFFFFF, true);

        context.drawText(textRenderer, Text.literal("Start"), left, 32, 0xD0D0D0, false);
        context.drawText(textRenderer, Text.literal("End"), left + 186, 32, 0xD0D0D0, false);
        context.drawText(textRenderer, Text.literal("Stride"), left, 56, 0xD0D0D0, false);
        context.drawText(textRenderer, Text.literal("Max Results"), left + 186, 56, 0xD0D0D0, false);
        context.drawText(textRenderer, Text.literal("Edit Selected Filter (key=value, comma-separated)"), left, 80, 0xD0D0D0, false);

        int enabledCubiomes = 0;
        int enabledMinecraft = 0;
        for (EditableFilter filter : filters) {
            if (!filter.enabled()) {
                continue;
            }
            if (filter.type() == FilterType.SPAWN_TOP_BLOCK) {
                enabledMinecraft++;
            } else {
                enabledCubiomes++;
            }
        }

        context.drawText(textRenderer, Text.literal("Pipeline: Cubiomes filters=" + enabledCubiomes + " | Minecraft handoff filters=" + enabledMinecraft), left, 140, 0xFFD37A, false);

        int y = 408;
        context.drawText(textRenderer, Text.literal("Seeds scanned: " + progress.scanned()), left, y, 0xD0D0D0, false);
        y += 12;
        context.drawText(textRenderer, Text.literal("Stage1 passed: " + progress.stage1Passed() + " | Stage2 checked: " + progress.stage2Checked()), left, y, 0xD0D0D0, false);
        y += 12;
        context.drawText(textRenderer, Text.literal("Accepted: " + progress.accepted()), left, y, 0x7CFF7C, false);
        y += 16;
        context.drawText(textRenderer, Text.literal("Status: " + statusText), left, y, 0x9AD0FF, false);

        context.drawText(textRenderer, Text.literal("Filters (click row to select)"), FILTER_LIST_X, FILTER_LIST_Y - 12, 0xFFFFFF, false);
        int filterRows = Math.min(FILTER_ROWS_VISIBLE, filters.size());
        for (int i = 0; i < filterRows; i++) {
            EditableFilter filter = filters.get(i);
            String prefix = i == selectedFilterIndex ? "> " : "  ";
            String state = filter.enabled() ? "ON" : "OFF";
            String text = prefix + "[" + state + "] " + typeLabel(filter.type()) + " - " + filter.summary();
            int color = i == selectedFilterIndex ? 0xB5EAEA : 0xD0D0D0;
            context.drawText(textRenderer, Text.literal(text), FILTER_LIST_X, FILTER_LIST_Y + i * FILTER_ROW_HEIGHT, color, false);
        }

        context.drawText(textRenderer, Text.literal("Results (click seed to open seed map)"), RESULTS_X, RESULTS_Y - 12, 0xFFFFFF, false);
        int visible = Math.min(RESULTS_VISIBLE, results.size());
        for (int i = 0; i < visible; i++) {
            SearchResult result = results.get(i);
            String prefix = i == selectedResultIndex ? "> " : "  ";
            int color = i == selectedResultIndex ? 0x7CFF7C : 0xB5EAEA;
            context.drawText(textRenderer, Text.literal(prefix + result.seed() + " (" + result.reason() + ")"), RESULTS_X, RESULTS_Y + i * RESULTS_ROW_HEIGHT, color, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);

        int clickedFilter = rowAt(mouseX, mouseY, FILTER_LIST_X, FILTER_LIST_Y, FILTER_ROW_HEIGHT, FILTER_ROWS_VISIBLE, filters.size());
        if (clickedFilter >= 0) {
            selectFilter(clickedFilter);
            return true;
        }

        int clickedResult = rowAt(mouseX, mouseY, RESULTS_X, RESULTS_Y, RESULTS_ROW_HEIGHT, RESULTS_VISIBLE, results.size());
        if (clickedResult >= 0) {
            selectedResultIndex = clickedResult;
            openSeedMapForSelection();
            return true;
        }

        return handled;
    }

    private static int rowAt(double mouseX, double mouseY, int x, int y, int rowHeight, int visibleRows, int rowCount) {
        if (mouseX < x || mouseX > x + 420) {
            return -1;
        }
        if (mouseY < y || mouseY > y + (visibleRows * rowHeight)) {
            return -1;
        }
        int row = (int) ((mouseY - y) / rowHeight);
        if (row < 0 || row >= rowCount) {
            return -1;
        }
        return row;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(String s, long fallback) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static StructureType parseStructure(String value) {
        try {
            return StructureType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return StructureType.VILLAGE;
        }
    }

    private void loadSettingsIntoUI() {
        // Load basic search parameters
        if (startSeedField != null) {
            startSeedField.setText(settings.getStartSeed());
        }
        if (endSeedField != null) {
            endSeedField.setText(settings.getEndSeed());
        }
        if (strideField != null) {
            strideField.setText(settings.getStride());
        }
        if (maxResultsField != null) {
            maxResultsField.setText(settings.getMaxResults());
        }

        seedMapPopupEnabled = settings.isSeedMapPopupEnabled();

        // Load filters
        filters.clear();
        for (DashboardSettings.FilterSettings filterSettings : settings.getFilters()) {
            EditableFilter filter = createFilterFromSettings(filterSettings);
            if (filter != null) {
                filters.add(filter);
            }
        }

        // Select the first filter if available
        selectedFilterIndex = -1;
        if (selectedFilterIndex < 0 && !filters.isEmpty()) {
            selectFilter(0);
        }
    }

    private void saveSettingsFromUI() {
        // Save basic search parameters
        if (startSeedField != null) {
            settings.setStartSeed(startSeedField.getText());
        }
        if (endSeedField != null) {
            settings.setEndSeed(endSeedField.getText());
        }
        if (strideField != null) {
            settings.setStride(strideField.getText());
        }
        if (maxResultsField != null) {
            settings.setMaxResults(maxResultsField.getText());
        }

        settings.setSeedMapPopupEnabled(seedMapPopupEnabled);

        // Save filters
        settings.getFilters().clear();
        for (EditableFilter filter : filters) {
            DashboardSettings.FilterSettings filterSettings = createSettingsFromFilter(filter);
            if (filterSettings != null) {
                settings.getFilters().add(filterSettings);
            }
        }
    }

    private EditableFilter createFilterFromSettings(DashboardSettings.FilterSettings settings) {
        return switch (settings.type) {
            case "BIOME_AT" -> {
                BiomeAtFilter filter = new BiomeAtFilter();
                filter.applyEditorString(settings.data);
                filter.setEnabled(settings.enabled);
                yield filter;
            }
            case "STRUCTURE" -> {
                StructureFilterEntry filter = new StructureFilterEntry();
                filter.applyEditorString(settings.data);
                filter.setEnabled(settings.enabled);
                yield filter;
            }
            case "SPAWN_TOP_BLOCK" -> {
                SpawnTopBlockFilter filter = new SpawnTopBlockFilter();
                filter.applyEditorString(settings.data);
                filter.setEnabled(settings.enabled);
                yield filter;
            }
            default -> null;
        };
    }

    private DashboardSettings.FilterSettings createSettingsFromFilter(EditableFilter filter) {
        String type = switch (filter.type()) {
            case BIOME_AT -> "BIOME_AT";
            case STRUCTURE -> "STRUCTURE";
            case SPAWN_TOP_BLOCK -> "SPAWN_TOP_BLOCK";
        };
        return new DashboardSettings.FilterSettings(type, filter.enabled(), filter.toEditorString());
    }

    private static final class BiomeAtFilter implements EditableFilter {
        private boolean enabled = true;
        private int biomeId = 1;
        private int scale = 4;
        private int x = 0;
        private int y = 0;
        private int z = 0;

        @Override
        public FilterType type() {
            return FilterType.BIOME_AT;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String summary() {
            return "biomeId=" + biomeId + " scale=" + scale + " pos=(" + x + "," + z + ")";
        }

        @Override
        public String toEditorString() {
            return "biomeId=" + biomeId + ", scale=" + scale + ", x=" + x + ", y=" + y + ", z=" + z;
        }

        @Override
        public void applyEditorString(String editorString) {
            Map<String, String> values = parseEditorMap(editorString);
            biomeId = requiredInt(values, "biomeid", biomeId);
            scale = requiredInt(values, "scale", scale);
            x = requiredInt(values, "x", x);
            y = requiredInt(values, "y", y);
            z = requiredInt(values, "z", z);
        }
    }

    private static final class StructureFilterEntry implements EditableFilter {
        private boolean enabled = true;
        private StructureType structureType = StructureType.VILLAGE;
        private int regionX = 0;
        private int regionZ = 0;
        private int minX = -512;
        private int maxX = 512;
        private int minZ = -512;
        private int maxZ = 512;

        @Override
        public FilterType type() {
            return FilterType.STRUCTURE;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String summary() {
            return structureType + " region=(" + regionX + "," + regionZ + ") rangeX=" + minX + ".." + maxX + " rangeZ=" + minZ + ".." + maxZ;
        }

        @Override
        public String toEditorString() {
            return "type=" + structureType + ", regionX=" + regionX + ", regionZ=" + regionZ + ", minX=" + minX + ", maxX=" + maxX + ", minZ=" + minZ + ", maxZ=" + maxZ;
        }

        @Override
        public void applyEditorString(String editorString) {
            Map<String, String> values = parseEditorMap(editorString);
            structureType = parseStructure(requiredString(values, "type", structureType.name()));
            regionX = requiredInt(values, "regionx", regionX);
            regionZ = requiredInt(values, "regionz", regionZ);
            minX = requiredInt(values, "minx", minX);
            maxX = requiredInt(values, "maxx", maxX);
            minZ = requiredInt(values, "minz", minZ);
            maxZ = requiredInt(values, "maxz", maxZ);
        }
    }

    private static final class SpawnTopBlockFilter implements EditableFilter {
        private boolean enabled = true;
        private String blockId = "minecraft:stone";
        private int spawnRadius = 32;
        private int minTopY = Integer.MIN_VALUE;
        private int maxTopY = Integer.MAX_VALUE;
        private int javaVerificationBudget = 64;

        @Override
        public FilterType type() {
            return FilterType.SPAWN_TOP_BLOCK;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String summary() {
            return "block=" + blockId + " spawnRadius=" + spawnRadius + " budget=" + javaVerificationBudget;
        }

        @Override
        public String toEditorString() {
            return "block=" + blockId + ", radius=" + spawnRadius + ", minY=" + minTopY + ", maxY=" + maxTopY + ", budget=" + javaVerificationBudget;
        }

        @Override
        public void applyEditorString(String editorString) {
            Map<String, String> values = parseEditorMap(editorString);
            blockId = requiredString(values, "block", blockId);
            spawnRadius = Math.max(0, requiredInt(values, "radius", spawnRadius));
            minTopY = requiredInt(values, "miny", minTopY);
            maxTopY = requiredInt(values, "maxy", maxTopY);
            javaVerificationBudget = Math.max(1, requiredInt(values, "budget", javaVerificationBudget));
        }
    }

    private static final class SeedMapConfirmScreen extends Screen {
        private final Screen parent;
        private final String url;

        private SeedMapConfirmScreen(Screen parent, String url) {
            super(Text.literal("Open Seed Map"));
            this.parent = parent;
            this.url = url;
        }

        @Override
        protected void init() {
            int centerX = width / 2;
            int centerY = height / 2;
            addDrawableChild(ButtonWidget.builder(Text.literal("Open"), button -> {
                SeedMapViewer.openInBrowser(url);
                if (client != null) {
                    client.setScreen(parent);
                }
            }).dimensions(centerX - 105, centerY + 16, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
                if (client != null) {
                    client.setScreen(parent);
                }
            }).dimensions(centerX + 5, centerY + 16, 100, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);
            int centerX = width / 2;
            int centerY = height / 2;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Open external seed map viewer?"), centerX, centerY - 20, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(url), centerX, centerY - 6, 0xB5EAEA);
        }

        @Override
        public void close() {
            MinecraftClient minecraftClient = this.client;
            if (minecraftClient != null) {
                minecraftClient.setScreen(parent);
            }
        }
    }
}
