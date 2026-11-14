package com.nexusplaylabss.freecell.ads;

/**
 * The one place that decides which ad unit a format uses in this run.
 *
 * Debug builds always use Google's test unit. In release, an active mediator config with a
 * non-empty code wins; otherwise the {@code ads_config} unit is used; an empty result means that
 * format is not requested at all.
 */
public final class AdUnitResolver {
    private AdUnitResolver() {
    }

    public static String resolve(boolean debugBuild, String testUnit,
                                 AdmobMediatorConfig mediator, String mediatorUnit,
                                 String adsConfigUnit) {
        if (debugBuild) return testUnit;
        if (mediator != null && mediator.isActive() && mediatorUnit != null
                && !mediatorUnit.isEmpty()) {
            return mediatorUnit;
        }
        return adsConfigUnit == null ? "" : adsConfigUnit;
    }
}
