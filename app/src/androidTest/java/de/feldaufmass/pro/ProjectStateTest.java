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
