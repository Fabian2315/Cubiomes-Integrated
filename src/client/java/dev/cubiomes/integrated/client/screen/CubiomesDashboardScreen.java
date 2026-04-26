package dev.cubiomes.integrated.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import dev.cubiomes.integrated.nativebridge.NativeCubiomes;
import dev.cubiomes.integrated.nativebridge.NativeCubiomes.StructureType;
import dev.cubiomes.integrated.search.SearchConfig;
import dev.cubiomes.integrated.search.SearchProgress;
import dev.cubiomes.integrated.search.SearchResult;
import dev.cubiomes.integrated.search.SeedSearcher;
import dev.cubiomes.integrated.search.filter.BiomeFilter;
import dev.cubiomes.integrated.search.filter.StructureFilter;
import dev.cubiomes.integrated.search.filter.TerrainFilter;
import dev.cubiomes.integrated.client.world.MinecraftTerrainVerifier;
import dev.cubiomes.integrated.client.world.WorldLauncher;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class CubiomesDashboardScreen extends Screen {
    private enum Tab {
        STRUCTURES,
        BIOMES,
        TERRAIN
    }

    private final SeedSearcher searcher = new SeedSearcher();
    private final MinecraftTerrainVerifier terrainVerifier = new MinecraftTerrainVerifier();

    private TextFieldWidget startSeedField;
    private TextFieldWidget endSeedField;
    private TextFieldWidget biomeIdField;
    private TextFieldWidget structureTypeField;
    private TextFieldWidget minXField;
    private TextFieldWidget maxXField;
    private TextFieldWidget minZField;
    private TextFieldWidget maxZField;
    private TextFieldWidget topBlockField;
    private TextFieldWidget terrainBatchField;

    private Tab activeTab = Tab.STRUCTURES;
    private CompletableFuture<List<SearchResult>> inFlight;
    private final List<SearchResult> results = new ArrayList<>();
    private SearchProgress progress = new SearchProgress(0, 0, 0, 0);

    public CubiomesDashboardScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        int left = 16;
        int top = 24;

        startSeedField = addField(left, top, "0");
        endSeedField = addField(left + 130, top, "1000000");
        biomeIdField = addField(left, top + 30, "1");
        structureTypeField = addField(left + 130, top + 30, StructureType.VILLAGE.name());
        minXField = addField(left, top + 60, "-512");
        maxXField = addField(left + 65, top + 60, "512");
        minZField = addField(left + 130, top + 60, "-512");
        maxZField = addField(left + 195, top + 60, "512");
        topBlockField = addField(left, top + 90, "minecraft:mossy_cobblestone");
        terrainBatchField = addField(left + 210, top + 90, "64");

        addDrawableChild(ButtonWidget.builder(Text.literal("Structures"), b -> activeTab = Tab.STRUCTURES).dimensions(left, height - 28, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Biomes"), b -> activeTab = Tab.BIOMES).dimensions(left + 94, height - 28, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Terrain"), b -> activeTab = Tab.TERRAIN).dimensions(left + 168, height - 28, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Start Search"), b -> startSearch()).dimensions(width - 240, 24, 110, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> cancelSearch()).dimensions(width - 124, 24, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Generate & Join"), b -> generateAndJoin()).dimensions(width - 240, 50, 206, 20).build());
    }

    private TextFieldWidget addField(int x, int y, String value) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, 60, 18, Text.empty());
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private void startSearch() {
        cancelSearch();
        results.clear();
        progress = new SearchProgress(0, 0, 0, 0);

        SearchConfig config = buildConfig();
        inFlight = searcher.start(
            config,
            terrainVerifier,
            p -> progress = p,
            results::add
        );
    }

    private SearchConfig buildConfig() {
        long startSeed = parseLong(startSeedField.getText(), 0L);
        long endSeed = parseLong(endSeedField.getText(), startSeed + 1_000_000L);
        int biomeId = parseInt(biomeIdField.getText(), 1);

        StructureType structureType = parseStructure(structureTypeField.getText());
        StructureFilter structureFilter = new StructureFilter(
            structureType,
            parseInt(minXField.getText(), -512),
            parseInt(maxXField.getText(), 512),
            parseInt(minZField.getText(), -512),
            parseInt(maxZField.getText(), 512)
        );

        TerrainFilter terrainFilter = new TerrainFilter(
            activeTab == Tab.TERRAIN,
            topBlockField.getText(),
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            true,
            parseInt(terrainBatchField.getText(), 64)
        );

        return new SearchConfig(
            startSeed,
            Math.max(startSeed + 1, endSeed),
            1,
            250,
            NativeCubiomes.mcVersion1211(),
            0,
            List.of(structureFilter),
            List.of(new BiomeFilter(biomeId, 4, 0, 0, 0)),
            terrainFilter
        );
    }

    private void cancelSearch() {
        searcher.cancel();
        if (inFlight != null) {
            inFlight.cancel(true);
            inFlight = null;
        }
    }

    private void generateAndJoin() {
        if (client == null || results.isEmpty()) {
            return;
        }
        WorldLauncher.launchOrOpenCreateWorld(client, results.get(0).seed(), this);
    }

    @Override
    public void close() {
        cancelSearch();
        searcher.close();
        super.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int left = 16;
        int y = 8;

        context.drawText(textRenderer, Text.literal("Cubiomes Integrated Dashboard"), left, y, 0xFFFFFF, true);
        y += 104;

        context.drawText(textRenderer, Text.literal("Active Tab: " + activeTab), left, y, 0xD0D0D0, false);
        y += 12;
        context.drawText(textRenderer, Text.literal("Seeds scanned: " + progress.scanned()), left, y, 0xD0D0D0, false);
        y += 12;
        context.drawText(textRenderer, Text.literal("Stage1 passed: " + progress.stage1Passed() + " | Stage2 checked: " + progress.stage2Checked()), left, y, 0xD0D0D0, false);
        y += 12;
        context.drawText(textRenderer, Text.literal("Accepted: " + progress.accepted()), left, y, 0x7CFF7C, false);
        y += 16;

        if (activeTab == Tab.TERRAIN) {
            context.drawText(textRenderer, Text.literal("Slow filter warning: block-level checks are expensive and budget-limited."), left, y, 0xFFB347, false);
            y += 14;
        }

        context.drawText(textRenderer, Text.literal("Results:"), left, y, 0xFFFFFF, false);
        y += 12;

        int visible = Math.min(12, results.size());
        for (int i = 0; i < visible; i++) {
            SearchResult result = results.get(i);
            context.drawText(textRenderer, Text.literal("- " + result.seed() + " (" + result.reason() + ")"), left, y + i * 10, 0xB5EAEA, false);
        }
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
            return StructureType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return StructureType.VILLAGE;
        }
    }
}
