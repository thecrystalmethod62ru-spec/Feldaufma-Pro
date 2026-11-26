package de.feldaufmass.pro;

/** Schematic station-based geometry, distances in metres and areas in square metres. */
final class CivilGeometry {
    private CivilGeometry() {}

    static double overlapArea(double x, double y, double la, double lb,
                              double[] u, double[] v) {
        if (!Double.isFinite(x) || !Double.isFinite(y) ||
            !Double.isFinite(la) || !Double.isFinite(lb) || u == null || v == null ||
            la < 0 || lb < 0 || u.length < 2 || v.length < 2 ||
            !Double.isFinite(u[0]) || !Double.isFinite(u[1]) ||
            !Double.isFinite(v[0]) || !Double.isFinite(v[1]) ||
            u[0] > u[1] || v[0] > v[1])
            return Double.NaN;
        double along = Math.max(0, Math.min(x+la/2,y+lb/2)-Math.max(x-la/2,y-lb/2));
        double across = Math.max(0, Math.min(u[1],v[1])-Math.max(u[0],v[0]));
        double area = along * across;
        return Double.isFinite(area) ? area : Double.NaN;
    }
    static double rectangularArea(double length, double width) {
        return length * width;
    }

    static double rectangularVolume(double length, double width, double depth) {
        return rectangularArea(length, width) * depth;
    }
}
