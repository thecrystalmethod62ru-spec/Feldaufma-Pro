package de.feldaufmass.pro;

import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.File;
import java.util.UUID;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.equalTo;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MontageProtocolsTest {
    private static org.hamcrest.Matcher<android.view.View> labelledField(String label) {
        return new org.hamcrest.TypeSafeMatcher<android.view.View>(){
            public void describeTo(org.hamcrest.Description description){description.appendText(label);}
            public boolean matchesSafely(android.view.View view){
                if(!(view instanceof EditText)||!(view.getParent() instanceof android.view.ViewGroup))return false;
                android.view.ViewGroup form=(android.view.ViewGroup)view.getParent();
                for(int i=0;i<form.getChildCount();i++){
                    android.view.View sibling=form.getChildAt(i);
                    if(sibling instanceof android.widget.TextView && sibling.getLabelFor()==view.getId()
                            && label.contentEquals(((android.widget.TextView)sibling).getText()))return true;
                }
                return false;
            }
        };
    }
    @Test public void createProtocolAndExportProjectPdf() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            String id=UUID.randomUUID().toString();
            String[] original=new String[1];
            try {
                scenario.onActivity(activity->{
                    original[0]=activity.activeProject;
                    activity.activeProject=id;
                    activity.showMontage();
                });
                onData(equalTo("Prüfprotokolle")).inRoot(isDialog()).perform(click());
                onView(withText("Neues Protokoll")).inRoot(isDialog()).perform(click());
                onView(labelledField("Leitungsabschnitt")).inRoot(isDialog()).perform(scrollTo(),replaceText("Hausanschluss 4"),closeSoftKeyboard());
                onView(withText("Speichern")).inRoot(isDialog()).perform(click());
                scenario.onActivity(activity->{
                    File file=null;
                    try{
                        JSONArray rows=new JSONArray(activity.projectStorage(id).getString(MontageProtocols.KEY,"[]"));
                        assertEquals(1,rows.length());
                        assertEquals("Hausanschluss 4",rows.getJSONObject(0).getString("section"));
                        assertEquals("Druckprüfung",rows.getJSONObject(0).getString("type"));
                        file=File.createTempFile("protocol",".pdf",activity.getCacheDir());
                        new MontageProtocols(activity).writePdf(Uri.fromFile(file));
                        try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
                            PdfRenderer pdf=new PdfRenderer(fd)){assertEquals(1,pdf.getPageCount());}
                    }catch(Exception e){throw new AssertionError(e);}
                    finally{if(file!=null)file.delete();}
                });
            } finally {
                scenario.onActivity(activity->{activity.activeProject=original[0];activity.deleteSharedPreferences("project_"+id);});
            }
        }
    }
}
