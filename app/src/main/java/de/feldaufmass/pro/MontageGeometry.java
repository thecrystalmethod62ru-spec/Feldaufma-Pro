package de.feldaufmass.pro;

/** Pure geometric checks; all distances are expressed in metres. */
final class MontageGeometry {
    private MontageGeometry() {}

    static boolean positive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    static double requiredTrenchWidth(double offset, double envelope) {
        return 2 * Math.abs(offset) + envelope;
    }

    static boolean exceedsExcavationDepth(double cover, double height, double depth) {
        return cover + height > depth + 0.001;
    }

    static boolean elevationMismatch(double ground, double top, double cover) {
        return Math.abs(ground - top - cover) > 0.01;
    }
    static boolean verticallySeparated(double stationDelta, double c1, double c2,
                                       double h1, double h2, double z1, double z2) {
        if (!positive(h1) || !positive(h2)) return false;
        if (Double.isFinite(z1) && Double.isFinite(z2))
            return z1 - h1 >= z2 || z2 - h2 >= z1;
        return stationDelta <= 0.001 && Double.isFinite(c1) && c1 >= 0
                && Double.isFinite(c2) && c2 >= 0
                && (c1 + h1 <= c2 || c2 + h2 <= c1);
    }
}
