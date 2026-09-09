package de.scalebridge;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import com.health.openscale.bridge.OpenScaleBridge;

public final class MainActivity extends androidx.activity.ComponentActivity {
    private String tr(int id){return Tr.t(this,id);}

    private int GREEN,INK,MUTED,PAPER,SURFACE,SOFT,GRID,AMBER;
    private boolean dark,settingsOpen,registered,followLatest=true;
    private SharedPreferences prefs;
    private LinearLayout root,content,detailsHost;
    private ScrollView scroll;
    private MeasurementChart chart;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Map<String,Device> devices=new LinkedHashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private record Device(String address,String name,boolean likely){}
    private DeviceAdapter deviceAdapter;
    private ListPopupWindow devicePopup;
    private TextView deviceCount;
    private List<Store.Row> graphRows=List.of();
    private int period,metricIndex,selectedIndex=-1;
    private ChartData.Metric detailCurve;
    private double windowLeft=Double.NaN,windowRight=Double.NaN;
    private String selectedId,pendingBluetoothAction,lastDisplayedReception;
    private WebView loginView;
    private int openDialogs;
    private androidx.activity.result.ActivityResultLauncher<Set<String>> healthPermissions;
    private androidx.activity.result.ActivityResultLauncher<Intent> createBackup;
    private androidx.activity.result.ActivityResultLauncher<Intent> openCsv;
    private androidx.activity.result.ActivityResultLauncher<Intent> backupFolder;
    private final BroadcastReceiver updates=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
        if(ScaleService.INTERACTION.equals(i.getAction())){String[] labels=i.getStringArrayExtra("labels");if(i.getBooleanExtra("consent",false)){LinearLayout box=column();EditText code=input(box,tr(R.string.ui_111),InputType.TYPE_CLASS_NUMBER);dialog().setTitle(tr(R.string.ui_140)).setView(box).setPositiveButton(tr(R.string.ui_114),(d,w)->ScaleService.feedback(0,code.getText().toString())).setNegativeButton(tr(R.string.ui_92),null).show();}else if(labels!=null)dialog().setTitle(tr(R.string.ui_141)).setItems(labels,(d,w)->ScaleService.feedback(w,null)).setNegativeButton(tr(R.string.ui_92),null).show();return;}
        if(ScaleService.DEVICE.equals(i.getAction())){
            String address=i.getStringExtra("address");if(address==null)return;
            devices.put(address,new Device(address,i.getStringExtra("name"),i.getBooleanExtra("likely",false)));
            // Scan results update only the dropdown, not the entire screen or its scroll position.
            if(deviceAdapter!=null)deviceAdapter.refresh();updateDeviceCount();return;
        }
        render();
    }};
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(Tr.wrap(base));}
    @Override public void onCreate(Bundle saved){
        prefs=getSharedPreferences("settings",0);String theme=prefs.getString("theme","system");
        dark="dark".equals(theme)||("system".equals(theme)&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(saved);
        createBackup=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->{Uri uri=result.getData()==null?null:result.getData().getData();if(result.getResultCode()==RESULT_OK&&uri!=null)writeBackup(uri);});
        openCsv=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->{Uri uri=result.getData()==null?null:result.getData().getData();if(result.getResultCode()==RESULT_OK&&uri!=null){prefs.edit().putString("csvStatus",Tr.text(this,"Datei wird importiert …")).apply();render();readCsv(uri);}else{prefs.edit().putString("csvStatus",Tr.text(this,"Keine Datei ausgewählt.")).apply();render();}});
        backupFolder=registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),result->{
            boolean enable=prefs.getBoolean("backupEnablePending",false);prefs.edit().remove("backupEnablePending").apply();Intent data=result.getData();Uri uri=data==null?null:data.getData();
            if(result.getResultCode()==RESULT_OK&&uri!=null){try{
                int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)throw new SecurityException(Tr.text(this,"Der ausgewählte Ordner erlaubt keinen Schreibzugriff."));
                if(flags!=0)getContentResolver().takePersistableUriPermission(uri,flags);
                SharedPreferences.Editor edit=prefs.edit().putString("backupTree",uri.toString()).putString("backupStatus",Tr.text(this,"Zielordner ausgewählt."));if(enable)edit.putBoolean("autoBackup",true);edit.apply();
                BackupJob.configure(this);render();
            }catch(Exception e){prefs.edit().putBoolean("autoBackup",false).putString("backupStatus",Tr.text(this,"Zielordner konnte nicht gespeichert werden: ")+e.getMessage()).apply();BackupJob.configure(this);render();}}
            else if(enable){prefs.edit().putBoolean("autoBackup",false).apply();render();}
        });
        healthPermissions=registerForActivityResult(HealthSync.permissionContract(),granted->{boolean enabled=granted.stream().anyMatch(HealthSync.permissions()::contains)&&prefs.getInt("user",-1)>0;prefs.edit().putBoolean("healthSync",enabled).apply();HealthJob.periodic(this);if(enabled)HealthJob.schedule(this);else HealthSync.status(this,"Health Connect benötigt eine Schreibberechtigung.");render();});palette();period=Math.max(0,Math.min(4,prefs.getInt("chartPeriod",1)));metricIndex=Math.max(0,Math.min(ChartData.Metric.values().length-1,prefs.getInt("chartMetric",0)));
        if(saved!=null){settingsOpen=saved.getBoolean("settings");selectedId=saved.getString("selected");followLatest=saved.getBoolean("follow",true);pendingBluetoothAction=saved.getString("bluetoothAction");windowLeft=saved.getDouble("windowLeft",Double.NaN);windowRight=saved.getDouble("windowRight",Double.NaN);lastDisplayedReception=saved.getString("displayedReception");}
        getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);getWindow().setBackgroundDrawableResource(dark?android.R.color.black:android.R.color.white);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        Sync.recover(this);render(false);UploadJob.periodic(this);HealthJob.periodic(this);HealthJob.schedule(this);BackupJob.configure(this);
    }
    private void palette(){
        GREEN=Color.parseColor(dark?"#77DDB1":"#176C58");INK=Color.parseColor(dark?"#E8F0EA":"#192C27");MUTED=Color.parseColor(dark?"#A4B5AC":"#63766B");
        PAPER=Color.parseColor(dark?"#0F1714":"#F3F6F1");SURFACE=Color.parseColor(dark?"#1B2722":"#FFFFFF");SOFT=Color.parseColor(dark?"#273A30":"#EAF1EA");
        GRID=Color.parseColor(dark?"#34463D":"#E1E9E1");AMBER=Color.parseColor(dark?"#F1BF76":"#986018");
    }
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putBoolean("settings",settingsOpen);b.putString("selected",selectedId);b.putBoolean("follow",followLatest);b.putString("bluetoothAction",pendingBluetoothAction);b.putDouble("windowLeft",windowLeft);b.putDouble("windowRight",windowRight);b.putString("displayedReception",lastDisplayedReception);}
    // Android 12 uses our signature permission; Android 13+ additionally uses NOT_EXPORTED.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override protected void onStart(){
        super.onStart();IntentFilter filter=new IntentFilter(Sync.EVENT);filter.addAction(ScaleService.DEVICE);filter.addAction(ScaleService.INTERACTION);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(updates,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(updates,filter,"de.scalebridge.INTERNAL",null);
        registered=true;if(!ScaleService.running())Reception.begin();render();
    }
    @Override protected void onStop(){if(registered){unregisterReceiver(updates);registered=false;}super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);if(devicePopup!=null)devicePopup.dismiss();if(loginView!=null)loginView.destroy();executor.shutdown();super.onDestroy();}
    @Override public void onBackPressed(){if(settingsOpen){settingsOpen=false;render(false);}else super.onBackPressed();}
    private boolean hasBluetoothPermissions(){return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable menuBackground(){GradientDrawable d=bg(SURFACE,16);d.setPadding(dp(4),dp(10),dp(4),dp(10));return d;}
    private TextView text(String s,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(Tr.text(this,s));v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("tnum");if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(0,dp(3),0,dp(3));return v;}
    private void paragraph(LinearLayout box,String s){box.addView(text(s,13,MUTED,false));}
    private LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private LinearLayout panel(LinearLayout parent,String title){
        LinearLayout box=column();box.setPadding(dp(18),dp(16),dp(18),dp(16));box.setBackground(bg(SURFACE,20));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);parent.addView(box,p);
        if(title!=null)box.addView(text(title,18,INK,true));return box;
    }
    private LinearLayout card(String title){return panel(content,title);}
    private Button button(LinearLayout box,String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?(dark?PAPER:Color.WHITE):INK);b.setBackgroundTintList(null);
        b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(dark?0x3377DDB1:0x22176C58),bg(primary?GREEN:SOFT,16),bg(Color.WHITE,16)));b.setStateListAnimator(null);b.setElevation(0);
        b.setMinHeight(dp(50));b.setMinimumHeight(dp(50));b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setGravity(primary?Gravity.CENTER:Gravity.CENTER_VERTICAL|Gravity.START);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);box.addView(b,lp);b.setOnClickListener(v->{v.setPressed(false);v.jumpDrawablesToCurrentState();action.run();});return b;
    }
    private AlertDialog.Builder dialog(){return new AlertDialog.Builder(this){
        @Override public AlertDialog create(){AlertDialog d=super.create();d.create();
            Window window=d.getWindow();window.setBackgroundDrawable(bg(SURFACE,24));window.setDimAmount(.32f);window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(420)),-2);
            // Configure before the first frame. Window blur avoids stale RenderEffect layers on buttons.
            WindowManager.LayoutParams attrs=window.getAttributes();attrs.windowAnimations=0;attrs.setBlurBehindRadius(dp(14));window.setAttributes(attrs);window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            tintDialog(window.getDecorView());
            for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button b=d.getButton(which);if(b!=null){b.setAllCaps(false);b.setTextColor(GREEN);b.setBackgroundTintList(null);b.setBackground(bg(SOFT,12));b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setStateListAnimator(null);
                if(b.getLayoutParams() instanceof ViewGroup.MarginLayoutParams lp){lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;lp.setMargins(dp(6),dp(4),dp(6),dp(4));b.setLayoutParams(lp);}
                if(b.getParent() instanceof ViewGroup actions){actions.setPadding(dp(12),dp(8),dp(12),dp(16));actions.setMinimumHeight(dp(80));actions.setClipChildren(false);actions.setClipToPadding(false);ViewGroup.LayoutParams ap=actions.getLayoutParams();ap.height=ViewGroup.LayoutParams.WRAP_CONTENT;actions.setLayoutParams(ap);}}}
            window.getDecorView().addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){openDialogs++;}public void onViewDetachedFromWindow(View v){openDialogs=Math.max(0,openDialogs-1);}});
            return d;}
    };}
    private void tintDialog(View v){if("palette".equals(v.getTag()))return;if(v instanceof TextView t){t.setTextColor(INK);t.setFontFeatureSettings("tnum");}if(v instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++)tintDialog(g.getChildAt(i));}
    private IconView icon(String glyph,String description,Runnable action){IconView v=new IconView(this,glyph,GREEN);v.setContentDescription(description);v.setBackground(bg(SOFT,16));v.setOnClickListener(w->action.run());v.setFocusable(true);v.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return v;}
    private void render(){render(true);}
    private void render(boolean keepScroll){
        if(isFinishing()||isDestroyed())return;int position=keepScroll&&scroll!=null?scroll.getScrollY():0;
        if(devicePopup!=null&&devicePopup.isShowing())return;
        deviceAdapter=null;deviceCount=null;root=column();root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);return insets;});
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setBaselineAligned(false);header.setPadding(dp(20),dp(14),dp(20),dp(14));root.addView(header);
        LinearLayout headings=column();headings.addView(text("SCALE2GARMIN",11,GREEN,true));headings.addView(text(settingsOpen?tr(R.string.ui_0):tr(R.string.ui_1),26,INK,true));header.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(icon(settingsOpen?"‹":"⚙",settingsOpen?tr(R.string.ui_28):tr(R.string.ui_27),()->{settingsOpen=!settingsOpen;render(false);}));
        scroll=settingsOpen?new ScrollView(this):new RefreshScrollView(this,this::receiveManually);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=column();content.setPadding(dp(16),0,dp(16),dp(20));scroll.addView(content);
        if(settingsOpen)settings();else overview();
        setContentView(root);root.requestApplyInsets();ScrollView current=scroll;current.post(()->current.scrollTo(0,position));
    }
    private ChartData.Metric metric(){if(metricIndex>4)metricIndex=0;return ChartData.Metric.values()[metricIndex];}
    private void receiveManually(){
        if(!prefs.contains("address")){settingsOpen=true;render(false);return;}
        Reception.begin();lastDisplayedReception=null;start(ScaleService.CONNECT);render();
    }
    private List<ChartData.Metric> shownMetrics(){
        List<ChartData.Metric> result=new ArrayList<>();result.add(metric());
        Set<String> extra=prefs.getStringSet("chartMetrics",Set.of());
        for(ChartData.Metric m:chartOptions())if(m!=metric()&&extra.contains(m.name()))result.add(m);
        return result;
    }
    private ChartData.Metric[] chartOptions(){return Arrays.copyOf(ChartData.Metric.values(),5);}
    private void applyMetrics(Set<String> chosen){
        if(!chosen.contains(metric().name()))for(ChartData.Metric m:chartOptions())if(chosen.contains(m.name())){metricIndex=m.ordinal();break;}
        detailCurve=null;prefs.edit().putInt("chartMetric",metricIndex).putStringSet("chartMetrics",new HashSet<>(chosen)).apply();render();
    }
    private void chooseMetrics(){
        Set<String> chosen=new HashSet<>();for(ChartData.Metric m:shownMetrics())chosen.add(m.name());
        LinearLayout box=column();box.setPadding(dp(20),dp(20),dp(20),dp(20));
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);title.setBaselineAligned(false);box.addView(title);
        title.addView(text(Tr.text(this,"Messwerte auswählen"),20,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        AlertDialog[] holder=new AlertDialog[1];IconView close=icon("⌃",Tr.text(this,"Auswahl einklappen"),()->holder[0].dismiss());close.setTag("collapseMetrics");title.addView(close);
        paragraph(box,Tr.text(this,"Name: nur diese Kurve · Häkchen: mehrere"));
        for(ChartData.Metric m:chartOptions()){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);box.addView(row);
            String label=Tr.text(this,m.label)+" · "+m.unit;
            TextView name=text(label,16,INK,false);name.setGravity(Gravity.CENTER_VERTICAL);name.setMinHeight(dp(52));name.setFocusable(true);name.setOnClickListener(v->{applyMetrics(Set.of(m.name()));holder[0].dismiss();});row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            CheckBox check=new CheckBox(this);check.setContentDescription(label);check.setTag("metricCheck."+m.name());check.setChecked(chosen.contains(m.name()));check.setPadding(0,0,0,0);row.addView(check,new LinearLayout.LayoutParams(dp(48),dp(48)));
            check.setOnCheckedChangeListener((v,on)->{
                if(on)chosen.add(m.name());else if(chosen.size()==1){check.setChecked(true);return;}else chosen.remove(m.name());
                applyMetrics(chosen);
            });
        }
        ScrollView wrapper=new ScrollView(this);wrapper.addView(box);holder[0]=dialog().setView(wrapper).create();holder[0].show();
    }
    private List<Store.Row> allRows(){try(Store db=new Store(this)){return db.timeline(prefs.getInt("user",-1),0,Instant.now().getEpochSecond()+300);}}
    private void overview(){
        List<Store.Row> all=allRows();Store.Row current=null;for(Store.Row row:all)if(row.measurement().id().equals(Reception.measurementId()))current=row;
        boolean newReception=current!=null&&!current.measurement().id().equals(lastDisplayedReception);
        if(newReception){
            lastDisplayedReception=current.measurement().id();selectedId=lastDisplayedReception;followLatest=false;
            windowLeft=Double.NaN;windowRight=Double.NaN;detailCurve=null;
            Measurement incoming=current.measurement();
            if(shownMetrics().stream().noneMatch(m->Double.isFinite(m.value(incoming))))metricIndex=0;
        }
        connection(current);
        LinearLayout box=card(null);LinearLayout filters=new LinearLayout(this);box.addView(filters);
        LinearLayout range=column(),kind=column();LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,.42f);p.rightMargin=dp(10);filters.addView(range,p);filters.addView(kind,new LinearLayout.LayoutParams(0,-2,.58f));
        paragraph(range,tr(R.string.ui_12));paragraph(kind,tr(R.string.ui_11));
        selector(range,ChartData.PERIODS,period,tr(R.string.ui_13),index->{if(period!=index){windowLeft=Double.NaN;windowRight=Double.NaN;period=index;prefs.edit().putInt("chartPeriod",index).apply();followLatest=true;selectedId=null;render();}});
        List<ChartData.Metric> selectedMetrics=shownMetrics();
        String selectedLabel=Tr.text(this,metric().label)+(selectedMetrics.size()>1?" +"+(selectedMetrics.size()-1):" · "+metric().unit)+"  ▾";
        Button metricMenu=button(kind,selectedLabel,false,this::chooseMetrics);metricMenu.setTag("metricMenu");metricMenu.setContentDescription(tr(R.string.ui_10));
        ((LinearLayout.LayoutParams)metricMenu.getLayoutParams()).topMargin=0;
        TextView reset=text(Tr.text(this,"Zeitausschnitt zurücksetzen"),12,GREEN,true);reset.setPadding(0,dp(12),0,dp(8));reset.setMinHeight(dp(48));reset.setGravity(Gravity.CENTER_VERTICAL);reset.setOnClickListener(v->{if(chart!=null)chart.resetWindow();});box.addView(reset);
        long start=ChartData.start(period,Instant.now(),ZoneId.systemDefault()),end=Instant.now().getEpochSecond();
        List<ChartData.Metric> shown=shownMetrics();graphRows=new ArrayList<>();
        for(Store.Row row:all)if(shown.stream().anyMatch(m->Double.isFinite(m.value(row.measurement()))))graphRows.add(row);
        selectedIndex=graphRows.size()-1;
        if(!followLatest&&selectedId!=null)for(int i=0;i<graphRows.size();i++)if(selectedId.equals(graphRows.get(i).measurement().id())){selectedIndex=i;break;}
        if(!graphRows.isEmpty())end=Math.max(end,graphRows.get(graphRows.size()-1).measurement().unixSeconds);
        chart=new MeasurementChart(this,graphRows,shown,selectedIndex,start,end,GREEN,MUTED,GRID,SURFACE,this::selectMeasurement);chart.setTag("measurementChart");
        double target=targetWeight();if(Double.isFinite(target))chart.setTargetWeight(target);
        if(detailCurve!=null)chart.setSelectedMetric(detailCurve);
        if(Double.isFinite(windowLeft))chart.restoreWindow(windowLeft,windowRight);
        chart.onWindowChanged(()->{windowLeft=chart.viewport().left();windowRight=chart.viewport().right();if(detailsHost!=null)details();});
        box.addView(chart,new LinearLayout.LayoutParams(-1,dp(230+shown.size()*21)));

        insights(all,shown);

        detailsHost=column();detailsHost.setTag("measurementDetails");content.addView(detailsHost);details();
        if(!prefs.contains("address"))button(content,tr(R.string.ui_44),true,()->{settingsOpen=true;render(false);});

    }
    private void insights(List<Store.Row> rows,List<ChartData.Metric> metrics){
        LinearLayout box=card(Tr.text(this,"Trends & Prognosen"));boolean any=false;
        for(ChartData.Metric m:metrics){TrendAnalysis.Result trend=TrendAnalysis.analyze(rows,m,ZoneId.systemDefault());if(trend==null)continue;any=true;
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setBaselineAligned(false);box.addView(line);
            LinearLayout labels=column();labels.addView(text(Tr.text(this,m.label),14,INK,true));double weekly=trend.slopePerDay()*7;
            String direction=Math.abs(weekly)<(m==ChartData.Metric.WEIGHT?.1:.2)?Tr.text(this,"stabil"):String.format(Tr.locale(this),"%+.1f %s/"+Tr.text(this,"Woche"),weekly,m.unit);
            labels.addView(text(Tr.text(this,"Trend")+" · "+direction,13,weekly==0?MUTED:GREEN,false));line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            if(Double.isFinite(trend.projected())){String quality=Tr.text(this,switch(trend.quality()){case HIGH->"hohe Verlässlichkeit";case MEDIUM->"mittlere Verlässlichkeit";case LOW->"geringe Verlässlichkeit";});
                LinearLayout forecast=column();TextView value=text(n(trend.projected(),m.unit),16,INK,true);value.setGravity(Gravity.RIGHT);forecast.addView(value);TextView caption=text(trend.days()+" "+Tr.text(this,"Tage")+" · "+quality,11,MUTED,false);caption.setGravity(Gravity.RIGHT);forecast.addView(caption);line.addView(forecast);
            }else{TextView more=text(Tr.text(this,"Mehr Daten nötig"),12,MUTED,false);more.setGravity(Gravity.RIGHT);line.addView(more);}
        }
        if(!any)paragraph(box,Tr.text(this,"Für Trends werden Messungen an mindestens drei Tagen benötigt."));
        goal(box,rows);
        paragraph(box,Tr.text(this,"Gestrichelte Linien zeigen den robusten Trend. Prognosen sind Schätzungen aus den letzten 90 Tagen."));
        box.setTag("trendInsights");
    }
    private String targetKey(){return "targetWeight."+prefs.getInt("user",-1);}
    private double targetWeight(){return prefs.getFloat(targetKey(),Float.NaN);}
    private void goal(LinearLayout box,List<Store.Row> rows){
        double target=targetWeight();String label=Double.isFinite(target)?Tr.text(this,"Wunschgewicht")+" · "+n(target,"kg"):Tr.text(this,"Wunschgewicht festlegen");
        Button edit=button(box,label,false,this::editTargetWeight);edit.setTag("targetWeight");
        if(!Double.isFinite(target))return;TrendAnalysis.Result trend=TrendAnalysis.analyze(rows,ChartData.Metric.WEIGHT,ZoneId.systemDefault());TrendAnalysis.Goal goal=TrendAnalysis.goal(trend,target,LocalDate.now());
        String message=switch(goal.status()){
            case ESTIMATED->Tr.text(this,"Noch etwa ")+duration(goal.days())+" · "+Tr.text(this,"voraussichtlich ")+DateTimeFormatter.ofPattern("d. MMM yyyy",Tr.locale(this)).format(goal.date());
            case REACHED->Tr.text(this,"Wunschgewicht erreicht.");
            case STABLE->Tr.text(this,"Der aktuelle Trend ist zu stabil für eine belastbare Zeitangabe.");
            case AWAY->Tr.text(this,"Der aktuelle Trend führt momentan vom Wunschgewicht weg.");
            case TOO_FAR->Tr.text(this,"Beim aktuellen Tempo liegt das Ziel mehr als zehn Jahre entfernt.");
            case NOT_ENOUGH_DATA->Tr.text(this,"Für die Zeitschätzung werden mindestens fünf Messtage über zwei Wochen benötigt.");
        };TextView status=text(message,13,goal.status()==TrendAnalysis.GoalStatus.ESTIMATED||goal.status()==TrendAnalysis.GoalStatus.REACHED?GREEN:MUTED,false);status.setTag("goalEstimate");box.addView(status);
    }
    private String duration(int days){if(days<14)return days+" "+Tr.text(this,"Tage");if(days<70)return (int)Math.ceil(days/7.0)+" "+Tr.text(this,"Wochen");return String.format(Tr.locale(this),"%.1f %s",days/30.44,Tr.text(this,"Monate"));}
    private void editTargetWeight(){
        if(prefs.getInt("user",-1)<1){toast(Tr.text(this,"Bitte zuerst deinen Speicherplatz auswählen."));return;}LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),dp(8));
        paragraph(box,Tr.text(this,"Die Restdauer wird aus deinem robusten Gewichtstrend geschätzt."));EditText value=input(box,Tr.text(this,"Wunschgewicht in kg"),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);double old=targetWeight();if(Double.isFinite(old))value.setText(String.format(Locale.ROOT,"%.1f",old));value.setSelectAllOnFocus(true);value.setBackground(bg(SOFT,12));value.setPadding(dp(14),dp(12),dp(14),dp(12));
        AlertDialog d=dialog().setTitle(Tr.text(this,"Wunschgewicht")).setView(box).setPositiveButton(Tr.text(this,"Übernehmen"),null).setNegativeButton(tr(R.string.ui_92),null).setNeutralButton(Tr.text(this,"Ziel entfernen"),null).create();d.setOnShowListener(x->{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{double target=Double.parseDouble(value.getText().toString().trim().replace(',','.'));if(target<1||target>300)throw new NumberFormatException();prefs.edit().putFloat(targetKey(),(float)target).apply();d.dismiss();render();}catch(NumberFormatException e){value.setError(Tr.text(this,"Bitte ein Gewicht zwischen 1 und 300 kg eingeben."));}});d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{prefs.edit().remove(targetKey()).apply();d.dismiss();render();});});d.show();
    }
    private void connection(Store.Row latest){
        LinearLayout box=card(null),line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setBaselineAligned(false);box.addView(line);
        boolean measuring="measuring".equals(ScaleService.stage());boolean received=latest!=null,sent=received&&"sent".equals(latest.state());
        TextView scale=text(tr(R.string.ui_9),13,ScaleService.connected()?GREEN:MUTED,ScaleService.connected());scale.setTag("stageScale");line.addView(scale,new LinearLayout.LayoutParams(0,-2,.9f));
        line.addView(text("→",13,MUTED,false));
        TextView local=text("Scale2Garmin",13,received?GREEN:MUTED,received);local.setGravity(Gravity.CENTER);local.setTag("stageLocal");line.addView(local,new LinearLayout.LayoutParams(0,-2,1.5f));line.addView(text("→",13,MUTED,false));
        TextView garmin=text("Garmin",13,sent?GREEN:MUTED,sent);garmin.setTag("stageGarmin");garmin.setGravity(Gravity.CENTER);garmin.setContentDescription(sent?tr(R.string.ui_61):tr(R.string.ui_62));garmin.setOnClickListener(v->confirmUpload());line.addView(garmin,new LinearLayout.LayoutParams(0,-2,1.1f));
        if((ScaleService.running()&&!received)||Sync.running()){
            FrameLayout slot=new FrameLayout(this);slot.setBackground(bg(SOFT,16));line.addView(slot,new LinearLayout.LayoutParams(dp(40),dp(40)));
            ProgressBar progress=new ProgressBar(this);progress.setPadding(0,0,0,0);progress.setIndeterminateTintList(ColorStateList.valueOf(GREEN));progress.setContentDescription(Sync.running()?tr(R.string.ui_50):tr(R.string.ui_49));progress.setTag("receiveProgress");slot.addView(progress,new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));
        }else {IconView reconnect=icon(sent?"✓":"↻",tr(R.string.ui_51),this::receiveManually);reconnect.setLayoutParams(new LinearLayout.LayoutParams(dp(40),dp(40)));line.addView(reconnect);}

        String status;
        if(!prefs.contains("address"))status=tr(R.string.ui_52);
        else if(!hasBluetoothPermissions())status=tr(R.string.ui_46);
        else if(measuring)status=tr(R.string.ui_53);
        else if(Sync.running())status=tr(R.string.ui_54);
        else if(latest!=null&&"review".equals(latest.state()))status=tr(R.string.ui_55);
        else if(sent)status=tr(R.string.ui_56)+shortDate(latest.measurement());
        else if(received)status=prefs.getBoolean("autoUpload",false)?tr(R.string.ui_57):tr(R.string.ui_58);
        else status=ScaleService.running()?tr(R.string.ui_59):prefs.getString("bleStatus",tr(R.string.ui_60));
        paragraph(box,status);
    }
    private String n(double value,String unit){return Double.isFinite(value)?String.format(Tr.locale(this),"%.1f %s",value,unit):"–";}
    private String shortDate(Measurement m){return DateTimeFormatter.ofPattern("dd.MM. · HH:mm",Tr.locale(this)).format(Instant.ofEpochSecond(m.unixSeconds).atZone(ZoneId.systemDefault()));}
    private String date(Measurement m){return DateTimeFormatter.ofPattern("dd. MMM yyyy · HH:mm",Tr.locale(this)).format(Instant.ofEpochSecond(m.unixSeconds).atZone(ZoneId.systemDefault()));}
    private void selectMeasurement(int index){if(index<0||index>=graphRows.size())return;selectedIndex=index;detailCurve=chart.selectedMetric();selectedId=graphRows.get(index).measurement().id();followLatest=index==graphRows.size()-1;chart.select(index);details();}
    private void details(){
        detailsHost.removeAllViews();if(selectedIndex<0||graphRows.isEmpty())return;Store.Row row=graphRows.get(selectedIndex);Measurement m=row.measurement();selectedId=m.id();ChartData.Metric detailMetric=chart==null?metric():chart.selectedMetric();
        LinearLayout box=panel(detailsHost,null);ChartData.Group group=chart==null?null:chart.groupFor(selectedIndex);
        if(group!=null&&group.count()>1){box.addView(text("Ø "+n(group.mean(),detailMetric.unit),22,GREEN,true));paragraph(box,group.count()+tr(R.string.ui_65));}
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setBaselineAligned(false);box.addView(top);
        LinearLayout title=column();title.addView(text(date(m),13,MUTED,false));title.addView(text(n(detailMetric.value(m),detailMetric.unit),32,INK,true));top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        IconView previous=icon("‹",tr(R.string.ui_29),()->selectMeasurement(selectedIndex-1)),next=icon("›",tr(R.string.ui_30),()->selectMeasurement(selectedIndex+1));
        previous.setEnabled(selectedIndex>0);previous.setAlpha(selectedIndex>0?1:.3f);next.setEnabled(selectedIndex<graphRows.size()-1);next.setAlpha(selectedIndex<graphRows.size()-1?1:.3f);top.addView(previous);top.addView(next);
        String change=Tr.text(this,detailMetric.label)+" · "+(selectedIndex+1)+" / "+graphRows.size();
        if(selectedIndex>0&&Double.isFinite(detailMetric.value(m))&&Double.isFinite(detailMetric.value(graphRows.get(selectedIndex-1).measurement()))){double delta=detailMetric.value(m)-detailMetric.value(graphRows.get(selectedIndex-1).measurement());change=String.format(Tr.locale(this),tr(R.string.ui_66),delta,detailMetric.unit);}
        paragraph(box,change);
        String[][] values={{tr(R.string.ui_15),n(m.weight,"kg")},{tr(R.string.ui_16),n(m.fat,"%")},{tr(R.string.ui_17),n(m.musclePercent,"%")},{tr(R.string.ui_18),n(m.water,"%")},{tr(R.string.ui_19),n(ChartData.Metric.BONE.value(m),"%")},{tr(R.string.ui_21),n(m.basal,"kcal")}};
        for(int i=0;i<values.length;i+=2){LinearLayout pair=new LinearLayout(this);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,-2);pl.topMargin=dp(7);box.addView(pair,pl);for(int j=i;j<i+2;j++){LinearLayout cell=column();cell.addView(text(values[j][0],11,MUTED,false));cell.addView(text(values[j][1],16,INK,true));pair.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}}
        paragraph(box,tr(R.string.ui_68)+n(m.muscleKg(),"kg")+tr(R.string.ui_69));
        String label=switch(row.state()){case "sent"->tr(R.string.ui_70);case "sending"->tr(R.string.ui_71);case "review"->tr(R.string.ui_72);default->tr(R.string.ui_73);};
        box.addView(text(label+tr(R.string.ui_74)+m.user,12,"review".equals(row.state())?AMBER:GREEN,true));
        if(!row.note().isEmpty())paragraph(box,row.note());
        if("review".equals(row.state()))button(box,tr(R.string.ui_75),false,()->review(m));
    }
    private void review(Measurement m){dialog().setTitle(tr(R.string.ui_76)).setMessage(tr(R.string.ui_77))
        .setPositiveButton(tr(R.string.ui_78),(d,w)->{try(Store db=new Store(this)){db.state(m.id(),"sent",tr(R.string.ui_79));}render();})
        .setNeutralButton(tr(R.string.ui_80),(d,w)->{try(Store db=new Store(this)){db.state(m.id(),"pending","");}render();}).setNegativeButton(tr(R.string.ui_81),null).show();}
    private Spinner selector(LinearLayout parent,String[] labels,int selection,String description,java.util.function.IntConsumer select){
        labels=Arrays.stream(labels).map(label->Tr.text(this,label)).toArray(String[]::new);Spinner spinner=new Spinner(this,Spinner.MODE_DROPDOWN);spinner.setContentDescription(description);spinner.setBackgroundTintList(null);spinner.setBackground(bg(SOFT,12));spinner.setPopupBackgroundDrawable(menuBackground());spinner.setMinimumHeight(dp(48));
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels){
            @Override public View getView(int p,View old,android.view.ViewGroup group){TextView v=(TextView)super.getView(p,old,group);v.setText(getItem(p)+"  ▾");v.setTextColor(INK);v.setTextSize(13);v.setSingleLine(false);v.setPadding(dp(10),dp(8),dp(10),dp(8));return v;}
            @Override public View getDropDownView(int p,View old,android.view.ViewGroup group){TextView v=(TextView)super.getDropDownView(p,old,group);v.setTextColor(INK);v.setBackgroundColor(Color.TRANSPARENT);v.setTextSize(15);v.setSingleLine(false);v.setMinHeight(dp(48));v.setPadding(dp(14),dp(12),dp(14),dp(12));return v;}
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(selection);parent.addView(spinner,new LinearLayout.LayoutParams(-1,-2));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){@Override public void onNothingSelected(AdapterView<?> p){}@Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){select.accept(pos);}});return spinner;
    }
    private void settings(){
        LinearLayout scale=card(tr(R.string.ui_2));
        LinearLayout deviceField=column();deviceField.setPadding(dp(12),dp(10),dp(12),dp(10));deviceField.setBackground(bg(SOFT,12));deviceField.setContentDescription(tr(R.string.ui_31));deviceField.setFocusable(true);scale.addView(deviceField);
        deviceField.addView(text(prefs.getString("deviceName",tr(R.string.ui_32))+"  ▾",14,INK,true));
        TextView address=text(prefs.getString("address",tr(R.string.ui_33)),12,MUTED,false);address.setTypeface(Typeface.MONOSPACE);deviceField.addView(address);
        deviceField.setOnClickListener(v->showDevices(deviceField));
        deviceCount=text("",12,MUTED,false);scale.addView(deviceCount);updateDeviceCount();
        button(scale,tr(R.string.ui_34),false,()->{devices.clear();updateDeviceCount();start(ScaleService.DISCOVER);});
        String driver=prefs.getString("driver."+prefs.getString("address",""),"");
        if(prefs.contains("address"))paragraph(scale,OpenScaleBridge.isMedisana(driver)?"Medisana BS440 / BS444":"openScale · "+prefs.getString("model."+prefs.getString("address",""),""));
        if(!OpenScaleBridge.isMedisana(driver)){button(scale,tr(R.string.ui_120),false,this::scaleProfile);button(scale,tr(R.string.ui_121),false,this::driverSettings);}
        paragraph(scale,tr(R.string.ui_38));String[] names=new String[9];names[0]=tr(R.string.ui_37);for(int i=1;i<=8;i++)names[i]=tr(R.string.ui_36)+i;
        selector(scale,names,Math.max(0,Math.min(8,prefs.getInt("user",-1))),tr(R.string.ui_35),pos->{int next=pos==0?-1:pos;if(next!=prefs.getInt("user",-1)){prefs.edit().putInt("user",next).putBoolean("autoUpload",false).putBoolean("healthSync",false).apply();UploadJob.periodic(this);HealthJob.periodic(this);windowLeft=Double.NaN;windowRight=Double.NaN;followLatest=true;selectedId=null;render();}});
        paragraph(scale,tr(R.string.ui_39));
        paragraph(scale,prefs.getString("bleStatus",""));
        LinearLayout appearance=card(tr(R.string.ui_3));String mode=prefs.getString("theme","system");selector(appearance,new String[]{tr(R.string.ui_4),tr(R.string.ui_5),tr(R.string.ui_6)},"dark".equals(mode)?1:"light".equals(mode)?2:0,tr(R.string.ui_14),i->{String next=new String[]{"system","dark","light"}[i];if(!next.equals(prefs.getString("theme","system"))){prefs.edit().putString("theme",next).apply();recreate();}});
        paragraph(appearance,Tr.text(this,tr(R.string.ui_7)));String language=prefs.getString("language","system");
        selector(appearance,new String[]{tr(R.string.ui_8),"Deutsch","English"},language.equals("de")?1:language.equals("en")?2:0,Tr.text(this,tr(R.string.ui_7)),i->{String next=new String[]{"system","de","en"}[i];if(!next.equals(prefs.getString("language","system"))){prefs.edit().putString("language",next).apply();recreate();}});
        LinearLayout garmin=card("Garmin Connect");boolean loggedIn=new Vault(this).exists();paragraph(garmin,loggedIn?tr(R.string.ui_82):tr(R.string.ui_83));
        if(!loggedIn){button(garmin,tr(R.string.ui_84),true,this::login);button(garmin,tr(R.string.ui_85),false,this::browserLogin);}
        else {
            button(garmin,tr(R.string.ui_86),false,this::confirmUpload);
            button(garmin,tr(R.string.ui_87),false,this::accountMenu);
        }
        toggle(garmin,tr(R.string.ui_93),"autoUpload",on->{if(on&&(prefs.getInt("user",-1)<1||!new Vault(this).exists())){toast(tr(R.string.ui_94));return false;}prefs.edit().putBoolean("autoUpload",on).apply();UploadJob.periodic(this);if(on)UploadJob.schedule(this);return true;});
        toggle(garmin,tr(R.string.ui_95),"muscle",on->{prefs.edit().putBoolean("muscle",on).apply();return true;});
        paragraph(garmin,tr(R.string.ui_96));
        paragraph(garmin,prefs.getString("syncStatus",""));
        healthSettings();
        backupSettings();

    }
    private void backupSettings(){
        LinearLayout box=card(Tr.text(this,"CSV-Sicherung"));
        paragraph(box,Tr.text(this,"Speichert alle Messungen als lesbare CSV-Datei an einem frei gewählten Ort."));
        button(box,Tr.text(this,"CSV-Backup speichern"),false,()->createBackup.launch(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE,"Scale2Garmin-"+java.time.LocalDate.now()+".csv")));
        TextView automatic=text(Tr.text(this,"Automatische Sicherung"),15,INK,true);automatic.setPadding(0,dp(20),0,dp(3));box.addView(automatic);
        paragraph(box,Tr.text(this,"Erstellt nach jeder neuen Messung eine vollständige, wieder importierbare CSV-Datei und überschreibt die vorherige Sicherung."));
        String folder=BackupJob.folderLabel(this);
        button(box,Tr.text(this,folder.isBlank()?"Zielordner auswählen":"Zielordner ändern"),false,()->chooseBackupFolder(false));
        paragraph(box,folder.isBlank()?Tr.text(this,"Kein Zielordner ausgewählt."):Tr.text(this,"Ziel: ")+folder);
        toggle(box,Tr.text(this,"Nach neuen Messungen automatisch sichern"),"autoBackup",on->{
            if(on&&prefs.getString("backupTree","").isBlank()){chooseBackupFolder(true);return false;}
            prefs.edit().putBoolean("autoBackup",on).apply();BackupJob.configure(this);return true;
        });
        toggle(box,Tr.text(this,"Für jeden Tag eine eigene Sicherungsdatei"),"datedBackups",on->{prefs.edit().putBoolean("datedBackups",on).apply();return true;});
        paragraph(box,Tr.text(this,"Optional: behält Tagesstände im Format Scale2Garmin-Backup-JJJJ-MM-TT.csv, jeweils aktualisiert nach neuen Messungen."));
        String backupStatus=prefs.getString("backupStatus","");if(!backupStatus.isBlank())paragraph(box,backupStatus);
        button(box,Tr.text(this,"CSV importieren"),false,()->{prefs.edit().putString("csvStatus",Tr.text(this,"Datei auswählen …")).apply();Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);openCsv.launch(pick);});
        String csvStatus=prefs.getString("csvStatus","");if(!csvStatus.isBlank())paragraph(box,csvStatus);
        paragraph(box,Tr.text(this,"Importiert Scale2Garmin-Backups und VitaDock+-Gewichtsexporte."));
    }
    private void chooseBackupFolder(boolean enableAfterSelection){
        prefs.edit().putBoolean("backupEnablePending",enableAfterSelection).apply();
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        backupFolder.launch(pick);
    }
    private void writeBackup(Uri uri){executor.execute(()->{try(java.io.OutputStream stream=getContentResolver().openOutputStream(uri,"wt");java.io.Writer writer=new java.io.OutputStreamWriter(java.util.Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8);Store db=new Store(this)){int count=CsvBackup.write(db,writer);runOnUiThread(()->toast(count+Tr.text(this," Messungen gespeichert.")));}catch(Exception e){runOnUiThread(()->toast(Tr.text(this,"Backup konnte nicht gespeichert werden: ")+e.getMessage()));}});}
    private void readCsv(Uri uri){int user=prefs.getInt("user",-1);if(user<1){String status=Tr.text(this,"Bitte zuerst deinen Speicherplatz auswählen.");prefs.edit().putString("csvStatus",status).apply();toast(status);render();return;}executor.execute(()->{try(java.io.InputStream stream=getContentResolver().openInputStream(uri);java.io.Reader reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(stream),java.nio.charset.StandardCharsets.UTF_8);Store db=new Store(this)){CsvBackup.Result result=CsvBackup.read(db,reader,user);runOnUiThread(()->{windowLeft=Double.NaN;windowRight=Double.NaN;followLatest=true;selectedId=null;String status=result.imported()+Tr.text(this," Messungen importiert · ")+result.duplicates()+Tr.text(this," Duplikate übersprungen.");prefs.edit().putString("csvStatus",status).apply();BackupJob.schedule(this);toast(status);render();});}catch(Exception e){runOnUiThread(()->{String status=Tr.text(this,"Import fehlgeschlagen: ")+e.getMessage();prefs.edit().putString("csvStatus",status).apply();render();dialog().setTitle(Tr.text(this,"CSV konnte nicht importiert werden")).setMessage(e.getMessage()).setPositiveButton(Tr.text(this,"Schließen"),null).show();});}});}
    private void accountMenu(){
        LinearLayout box=column();box.setTag("palette");box.setPadding(dp(24),dp(24),dp(24),dp(24));
        box.addView(text(tr(R.string.ui_88),22,INK,true));
        paragraph(box,Tr.text(this,"Anmeldung und Verbindung verwalten"));
        AlertDialog[] holder=new AlertDialog[1];
        accountAction(box,"↻",tr(R.string.ui_89),"Mit E-Mail und Passwort anmelden",GREEN,()->{holder[0].dismiss();login();});
        accountAction(box,"↗",tr(R.string.ui_90),"Garmin-Anmeldeseite öffnen",GREEN,()->{holder[0].dismiss();browserLogin();});
        accountAction(box,"−",tr(R.string.ui_91),"Gespeicherte Anmeldung entfernen",AMBER,()->{holder[0].dismiss();new Vault(this).clear();prefs.edit().putBoolean("autoUpload",false).apply();UploadJob.periodic(this);render();});
        Button cancel=button(box,tr(R.string.ui_92),false,()->holder[0].dismiss());cancel.setGravity(Gravity.CENTER);cancel.setTextColor(MUTED);cancel.setBackground(bg(SURFACE,12));
        LinearLayout.LayoutParams spacing=(LinearLayout.LayoutParams)cancel.getLayoutParams();spacing.topMargin=dp(16);cancel.setLayoutParams(spacing);
        ScrollView wrapper=new ScrollView(this);wrapper.addView(box);
        holder[0]=dialog().setView(wrapper).create();holder[0].show();
    }
    private void accountAction(LinearLayout parent,String glyph,String title,String subtitle,int accent,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);row.setPadding(dp(14),dp(16),dp(14),dp(16));
        GradientDrawable fill=bg(SOFT,16);fill.setStroke(dp(1),GRID);row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x2277DDB1),fill,bg(Color.WHITE,16)));row.setFocusable(true);row.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);parent.addView(row,lp);
        IconView symbol=new IconView(this,glyph,accent);row.addView(symbol,new LinearLayout.LayoutParams(dp(36),dp(44)));
        LinearLayout labels=column();labels.setPadding(dp(10),0,0,0);labels.addView(text(title,15,accent,true));labels.addView(text(subtitle,12,MUTED,false));row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void healthSettings(){
        LinearLayout box=card("Health Connect");
        paragraph(box,Tr.text(this,"Überträgt vorhandene und neue Messungen deines ausgewählten Speicherplatzes. Unabhängig von Garmin."));
        toggle(box,Tr.text(this,"Mit Health Connect synchronisieren"),"healthSync",on->{
            if(on){requestHealthPermissions();return false;}
            prefs.edit().putBoolean("healthSync",false).apply();HealthJob.periodic(this);return true;
        });
        if(prefs.getBoolean("healthSync",false)){
            button(box,Tr.text(this,"Jetzt zu Health Connect synchronisieren"),false,()->{HealthJob.schedule(this);HealthSync.status(this,"Übertragung zu Health Connect …");});
            button(box,Tr.text(this,"Berechtigungen verwalten"),false,this::requestHealthPermissions);
            paragraph(box,prefs.getString("healthStatus",""));
        }
        button(box,Tr.text(this,"Datennutzung und Datenschutz"),false,()->startActivity(new Intent(this,HealthPrivacyActivity.class)));
    }
    private void requestHealthPermissions(){
        if(prefs.getInt("user",-1)<1){toast(Tr.text(this,"Bitte zuerst deinen Speicherplatz auswählen."));return;}
        int status=HealthSync.availability(this);
        if(status==androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE){healthPermissions.launch(HealthSync.permissions());return;}
        dialog().setTitle("Health Connect").setMessage(Tr.text(this,"Health Connect ist nicht verfügbar. Bitte installieren oder aktualisieren."))
            .setPositiveButton(Tr.text(this,"Health Connect öffnen"),(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")));}catch(ActivityNotFoundException e){toast(Tr.text(this,"Health Connect ist auf diesem Gerät nicht verfügbar."));}})
            .setNegativeButton(tr(R.string.ui_92),null).show();
    }
    private String profileKey(String key){return key+"."+prefs.getInt("user",1);}
    private void scaleProfile(){
        LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),dp(8));
        paragraph(box,tr(R.string.ui_122));
        EditText name=input(box,tr(R.string.ui_123),InputType.TYPE_CLASS_TEXT),birthday=input(box,tr(R.string.ui_124),InputType.TYPE_CLASS_DATETIME),height=input(box,tr(R.string.ui_125),InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        name.setText(prefs.getString(profileKey("profileName"),""));birthday.setText(prefs.getString(profileKey("profileBirthday"),""));if(prefs.contains(profileKey("profileHeight")))height.setText(""+prefs.getFloat(profileKey("profileHeight"),0));
        Spinner sex=selector(box,new String[]{tr(R.string.ui_126),tr(R.string.ui_127)},prefs.getBoolean(profileKey("profileMale"),true)?0:1,tr(R.string.ui_128),i->{});
        Spinner activity=selector(box,new String[]{tr(R.string.ui_129),tr(R.string.ui_130),tr(R.string.ui_131),tr(R.string.ui_132),tr(R.string.ui_133)},prefs.getInt(profileKey("profileActivity"),0),tr(R.string.ui_134),i->{});
        AlertDialog d=dialog().setTitle(tr(R.string.ui_120)).setView(box).setPositiveButton(tr(R.string.ui_135),null).setNegativeButton(tr(R.string.ui_92),null).show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{LocalDate dob=LocalDate.parse(birthday.getText().toString().trim());float cm=Float.parseFloat(height.getText().toString().replace(',','.'));if(cm<80||cm>250||dob.isAfter(LocalDate.now().minusYears(1))||dob.isBefore(LocalDate.now().minusYears(120)))throw new IllegalArgumentException();prefs.edit().putString(profileKey("profileName"),name.getText().toString().trim()).putString(profileKey("profileBirthday"),dob.toString()).putFloat(profileKey("profileHeight"),cm).putBoolean(profileKey("profileMale"),sex.getSelectedItemPosition()==0).putInt(profileKey("profileActivity"),activity.getSelectedItemPosition()).apply();d.dismiss();}catch(Exception e){toast(tr(R.string.ui_136));}});
    }
    private void driverSettings(){
        String address=prefs.getString("address",""),id=prefs.getString("driver."+address,"");String handler=id.replaceFirst("_[0-9]+$","");
        SharedPreferences config=getSharedPreferences("driver-settings-"+handler+"-"+address,0);LinearLayout box=column();box.setPadding(dp(20),dp(8),dp(20),dp(8));
        Map<String,EditText> fields=new LinkedHashMap<>();
        if(id.startsWith("MiScaleS400Handler")){fields.put("s400_bind_key",input(box,tr(R.string.ui_137),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD));fields.put("s400_mac_address",input(box,tr(R.string.ui_138),InputType.TYPE_CLASS_TEXT));}
        if(id.startsWith("HuaweiHagridWspHandler"))for(String key:new String[]{"cak","c1","c2"})fields.put("ble/huawei_hagrid/"+key+"_hex",input(box,key.toUpperCase(Locale.ROOT)+" (Hex)",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD));
        if(fields.isEmpty())paragraph(box,tr(R.string.ui_139));
        for(var field:fields.entrySet())field.getValue().setText(config.getString(field.getKey(),""));
        dialog().setTitle(tr(R.string.ui_121)).setView(box).setPositiveButton(tr(R.string.ui_135),(d,w)->{SharedPreferences.Editor edit=config.edit();for(var field:fields.entrySet())edit.putString(field.getKey(),field.getValue().getText().toString().trim());edit.apply();}).setNegativeButton(tr(R.string.ui_92),null).show();
    }
    private void toggle(LinearLayout parent,String label,String key,java.util.function.Predicate<Boolean> change){Switch v=new Switch(this);v.setText(label);v.setTextSize(14);v.setTextColor(INK);v.setPadding(0,dp(14),0,dp(14));v.setSwitchPadding(dp(10));v.setChecked(prefs.getBoolean(key,false));parent.addView(v);v.setOnCheckedChangeListener((button,on)->{if(!change.test(on))button.setChecked(false);});}
    private void updateDeviceCount(){if(deviceCount!=null){long likely=devices.values().stream().filter(Device::likely).count();deviceCount.setText(devices.isEmpty()?tr(R.string.ui_40):devices.size()+tr(R.string.ui_41)+likely+tr(R.string.ui_42));}}
    private void showDevices(View anchor){
        deviceAdapter=new DeviceAdapter();deviceAdapter.refresh();devicePopup=new ListPopupWindow(this);devicePopup.setAnchorView(anchor);devicePopup.setWidth(anchor.getWidth());devicePopup.setHeight(dp(300));devicePopup.setModal(true);devicePopup.setBackgroundDrawable(menuBackground());devicePopup.setAdapter(deviceAdapter);
        devicePopup.setOnItemClickListener((parent,v,position,id)->{Device d=deviceAdapter.items.get(position);devicePopup.dismiss();devicePopup=null;
            if(!d.address().equals(prefs.getString("address",""))){prefs.edit().putBoolean("autoUpload",false).putBoolean("healthSync",false).apply();UploadJob.periodic(this);HealthJob.periodic(this);}prefs.edit().putString("address",d.address()).putString("deviceName",d.name()).putString("bleStatus",Tr.text(this,"Zum Empfangen nach unten ziehen oder auf ↻ tippen.")).apply();stopService(new Intent(this,ScaleService.class));render();});
        devicePopup.setOnDismissListener(()->main.post(()->{devicePopup=null;if(!isDestroyed())render();}));devicePopup.show();
    }
    private final class DeviceAdapter extends BaseAdapter {
        private final List<Device> items=new ArrayList<>();
        void refresh(){items.clear();items.addAll(devices.values());String saved=prefs.getString("address",null);if(saved!=null&&items.stream().noneMatch(d->d.address().equals(saved)))items.add(new Device(saved,prefs.getString("deviceName","Deine BS444"),true));items.sort(Comparator.comparing(Device::likely).reversed().thenComparing(Device::name).thenComparing(Device::address));notifyDataSetChanged();}
        @Override public int getCount(){return items.size();}@Override public Object getItem(int p){return items.get(p);}@Override public long getItemId(int p){return p;}
        @Override public View getView(int p,View old,ViewGroup parent){Device d=items.get(p);LinearLayout row=column();row.setPadding(dp(12),dp(10),dp(12),dp(10));row.setBackgroundColor(Color.TRANSPARENT);row.addView(text(d.name()+(d.likely()?tr(R.string.ui_43):""),14,d.likely()?GREEN:INK,d.likely()));TextView mac=text(d.address(),12,MUTED,false);mac.setTypeface(Typeface.MONOSPACE);row.addView(mac);return row;}
    }
    private void start(String action){
        if(ScaleService.ENSURE.equals(action)&&!prefs.contains("address"))return;
        List<String> permissions=new ArrayList<>();for(String p:new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)permissions.add(p);
        if(!permissions.isEmpty()){pendingBluetoothAction=action;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)permissions.add(Manifest.permission.POST_NOTIFICATIONS);requestPermissions(permissions.toArray(new String[0]),1);return;}
        try{startForegroundService(new Intent(this,ScaleService.class).setAction(action));}catch(Exception e){toast(tr(R.string.ui_48));}
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==1){String action=pendingBluetoothAction;pendingBluetoothAction=null;if(hasBluetoothPermissions()&&action!=null)start(action);else toast(tr(R.string.ui_47));render();}}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void confirmUpload(){
        if(!new Vault(this).exists()){toast(tr(R.string.ui_98));return;}
        int user=prefs.getInt("user",-1);if(user<1){toast(tr(R.string.ui_99));return;}
        int count;try(Store db=new Store(this)){count=db.rows(user,true).size();}if(count==0){toast(tr(R.string.ui_100));return;}
        dialog().setTitle(tr(R.string.ui_101)).setMessage(count+tr(R.string.ui_102)+user+tr(R.string.ui_103)).setPositiveButton(tr(R.string.ui_104),(d,w)->executor.execute(()->Sync.run(getApplicationContext(),true))).setNegativeButton(tr(R.string.ui_92),null).show();
    }
    private EditText input(LinearLayout box,String hint,int type){EditText e=new EditText(this);e.setHint(hint);e.setInputType(type);e.setSingleLine(true);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);box.addView(e);return e;}

    private void login(){
        prefs.edit().putBoolean("autoUpload",false).apply();UploadJob.periodic(this);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(8),dp(24),0);
        EditText email=input(box,tr(R.string.ui_105),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText password=input(box,tr(R.string.ui_106),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        dialog().setTitle(tr(R.string.ui_107)).setView(box).setPositiveButton(tr(R.string.ui_108),(d,w)->{
            String mail=email.getText().toString().trim(),pw=password.getText().toString();password.setText("");GarminClient client=new GarminClient(getApplicationContext());
            Sync.status(this,tr(R.string.ui_109));executor.execute(()->{try{boolean done=client.login(mail,pw);runOnUiThread(()->{if(done){Sync.status(this,tr(R.string.ui_110));render();}else mfa(client);});}catch(Exception e){Sync.status(this,Sync.error(e));}});
        }).setNegativeButton(tr(R.string.ui_92),null).show();
    }
    private void mfa(GarminClient client){
        if(isFinishing()||isDestroyed())return;
        EditText code=new EditText(this);code.setHint(tr(R.string.ui_111));code.setInputType(InputType.TYPE_CLASS_NUMBER);
        dialog().setTitle(tr(R.string.ui_112)).setMessage(tr(R.string.ui_113)).setView(code).setPositiveButton(tr(R.string.ui_114),(d,w)->executor.execute(()->{try{if(client.verify(code.getText().toString().trim()))Sync.status(this,tr(R.string.ui_110));else Sync.status(this,tr(R.string.ui_115));}catch(Exception e){Sync.status(this,Sync.error(e));}})).setNegativeButton(tr(R.string.ui_92),null).show();
    }
    private void browserLogin(){
        prefs.edit().putBoolean("autoUpload",false).apply();UploadJob.periodic(this);
        WebView web=new WebView(this);loginView=web;web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setSaveFormData(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        AlertDialog dialog=dialog().setTitle(tr(R.string.ui_116)).setView(web).setNegativeButton(tr(R.string.ui_117),null).create();
        boolean[] captured={false};
        web.setWebViewClient(new WebViewClient(){
            private boolean inspect(String url){Uri uri=Uri.parse(url);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||!(host.equals("garmin.com")||host.endsWith(".garmin.com")))return true;
                String ticket=uri.getQueryParameter("ticket");if(ticket!=null&&!captured[0]&&host.equals("sso.garmin.com")){captured[0]=true;dialog.dismiss();Sync.status(MainActivity.this,tr(R.string.ui_118));executor.execute(()->{try{new GarminClient(getApplicationContext()).completeBrowserTicket(ticket);Sync.status(MainActivity.this,tr(R.string.ui_110));}catch(Exception e){Sync.status(MainActivity.this,Sync.error(e));}});return true;}return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return inspect(r.getUrl().toString());}
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){if(inspect(url))v.stopLoading();}
            @Override public void onPageFinished(WebView v,String url){
                // The legacy Garmin widget sometimes renders its success ticket instead of navigating.
                // Read only this ticket, only on the expected HTTPS Garmin SSO origin; no JS bridge.
                Uri page=Uri.parse(url);
                if(captured[0]||!"https".equals(page.getScheme())||!"sso.garmin.com".equals(page.getHost()))return;
                v.evaluateJavascript("(function(){var m=document.documentElement.innerHTML.match(/embed\\?ticket=(ST-[A-Za-z0-9._-]+)/);return m?m[1]:null;})()",value->{
                    if(captured[0]||loginView!=web)return;
                    try{Object ticket=new org.json.JSONTokener(value).nextValue();if(ticket instanceof String t)inspect("https://sso.garmin.com/sso/embed?ticket="+Uri.encode(t));}catch(org.json.JSONException ignored){}
                });
            }
            @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){h.cancel();Sync.status(MainActivity.this,tr(R.string.ui_119));}
        });
        dialog.setOnDismissListener(d->{web.stopLoading();web.destroy();if(loginView==web)loginView=null;CookieManager.getInstance().removeAllCookies(null);WebStorage.getInstance().deleteAllData();});
        dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(520)),(int)(getResources().getDisplayMetrics().heightPixels*.85));dialog.show();
        web.loadUrl("https://sso.garmin.com/sso/signin?"+OAuth.form(Map.of("id","gauth-widget","embedWidget","true","gauthHost","https://sso.garmin.com/sso/embed","service","https://sso.garmin.com/sso/embed","source","https://sso.garmin.com/sso/embed","redirectAfterAccountLoginUrl","https://sso.garmin.com/sso/embed","redirectAfterAccountCreationUrl","https://sso.garmin.com/sso/embed")));
    }

}
