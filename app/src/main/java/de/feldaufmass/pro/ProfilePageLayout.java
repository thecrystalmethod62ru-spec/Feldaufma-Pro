package de.feldaufmass.pro;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ProfilePageLayout {
 final LinearLayout profilePage,profileWork,profileInfo;
 final TextView profilePageTitle,profileDimensions,profileMaterial;
 final PipeLayoutView fullCross;
 final ScrollView profileInfoScroll;
 private final Context context;
 ProfilePageLayout(Context context){
 this.context=context;
 profilePage=new LinearLayout(context);profilePage.setOrientation(1);profilePage.setBackgroundColor(Color.BLACK);profilePage.setVisibility(View.GONE);
  profilePageTitle=sheetText("Regelprofil",16,true);profilePageTitle.setTextColor(Color.WHITE);profilePageTitle.setPadding(dp(8),dp(6),dp(8),dp(6));profilePage.addView(profilePageTitle);
  profileWork=new LinearLayout(context);profileWork.setOrientation(0);profilePage.addView(profileWork,new LinearLayout.LayoutParams(-1,0,1));profileWork.setPadding(dp(6),dp(4),dp(6),dp(4));
  fullCross=new PipeLayoutView(context);profileWork.addView(fullCross,new LinearLayout.LayoutParams(-1,0,3));profileWork.setOrientation(LinearLayout.VERTICAL);
  profileInfo=new LinearLayout(context);profileInfo.setOrientation(1);profileInfo.setPadding(dp(8),dp(8),dp(8),dp(8));android.graphics.drawable.GradientDrawable parameterFrame=editorShape(0xff041012,8);parameterFrame.setStroke(dp(1),0xff008b93);profileInfo.setBackground(parameterFrame);
  profileInfoScroll=new ScrollView(context);profileInfoScroll.addView(profileInfo);profileInfoScroll.setFillViewport(true);profileWork.addView(profileInfoScroll,new LinearLayout.LayoutParams(-1,0,2));
  profileDimensions=sheetText("",14,true);profileDimensions.setTextColor(0xff28d5c7);TextView parameterTitle=sheetText("PARAMETER",12,true);parameterTitle.setTextColor(0xff28d5c7);profileInfo.addView(parameterTitle);profileInfo.addView(profileDimensions);profileDimensions.setPadding(dp(8),dp(10),dp(8),dp(10));android.graphics.drawable.GradientDrawable dimensionFrame=editorShape(Color.BLACK,4);dimensionFrame.setStroke(dp(1),0xff008b93);profileDimensions.setBackground(dimensionFrame);
  profileMaterial=sheetText("",12,false);profileMaterial.setTextColor(0xffcbd6d5);profileInfo.addView(profileMaterial);profileMaterial.setPadding(dp(8),dp(10),dp(8),dp(10));
  ProfileResponsiveLayout.bind(profileWork,fullCross,profileInfoScroll);
 }
 private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
 private TextView sheetText(String text,int size,boolean bold){TextView v=new TextView(context);v.setText(text);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 private GradientDrawable editorShape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
}
