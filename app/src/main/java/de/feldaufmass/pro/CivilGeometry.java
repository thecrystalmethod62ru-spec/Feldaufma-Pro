package de.feldaufmass.pro;

/** Schematic station-based geometry, distances in metres and areas in square metres. */
final class CivilGeometry {
    private CivilGeometry() {}

    static double overlapArea(double x, double y, double la, double lb,
                              double[] u, double[] v) {
        if (!Double.isFinite(x) || !Double.isFinite(y) ||
            !Double.isFinite(la) || !Double.isFinite(lb) || u == null || v == null)
            return Double.NaN;
        double along = Math.max(0, Math.min(x+la/2,y+lb/2)-Math.max(x-la/2,y-lb/2));
        double across = Math.max(0, Math.min(u[1],v[1])-Math.max(u[0],v[0]));
        double area = along * across;
        return Double.isFinite(area) ? area : Double.NaN;
    }
}
