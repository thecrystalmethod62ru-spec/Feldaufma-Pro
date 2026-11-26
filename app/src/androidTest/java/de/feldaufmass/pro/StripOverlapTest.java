package de.feldaufmass.pro;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class StripOverlapTest {
    private static void check(double gross, double net, double[]... strips) {
        StripOverlap.Result result=StripOverlap.calculate(Arrays.asList(strips));
        assertEquals(gross,result.gross,0.01);
        assertEquals(net,result.net,0.01);
        assertEquals(gross-net,result.overlap,0.01);
    }
    @Test public void singleStrip() {
        check(20,20,new double[]{0,0,10,0,1,1});
    }
    @Test public void crossingStrips() {
        check(40,36,new double[]{-5,0,5,0,1,1},new double[]{0,-5,0,5,1,1});
    }
    @Test public void duplicateStrip() {
        check(40,20,new double[]{0,0,10,0,1,1},new double[]{0,0,10,0,1,1});
    }
    @Test public void separateStrips() {
        check(40,40,new double[]{0,0,10,0,1,1},new double[]{0,5,10,5,1,1});
    }
    @Test public void adjacentStationIntervals() {
        check(20,20,new double[]{0,0,5,0,1,1},new double[]{5,0,10,0,1,1});
    }
    @Test public void asymmetricWidths() {
        check(35,30,new double[]{0,0,10,0,2,1},new double[]{0,2,5,2,0,1});
    }
    @Test(expected=IllegalArgumentException.class) public void missingWidth() {
        StripOverlap.calculate(Arrays.asList(new double[]{0,0,10,0,Double.NaN,1}));
    }
}
