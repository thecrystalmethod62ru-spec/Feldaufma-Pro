package de.feldaufmass.pro;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FittingReviewTest {
    @Test public void reviewTwoLinesFromOneSketch() {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            String id=UUID.randomUUID().toString();
            String[] original=new String[1];
            String bend="45°-Bogen DN 80 Werkstoff: PE 100-RC, Station 0+012,5";
            try {
                scenario.onActivity(activity->{
                    original[0]=activity.activeProject;
                    activity.activeProject=id;
                    assertTrue(activity.projectStorage(id).edit().putString("length","20")
                        .putString("routeSketch","[[0,0],[1,0,6.25,\"p2\",[],null,null,null,\"Schacht\"],[2,0],[3,0,2]]").commit());
                    activity.reviewFittingText("Blatt A",bend+"\nMuffe");
                });
                onView(withText("Zeile 2 · Muffe")).perform(click());
                onView(withText("Zurück")).perform(click());
                onView(withText("Zeile 1 · "+bend)).perform(click());
                onView(withText("Zum Projekt hinzufügen")).perform(click());
                onView(withText("Zeile 1 · "+bend)).check(doesNotExist());
                onView(withText("Zeile 2 · Muffe")).perform(click());
                onView(withText("Station aus Projekt wählen")).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
                onView(withText("Station 8,250 m · Punkt 4")).check(doesNotExist());
                onView(withText("Station 6,250 m · Punkt 2 · Schacht")).perform(click());
                onView(withText("Zum Projekt hinzufügen")).perform(click());
                scenario.onActivity(activity->{
                    try {
                        JSONArray items=new JSONArray(activity.projectStorage(id).getString("montageFittings","[]"));
                        assertEquals(2,items.length());
                        assertEquals("PE 100-RC",items.getJSONObject(0).getString("material"));
                        assertFalse(items.getJSONObject(0).getBoolean("installed"));
                        assertEquals(12.5,items.getJSONObject(0).getDouble("station"),0);
                        assertEquals("Blatt A",items.getJSONObject(0).getString("sourceName"));
                        assertEquals("p2",items.getJSONObject(1).getString("routePointId"));
                        assertFalse(items.getJSONObject(0).has("routePointId"));
                        assertEquals(6.25,items.getJSONObject(1).getDouble("station"),0);
                        assertEquals(2,items.getJSONObject(1).getInt("sourceLineNumber"));
                        assertFalse(items.getJSONObject(1).getBoolean("installed"));
                    } catch (JSONException e) {throw new AssertionError(e);}
                });
            } finally {
                scenario.onActivity(activity->{activity.activeProject=original[0];activity.deleteSharedPreferences("project_"+id);});
            }
        }
    }
    @Test public void reviewChangedStationInFittingEditor() {
        try (ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            String id=UUID.randomUUID().toString();
            String[] original=new String[1];
            try {
                scenario.onActivity(activity->{
                    try {
                        original[0]=activity.activeProject;
                        activity.activeProject=id;
                        org.json.JSONObject fitting=new org.json.JSONObject().put("id","m1").put("type","COUPLING")
                            .put("station",6.25).put("routePointId","p2");
                        assertTrue(activity.projectStorage(id).edit().putString("length","20")
                            .putString("routeSketch","[[0,0],[1,0,7.5,\"p2\",[],null,null,null,\"Schacht\"]]")
                            .putString("montageFittings",new JSONArray().put(fitting).toString()).commit());
                        activity.showMontageFitting(MontageCatalog.Fitting.COUPLING,fitting);
                    } catch (JSONException e) {throw new AssertionError(e);}
                });
                onView(withText("Punkt 2 · Schacht · Jetzt bei 7,500 m. Zum Übernehmen Punkt erneut wählen."))
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.TextView.class)));
                onView(withText("Station aus Projekt wählen")).perform(androidx.test.espresso.action.ViewActions.scrollTo(),click());
                onView(withText("Station 7,500 m · Punkt 2 · Schacht")).perform(click());
                onView(withText("Änderungen speichern")).perform(click());
                scenario.onActivity(activity->{
                    try {
                        JSONArray items=new JSONArray(activity.projectStorage(id).getString("montageFittings","[]"));
                        assertEquals(7.5,items.getJSONObject(0).getDouble("station"),0);
                        assertEquals("p2",items.getJSONObject(0).getString("routePointId"));
                    } catch (JSONException e) {throw new AssertionError(e);}
                });
            } finally {
                scenario.onActivity(activity->{activity.activeProject=original[0];activity.deleteSharedPreferences("project_"+id);});
            }
        }
    }
}
