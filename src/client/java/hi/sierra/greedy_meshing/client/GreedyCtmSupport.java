package hi.sierra.greedy_meshing.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Detects blocks affected by OptiFine-format connected-texture ("CTM") or random-texture resource
 * pack entries (parsed from {@code assets/*&#47;optifine/{ctm,random}/**&#47;*.properties}), so those
 * blocks can be excluded from greedy merging: their real rendered texture is chosen per-position
 * from neighbouring blocks by mods like Continuity, which a single merged quad can't reproduce
 * (greedy meshing only ever samples one arbitrary texture for the whole merged run).
 *
 * <p>This parses the pack files directly rather than detecting a CTM mod's own runtime wrapper
 * classes — an earlier attempt at the latter (checking for Continuity's {@code CtmBlockStateModel})
 * over-triggered, because Continuity wraps every block model defensively as soon as CTM is enabled
 * anywhere, not just the ones actually matched by a pack's properties. The properties format itself
 * is a stable, documented, external spec, so matching against it directly is the reliable signal.</p>
 *
 * <p>Known limitation: only the common {@code matchBlocks}/{@code matchTiles} keys are read. Entries
 * that restrict the effect to specific faces ({@code faces=}), biomes, or block states aren't
 * narrowed further — a matched block is excluded from merging entirely, on every face, which trades
 * a bit of unclaimed optimization for correctness rather than risking a missed case.</p>
 */
public final class GreedyCtmSupport {
    private static volatile Set<String> matchedBlockIds = Set.of();
    private static volatile List<Pattern> tilePatterns = List.of();

    /** Distinct BlockStates actually excluded from merging so far, for the F3 overlay. Reset on reload. */
    private static final Set<BlockState> excludedStates = ConcurrentHashMap.newKeySet();

    private GreedyCtmSupport() {
    }

    public static void reload(ResourceManager manager) {
        excludedStates.clear();
        Set<String> blockIds = new HashSet<>();
        List<Pattern> patterns = new ArrayList<>();

        //? if >=1.21.11 {
        /*Map<Identifier, Resource> resources = manager.listResources("optifine",
                loc -> loc.getPath().endsWith(".properties")
                        && (loc.getPath().contains("/ctm/") || loc.getPath().contains("/random/")));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            parseInto(entry.getKey().getNamespace(), entry.getValue(), blockIds, patterns);
        }
        *///?} else {
        Map<ResourceLocation, Resource> resources = manager.listResources("optifine",
                loc -> loc.getPath().endsWith(".properties")
                        && (loc.getPath().contains("/ctm/") || loc.getPath().contains("/random/")));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            parseInto(entry.getKey().getNamespace(), entry.getValue(), blockIds, patterns);
        }
        //?}

        matchedBlockIds = blockIds;
        tilePatterns = patterns;
    }

    private static void parseInto(String defaultNamespace, Resource resource, Set<String> blockIds, List<Pattern> patterns) {
        try (InputStream in = resource.open()) {
            Properties props = new Properties();
            props.load(in);

            String matchBlocks = props.getProperty("matchBlocks");
            if (matchBlocks != null) {
                for (String id : splitEntries(matchBlocks)) {
                    blockIds.add(normalizeBlockId(id));
                }
            }

            String matchTiles = props.getProperty("matchTiles");
            if (matchTiles != null) {
                for (String tile : splitEntries(matchTiles)) {
                    patterns.add(globToPattern(normalizeTileId(defaultNamespace, tile)));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Malformed or unreadable properties file — skip it rather than fail resource reload.
        }
    }

    private static List<String> splitEntries(String value) {
        List<String> out = new ArrayList<>();
        for (String token : value.split("[\\s,]+")) {
            token = token.trim();
            if (!token.isEmpty()) {
                out.add(token);
            }
        }
        return out;
    }

    /** Block IDs default to the "minecraft" namespace when none is given, per OptiFine convention. */
    private static String normalizeBlockId(String id) {
        String normalized = id.contains(":") ? id : "minecraft:" + id;
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * Texture references default to the properties file's own namespace (not "minecraft") and to
     * the conventional {@code block/} folder when no path segment is present.
     */
    private static String normalizeTileId(String defaultNamespace, String tile) {
        String namespace = defaultNamespace;
        String path = tile;
        int colon = tile.indexOf(':');
        if (colon >= 0) {
            namespace = tile.substring(0, colon);
            path = tile.substring(colon + 1);
        }
        if (!path.contains("/")) {
            path = "block/" + path;
        }
        return namespace + ":" + path;
    }

    private static Pattern globToPattern(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    public static boolean matchesBlock(BlockState state) {
        if (matchedBlockIds.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean matched = matchedBlockIds.contains(id.toLowerCase(Locale.ROOT));
        if (matched) {
            excludedStates.add(state);
        }
        return matched;
    }

    public static boolean matchesTile(BlockState state, TextureAtlasSprite sprite) {
        if (tilePatterns.isEmpty()) {
            return false;
        }
        String name = sprite.contents().name().toString().toLowerCase(Locale.ROOT);
        for (Pattern pattern : tilePatterns) {
            if (pattern.matcher(name).matches()) {
                excludedStates.add(state);
                return true;
            }
        }
        return false;
    }

    /** Distinct blocks excluded from merging by a CTM/random match so far, for the F3 overlay. */
    public static int excludedCount() {
        return excludedStates.size();
    }
}
