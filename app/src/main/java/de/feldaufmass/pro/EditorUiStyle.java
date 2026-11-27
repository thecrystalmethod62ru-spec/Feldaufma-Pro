package de.feldaufmass.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.widget.Button;

/** Shared editor styling and vector icons; independent of project state. */
final class EditorUiStyle {
 private final Context context;
 EditorUiStyle(Context context){this.context=context;}
 private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
 android.graphics.drawable.GradientDrawable editorShape(int color,int radius){
  android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(radius));return shape;
 }
 android.graphics.drawable.GradientDrawable proButtonShape(boolean primary){
  android.graphics.drawable.GradientDrawable shape=primary?new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xff008b91,0xff034347}):editorShape(0xff041012,8);
  shape.setCornerRadius(dp(8));shape.setStroke(dp(1),primary?0xff00e5ee:0xff008b93);return shape;
 }
 void styleEditorButton(Button b,boolean primary){
  b.setAllCaps(false);b.setTextSize(primary?15:12);b.setTextColor(Color.WHITE);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),dp(4),dp(4),dp(4));b.setBackgroundTintList(null);
  b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3312665f),proButtonShape(primary),null));
 }
 void compactToolIcon(Button button,String label,String kind){
  android.graphics.drawable.Drawable icon=new android.graphics.drawable.Drawable(){
   final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
   @Override public void draw(Canvas c){
    c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
    p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
    switch(kind){
     case "undo":case "redo":{boolean reverse=kind.equals("redo");c.save();if(reverse)c.scale(-1,1,12,12);c.drawLine(4,9,10,3,p);c.drawLine(4,9,10,15,p);c.drawLine(4,9,15,9,p);c.drawArc(9,9,21,21,-90,180,false,p);c.restore();break;}
     case "cursor":{android.graphics.Path cursor=new android.graphics.Path();cursor.moveTo(5,3);cursor.lineTo(20,14);cursor.lineTo(13,15);cursor.lineTo(10,21);cursor.close();c.drawPath(cursor,p);break;}
     case "pencil":c.drawLine(4,20,7,13,p);c.drawLine(7,13,17,3,p);c.drawLine(17,3,21,7,p);c.drawLine(21,7,11,17,p);c.drawLine(11,17,4,20,p);c.drawLine(14,6,18,10,p);break;
     case "nodes":c.drawLine(4,19,20,5,p);for(int k=0;k<3;k++)c.drawCircle(4+8*k,19-7*k,2.5f,p);break;
     case "measure":c.drawLine(3,12,21,12,p);c.drawLine(3,7,3,17,p);c.drawLine(21,7,21,17,p);c.drawLine(3,12,8,8,p);c.drawLine(3,12,8,16,p);c.drawLine(21,12,16,8,p);c.drawLine(21,12,16,16,p);break;
     case "folder":c.drawRoundRect(3,7,21,20,2,2,p);c.drawLine(3,7,3,4,p);c.drawLine(3,4,10,4,p);c.drawLine(10,4,13,7,p);break;
     case "document":c.drawRoundRect(5,3,19,21,2,2,p);for(int yy=8;yy<=16;yy+=4)c.drawLine(8,yy,16,yy,p);break;
     case "save":c.drawRoundRect(4,3,20,21,2,2,p);c.drawRect(8,3,16,9,p);c.drawRect(8,13,16,21,p);break;
     case "back":c.drawLine(15,5,8,12,p);c.drawLine(8,12,15,19,p);break;
     case "map":{android.graphics.Path map=new android.graphics.Path();map.moveTo(3,5);map.lineTo(9,3);map.lineTo(15,6);map.lineTo(21,3);map.lineTo(21,19);map.lineTo(15,22);map.lineTo(9,19);map.lineTo(3,21);map.close();c.drawPath(map,p);c.drawLine(9,3,9,19,p);c.drawLine(15,6,15,22,p);break;}
     case "image":c.drawRoundRect(3,4,21,20,2,2,p);c.drawCircle(8,9,2,p);c.drawLine(4,18,11,12,p);c.drawLine(11,12,16,17,p);break;
     case "mic":c.drawRoundRect(9,3,15,14,3,3,p);c.drawArc(6,7,18,18,0,180,false,p);c.drawLine(12,18,12,22,p);c.drawLine(8,22,16,22,p);break;
     case "ruler":c.save();c.rotate(-45,12,12);c.drawRect(8,3,16,21,p);for(int y=6;y<20;y+=3)c.drawLine(8,y,y%2==0?12:11,y,p);c.restore();break;
     case "center":c.drawCircle(12,12,5,p);c.drawLine(12,2,12,7,p);c.drawLine(12,17,12,22,p);c.drawLine(2,12,7,12,p);c.drawLine(17,12,22,12,p);break;
     case "view":for(int x:new int[]{4,20})for(int y:new int[]{4,20}){c.drawLine(x,y,x+(x==4?5:-5),y,p);c.drawLine(x,y,x,y+(y==4?5:-5),p);}break;
     case "legend":for(int y=6;y<=18;y+=6){c.drawCircle(5,y,1,p);c.drawLine(9,y,20,y,p);}break;
     case "objects":c.drawRoundRect(5,5,19,19,2,2,p);c.drawLine(9,12,15,12,p);c.drawLine(12,9,12,15,p);break;
     case "sections":c.drawLine(3,12,21,12,p);for(int x:new int[]{3,9,15,21}){c.drawLine(x,7,x,17,p);}break;
     case "pipe":c.drawCircle(7.5f,12,6,p);c.drawCircle(7.5f,12,3.8f,p);c.drawCircle(19,14,4,p);c.drawCircle(19,14,2.1f,p);break;
     case "settings":c.drawCircle(12,12,7,p);c.drawCircle(12,12,3,p);for(int k=0;k<8;k++){c.save();c.rotate(k*45,12,12);c.drawLine(12,2,12,5,p);c.restore();}break;
     case "cloud":{android.graphics.Path cloud=new android.graphics.Path();cloud.moveTo(6,18);cloud.cubicTo(0,18,1,9,7,9);cloud.cubicTo(7,2,18,2,18,10);cloud.cubicTo(24,10,24,18,18,18);cloud.close();c.drawPath(cloud,p);break;}
      case "layers":c.drawLine(4,8,12,3,p);c.drawLine(12,3,20,8,p);c.drawLine(20,8,12,13,p);c.drawLine(12,13,4,8,p);c.drawLine(4,13,12,18,p);c.drawLine(12,18,20,13,p);c.drawLine(4,18,12,22,p);c.drawLine(12,22,20,18,p);break;
     default:for(int y=6;y<=18;y+=6)c.drawLine(4,y,20,y,p);
    }c.restore();
   }
   @Override public void setAlpha(int a){p.setAlpha(a);}
   @Override public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);}
   @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
  };
  icon.setBounds(0,0,dp(20),dp(20));button.setText(label);button.setContentDescription(label);button.setCompoundDrawables(null,icon,null,null);button.setCompoundDrawablePadding(dp(3));button.setGravity(Gravity.CENTER);button.setMinHeight(dp(52));button.setTextSize(12);
 }
}
