package de.scalebridge;

/** Original scale values; muscle percentage is retained separately from derived kg. */
public final class Measurement {
    public final String address;
    public final long scaleSeconds, unixSeconds;
    public final int user;
    public final double weight, fat, water, musclePercent, bone, basal;
    public Measurement(String address, long scaleSeconds, int user, double weight,
                       double fat, double water, double musclePercent, double bone, double basal) {
        this.address = address; this.scaleSeconds = scaleSeconds; this.user = user;
        this.unixSeconds = scaleSeconds + Bs444Protocol.EPOCH;
        this.weight = weight; this.fat = fat; this.water = water;
        this.musclePercent = musclePercent; this.bone = bone; this.basal = basal;
    }
    public String id() { return address + ":" + user + ":" + scaleSeconds; }
    public double muscleKg() { return weight * musclePercent / 100.0; }
    public boolean hasComposition() { return Double.isFinite(fat); }
}
