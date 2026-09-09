package de.scalebridge;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;
import com.health.openscale.bridge.OpenScaleBridge;

/** One user-started foreground session. No hidden boot-time BLE scanning. */
@SuppressLint("MissingPermission")
public final class ScaleService extends Service {
    private String tr(int id){return Tr.t(this,id);}

    public static final String DISCOVER="discover", CONNECT="connect", ENSURE="ensure", STOP="stop", DEVICE="de.scalebridge.DEVICE";
    private static volatile boolean active,ready;
    private static volatile String currentStage="off";
    public static boolean running(){return active;}
    public static boolean connected(){return ready;}
    public static String stage(){return currentStage;}
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothGatt gatt;
    private BluetoothLeScanner scanner;
    private final ArrayDeque<BluetoothGattDescriptor> descriptors=new ArrayDeque<>();
    private Bs444Protocol protocol=new Bs444Protocol();
    private String target;
    private boolean stopped,scanning,discovery;
    private OpenScaleBridge bridge;
    private int generation;
    private long receptionStartedSeconds;
    public static final String INTERACTION="de.scalebridge.INTERACTION";
    private static ScaleService current;
    public static void feedback(int index,String consent){if(current!=null&&current.bridge!=null)current.bridge.feedback(index,consent);}
    private final Set<String> seen=new HashSet<>();
    private final Runnable idleTimeout=()->finish(tr(R.string.ui_142));
    private final Runnable completed=()->finish(tr(R.string.ui_143));
    private void finish(String message){stopped=true;active=false;currentStage="off";main.removeCallbacksAndMessages(null);stopScan();disconnect();status(message);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private final Runnable timeout=()->{if(!stopped){status(tr(R.string.ui_163));disconnect();retry();}};
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int flags,int startId) {
        if(i==null||STOP.equals(i.getAction())){getSharedPreferences("settings",0).edit().putString("bleStatus",tr(R.string.ui_144)).apply();stopSelf();return START_NOT_STICKY;}
        // Activity recreation / returning from Garmin must not tear down a working GATT session.
        if(ENSURE.equals(i.getAction())&&active&&!stopped&&(discovery||Objects.equals(target,getSharedPreferences("settings",0).getString("address",null))))return START_NOT_STICKY;
        if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){stopSelf();return START_NOT_STICKY;}
        stopped=false;generation++;receptionStartedSeconds=System.currentTimeMillis()/1000;Reception.begin();current=this;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("scale",tr(R.string.ui_146),NotificationManager.IMPORTANCE_LOW));
        startForeground(444,notification(tr(R.string.ui_147)));
        active=true;
        BluetoothManager manager=getSystemService(BluetoothManager.class);adapter=manager.getAdapter();
        if(adapter==null||!adapter.isEnabled()){status(tr(R.string.ui_148));stopSelf();return START_NOT_STICKY;}
        discovery=DISCOVER.equals(i.getAction());target=discovery?null:getSharedPreferences("settings",0).getString("address",null);
        main.removeCallbacksAndMessages(null);stopScan();disconnect();seen.clear();
        main.postDelayed(idleTimeout,90000);
        if(!discovery&&target==null){status(tr(R.string.ui_149));stopSelf();return START_NOT_STICKY;}
        if(!discovery&&"CLASSIC_SPP".equals(getSharedPreferences("settings",0).getString("link."+target,""))){connectDriver(getSharedPreferences("settings",0).getString("driver."+target,""),target);return START_NOT_STICKY;}
        if(discovery)for(BluetoothDevice device:adapter.getBondedDevices()){
            OpenScaleBridge.Match match=OpenScaleBridge.identifyPaired(device.getName()==null?"":device.getName(),device.getAddress());
            if(match!=null&&"CLASSIC_SPP".equals(match.linkMode)){String address=device.getAddress();getSharedPreferences("settings",0).edit().putString("driver."+address,match.id).putString("link."+address,match.linkMode).putString("model."+address,match.label).apply();sendBroadcast(new Intent(DEVICE).setPackage(getPackageName()).putExtra("address",address).putExtra("name",match.label).putExtra("likely",true));}
        }
        startScan();return START_NOT_STICKY;
    }
    private Notification notification(String text) {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,ScaleService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"scale").setSmallIcon(de.scalebridge.R.drawable.ic_scale).setContentTitle("Scale2Garmin").setContentText(text).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,tr(R.string.ui_145),stop).build()).build();
    }
    private void status(String s) {
        s=Tr.text(this,s);
        getSharedPreferences("settings",0).edit().putString("bleStatus",s).apply();
        sendBroadcast(new Intent(Sync.EVENT).setPackage(getPackageName()));
        if(!stopped)getSystemService(NotificationManager.class).notify(444,notification(s));
    }
    private void startScan() {
        if(stopped)return;
        try {
            scanner=adapter.getBluetoothLeScanner();if(scanner==null){status(tr(R.string.ui_150));stopSelf();return;}
            List<ScanFilter> filters=target==null?null:List.of(new ScanFilter.Builder().setDeviceAddress(target).build());
            scanner.startScan(filters,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),scan);scanning=true;
            currentStage=discovery?"discovering":"waiting";
            status(discovery?tr(R.string.ui_151):tr(R.string.ui_152));
            if(discovery)main.postDelayed(()->{if(discovery){stopScan();String saved=getSharedPreferences("settings",0).getString("address",null);if(saved!=null){discovery=false;target=saved;startScan();}else{status(tr(R.string.ui_153));stopSelf();}}},30000);
        }catch(SecurityException|IllegalStateException e){status(tr(R.string.ui_154));stopSelf();}
    }
    private void stopScan(){if(scanning&&scanner!=null){try{scanner.stopScan(scan);}catch(Exception ignored){}scanning=false;}}
    private final ScanCallback scan=new ScanCallback(){
        @Override public void onScanResult(int type,ScanResult r){main.post(()->found(r));}
        @Override public void onScanFailed(int error){main.post(()->{scanning=false;status(tr(R.string.ui_155)+error+tr(R.string.ui_156));stopSelf();});}
    };
    private void found(ScanResult r) {
        if(stopped||!scanning)return;
        BluetoothDevice d=r.getDevice();String address=d.getAddress();
        if(discovery) {
            String name=r.getScanRecord()==null?null:r.getScanRecord().getDeviceName();if(name==null)name=d.getName();if(name==null)name=tr(R.string.ui_157);
            String lower=name.toLowerCase(Locale.ROOT);
            OpenScaleBridge.Match match=OpenScaleBridge.identify(r);
            boolean likely=match!=null||lower.startsWith("013197")||lower.startsWith("013198")||lower.startsWith("0202b6")||lower.contains("bs444")||address.startsWith("E4:54:EB");
            if(match!=null){getSharedPreferences("settings",0).edit().putString("driver."+address,match.id).putString("link."+address,match.linkMode).putString("model."+address,match.label).apply();name=match.label+" · "+name;}
            if(seen.add(address))sendBroadcast(new Intent(DEVICE).setPackage(getPackageName()).putExtra("address",address).putExtra("name",name).putExtra("likely",likely));
        } else if(address.equals(target)) {
            String driver=getSharedPreferences("settings",0).getString("driver."+address,"");
            if(!OpenScaleBridge.isMedisana(driver)){
                connectDriver(driver,address);
                return;
            }
            stopScan();protocol=new Bs444Protocol();currentStage="connecting";status(tr(R.string.ui_162));
            gatt=d.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);main.postDelayed(timeout,25000);
        }
    }
    private void connectDriver(String driver,String address){
                final int session=generation;
                stopScan();currentStage="connecting";status(tr(R.string.ui_158));
                bridge=new OpenScaleBridge(Tr.wrap(this),new OpenScaleBridge.Listener(){
                    public void reading(OpenScaleBridge.Reading r){main.post(()->{if(!stopped&&generation==session)saveMeasurement(new Measurement(target,r.unixSeconds-Bs444Protocol.EPOCH,r.user,r.weight,r.fat,r.water,r.musclePercent,r.bone,r.basal));});}
                    public void status(String value){main.post(()->{if(!stopped&&generation==session)ScaleService.this.status(value);});}
                    public void connected(){main.post(()->{if(!stopped&&generation==session){ready=true;currentStage="connected";ScaleService.this.status(tr(R.string.ui_159));}});}
                    public void ended(){main.post(()->{if(!stopped&&generation==session)finish(tr(R.string.ui_160));});}
                    public void interaction(String[] labels,boolean consent){main.post(()->{if(!stopped&&generation==session)sendBroadcast(new Intent(INTERACTION).setPackage(getPackageName()).putExtra("labels",labels).putExtra("consent",consent));});}
                });
                try{bridge.connect(driver,address);}catch(Exception e){finish(tr(R.string.ui_161));}
    }
    private void retry(){if(!stopped&&!discovery)main.postDelayed(this::startScan,15000);}
    private void disconnect(){ready=false;main.removeCallbacks(timeout);descriptors.clear();if(bridge!=null){OpenScaleBridge old=bridge;bridge=null;old.close();}if(gatt!=null){BluetoothGatt old=gatt;gatt=null;try{old.disconnect();old.close();}catch(Exception ignored){}}}
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt g,int status,int state){main.post(()->{
            if(g!=gatt||stopped)return;
            if(status==BluetoothGatt.GATT_SUCCESS&&state==BluetoothProfile.STATE_CONNECTED){if(!g.discoverServices()){disconnect();retry();}}
            else if(state==BluetoothProfile.STATE_DISCONNECTED||status!=BluetoothGatt.GATT_SUCCESS){disconnect();currentStage="waiting";ScaleService.this.status(tr(R.string.ui_164));retry();}
        });}
        @Override public void onServicesDiscovered(BluetoothGatt g,int status){main.post(()->{
            if(g!=gatt||stopped)return;
            BluetoothGattService s=g.getService(Bs444Protocol.SERVICE);
            if(status!=BluetoothGatt.GATT_SUCCESS||s==null){ScaleService.this.status(tr(R.string.ui_165));stopSelf();return;}
            for(UUID id:List.of(Bs444Protocol.BODY,Bs444Protocol.WEIGHT,Bs444Protocol.EXTRA)) {
                BluetoothGattCharacteristic ch=s.getCharacteristic(id);
                if(ch==null&&id.equals(Bs444Protocol.EXTRA))continue;
                if(ch==null||ch.getDescriptor(Bs444Protocol.CCC)==null||!g.setCharacteristicNotification(ch,true)){ScaleService.this.status(tr(R.string.ui_166));stopSelf();return;}
                descriptors.add(ch.getDescriptor(Bs444Protocol.CCC));
            }
            nextDescriptor();
        });}
        @Override public void onDescriptorWrite(BluetoothGatt g,BluetoothGattDescriptor d,int status){main.post(()->{if(g!=gatt||stopped)return;if(status!=BluetoothGatt.GATT_SUCCESS){ScaleService.this.status(tr(R.string.ui_167));disconnect();retry();}else nextDescriptor();});}
        @Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int status){main.post(()->{if(g!=gatt||stopped)return;main.removeCallbacks(timeout);if(status!=BluetoothGatt.GATT_SUCCESS){ScaleService.this.status(tr(R.string.ui_168));disconnect();retry();}else {getSharedPreferences("settings",0).edit().putBoolean("scaleClockConfirmed",true).apply();ready=true;currentStage="connected";ScaleService.this.status(tr(R.string.ui_169));}});}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c,byte[] value){byte[] copy=value.clone();main.post(()->receive(g,c.getUuid(),copy));}
        @Override public void onCharacteristicChanged(BluetoothGatt g,BluetoothGattCharacteristic c){if(Build.VERSION.SDK_INT<33){byte[] copy=c.getValue().clone();main.post(()->receive(g,c.getUuid(),copy));}}
    };
    private void nextDescriptor() {
        if(gatt==null)return;
        BluetoothGattDescriptor d=descriptors.poll();boolean ok;
        if(d!=null) {
            if(Build.VERSION.SDK_INT>=33)ok=gatt.writeDescriptor(d,BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)==BluetoothStatusCodes.SUCCESS;
            else {d.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);ok=gatt.writeDescriptor(d);}
        } else {
            BluetoothGattCharacteristic c=gatt.getService(Bs444Protocol.SERVICE).getCharacteristic(Bs444Protocol.COMMAND);
            if(c==null){status(tr(R.string.ui_170));stopSelf();return;}
            long clockSeconds=System.currentTimeMillis()/1000;byte[] time=Bs444Protocol.timeCommand(clockSeconds);getSharedPreferences("settings",0).edit().putLong("scaleClockSent",clockSeconds).putBoolean("scaleClockConfirmed",false).apply();
            if(Build.VERSION.SDK_INT>=33)ok=gatt.writeCharacteristic(c,time,BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)==BluetoothStatusCodes.SUCCESS;
            else {c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);c.setValue(time);ok=gatt.writeCharacteristic(c);}
        }
        if(!ok){status(tr(R.string.ui_171));disconnect();retry();}
    }
    private void receive(BluetoothGatt source,UUID characteristic,byte[] bytes) {
        if(stopped||source!=gatt)return;
        try {
            Measurement m=protocol.accept(target,characteristic,bytes);
            if(m==null){if(characteristic.equals(Bs444Protocol.WEIGHT)){currentStage="measuring";status(tr(R.string.ui_172));}return;}
            // BS44x has no history download. A timestamp outside this explicitly started
            // reception therefore belongs to a stale device clock, not to an old record.
            saveMeasurement(m,true);
        }catch(IllegalArgumentException e){status(e.getMessage());}
    }
    private static boolean same(double a,double b){return Double.doubleToLongBits(a)==Double.doubleToLongBits(b);}
    private static boolean sameValues(Measurement a,Measurement b){return a.user==b.user&&same(a.weight,b.weight)&&same(a.fat,b.fat)&&same(a.water,b.water)&&same(a.musclePercent,b.musclePercent)&&same(a.bone,b.bone)&&same(a.basal,b.basal);}
    private void saveMeasurement(Measurement m){saveMeasurement(m,false);}
    private void saveMeasurement(Measurement m,boolean liveOnlyDevice){
            try(Store db=new Store(this)) {
                Measurement original=m;long received=System.currentTimeMillis()/1000;
                boolean staleLiveClock=liveOnlyDevice&&(m.unixSeconds<receptionStartedSeconds-120||m.unixSeconds>received+300);
                if(staleLiveClock)m=new Measurement(m.address,received-Bs444Protocol.EPOCH,m.user,m.weight,m.fat,m.water,m.musclePercent,m.bone,m.basal);
                Measurement existing=db.measurement(m.id());
                boolean collision=existing!=null&&!sameValues(existing,m);
                if(collision)m=new Measurement(m.address,received-Bs444Protocol.EPOCH,m.user,m.weight,m.fat,m.water,m.musclePercent,m.bone,m.basal);
                boolean added=db.insert(m);
                android.content.SharedPreferences trace=getSharedPreferences("settings",0);
                String entry="scale="+java.time.Instant.ofEpochSecond(original.unixSeconds)+" stored="+java.time.Instant.ofEpochSecond(m.unixSeconds)+" staleLiveClock="+staleLiveClock+" collision="+collision+" received="+java.time.Instant.now()+" user="+m.user+" inserted="+added+" clockSent="+trace.getLong("scaleClockSent",0)+" clockConfirmed="+trace.getBoolean("scaleClockConfirmed",false);
                String history=entry+"\n"+trace.getString("timeDiagnostics","");String[] entries=history.split("\n");trace.edit().putString("timeDiagnostics",String.join("\n",java.util.Arrays.copyOf(entries,Math.min(10,entries.length)))).apply();
                currentStage="received";
                // A scale may replay old stored readings immediately after connecting.
                // Keep those in history, but do not stop before the new weigh-in arrives.
                if(added&&m.unixSeconds>=receptionStartedSeconds-300&&m.unixSeconds<=System.currentTimeMillis()/1000+300&&m.user==getSharedPreferences("settings",0).getInt("user",-1)){
                    Reception.received(m);main.removeCallbacks(completed);main.postDelayed(completed,2000);
                }
                status(added?tr(R.string.ui_173)+m.user:tr(R.string.ui_174));
                if(added){UploadJob.schedule(this);HealthJob.schedule(this);BackupJob.schedule(this);}
            }
    }
    @Override public void onDestroy(){stopped=true;active=false;currentStage="off";current=null;main.removeCallbacksAndMessages(null);stopScan();disconnect();sendBroadcast(new Intent(Sync.EVENT).setPackage(getPackageName()));stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
