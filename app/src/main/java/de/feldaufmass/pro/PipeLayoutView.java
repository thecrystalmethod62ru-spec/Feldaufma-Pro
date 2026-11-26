package de.feldaufmass.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;

public final class PipeLayoutView extends View {
    public interface Selection { void select(int index); }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<PipeLayout.Pipe> pipes=new ArrayList<>();
    private double width=1,depth=1;
    private float scale,cx,bottom;
    private Selection selection;
    public PipeLayoutView(Context context){super(context);setContentDescription("Rohrquerschnitt");}
    public void setLayoutData(List<PipeLayout.Pipe> pipes,double width,double depth){
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(depth)||depth<=0)throw new IllegalArgumentException("Ungültige Grabenmaße");
        this.pipes=new ArrayList<>(pipes);this.width=width;this.depth=depth;invalidate();
    }
    public void setSelection(Selection selection){this.selection=selection;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float pad=24*getResources().getDisplayMetrics().density;
        double extent=width/2,above=depth;
        for(PipeLayout.Pipe pipe:pipes){extent=Math.max(extent,Math.abs(pipe.offset)+pipe.diameter/2);above=Math.max(above,pipe.bedding+pipe.diameter);}
        scale=(float)Math.min((getWidth()-2*pad)/(2*extent),(getHeight()-2*pad)/above);
        if(scale<=0)return;
        cx=getWidth()/2f;bottom=getHeight()-pad;
        float left=cx-(float)(width*scale/2),right=cx+(float)(width*scale/2),top=bottom-(float)(depth*scale);
        p.setColor(0x33b79b70);c.drawRect(left,top,right,bottom,p);
        p.setColor(0xff68945d);p.setStrokeWidth(3);c.drawLine(left,top,right,top,p);
        p.setColor(0xffb79b70);c.drawLine(left,top,left,bottom,p);c.drawLine(right,top,right,bottom,p);c.drawLine(left,bottom,right,bottom,p);
        int[] colors={0xff397db5,0xffd4ab35,0xffc95353,0xff9567b8};
        p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
        for(int i=0;i<pipes.size();i++){
            PipeLayout.Pipe pipe=pipes.get(i);
            float x=cx+(float)(pipe.offset*scale),y=bottom-(float)(pipe.centerHeight()*scale),r=(float)(pipe.diameter*scale/2);
            p.setColor(colors[i%colors.length]);c.drawCircle(x,y,r,p);
            p.setColor(0xffeeeeee);c.drawCircle(x,y,r-(float)(pipe.wall*scale),p);
            p.setColor(0xff777777);c.drawText(pipe.name,x-p.measureText(pipe.name)/2,y-r-4,p);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_DOWN)return true;
        if(event.getAction()==MotionEvent.ACTION_UP){
            int best=-1;double distance=Double.POSITIVE_INFINITY;
            for(int i=0;i<pipes.size();i++){
                PipeLayout.Pipe pipe=pipes.get(i);
                double d=Math.hypot(event.getX()-cx-pipe.offset*scale,event.getY()-bottom+pipe.centerHeight()*scale);
                if(d<=Math.max(pipe.diameter*scale/2,18*getResources().getDisplayMetrics().density)&&d<distance){best=i;distance=d;}
            }
            if(best>=0&&selection!=null)selection.select(best);
            performClick();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
