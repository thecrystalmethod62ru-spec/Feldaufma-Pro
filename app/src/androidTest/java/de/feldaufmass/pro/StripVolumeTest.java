package de.feldaufmass.pro;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class StripVolumeTest {
    private double[] strip(double x1,double y1,double x2,double y2,double depth) {
        return new double[]{x1,y1,x2,y2,1,1,depth};
    }
    private void check(double gross,double net,double[]... strips) {
        StripVolume.Result r=StripVolume.calculate(Arrays.asList(strips));
        assertEquals(gross,r.gross,0.01);
        assertEquals(net,r.net,0.01);
        assertEquals(gross-net,r.overlap,0.01);
    }
    @Test public void single(){check(30,30,strip(0,0,10,0,1.5));}
    @Test public void crossingUnequalDepth(){check(48,44.4,strip(0,0,10,0,1.5),strip(5,-5,5,5,0.9));}
    @Test public void identicalUnequalDepth(){check(50,30,strip(0,0,10,0,1.5),strip(0,0,10,0,1));}
    @Test public void tripleIdentical(){check(90,40,strip(0,0,10,0,1),strip(0,0,10,0,1.5),strip(0,0,10,0,2));}
    @Test public void adjacentDepths(){check(30,30,strip(0,0,5,0,1),strip(5,0,10,0,2));}
    @Test public void empty(){check(0,0);}
    @Test(expected=IllegalArgumentException.class) public void negativeDepth(){StripVolume.calculate(Arrays.asList(strip(0,0,10,0,-1)));}
}
