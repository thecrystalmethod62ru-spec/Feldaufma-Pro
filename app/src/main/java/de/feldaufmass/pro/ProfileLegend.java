package de.feldaufmass.pro;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ProfileLegend {
 private ProfileLegend(){}
 static LinearLayout create(Context context){
 LinearLayout profileLegend=new LinearLayout(context);profileLegend.setOrientation(1);profileLegend.setPadding(dp(context,4),dp(context,6),dp(context,4),dp(context,6));TextView legendTitle=label(context,"LEGENDE",12,true);legendTitle.setTextColor(0xff28d5c7);profileLegend.addView(legendTitle);String[] legendNames={"Oberfläche / Deckschicht","Tragschicht","Verfüllung","Anstehender Boden","Bettung"};for(int li=0;li<legendNames.length;li++){final int pattern=li;LinearLayout legendRow=new LinearLayout(context);legendRow.setGravity(Gravity.CENTER_VERTICAL);View swatch=new ProfileHatchView(context,pattern);legendRow.addView(swatch,new LinearLayout.LayoutParams(dp(context,32),dp(context,20)));TextView item=label(context,legendNames[li],12,false);item.setTextColor(0xffcbd6d5);item.setPadding(dp(context,8),dp(context,3),0,dp(context,3));legendRow.addView(item,new LinearLayout.LayoutParams(0,-2,1));profileLegend.addView(legendRow,new LinearLayout.LayoutParams(-1,dp(context,26)));}
 return profileLegend;
 }
 static LinearLayout createCompact(Context context){
  LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(context,4),dp(context,2),dp(context,4),dp(context,2));
  TextView title=label(context,"Legende",11,true);title.setTextColor(0xffeeeeee);root.addView(title);
  LinearLayout row=new LinearLayout(context);root.addView(row,new LinearLayout.LayoutParams(-1,-2));
  String[] names={"Deckschicht","Tragschicht","Verfüllung","Boden","Bettung"};
  for(int i=0;i<names.length;i++){LinearLayout cell=new LinearLayout(context);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));cell.addView(new ProfileHatchView(context,i),new LinearLayout.LayoutParams(dp(context,30),dp(context,14)));TextView name=label(context,names[i],10,false);name.setTextColor(0xffeeeeee);name.setGravity(Gravity.CENTER);cell.addView(name);}
  return root;
 }
 private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
 private static TextView label(Context context,String text,int size,boolean bold){TextView v=new TextView(context);v.setText(text);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
}
