package de.feldaufmass.pro;

import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiScreenshotTest {
 private void capture(String name) throws Exception {
  android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();
  inst.waitForIdleSync();
  android.os.SystemClock.sleep(900);
  Bitmap bitmap=inst.getUiAutomation().takeScreenshot();
  assertNotNull(bitmap);
  File dir=new File(inst.getTargetContext().getFilesDir(),"ui-screenshots");
  assertTrue(dir.isDirectory()||dir.mkdirs());
  try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){
   assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
  }finally{bitmap.recycle();}
 }
 @Test public void startMenu() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);a.showProjectStart();});
   capture("01-start-menu");
  }
 }
 private void editor(boolean full,boolean landscape,String name) throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{
    a.setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    a.enterProject();
   });
   android.os.SystemClock.sleep(1000);
   scenario.onActivity(a->a.editRoute(false,full));
   capture(name);
  }
 }
 @Test public void normalPortrait() throws Exception {editor(false,false,"02-normal-portrait");}
 @Test public void normalLandscape() throws Exception {editor(false,true,"03-normal-landscape");}
 @Test public void fullPortrait() throws Exception {editor(true,false,"04-full-portrait");}
 @Test public void fullLandscape() throws Exception {editor(true,true,"05-full-landscape");}
 @Test public void regelprofil() throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);a.enterProject();});
   android.os.SystemClock.sleep(1000);
   scenario.onActivity(a->a.editRoute(false,true));
   androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(
    androidx.test.espresso.matcher.ViewMatchers.withContentDescription("Regelprofil"),
    androidx.test.espresso.matcher.ViewMatchers.isDisplayed())).perform(androidx.test.espresso.action.ViewActions.click());
   capture("06-regelprofil");
  }
 }
}
