package de.feldaufmass.pro;

import org.junit.Test;
import static org.junit.Assert.*;

public class MontageGeometryTest {
    @Test public void widthIncludesOffsetOnEitherSide() {
        assertEquals(0.8, MontageGeometry.requiredTrenchWidth(0.2, 0.4), 1e-9);
        assertEquals(0.8, MontageGeometry.requiredTrenchWidth(-0.2, 0.4), 1e-9);
        assertEquals(0.4, MontageGeometry.requiredTrenchWidth(0, 0.4), 1e-9);
    }

    @Test public void depthCheckUsesMillimetreTolerance() {
        assertFalse(MontageGeometry.exceedsExcavationDepth(0.8, 0.2, 1.0));
        assertFalse(MontageGeometry.exceedsExcavationDepth(0.8, 0.2005, 1.0));
        assertTrue(MontageGeometry.exceedsExcavationDepth(0.8, 0.202, 1.0));
    }

    @Test public void elevationCheckUsesCentimetreTolerance() {
        assertFalse(MontageGeometry.elevationMismatch(100, 99, 1));
        assertFalse(MontageGeometry.elevationMismatch(100, 99, 1.005));
        assertTrue(MontageGeometry.elevationMismatch(100, 99, 1.02));
    }

    @Test public void positiveRejectsInvalidNumbers() {
        assertTrue(MontageGeometry.positive(0.1));
        assertFalse(MontageGeometry.positive(0));
        assertFalse(MontageGeometry.positive(-1));
        assertFalse(MontageGeometry.positive(Double.NaN));
        assertFalse(MontageGeometry.positive(Double.POSITIVE_INFINITY));
    }
    @Test public void absoluteElevationsDetectOverlapAndContact() {
        assertTrue(MontageGeometry.verticallySeparated(2, 0, 0, 0.5, 0.5, 100, 99.5));
        assertFalse(MontageGeometry.verticallySeparated(2, 0, 0, 0.5, 0.5, 100, 99.8));
        assertTrue(MontageGeometry.verticallySeparated(2, 0, 0, 0.5, 0.5, 99, 100));
    }

    @Test public void relativeCoverRequiresSameStation() {
        double n = Double.NaN;
        assertTrue(MontageGeometry.verticallySeparated(0, 0.5, 1, 0.5, 0.5, n, n));
        assertFalse(MontageGeometry.verticallySeparated(0, 0.5, 0.7, 0.5, 0.5, n, n));
        assertFalse(MontageGeometry.verticallySeparated(2, 0.5, 1, 0.5, 0.5, n, n));
        assertFalse(MontageGeometry.verticallySeparated(0, n, 1, 0.5, 0.5, n, n));
        assertFalse(MontageGeometry.verticallySeparated(0, 0.5, 1, 0, 0.5, 100, 99));
    }
}
