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
}
