package hi.sierra.greedy_meshing.client;

import net.minecraft.core.SectionPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GreedyDebugStore {
    private static final Map<Long, List<DebugQuad>> SECTION_QUADS = new ConcurrentHashMap<>();

    private GreedyDebugStore() {
    }

    public static void setSectionQuads(long sectionKey, List<DebugQuad> quads) {
        if (quads.isEmpty()) {
            SECTION_QUADS.remove(sectionKey);
            return;
        }
        SECTION_QUADS.put(sectionKey, List.copyOf(quads));
    }

    public static void clear() {
        SECTION_QUADS.clear();
    }

    public static void clearSection(long sectionKey) {
        SECTION_QUADS.remove(sectionKey);
    }

    public static List<DebugQuad> getQuads() {
        if (SECTION_QUADS.isEmpty()) {
            return List.of();
        }
        return SECTION_QUADS.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    public static int countQuads() {
        if (SECTION_QUADS.isEmpty()) {
            return 0;
        }

        return SECTION_QUADS.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    public static int countSections() {
        return SECTION_QUADS.size();
    }

    public static List<DebugQuad> getQuadsNear(int sectionX, int sectionY, int sectionZ, int radius) {
        if (SECTION_QUADS.isEmpty()) {
            return List.of();
        }

        return SECTION_QUADS.entrySet().stream()
                .filter(e -> {
                    long key = e.getKey();
                    int sx = SectionPos.x(key);
                    int sy = SectionPos.y(key);
                    int sz = SectionPos.z(key);
                    return Math.abs(sx - sectionX) <= radius
                            && Math.abs(sy - sectionY) <= radius
                            && Math.abs(sz - sectionZ) <= radius;
                })
                .flatMap(e -> e.getValue().stream())
                .toList();
    }

    public static int countQuadsNear(int sectionX, int sectionY, int sectionZ, int radius) {
        if (SECTION_QUADS.isEmpty()) {
            return 0;
        }

        return SECTION_QUADS.entrySet().stream()
                .filter(e -> {
                    long key = e.getKey();
                    int sx = SectionPos.x(key);
                    int sy = SectionPos.y(key);
                    int sz = SectionPos.z(key);
                    return Math.abs(sx - sectionX) <= radius
                            && Math.abs(sy - sectionY) <= radius
                            && Math.abs(sz - sectionZ) <= radius;
                })
                .mapToInt(e -> e.getValue().size())
                .sum();
    }

    public static int countSectionsNear(int sectionX, int sectionY, int sectionZ, int radius) {
        if (SECTION_QUADS.isEmpty()) {
            return 0;
        }

        return (int) SECTION_QUADS.keySet().stream()
                .filter(key -> {
                    int sx = SectionPos.x(key);
                    int sy = SectionPos.y(key);
                    int sz = SectionPos.z(key);
                    return Math.abs(sx - sectionX) <= radius
                            && Math.abs(sy - sectionY) <= radius
                            && Math.abs(sz - sectionZ) <= radius;
                })
                .count();
    }

    public record DebugQuad(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3
    ) {
    }
}
