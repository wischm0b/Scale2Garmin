package de.scalebridge;

import java.time.*;
import java.util.*;

/** Local calendar ranges and display-only averages; original rows remain intact. */
public final class ChartData {
    public enum Metric {
        WEIGHT("Gewicht", "kg"), FAT("Körperfett", "%"), MUSCLE("Muskelanteil", "%"),
        WATER("Körperwasser", "%"), BONE("Knochenmasse", "%"), MUSCLE_KG("Muskelmasse¹", "kg"), BASAL("Grundumsatz", "kcal");
        public final String label, unit;
        Metric(String label,String unit){this.label=label;this.unit=unit;}
        public double value(Measurement m){return switch(this){case WEIGHT->m.weight;case FAT->m.fat;case MUSCLE->m.musclePercent;case WATER->m.water;case BONE->Double.isFinite(m.weight)&&m.weight>0?100*m.bone/m.weight:Double.NaN;case MUSCLE_KG->m.muscleKg();case BASAL->m.basal;};}
    }
    public static final String[] PERIODS={"7 Tage","30 Tage","90 Tage","1 Jahr","Alles"};
    public record Group(int first,int last,long timestamp,double mean,int count){
        public Group(int first,int last,long timestamp,double mean){this(first,last,timestamp,mean,last-first+1);}
    }
    /** Extrema of the visible line, including interpolated crossings at both viewport edges. */
    public static double[] visibleBounds(List<Group> groups,double left,double right){
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(int i=0;i<groups.size();i++){
            Group g=groups.get(i);
            if(g.timestamp()>=left&&g.timestamp()<=right){min=Math.min(min,g.mean());max=Math.max(max,g.mean());}
            if(i==0)continue;Group previous=groups.get(i-1);
            if(g.timestamp()<=previous.timestamp()||g.timestamp()<left||previous.timestamp()>right)continue;
            double a=Math.max(left,previous.timestamp()),b=Math.min(right,g.timestamp());
            double va=previous.mean()+(g.mean()-previous.mean())*(a-previous.timestamp())/(g.timestamp()-previous.timestamp());
            double vb=previous.mean()+(g.mean()-previous.mean())*(b-previous.timestamp())/(g.timestamp()-previous.timestamp());
            min=Math.min(min,Math.min(va,vb));max=Math.max(max,Math.max(va,vb));
        }
        return new double[]{min,max};
    }
    /** Indices refer to the original history, even when a metric is missing or off-screen. */
    public static List<Group> visibleGroups(List<Store.Row> rows,Metric metric,double left,double right,float widthDp){
        List<Group> result=new ArrayList<>();int first=-1,last=-1,count=0;double sum=0,time=0;long firstTime=0;
        for(int i=0;i<rows.size();i++){
            Measurement m=rows.get(i).measurement();double value=metric.value(m);
            if(m.unixSeconds<left||m.unixSeconds>right||!Double.isFinite(value))continue;
            if(count>0&&(m.unixSeconds-firstTime)/Math.max(1,right-left)*widthDp>=12){
                result.add(new Group(first,last,Math.round(time/count),sum/count,count));count=0;sum=0;time=0;
            }
            if(count==0){first=i;firstTime=m.unixSeconds;}last=i;count++;sum+=value;time+=m.unixSeconds;
        }
        if(count>0)result.add(new Group(first,last,Math.round(time/count),sum/count,count));return result;
    }
    /** Anchor aggregation to history, not the viewport; keep one group on either side for clipped lines. */
    public static List<Group> lineGroups(List<Store.Row> rows,Metric metric,double left,double right,float widthDp){
        List<Group> all=new ArrayList<>();int first=-1,last=-1,count=0;double sum=0,time=0;long anchor=0;
        double gap=12*Math.max(1,right-left)/Math.max(1,widthDp);
        for(int i=0;i<rows.size();i++){
            Measurement m=rows.get(i).measurement();double value=metric.value(m);if(!Double.isFinite(value))continue;
            if(count>0&&m.unixSeconds-anchor>=gap){all.add(new Group(first,last,Math.round(time/count),sum/count,count));count=0;sum=0;time=0;}
            if(count==0){first=i;anchor=m.unixSeconds;}last=i;count++;sum+=value;time+=m.unixSeconds;
        }
        if(count>0)all.add(new Group(first,last,Math.round(time/count),sum/count,count));
        int begin=0,end=all.size();while(begin<end&&all.get(begin).timestamp()<left)begin++;
        int stop=begin;while(stop<end&&all.get(stop).timestamp()<=right)stop++;
        return new ArrayList<>(all.subList(Math.max(0,begin-1),Math.min(end,stop+1)));
    }
    public static List<Group> aggregate(List<Store.Row> rows,Metric metric,long start,long end,float widthDp){
        List<Group> groups=new ArrayList<>();if(rows.isEmpty())return groups;
        long firstTime=rows.get(0).measurement().unixSeconds,lastTime=rows.get(rows.size()-1).measurement().unixSeconds;
        long left=start>0?Math.min(start,firstTime):firstTime,right=Math.max(end,lastTime);
        double span=Math.max(1,right-left),minimumGap=12;int first=0;double sum=0,timeSum=0;
        for(int i=0;i<rows.size();i++){
            Measurement m=rows.get(i).measurement();
            // Group samples closer than 12 dp to the group's first sample. This avoids an
            // endless chain of neighbours collapsing an entire densely sampled history.
            if(i>first&&(m.unixSeconds-rows.get(first).measurement().unixSeconds)/span*widthDp>=minimumGap){
                groups.add(new Group(first,i-1,left+Math.round(timeSum/(i-first)),sum/(i-first)));first=i;sum=0;timeSum=0;
            }
            sum+=metric.value(m);timeSum+=m.unixSeconds-left;
        }
        groups.add(new Group(first,rows.size()-1,left+Math.round(timeSum/(rows.size()-first)),sum/(rows.size()-first)));return groups;
    }
    public static long start(int period,Instant now,ZoneId zone){
        LocalDate today=now.atZone(zone).toLocalDate();
        return switch(period){
            case 0->today.minusDays(6).atStartOfDay(zone).toEpochSecond();
            case 1->today.minusDays(29).atStartOfDay(zone).toEpochSecond();
            case 2->today.minusDays(89).atStartOfDay(zone).toEpochSecond();
            case 3->today.minusYears(1).plusDays(1).atStartOfDay(zone).toEpochSecond();
            default->0;
        };
    }
    public static List<Store.Row> valid(List<Store.Row> rows,Metric metric){
        List<Store.Row> result=new ArrayList<>();for(Store.Row row:rows)if(Double.isFinite(metric.value(row.measurement())))result.add(row);return result;
    }
}
