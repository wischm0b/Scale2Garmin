package de.scalebridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.time.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={31,35})
public class AppTest {
    private Context context(){return RuntimeEnvironment.getApplication();}
    @Before public void clean(){Context c=context();c.deleteDatabase("measurements.db");c.getSharedPreferences("settings",0).edit().clear().putString("language","de").commit();c.getSystemService(android.app.job.JobScheduler.class).cancelAll();Reception.begin();c.getSharedPreferences("vault",0).edit().clear().commit();}
    @After public void clearService(){ReflectionHelpers.setStaticField(ScaleService.class,"active",false);ReflectionHelpers.setStaticField(ScaleService.class,"ready",false);ReflectionHelpers.setStaticField(ScaleService.class,"currentStage","off");}
    private TextView find(View v,String label){if(v instanceof TextView t&&label.equals(t.getText().toString()))return t;if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView f=find(group.getChildAt(i),label);if(f!=null)return f;}return null;}
    private View description(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View f=description(g.getChildAt(i),label);if(f!=null)return f;}return null;}
    private View decor(Activity a){return a.getWindow().getDecorView();}
    private Measurement measurement(int days,int user,double weight){long now=Instant.now().getEpochSecond()-3600;return new Measurement("AA:BB:CC:DD:EE:FF",now-days*86400-Bs444Protocol.EPOCH,user,weight,24+days*.1,52.1,38.2,3.1,1750);}
    private void seed(){context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();try(Store db=new Store(context())){for(int i=20;i>=0;i--){Measurement m=measurement(i,1,82+i*.11+Math.sin(i)*.25);db.insert(m);db.state(m.id(),"sent","");}}}
    private void layout(Activity a){View v=decor(a);v.measure(View.MeasureSpec.makeMeasureSpec(1179,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2553,View.MeasureSpec.EXACTLY));v.layout(0,0,1179,2553);}
    @Test public void dashboardAndSettingsReplaceTabs(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            Activity a=c.get();assertNotNull(find(decor(a),"Dein Verlauf"));assertNotNull(find(decor(a),"Trends & Prognosen"));assertNotNull(decor(a).findViewWithTag("trendInsights"));assertNull(find(decor(a),"Empfang starten"));assertNull(find(decor(a),"FIT-Datei speichern"));assertNull(find(decor(a),"Messungen"));
            description(decor(a),"Einstellungen öffnen").performClick();assertNotNull(find(decor(a),"Deine Waage"));assertNotNull(description(decor(a),"Speicherplatz"));assertNotNull(description(decor(a),"Farbschema"));assertNotNull(find(decor(a),"CSV importieren"));assertNotNull(find(decor(a),"Automatische Sicherung"));assertNotNull(find(decor(a),"Für jeden Tag eine eigene Sicherungsdatei"));assertNull(find(decor(a),"Zeitstempel prüfen"));
            description(decor(a),"Zurück zum Verlauf").performClick();assertNotNull(find(decor(a),"Dein Verlauf"));
        }
    }
    @Test public void csvPickerAcceptsEveryProviderMimeType(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            Activity a=c.get();description(decor(a),"Einstellungen öffnen").performClick();find(decor(a),"CSV importieren").performClick();
            Intent pick=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(pick);assertEquals(Intent.ACTION_OPEN_DOCUMENT,pick.getAction());assertEquals("*/*",pick.getType());assertTrue(pick.hasCategory(Intent.CATEGORY_OPENABLE));assertNull(pick.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));assertTrue((pick.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);
        }
    }
    @Test public void backupFolderUsesPersistableAndroidFolderPicker(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            Activity a=c.get();description(decor(a),"Einstellungen öffnen").performClick();find(decor(a),"Zielordner auswählen").performClick();
            Intent pick=Shadows.shadowOf(a).getNextStartedActivity();assertNotNull(pick);assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE,pick.getAction());
            assertTrue((pick.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);assertTrue((pick.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0);assertTrue((pick.getFlags()&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)!=0);assertTrue((pick.getFlags()&Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)!=0);
        }
    }
    @Test public void databaseDeduplicatesAndSeparatesUsers(){try(Store db=new Store(context())){Measurement m=ProtocolTest.sample();assertTrue(db.insert(m));assertFalse(db.insert(m));assertEquals(1,db.rows(1,true).size());assertEquals(0,db.rows(2,true).size());db.state(m.id(),"sending","");db.recoverInterrupted();assertEquals("review",db.rows(1,false).get(0).state());assertTrue(db.rows(1,true).isEmpty());db.state(m.id(),"sent","");assertFalse(db.insert(m));}}
    @Test public void timelineIncludesMoreThan200AndOnlySelectedUser(){try(Store db=new Store(context())){for(int i=0;i<260;i++)db.insert(measurement(i,1,80));db.insert(measurement(0,2,60));assertEquals(260,db.timeline(1,0,Instant.now().getEpochSecond()).size());assertEquals(1,db.timeline(2,0,Instant.now().getEpochSecond()).size());long from=Instant.now().getEpochSecond()-7*86400;assertEquals(7,db.timeline(1,from,Instant.now().getEpochSecond()).size());}}
    @Test public void startupRecoversInterruptedUploadAndDoesNotSend(){
        context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();Measurement m=measurement(0,1,96);try(Store db=new Store(context())){db.insert(m);db.state(m.id(),"sending","");}
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            assertNotNull(find(decor(c.get()),"96,0 kg"));assertFalse(context().getSharedPreferences("settings",0).getBoolean("autoUpload",false));try(Store db=new Store(context())){assertEquals("review",db.rows(1,false).get(0).state());}
        }
    }
    @Test public void openingConfiguredAppWaitsForExplicitReception(){
        context().getSharedPreferences("settings",0).edit().putString("address","AA:BB:CC:DD:EE:FF").commit();Shadows.shadowOf((Application)context()).grantPermissions(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT);
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup()){
            assertNull(Shadows.shadowOf((Application)context()).getNextStartedService());
            c.pause().stop().start().resume();assertNull(Shadows.shadowOf((Application)context()).getNextStartedService());
            description(decor(c.get()),"Verbindung erneut versuchen").performClick();
            Intent intent=Shadows.shadowOf((Application)context()).getNextStartedService();assertNotNull(intent);assertEquals(ScaleService.CONNECT,intent.getAction());
        }
    }
    @Test public void ensureDoesNotRestartExistingConnection(){
        context().getSharedPreferences("settings",0).edit().putString("address","AA:BB:CC:DD:EE:FF").commit();
        var controller=Robolectric.buildService(ScaleService.class).create();
        try{
            ScaleService s=controller.get();ReflectionHelpers.setStaticField(ScaleService.class,"active",true);ReflectionHelpers.setStaticField(ScaleService.class,"ready",true);ReflectionHelpers.setStaticField(ScaleService.class,"currentStage","connected");ReflectionHelpers.setField(s,"target","AA:BB:CC:DD:EE:FF");
            assertEquals(Service.START_NOT_STICKY,s.onStartCommand(new Intent().setAction(ScaleService.ENSURE),0,2));assertTrue(ScaleService.connected());assertEquals("connected",ScaleService.stage());
        }finally{controller.destroy();}
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void garminGreenRequiresConfirmedLatestUpload(){
        context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();Measurement m=measurement(0,1,96);try(Store db=new Store(context())){db.insert(m);db.state(m.id(),"sent","");}
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            TextView local=decor(c.get()).findViewWithTag("stageLocal"),garmin=decor(c.get()).findViewWithTag("stageGarmin");assertFalse(local.getTypeface().isBold());assertFalse(garmin.getTypeface().isBold());Reception.received(m);
            try(Store db=new Store(context())){db.state(m.id(),"sent","");}context().sendBroadcast(new Intent(Sync.EVENT).setPackage(context().getPackageName()));Shadows.shadowOf(Looper.getMainLooper()).idle();
            garmin=decor(c.get()).findViewWithTag("stageGarmin");assertTrue(garmin.getTypeface().isBold());local=decor(c.get()).findViewWithTag("stageLocal");assertEquals(local.getCurrentTextColor(),garmin.getCurrentTextColor());assertNull(decor(c.get()).findViewWithTag("receiveProgress"));
        }
    }
    @Test public void healthOptionIsSeparateAndDropdownsHaveRoundedSurfaces(){
        seed();try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            Spinner metric=(Spinner)description(decor(c.get()),"Zeitraum");
            assertTrue(metric.getPopupBackground() instanceof android.graphics.drawable.GradientDrawable);
            assertTrue(((android.graphics.drawable.GradientDrawable)metric.getPopupBackground()).getCornerRadius()>0);
            View chart=decor(c.get()).findViewWithTag("measurementChart");ViewGroup chartPanel=(ViewGroup)chart.getParent();ViewGroup content=(ViewGroup)chartPanel.getParent();View details=decor(c.get()).findViewWithTag("measurementDetails");assertTrue(content.indexOfChild(details)>=0);assertTrue(content.indexOfChild(details)<content.indexOfChild(chartPanel));
            description(decor(c.get()),"Einstellungen öffnen").performClick();
            Switch sync=(Switch)find(decor(c.get()),"Mit Health Connect synchronisieren");assertNotNull(sync);assertFalse(sync.isChecked());assertNotNull(find(decor(c.get()),"Datennutzung und Datenschutz"));
        }
    }
    @Test public void expandingDetailsPreservesTheReadingAfterChartDraw(){
        seed();try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            layout(c.get());View root=decor(c.get());ViewGroup details=root.findViewWithTag("measurementDetails");View summary=((ViewGroup)details.getChildAt(0)).getChildAt(0);
            View expanded=root.findViewWithTag("expandedMeasurementDetails");assertEquals(View.GONE,expanded.getVisibility());
            String selected=ReflectionHelpers.getField(c.get(),"selectedId");
            MeasurementChart chart=root.findViewWithTag("measurementChart");android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(chart.getWidth(),chart.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);chart.draw(new android.graphics.Canvas(bitmap));
            View toggle=root.findViewWithTag("measurementDetailsToggle");toggle.performClick();assertEquals(View.VISIBLE,expanded.getVisibility());toggle.performClick();assertEquals(View.GONE,expanded.getVisibility());
            assertSame(summary,((ViewGroup)details.getChildAt(0)).getChildAt(0));assertEquals(selected,ReflectionHelpers.getField(c.get(),"selectedId"));assertNull(find(root,"Zeitausschnitt zurücksetzen"));bitmap.recycle();
        }
    }
    @Test public void changingChartWindowDoesNotRebuildOrResizeMeasurementPanel(){
        seed();try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            layout(c.get());ViewGroup details=decor(c.get()).findViewWithTag("measurementDetails");View panel=details.getChildAt(0);int height=details.getHeight();String selected=ReflectionHelpers.getField(c.get(),"selectedId");
            MeasurementChart chart=decor(c.get()).findViewWithTag("measurementChart");
            for(double zoom:new double[]{2,.5,3}){chart.viewport().transform(.5,.4,zoom);((Runnable)ReflectionHelpers.getField(chart,"windowListener")).run();layout(c.get());assertSame(panel,details.getChildAt(0));assertEquals(height,details.getHeight());assertEquals(selected,ReflectionHelpers.getField(c.get(),"selectedId"));}
        }
    }
    @Test public void receptionStopsAfterIdleAndAfterReceipt(){
        var controller=Robolectric.buildService(ScaleService.class).create();try{
            ScaleService service=controller.get();ReflectionHelpers.setStaticField(ScaleService.class,"active",true);
            Runnable idle=ReflectionHelpers.getField(service,"idleTimeout");idle.run();assertFalse(ScaleService.running());assertTrue(Shadows.shadowOf(service).isStoppedBySelf());
            ReflectionHelpers.setField(service,"stopped",false);ReflectionHelpers.setStaticField(ScaleService.class,"active",true);
            context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();Measurement m=measurement(0,1,82);
            ReflectionHelpers.callInstanceMethod(service,"saveMeasurement",ReflectionHelpers.ClassParameter.from(Measurement.class,m));assertEquals(m.id(),Reception.measurementId());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3));assertFalse(ScaleService.running());assertEquals(m.id(),Reception.measurementId());
        }finally{controller.destroy();}
    }
    @Test public void replayedScaleHistoryDoesNotFinishNewWeighIn(){
        context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();Measurement old=measurement(1,1,81);try(Store db=new Store(context())){db.insert(old);db.state(old.id(),"sent","");}
        var controller=Robolectric.buildService(ScaleService.class).create();try{
            ScaleService service=controller.get();ReflectionHelpers.setField(service,"receptionStartedSeconds",Instant.now().getEpochSecond());ReflectionHelpers.setStaticField(ScaleService.class,"active",true);
            ReflectionHelpers.callInstanceMethod(service,"saveMeasurement",ReflectionHelpers.ClassParameter.from(Measurement.class,old));Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3));assertNull(Reception.measurementId());assertTrue(ScaleService.running());
        }finally{controller.destroy();}
    }
    @Test public void receivedReadingReplacesOldSelectionAndAppearsDespiteClockSkew(){
        seed();
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            for(long skew:new long[]{120,-120}){
                ReflectionHelpers.callInstanceMethod(c.get(),"selectMeasurement",ReflectionHelpers.ClassParameter.from(int.class,0));
                MeasurementChart oldChart=decor(c.get()).findViewWithTag("measurementChart");
                ReflectionHelpers.setField(c.get(),"windowLeft",oldChart.viewport().lower);
                ReflectionHelpers.setField(c.get(),"windowRight",oldChart.viewport().lower+86400);
                Reception.begin();long now=Instant.now().getEpochSecond();
                Measurement incoming=new Measurement("AA:BB:CC:DD:EE:FF",now+skew-Bs444Protocol.EPOCH,1,83.45,23,51,38,3,1750);
                var controller=Robolectric.buildService(ScaleService.class).create();
                try{
                    ScaleService service=controller.get();ReflectionHelpers.setField(service,"receptionStartedSeconds",now);ReflectionHelpers.setStaticField(ScaleService.class,"active",true);
                    ReflectionHelpers.callInstanceMethod(service,"saveMeasurement",ReflectionHelpers.ClassParameter.from(Measurement.class,incoming));
                    Shadows.shadowOf(Looper.getMainLooper()).idle();
                    assertEquals(incoming.id(),ReflectionHelpers.getField(c.get(),"selectedId"));
                    java.util.List<Store.Row> rows=ReflectionHelpers.getField(c.get(),"graphRows");
                    int selected=ReflectionHelpers.getField(c.get(),"selectedIndex");assertEquals(incoming.unixSeconds,rows.get(selected).measurement().unixSeconds);
                    MeasurementChart chart=decor(c.get()).findViewWithTag("measurementChart");assertTrue(chart.viewport().right()>=incoming.unixSeconds);assertTrue(chart.viewport().left()<=incoming.unixSeconds);
                    assertNull(decor(c.get()).findViewWithTag("receiveProgress"));
                    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertFalse(ScaleService.running());
                    // Later upload/status events must not undo a deliberate selection again.
                    ReflectionHelpers.callInstanceMethod(c.get(),"selectMeasurement",ReflectionHelpers.ClassParameter.from(int.class,0));String chosen=ReflectionHelpers.getField(c.get(),"selectedId");
                    context().sendBroadcast(new Intent(Sync.EVENT).setPackage(context().getPackageName()));Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(chosen,ReflectionHelpers.getField(c.get(),"selectedId"));
                }finally{controller.destroy();}
            }
        }
    }
    @Test public void oldBackfillWrongUserAndInvalidFutureDoNotCompleteReception(){
        context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();
        long now=Instant.now().getEpochSecond();var controller=Robolectric.buildService(ScaleService.class).create();
        try{
            ScaleService service=controller.get();ReflectionHelpers.setField(service,"receptionStartedSeconds",now);ReflectionHelpers.setStaticField(ScaleService.class,"active",true);
            for(long[] sample:new long[][]{{-86400,1},{900,1},{0,2}}){
                Measurement m=new Measurement("AA",now+sample[0]-Bs444Protocol.EPOCH,(int)sample[1],82,24,51,38,3,1750);
                ReflectionHelpers.callInstanceMethod(service,"saveMeasurement",ReflectionHelpers.ClassParameter.from(Measurement.class,m));
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertNull(Reception.measurementId());assertTrue(ScaleService.running());
            }
        }finally{controller.destroy();}
    }
    @Test public void reusedScaleTimestampCreatesCurrentMeasurementInsteadOfDroppingIt(){
        long oldTime=Instant.now().minus(Duration.ofDays(1)).getEpochSecond();
        Measurement previous=new Measurement("AA",oldTime-Bs444Protocol.EPOCH,1,80,24,51,38,3,1700);
        context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();try(Store db=new Store(context())){assertTrue(db.insert(previous));}
        var controller=Robolectric.buildService(ScaleService.class).create();try{
            ScaleService service=controller.get();long now=Instant.now().getEpochSecond();ReflectionHelpers.setField(service,"receptionStartedSeconds",now);ReflectionHelpers.setStaticField(ScaleService.class,"active",true);
            Measurement incoming=new Measurement("AA",oldTime-Bs444Protocol.EPOCH,1,82,23,52,39,3.1,1755);
            ReflectionHelpers.callInstanceMethod(service,"saveMeasurement",ReflectionHelpers.ClassParameter.from(Measurement.class,incoming));
            assertNotNull(Reception.measurementId());assertNotEquals(previous.id(),Reception.measurementId());
            try(Store db=new Store(context())){var rows=db.timeline(1,0,now+300);assertEquals(2,rows.size());Measurement stored=rows.get(1).measurement();assertEquals(82,stored.weight,0);assertTrue(Math.abs(stored.unixSeconds-now)<=2);}
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertFalse(ScaleService.running());
        }finally{controller.destroy();}
    }
    @Test public void pullRefreshRequiresTopAndEnoughDistance(){
        int[] count={0};RefreshScrollView scroll=new RefreshScrollView(context(),()->count[0]++);scroll.addView(new TextView(context()));
        float density=context().getResources().getDisplayMetrics().density;
        scroll.onInterceptTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,10,10,0));
        assertTrue(scroll.onInterceptTouchEvent(MotionEvent.obtain(0,1,MotionEvent.ACTION_MOVE,10,110*density,0)));
        scroll.onTouchEvent(MotionEvent.obtain(0,2,MotionEvent.ACTION_MOVE,10,110*density,0));scroll.onTouchEvent(MotionEvent.obtain(0,3,MotionEvent.ACTION_UP,10,110*density,0));assertEquals(1,count[0]);
        scroll.onInterceptTouchEvent(MotionEvent.obtain(0,4,MotionEvent.ACTION_DOWN,10,10,0));scroll.onInterceptTouchEvent(MotionEvent.obtain(0,5,MotionEvent.ACTION_MOVE,10,30*density,0));scroll.onTouchEvent(MotionEvent.obtain(0,6,MotionEvent.ACTION_CANCEL,10,30*density,0));assertEquals(1,count[0]);
    }
    @Test public void englishTranslationIncludesMetricsAndSettings(){
        seed();context().getSharedPreferences("settings",0).edit().putString("language","en").commit();
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            assertNotNull(find(decor(c.get()),"Your history"));assertNotNull(find(decor(c.get()),"82.0 kg"));assertNotNull(description(decor(c.get()),"Metric"));
            description(decor(c.get()),"Open settings").performClick();assertNotNull(find(decor(c.get()),"Your scale"));assertNotNull(description(decor(c.get()),"Language"));assertNotNull(find(decor(c.get()),"Sign in to Garmin"));
        }
    }
    @Test public void hundredScanResultsStayInDropdown(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            description(decor(c.get()),"Einstellungen öffnen").performClick();View before=description(decor(c.get()),"Bluetooth-Gerät auswählen");
            for(int i=0;i<100;i++)context().sendBroadcast(new Intent(ScaleService.DEVICE).setPackage(context().getPackageName()).putExtra("address",String.format("AA:BB:CC:DD:EE:%02X",i)).putExtra("name","Device "+i).putExtra("likely",i==99));
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertSame(before,description(decor(c.get()),"Bluetooth-Gerät auswählen"));assertNull(find(decor(c.get()),"Device 40"));assertNotNull(find(decor(c.get()),"100 Geräte gefunden · 1 Waagen-Kandidat(en) zuerst"));
            before.performClick();ListPopupWindow popup=ReflectionHelpers.getField(c.get(),"devicePopup");assertEquals(100,popup.getListView().getAdapter().getCount());View row=popup.getListView().getAdapter().getView(0,null,popup.getListView());assertNotNull(find(row,"AA:BB:CC:DD:EE:63"));
        }
    }
    @Test public void filtersPersistAndSelectionMovesDetails(){
        seed();try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            ((Spinner)description(decor(c.get()),"Zeitraum")).setSelection(0);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,context().getSharedPreferences("settings",0).getInt("chartPeriod",-1));
            description(decor(c.get()),"Messwert").performClick();
            AlertDialog choice=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            find(choice.getWindow().getDecorView(),"Körperfett · %").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,context().getSharedPreferences("settings",0).getInt("chartMetric",-1));
            String before=ReflectionHelpers.getField(c.get(),"selectedId");description(decor(c.get()),"Vorherige Messung").performClick();String after=ReflectionHelpers.getField(c.get(),"selectedId");assertNotEquals(before,after);assertNotNull(find(decor(c.get()),"24,1 %"));
        }
    }
    @Test public void staleBs444ClockUsesPhoneReceiptTimeAndCompletesReception(){
        context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();var controller=Robolectric.buildService(ScaleService.class).create();try{
            ScaleService service=controller.get();long now=Instant.now().getEpochSecond();ReflectionHelpers.setField(service,"receptionStartedSeconds",now);ReflectionHelpers.setStaticField(ScaleService.class,"active",true);
            Measurement stale=new Measurement("AA",now-86400-Bs444Protocol.EPOCH,1,82.4,23,52,39,3.1,1750);
            ReflectionHelpers.callInstanceMethod(service,"saveMeasurement",ReflectionHelpers.ClassParameter.from(Measurement.class,stale),ReflectionHelpers.ClassParameter.from(boolean.class,true));
            String id=Reception.measurementId();assertNotNull(id);try(Store db=new Store(context())){Measurement stored=db.measurement(id);assertNotNull(stored);assertEquals(82.4,stored.weight,0);assertTrue(Math.abs(stored.unixSeconds-now)<=2);assertNull(db.measurement(stale.id()));}
            assertTrue(context().getSharedPreferences("settings",0).getString("timeDiagnostics","").contains("staleLiveClock=true"));Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));assertFalse(ScaleService.running());
        }finally{controller.destroy();}
    }
    @Test public void currentBs444TimestampIsPreserved(){
        context().getSharedPreferences("settings",0).edit().putInt("user",1).commit();var controller=Robolectric.buildService(ScaleService.class).create();try{
            ScaleService service=controller.get();long now=Instant.now().getEpochSecond();ReflectionHelpers.setField(service,"receptionStartedSeconds",now-30);ReflectionHelpers.setStaticField(ScaleService.class,"active",true);Measurement current=new Measurement("AA",now-15-Bs444Protocol.EPOCH,1,82,23,52,39,3,1700);
            ReflectionHelpers.callInstanceMethod(service,"saveMeasurement",ReflectionHelpers.ClassParameter.from(Measurement.class,current),ReflectionHelpers.ClassParameter.from(boolean.class,true));assertEquals(current.id(),Reception.measurementId());assertTrue(context().getSharedPreferences("settings",0).getString("timeDiagnostics","").contains("staleLiveClock=false"));
        }finally{controller.destroy();}
    }
    @Test public void targetWeightShowsAnEstimateAndIsScopedToUser(){
        seed();context().getSharedPreferences("settings",0).edit().putFloat("targetWeight.1",80).commit();try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            TextView estimate=decor(c.get()).findViewWithTag("goalEstimate");assertNotNull(estimate);assertTrue(estimate.getText().toString().startsWith("Noch etwa "));assertNotNull(find(decor(c.get()),"Wunschgewicht · 80,0 kg"));
            context().getSharedPreferences("settings",0).edit().putInt("user",2).commit();ReflectionHelpers.callInstanceMethod(c.get(),"render");assertNotNull(find(decor(c.get()),"Wunschgewicht festlegen"));assertNull(decor(c.get()).findViewWithTag("goalEstimate"));
        }
    }
    @Test @Config(sdk=35,qualifiers="w393dp-h851dp-port-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void touchSelectsMeasurementAndRenderPreviews() throws Exception {
        seed();for(String mode:new String[]{"light","dark"}){
            context().getSharedPreferences("settings",0).edit().putString("theme",mode).commit();
            try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
                Activity a=c.get();layout(a);save(a,"overview-"+mode);MeasurementChart chart=decor(a).findViewWithTag("measurementChart");float[] xs=ReflectionHelpers.getField(chart,"xs"),ys=ReflectionHelpers.getField(chart,"ys");
                assertNotNull(xs);assertTrue(xs.length<21);assertTrue(xs.length>1);chart.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,xs[0],ys[0],0));chart.onTouchEvent(MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,xs[0],ys[0],0));assertEquals(chart.groupFor(0).last(),(int)ReflectionHelpers.getField(c.get(),"selectedIndex"));assertNotNull(find(decor(a),"2 Messungen zusammengefasst · Einzelwert unten"));
                description(decor(a),"Einstellungen öffnen").performClick();layout(a);save(a,"settings-"+mode);
                Shadows.shadowOf(Looper.getMainLooper()).idle();ScrollView settings=ReflectionHelpers.getField(c.get(),"scroll");settings.scrollTo(0,settings.getChildAt(0).getHeight());save(a,"settings-backup-"+mode);
            }
        }
    }
    @Test @Config(sdk=35,qualifiers="w393dp-h851dp-port-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void multipleCurvesCanBeSelectedAndViewportSurvivesStatusUpdates() throws Exception {
        seed();context().getSharedPreferences("settings",0).edit().putString("theme","dark").commit();
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            decor(c.get()).findViewWithTag("metricMenu").performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            assertNotNull(find(dialog.getWindow().getDecorView(),"Knochenmasse · %"));
            assertNull(find(dialog.getWindow().getDecorView(),"Grundumsatz · kcal"));
            assertNull(find(dialog.getWindow().getDecorView(),"Muskelmasse¹ · kg"));
            View popup=dialog.getWindow().getDecorView();popup.measure(View.MeasureSpec.makeMeasureSpec(1059,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2300,View.MeasureSpec.AT_MOST));popup.layout(0,0,popup.getMeasuredWidth(),popup.getMeasuredHeight());
            android.graphics.Bitmap snapshot=android.graphics.Bitmap.createBitmap(popup.getWidth(),popup.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);popup.draw(new android.graphics.Canvas(snapshot));
            new java.io.File("build/reports/screenshots").mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream("build/reports/screenshots/metrics-popup.png")){snapshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            ((CheckBox)dialog.getWindow().getDecorView().findViewWithTag("metricCheck.FAT")).performClick();
            ((CheckBox)dialog.getWindow().getDecorView().findViewWithTag("metricCheck.MUSCLE")).performClick();dialog.getWindow().getDecorView().findViewWithTag("collapseMetrics").performClick();
            layout(c.get());save(c.get(),"overview-multi-dark");
            MeasurementChart chart=decor(c.get()).findViewWithTag("measurementChart");java.util.List<?> curves=ReflectionHelpers.getField(chart,"series");assertEquals(3,curves.size());
            assertEquals((double)ReflectionHelpers.getField(curves.get(1),"min"),(double)ReflectionHelpers.getField(curves.get(2),"min"),0);
            assertEquals((double)ReflectionHelpers.getField(curves.get(1),"max"),(double)ReflectionHelpers.getField(curves.get(2),"max"),0);
            float[] fatX=ReflectionHelpers.getField(curves.get(1),"xs"),fatY=ReflectionHelpers.getField(curves.get(1),"ys");
            chart.onTouchEvent(MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,fatX[0],fatY[0],0));chart.onTouchEvent(MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,fatX[0],fatY[0],0));assertEquals(ChartData.Metric.FAT,chart.selectedMetric());
            chart.viewport().transform(.5,.5,3);chart.invalidate();layout(c.get());save(c.get(),"overview-multi-zoom");
            ((Runnable)ReflectionHelpers.getField(chart,"windowListener")).run();double span=chart.viewport().span();
            context().sendBroadcast(new Intent(Sync.EVENT).setPackage(context().getPackageName()));Shadows.shadowOf(Looper.getMainLooper()).idle();
            chart=decor(c.get()).findViewWithTag("measurementChart");assertEquals(span,chart.viewport().span(),1);
            assertNull(find(decor(c.get()),"Zeitausschnitt zurücksetzen"));
        }
    }
    @Test @Config(sdk=35,qualifiers="w393dp-h851dp-port-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void standardDialogActionsRemainInsidePopup() throws Exception {
        for(String mode:new String[]{"light","dark"}){
            context().getSharedPreferences("settings",0).edit().putString("theme",mode).commit();
            try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
                for(String method:new String[]{"chooseMetrics","login","scaleProfile","driverSettings","confirmUpload"}){
                    if(method.equals("confirmUpload"))ReflectionHelpers.callInstanceMethod(c.get(),"review",ReflectionHelpers.ClassParameter.from(Measurement.class,measurement(0,1,82)));
                    else ReflectionHelpers.callInstanceMethod(c.get(),method);
                    AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();View v=d.getWindow().getDecorView();
                    v.measure(View.MeasureSpec.makeMeasureSpec(1059,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2300,View.MeasureSpec.AT_MOST));v.layout(0,0,v.getMeasuredWidth(),v.getMeasuredHeight());
                    for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
                        Button b=d.getButton(which);if(b==null||b.getVisibility()!=View.VISIBLE)continue;
                        android.graphics.Rect rect=new android.graphics.Rect();b.getDrawingRect(rect);((ViewGroup)v).offsetDescendantRectToMyCoords(b,rect);
                        assertTrue(method,rect.top>=0);assertTrue(method,rect.bottom<v.getHeight());assertTrue(method,rect.left>=0&&rect.right<=v.getWidth());
                    }
                    android.graphics.Bitmap bmp=android.graphics.Bitmap.createBitmap(v.getWidth(),v.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);v.draw(new android.graphics.Canvas(bmp));
                    new java.io.File("build/reports/screenshots").mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream("build/reports/screenshots/dialog-"+method+"-"+mode+".png")){bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}d.dismiss();
                }
            }
        }
    }
    private void save(Activity a,String name) throws Exception {
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1179,2553,android.graphics.Bitmap.Config.ARGB_8888);decor(a).draw(new android.graphics.Canvas(bitmap));java.io.File dir=new java.io.File("build/reports/screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}
    }
    @Test @Config(sdk=35,qualifiers="w393dp-h851dp-port-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void accountDialogMatchesThemeAndEnglishPreview() throws Exception {
        seed();context().getSharedPreferences("vault",0).edit().putString("session","ui-fixture-only").commit();
        for(String lang:new String[]{"de","en"}){
            context().getSharedPreferences("settings",0).edit().putString("theme","dark").putString("language",lang).commit();
            try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
                Activity a=c.get();layout(a);if(lang.equals("en"))save(a,"overview-english");
                description(decor(a),lang.equals("de")?"Einstellungen öffnen":"Open settings").performClick();layout(a);
                find(decor(a),lang.equals("de")?"Konto / Anmeldung":"Account / sign-in").performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();
                AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(dialog.isShowing());assertEquals(1,(int)ReflectionHelpers.getField(c.get(),"openDialogs"));
                assertTrue(dialog.getWindow().getDecorView().getBackground() instanceof android.graphics.drawable.GradientDrawable);
                assertEquals(0,dialog.getWindow().getAttributes().windowAnimations);
                TextView action=find(dialog.getWindow().getDecorView(),lang.equals("de")?"Erneut anmelden":"Sign in again");assertNotNull(action);assertTrue(action.getTypeface().isBold());
                assertNotNull(find(dialog.getWindow().getDecorView(),lang.equals("de")?"Mit E-Mail und Passwort anmelden":"Sign in with email and password"));
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(1179,2553,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);decor(a).draw(canvas);
                android.graphics.Paint dim=new android.graphics.Paint();dim.setColor(0x66000000);canvas.drawRect(0,0,1179,2553,dim);
                View popup=dialog.getWindow().getDecorView();popup.measure(View.MeasureSpec.makeMeasureSpec(1083,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1600,View.MeasureSpec.AT_MOST));popup.layout(0,0,1083,popup.getMeasuredHeight());canvas.translate(48,(2553-popup.getMeasuredHeight())/2f);popup.draw(canvas);
                try(java.io.FileOutputStream out=new java.io.FileOutputStream("build/reports/screenshots/account-"+lang+".png")){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
                dialog.dismiss();Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,(int)ReflectionHelpers.getField(c.get(),"openDialogs"));
            }
        }
    }
}
