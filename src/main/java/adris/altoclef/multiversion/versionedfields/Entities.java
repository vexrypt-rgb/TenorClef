package adris.altoclef.multiversion.versionedfields;

import net.minecraft.entity.Entity;

/**
 * A helper class implementing entities that are not yet supported in certain versions
 */
public class Entities {

    public static final Class<? extends Entity> UNSUPPORTED;
    public static final Class<? extends Entity> WARDEN;
    public static final Class<? extends Entity> GLOW_SQUID;
    /** Piglin brutes exist only on 1.16.2+ (absent on classic RSG 1.16.1). */
    public static final Class<? extends Entity> PIGLIN_BRUTE;

    static {
        UNSUPPORTED = VersionedFieldHelper.getUnsupportedEntityClass();

        //#if MC >= 11904
        WARDEN = net.minecraft.entity.mob.WardenEntity.class;
        //#else
        //$$ WARDEN = UNSUPPORTED;
        //#endif

        //#if MC >= 11701
        GLOW_SQUID = net.minecraft.entity.passive.GlowSquidEntity.class;
        //#else
        //$$ GLOW_SQUID = UNSUPPORTED;
        //#endif

        //#if MC >= 11602
        PIGLIN_BRUTE = net.minecraft.entity.mob.PiglinBruteEntity.class;
        //#else
        //$$ PIGLIN_BRUTE = UNSUPPORTED;
        //#endif
    }



}
