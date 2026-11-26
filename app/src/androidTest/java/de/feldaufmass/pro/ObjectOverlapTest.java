package de.feldaufmass.pro;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ObjectOverlapTest {
    private JSONObject objectAt(double station) throws JSONException {
        return new JSONObject().put("station", station).put("side", "mittig").put("detailWidth", "1,0");
    }

    @Test public void unmeasuredObjectInsideMeasuredExtentNeedsReview() throws JSONException {
        JSONObject measured = objectAt(10).put("detailLength", "4,0");
        JSONObject pending = objectAt(11);
        assertTrue(MainActivity.potentialObjectOverlap(measured, pending, 1));
        assertTrue(MainActivity.potentialObjectOverlap(pending, measured, 1));
        assertTrue(Double.isNaN(MainActivity.overlapArea(measured, pending, 1)));
        pending.put("station", 12);
        assertTrue(MainActivity.potentialObjectOverlap(measured, pending, 1));
        pending.put("station", 12.1);
        assertFalse(MainActivity.potentialObjectOverlap(measured, pending, 1));
    }

    @Test public void separatedSidesAndMeasuredEndContactDoNotTriggerReview() throws JSONException {
        JSONObject measured = objectAt(10).put("detailLength", 4);
        JSONObject other = objectAt(11).put("side", "links").put("detailOffset", 1);
        assertFalse(MainActivity.potentialObjectOverlap(measured, other, 1));
        other.put("station", 13).put("side", "mittig").put("detailLength", 2);
        assertFalse(MainActivity.potentialObjectOverlap(measured, other, 1));
    }

    @Test public void twoUnmeasuredObjectsUseStationTolerance() throws JSONException {
        assertTrue(MainActivity.potentialObjectOverlap(objectAt(0), objectAt(0.009), 1));
        assertFalse(MainActivity.potentialObjectOverlap(objectAt(0), objectAt(0.01), 1));
    }
}
