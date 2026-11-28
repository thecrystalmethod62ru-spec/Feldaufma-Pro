package de.feldaufmass.pro;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

final class ProfilePageLayout {
 final LinearLayout profilePage,profileWork,profileInfo,profileCatalog,profileCenter;
 final TextView profilePageTitle,profileDimensions,profileMaterial;
 final PipeLayoutView fullCross;
 final ScrollView profileInfoScroll;
 Button profileEdit,editSectionProfile,baseSettings,applyProfile;
 Button[] fullButtons;
 private Button standardCard,sectionCard;
 private Button catalogShortcut;
 private LinearLayout savedProfiles;
 private final Context context;
 ProfilePageLayout(Context context){
 this.context=context;
 profilePage=new LinearLayout(context);profilePage.setOrientation(1);profilePage.setBackgroundColor(Color.BLACK);profilePage.setVisibility(View.GONE);
  profilePageTitle=sheetText("Regelprofil",16,true);profilePageTitle.setTextColor(Color.WHITE);profilePageTitle.setPadding(dp(6),dp(3),dp(4),dp(3));LinearLayout titleRow=new LinearLayout(context);titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);titleRow.addView(profilePageTitle,new LinearLayout.LayoutParams(0,-2,1));catalogShortcut=new Button(context);catalogShortcut.setText("Katalog");configureAction(catalogShortcut);catalogShortcut.setTextColor(0xff28d5c7);catalogShortcut.setBackground(panelFrame());titleRow.addView(catalogShortcut,new LinearLayout.LayoutParams(dp(72),dp(32)));profilePage.addView(titleRow);catalogShortcut.setOnClickListener(v->{if(baseSettings!=null)baseSettings.performClick();});
  profileWork=new LinearLayout(context);profileWork.setOrientation(0);profilePage.addView(profileWork,new LinearLayout.LayoutParams(-1,0,1));profileWork.setPadding(dp(6),dp(4),dp(6),dp(4));
  profileCatalog=new LinearLayout(context);profileCatalog.setOrientation(LinearLayout.VERTICAL);profileCatalog.setPadding(dp(6),dp(6),dp(6),dp(6));profileCatalog.setBackground(panelFrame());profileCatalog.setVisibility(View.GONE);profileWork.addView(profileCatalog,new LinearLayout.LayoutParams(dp(140),-1));
  profileCenter=new LinearLayout(context);profileCenter.setOrientation(LinearLayout.VERTICAL);profileWork.addView(profileCenter,new LinearLayout.LayoutParams(-1,0,3));
  fullCross=new PipeLayoutView(context);profileCenter.addView(fullCross,new LinearLayout.LayoutParams(-1,0,1));profileWork.setOrientation(LinearLayout.VERTICAL);
  profileInfo=new LinearLayout(context);profileInfo.setOrientation(1);profileInfo.setPadding(dp(4),dp(3),dp(4),dp(3));android.graphics.drawable.GradientDrawable parameterFrame=editorShape(0xff041012,2);parameterFrame.setStroke(dp(1),0xff008b93);profileInfo.setBackground(parameterFrame);
  profileInfoScroll=new ScrollView(context);profileInfoScroll.addView(profileInfo);profileInfoScroll.setFillViewport(true);profileWork.addView(profileInfoScroll,new LinearLayout.LayoutParams(-1,0,2));
  profileDimensions=sheetText("",11,false);profileDimensions.setTextColor(0xff28d5c7);TextView parameterTitle=sheetText("Parameter",12,true);parameterTitle.setTextColor(0xff28d5c7);profileInfo.addView(parameterTitle);profileInfo.addView(profileDimensions);profileDimensions.setPadding(dp(4),dp(3),dp(4),dp(3));android.graphics.drawable.GradientDrawable dimensionFrame=editorShape(Color.BLACK,4);dimensionFrame.setStroke(dp(1),0xff008b93);profileDimensions.setBackground(dimensionFrame);profileDimensions.setVisibility(View.GONE);
  profileMaterial=sheetText("",11,false);profileMaterial.setTextColor(0xffcbd6d5);profileInfo.addView(profileMaterial);profileMaterial.setPadding(dp(4),dp(3),dp(4),dp(3));
  LinearLayout parameterRows=new LinearLayout(context);parameterRows.setOrientation(LinearLayout.VERTICAL);
  profileInfo.addView(parameterRows,1,new LinearLayout.LayoutParams(-1,-2));
  TextView widthValue=parameterRow(parameterRows,"Grabenbreite"),depthValue=parameterRow(parameterRows,"Grabentiefe");
  TextView pipeValue=parameterRow(parameterRows,"Rohrdimension"),bedValue=parameterRow(parameterRows,"Bettung"),surfaceValue=parameterRow(parameterRows,"Oberbau");
  Runnable refreshValues=()->{
   widthValue.setText(String.format(java.util.Locale.GERMANY,"%.2f m",fullCross.profileWidth()));
   depthValue.setText(String.format(java.util.Locale.GERMANY,"%.2f m",fullCross.profileDepth()));
   java.util.List<PipeLayout.Pipe> pipes=fullCross.profilePipes();
   java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();
   double min=Double.POSITIVE_INFINITY,max=0;
   for(PipeLayout.Pipe pipe:pipes){names.add(pipe.name);min=Math.min(min,pipe.bedding);max=Math.max(max,pipe.bedding);}
   pipeValue.setText(pipes.isEmpty()?"—":android.text.TextUtils.join(", ",names));
   bedValue.setText(pipes.isEmpty()?"—":Math.abs(max-min)<1e-8?String.format(java.util.Locale.GERMANY,"%.2f m",min):String.format(java.util.Locale.GERMANY,"%.2f–%.2f m",min,max));
   String material=profileMaterial.getText().toString();
   surfaceValue.setText(material.startsWith("Oberfläche: ")?material.substring(12):material);
  };
  android.text.TextWatcher refreshWatcher=new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int start,int count,int after){}
   public void onTextChanged(CharSequence s,int start,int before,int count){refreshValues.run();}
   public void afterTextChanged(android.text.Editable e){}
  };
  profileDimensions.addTextChangedListener(refreshWatcher);
  profileMaterial.addTextChangedListener(refreshWatcher);
  profileMaterial.setVisibility(View.GONE);
  parameterRows.setOnClickListener(v->{if(profileEdit!=null)profileEdit.performClick();});
  parameterRows.setContentDescription("Grabenbreite und Grabentiefe bearbeiten");
  final LinearLayout portraitPanels=new LinearLayout(context);
  portraitPanels.setOrientation(LinearLayout.HORIZONTAL);
  final ScrollView catalogScroll=new ScrollView(context);
  catalogScroll.setFillViewport(true);
  final boolean[] lastWide={false}, initialized={false};
  profileWork.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{
   int width=r-l-profileWork.getPaddingLeft()-profileWork.getPaddingRight();
   int height=b-t-profileWork.getPaddingTop()-profileWork.getPaddingBottom();
   if(width<=0||height<=0)return;
   boolean wide=getResourcesLandscape();
   int panelHeight=Math.min(dp(190),Math.max(dp(150),Math.round(height*.28f)));
   if(initialized[0]&&lastWide[0]==wide){
    if(!wide&&portraitPanels.getLayoutParams().height!=panelHeight)portraitPanels.setLayoutParams(new LinearLayout.LayoutParams(-1,panelHeight));
    return;
   }
   initialized[0]=true;lastWide[0]=wide;
   detach(profileCenter);detach(profileInfoScroll);detach(profileCatalog);detach(catalogScroll);detach(portraitPanels);
   profileWork.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
   profileCatalog.setVisibility(View.VISIBLE);
   catalogScroll.addView(profileCatalog,new ScrollView.LayoutParams(-1,-2));
   if(wide){
    profileWork.addView(catalogScroll,new LinearLayout.LayoutParams(Math.round(width*.19f),-1));
    profileWork.addView(profileCenter,new LinearLayout.LayoutParams(0,-1,1));
    profileWork.addView(profileInfoScroll,new LinearLayout.LayoutParams(Math.round(width*.30f),-1));
   }else{
    profileWork.addView(profileCenter,new LinearLayout.LayoutParams(-1,0,1));
    profileWork.addView(portraitPanels,new LinearLayout.LayoutParams(-1,panelHeight));
    LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1);left.setMargins(0,0,dp(3),0);
    portraitPanels.addView(profileInfoScroll,left);
    portraitPanels.addView(catalogScroll,new LinearLayout.LayoutParams(0,-1,1));
   }
  });
 }
 void buildActions(java.util.function.BiConsumer<Button,Boolean> style, java.util.function.BiConsumer<Button,String> settingsIcon){
  profileCenter.addView(ProfileLegend.createCompact(context),new LinearLayout.LayoutParams(-1,-2));
  profileEdit=new Button(context);profileEdit.setText("Maße bearbeiten");style.accept(profileEdit,false);configureAction(profileEdit);
  profileDimensions.setOnClickListener(v->profileEdit.performClick());profileDimensions.setContentDescription("Grabenbreite und Grabentiefe bearbeiten");
  editSectionProfile=new Button(context);style.accept(editSectionProfile,false);settingsIcon.accept(editSectionProfile,"Abschnitt bearbeiten");
  baseSettings=new Button(context);style.accept(baseSettings,false);settingsIcon.accept(baseSettings,"Standard-Profil verwalten");
  applyProfile=new Button(context);style.accept(applyProfile,true);applyProfile.setText("Übernehmen");configureAction(applyProfile);
  LinearLayout fullTabs=new LinearLayout(context);fullTabs.setPadding(dp(4),0,dp(4),dp(4));profileCenter.addView(fullTabs,0,new LinearLayout.LayoutParams(-1,dp(44)));profilePage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{boolean wide=getResourcesLandscape();int next=wide?dp(44):0;fullTabs.setVisibility(wide?View.VISIBLE:View.GONE);if(fullTabs.getLayoutParams().height!=next){fullTabs.getLayoutParams().height=next;fullTabs.requestLayout();}});
  String[] names={"Aufbau","Leitungen","Material","Berechnung"};fullButtons=new Button[names.length];
  for(int i=0;i<names.length;i++){Button b=new Button(context);fullButtons[i]=b;style.accept(b,i==0);b.setText(names[i]);b.setAllCaps(false);b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(2),0,dp(2),0);fullTabs.addView(b,lp);}
  LinearLayout profileActions=new LinearLayout(context);profilePage.addView(profileActions,new LinearLayout.LayoutParams(-1,dp(40)));
  for(Button b:new Button[]{editSectionProfile,baseSettings}){android.graphics.drawable.Drawable icon=b.getCompoundDrawables()[1];b.setCompoundDrawables(icon,null,null,null);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),0,dp(6),0);}
  profileInfo.addView(editSectionProfile,new LinearLayout.LayoutParams(-1,dp(26)));
  TextView catalogTitle=sheetText("Profilkatalog",12,true);catalogTitle.setTextColor(0xff28d5c7);profileCatalog.addView(catalogTitle);
  Button standard=new Button(context);standardCard=standard;style.accept(standard,true);standard.setText("Standardprofil");configureAction(standard);standard.setOnClickListener(v->baseSettings.performClick());profileCatalog.addView(standard,new LinearLayout.LayoutParams(-1,dp(30)));
  Button section=new Button(context);sectionCard=section;style.accept(section,false);section.setText("Abschnitt");configureAction(section);section.setOnClickListener(v->editSectionProfile.performClick());profileCatalog.addView(section,new LinearLayout.LayoutParams(-1,dp(30)));
  savedProfiles=new LinearLayout(context);savedProfiles.setOrientation(LinearLayout.VERTICAL);profileCatalog.addView(savedProfiles);
  for(Button b:new Button[]{baseSettings,profileEdit,applyProfile})profileActions.addView(b,new LinearLayout.LayoutParams(0,-1,1));
  Button more=new Button(context);more.setText("Weitere Aktionen ▾");configureAction(more);style.accept(more,false);configureAction(more);
  profileCatalog.addView(more,new LinearLayout.LayoutParams(-1,dp(28)));
  more.setOnClickListener(v->{android.widget.PopupMenu menu=new android.widget.PopupMenu(context,more);
   Button[] actions={profileEdit,baseSettings,applyProfile,fullButtons[1],fullButtons[2],fullButtons[3]};
   for(int i=0;i<actions.length;i++)menu.getMenu().add(0,i,i,actions[i].getText());
   menu.setOnMenuItemClickListener(item->{actions[item.getItemId()].performClick();return true;});menu.show();});
  profilePage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{
   boolean wide=getResourcesLandscape();profileActions.setVisibility(wide?View.VISIBLE:View.GONE);more.setVisibility(wide?View.GONE:View.VISIBLE);
  });
 }
 void bindSavedProfiles(java.util.List<ProfileTemplateStore.Template> templates,Runnable add,java.util.function.Consumer<ProfileTemplateStore.Template> choose){
  if(savedProfiles==null)return;
  savedProfiles.removeAllViews();
  for(ProfileTemplateStore.Template template:templates){
   Button row=new Button(context);configureAction(row);row.setTextSize(10);row.setTextColor(Color.WHITE);row.setBackground(panelFrame());
   row.setText(template.label());row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);
   savedProfiles.addView(row,new LinearLayout.LayoutParams(-1,dp(28)));row.setOnClickListener(v->choose.accept(template));
  }
  Button addButton=new Button(context);configureAction(addButton);addButton.setText("+ Profil speichern");addButton.setTextColor(0xff28d5c7);addButton.setBackground(panelFrame());
  savedProfiles.addView(addButton,new LinearLayout.LayoutParams(-1,dp(28)));addButton.setOnClickListener(v->add.run());
  catalogShortcut.setOnClickListener(v->{
   android.widget.PopupMenu menu=new android.widget.PopupMenu(context,catalogShortcut);
   menu.getMenu().add(0,0,0,"Standardprofil bearbeiten");
   menu.getMenu().add(0,1,1,"Aktuelles Profil speichern");
   for(int i=0;i<templates.size();i++)menu.getMenu().add(0,i+2,i+2,templates.get(i).label());
   menu.setOnMenuItemClickListener(item->{int id=item.getItemId();if(id==0)baseSettings.performClick();else if(id==1)add.run();else choose.accept(templates.get(id-2));return true;});
   menu.show();
  });
 }
 void updateCatalog(String standard,String section,boolean sectionSelected){
  if(standardCard==null||sectionCard==null)return;
  standardCard.setText("Standard · "+standard);
  sectionCard.setText(section==null?"Abschnitt auswählen":section.replace("\n"," · "));
  standardCard.setSelected(!sectionSelected);sectionCard.setSelected(sectionSelected);
  standardCard.setBackground(panelFrame());sectionCard.setBackground(panelFrame());
  standardCard.setTextColor(sectionSelected?Color.WHITE:0xff28d5c7);
  sectionCard.setTextColor(sectionSelected?0xff28d5c7:Color.WHITE);
 }
 private static String valueFor(String[] lines,String label){
  for(String line:lines){int colon=line.indexOf(':');if(colon>=0&&line.substring(0,colon).trim().equalsIgnoreCase(label))return line.substring(colon+1).trim();}
  return "—";
 }
 private TextView parameterRow(LinearLayout parent,String name){
  LinearLayout row=new LinearLayout(context);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(3),0,dp(3),0);
  TextView key=sheetText(name,11,false);key.setTextColor(0xffd0d5d5);
  TextView value=sheetText("—",11,true);value.setTextColor(Color.WHITE);value.setGravity(android.view.Gravity.END);
  row.addView(key,new LinearLayout.LayoutParams(0,-2,1));value.setMaxLines(2);value.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
  parent.addView(row,new LinearLayout.LayoutParams(-1,dp(22)));
  View rule=new View(context);rule.setBackgroundColor(0xff263b3d);parent.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
  return value;
 }
 private static void detach(View view){if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);}
 private boolean getResourcesLandscape(){return context.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;}
 private GradientDrawable panelFrame(){GradientDrawable d=editorShape(0xff041012,2);d.setStroke(dp(1),0xff008b93);return d;}
 private void configureAction(Button b){b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);b.setTextSize(12);}
 private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
 private TextView sheetText(String text,int size,boolean bold){TextView v=new TextView(context);v.setText(text);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
 private GradientDrawable editorShape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
}
