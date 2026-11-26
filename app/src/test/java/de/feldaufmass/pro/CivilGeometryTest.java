package de.feldaufmass.pro;

import org.junit.Test;
import static org.junit.Assert.*;

public class CivilGeometryTest {
    private final double[] a = {0, 2};

    @Test public void partialOverlap() {
        assertEquals(1.0, CivilGeometry.overlapArea(2, 3, 2, 2, a, new double[]{1,3}), 1e-9);
    }
    @Test public void identicalObjects() {
        assertEquals(4.0, CivilGeometry.overlapArea(2, 2, 2, 2, a, a), 1e-9);
    }
    @Test public void disjointOrTouchingObjects() {
        assertEquals(0.0, CivilGeometry.overlapArea(2, 4, 2, 2, a, a), 1e-9);
        assertEquals(0.0, CivilGeometry.overlapArea(2, 5, 2, 2, a, a), 1e-9);
        assertEquals(0.0, CivilGeometry.overlapArea(2, 2, 2, 2, a, new double[]{3,4}), 1e-9);
    }
    @Test public void missingMeasurementsAreNotZero() {
        assertTrue(Double.isNaN(CivilGeometry.overlapArea(Double.NaN, 2, 2, 2, a, a)));
        assertTrue(Double.isNaN(CivilGeometry.overlapArea(2, 2, 2, 2, null, a)));
    }
    @Test public void rectangularQuantities() {
        assertEquals(92.0, CivilGeometry.rectangularArea(100, 0.92), 1e-9);
        assertEquals(136.16, CivilGeometry.rectangularVolume(100, 0.92, 1.48), 1e-9);
        assertEquals(46.0, CivilGeometry.rectangularArea(100, 0.23 + 0.23), 1e-9);
        assertEquals(9.66, CivilGeometry.rectangularVolume(100, 1.38, 0.07), 1e-9);
        assertEquals(27.6, CivilGeometry.rectangularVolume(100, 0.92, 0.30), 1e-9);
        assertTrue(Double.isNaN(CivilGeometry.rectangularVolume(100, 0.92, Double.NaN)));
    }
}
