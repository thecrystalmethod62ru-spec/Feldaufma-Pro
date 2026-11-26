package de.feldaufmass.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Uniformly scaled metric route, also used for the first page of the installation plan. */
final class MontagePlanView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DxfExport model;
    private final ArrayList<double[]> positions = new ArrayList<>();
    private double minX, maxX, minY, maxY;

    MontagePlanView(Context context, DxfExport model, JSONArray fittings) {
        super(context);
        this.model = model;
        for (double[] point : model.vertices) extend(point);
        for (int i = 0; i < fittings.length(); i++) {
            JSONObject row = fittings.optJSONObject(i);
            double[] point = row == null ? null : model.mountingPoint(row.optDouble("station"), row.optDouble("offset", 0));
            positions.add(point);
            if (point != null) extend(point);
        }
        setContentDescription("Bemaßte Trasse mit nummerierten Formstückpositionen");
    }

    private void extend(double[] point) {
        minX = Math.min(minX, point[0]); maxX = Math.max(maxX, point[0]);
        minY = Math.min(minY, point[1]); maxY = Math.max(maxY, point[1]);
    }

    boolean hasPosition(int index) { return positions.get(index) != null; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        render(canvas, getWidth(), getHeight());
    }

    void render(Canvas canvas, int width, int height) {
        float unit = Math.min(width, height) / 240f;
        float pad = 20 * unit;
        double dx = maxX - minX, dy = maxY - minY;
        double scale = Math.min(dx == 0 ? Double.POSITIVE_INFINITY : (width-2*pad)/dx,
                                dy == 0 ? Double.POSITIVE_INFINITY : (height-2*pad)/dy);
        if (!Double.isFinite(scale) || scale <= 0) return;
        double left = (width-dx*scale)/2, top = (height-dy*scale)/2;
        paint.setColor(0xff276681); paint.setStrokeWidth(3*unit); paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 1; i < model.vertices.size(); i++) {
            double[] a = model.vertices.get(i-1), b = model.vertices.get(i);
            canvas.drawLine((float)(left+(a[0]-minX)*scale), (float)(top+(maxY-a[1])*scale),
                            (float)(left+(b[0]-minX)*scale), (float)(top+(maxY-b[1])*scale), paint);
        }
        paint.setTextSize(11*unit); paint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < positions.size(); i++) {
            double[] p = positions.get(i);
            if (p == null) continue;
            float x = (float)(left+(p[0]-minX)*scale), y = (float)(top+(maxY-p[1])*scale);
            String label = String.valueOf(i+1);
            paint.setColor(0xff944413); canvas.drawCircle(x,y,Math.max(8*unit,paint.measureText(label)/2+2*unit),paint);
            paint.setColor(0xffffffff); canvas.drawText(label,x,y-(paint.ascent()+paint.descent())/2,paint);
        }
    }
}
