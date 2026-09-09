package de.scalebridge;

import java.io.ByteArrayOutputStream;

/** Small FIT encoder using the publicly documented weight_scale profile. */
public final class FitWriter {
    private static void le(ByteArrayOutputStream out,long n,int bytes) { for(int i=0;i<bytes;i++) out.write((int)(n>>(8*i))&255); }
    private static void definition(ByteArrayOutputStream out,int local,int global,int[][] fields) {
        out.write(0x40|local); out.write(0); out.write(0); le(out,global,2); out.write(fields.length);
        for(int[] f:fields) {out.write(f[0]);out.write(f[1]);out.write(f[2]);}
    }
    private static void scaled(ByteArrayOutputStream out,double value,int scale) {
        le(out,Double.isFinite(value)?Math.round(value*scale):65535,2);
    }
    public static byte[] encode(Measurement m,boolean includeMuscle) {
        long timestamp=m.unixSeconds-631065600L;
        if(timestamp<0||timestamp>=0xffffffffL) throw new IllegalArgumentException("FIT-Zeitstempel ungültig");
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        definition(body,0,0,new int[][]{{0,1,0},{1,2,132},{2,2,132},{3,4,140},{4,4,134}});
        body.write(0); body.write(9); // File type WEIGHT.
        le(body,255,2); le(body,1,2); // Development manufacturer, no Garmin device impersonation.
        le(body,Integer.toUnsignedLong(m.address.hashCode())|1,4); le(body,timestamp,4);
        definition(body,1,30,new int[][]{{253,4,134},{0,2,132},{1,2,132},{2,2,132},{4,2,132},{5,2,132},{7,2,132}});
        body.write(1);le(body,timestamp,4);scaled(body,m.weight,100);scaled(body,m.fat,100);
        scaled(body,m.water,100);scaled(body,m.bone,100);scaled(body,includeMuscle?m.muscleKg():Double.NaN,100);scaled(body,m.basal,4);
        ByteArrayOutputStream file=new ByteArrayOutputStream();
        file.write(14);file.write(0x20);le(file,2184,2);le(file,body.size(),4);
        file.write('.');file.write('F');file.write('I');file.write('T');
        le(file,crc(file.toByteArray()),2);byte[] payload=body.toByteArray();file.write(payload,0,payload.length);le(file,crc(file.toByteArray()),2);
        return file.toByteArray();
    }
    public static int crc(byte[] bytes) {
        int crc=0;
        for(byte b:bytes) {crc^=b&255;for(int i=0;i<8;i++) crc=(crc&1)!=0?(crc>>>1)^0xa001:crc>>>1;}
        return crc;
    }
}
