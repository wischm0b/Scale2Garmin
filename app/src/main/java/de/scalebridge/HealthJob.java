package de.scalebridge;

import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;

/** Local Health Connect writes do not depend on network, Garmin login or Garmin's job. */
public final class HealthJob extends JobService {
    private ExecutorService executor;
    public static void schedule(Context c){
        if(c.getSharedPreferences("settings",0).getBoolean("healthSync",false))
            c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(446,new ComponentName(c,HealthJob.class))
                .setBackoffCriteria(60000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public static void periodic(Context c){
        JobScheduler jobs=c.getSystemService(JobScheduler.class);
        if(!c.getSharedPreferences("settings",0).getBoolean("healthSync",false)){jobs.cancel(446);jobs.cancel(447);return;}
        jobs.schedule(new JobInfo.Builder(447,new ComponentName(c,HealthJob.class)).setPeriodic(15*60*1000L).build());
    }
    @Override public boolean onStartJob(JobParameters p){executor=Executors.newSingleThreadExecutor();ExecutorService worker=executor;worker.execute(()->{boolean retry=HealthSync.run(this);jobFinished(p,retry);worker.shutdown();});return true;}
    @Override public boolean onStopJob(JobParameters p){if(executor!=null)executor.shutdownNow();return true;}
}
