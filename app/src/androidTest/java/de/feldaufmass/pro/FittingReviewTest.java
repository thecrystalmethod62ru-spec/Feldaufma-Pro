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
            String tee="T-Stück DN 80 Station 12,5 m";
            try {
                scenario.onActivity(activity->{
                    original[0]=activity.activeProject;
                    activity.activeProject=id;
                    assertTrue(activity.projectStorage(id).edit().putString("length","20").commit());
                    activity.reviewFittingText("Blatt A",tee+"\nMuffe");
                });
                onView(withText("Zeile 2 · Muffe")).perform(click());
                onView(withText("Zurück")).perform(click());
                onView(withText("Zeile 1 · "+tee)).perform(click());
                onView(withText("Hinzufügen")).perform(click());
                onView(withText("Zeile 1 · "+tee)).check(doesNotExist());
                onView(withText("Zeile 2 · Muffe")).perform(click());
                onView(withText("Hinzufügen")).perform(click());
                scenario.onActivity(activity->{
                    try {
                        JSONArray items=new JSONArray(activity.projectStorage(id).getString("montageFittings","[]"));
                        assertEquals(2,items.length());
                        assertEquals(12.5,items.getJSONObject(0).getDouble("station"),0);
                        assertEquals("Blatt A",items.getJSONObject(0).getString("sourceName"));
                        assertEquals(2,items.getJSONObject(1).getInt("sourceLineNumber"));
                        assertFalse(items.getJSONObject(1).getBoolean("installed"));
                    } catch (JSONException e) {throw new AssertionError(e);}
                });
            } finally {
                scenario.onActivity(activity->{activity.activeProject=original[0];activity.deleteSharedPreferences("project_"+id);});
            }
        }
    }
}
