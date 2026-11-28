package de.feldaufmass.pro;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Portrait-only workspace proportions. Existing actions remain owned by MainActivity. */
final class PortraitWorkspaceLayout {
 private final Context context;
 PortraitWorkspaceLayout(Context context){this.context=context;}
 private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
 int panelHeight(int available){return Math.min(dp(190),Math.max(dp(105),Math.round(available*.22f)));}
 LinearLayout.LayoutParams leftPanelParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(0,0,dp(4),0);return p;}
 private static void detach(View v){if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);}
 void configureHeader(boolean landscape,LinearLayout block,LinearLayout identity,LinearLayout shortcuts,Button back,LinearLayout heading,Button project,Button rotate,FrameLayout canvas,FrameLayout.LayoutParams oldRotate){
  detach(rotate);
  if(landscape){
   shortcuts.setVisibility(View.VISIBLE);
   project.setOnLongClickListener(null);
   canvas.addView(rotate,oldRotate);
   back.setLayoutParams(new LinearLayout.LayoutParams(dp(44),dp(44)));
   project.setLayoutParams(new LinearLayout.LayoutParams(0,dp(40),1));
   project.setTextSize(12);
   for(int i=0;i<shortcuts.getChildCount();i++){View v=shortcuts.getChildAt(i);if(v instanceof Button){v.setLayoutParams(new LinearLayout.LayoutParams(dp(40),dp(40)));}}
   return;
  }
  block.setPadding(dp(4),dp(2),dp(4),dp(2));
  identity.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(40)));
  back.setLayoutParams(new LinearLayout.LayoutParams(dp(30),dp(36)));
  project.setLayoutParams(new LinearLayout.LayoutParams(0,dp(36),1));
  project.setTextSize(10);
  identity.addView(rotate,new LinearLayout.LayoutParams(dp(32),dp(36)));
  shortcuts.setGravity(Gravity.CENTER_VERTICAL);
  shortcuts.setVisibility(View.GONE);
  project.setOnLongClickListener(v->{shortcuts.setVisibility(shortcuts.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);return true;});
  project.setContentDescription("Aktuelles Projekt; lange drücken für Schnellaktionen");
  for(int i=0;i<shortcuts.getChildCount();i++){View v=shortcuts.getChildAt(i);if(v instanceof Button){Button b=(Button)v;b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(2),0,dp(2),0);b.setLayoutParams(new LinearLayout.LayoutParams(0,dp(30),1));}}
 }
 void configureToolbar(LinearLayout toolbar,Button[] modes,Button build,Button docs){
  toolbar.setPadding(dp(2),dp(1),dp(2),dp(2));
  build.setText("Leitung /\nFormstück");docs.setText("Schachtschein\nUnterlagen");
  build.setContentDescription("Leitungen und Formstücke");docs.setContentDescription("Schachtscheine und Unterlagen");
  for(int i=0;i<toolbar.getChildCount();i++){
   View v=toolbar.getChildAt(i);if(!(v instanceof Button))continue;Button b=(Button)v;
   b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
   b.setPadding(dp(1),dp(2),dp(1),dp(2));b.setIncludeFontPadding(false);b.setTextSize(9);b.setMaxLines(2);b.setCompoundDrawablePadding(dp(2));
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(49),b==build||b==docs?1.18f:1f);p.setMargins(dp(1),0,dp(1),0);b.setLayoutParams(p);
  }
 }
}
