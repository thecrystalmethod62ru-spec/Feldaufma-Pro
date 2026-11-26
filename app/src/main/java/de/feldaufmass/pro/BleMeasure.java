package de.feldaufmass.pro;

import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.*;
import android.widget.*;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.nio.charset.StandardCharsets;

/** Explicit text-with-unit BLE measurement prototype. Unknown binary protocols are not decoded. */
final class BleMeasure {
 final Activity activity;
 final DoubleConsumer accept;
 final Handler handler=new Handler(Looper.getMainLooper());
 BluetoothLeScanner scanner;
 BluetoothGatt gatt;
 BluetoothGattCharacteristic channel;
 AlertDialog dialog;
 boolean closed,scanning;
 TextView status;
 Button use;
 double candidate=Double.NaN;
 final LinkedHashMap<String,BluetoothDevice> devices=new LinkedHashMap<>();
 final ArrayAdapter<String> names;
 final ArrayList<BluetoothDevice> list=new ArrayList<>();
 BleMeasure(Activity activity,DoubleConsumer accept){this.activity=activity;this.accept=accept;names=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1);}
 static double parse(byte[] value){
  if(value==null||value.length==0||value.length>80)return Double.NaN;
  String s=new String(value,StandardCharsets.UTF_8).trim();
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)^(\\d{1,7}(?:[.,]\\d{1,7})?)\\s*(mm|cm|m)$").matcher(s);
  if(!m.matches())return Double.NaN;
  double n=Double.parseDouble(m.group(1).replace(',','.'));String unit=m.group(2).toLowerCase(Locale.ROOT);
  if(unit.equals("mm"))n/=1000;else if(unit.equals("cm"))n/=100;
  return Double.isFinite(n)&&n>0?n:Double.NaN;
 }
 void show(){
  LinearLayout form=new LinearLayout(activity);form.setOrientation(1);int pad=(int)(16*activity.getResources().getDisplayMetrics().density);form.setPadding(pad,pad,pad,pad);
  status=new TextView(activity);status.setText("BLE-Gerät auswählen. Nur eindeutiger Text mit Einheit m, cm oder mm wird erkannt. Herstellerprotokolle nicht automatisch unterstützt.");form.addView(status);
  ListView rows=new ListView(activity);rows.setAdapter(names);form.addView(rows,new LinearLayout.LayoutParams(-1,pad*15));rows.setOnItemClickListener((parent,v,index,id)->{if(index<list.size())connect(list.get(index));});
  use=new Button(activity);use.setText("Messwert übernehmen");use.setEnabled(false);form.addView(use);
  use.setOnClickListener(v->{if(Double.isFinite(candidate)){double n=candidate;dialog.dismiss();accept.accept(n);}});
  dialog=new AlertDialog.Builder(activity).setTitle("Bluetooth-Messgerät (Test)").setView(form).setNegativeButton("Schließen",null).create();
  dialog.setOnDismissListener(d->close());dialog.show();
  try{
   BluetoothManager manager=(BluetoothManager)activity.getSystemService(Context.BLUETOOTH_SERVICE);BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
   if(adapter==null||!adapter.isEnabled()){status.setText("Bluetooth nicht verfügbar oder ausgeschaltet.");return;}
   scanner=adapter.getBluetoothLeScanner();if(scanner==null){status.setText("BLE-Suche nicht verfügbar.");return;}
   scanning=true;scanner.startScan(scan);handler.postDelayed(()->{stopScan();if(!closed&&gatt==null)status.setText("Suche beendet. Gerät auswählen oder erneut öffnen.");},12000);
  }catch(Exception e){stopScan();status.setText("Bluetooth-Suche fehlgeschlagen: "+e.getMessage());}
 }
 final ScanCallback scan=new ScanCallback(){
  @Override public void onScanResult(int type,ScanResult result){handler.post(()->{if(closed||!scanning)return;try{
   BluetoothDevice device=result.getDevice();String address=device.getAddress();if(devices.containsKey(address)||devices.size()>=60)return;
   devices.put(address,device);list.add(device);String name=device.getName();names.add((name==null?"Unbenannt":name)+"\n"+address);
  }catch(Exception ignored){}});}
  @Override public void onScanFailed(int code){handler.post(()->{if(!closed){stopScan();status.setText("BLE-Suche fehlgeschlagen: "+code);}});}
 };
 void stopScan(){if(scanning){scanning=false;try{scanner.stopScan(scan);}catch(Exception ignored){}}}
 void disconnect(){BluetoothGatt previous=gatt;gatt=null;channel=null;candidate=Double.NaN;if(use!=null)use.setEnabled(false);if(previous!=null){try{previous.disconnect();}catch(Exception ignored){}try{previous.close();}catch(Exception ignored){}}}
 void connect(BluetoothDevice device){
  stopScan();disconnect();status.setText("Verbindung wird aufgebaut …");
  try{
   gatt=device.connectGatt(activity,false,callback,BluetoothDevice.TRANSPORT_LE);BluetoothGatt attempt=gatt;
   handler.postDelayed(()->{if(!closed&&gatt==attempt&&channel==null){disconnect();status.setText("Zeitlimit erreicht. Gerät erneut auswählen.");}},25000);
  }catch(Exception e){status.setText("Verbindung fehlgeschlagen: "+e.getMessage());}
 }
 final BluetoothGattCallback callback=new BluetoothGattCallback(){
  @Override public void onConnectionStateChange(BluetoothGatt source,int code,int state){handler.post(()->{
   if(closed||source!=gatt)return;
   if(code!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){disconnect();status.setText("Verbindung beendet ("+code+").");return;}
   if(state==BluetoothProfile.STATE_CONNECTED){try{if(!source.discoverServices())status.setText("Dienstsuche fehlgeschlagen.");}catch(Exception e){status.setText("Dienstsuche fehlgeschlagen.");}}
  });}
  @Override public void onServicesDiscovered(BluetoothGatt source,int code){handler.post(()->{
   if(closed||source!=gatt)return;if(code!=BluetoothGatt.GATT_SUCCESS){status.setText("Dienstsuche fehlgeschlagen.");return;}
   ArrayList<BluetoothGattCharacteristic> available=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
   for(BluetoothGattService service:source.getServices())for(BluetoothGattCharacteristic c:service.getCharacteristics())if((c.getProperties()&(BluetoothGattCharacteristic.PROPERTY_NOTIFY|BluetoothGattCharacteristic.PROPERTY_INDICATE))!=0){available.add(c);labels.add(service.getUuid()+"\n"+c.getUuid());}
   if(available.isEmpty()){status.setText("Keine Benachrichtigungs-Kanäle gefunden.");return;}
   new AlertDialog.Builder(activity).setTitle("Messkanal auswählen (GATT)").setItems(labels.toArray(new String[0]),(d,i)->subscribe(source,available.get(i))).setNegativeButton("Abbrechen",null).show();
  });}
  @Override public void onDescriptorWrite(BluetoothGatt source,BluetoothGattDescriptor descriptor,int code){handler.post(()->{if(!closed&&source==gatt)status.setText(code==BluetoothGatt.GATT_SUCCESS?"Messgerät auslösen. Auf Messwert warten …":"Benachrichtigung fehlgeschlagen: "+code);});}
  @Override public void onCharacteristicChanged(BluetoothGatt source,BluetoothGattCharacteristic c,byte[] value){received(source,c,value.clone());}
  @Override public void onCharacteristicChanged(BluetoothGatt source,BluetoothGattCharacteristic c){if(Build.VERSION.SDK_INT<33){byte[] v=c.getValue();if(v!=null)received(source,c,v.clone());}}
 };
 void subscribe(BluetoothGatt source,BluetoothGattCharacteristic c){
  if(closed||source!=gatt)return;
  try{
   BluetoothGattDescriptor descriptor=c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
   if(descriptor==null||!source.setCharacteristicNotification(c,true)){status.setText("Kanal nicht abonnierbar.");return;}
   byte[] value=(c.getProperties()&BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=0?BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE:BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
   channel=c;boolean started;
   if(Build.VERSION.SDK_INT>=33)started=source.writeDescriptor(descriptor,value)==BluetoothStatusCodes.SUCCESS;
   else{descriptor.setValue(value);started=source.writeDescriptor(descriptor);}
   if(!started){channel=null;status.setText("Kanal konnte nicht aktiviert werden.");}
  }catch(Exception e){channel=null;status.setText("Kanal konnte nicht aktiviert werden.");}
 }
 void received(BluetoothGatt source,BluetoothGattCharacteristic c,byte[] bytes){handler.post(()->{
  if(closed||source!=gatt||channel!=c)return;candidate=parse(bytes);use.setEnabled(Double.isFinite(candidate));
  if(Double.isFinite(candidate))status.setText(String.format(Locale.GERMANY,"Empfangen: %.3f m\nAm Gerät prüfen, dann übernehmen.",candidate));
  else{StringBuilder hex=new StringBuilder();for(int i=0;i<Math.min(32,bytes.length);i++)hex.append(String.format(Locale.ROOT,"%02X ",bytes[i]&255));status.setText("Unbekanntes Datenformat – keine Übernahme.\nHEX: "+hex);}
 });}
 void close(){if(closed)return;closed=true;stopScan();handler.removeCallbacksAndMessages(null);disconnect();if(dialog!=null&&dialog.isShowing())dialog.dismiss();}
}
