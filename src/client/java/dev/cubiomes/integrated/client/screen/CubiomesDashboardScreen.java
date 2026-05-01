package dev.cubiomes.integrated.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import dev.cubiomes.integrated.nativebridge.NativeCubiomes;
import dev.cubiomes.integrated.nativebridge.NativeCubiomes.StructureType;
import dev.cubiomes.integrated.search.SearchConfig;
import dev.cubiomes.integrated.search.SearchProgress;
import dev.cubiomes.integrated.search.SearchResult;
import dev.cubiomes.integrated.search.SeedSearcher;
import dev.cubiomes.integrated.search.filter.BiomeFilter;
import dev.cubiomes.integrated.search.filter.StructureFilter;
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
        STRUCTURE
    }

    private interface EditableFilter {
        FilterType type();

        boolean enabled();

        void setEnabled(boolean enabled);

        String summary();

        String toEditorString();

        void applyEditorString(String editorString);

        EditableFilter copy();
    }

    private final SeedSearcher searcher = new SeedSearcher();
    private final Screen parent;
    private final DashboardSettings settings;

    private TextFieldWidget startSeedField;
    private TextFieldWidget endSeedField;
    private TextFieldWidget strideField;
    private TextFieldWidget maxResultsField;

    private CompletableFuture<List<SearchResult>> inFlight;
    private final List<EditableFilter> filters = new ArrayList<>();
    private final List<SearchResult> results = new ArrayList<>();
    private SearchProgress progress = new SearchProgress(0, 0, 0);
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

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Biome"), b -> openFilterEditor(new BiomeAtFilter(), true)).dimensions(left, top + 84, 92, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Add Structure"), b -> openFilterEditor(new StructureFilterEntry(), true)).dimensions(left + 96, top + 84, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Toggle"), b -> toggleSelectedFilter()).dimensions(left + 206, top + 84, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Remove"), b -> removeSelectedFilter()).dimensions(left + 280, top + 84, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Up"), b -> moveSelectedFilter(-1)).dimensions(left + 354, top + 84, 42, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Down"), b -> moveSelectedFilter(1)).dimensions(left + 400, top + 84, 56, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Edit Selected"), b -> applySelectedFilterEdits()).dimensions(left + 526, top + 56, 112, 20).build());

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
        progress = new SearchProgress(0, 0, 0);

        SearchConfig config = buildConfig();
        statusText = "Search started: cubiomes pipeline";

        inFlight = searcher.start(
            config,
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
            biomeFilters
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

        openFilterEditor(filter.copy(), false);
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
    }

    private void openFilterEditor(EditableFilter filter, boolean creatingNewFilter) {
        if (client == null) {
            return;
        }

        client.setScreen(new FilterEditorScreen(this, filter, creatingNewFilter));
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
        };
    }

    private static String biomeLabel(int biomeId) {
        String label = NativeCubiomes.biomeName(NativeCubiomes.mcVersion1211(), biomeId);
        if (label == null || label.isBlank()) {
            return "biome " + biomeId;
        }
        return label + " (id=" + biomeId + ")";
    }

    private static String structureLabel(StructureType structureType) {
        String label = NativeCubiomes.structureName(structureType.ordinal());
        if (label == null || label.isBlank()) {
            return structureType.name();
        }
        return label;
    }

    private static List<BiomeOption> buildBiomeOptions() {
        List<BiomeOption> options = new ArrayList<>();
        for (int biomeId = 0; biomeId < 256; biomeId++) {
            String label = NativeCubiomes.biomeName(NativeCubiomes.mcVersion1211(), biomeId);
            if (label == null || label.isBlank()) {
                continue;
            }
            if ("unknown".equalsIgnoreCase(label) || "minecraft:unknown".equalsIgnoreCase(label)) {
                continue;
            }
            options.add(new BiomeOption(biomeId, label));
        }
        options.sort(Comparator.comparing(BiomeOption::label));
        return options;
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
        context.drawText(textRenderer, Text.literal("Edit Selected Filter"), left, 80, 0xD0D0D0, false);

        int enabledCubiomes = 0;
        for (EditableFilter filter : filters) {
            if (!filter.enabled()) {
                continue;
            }
            enabledCubiomes++;
        }

        context.drawText(textRenderer, Text.literal("Pipeline: Cubiomes filters=" + enabledCubiomes), left, 140, 0xFFD37A, false);

        int y = 408;
        context.drawText(textRenderer, Text.literal("Seeds scanned: " + progress.scanned()), left, y, 0xD0D0D0, false);
        y += 12;
        context.drawText(textRenderer, Text.literal("Stage1 passed: " + progress.stage1Passed()), left, y, 0xD0D0D0, false);
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
            default -> null;
        };
    }

    private DashboardSettings.FilterSettings createSettingsFromFilter(EditableFilter filter) {
        String type = switch (filter.type()) {
            case BIOME_AT -> "BIOME_AT";
            case STRUCTURE -> "STRUCTURE";
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
            return biomeLabel(biomeId) + " scale=" + scale + " pos=(" + x + "," + z + ")";
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

        @Override
        public EditableFilter copy() {
            BiomeAtFilter copy = new BiomeAtFilter();
            copy.enabled = enabled;
            copy.biomeId = biomeId;
            copy.scale = scale;
            copy.x = x;
            copy.y = y;
            copy.z = z;
            return copy;
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
            return structureLabel(structureType) + " region=(" + regionX + "," + regionZ + ") rangeX=" + minX + ".." + maxX + " rangeZ=" + minZ + ".." + maxZ;
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

        @Override
        public EditableFilter copy() {
            StructureFilterEntry copy = new StructureFilterEntry();
            copy.enabled = enabled;
            copy.structureType = structureType;
            copy.regionX = regionX;
            copy.regionZ = regionZ;
            copy.minX = minX;
            copy.maxX = maxX;
            copy.minZ = minZ;
            copy.maxZ = maxZ;
            return copy;
        }
    }

    private record BiomeOption(int biomeId, String label) {
    }

    private final class FilterEditorScreen extends Screen {
        private static final int LIST_X = 16;
        private static final int LIST_Y = 44;
        private static final int LIST_WIDTH = 250;
        private static final int LIST_HEIGHT = 168;
        private static final int LIST_ROW_HEIGHT = 12;
        private static final int LIST_ROWS = 14;

        private final Screen parentScreen;
        private final boolean creatingNewFilter;
        private final List<BiomeOption> biomeOptions = buildBiomeOptions();
        private final List<StructureType> structureOptions = List.of(StructureType.values());
        private EditableFilter workingFilter;
        private TextFieldWidget scaleField;
        private TextFieldWidget xField;
        private TextFieldWidget yField;
        private TextFieldWidget zField;
        private TextFieldWidget regionXField;
        private TextFieldWidget regionZField;
        private TextFieldWidget minXField;
        private TextFieldWidget maxXField;
        private TextFieldWidget minZField;
        private TextFieldWidget maxZField;
        private String errorText = "";
        private int biomeScrollIndex;
        private int structureScrollIndex;
        private int selectedBiomeIndex;
        private int selectedStructureIndex;

        private FilterEditorScreen(Screen parentScreen, EditableFilter workingFilter, boolean creatingNewFilter) {
            super(Text.literal("Edit Filter"));
            this.parentScreen = parentScreen;
            this.workingFilter = workingFilter;
            this.creatingNewFilter = creatingNewFilter;
        }

        @Override
        protected void init() {
            int rightX = 292;
            int topY = 44;

            addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveChanges()).dimensions(width - 214, 16, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> closeEditor()).dimensions(width - 108, 16, 92, 20).build());

            if (workingFilter instanceof BiomeAtFilter biome) {
                selectedBiomeIndex = findBiomeIndex(biome.biomeId);
                biomeScrollIndex = clampScroll(selectedBiomeIndex, biomeOptions.size());
                scaleField = addField(rightX + 52, topY + 28, 70, Integer.toString(biome.scale));
                xField = addField(rightX + 52, topY + 52, 70, Integer.toString(biome.x));
                yField = addField(rightX + 52, topY + 76, 70, Integer.toString(biome.y));
                zField = addField(rightX + 52, topY + 100, 70, Integer.toString(biome.z));
            } else if (workingFilter instanceof StructureFilterEntry structure) {
                selectedStructureIndex = findStructureIndex(structure.structureType);
                structureScrollIndex = clampScroll(selectedStructureIndex, structureOptions.size());
                regionXField = addField(rightX + 52, topY + 28, 70, Integer.toString(structure.regionX));
                regionZField = addField(rightX + 52, topY + 52, 70, Integer.toString(structure.regionZ));
                minXField = addField(rightX + 52, topY + 76, 70, Integer.toString(structure.minX));
                maxXField = addField(rightX + 132, topY + 76, 70, Integer.toString(structure.maxX));
                minZField = addField(rightX + 52, topY + 100, 70, Integer.toString(structure.minZ));
                maxZField = addField(rightX + 132, topY + 100, 70, Integer.toString(structure.maxZ));
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);

            int centerX = width / 2;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Filter Editor"), centerX, 12, 0xFFFFFF);
            context.drawText(textRenderer, Text.literal(errorText.isBlank() ? "Click an item in the list to select it." : errorText), 16, 28, errorText.isBlank() ? 0xD0D0D0 : 0xFF8080, false);

            context.fill(LIST_X - 2, LIST_Y - 2, LIST_X + LIST_WIDTH + 2, LIST_Y + LIST_HEIGHT + 2, 0x66000000);
            context.drawText(textRenderer, Text.literal(workingFilter.type() == FilterType.BIOME_AT ? "Biomes" : "Structures"), LIST_X, LIST_Y - 12, 0xFFFFFF, false);

            if (workingFilter instanceof BiomeAtFilter biome) {
                renderBiomeList(context, mouseX, mouseY, biome);
                drawBiomeLabels(context, biome);
            } else if (workingFilter instanceof StructureFilterEntry structure) {
                renderStructureList(context, mouseX, mouseY, structure);
                drawStructureLabels(context, structure);
            }
        }

        private void renderBiomeList(DrawContext context, int mouseX, int mouseY, BiomeAtFilter biome) {
            int rows = Math.min(LIST_ROWS, Math.max(0, biomeOptions.size() - biomeScrollIndex));
            for (int row = 0; row < rows; row++) {
                int optionIndex = biomeScrollIndex + row;
                BiomeOption option = biomeOptions.get(optionIndex);
                int rowY = LIST_Y + row * LIST_ROW_HEIGHT;
                boolean selected = option.biomeId() == biome.biomeId;
                boolean hovered = mouseX >= LIST_X && mouseX <= LIST_X + LIST_WIDTH && mouseY >= rowY && mouseY < rowY + LIST_ROW_HEIGHT;
                if (selected || hovered) {
                    context.fill(LIST_X, rowY, LIST_X + LIST_WIDTH, rowY + LIST_ROW_HEIGHT, selected ? 0xAA335577 : 0x55335577);
                }
                context.drawText(textRenderer, Text.literal(option.label()), LIST_X + 4, rowY + 2, selected ? 0xFFFFFF : 0xD0D0D0, false);
            }
        }

        private void renderStructureList(DrawContext context, int mouseX, int mouseY, StructureFilterEntry structure) {
            int rows = Math.min(LIST_ROWS, Math.max(0, structureOptions.size() - structureScrollIndex));
            for (int row = 0; row < rows; row++) {
                int optionIndex = structureScrollIndex + row;
                StructureType option = structureOptions.get(optionIndex);
                int rowY = LIST_Y + row * LIST_ROW_HEIGHT;
                boolean selected = option == structure.structureType;
                boolean hovered = mouseX >= LIST_X && mouseX <= LIST_X + LIST_WIDTH && mouseY >= rowY && mouseY < rowY + LIST_ROW_HEIGHT;
                if (selected || hovered) {
                    context.fill(LIST_X, rowY, LIST_X + LIST_WIDTH, rowY + LIST_ROW_HEIGHT, selected ? 0xAA335577 : 0x55335577);
                }
                context.drawText(textRenderer, Text.literal(structureLabel(option)), LIST_X + 4, rowY + 2, selected ? 0xFFFFFF : 0xD0D0D0, false);
            }
        }

        private void drawBiomeLabels(DrawContext context, BiomeAtFilter biome) {
            int rightX = 292;
            int topY = 44;
            context.drawText(textRenderer, Text.literal("Selected: " + biomeLabel(biome.biomeId)), rightX, topY + 2, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Scale"), rightX, topY + 30, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("X"), rightX, topY + 54, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Y"), rightX, topY + 78, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Z"), rightX, topY + 102, 0xD0D0D0, false);
        }

        private void drawStructureLabels(DrawContext context, StructureFilterEntry structure) {
            int rightX = 292;
            int topY = 44;
            context.drawText(textRenderer, Text.literal("Selected: " + structureLabel(structure.structureType)), rightX, topY + 2, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Region X"), rightX, topY + 30, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Region Z"), rightX, topY + 54, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Min X"), rightX, topY + 78, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Max X"), rightX + 80, topY + 78, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Min Z"), rightX, topY + 102, 0xD0D0D0, false);
            context.drawText(textRenderer, Text.literal("Max Z"), rightX + 80, topY + 102, 0xD0D0D0, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (workingFilter instanceof BiomeAtFilter) {
                int clicked = rowAt(mouseX, mouseY, LIST_X, LIST_Y, LIST_ROW_HEIGHT, LIST_ROWS, biomeOptions.size());
                if (clicked >= 0) {
                    selectedBiomeIndex = biomeScrollIndex + clicked;
                    selectedBiomeIndex = Math.max(0, Math.min(selectedBiomeIndex, biomeOptions.size() - 1));
                    if (selectedBiomeIndex >= 0 && selectedBiomeIndex < biomeOptions.size()) {
                        ((BiomeAtFilter) workingFilter).biomeId = biomeOptions.get(selectedBiomeIndex).biomeId();
                    }
                    return true;
                }
            } else if (workingFilter instanceof StructureFilterEntry) {
                int clicked = rowAt(mouseX, mouseY, LIST_X, LIST_Y, LIST_ROW_HEIGHT, LIST_ROWS, structureOptions.size());
                if (clicked >= 0) {
                    selectedStructureIndex = structureScrollIndex + clicked;
                    selectedStructureIndex = Math.max(0, Math.min(selectedStructureIndex, structureOptions.size() - 1));
                    if (selectedStructureIndex >= 0 && selectedStructureIndex < structureOptions.size()) {
                        ((StructureFilterEntry) workingFilter).structureType = structureOptions.get(selectedStructureIndex);
                    }
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (workingFilter instanceof BiomeAtFilter) {
                biomeScrollIndex = clampScroll(biomeScrollIndex - (int) Math.signum(verticalAmount), biomeOptions.size());
                return true;
            }
            if (workingFilter instanceof StructureFilterEntry) {
                structureScrollIndex = clampScroll(structureScrollIndex - (int) Math.signum(verticalAmount), structureOptions.size());
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        private void saveChanges() {
            try {
                if (workingFilter instanceof BiomeAtFilter biome) {
                    biome.scale = parseField(scaleField, "scale");
                    biome.x = parseField(xField, "x");
                    biome.y = parseField(yField, "y");
                    biome.z = parseField(zField, "z");
                    if (selectedBiomeIndex >= 0 && selectedBiomeIndex < biomeOptions.size()) {
                        biome.biomeId = biomeOptions.get(selectedBiomeIndex).biomeId();
                    }
                } else if (workingFilter instanceof StructureFilterEntry structure) {
                    structure.regionX = parseField(regionXField, "region X");
                    structure.regionZ = parseField(regionZField, "region Z");
                    structure.minX = parseField(minXField, "min X");
                    structure.maxX = parseField(maxXField, "max X");
                    structure.minZ = parseField(minZField, "min Z");
                    structure.maxZ = parseField(maxZField, "max Z");
                    if (structure.minX > structure.maxX) {
                        throw new IllegalArgumentException("min X must be <= max X");
                    }
                    if (structure.minZ > structure.maxZ) {
                        throw new IllegalArgumentException("min Z must be <= max Z");
                    }
                    if (selectedStructureIndex >= 0 && selectedStructureIndex < structureOptions.size()) {
                        structure.structureType = structureOptions.get(selectedStructureIndex);
                    }
                }

                if (creatingNewFilter) {
                    filters.add(workingFilter.copy());
                    selectedFilterIndex = filters.size() - 1;
                } else if (selectedFilterIndex >= 0 && selectedFilterIndex < filters.size()) {
                    filters.set(selectedFilterIndex, workingFilter.copy());
                }

                statusText = "Updated filter: " + typeLabel(workingFilter.type());
                closeEditor();
            } catch (IllegalArgumentException exception) {
                errorText = exception.getMessage();
            }
        }

        private int parseField(TextFieldWidget field, String name) {
            if (field == null) {
                throw new IllegalArgumentException("Missing field: " + name);
            }
            try {
                return Integer.parseInt(field.getText().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid " + name + ": " + field.getText());
            }
        }

        private void closeEditor() {
            if (client != null) {
                client.setScreen(parentScreen);
            }
        }

        @Override
        public void close() {
            closeEditor();
        }

        private int findBiomeIndex(int biomeId) {
            for (int index = 0; index < biomeOptions.size(); index++) {
                if (biomeOptions.get(index).biomeId() == biomeId) {
                    return index;
                }
            }
            return biomeOptions.isEmpty() ? -1 : 0;
        }

        private int findStructureIndex(StructureType structureType) {
            for (int index = 0; index < structureOptions.size(); index++) {
                if (structureOptions.get(index) == structureType) {
                    return index;
                }
            }
            return structureOptions.isEmpty() ? -1 : 0;
        }

        private int clampScroll(int scrollIndex, int optionCount) {
            int maxScroll = Math.max(0, optionCount - LIST_ROWS);
            return Math.max(0, Math.min(maxScroll, scrollIndex));
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
