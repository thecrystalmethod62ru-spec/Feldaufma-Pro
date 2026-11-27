package de.feldaufmass.pro;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FittingTextParserTest {
    private String station(String source) {
        return FittingTextParser.parse("Muffe DN 80 " + source).get(0).station;
    }

    @Test public void kilometreStationingPreservesDecimalMetres() {
        assertEquals("12,5", station("Station 0+012,5"));
        assertEquals("2034.75", station("Stat. 2 + 034.75 m"));
        assertEquals("0", station("Station 0+000"));
        assertEquals("12,5", station("Station 12,5 m"));
    }

    @Test public void ambiguousAndIncompleteStationingRequiresManualInput() {
        assertEquals("", station("Station 1+03"));
        assertEquals("", station("Station 1+0000"));
        assertEquals("", station("Station 1+020 und Stat. 30 m"));
        assertEquals("", station("Station unleserlich, Stat. 30 m"));
        assertEquals("", station("1+020"));
    }
}
