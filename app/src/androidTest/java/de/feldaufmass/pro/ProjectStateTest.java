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
 @Test public void fittingTextSuggestsOnlyExplicitValues(){
  java.util.List<FittingTextParser.Candidate> found=FittingTextParser.parse(
    "Bogen 22,5° DN 80, Station 12,5 m\nBogen 32°\nMuffe und Flansch\nT Stück DN 80 DN 100\nSchieber");
  assertEquals(3,found.size());assertEquals(MontageCatalog.Fitting.BEND_22_5,found.get(0).type);
  assertEquals("80",found.get(0).dn);assertEquals("12,5",found.get(0).station);
  assertEquals("",found.get(1).dn);assertEquals("",found.get(2).station);
 }
 @Test public void confirmedPipeLengthsAreIndependentOfTrenchLength(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    String id=java.util.UUID.randomUUID().toString();
    try{
     org.json.JSONObject pipe=new org.json.JSONObject().put("type","Trinkwasser").put("diameter",90)
       .put("material","PE").put("sdr",11).put("installed",true).put("installedLength",2.1);
     org.json.JSONArray rows=new org.json.JSONArray().put(pipe)
       .put(new org.json.JSONObject(pipe.toString()).put("installedLength",3.2))
       .put(new org.json.JSONObject(pipe.toString()).put("installed",false).put("installedLength",80));
     android.content.SharedPreferences prefs=activity.projectStorage(id);
     assertTrue(prefs.edit().putString("extraUtilities",rows.toString()).putString("length","75").commit());
     assertEquals("Leitungen\n5,3 m  Trinkwasser · Ø 90 mm · PE · SDR 11",activity.projectMaterialText(prefs));
    }catch(Exception e){throw new AssertionError(e);}
    finally{activity.deleteSharedPreferences("project_"+id);}
   });
  }
 }
 @Test public void savedMontagePlanHasDrawingAndRegister(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    String original=activity.activeProject,testId=java.util.UUID.randomUUID().toString();File file=null;
    try{
     activity.activeProject=testId;
     org.json.JSONObject fitting=new org.json.JSONObject().put("type","TEE").put("station",5).put("offset",1);
     assertTrue(activity.projectStorage(testId).edit().putString("project","Bergstraße").putString("length","12,0")
       .putString("routeSketch","[[0.1,0.2,null],[0.5,0.2,8],[0.5,0.5,4]]")
       .putString("montageFittings",new org.json.JSONArray().put(fitting).toString()).commit());
     file=File.createTempFile("montageplan",".pdf",activity.getCacheDir());
     activity.writeMontagePlan(Uri.fromFile(file));
     try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
         PdfRenderer pdf=new PdfRenderer(fd)){
      assertEquals(2,pdf.getPageCount());
      try(PdfRenderer.Page drawing=pdf.openPage(0)){assertTrue(drawing.getWidth()>drawing.getHeight());}
     }
    }catch(Exception e){throw new AssertionError(e);}
    finally{activity.activeProject=original;activity.deleteSharedPreferences("project_"+testId);if(file!=null)file.delete();}
   });
  }
 }
 @Test public void metricDxfPositionsMontageOnSharedRoute(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    try{
     RouteSketch route=new RouteSketch(activity,"[[0.1,0.5,null],[0.5,0.5,8],[0.5,0.8,4]]");
     java.io.StringWriter out=new java.io.StringWriter();
     DxfExport model=new DxfExport(out,route,true);
     assertArrayEquals(new double[]{3,-.5},model.mountingPoint(3,.5),.000001);
     assertNull(model.mountingPoint(8,.5));assertNull(model.mountingPoint(13,0));
     org.json.JSONObject tee=new org.json.JSONObject().put("type","TEE").put("station",3).put("offset",.5);
     org.json.JSONObject joint=new org.json.JSONObject().put("number","S-101").put("type","Steckverbindung").put("station",12);
     model.write("Test",new org.json.JSONArray(),new org.json.JSONArray(),new org.json.JSONArray().put(tee),new org.json.JSONArray().put(joint));
     assertTrue(out.toString().contains("T-Stueck - Station 3.000 m"));
     assertTrue(out.toString().contains("S-101 Steckverbindung - Station 12.000 m"));
    }catch(Exception e){throw new AssertionError(e);}
   });
  }
 }
 @Test public void exportConnectionsWithMissingPhotoKeepsBothPages(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    String original=activity.activeProject;
    String testId=java.util.UUID.randomUUID().toString();
    File file=null;
    try{
     activity.activeProject=testId;
     org.json.JSONObject photo=new org.json.JSONObject().put("file","missing_joint_test.jpg");
     org.json.JSONObject joint=new org.json.JSONObject().put("id","j1").put("number","S-101")
         .put("type","Schweißverbindung").put("photos",new org.json.JSONArray().put(photo));
     assertTrue(activity.projectStorage(testId).edit().putString(MontageConnections.KEY,new org.json.JSONArray().put(joint).toString()).commit());
     file=File.createTempFile("joint-register",".pdf",activity.getCacheDir());
     new MontageConnections(activity).writePdf(Uri.fromFile(file));
     try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
         PdfRenderer pdf=new PdfRenderer(fd)){
      assertEquals(2,pdf.getPageCount());
     }
    }catch(Exception e){throw new AssertionError(e);}
    finally{activity.activeProject=original;activity.deleteSharedPreferences("project_"+testId);if(file!=null)file.delete();}
   });
  }
 }
 @Test public void restoreMetricProjectWithPipesAndBranches(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    String source=java.util.UUID.randomUUID().toString(),target=java.util.UUID.randomUUID().toString();
    File zip=null;
    try{
     android.content.SharedPreferences prefs=activity.projectStorage(source);
     assertTrue(prefs.edit().putString("project","Hangweg").putInt("metricOverlapMode",2)
         .putBoolean("metricIncludeCorners",true).putString("metricPipeMinGap","0.12").commit());
     new PipeLayoutStore(prefs).save(java.util.Collections.singletonList(new PipeLayout.Pipe("TW",.1,.16,.01,.2,.12,.8,true)));
     new RouteBranchStore(prefs).save(java.util.Collections.singletonList(new RouteBranch("a","Haus 2",4,.8,1.2,"Pflaster",new double[]{3},new double[]{90})));
     zip=File.createTempFile("metric",".zip",activity.getCacheDir());
     activity.writeProjectBackup(zip,prefs.getAll());
     try(java.io.InputStream in=new java.io.FileInputStream(zip)){
      activity.readProjectBackup(in,target,new java.util.ArrayList<>());
     }
     android.content.SharedPreferences copy=activity.projectStorage(target);
     assertEquals("Hangweg (Import)",copy.getString("project",""));
     assertEquals(2,copy.getInt("metricOverlapMode",0));assertTrue(copy.getBoolean("metricIncludeCorners",false));
     assertEquals("0.12",copy.getString("metricPipeMinGap",""));
     PipeLayout.Pipe pipe=new PipeLayoutStore(copy).load().get(0);
     assertTrue(pipe.fixed);assertEquals(.1,pipe.offset,0);assertEquals(.8,pipe.minCover,0);
     RouteBranch branch=new RouteBranchStore(copy).load().get(0);
     assertEquals("a",branch.id);assertEquals(3,branch.length(),0);
    }catch(Exception e){throw new AssertionError(e);}
    finally{if(zip!=null)zip.delete();activity.deleteSharedPreferences("project_"+source);activity.deleteSharedPreferences("project_"+target);}
   });
  }
 }
 @Test public void materialListCountsOnlyConfirmedInstallations(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{
    try{
     org.json.JSONObject fitting=new org.json.JSONObject().put("type","TEE").put("dn","80").put("material","PE");
     org.json.JSONArray rows=new org.json.JSONArray().put(fitting);
     assertEquals("",activity.montageMaterialText(rows));
     fitting.put("installed",true);
     rows.put(new org.json.JSONObject(fitting.toString())).put(new org.json.JSONObject(fitting.toString()).put("dn","100"));
     assertEquals("1 Stk.  T-Stück · DN 100 · PE\n2 Stk.  T-Stück · DN 80 · PE",activity.montageMaterialText(rows));
     fitting.put("installed",false);
     assertEquals("1 Stk.  T-Stück · DN 100 · PE\n1 Stk.  T-Stück · DN 80 · PE",activity.montageMaterialText(rows));
    }catch(org.json.JSONException e){throw new AssertionError(e);}
   });
  }
 }
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
 @Test public void fullscreenSaveReopensWithMeasuredRouteAndSurfaceBoundary(){
  String projectId=java.util.UUID.randomUUID().toString();
  String[] original=new String[1],expectedRoute=new String[1];
  String surfaces="[{\"from\":0,\"to\":4,\"surface\":\"Asphalt\"},{\"from\":4,\"to\":10,\"surface\":\"Pflaster\"}]";
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   try{
    scenario.onActivity(activity->{
     original[0]=activity.activeProject;
     assertTrue(activity.projectStorage(projectId).edit().putString("segments",surfaces)
      .putString("routeSketch","[[0.1,0.1,null],[0.5,0.1,4],[0.5,0.7,null]]").commit());
     assertTrue(activity.projectRegistry().edit().putString("active",projectId).commit());
    });
    scenario.recreate();
    scenario.onActivity(activity->activity.editRoute(false,true));
    androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(RouteSketch.class))
     .check((view,error)->{
      if(error!=null)throw error;
      RouteSketch route=(RouteSketch)view;
      route.vertices.get(2).metres=6;
      route.changed.run();
      expectedRoute[0]=route.encode();
     });
    androidx.test.espresso.Espresso.onView(org.hamcrest.Matchers.allOf(
     androidx.test.espresso.matcher.ViewMatchers.withText("Speichern"),
     androidx.test.espresso.matcher.ViewMatchers.isDisplayed())).perform(androidx.test.espresso.action.ViewActions.click());
    scenario.recreate();
    scenario.onActivity(activity->{
     assertEquals(projectId,activity.activeProject);
     assertEquals(expectedRoute[0],activity.routeData());
     assertEquals(10,new RouteSketch(activity,activity.routeData()).measuredLength(),0);
     assertEquals(2,activity.segments.length());
     assertEquals(4,activity.segments.optJSONObject(1).optDouble("from"),0);
     assertEquals("Pflaster",activity.segments.optJSONObject(1).optString("surface"));
    });
   }finally{
    scenario.onActivity(activity->{
     if(original[0]!=null)activity.projectRegistry().edit().putString("active",original[0]).commit();
    });
    scenario.recreate();
    scenario.onActivity(activity->activity.deleteSharedPreferences("project_"+projectId));
   }
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
