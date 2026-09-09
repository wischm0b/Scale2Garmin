package de.scalebridge;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

public final class UploadJob extends JobService {
    private ExecutorService executor;
    public static void schedule(Context c) {
        if(!c.getSharedPreferences("settings",0).getBoolean("autoUpload",false))return;
        c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(444,new ComponentName(c,UploadJob.class))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(30*60*1000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public static void periodic(Context c) {
        JobScheduler jobs=c.getSystemService(JobScheduler.class);
        if(!c.getSharedPreferences("settings",0).getBoolean("autoUpload",false)){jobs.cancel(444);jobs.cancel(445);return;}
        jobs.schedule(new JobInfo.Builder(445,new ComponentName(c,UploadJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).build());
    }
    @Override public boolean onStartJob(JobParameters p) {executor=Executors.newSingleThreadExecutor();executor.execute(()->{boolean retry=Sync.run(this,false);jobFinished(p,retry);executor.shutdown();});return true;}
    @Override public boolean onStopJob(JobParameters p) {if(executor!=null)executor.shutdownNow();return true;}
}
