package de.scalebridge;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.*;
import java.util.*;
public class ChartDataTest {
    @Test public void calendarPeriodsHandleDaylightSaving(){ZoneId zone=ZoneId.of("Europe/Berlin");Instant now=ZonedDateTime.of(2026,3,30,10,0,0,0,zone).toInstant();assertEquals(LocalDate.of(2026,3,24).atStartOfDay(zone).toEpochSecond(),ChartData.start(0,now,zone));assertEquals(0,ChartData.start(4,now,zone));}
    @Test public void metricsNeverTurnMissingValuesIntoZero(){Measurement m=new Measurement("A",1,1,80,Double.NaN,50,40,3,1700);List<Store.Row> rows=List.of(new Store.Row(m,"pending",""));assertTrue(ChartData.valid(rows,ChartData.Metric.FAT).isEmpty());assertEquals(1,ChartData.valid(rows,ChartData.Metric.WEIGHT).size());assertEquals(32,ChartData.Metric.MUSCLE_KG.value(m),0);}
    @Test public void closeSamplesAreAveragedWithoutChangingOriginals(){
        List<Store.Row> rows=new ArrayList<>();for(int i=0;i<4;i++)rows.add(new Store.Row(new Measurement("A",i<3?100+i:1000,1,80+i*2,20,50,40,3,1700),"sent",""));
        long start=Bs444Protocol.EPOCH+100,end=Bs444Protocol.EPOCH+1000;
        var groups=ChartData.aggregate(rows,ChartData.Metric.WEIGHT,start,end,300);
        assertEquals(2,groups.size());assertEquals(3,groups.get(0).count());assertEquals(82,groups.get(0).mean(),0);assertEquals(start+1,groups.get(0).timestamp());assertEquals(86,groups.get(1).mean(),0);assertEquals(4,rows.size());assertEquals(80,rows.get(0).measurement().weight,0);
        assertEquals(4,ChartData.aggregate(rows,ChartData.Metric.WEIGHT,start,end,20000).size());
    }
    @Test public void linesKeepNeighboursAcrossEmptyWindowsAndIgnoreMissingMetrics(){
        List<Store.Row> rows=new ArrayList<>();
        for(int i=0;i<4;i++)rows.add(new Store.Row(new Measurement("A",100+i*100,1,80+i,i==1?Double.NaN:20+i,50,40,3,1700),"sent",""));
        long e=Bs444Protocol.EPOCH;
        var groups=ChartData.lineGroups(rows,ChartData.Metric.FAT,e+180,e+220,300);
        assertEquals(2,groups.size());assertEquals(0,groups.get(0).first());assertEquals(2,groups.get(1).first());
        assertEquals(groups,ChartData.lineGroups(rows,ChartData.Metric.FAT,e+181,e+221,300));
    }
    @Test public void panningPreservesDenseGroupAverages(){
        List<Store.Row> rows=new ArrayList<>();
        for(int i=0;i<8;i++)rows.add(new Store.Row(new Measurement("A",100+i,1,80+i,20,50,40,3,1700),"sent",""));
        long e=Bs444Protocol.EPOCH;
        var a=ChartData.lineGroups(rows,ChartData.Metric.WEIGHT,e+100,e+200,300);
        var b=ChartData.lineGroups(rows,ChartData.Metric.WEIGHT,e+102,e+202,300);
        assertEquals(a,b);assertEquals(81.5,a.get(0).mean(),0);
    }
    @Test public void bonePercentageUsesWeightAndPreservesStoredKilograms(){
        Measurement m=new Measurement("A",1,1,80,20,50,40,3,1700);
        assertEquals(3.75,ChartData.Metric.BONE.value(m),0);assertEquals(3,m.bone,0);
        assertTrue(Double.isNaN(ChartData.Metric.BONE.value(new Measurement("A",1,1,0,20,50,40,3,1700))));
    }
    @Test public void robustTrendUsesDailyMeansAndResistsOneOutlier(){
        List<Store.Row> rows=new ArrayList<>();long start=Instant.parse("2026-01-01T08:00:00Z").getEpochSecond();
        for(int day=0;day<60;day++){double value=90-day*.1+(day==25?12:0);for(int repeat=0;repeat<(day==10?5:1);repeat++){long unix=start+day*86400+repeat*60;rows.add(new Store.Row(new Measurement("A",unix-Bs444Protocol.EPOCH,1,value,20,50,40,3,1700),"sent",""));}}
        TrendAnalysis.Result trend=TrendAnalysis.analyze(rows,ChartData.Metric.WEIGHT,ZoneOffset.UTC);assertNotNull(trend);assertEquals(-.1,trend.slopePerDay(),.005);assertEquals(30,trend.days());assertEquals(81.1,trend.projected(),.2);assertEquals(60,trend.sampleDays());
    }
    @Test public void forecastWaitsForEnoughHistory(){
        List<Store.Row> rows=new ArrayList<>();long start=Instant.parse("2026-01-01T08:00:00Z").getEpochSecond();for(int day=0;day<4;day++){long unix=start+day*86400;rows.add(new Store.Row(new Measurement("A",unix-Bs444Protocol.EPOCH,1,80+day,20,50,40,3,1700),"sent",""));}
        TrendAnalysis.Result trend=TrendAnalysis.analyze(rows,ChartData.Metric.WEIGHT,ZoneOffset.UTC);assertNotNull(trend);assertTrue(Double.isNaN(trend.projected()));
    }
    @Test public void shorterUsefulHistoryGetsFourteenDayForecast(){
        List<Store.Row> rows=new ArrayList<>();long start=Instant.parse("2026-01-01T08:00:00Z").getEpochSecond();for(int day=0;day<=15;day+=3){long unix=start+day*86400;rows.add(new Store.Row(new Measurement("A",unix-Bs444Protocol.EPOCH,1,80-day*.05,20,50,40,3,1700),"sent",""));}
        TrendAnalysis.Result trend=TrendAnalysis.analyze(rows,ChartData.Metric.WEIGHT,ZoneOffset.UTC);assertNotNull(trend);assertEquals(14,trend.days());assertTrue(Double.isFinite(trend.projected()));
    }
    @Test public void goalEstimateHandlesDirectionAndStableTrends(){
        List<Store.Row> rows=new ArrayList<>();long start=Instant.parse("2026-01-01T08:00:00Z").getEpochSecond();for(int day=0;day<30;day++){long unix=start+day*86400;rows.add(new Store.Row(new Measurement("A",unix-Bs444Protocol.EPOCH,1,90-day*.1,20,50,40,3,1700),"sent",""));}
        TrendAnalysis.Result falling=TrendAnalysis.analyze(rows,ChartData.Metric.WEIGHT,ZoneOffset.UTC);LocalDate today=LocalDate.of(2026,2,1);TrendAnalysis.Goal goal=TrendAnalysis.goal(falling,85,today);assertEquals(TrendAnalysis.GoalStatus.ESTIMATED,goal.status());assertTrue(goal.days()>=19&&goal.days()<=22);assertEquals(today.plusDays(goal.days()),goal.date());assertEquals(TrendAnalysis.GoalStatus.AWAY,TrendAnalysis.goal(falling,95,today).status());
        rows.clear();for(int day=0;day<30;day++){long unix=start+day*86400;rows.add(new Store.Row(new Measurement("A",unix-Bs444Protocol.EPOCH,1,80,20,50,40,3,1700),"sent",""));}TrendAnalysis.Result flat=TrendAnalysis.analyze(rows,ChartData.Metric.WEIGHT,ZoneOffset.UTC);assertEquals(TrendAnalysis.GoalStatus.STABLE,TrendAnalysis.goal(flat,75,today).status());assertEquals(TrendAnalysis.GoalStatus.REACHED,TrendAnalysis.goal(flat,80,today).status());
    }
}
