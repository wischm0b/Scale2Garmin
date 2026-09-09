package de.scalebridge;

import java.util.*;

/**
 * BS444 protocol implementation informed by openScale (GPL-3.0-or-later).
 * Copyright (C) 2025 olie.xdev (reference driver). See THIRD_PARTY.md.
 * Packets are joined by scale timestamp AND user, never by arrival order.
 */
public final class Bs444Protocol {
    public static final long EPOCH = 1262304000L;
    public static UUID uuid(int n) { return UUID.fromString(String.format(Locale.ROOT,"0000%04x-0000-1000-8000-00805f9b34fb",n)); }
    public static final UUID SERVICE=uuid(0x78b2), WEIGHT=uuid(0x8a21), BODY=uuid(0x8a22), COMMAND=uuid(0x8a81), EXTRA=uuid(0x8a82), CCC=uuid(0x2902);
    private final Map<String, byte[]> weights = new LinkedHashMap<>(), bodies = new LinkedHashMap<>();
    private static int u16(byte[] d,int p) { return (d[p]&255)|((d[p+1]&255)<<8); }
    private static long u32(byte[] d,int p) { return (u16(d,p)&65535L)|((u16(d,p+2)&65535L)<<16); }
    private static String key(long t,int user) { return t+":"+user; }
    public static byte[] timeCommand(long unixSeconds) {
        long t=unixSeconds-EPOCH;
        if(t<0 || t>0xffffffffL) throw new IllegalArgumentException("Ungültige Uhrzeit");
        return new byte[]{2,(byte)t,(byte)(t>>8),(byte)(t>>16),(byte)(t>>24)};
    }
    private static double metric(byte[] b,int offset,double max) {
        int raw=u16(b,offset)&0xfff;
        double n=raw/10.0;
        return raw==0xfff || n<=0 || n>max ? Double.NaN : n;
    }
    public synchronized Measurement accept(String address,UUID characteristic,byte[] data) {
        String key;
        if(WEIGHT.equals(characteristic)) {
            if(data.length<14) throw new IllegalArgumentException("Gewichtspaket zu kurz");
            if((data[0]&0x60)!=0) throw new IllegalArgumentException("Bitte die Waage auf kg einstellen");
            key=key(u32(data,5),data[13]&255); weights.put(key,data.clone());
        } else if(BODY.equals(characteristic)) {
            if(data.length<16) throw new IllegalArgumentException("Körperanalysepaket zu kurz");
            key=key(u32(data,1),data[5]&255); bodies.put(key,data.clone());
        } else return null;
        // Bounded session memory, even if a device sends unmatched or corrupt packets.
        while(weights.size()>32) weights.remove(weights.keySet().iterator().next());
        while(bodies.size()>32) bodies.remove(bodies.keySet().iterator().next());
        if(!weights.containsKey(key)||!bodies.containsKey(key)) return null;
        byte[] w=weights.remove(key), b=bodies.remove(key);
        double kg=u16(w,1)/100.0;
        if(kg<1 || kg>300) throw new IllegalArgumentException("Gewicht außerhalb des gültigen Bereichs");
        double basal=u16(b,6); if(basal==65535 || basal==0 || basal>10000) basal=Double.NaN;
        return new Measurement(address,u32(w,5),w[13]&255,kg,metric(b,8,100),metric(b,10,100),metric(b,12,100),metric(b,14,30),basal);
    }
}
