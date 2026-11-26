package de.feldaufmass.pro;

import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProjectStateTest {
 @Test public void routeEditorUndoRedoPreservesMetadata(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    String saved=activity.routeData();
    RouteSketch route=new RouteSketch(activity,"[[0.2,0.2,null],[0.6,0.4,12.5]]");
    RouteSketch.Vertex point=route.vertices.get(1);
    point.name="KVS";point.latitude=52;point.longitude=11;point.accuracy=.4;point.photos.put("point.jpg");
    String original=route.encode(),id=point.id;
    MainActivity.RouteEditHistory history=new MainActivity.RouteEditHistory(original);
    history.record(original);assertTrue(history.undo.isEmpty());
    route.movePoint(1,new android.graphics.PointF(.7f,.5f));history.record(route.encode());
    String moved=route.encode();
    point.metres=17;history.record(route.encode());String measured=route.encode();
    route.vertices.remove(1);history.record(route.encode());
    assertEquals(measured,history.step(false));
    assertEquals(moved,history.step(false));
    RouteSketch restored=new RouteSketch(activity,history.step(false));
    RouteSketch.Vertex v=restored.vertices.get(1);
    assertEquals(id,v.id);assertEquals("KVS",v.name);assertEquals("point.jpg",v.photos.optString(0));
    assertEquals(52,v.latitude,0);assertEquals(11,v.longitude,0);assertEquals(.4,v.accuracy,0);
    assertEquals(12.5,restored.measuredLength(),0);assertEquals(.6,v.point.x,.00001);
    assertEquals(moved,history.step(true));assertEquals(measured,history.step(true));
    restored.vertices.get(1).metres=30;history.record(restored.encode());assertTrue(history.redo.isEmpty());
    for(int j=0;j<60;j++){restored.vertices.get(1).metres=100+j;history.record(restored.encode());}
    assertEquals(50,history.undo.size());
    assertEquals(saved,activity.routeData());
   });
  }
 }
 @Test public void projectsRemainIsolatedAcrossRecreation(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   String a=java.util.UUID.randomUUID().toString(),b=java.util.UUID.randomUUID().toString();
   final String[] original=new String[1],legacyRoute=new String[1],routeA=new String[1];
   scenario.onActivity(activity->{
    original[0]=activity.projectRegistry().getString("active",MainActivity.LEGACY_PROJECT);
    legacyRoute[0]=activity.projectStorage(MainActivity.LEGACY_PROJECT).getString("routeSketch","[]");
   });
   try{
    scenario.onActivity(activity->{
     RouteSketch route=new RouteSketch(activity,"[[0.2,0.2,null],[0.6,0.4,12.5]]");
     route.vertices.get(1).name="KVS";route.vertices.get(1).photos.put("isolated_test.jpg");
     route.vertices.get(1).latitude=52;route.vertices.get(1).longitude=11;route.vertices.get(1).accuracy=.4;
     routeA[0]=route.encode();
     assertTrue(activity.projectStorage(a).edit().putString("project","Projekt A").putString("routeSketch",routeA[0]).putString("width","1.2").putString("objects","[{\"station\":5,\"type\":\"KVS\"}]").commit());
     assertTrue(activity.projectStorage(b).edit().putString("project","Projekt B").putString("width","0.8").commit());
     assertTrue(activity.projectRegistry().edit().putString("active",a).commit());
    });
    scenario.recreate();
    scenario.onActivity(activity->{
     assertEquals(a,activity.activeProject);assertEquals("Projekt A",activity.project.getText().toString());
     assertEquals("1.2",activity.width.getText().toString());assertEquals(routeA[0],activity.routeData());
     assertEquals(1,activity.objects.length());
     assertTrue(activity.projectRegistry().edit().putString("active",b).commit());
    });
    scenario.recreate();
    scenario.onActivity(activity->{
     assertEquals(b,activity.activeProject);assertEquals("Projekt B",activity.project.getText().toString());
     assertEquals("0.8",activity.width.getText().toString());assertEquals("[]",activity.routeData());
     assertEquals(0,activity.objects.length());
     assertTrue(activity.saveRoute(new RouteSketch(activity,"[[0.1,0.1,null],[0.8,0.8,30]]")));
     assertTrue(activity.projectRegistry().edit().putString("active",a).commit());
    });
    scenario.recreate();
    scenario.onActivity(activity->{
     assertEquals(routeA[0],activity.routeData());
     RouteSketch route=new RouteSketch(activity,activity.routeData());
     assertEquals(12.5,route.measuredLength(),0);assertEquals("KVS",route.vertices.get(1).name);
     assertEquals("isolated_test.jpg",route.vertices.get(1).photos.optString(0));assertEquals(.4,route.vertices.get(1).accuracy,0);
     assertEquals(legacyRoute[0],activity.projectStorage(MainActivity.LEGACY_PROJECT).getString("routeSketch","[]"));
     assertEquals(30,new RouteSketch(activity,activity.projectStorage(b).getString("routeSketch","[]")).measuredLength(),0);
    });
   }finally{
    scenario.onActivity(activity->{
     activity.projectRegistry().edit().putString("active",original[0]).commit();
     activity.deleteSharedPreferences("project_"+a);activity.deleteSharedPreferences("project_"+b);
    });
   }
  }
 }
 @Test public void gpsUndoPreservesLaterMetadata(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    RouteSketch route=new RouteSketch(activity,"[[0.2,0.5,null],[0.8,0.5,20]]");
    RouteSketch.Vertex v=route.vertices.get(1);
    v.latitude=52;v.longitude=11;v.accuracy=.4;v.rememberGpsMove();
    v.point.set(.7f,.6f);v.latitude=52.001;v.name="KVS";v.photos.put("photo.jpg");v.metres=21;
    RouteSketch restored=new RouteSketch(activity,route.encode());
    RouteSketch.Vertex p=restored.vertices.get(1);
    assertTrue(p.undoGpsMove());assertEquals(.8,p.point.x,.0001);assertEquals(.5,p.point.y,.0001);
    assertEquals(52,p.latitude,0);assertEquals(11,p.longitude,0);assertEquals(.4,p.accuracy,0);
    assertEquals("KVS",p.name);assertEquals("photo.jpg",p.photos.optString(0));assertEquals(21,p.metres,0);
    assertFalse(p.undoGpsMove());
   });
  }
 }
 @Test public void gpsCalibrationAndPointMetadata(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    RouteSketch route=new RouteSketch(activity,"[[0.2,0.5,null],[0.8,0.5,20],[0.5,0.7,10]]");
    RouteSketch.Vertex a=route.vertices.get(0),b=route.vertices.get(1),c=route.vertices.get(2);
    a.latitude=b.latitude=52;a.longitude=11;b.longitude=11.001;a.accuracy=b.accuracy=.5;
    assertTrue(RouteSketch.preciseGps(.5));assertFalse(RouteSketch.preciseGps(.51));assertFalse(RouteSketch.preciseGps(Double.NaN));
    android.graphics.PointF target=route.gpsPosition(52,11.0005,2);
    assertNotNull(target);assertEquals(.5,target.x,.0001);assertEquals(.5,target.y,.0001);
    c.name="Hausanschluss 12";c.photos.put("test.jpg");c.latitude=52;c.longitude=11.0005;c.accuracy=.4;
    RouteSketch restored=new RouteSketch(activity,route.encode());
    assertEquals("Hausanschluss 12",restored.vertices.get(2).name);
    assertEquals("test.jpg",restored.vertices.get(2).photos.optString(0));
    assertEquals(.4,restored.vertices.get(2).accuracy,0);
    assertEquals(30,restored.measuredLength(),0);
    b.accuracy=.51;assertNull(route.gpsPosition(52,11.0005,2));
   });
  }
 }
 @Test public void contentClearsVisibleSystemBars(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    androidx.core.view.WindowInsetsCompat windowInsets=
      androidx.core.view.ViewCompat.getRootWindowInsets(activity.shell);
    assertNotNull(windowInsets);
    androidx.core.graphics.Insets bars=windowInsets.getInsets(
       androidx.core.view.WindowInsetsCompat.Type.systemBars()
       |androidx.core.view.WindowInsetsCompat.Type.displayCutout());
    assertTrue(bars.top>0);
    assertEquals(bars.top,activity.shell.getPaddingTop());
    assertEquals(bars.bottom,activity.shell.getPaddingBottom());
    assertEquals(bars.left,activity.shell.getPaddingLeft());
    assertEquals(bars.right,activity.shell.getPaddingRight());
    assertTrue(activity.body.getBottom()<=activity.shell.getHeight()-bars.bottom);
   });
  }
 }
 @Test public void detailHistoryAndExportUseSavedGeometry() throws Exception {
  try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
   scenario.onActivity(activity -> {
    try { verifyProjectState(activity); } catch (Exception e) { throw new AssertionError(e); }
   });
   scenario.recreate();
   scenario.onActivity(activity -> {
    assertEquals(2, activity.objects.length());
    assertTrue(activity.undoObjectsButton.isEnabled());
    assertFalse(activity.redoObjectsButton.isEnabled());
    activity.undoObjectsButton.performClick();
    assertEquals(1, activity.objects.length());
   });
  }
  try (ActivityScenario<MainActivity> reopened = ActivityScenario.launch(MainActivity.class)) {
   reopened.onActivity(activity -> {
    assertTrue(activity.redoObjectsButton.isEnabled());
    activity.redoObjectsButton.performClick();
    assertEquals(2, activity.objects.length());
    assertEquals(8, activity.objects.optJSONObject(1).optDouble("station"), 0);
    activity.undoObjectsButton.performClick();
    activity.objectStation.setText("9");
    activity.addObject();
   });
   reopened.recreate();
   reopened.onActivity(activity -> {
    assertFalse(activity.redoObjectsButton.isEnabled());
    assertEquals(9, activity.objects.optJSONObject(1).optDouble("station"), 0);
    activity.objectStation.setText("2");
    activity.addObject();
    assertEquals("3",activity.overviewValues[3].getText().toString());
    android.widget.Button first=(android.widget.Button)activity.stationRows.getChildAt(0);
    assertTrue(first.getText().toString().startsWith("2,00 m"));
    activity.sheetTabs.get(activity.quantityPage).performClick();
    assertTrue(activity.quantityPage.isShown());
    assertTrue(activity.sheetTabs.get(activity.quantityPage).isSelected());
    activity.sheetTabs.get(activity.planPage).performClick();
    assertTrue(activity.planPage.isShown());
    activity.undoObjectsButton.performClick();
    assertEquals("2",activity.overviewValues[3].getText().toString());
    assertTrue(((android.widget.Button)activity.stationRows.getChildAt(0)).getText().toString().startsWith("5,00 m"));
   });
  }
 }
 @Test public void savedPointsCanBeRealignedWithoutLosingMeasuresOrPhotos(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    RouteSketch original=new RouteSketch(activity,"[[0.4,0.4],[0.7,0.6,4.25]]");
    String id=original.vertices.get(1).id;
    original.vertices.get(1).photos.put("punkt_test.jpg");
    for(int angle=0;angle<=90;angle+=15){
     RouteSketch edited=new RouteSketch(activity,original.encode());
     edited.angle=angle;
     edited.movePoint(1,new android.graphics.PointF(.1f,.8f));
     RouteSketch reopened=new RouteSketch(activity,edited.encode());
     android.graphics.PointF p=reopened.vertices.get(1).point,a=reopened.vertices.get(0).point;
     assertEquals(angle,Math.toDegrees(Math.atan2(Math.abs(p.y-a.y),Math.abs(p.x-a.x))),.001);
     assertEquals(4.25,reopened.measuredLength(),0);
     assertEquals(id,reopened.vertices.get(1).id);
     assertEquals("punkt_test.jpg",reopened.vertices.get(1).photos.optString(0));
    }
   });
  }
 }
 @Test public void routePersistsAcrossActivityRecreation(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   final String[] expected=new String[1];
   scenario.onActivity(activity->{
    RouteSketch route=new RouteSketch(activity,"[[0.2,0.2],[0.6,0.4,12.5],[0.8,0.8]]");
    route.vertices.get(1).photos.put("test_photo.jpg");
    route.angle=45;
    route.movePoint(1,new android.graphics.PointF(.5f,.5f));
    expected[0]=route.encode();
    assertTrue(activity.saveRoute(route));
    activity.refreshRoute();
   });
   scenario.recreate();
   scenario.onActivity(activity->{
    assertEquals(expected[0],activity.routeData());
    RouteSketch reopened=new RouteSketch(activity,activity.routeData());
    assertEquals(3,reopened.vertices.size());
    assertEquals(12.5,reopened.vertices.get(1).metres,0);
    assertTrue(Double.isNaN(reopened.vertices.get(2).metres));
    assertEquals("test_photo.jpg",reopened.vertices.get(1).photos.optString(0));
    reopened.vertices.get(2).metres=7.5;
    assertTrue(activity.saveRoute(reopened));
    assertEquals(20,new RouteSketch(activity,activity.routeData()).measuredLength(),0);
    activity.getPreferences(0).edit().remove("routeSketch").commit();
   });
  }
 }
 private void touch(RouteSketch route,int action,float x,float y){
  android.view.MotionEvent e=android.view.MotionEvent.obtain(0,10,action,x,y,0);
  try{route.onTouchEvent(e);}finally{e.recycle();}
 }
 private void verifyProjectState(MainActivity activity) throws Exception {
  activity.showPage(activity.measurePage);
  assertFalse(activity.stoneLength.isShown());
  assertTrue(activity.surface.isShown());
  assertTrue(activity.handling.isShown());
  activity.belagsToggle.performClick();
  assertTrue(activity.underlying.isShown());
  activity.stoneColor.setText("Grau");
  activity.belagsToggle.performClick();
  assertFalse(activity.stoneColor.isShown());
  activity.openBelagsDetails();
  assertTrue(activity.stoneColor.isShown());
  assertEquals("Grau",activity.stoneColor.getText().toString());
  activity.length.setText("20");
  activity.width.setText("1");
  activity.depth.setText("2");
  assertTrue(activity.calculate());
  activity.surface.setSelection(2);
  activity.stoneDepth.setText("8");
  activity.bedding.setText("Splitt");
  activity.baseInput.setText("0.20");
  assertTrue(activity.calculate());
  assertTrue(activity.surfaceLayers.getText().toString().contains("Pflaster · 8 cm"));
  assertTrue(activity.surfaceLayers.getText().toString().contains("Bettung · Splitt"));
  assertEquals(.08,activity.topThickness,.0001);
  RouteSketch route=new RouteSketch(activity,"[]");
  android.widget.FrameLayout holder=new android.widget.FrameLayout(activity);
  holder.addView(route);
  route.layout(0,0,300,300);
  route.editable=true;
  touch(route,android.view.MotionEvent.ACTION_DOWN,40,40);
  touch(route,android.view.MotionEvent.ACTION_MOVE,40,200);
  touch(route,android.view.MotionEvent.ACTION_UP,200,200);
  assertEquals(3,route.vertices.size());
  assertTrue(Double.isNaN(route.measuredLength()));
  route.vertices.get(1).metres=3.25;
  assertTrue(Double.isNaN(route.measuredLength()));
  route.vertices.get(2).metres=6.5;
  activity.getPreferences(0).edit().putString("routeSketch",route.encode()).apply();
  activity.refreshRoute();
  assertTrue(activity.routeSummary.getText().toString().contains("9,75 m"));
  assertEquals(20,activity.l,0);
  activity.objectStation.setText("5");
  activity.addObject();
  assertEquals(1, activity.objects.length());
  activity.objects.getJSONObject(0).put("detailLength","3,5").put("detailWidth","2").put("detailDepth","1");
  activity.saveObjects();
  assertTrue(activity.detailQuantities.getText().toString().contains("7,00 m³"));
  activity.undoObjectsButton.performClick();
  assertFalse(activity.objects.getJSONObject(0).has("detailWidth"));
  assertFalse(activity.detailQuantities.getText().toString().contains("7,00 m³"));
  activity.redoObjectsButton.performClick();
  assertTrue(activity.detailQuantities.getText().toString().contains("7,00 m³"));

  activity.width.setText("4");
  activity.depth.setText("0");
  assertFalse(activity.calculate());
  assertEquals(1, activity.w, 0);
  assertEquals(2, activity.d, 0);
  File report = new File(activity.getCacheDir(),"aufmass.pdf");
  activity.writePdf(Uri.fromFile(report));
  try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(report, ParcelFileDescriptor.MODE_READ_ONLY);
       PdfRenderer renderer = new PdfRenderer(fd)) {
   assertTrue(renderer.getPageCount() >= 3);
  }
  activity.undoObjectsButton.performClick();
  activity.objectStation.setText("8");
  activity.addObject();
  assertFalse(activity.redoObjectsButton.isEnabled());
  assertEquals(2, activity.objects.length());
 }
}
