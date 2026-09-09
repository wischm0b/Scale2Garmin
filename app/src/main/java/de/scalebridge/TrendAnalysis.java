package de.scalebridge;

import java.time.*;
import java.util.*;

/** Robust, display-only trend and forecast. Measurements remain unchanged. */
public final class TrendAnalysis {
    private TrendAnalysis(){}
    public enum Quality { HIGH, MEDIUM, LOW }
    public enum GoalStatus { ESTIMATED, REACHED, STABLE, AWAY, TOO_FAR, NOT_ENOUGH_DATA }
    public record Goal(GoalStatus status,int days,LocalDate date){}
    public record Result(ChartData.Metric metric,int days,double slopePerDay,double current,double projected,
                         long firstTimestamp,long lastTimestamp,int sampleDays,double spanDays,double medianError,Quality quality){
        public double valueAt(long timestamp){return current+slopePerDay*(timestamp-lastTimestamp)/86400.0;}
    }
    private record Day(long timestamp,double value){}

    public static Result analyze(List<Store.Row> rows,ChartData.Metric metric,ZoneId zone){
        if(rows==null||rows.isEmpty())return null;
        TreeMap<Long,double[]> byDay=new TreeMap<>();long latest=Long.MIN_VALUE;
        for(Store.Row row:rows){Measurement m=row.measurement();double value=metric.value(m);if(!Double.isFinite(value))continue;latest=Math.max(latest,m.unixSeconds);}
        if(latest==Long.MIN_VALUE)return null;long cutoff=latest-90L*86400;
        for(Store.Row row:rows){Measurement m=row.measurement();double value=metric.value(m);if(!Double.isFinite(value)||m.unixSeconds<cutoff)continue;
            long key=Instant.ofEpochSecond(m.unixSeconds).atZone(zone).toLocalDate().toEpochDay();double[] day=byDay.computeIfAbsent(key,k->new double[3]);day[0]+=value;day[1]++;day[2]+=m.unixSeconds;
        }
        List<Day> points=new ArrayList<>();for(double[] d:byDay.values())points.add(new Day(Math.round(d[2]/d[1]),d[0]/d[1]));
        if(points.size()<3)return null;double span=(points.get(points.size()-1).timestamp-points.get(0).timestamp)/86400.0;if(span<3)return null;
        List<Double> slopes=new ArrayList<>();for(int i=0;i<points.size();i++)for(int j=i+1;j<points.size();j++){
            double dx=(points.get(j).timestamp-points.get(i).timestamp)/86400.0;if(dx>=1)slopes.add((points.get(j).value-points.get(i).value)/dx);
        }
        if(slopes.isEmpty())return null;double slope=median(slopes);long last=points.get(points.size()-1).timestamp;
        List<Double> centers=new ArrayList<>();for(Day p:points)centers.add(p.value-slope*(p.timestamp-last)/86400.0);double current=median(centers);
        List<Double> errors=new ArrayList<>();for(Day p:points)errors.add(Math.abs(p.value-(current+slope*(p.timestamp-last)/86400.0)));double error=median(errors);
        double good=metric==ChartData.Metric.WEIGHT?.55:.8;Quality quality=error<=good?Quality.HIGH:error<=good*2?Quality.MEDIUM:Quality.LOW;
        int horizon=points.size()>=8&&span>=42?30:14;boolean forecast=points.size()>=5&&span>=13;
        double projected=forecast?clamp(metric,current+slope*horizon):Double.NaN;
        return new Result(metric,horizon,slope,current,projected,points.get(0).timestamp,last,points.size(),span,error,quality);
    }
    public static Goal goal(Result trend,double target,LocalDate today){
        if(trend==null||trend.metric()!=ChartData.Metric.WEIGHT||!Double.isFinite(trend.projected()))return new Goal(GoalStatus.NOT_ENOUGH_DATA,0,null);
        double remaining=target-trend.current();if(Math.abs(remaining)<=.1)return new Goal(GoalStatus.REACHED,0,today);
        if(Math.abs(trend.slopePerDay()*7)<.1)return new Goal(GoalStatus.STABLE,0,null);
        if(Math.signum(remaining)!=Math.signum(trend.slopePerDay()))return new Goal(GoalStatus.AWAY,0,null);
        int days=(int)Math.ceil(remaining/trend.slopePerDay());if(days>3650)return new Goal(GoalStatus.TOO_FAR,days,null);
        return new Goal(GoalStatus.ESTIMATED,days,today.plusDays(days));
    }
    private static double clamp(ChartData.Metric metric,double value){return Math.max(0,Math.min(metric==ChartData.Metric.WEIGHT?300:metric==ChartData.Metric.BASAL?10000:100,value));}
    private static double median(List<Double> values){Collections.sort(values);int n=values.size();return n%2==1?values.get(n/2):(values.get(n/2-1)+values.get(n/2))/2;}
}
