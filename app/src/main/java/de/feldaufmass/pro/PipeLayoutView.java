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
    private final List<android.graphics.RectF> dimensionLabels=new ArrayList<>();
    private List<PipeLayout.Pipe> pipes=new ArrayList<>();
    private double width=1,depth=1, minGap=0,minCover=0;
    public void setRequirements(double gap,double cover){
        PipeLayout.warnings(pipes,width,depth,gap,cover);
        minGap=gap;minCover=cover;invalidate();
    }
    private double cutLeft,cutRight,deck,base;
    private int surfaceColor=0xff777777;
    private boolean civilProfile;
    private boolean asphaltProfile;
    public void setAsphaltProfile(boolean asphalt){asphaltProfile=asphalt;invalidate();}
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
    public PipeLayoutView(Context context){super(context);setBackgroundColor(android.graphics.Color.BLACK);setContentDescription("Rohrquerschnitt");}
    public void setLayoutData(List<PipeLayout.Pipe> pipes,double width,double depth){
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(depth)||depth<=0)throw new IllegalArgumentException("Ungültige Grabenmaße");
        this.pipes=new ArrayList<>(pipes);this.width=width;this.depth=depth;invalidate();
    }
    public String dimensionSummary(){return String.format(java.util.Locale.GERMANY,"Breite: %.2f m\nTiefe: %.2f m",width,depth);}
    public void setSelection(Selection selection){this.selection=selection;}
    private void texture(Canvas c,float l,float t,float r,float b,int fill,int grain,boolean gravel){
        l=Math.max(0,l);t=Math.max(0,t);r=Math.min(getWidth(),r);b=Math.min(getHeight(),b);
        if(r<=l||b<=t)return;
        p.setStyle(Paint.Style.FILL);p.setColor(fill);c.drawRect(l,t,r,b,p);
        c.save();c.clipRect(l,t,r,b);p.setColor(grain);p.setStrokeWidth(1);
        float step=6*getResources().getDisplayMetrics().density;
        for(int row=0;row<(b-t)/step+1;row++)for(float x=l+step/3+(row%2)*step/2;x<r;x+=step){
            float y=t+step/3+row*step;
            if(gravel){p.setStyle(Paint.Style.STROKE);c.drawOval(x,y,x+step/3,y+step/4,p);}
            else {p.setStyle(Paint.Style.FILL);float k=(float)(.65+.35*Math.sin(x*1.7+y*.8));c.drawOval(x,y,x+step*.38f*k,y+step*.22f,p);}
        }
        p.setStyle(Paint.Style.FILL);c.restore();
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        dimensionLabels.clear();
        float density=getResources().getDisplayMetrics().density;
        float pad=Math.min(42*density,Math.min(getWidth(),getHeight())*.20f);
        if(getWidth()<=0||getHeight()<=0)return;
        double leftExtent=width/2+cutLeft,rightExtent=width/2+cutRight,above=depth;
        for(PipeLayout.Pipe pipe:pipes){leftExtent=Math.max(leftExtent,-pipe.offset+pipe.diameter/2);rightExtent=Math.max(rightExtent,pipe.offset+pipe.diameter/2);above=Math.max(above,pipe.bedding+pipe.diameter);}
        scale=(float)Math.min((getWidth()-2*pad)/(leftExtent+rightExtent),(getHeight()-2*pad)/above);
        if(dragged>=0&&dragScale>0){scale=dragScale;cx=dragCx;bottom=dragBottom;}
        else{cx=getWidth()/2f+(float)((leftExtent-rightExtent)*scale/2);bottom=getHeight()-pad;}
        if(!Float.isFinite(scale)||scale<=0)return;
        float left=cx-(float)(width*scale/2),right=cx+(float)(width*scale/2),top=bottom-(float)(depth*scale);
        texture(c,left,top,right,bottom,0xff674323,0xffa87949,false);
        p.setColor(0xff00e5ee);p.setStrokeWidth(1.5f);c.drawLine(left,top,right,top,p);
        p.setColor(0xff00e5ee);c.drawLine(left,top,left,bottom,p);c.drawLine(right,top,right,bottom,p);c.drawLine(left,bottom,right,bottom,p);
        if(civilProfile){
            float a=left-(float)(cutLeft*scale),b=right+(float)(cutRight*scale);
            texture(c,0,top,left,bottom,0xff302922,0xff78634d,false);texture(c,right,top,getWidth(),bottom,0xff302922,0xff78634d,false);
            float deckBottom=top+(float)(Math.min(depth,deck)*scale),baseBottom=top+(float)(Math.min(depth,deck+base)*scale);
            texture(c,0,top,a,deckBottom,asphaltProfile?0xff383c3f:surfaceColor,asphaltProfile?0xff777b7e:0xff96938a, !asphaltProfile);texture(c,b,top,getWidth(),deckBottom,asphaltProfile?0xff383c3f:surfaceColor,asphaltProfile?0xff777b7e:0xff96938a,!asphaltProfile);
            texture(c,0,deckBottom,left,baseBottom,0xff666052,0xffa39a83,true);texture(c,right,deckBottom,getWidth(),baseBottom,0xff666052,0xffa39a83,true);
            p.setColor(0xff00e5ee);p.setStrokeWidth(1.5f);c.drawLine(left,top,left,bottom,p);c.drawLine(right,top,right,bottom,p);c.drawLine(left,bottom,right,bottom,p);
            p.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);

            if(cutLeft+cutRight>0)dimension(c,a,top-pad/2,b,top-pad/2,width+cutLeft+cutRight);
            if(asphaltProfile&&deck>0){
                float cutBottom=deckBottom;
                p.setColor(0xffd5d9dc);p.setStrokeWidth(1.5f);
                c.drawLine(left,top-4,left,cutBottom,p);c.drawLine(right,top-4,right,cutBottom,p);
                android.graphics.PathEffect prior=p.getPathEffect();
                p.setPathEffect(new android.graphics.DashPathEffect(new float[]{4,2},0));
                p.setColor(0xffffb267);
                if(cutLeft>0)c.drawLine(a,top-4,a,cutBottom,p);
                if(cutRight>0)c.drawLine(b,top-4,b,cutBottom,p);
                p.setPathEffect(prior);
            }
        }
        p.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);
        dimension(c,left,bottom+pad/2,right,bottom+pad/2,width);
        dimension(c,right+pad/3,top,right+pad/3,bottom,depth);
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
            p.setColor(0xff101719);c.drawCircle(x,y,Math.max(0,r-(float)(pipe.wall*scale)),p);
            String label=pipe.name+(pipe.fixed?" [fix]":"");drawLabel(c,label,x-p.measureText(label)/2,y-r-4,conflict?0xffff8a80:0xffeeeeee);
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
        int color=metres<0?0xffff8a80:0xff00e5ee;
        float d=getResources().getDisplayMetrics().density;
        p.setColor(color);
        p.setStrokeWidth(1.5f*d);
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
        String label=String.format(java.util.Locale.GERMANY,"%.3f m",metres);
        float x=(x1+x2-p.measureText(label))/2,y=(y1+y2)/2-5*d;
        x=Math.max(4*d,Math.min(x,getWidth()-p.measureText(label)-4*d));y=Math.max(p.getTextSize()+4*d,Math.min(y,getHeight()-4*d));
        float textWidth=p.measureText(label), textHeight=p.getTextSize();
        android.graphics.RectF bounds=new android.graphics.RectF();
        float originalX=x,originalY=y;
        boolean placed=false;
        for(int i=0;i<25;i++){
            int ring=(i+1)/2;
            float offset=(i%2==1?-1:1)*ring*(textHeight+8*d);
            x=originalX;
            y=Math.max(textHeight+4*d,Math.min(originalY+offset,getHeight()-4*d));
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
        p.setStyle(Paint.Style.FILL);p.setColor(0xff041012);c.drawRoundRect(x-3*d,y-p.getTextSize(),x+p.measureText(label)+3*d,y+3*d,3*d,3*d,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(color);c.drawRoundRect(x-3*d,y-p.getTextSize(),x+p.measureText(label)+3*d,y+3*d,3*d,3*d,p);p.setStyle(Paint.Style.FILL);c.drawText(label,x,y,p);
    }
    private void drawLabel(Canvas c,String label,float x,float y,int color){
        float d=getResources().getDisplayMetrics().density,w=p.measureText(label),h=p.getTextSize();
        x=Math.max(4*d,Math.min(x,getWidth()-w-4*d));
        float originalY=y;
        android.graphics.RectF bounds=new android.graphics.RectF();
        for(int i=0;i<25;i++){
            int ring=(i+1)/2;
            y=Math.max(h+4*d,Math.min(originalY+(i%2==1?-1:1)*ring*(h+8*d),getHeight()-4*d));
            bounds.set(x-3*d,y-h,x+w+3*d,y+3*d);
            boolean overlap=false;
            for(android.graphics.RectF other:dimensionLabels){if(android.graphics.RectF.intersects(bounds,other)){overlap=true;break;}}
            if(!overlap)break;
        }
        dimensionLabels.add(new android.graphics.RectF(bounds));
        Paint.Style previous=p.getStyle();float stroke=p.getStrokeWidth();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xff141414);c.drawText(label,x,y,p);
        p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawText(label,x,y,p);
        p.setStyle(previous);p.setStrokeWidth(stroke);
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
