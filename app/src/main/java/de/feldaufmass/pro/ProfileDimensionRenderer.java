package de.feldaufmass.pro;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

/** Dimension lines and collision-aware annotation placement. */
final class ProfileDimensionRenderer {
 private final Paint p;
 private final List<RectF> dimensionLabels=new ArrayList<>();
 private float density;
 private int width,height;
 ProfileDimensionRenderer(Paint paint){p=paint;}
 void begin(int width,int height,float density){
  this.width=width;this.height=height;this.density=density;dimensionLabels.clear();
 }
    void dimension(Canvas c,float x1,float y1,float x2,float y2,double metres){
        int color=metres<0?0xffff8a80:0xffeeeeee;
        float d=density;
        // A dark underlay keeps dimensions readable over dense material hatches.
        p.setColor(0xff000000);
        p.setStrokeWidth(2.4f*d);
        c.drawLine(x1,y1,x2,y2,p);
        p.setColor(color);
        p.setStrokeWidth(.8f*d);
        c.drawLine(x1,y1,x2,y2,p);
        double length=Math.hypot(x2-x1,y2-y1);
        if(length>0){
            float tick=(float)Math.min(4*d,length/4);
            float nx=(float)(-(y2-y1)/length*tick),ny=(float)((x2-x1)/length*tick);
            c.drawLine(x1-nx,y1-ny,x1+nx,y1+ny,p);
            c.drawLine(x2-nx,y2-ny,x2+nx,y2+ny,p);
            float arrow=(float)Math.min(6*d,length/3);
            float ux=(float)((x2-x1)/length*arrow),uy=(float)((y2-y1)/length*arrow);
            c.drawLine(x1,y1,x1+ux+nx,y1+uy+ny,p);c.drawLine(x1,y1,x1+ux-nx,y1+uy-ny,p);
            c.drawLine(x2,y2,x2-ux+nx,y2-uy+ny,p);c.drawLine(x2,y2,x2-ux-nx,y2-uy-ny,p);
        }
        String label=String.format(java.util.Locale.GERMANY,Math.abs(metres*100-Math.rint(metres*100))<1e-7?"%.2f m":"%.3f m",metres);
        float x=(x1+x2-p.measureText(label))/2,y=(y1+y2)/2-5*d;
        x=Math.max(4*d,Math.min(x,width-p.measureText(label)-4*d));y=Math.max(p.getTextSize()+4*d,Math.min(y,height-4*d));
        float textWidth=p.measureText(label), textHeight=p.getTextSize();
        android.graphics.RectF bounds=new android.graphics.RectF();
        float originalX=x,originalY=y;
        boolean placed=false;
        for(int i=0;i<25;i++){
            int ring=(i+1)/2;
            float offset=(i%2==1?-1:1)*ring*(textHeight+8*d);
            x=originalX;
            y=Math.max(textHeight+4*d,Math.min(originalY+offset,height-4*d));
            bounds.set(x-3*d,y-textHeight,x+textWidth+3*d,y+3*d);
            boolean overlaps=false;
            for(android.graphics.RectF other:dimensionLabels){
                if(android.graphics.RectF.intersects(bounds,other)){overlaps=true;break;}
            }
            if(!overlaps){placed=true;break;}
        }
        if(!placed){x=originalX;y=originalY;bounds.set(x-3*d,y-textHeight,x+textWidth+3*d,y+3*d);}
        dimensionLabels.add(new android.graphics.RectF(bounds));
        if(placed&&Math.abs(y-originalY)>d){
            p.setColor(color);p.setStrokeWidth(d);
            c.drawLine((x1+x2)/2,(y1+y2)/2,x+textWidth/2,y-textHeight/2,p);
        }
        p.setStyle(Paint.Style.FILL);p.setColor(0xff000000);c.drawRect(x-2*d,y-p.getTextSize(),x+p.measureText(label)+2*d,y+2*d,p);
        p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawText(label,x,y,p);
    }
    void drawLabel(Canvas c,String label,float x,float y,int color){
        drawLabelInternal(c,label,x,y,color,Float.NaN,Float.NaN);
    }
    void leader(Canvas c,String label,float anchorX,float anchorY,float x,float y,int color){
        drawLabelInternal(c,label,x,y,color,anchorX,anchorY);
    }
    private void drawLabelInternal(Canvas c,String label,float x,float y,int color,float anchorX,float anchorY){
        float d=density,w=p.measureText(label),h=p.getTextSize();
        x=Math.max(4*d,Math.min(x,width-w-4*d));
        float originalY=y;
        android.graphics.RectF bounds=new android.graphics.RectF();
        for(int i=0;i<25;i++){
            int ring=(i+1)/2;
            y=Math.max(h+4*d,Math.min(originalY+(i%2==1?-1:1)*ring*(h+8*d),height-4*d));
            bounds.set(x-3*d,y-h,x+w+3*d,y+3*d);
            boolean overlap=false;
            for(android.graphics.RectF other:dimensionLabels){if(android.graphics.RectF.intersects(bounds,other)){overlap=true;break;}}
            if(!overlap)break;
        }
        dimensionLabels.add(new android.graphics.RectF(bounds));
        Paint.Style previous=p.getStyle();float stroke=p.getStrokeWidth();
        if(Float.isFinite(anchorX)&&Float.isFinite(anchorY)){
            float endX=anchorX<x+w/2?x-3*d:x+w+3*d;
            float endY=y-h/2;
            float elbow=endX+(anchorX<endX?-6*d:6*d);
            p.setStyle(Paint.Style.FILL);p.setColor(0xff000000);p.setStrokeWidth(2.5f*d);
            c.drawLine(anchorX,anchorY,elbow,endY,p);c.drawLine(elbow,endY,endX,endY,p);
            p.setColor(color);p.setStrokeWidth(Math.max(0.7f,d*.7f));
            c.drawLine(anchorX,anchorY,elbow,endY,p);c.drawLine(elbow,endY,endX,endY,p);c.drawCircle(anchorX,anchorY,1.3f*d,p);
        }
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xff141414);c.drawText(label,x,y,p);
        p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawText(label,x,y,p);
        p.setStyle(previous);p.setStrokeWidth(stroke);
    }
}
