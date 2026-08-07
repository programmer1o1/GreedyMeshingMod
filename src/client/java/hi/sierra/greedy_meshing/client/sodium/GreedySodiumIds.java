package hi.sierra.greedy_meshing.client.sodium;

//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}

/**
 * Only used by {@link GreedySodiumConfigEntryPoint} (Sodium's new config API), kept as its own
 * top-level file so that class's Sodium-API stonecutter guard never has to nest another guard inside
 * its own comment block — Identifier vs ResourceLocation is purely a Minecraft-version fact anyway.
 */
final class GreedySodiumIds {
    private GreedySodiumIds() {
    }

    //? if >=1.21.11 {
    /*static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath("greedy_meshing", path);
    }
    *///?} else {
    static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath("greedy_meshing", path);
    }
    //?}
}
