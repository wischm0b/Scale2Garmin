package de.scalebridge;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Geometric icons share the same centered 24-unit canvas, independent of font baselines. */
public final class IconView extends View {
    private final String symbol;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    public IconView(Context context,String symbol,int color){super(context);this.symbol=symbol;ink.setColor(color);ink.setStrokeWidth(1.9f);ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);ink.setStrokeJoin(Paint.Join.ROUND);}
    private void path(Canvas c,float... points){Path p=new Path();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);c.drawPath(p,ink);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float size=Math.min(getWidth(),getHeight())*.55f;c.save();c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
        switch(symbol){
            case "⚙"->{
                Path gear=new Path();
                for(int i=0;i<24;i++){double a=-Math.PI/2+i*Math.PI/12;float radius=(i%3==1)?9.8f:((i%3==2)?7.7f:9.8f);float x=12+(float)Math.cos(a)*radius,y=12+(float)Math.sin(a)*radius;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}
                gear.close();c.drawPath(gear,ink);c.drawCircle(12,12,3.1f,ink);
            }
            case "‹"->path(c,15,5,8,12,15,19);
            case "›"->path(c,9,5,16,12,9,19);
            case "⌃"->path(c,5,15,12,8,19,15);
            case "✓"->path(c,4,12,10,18,20,6);
            case "−"->path(c,5,12,19,12);
            case "↗"->{path(c,5,19,19,5);path(c,9,5,19,5,19,15);}
            default->{
                // Filled arrowhead straddles the arc and points along its tangent.
                c.drawArc(new RectF(4,4,20,20),45,280,false,ink);
                double angle=Math.toRadians(325);
                float nx=(float)Math.cos(angle),ny=(float)Math.sin(angle);
                float x=12+8*nx,y=12+8*ny;
                Path head=new Path();head.moveTo(x+2.8f*nx,y+2.8f*ny);
                head.lineTo(x-3.8f*ny,y+3.8f*nx);
                head.lineTo(x-2.8f*nx,y-2.8f*ny);head.close();
                ink.setStyle(Paint.Style.FILL);c.drawPath(head,ink);ink.setStyle(Paint.Style.STROKE);
            }
        }c.restore();
    }
}
