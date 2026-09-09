package de.scalebridge;

import android.content.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Sync {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    public static boolean running(){return RUNNING.get();}
    public static final String EVENT="de.scalebridge.UPDATE";
    public static void recover(Context c){
        if(!RUNNING.compareAndSet(false,true))return;
        try(Store db=new Store(c)){db.recoverInterrupted();}finally{RUNNING.set(false);}
    }
    public static void status(Context c,String s){c.getSharedPreferences("settings",0).edit().putString("syncStatus",Tr.text(c,s)).apply();c.sendBroadcast(new Intent(EVENT).setPackage(c.getPackageName()));}
    public static String error(Exception e){return e instanceof java.io.IOException?e.getMessage():"Vorgang fehlgeschlagen ("+e.getClass().getSimpleName()+"). Bitte erneut anmelden oder Verbindung prüfen.";}
    public static boolean run(Context c,boolean manual) {
        if(!RUNNING.compareAndSet(false,true))return false;
        try(Store db=new Store(c)) {
            SharedPreferences prefs=c.getSharedPreferences("settings",0);
            if(!manual&&!prefs.getBoolean("autoUpload",false))return false;
            int user=prefs.getInt("user",-1);if(user<1||user>8){status(c,Tr.t(c,R.string.ui_175));return false;}
            if(System.currentTimeMillis()<prefs.getLong("uploadAfter",0)){status(c,Tr.t(c,R.string.ui_176));return true;}
            db.recoverInterrupted();
            java.util.List<Store.Row> pending=db.rows(user,true);if(pending.isEmpty()){status(c,Tr.t(c,R.string.ui_177)+user+".");return false;}
            status(c,Tr.t(c,R.string.ui_178));GarminClient client=new GarminClient(c);client.ensureSession();int sent=0;
            for(Store.Row row:pending) {
                if(Thread.currentThread().isInterrupted()||!new Vault(c).exists()||prefs.getInt("user",-1)!=user||(!manual&&!prefs.getBoolean("autoUpload",false)))return false;
                Measurement m=row.measurement();
                if(m.unixSeconds>System.currentTimeMillis()/1000+300||m.unixSeconds<1577836800L){db.state(m.id(),"review",Tr.t(c,R.string.ui_179));continue;}
                // Weight-only models are valid; absent body metrics remain absent in FIT.
                db.state(m.id(),"sending","");
                status(c,Tr.t(c,R.string.ui_54));
                try {
                    client.upload(m,prefs.getBoolean("muscle",false));db.state(m.id(),"sent","");sent++;status(c,Tr.t(c,R.string.ui_180));
                } catch(GarminClient.HttpFailure e) {
                    if(e.status==429) {
                        db.state(m.id(),"pending",e.getMessage());prefs.edit().putLong("uploadAfter",System.currentTimeMillis()+1800000).apply();status(c,e.getMessage());return true;
                    }
                    if(e.status==401||e.status==403)db.state(m.id(),"pending",e.getMessage());
                    else db.state(m.id(),"review",e.getMessage());
                    status(c,e.getMessage());return false;
                } catch(Exception e) {db.state(m.id(),"review",Tr.t(c,R.string.ui_181));status(c,error(e));return false;}
            }
            status(c,sent+Tr.t(c,R.string.ui_182));
            return false;
        } catch(Exception e){status(c,error(e));return e instanceof java.net.SocketTimeoutException||e instanceof java.net.UnknownHostException;}
        finally {RUNNING.set(false);c.sendBroadcast(new Intent(EVENT).setPackage(c.getPackageName()));}
    }
}
