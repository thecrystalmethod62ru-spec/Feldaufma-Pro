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
    private final ProfileDimensionRenderer annotations=new ProfileDimensionRenderer(p);
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
    double profileWidth(){return width;}
    double profileDepth(){return depth;}
    List<PipeLayout.Pipe> profilePipes(){return new ArrayList<>(pipes);}
    public String dimensionSummary(){return String.format(java.util.Locale.GERMANY,"Breite: %.2f m\nTiefe: %.2f m",width,depth);}
    public void setSelection(Selection selection){this.selection=selection;}
    private void texture(Canvas c,float l,float t,float r,float b,ProfileMaterialRenderer.Pattern pattern){
        ProfileMaterialRenderer.draw(c,p,getWidth(),getHeight(),getResources().getDisplayMetrics().density,l,t,r,b,pattern);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        annotations.begin(getWidth(),getHeight(),getResources().getDisplayMetrics().density);
        float density=getResources().getDisplayMetrics().density;
        float pad=Math.min(32*density,Math.min(getWidth(),getHeight())*.14f);
        boolean technicalSheet=civilProfile&&getWidth()/density>=280&&getHeight()/density>=200;
        if(getWidth()<=0||getHeight()<=0)return;
        double leftExtent=width/2+cutLeft,rightExtent=width/2+cutRight,above=depth;
        for(PipeLayout.Pipe pipe:pipes){leftExtent=Math.max(leftExtent,-pipe.offset+pipe.diameter/2);rightExtent=Math.max(rightExtent,pipe.offset+pipe.diameter/2);above=Math.max(above,pipe.bedding+pipe.diameter);}
        float annotationGutter=technicalSheet?Math.min(96*density,getWidth()*.24f):pad;
        scale=(float)Math.min((getWidth()-pad-annotationGutter)/(leftExtent+rightExtent),(getHeight()-2*pad)/above);
        if(dragged>=0&&dragScale>0){scale=dragScale;cx=dragCx;bottom=dragBottom;}
        else{cx=(pad+getWidth()-annotationGutter)/2f+(float)((leftExtent-rightExtent)*scale/2);bottom=getHeight()-pad;}
        if(!Float.isFinite(scale)||scale<=0)return;
        float left=cx-(float)(width*scale/2),right=cx+(float)(width*scale/2),top=bottom-(float)(depth*scale);
        texture(c,left,top,right,bottom,ProfileMaterialRenderer.Pattern.BACKFILL);
        if(civilProfile){float dt=top+(float)(Math.min(depth,deck)*scale),bt=top+(float)(Math.min(depth,deck+base)*scale);texture(c,left,top,right,dt,asphaltProfile?ProfileMaterialRenderer.Pattern.ASPHALT:ProfileMaterialRenderer.Pattern.GRAVEL);texture(c,left,dt,right,bt,ProfileMaterialRenderer.Pattern.GRAVEL);p.setColor(0xffeeeeee);if(deck>0)c.drawLine(left,dt,right,dt,p);if(base>0)c.drawLine(left,bt,right,bt,p);}
        // Bedding is the stored distance from trench bottom to pipe underside.
        // Do not assume a material thickness or cover that is not in the project.
        float beddingTop=bottom;
        if(civilProfile&&!pipes.isEmpty()){
            double bedding=Double.POSITIVE_INFINITY;
            for(PipeLayout.Pipe pipe:pipes)bedding=Math.min(bedding,pipe.bedding);
            if(Double.isFinite(bedding)&&bedding>0){
                float bedTop=bottom-(float)(Math.min(bedding,Math.max(0,depth-deck-base))*scale);
                beddingTop=bedTop;
                texture(c,left,bedTop,right,bottom,ProfileMaterialRenderer.Pattern.SAND);
                p.setColor(0xffeeeeee);p.setStrokeWidth(Math.max(1f,density*.7f));
                c.drawLine(left,bedTop,right,bedTop,p);
            }
        }
        p.setColor(0xffeeeeee);p.setStrokeWidth(1.5f);c.drawLine(left,top,right,top,p);
        p.setColor(0xffeeeeee);c.drawLine(left,top,left,bottom,p);c.drawLine(right,top,right,bottom,p);c.drawLine(left,bottom,right,bottom,p);
        if(civilProfile){
            float a=left-(float)(cutLeft*scale),b=right+(float)(cutRight*scale);
            texture(c,0,top,left,bottom,ProfileMaterialRenderer.Pattern.NATIVE_SOIL);texture(c,right,top,getWidth(),bottom,ProfileMaterialRenderer.Pattern.NATIVE_SOIL);
            float deckBottom=top+(float)(Math.min(depth,deck)*scale),baseBottom=top+(float)(Math.min(depth,deck+base)*scale);
            texture(c,0,top,a,deckBottom,asphaltProfile?ProfileMaterialRenderer.Pattern.ASPHALT:ProfileMaterialRenderer.Pattern.GRAVEL);texture(c,b,top,getWidth(),deckBottom,asphaltProfile?ProfileMaterialRenderer.Pattern.ASPHALT:ProfileMaterialRenderer.Pattern.GRAVEL);
            texture(c,0,deckBottom,left,baseBottom,ProfileMaterialRenderer.Pattern.GRAVEL);texture(c,right,deckBottom,getWidth(),baseBottom,ProfileMaterialRenderer.Pattern.GRAVEL);
            p.setColor(0xffeeeeee);p.setStrokeWidth(1.5f);c.drawLine(left,top,left,bottom,p);c.drawLine(right,top,right,bottom,p);c.drawLine(left,bottom,right,bottom,p);
            p.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);

            p.setColor(0xffeeeeee);p.setStrokeWidth(Math.max(.7f,density*.7f));
            c.drawLine(0,top,getWidth(),top,p);
            if(deckBottom>top)c.drawLine(0,deckBottom,getWidth(),deckBottom,p);
            if(baseBottom>deckBottom)c.drawLine(0,baseBottom,getWidth(),baseBottom,p);
            if(cutLeft+cutRight>0)annotations.dimension(c,a,top-pad*.80f,b,top-pad*.80f,width+cutLeft+cutRight);
            // Show actual layer values, not illustrative sample dimensions.
            if(deck>0&&deckBottom>top&&(technicalSheet||deckBottom-top>=10*density))annotations.leader(c,String.format(java.util.Locale.GERMANY,"%s %.2f m",asphaltProfile?"Asphalt":"Deckschicht",Math.min(depth,deck)),right-3*density,(top+deckBottom)/2,right+12*density,(top+deckBottom)/2+4*density,0xffeeeeee);
            if(base>0&&baseBottom>deckBottom&&(technicalSheet||baseBottom-deckBottom>=10*density))annotations.leader(c,String.format(java.util.Locale.GERMANY,"Tragschicht %.2f m",Math.min(base,Math.max(0,depth-deck))),right-3*density,(deckBottom+baseBottom)/2,right+12*density,(deckBottom+baseBottom)/2+4*density,0xffeeeeee);
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
        annotations.dimension(c,left,top-pad*.28f,right,top-pad*.28f,width);
        annotations.dimension(c,left-pad*.55f,top,left-pad*.55f,bottom,depth);
        if(civilProfile&&getWidth()/density>=280&&getHeight()/density>=220){
            float fillTop=top+(float)(Math.min(depth,deck+base)*scale);
            if(beddingTop-fillTop>18*density)annotations.leader(c,"Verfüllung",right-4*density,(fillTop+beddingTop)/2,right+12*density,(fillTop+beddingTop)/2,0xffeeeeee);
            if(bottom-beddingTop>3*density)annotations.leader(c,String.format(java.util.Locale.GERMANY,"Bettung %.2f m",(bottom-beddingTop)/scale),right-4*density,(beddingTop+bottom)/2,right+12*density,bottom-4*density,0xffeeeeee);
        }
        int[] colors={0xffeeeeee,0xffd4ab35,0xff398b75,0xff9567b8};
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
            p.setStyle(Paint.Style.FILL);p.setColor(conflict?0xffd32f2f:colors[i%colors.length]);c.drawCircle(x,y,r,p);
            float inner=Math.max(0,r-(float)(pipe.wall*scale));
            p.setColor(0xff080c0d);c.drawCircle(x,y,inner,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(.7f,density*.7f));
            p.setColor(conflict?0xffff8a80:0xffeeeeee);c.drawCircle(x,y,r,p);
            if(inner>0)c.drawCircle(x,y,inner,p);
            p.setStyle(Paint.Style.FILL);
            if(technicalSheet){p.setColor(0xff949d9d);p.setStrokeWidth(Math.max(.7f,density*.5f));float axis=Math.min(r*.55f,5*density);c.drawLine(x-axis,y,x+axis,y,p);c.drawLine(x,y-axis,x,y+axis,p);}
            String label=pipe.name+(pipe.fixed?" [fix]":"");annotations.drawLabel(c,label,x-p.measureText(label)/2,y-r-4,conflict?0xffff8a80:0xffeeeeee);
        }
        // Dimension the selected pipe while dragging, or the only pipe in the section.
        int selected=dragged>=0?dragged:(selectedPipe>=0?selectedPipe:(pipes.size()==1?0:-1));
        if(selected>=0&&selected<pipes.size()){
            PipeLayout.Pipe a=pipes.get(selected);
            float x=cx+(float)(a.offset*scale),y=bottom-(float)(a.centerHeight()*scale),r=(float)(a.diameter*scale/2);
            if(dragged>=0||selectedPipe>=0){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xff55c7b5);c.drawCircle(x,y,r+4,p);p.setStyle(Paint.Style.FILL);}
            annotations.dimension(c,left,y,x-r,y,width/2+a.offset-a.diameter/2);
            annotations.dimension(c,x+r,y,right,y,width/2-a.offset-a.diameter/2);
            annotations.dimension(c,x,top,x,y-r,depth-a.bedding-a.diameter);
            for(int j=0;j<pipes.size();j++){
                if(j==selected)continue;
                PipeLayout.Pipe b=pipes.get(j);
                double dx=b.offset-a.offset,dy=b.centerHeight()-a.centerHeight(),d=Math.hypot(dx,dy);
                if(d==0)continue;
                float ux=(float)(dx/d),uy=(float)(-dy/d);
                float bx=cx+(float)(b.offset*scale),by=bottom-(float)(b.centerHeight()*scale),br=(float)(b.diameter*scale/2);
                annotations.dimension(c,x+ux*r,y+uy*r,bx-ux*br,by-uy*br,d-(a.diameter+b.diameter)/2);
            }
        }
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
