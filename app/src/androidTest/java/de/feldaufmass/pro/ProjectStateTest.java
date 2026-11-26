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
 private void verifyProjectState(MainActivity activity) throws Exception {
  activity.length.setText("20");
  activity.width.setText("1");
  activity.depth.setText("2");
  assertTrue(activity.calculate());
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
   assertTrue(renderer.getPageCount() >= 2);
  }
  activity.undoObjectsButton.performClick();
  activity.objectStation.setText("8");
  activity.addObject();
  assertFalse(activity.redoObjectsButton.isEnabled());
  assertEquals(2, activity.objects.length());
 }
}
