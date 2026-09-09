package de.scalebridge;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class ChartViewportTest {
    @Test public void zoomPreservesFocusAndPanPreservesSpan(){
        ChartViewport v=new ChartViewport(0,10000,2000,8000);v.transform(.25,.25,2);
        assertEquals(3000,v.span(),.001);assertEquals(3500,v.left()+v.span()*.25,.001);
        double before=v.left();v.transform(.5,.6,1);assertEquals(before-300,v.left(),.001);assertEquals(3000,v.span(),.001);
        v.reset();assertEquals(2000,v.left(),0);assertEquals(8000,v.right(),0);
    }
    @Test public void boundsPreventInvertedOrUnboundedWindows(){
        ChartViewport v=new ChartViewport(0,10000,2000,8000);v.transform(.5,.5,100000);assertEquals(60,v.span(),0);
        v.transform(.5,-1000,1);assertEquals(10000,v.right(),0);assertEquals(60,v.span(),0);
        v.transform(.5,1000,1);assertEquals(0,v.left(),0);v.transform(.5,.5,.0001);assertEquals(10000,v.span(),0);
        v.set(Double.NaN,30);assertEquals(10000,v.span(),0);
    }
    @Test public void visibleAggregationSkipsMissingValuesWithoutLosingOriginalIndices(){
        List<Store.Row> rows=new ArrayList<>();double[] fat={10,Double.NaN,30,40};
        for(int i=0;i<4;i++)rows.add(new Store.Row(new Measurement("A",100+i,1,80,fat[i],50,30,3,1700),"pending",""));
        long epoch=Bs444Protocol.EPOCH;
        var g=ChartData.visibleGroups(rows,ChartData.Metric.FAT,epoch+100,epoch+103,1);
        assertEquals(1,g.size());assertEquals(3,g.get(0).count());assertEquals(80.0/3,g.get(0).mean(),.0001);assertEquals(0,g.get(0).first());assertEquals(3,g.get(0).last());
        g=ChartData.visibleGroups(rows,ChartData.Metric.FAT,epoch+101,epoch+102,300);
        assertEquals(1,g.size());assertEquals(2,g.get(0).first());assertEquals(30,g.get(0).mean(),0);
        assertTrue(ChartData.visibleGroups(rows,ChartData.Metric.FAT,epoch+104,epoch+110,300).isEmpty());
    }
}
