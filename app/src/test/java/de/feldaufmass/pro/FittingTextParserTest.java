package de.feldaufmass.pro;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class FittingTextParserTest {
    @Test public void bendAngleMayPrecedeNameOrFollowDiameter() {
        for(String line:new String[]{"45°-Bogen DN 80","Bogen DN80 45 Grad","Bogen: 45° DN 80"}){
            FittingTextParser.Candidate item=FittingTextParser.parse(line).get(0);
            assertEquals(MontageCatalog.Fitting.BEND_45,item.type);
            assertEquals("80",item.dn);
            assertEquals(line,item.evidence);
        }
        for(String line:new String[]{"Bogen 45° oder 90°","Bogen DN80 Drehung 45°","Bogen 15°"})
            assertEquals(0,FittingTextParser.parse(line).size());
    }

    @Test public void fittingWithCompoundDiameterKeepsItsSourceForReview() {
        for(String sizes:new String[]{"DN 100 x 80","DN100 / 80","DN100×80","DN 80 oder 100","DN 80 DN ?"}){
            String line="Reduzierung "+sizes+" Station 5 m";
            FittingTextParser.Candidate item=FittingTextParser.parse(line).get(0);
            assertEquals("",item.dn);
            assertEquals("5",item.station);
            assertEquals(line,item.evidence);
        }
        assertEquals("80",FittingTextParser.parse("Muffe DN: 80").get(0).dn);
    }

    @Test public void onlyCompleteLabelledMaterialsAreSuggested() {
        assertEquals("PE 100-RC",FittingTextParser.parse("Muffe Werkstoff: PE 100-RC, Station 2 m").get(0).material);
        assertEquals("Guss",FittingTextParser.parse("Muffe DN80 Material=Guss").get(0).material);
        for(String note:new String[]{"Rohr aus Stahl","Material: PVC-C","Material: Stahl / Guss",
                "Werkstoff: PP oder PVC","Material: PP; Werkstoff: Guss"}){
            assertEquals("",FittingTextParser.parse("Muffe "+note).get(0).material);
        }
    }

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
        assertEquals("", station("Station 1+020 bis 1+040"));
        assertEquals("", station("Station 1+020–1+040"));
        assertEquals("", station("1+020"));
    }
}
