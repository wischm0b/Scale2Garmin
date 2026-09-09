package de.scalebridge;

import android.app.job.JobScheduler;
import android.content.Context;
import java.time.LocalDate;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class BackupJobTest {
    private Context context(){return RuntimeEnvironment.getApplication();}
    @Before public void clean(){context().getSharedPreferences("settings",0).edit().clear().commit();context().getSystemService(JobScheduler.class).cancelAll();}
    @Test public void jobsNeedBothOptInAndDestinationAndNoDailyJobRemains(){
        JobScheduler jobs=context().getSystemService(JobScheduler.class);
        jobs.schedule(new android.app.job.JobInfo.Builder(BackupJob.LEGACY_PERIODIC,new android.content.ComponentName(context(),BackupJob.class)).setPeriodic(24L*60*60*1000).build());
        BackupJob.schedule(context());BackupJob.configure(context());assertNull(jobs.getPendingJob(BackupJob.NOW));assertNull(jobs.getPendingJob(BackupJob.LEGACY_PERIODIC));
        context().getSharedPreferences("settings",0).edit().putString("backupTree","content://provider/tree/folder").commit();BackupJob.schedule(context());assertNull(jobs.getPendingJob(BackupJob.NOW));
        context().getSharedPreferences("settings",0).edit().putBoolean("autoBackup",true).commit();BackupJob.schedule(context());BackupJob.configure(context());
        assertNotNull(jobs.getPendingJob(BackupJob.NOW));assertNull(jobs.getPendingJob(BackupJob.NOW).getRequiredNetwork());assertNull(jobs.getPendingJob(BackupJob.LEGACY_PERIODIC));
        context().getSharedPreferences("settings",0).edit().putBoolean("autoBackup",false).commit();BackupJob.configure(context());assertNull(jobs.getPendingJob(BackupJob.NOW));assertNull(jobs.getPendingJob(BackupJob.LEGACY_PERIODIC));
    }
    @Test public void fileNameOverwritesByDefaultAndCanKeepDailyVersions(){LocalDate day=LocalDate.of(2026,9,9);assertEquals("Scale2Garmin-Backup.csv",BackupJob.fileName(day,false));assertEquals("Scale2Garmin-Backup-2026-09-09.csv",BackupJob.fileName(day,true));}
}
