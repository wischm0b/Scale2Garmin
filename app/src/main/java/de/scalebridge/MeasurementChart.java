package de.scalebridge;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.IntConsumer;

/** Multiple metric curves sharing an independently zoomable time axis. */
public final class MeasurementChart extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Store.Row> rows;
    private final List<ChartData.Metric> metrics;
    private ChartData.Metric selectedMetric;
    private final int accent,muted,grid,surface;
    private final IntConsumer onSelect;
    private final RectF plot=new RectF();
    private final ChartViewport viewport;
    private Runnable windowListener=()->{};
    private int selected;
    private double targetWeight=Double.NaN;
    // Primary series coordinates remain available to accessibility/UI verification.
    private float[] xs,ys;
    private List<ChartData.Group> groups=List.of();
    private final List<Series> series=new ArrayList<>();
    private final Map<ChartData.Metric,TrendAnalysis.Result> trends=new EnumMap<>(ChartData.Metric.class);
    private float downX,downY,previousFocus,previousSpan;
    private boolean multiTouch;
    private int pointerA=-1,pointerB=-1;
    private static final class Series {
        final ChartData.Metric metric;final int color;final List<ChartData.Group> groups;
        final float[] xs,ys;final TrendAnalysis.Result trend;double min,max;
        Series(ChartData.Metric m,int c,List<ChartData.Group> g,TrendAnalysis.Result t){metric=m;color=c;groups=g;trend=t;xs=new float[g.size()];ys=new float[g.size()];}
    }
    public MeasurementChart(Context c,List<Store.Row> rows,ChartData.Metric metric,int selected,long start,long end,int accent,int muted,int grid,int surface,IntConsumer onSelect){
        this(c,rows,List.of(metric),selected,start,end,accent,muted,grid,surface,onSelect);
    }
    public MeasurementChart(Context c,List<Store.Row> rows,List<ChartData.Metric> metrics,int selected,long start,long end,int accent,int muted,int grid,int surface,IntConsumer onSelect){
        super(c);this.rows=rows;this.metrics=List.copyOf(metrics);this.selectedMetric=metrics.get(0);this.selected=selected;
        this.accent=accent;this.muted=muted;this.grid=grid;this.surface=surface;this.onSelect=onSelect;
        for(ChartData.Metric m:metrics)trends.put(m,TrendAnalysis.analyze(rows,m,ZoneId.systemDefault()));
        double first=rows.isEmpty()?end-86400:rows.get(0).measurement().unixSeconds;
        double last=rows.isEmpty()?end:rows.get(rows.size()-1).measurement().unixSeconds;
        double homeLeft=start>0?start:first,homeRight=Math.max(end,last);
        if(homeRight-homeLeft<60){homeLeft-=43200;homeRight+=43200;}
        viewport=new ChartViewport(Math.min(first,homeLeft),homeRight,homeLeft,homeRight);
        setFocusable(true);setClickable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(Tr.text(c,"Diagramm: Mit zwei Fingern zoomen oder verschieben. Messpunkt antippen für Details."));
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    public ChartViewport viewport(){return viewport;}
    public void onWindowChanged(Runnable listener){windowListener=listener;}
    public void restoreWindow(double left,double right){viewport.set(left,right);invalidate();}
    public void resetWindow(){viewport.reset();rebuild();invalidate();windowListener.run();}
    public ChartData.Metric selectedMetric(){return selectedMetric;}
    public void setSelectedMetric(ChartData.Metric metric){if(metrics.contains(metric))selectedMetric=metric;}
    public void setTargetWeight(double target){targetWeight=target;invalidate();}
    public void select(int index){selected=index;invalidate();}
    public ChartData.Group groupFor(int index){
        for(Series s:series)if(s.metric==selectedMetric)for(ChartData.Group g:s.groups)
            if(index>=g.first()&&index<=g.last()&&Double.isFinite(s.metric.value(rows.get(index).measurement())))return g;
        return null;
    }
    private String value(double n,ChartData.Metric m){return String.format(Tr.locale(getContext()),m==ChartData.Metric.BASAL?"%.0f":"%.1f",n);}
    private int color(ChartData.Metric m){
        boolean dark=Color.red(surface)<128;
        return switch(m){case WEIGHT->accent;case FAT->Color.parseColor(dark?"#74C8FF":"#006DB0");case MUSCLE->Color.parseColor(dark?"#FFB66E":"#A65100");case WATER->Color.parseColor(dark?"#BCACFF":"#7043B1");case BONE->Color.parseColor(dark?"#E4D374":"#7D6900");case MUSCLE_KG->Color.parseColor(dark?"#FFA5CF":"#AA316A");case BASAL->Color.parseColor(dark?"#E7EBF0":"#425668");};
    }
    private void rebuild(){
        series.clear();
        boolean hasKg=metrics.contains(ChartData.Metric.WEIGHT),hasPercent=metrics.stream().anyMatch(m->m!=ChartData.Metric.WEIGHT);
        plot.set(dp(57),dp(30+metrics.size()*21),getWidth()-dp(hasKg&&hasPercent?57:14),getHeight()-dp(32));
        // Fixed bounds over this history keep vertical scales stable during a time-axis gesture.
        double kgMin=Double.POSITIVE_INFINITY,kgMax=Double.NEGATIVE_INFINITY,pctMin=Double.POSITIVE_INFINITY,pctMax=Double.NEGATIVE_INFINITY;
        for(ChartData.Metric m:metrics)for(Store.Row row:rows){double v=m.value(row.measurement());if(!Double.isFinite(v))continue;
            if(m==ChartData.Metric.WEIGHT){kgMin=Math.min(kgMin,v);kgMax=Math.max(kgMax,v);}else{pctMin=Math.min(pctMin,v);pctMax=Math.max(pctMax,v);}}
        if(!Double.isFinite(kgMin)){kgMin=0;kgMax=1;}if(!Double.isFinite(pctMin)){pctMin=0;pctMax=1;}
        double kgMargin=Math.max(.4,(kgMax-kgMin)*.18),pctMargin=Math.max(.4,(pctMax-pctMin)*.18);
        for(ChartData.Metric m:metrics){
            Series s=new Series(m,color(m),ChartData.lineGroups(rows,m,viewport.left(),viewport.right(),Math.max(1,plot.width()/dp(1))),trends.get(m));
            boolean kg=m==ChartData.Metric.WEIGHT;s.min=kg?Math.max(0,kgMin-kgMargin):Math.max(0,pctMin-pctMargin);s.max=kg?kgMax+kgMargin:Math.min(100,pctMax+pctMargin);
            if(s.max<=s.min)s.max=s.min+1;
            for(int i=0;i<s.groups.size();i++){
                ChartData.Group g=s.groups.get(i);s.xs[i]=plot.left+(float)((g.timestamp()-viewport.left())/viewport.span())*plot.width();
                s.ys[i]=plot.bottom-(float)((g.mean()-s.min)/(s.max-s.min))*plot.height();
            }
            series.add(s);
        }
        Series first=series.get(0);groups=first.groups;xs=first.xs;ys=first.ys;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);rebuild();paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(dp(11));paint.setStrokeWidth(dp(1));paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
        for(int n=0;n<series.size();n++){
            Series s=series.get(n);float y=dp(15+n*21);paint.setColor(s.color);paint.setTextAlign(Paint.Align.LEFT);canvas.drawCircle(dp(5),y-dp(3),dp(3),paint);
            String label=Tr.text(getContext(),s.metric.label)+" · "+s.metric.unit;

            canvas.drawText(label,dp(15),y,paint);
        }
        for(int i=0;i<4;i++){
            float y=plot.bottom-i*plot.height()/3;paint.setColor(grid);canvas.drawLine(plot.left,y,plot.right,y,paint);
            Series kg=series.stream().filter(s->s.metric==ChartData.Metric.WEIGHT).findFirst().orElse(null);
            Series pct=series.stream().filter(s->s.metric!=ChartData.Metric.WEIGHT).findFirst().orElse(null);
            Series primary=kg!=null?kg:pct;
            paint.setColor(muted);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(value(primary.min+(primary.max-primary.min)*i/3,primary.metric),plot.left-dp(8),y+dp(4),paint);
            if(i==3)canvas.drawText(primary.metric.unit,plot.left-dp(8),plot.top-dp(12),paint);
            if(kg!=null&&pct!=null){paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(value(pct.min+(pct.max-pct.min)*i/3,pct.metric),plot.right+dp(8),y+dp(4),paint);if(i==3)canvas.drawText("%",plot.right+dp(8),plot.top-dp(12),paint);}
        }
        String pattern=viewport.span()<86400?"HH:mm":viewport.span()>86400*370?"MM.yy":"dd.MM.";
        DateTimeFormatter date=DateTimeFormatter.ofPattern(pattern,Tr.locale(getContext())).withZone(ZoneId.systemDefault());paint.setColor(muted);
        for(int i=0;i<3;i++){paint.setTextAlign(i==0?Paint.Align.LEFT:i==2?Paint.Align.RIGHT:Paint.Align.CENTER);canvas.drawText(date.format(Instant.ofEpochSecond((long)(viewport.left()+viewport.span()*i/2))),plot.left+plot.width()*i/2,plot.bottom+dp(25),paint);}
        boolean empty=series.stream().allMatch(s->s.groups.isEmpty());
        if(empty){paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(Tr.text(getContext(),"Keine Messwerte in diesem Ausschnitt"),plot.centerX(),plot.centerY(),paint);return;}
        canvas.save();canvas.clipRect(plot.left,plot.top-dp(8),plot.right,plot.bottom+dp(8));
        Series weight=series.stream().filter(s->s.metric==ChartData.Metric.WEIGHT).findFirst().orElse(null);if(weight!=null&&Double.isFinite(targetWeight)&&targetWeight>=weight.min&&targetWeight<=weight.max){
            float y=plot.bottom-(float)((targetWeight-weight.min)/(weight.max-weight.min))*plot.height();paint.setColor(weight.color);paint.setAlpha(135);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.5f));paint.setPathEffect(new DashPathEffect(new float[]{dp(2),dp(5)},0));canvas.drawLine(plot.left,y,plot.right,y,paint);paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
        }
        for(Series s:series){
            if(s.groups.isEmpty())continue;Path line=new Path();for(int i=0;i<s.xs.length;i++){if(i==0)line.moveTo(s.xs[i],s.ys[i]);else line.lineTo(s.xs[i],s.ys[i]);}
            paint.setColor(s.color);
            if(series.size()==1){Path fill=new Path(line);fill.lineTo(s.xs[s.xs.length-1],plot.bottom);fill.lineTo(s.xs[0],plot.bottom);fill.close();paint.setAlpha(18);canvas.drawPath(fill,paint);paint.setAlpha(255);}
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2.2f));paint.setStrokeJoin(Paint.Join.ROUND);canvas.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);
            if(s.trend!=null){long left=Math.max((long)viewport.left(),s.trend.firstTimestamp()),right=Math.min((long)viewport.right(),s.trend.lastTimestamp());if(right>left){
                float x1=plot.left+(float)((left-viewport.left())/viewport.span())*plot.width(),x2=plot.left+(float)((right-viewport.left())/viewport.span())*plot.width();
                float y1=plot.bottom-(float)((s.trend.valueAt(left)-s.min)/(s.max-s.min))*plot.height(),y2=plot.bottom-(float)((s.trend.valueAt(right)-s.min)/(s.max-s.min))*plot.height();
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setPathEffect(new DashPathEffect(new float[]{dp(8),dp(5)},0));paint.setAlpha(210);canvas.drawLine(x1,y1,x2,y2,paint);paint.setPathEffect(null);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
            }}
            for(int i=0;i<s.xs.length;i++){
                ChartData.Group g=s.groups.get(i);boolean chosen=s.metric==selectedMetric&&selected>=g.first()&&selected<=g.last();
                if(chosen){paint.setAlpha(95);paint.setStrokeWidth(dp(1));canvas.drawLine(s.xs[i],plot.top,s.xs[i],plot.bottom,paint);paint.setAlpha(255);paint.setColor(surface);canvas.drawCircle(s.xs[i],s.ys[i],dp(7),paint);paint.setColor(s.color);}
                canvas.drawCircle(s.xs[i],s.ys[i],dp(chosen?4.5f:3.2f),paint);
            }
        }
        canvas.restore();
    }
    private int[] hit(float x,float y){
        if(!plot.contains(x,y))return null;double best=dp(32)*dp(32);int[] result=null;
        for(int n=0;n<series.size();n++){Series s=series.get(n);for(int i=0;i<s.xs.length;i++){if(s.xs[i]<plot.left||s.xs[i]>plot.right)continue;double d=Math.pow(s.xs[i]-x,2)+Math.pow(s.ys[i]-y,2);if(d<=best){best=d;result=new int[]{n,i};}}}return result;
    }
    int nearest(float x,float y){int[] h=hit(x,y);return h==null?-1:h[1];}
    private void intercept(boolean block){if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(block);}
    private void beginTwo(MotionEvent e){
        multiTouch=true;pointerA=e.getPointerId(0);pointerB=e.getPointerId(1);previousFocus=(e.getX(0)+e.getX(1))/2;previousSpan=Math.abs(e.getX(0)-e.getX(1));intercept(true);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN->{downX=e.getX();downY=e.getY();multiTouch=false;pointerA=-1;pointerB=-1;return true;}
            case MotionEvent.ACTION_POINTER_DOWN->{if(pointerA<0&&e.getPointerCount()>=2)beginTwo(e);return true;}
            case MotionEvent.ACTION_MOVE->{
                if(e.getPointerCount()>=2){if(pointerA<0)beginTwo(e);int a=e.findPointerIndex(pointerA),b=e.findPointerIndex(pointerB);if(a<0||b<0){beginTwo(e);return true;}
                    float focus=(e.getX(a)+e.getX(b))/2,span=Math.abs(e.getX(a)-e.getX(b));
                    double zoom=previousSpan>=dp(20)&&span>=dp(20)?span/previousSpan:1;
                    float width=Math.max(1,plot.width());viewport.transform((previousFocus-plot.left)/width,(focus-plot.left)/width,zoom);
                    previousFocus=focus;previousSpan=span;rebuild();invalidate();windowListener.run();
                }return true;
            }
            case MotionEvent.ACTION_POINTER_UP->{pointerA=-1;pointerB=-1;return true;}
            case MotionEvent.ACTION_UP->{intercept(false);if(!multiTouch&&Math.hypot(e.getX()-downX,e.getY()-downY)<dp(24)){
                int[] h=hit(e.getX(),e.getY());if(h!=null){Series s=series.get(h[0]);ChartData.Group g=s.groups.get(h[1]);selected=g.last();selectedMetric=s.metric;onSelect.accept(selected);announceForAccessibility(value(g.mean(),s.metric)+" "+s.metric.unit);invalidate();performClick();}
            }return true;}
            case MotionEvent.ACTION_CANCEL->{intercept(false);multiTouch=true;pointerA=-1;pointerB=-1;return true;}
            default->{return true;}
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int next=Math.max(0,Math.min(rows.size()-1,selected+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)));if(!rows.isEmpty()){onSelect.accept(next);select(next);}return true;}return super.onKeyDown(key,event);}
}
