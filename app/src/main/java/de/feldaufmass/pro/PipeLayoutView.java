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
    private double width=1,depth=1, minGap=0,minCover=0;
    public void setRequirements(double gap,double cover){
        PipeLayout.warnings(pipes,width,depth,gap,cover);
        minGap=gap;minCover=cover;invalidate();
    }
    private double cutLeft,cutRight,deck,base;
    private int surfaceColor=0xff777777;
    private boolean civilProfile;
    public void setCivilProfile(double left,double right,double deck,double base,int color){
        civilProfile=true;cutLeft=Double.isFinite(left)?Math.max(0,left):0;cutRight=Double.isFinite(right)?Math.max(0,right):0;
        this.deck=Double.isFinite(deck)?Math.max(0,deck):0;this.base=Double.isFinite(base)?Math.max(0,base):0;surfaceColor=color;invalidate();
    }
    private float scale,cx,bottom;
    private Selection selection;
    public interface Move { void move(int index,double offset,double bedding); }
    private Move move;
    private int dragged=-1, selectedPipe=-1;
    public void setSelectedPipe(int index){selectedPipe=index;invalidate();}
    private float startX,startY,dragScale,dragCx,dragBottom;
    private PipeLayout.Pipe original;
    private boolean moving;
    public void setMove(Move move){this.move=move;}
    public PipeLayoutView(Context context){super(context);setContentDescription("Rohrquerschnitt");}
    public void setLayoutData(List<PipeLayout.Pipe> pipes,double width,double depth){
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(depth)||depth<=0)throw new IllegalArgumentException("Ungültige Grabenmaße");
        this.pipes=new ArrayList<>(pipes);this.width=width;this.depth=depth;invalidate();
    }
    public void setSelection(Selection selection){this.selection=selection;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float pad=24*getResources().getDisplayMetrics().density;
        double extent=width/2+Math.max(cutLeft,cutRight),above=depth;
        for(PipeLayout.Pipe pipe:pipes){extent=Math.max(extent,Math.abs(pipe.offset)+pipe.diameter/2);above=Math.max(above,pipe.bedding+pipe.diameter);}
        scale=(float)Math.min((getWidth()-2*pad)/(2*extent),(getHeight()-2*pad)/above);
        if(dragged>=0&&dragScale>0){scale=dragScale;cx=dragCx;bottom=dragBottom;}
        else{cx=getWidth()/2f;bottom=getHeight()-pad;}
        if(scale<=0)return;
        float left=cx-(float)(width*scale/2),right=cx+(float)(width*scale/2),top=bottom-(float)(depth*scale);
        p.setColor(0x33b79b70);c.drawRect(left,top,right,bottom,p);
        p.setColor(0xff68945d);p.setStrokeWidth(3);c.drawLine(left,top,right,top,p);
        p.setColor(0xffb79b70);c.drawLine(left,top,left,bottom,p);c.drawLine(right,top,right,bottom,p);c.drawLine(left,bottom,right,bottom,p);
        if(civilProfile){
            float a=left-(float)(cutLeft*scale),b=right+(float)(cutRight*scale);
            p.setColor(0xffb7a48a);c.drawRect(0,top,left,bottom,p);c.drawRect(right,top,getWidth(),bottom,p);
            float deckBottom=top+(float)(Math.min(depth,deck)*scale),baseBottom=top+(float)(Math.min(depth,deck+base)*scale);
            p.setColor(surfaceColor);c.drawRect(0,top,a,deckBottom,p);c.drawRect(b,top,getWidth(),deckBottom,p);
            p.setColor(0xff969696);c.drawRect(0,deckBottom,left,baseBottom,p);c.drawRect(right,deckBottom,getWidth(),baseBottom,p);
            p.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);
            dimension(c,left,bottom+pad/2,right,bottom+pad/2,width);
            dimension(c,right+pad/3,top,right+pad/3,bottom,depth);
            if(cutLeft+cutRight>0)dimension(c,a,top-pad/2,b,top-pad/2,width+cutLeft+cutRight);
        }
        int[] colors={0xff397db5,0xffd4ab35,0xff398b75,0xff9567b8};
        p.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
        for(int i=0;i<pipes.size();i++){
            PipeLayout.Pipe pipe=pipes.get(i);
            float x=cx+(float)(pipe.offset*scale),y=bottom-(float)(pipe.centerHeight()*scale),r=(float)(pipe.diameter*scale/2);
            boolean conflict=Math.abs(pipe.offset)+pipe.diameter/2>width/2+1e-9||depth-pipe.bedding-pipe.diameter+1e-9<Math.max(minCover,pipe.minCover);
            for(int j=0;j<pipes.size();j++){
                if(i==j)continue;
                PipeLayout.Pipe other=pipes.get(j);
                double gap=Math.hypot(pipe.offset-other.offset,pipe.centerHeight()-other.centerHeight())-(pipe.diameter+other.diameter)/2;
                if(gap+1e-9<Math.max(minGap,Math.max(pipe.minGap,other.minGap)))conflict=true;
            }
            p.setColor(conflict?0xffd32f2f:colors[i%colors.length]);c.drawCircle(x,y,r,p);
            p.setColor(0xffeeeeee);c.drawCircle(x,y,r-(float)(pipe.wall*scale),p);
            p.setColor(conflict?0xffd32f2f:0xff777777);String label=pipe.name+(pipe.fixed?" [fix]":"");c.drawText(label,x-p.measureText(label)/2,y-r-4,p);
        }
        // Dimension the selected pipe while dragging, or the only pipe in the section.
        int selected=dragged>=0?dragged:(selectedPipe>=0?selectedPipe:(pipes.size()==1?0:-1));
        if(selected>=0&&selected<pipes.size()){
            PipeLayout.Pipe a=pipes.get(selected);
            float x=cx+(float)(a.offset*scale),y=bottom-(float)(a.centerHeight()*scale),r=(float)(a.diameter*scale/2);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xff55c7b5);c.drawCircle(x,y,r+4,p);p.setStyle(Paint.Style.FILL);
            dimension(c,left,y,x-r,y,width/2+a.offset-a.diameter/2);
            dimension(c,x+r,y,right,y,width/2-a.offset-a.diameter/2);
            dimension(c,x,top,x,y-r,depth-a.bedding-a.diameter);
            for(int j=0;j<pipes.size();j++){
                if(j==selected)continue;
                PipeLayout.Pipe b=pipes.get(j);
                double dx=b.offset-a.offset,dy=b.centerHeight()-a.centerHeight(),d=Math.hypot(dx,dy);
                if(d==0)continue;
                float ux=(float)(dx/d),uy=(float)(-dy/d);
                float bx=cx+(float)(b.offset*scale),by=bottom-(float)(b.centerHeight()*scale),br=(float)(b.diameter*scale/2);
                dimension(c,x+ux*r,y+uy*r,bx-ux*br,by-uy*br,d-(a.diameter+b.diameter)/2);
            }
        }
    }
    private void dimension(Canvas c,float x1,float y1,float x2,float y2,double metres){
        p.setColor(metres<0?0xffd32f2f:0xff777777);
        p.setStrokeWidth(1.5f);
        c.drawLine(x1,y1,x2,y2,p);
        double length=Math.hypot(x2-x1,y2-y1);
        if(length>0){
            float nx=(float)(-(y2-y1)/length*4),ny=(float)((x2-x1)/length*4);
            c.drawLine(x1-nx,y1-ny,x1+nx,y1+ny,p);
            c.drawLine(x2-nx,y2-ny,x2+nx,y2+ny,p);
        }
        String label=String.format(java.util.Locale.GERMANY,"%.3f m",metres);
        c.drawText(label,(x1+x2-p.measureText(label))/2,(y1+y2)/2-5,p);
    }
    private int hit(float x,float y){
        int best=-1;double distance=Double.POSITIVE_INFINITY;
        if(scale<=0)return best;
        for(int i=0;i<pipes.size();i++){
            PipeLayout.Pipe pipe=pipes.get(i);
            double d=Math.hypot(x-cx-pipe.offset*scale,y-bottom+pipe.centerHeight()*scale);
            if(d<=Math.max(pipe.diameter*scale/2,18*getResources().getDisplayMetrics().density)&&d<distance){best=i;distance=d;}
        }
        return best;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                dragged=hit(event.getX(),event.getY());startX=event.getX();startY=event.getY();dragScale=scale;dragCx=cx;dragBottom=bottom;moving=false;
                original=dragged>=0?pipes.get(dragged):null;
                if(dragged>=0&&move!=null&&getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if(dragged>=0&&original!=null)pipes.set(dragged,original);
                dragged=-1;original=null;moving=false;invalidate();
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(original!=null&&!original.fixed&&move!=null&&dragScale>0){
                    float dx=event.getX()-startX,dy=event.getY()-startY;
                    if(Math.hypot(dx,dy)>android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop())moving=true;
                    if(moving){
                        double o=Math.round((original.offset+dx/dragScale)*1000)/1000.0;
                        double b=Math.max(0,Math.round((original.bedding-dy/dragScale)*1000)/1000.0);
                        pipes.set(dragged,new PipeLayout.Pipe(original.name,o,original.diameter,original.wall,b,original.minGap,original.minCover,original.fixed));invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(dragged>=0&&moving&&move!=null){
                    PipeLayout.Pipe target=pipes.get(dragged);pipes.set(dragged,original);invalidate();
                    move.move(dragged,target.offset,target.bedding);
                }else if(dragged>=0&&selection!=null){selection.select(dragged);performClick();}
                break;
            case MotionEvent.ACTION_CANCEL:
                if(dragged>=0&&original!=null){pipes.set(dragged,original);invalidate();}
                break;
            default:return true;
        }
        dragged=-1;original=null;moving=false;invalidate();
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
