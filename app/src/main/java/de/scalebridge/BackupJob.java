package de.scalebridge;

import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.*;

/** Writes a restorable CSV snapshot into a user-selected document tree. */
public final class BackupJob extends JobService {
    static final int NOW=448, LEGACY_PERIODIC=449;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();

    private static boolean configured(Context c){
        var p=c.getSharedPreferences("settings",0);
        return p.getBoolean("autoBackup",false)&&!p.getString("backupTree","").isBlank();
    }
    public static void schedule(Context c){
        if(!configured(c))return;
        c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(NOW,new ComponentName(c,BackupJob.class))
            .setBackoffCriteria(60_000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    /** Removes the daily 0.7.0 job and cancels pending work when backup is disabled. */
    public static void configure(Context c){
        JobScheduler jobs=c.getSystemService(JobScheduler.class);
        jobs.cancel(LEGACY_PERIODIC);if(!configured(c))jobs.cancel(NOW);
    }
    static String fileName(LocalDate day,boolean dated){return dated?"Scale2Garmin-Backup-"+day+".csv":"Scale2Garmin-Backup.csv";}
    static String folderLabel(Context c){
        String raw=c.getSharedPreferences("settings",0).getString("backupTree","");if(raw.isBlank())return "";Uri tree=Uri.parse(raw);
        Uri document=tree;try{document=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));}catch(Exception ignored){}
        try(Cursor cursor=c.getContentResolver().query(document,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()&&!cursor.getString(0).isBlank())return cursor.getString(0);
        }catch(Exception ignored){}
        try{String id=DocumentsContract.getTreeDocumentId(tree);int split=Math.max(id.lastIndexOf(':'),id.lastIndexOf('/'));return Uri.decode(id.substring(split+1));}catch(Exception ignored){return tree.getLastPathSegment();}
    }
    static int write(Context c) throws IOException {
        String raw=c.getSharedPreferences("settings",0).getString("backupTree","");if(raw.isBlank())throw new IOException("Kein Zielordner ausgewählt.");
        boolean dated=c.getSharedPreferences("settings",0).getBoolean("datedBackups",false);
        Uri tree=Uri.parse(raw),file=findOrCreate(c,tree,fileName(LocalDate.now(),dated));
        synchronized(BackupJob.class){try(OutputStream stream=c.getContentResolver().openOutputStream(file,"wt");Writer writer=new OutputStreamWriter(Objects.requireNonNull(stream),StandardCharsets.UTF_8);Store db=new Store(c)){return CsvBackup.write(db,writer);}}
    }
    private static Uri findOrCreate(Context c,Uri tree,String name) throws IOException {
        ContentResolver resolver=c.getContentResolver();String parent;
        try{parent=DocumentsContract.getTreeDocumentId(tree);}catch(Exception e){throw new IOException("Der Zielordner ist nicht mehr verfügbar.",e);}
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent);
        try(Cursor cursor=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){
            if(cursor!=null)while(cursor.moveToNext())if(name.equals(cursor.getString(1)))return DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0));
        }catch(Exception e){throw new IOException("Der Zielordner kann nicht gelesen werden.",e);}
        Uri parentUri=DocumentsContract.buildDocumentUriUsingTree(tree,parent);
        try{Uri created=DocumentsContract.createDocument(resolver,parentUri,"text/csv",name);if(created==null)throw new IOException("Die Sicherungsdatei konnte nicht angelegt werden.");return created;}
        catch(IOException e){throw e;}catch(Exception e){throw new IOException("Die Sicherungsdatei konnte nicht angelegt werden.",e);}
    }
    private static void status(Context c,String value){c.getSharedPreferences("settings",0).edit().putString("backupStatus",value).apply();c.sendBroadcast(new Intent(Sync.EVENT).setPackage(c.getPackageName()));}
    @Override public boolean onStartJob(JobParameters p){executor.execute(()->{try{int count=write(this);status(this,count+Tr.text(this," Messungen automatisch gesichert."));jobFinished(p,false);}catch(Exception e){status(this,Tr.text(this,"Automatische Sicherung fehlgeschlagen: ")+e.getMessage());jobFinished(p,true);}});return true;}
    @Override public boolean onStopJob(JobParameters p){return true;}
    @Override public void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
