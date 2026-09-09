package de.scalebridge;

import android.content.Context;
import android.app.job.JobScheduler;
import android.database.sqlite.SQLiteDatabase;
import androidx.health.connect.client.records.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={31,35})
public class HealthSyncTest {
    private Context context(){return RuntimeEnvironment.getApplication();}
    @Before public void clean(){context().deleteDatabase("measurements.db");context().getSharedPreferences("settings",0).edit().clear().commit();}
    @Test public void convertsUnitsAndDoesNotExportMuscleAsLeanMass(){
        Measurement m=new Measurement("AA",1000000,1,80,25,50,40,3,1700);
        var records=HealthSync.records(m,HealthSync.permissions(),0);assertEquals(5,records.size());
        assertEquals(80,((WeightRecord)records.get(0).getRecord()).getWeight().getKilograms(),.0001);
        assertEquals(25,((BodyFatRecord)records.get(1).getRecord()).getPercentage().getValue(),.0001);
        assertEquals(40,((BodyWaterMassRecord)records.get(2).getRecord()).getMass().getKilograms(),.0001);
        assertEquals(3,((BoneMassRecord)records.get(3).getRecord()).getMass().getKilograms(),.0001);
        assertEquals(1700,((BasalMetabolicRateRecord)records.get(4).getRecord()).getBasalMetabolicRate().getKilocaloriesPerDay(),.0001);
        assertEquals(java.time.Instant.ofEpochSecond(m.unixSeconds),((WeightRecord)records.get(0).getRecord()).getTime());
        assertTrue(records.stream().noneMatch(r->r.getRecord() instanceof LeanBodyMassRecord));
    }
    @Test public void partialPermissionsAndMissingValuesProduceOnlyAllowedRecords(){
        Measurement m=new Measurement("AA",1000000,1,80,Double.NaN,Double.NaN,40,Double.NaN,Double.NaN);
        assertEquals(1,HealthSync.records(m,HealthSync.permissions(),0).size());
        assertTrue(HealthSync.records(m,Set.of(),0).isEmpty());
        Set<String> weightOnly=Set.of("android.permission.health.WRITE_WEIGHT");
        assertEquals(1,HealthSync.records(ProtocolTest.sample(),weightOnly,0).size());
        assertTrue(HealthSync.records(m,weightOnly,1).isEmpty());
    }
    @Test public void lostResponseRetriesKeepStableIdsAndOtherUsersAreDistinct(){
        Measurement m=ProtocolTest.sample();var first=HealthSync.records(m,HealthSync.permissions(),0);var retry=HealthSync.records(m,HealthSync.permissions(),0);
        Set<String> ids=new HashSet<>();for(int i=0;i<first.size();i++){
            String id=first.get(i).getRecord().getMetadata().getClientRecordId();ids.add(id);
            assertEquals(id,retry.get(i).getRecord().getMetadata().getClientRecordId());assertEquals(1,first.get(i).getRecord().getMetadata().getClientRecordVersion());
        }
        assertEquals(first.size(),ids.size());
        Measurement other=new Measurement(m.address,m.scaleSeconds,2,m.weight,m.fat,m.water,m.musclePercent,m.bone,m.basal);
        assertNotEquals(first.get(0).getRecord().getMetadata().getClientRecordId(),HealthSync.records(other,HealthSync.permissions(),0).get(0).getRecord().getMetadata().getClientRecordId());
    }
    @Test public void migrationPreservesHistoryAndGarminState(){
        try(SQLiteDatabase old=context().openOrCreateDatabase("measurements.db",0,null)){
            old.execSQL("CREATE TABLE measurements (id TEXT PRIMARY KEY, address TEXT NOT NULL, stamp INTEGER NOT NULL, user INTEGER NOT NULL, weight REAL NOT NULL, fat REAL, water REAL, muscle REAL, bone REAL, basal REAL, state TEXT NOT NULL DEFAULT 'pending', note TEXT NOT NULL DEFAULT '')");
            old.execSQL("INSERT INTO measurements VALUES ('AA:1:100','AA',100,1,80,25,50,40,3,1700,'sent','confirmed')");old.setVersion(1);
        }
        try(Store db=new Store(context())){
            var rows=db.timeline(1,0,Long.MAX_VALUE/2);assertEquals(1,rows.size());String id=rows.get(0).measurement().id();
            assertEquals(0,db.healthMask(id));db.healthSent(id,1);db.healthSent(id,2);assertEquals(3,db.healthMask(id));
            assertEquals("sent",db.rows(1,false).get(0).state());assertEquals("confirmed",db.rows(1,false).get(0).note());
            db.state(id,"pending","");assertEquals(3,db.healthMask(id));
        }
    }
    @Test public void healthJobsAreOptInAndNeedNeitherGarminNorNetwork(){
        JobScheduler jobs=context().getSystemService(JobScheduler.class);jobs.cancelAll();
        HealthJob.schedule(context());assertNull(jobs.getPendingJob(446));assertFalse(HealthSync.run(context()));
        context().getSharedPreferences("settings",0).edit().putBoolean("healthSync",true).putInt("user",1).commit();
        HealthJob.schedule(context());HealthJob.periodic(context());
        assertNotNull(jobs.getPendingJob(446));assertNull(jobs.getPendingJob(446).getRequiredNetwork());assertNotNull(jobs.getPendingJob(447));assertNull(jobs.getPendingJob(444));
        context().getSharedPreferences("settings",0).edit().putBoolean("healthSync",false).commit();HealthJob.periodic(context());assertNull(jobs.getPendingJob(446));assertNull(jobs.getPendingJob(447));
    }
}
