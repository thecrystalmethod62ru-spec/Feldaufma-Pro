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
 private android.widget.Button visibleButton(android.view.View view,String label){
  if(view instanceof android.widget.Button){
   android.widget.Button b=(android.widget.Button)view;
   android.graphics.Rect bounds=new android.graphics.Rect();
   if(label.contentEquals(b.getText())&&b.isShown()&&b.isEnabled()&&b.isClickable()&&b.getGlobalVisibleRect(bounds)&&!bounds.isEmpty())return b;
  }
  if(view instanceof android.view.ViewGroup){
   android.view.ViewGroup group=(android.view.ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++){
    android.widget.Button b=visibleButton(group.getChildAt(i),label);if(b!=null)return b;
   }
  }
  return null;
 }
 @androidx.test.filters.SdkSuppress(minSdkVersion=29)
 @Test public void regelprofil() throws Exception { profile(false,"06-regelprofil"); }
 @androidx.test.filters.SdkSuppress(minSdkVersion=29)
 @Test public void regelprofilLandscape() throws Exception { profile(true,"07-regelprofil-landscape"); }
 private void profile(boolean landscape,String name) throws Exception {
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(a->{a.setRequestedOrientation(landscape?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);a.enterProject();});
   android.os.SystemClock.sleep(1000);
   scenario.onActivity(a->a.editRoute(false,true));
   boolean[] opened={false};
   for(int attempt=0;attempt<40&&!opened[0];attempt++){
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    scenario.onActivity(a->{
     for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){
      android.widget.Button tab=visibleButton(root,"Regelprofil");
      if(tab!=null){assertTrue(tab.performClick());opened[0]=true;break;}
     }
    });
    if(!opened[0])android.os.SystemClock.sleep(250);
   }
   // Preserve the actual screen even when navigation or visibility fails.
   capture(name);
   assertTrue("Regelprofil tab not visible",opened[0]);
   boolean[] visible={false};
   for(int attempt=0;attempt<20&&!visible[0];attempt++){
    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    scenario.onActivity(a->{
     for(android.view.View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){
      if(visibleButton(root,"Maße bearbeiten")!=null){visible[0]=true;break;}
     }
    });
    if(!visible[0])android.os.SystemClock.sleep(250);
   }
   capture(name);
   assertTrue("Regelprofil content not visible after switching tabs",visible[0]);
  }
 }
}
