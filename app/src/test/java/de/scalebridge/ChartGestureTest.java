package de.scalebridge;

import android.view.*;
import android.graphics.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk={31,35}) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ChartGestureTest {
    @Test public void verticalScaleFollowsTheWindowWithASmoothTransition(){
        var context=RuntimeEnvironment.getApplication();List<Store.Row> rows=new ArrayList<>();
        for(int i=0;i<30;i++)rows.add(new Store.Row(new Measurement("A",i*86400,1,i==0?150:80,20,50,30,3,1700),"sent",""));
        MeasurementChart chart=new MeasurementChart(context,rows,ChartData.Metric.WEIGHT,29,Bs444Protocol.EPOCH,Bs444Protocol.EPOCH+29*86400,Color.GREEN,Color.GRAY,Color.DKGRAY,Color.BLACK,index->{});
        chart.layout(0,0,1000,600);Bitmap bitmap=Bitmap.createBitmap(1000,600,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);chart.draw(canvas);
        double[] original=((double[])org.robolectric.util.ReflectionHelpers.getField(chart,"axisBounds")).clone();assertTrue(original[1]>100);
        chart.restoreWindow(Bs444Protocol.EPOCH+20*86400,Bs444Protocol.EPOCH+29*86400);chart.draw(canvas);
        double[] immediate=org.robolectric.util.ReflectionHelpers.getField(chart,"axisBounds");assertTrue(immediate[1]>81);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1));chart.draw(canvas);
        double[] settled=org.robolectric.util.ReflectionHelpers.getField(chart,"axisBounds");assertEquals(79.6,settled[0],.02);assertEquals(80.4,settled[1],.02);bitmap.recycle();
    }
    private MotionEvent event(int action,float... points){
        int count=points.length/2;MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=points[i*2];coords[i].y=points[i*2+1];coords[i].pressure=1;coords[i].size=1;}
        return MotionEvent.obtain(0,10,action,count,props,coords,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);
    }
    @Test public void twoFingerZoomAndPanNeverSelectOrRefresh(){
        var context=RuntimeEnvironment.getApplication();List<Store.Row> rows=new ArrayList<>();
        for(int i=0;i<30;i++)rows.add(new Store.Row(new Measurement("A",i*86400,1,80+i,20+i*.1,50,30,3,1700),"pending",""));
        int[] clicks={0},refreshes={0};MeasurementChart chart=new MeasurementChart(context,rows,List.of(ChartData.Metric.WEIGHT,ChartData.Metric.FAT),29,Bs444Protocol.EPOCH,Bs444Protocol.EPOCH+29*86400,Color.GREEN,Color.GRAY,Color.DKGRAY,Color.BLACK,index->clicks[0]++);
        RefreshScrollView parent=new RefreshScrollView(context,()->refreshes[0]++);parent.addView(chart);chart.layout(0,0,1000,600);chart.draw(new Canvas(Bitmap.createBitmap(1000,600,Bitmap.Config.ARGB_8888)));
        double initial=chart.viewport().span();chart.onTouchEvent(event(MotionEvent.ACTION_DOWN,350,250));
        chart.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),350,250,650,250));
        chart.onTouchEvent(event(MotionEvent.ACTION_MOVE,200,250,800,250));assertEquals(initial/2,chart.viewport().span(),1);
        double left=chart.viewport().left();chart.onTouchEvent(event(MotionEvent.ACTION_MOVE,250,250,850,250));assertTrue(chart.viewport().left()<left);assertEquals(initial/2,chart.viewport().span(),1);
        chart.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP|(1<<8),250,250,850,250));chart.onTouchEvent(event(MotionEvent.ACTION_UP,250,250));assertEquals(0,clicks[0]);assertEquals(0,refreshes[0]);
        chart.resetWindow();assertEquals(initial,chart.viewport().span(),1);
    }
    @Test public void refreshRejectsMultiTouchEvenAfterPullStarted(){
        int[] refreshes={0};RefreshScrollView parent=new RefreshScrollView(RuntimeEnvironment.getApplication(),()->refreshes[0]++);
        parent.onInterceptTouchEvent(event(MotionEvent.ACTION_DOWN,20,20));parent.onInterceptTouchEvent(event(MotionEvent.ACTION_MOVE,20,400));parent.onTouchEvent(event(MotionEvent.ACTION_MOVE,20,400));
        parent.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN|(1<<8),20,400,100,400));parent.onTouchEvent(event(MotionEvent.ACTION_UP,20,400));assertEquals(0,refreshes[0]);
    }
    @Test public void realViewDispatchKeepsDownwardGestureUntilRefreshThreshold(){
        int[] refreshes={0};var context=RuntimeEnvironment.getApplication();RefreshScrollView parent=new RefreshScrollView(context,()->refreshes[0]++);
        android.widget.LinearLayout content=new android.widget.LinearLayout(context);content.setOrientation(android.widget.LinearLayout.VERTICAL);View child=new View(context){@Override public boolean onTouchEvent(MotionEvent e){return true;}};content.addView(child,new android.widget.LinearLayout.LayoutParams(1000,1600));parent.addView(content);parent.layout(0,0,1000,600);content.layout(0,0,1000,1600);child.layout(0,0,1000,1600);
        float density=context.getResources().getDisplayMetrics().density;assertTrue(parent.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN,400,80)));assertTrue(parent.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE,400,80+20*density)));assertTrue(parent.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE,400,80+90*density)));assertTrue(parent.dispatchTouchEvent(event(MotionEvent.ACTION_UP,400,80+90*density)));assertEquals(1,refreshes[0]);assertEquals(0,content.getTranslationY(),0);
    }
    @Test public void upwardScrollAndPullAwayFromTopDoNotRefresh(){
        int[] refreshes={0};var context=RuntimeEnvironment.getApplication();RefreshScrollView parent=new RefreshScrollView(context,()->refreshes[0]++);View child=new View(context);parent.addView(child);parent.layout(0,0,1000,500);child.layout(0,0,1000,1500);parent.scrollTo(0,100);
        parent.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN,300,300));parent.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE,300,450));parent.dispatchTouchEvent(event(MotionEvent.ACTION_UP,300,450));assertEquals(0,refreshes[0]);
    }
    @Test public void pullWorksOnClickableLabelsPlainLabelsAndEmptySpace(){
        var context=RuntimeEnvironment.getApplication();float density=context.getResources().getDisplayMetrics().density;
        for(boolean clickable:new boolean[]{false,true}){
            int[] refreshes={0},clicks={0};RefreshScrollView parent=new RefreshScrollView(context,()->refreshes[0]++);
            android.widget.FrameLayout content=new android.widget.FrameLayout(context);android.widget.TextView label=new android.widget.TextView(context);label.setText(clickable?"Garmin":"Scale2Garmin");
            if(clickable)label.setOnClickListener(v->clicks[0]++);
            content.addView(label,new android.widget.FrameLayout.LayoutParams(500,300));parent.addView(content,new android.widget.ScrollView.LayoutParams(1000,1600));
            parent.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY));parent.layout(0,0,1000,600);
            for(float x:new float[]{200,800}){
                parent.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN,x,80));
                parent.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE,x+1,79));
                parent.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE,x,80+20*density));
                parent.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE,x,80+90*density));
                parent.dispatchTouchEvent(event(MotionEvent.ACTION_UP,x,80+90*density));
            }
            assertEquals("clickable="+clickable,2,refreshes[0]);assertEquals(0,clicks[0]);assertEquals(0,content.getTranslationY(),0);
        }
    }
}
