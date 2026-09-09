package de.scalebridge;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    public Store(Context c) { super(c,"measurements.db",null,2); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE measurements (id TEXT PRIMARY KEY, address TEXT NOT NULL, stamp INTEGER NOT NULL, user INTEGER NOT NULL, weight REAL NOT NULL, fat REAL, water REAL, muscle REAL, bone REAL, basal REAL, state TEXT NOT NULL DEFAULT 'pending', note TEXT NOT NULL DEFAULT '')");
        healthTable(db);
    }
    private static void healthTable(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS health_sync (id TEXT PRIMARY KEY, mask INTEGER NOT NULL DEFAULT 0)");}
    @Override public void onUpgrade(SQLiteDatabase db,int a,int b) { if(a<2)healthTable(db); }
    public int healthMask(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT mask FROM health_sync WHERE id=?",new String[]{id})){return c.moveToFirst()?c.getInt(0):0;}}
    public void healthSent(String id,int mask){ContentValues v=new ContentValues();v.put("id",id);v.put("mask",healthMask(id)|mask);getWritableDatabase().insertWithOnConflict("health_sync",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    private static void metric(ContentValues v,String key,double n) { if(Double.isFinite(n)) v.put(key,n); else v.putNull(key); }
    public boolean insert(Measurement m) {
        ContentValues v=new ContentValues();v.put("id",m.id());v.put("address",m.address);v.put("stamp",m.scaleSeconds);v.put("user",m.user);v.put("weight",m.weight);
        metric(v,"fat",m.fat);metric(v,"water",m.water);metric(v,"muscle",m.musclePercent);metric(v,"bone",m.bone);metric(v,"basal",m.basal);
        return getWritableDatabase().insertWithOnConflict("measurements",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    public Measurement measurement(String id){
        try(Cursor c=getReadableDatabase().query("measurements",null,"id=?",new String[]{id},null,null,null,"1")){
            return c.moveToFirst()?new Measurement(c.getString(1),c.getLong(2),c.getInt(3),c.getDouble(4),number(c,5),number(c,6),number(c,7),number(c,8),number(c,9)):null;
        }
    }
    public record Row(Measurement measurement,String state,String note) {}
    private static double number(Cursor c,int n) {return c.isNull(n)?Double.NaN:c.getDouble(n);}
    public List<Row> rows(int user,boolean pending) {
        List<Row> rows=new ArrayList<>();
        String where=pending?"user=? AND state='pending'":null;
        try(Cursor c=getReadableDatabase().query("measurements",null,where,pending?new String[]{""+user}:null,null,null,"stamp DESC",pending?"100":"200")) {
            while(c.moveToNext()) rows.add(new Row(new Measurement(c.getString(1),c.getLong(2),c.getInt(3),c.getDouble(4),number(c,5),number(c,6),number(c,7),number(c,8),number(c,9)),c.getString(10),c.getString(11)));
        }
        return rows;
    }
    public List<Row> allMeasurements(){
        List<Row> result=new ArrayList<>();try(Cursor c=getReadableDatabase().query("measurements",null,null,null,null,null,"stamp ASC, id ASC")){
            while(c.moveToNext())result.add(new Row(new Measurement(c.getString(1),c.getLong(2),c.getInt(3),c.getDouble(4),number(c,5),number(c,6),number(c,7),number(c,8),number(c,9)),c.getString(10),c.getString(11)));
        }return result;
    }
    /** Full selected-user history for charts; unlike the legacy overview this has no 200-row cap. */
    public List<Row> timeline(int user,long from,long through) {
        List<Row> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("measurements",null,"user=? AND stamp>=? AND stamp<=?",new String[]{""+user,""+(from-Bs444Protocol.EPOCH),""+(through-Bs444Protocol.EPOCH)},null,null,"stamp ASC, id ASC")){
            while(c.moveToNext())result.add(new Row(new Measurement(c.getString(1),c.getLong(2),c.getInt(3),c.getDouble(4),number(c,5),number(c,6),number(c,7),number(c,8),number(c,9)),c.getString(10),c.getString(11)));
        }
        return result;
    }
    public void state(String id,String state,String note) {ContentValues v=new ContentValues();v.put("state",state);v.put("note",note);getWritableDatabase().update("measurements",v,"id=?",new String[]{id});}
    public void recoverInterrupted() { getWritableDatabase().execSQL("UPDATE measurements SET state='review', note='Upload unterbrochen. Bitte zuerst in Garmin prüfen.' WHERE state='sending'"); }
}
