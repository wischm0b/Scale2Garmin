package de.scalebridge;

import java.io.*;
import java.time.Instant;
import java.util.*;

/** Stable, locale-independent CSV backup format. */
public final class CsvBackup {
    private static final String[] HEADER={"timestamp","user","weight_kg","body_fat_percent","body_water_percent","muscle_percent","bone_kg","basal_kcal","address","sync_state","note"};
    public record Result(int imported,int duplicates){}
    private static String cell(double n){return Double.isFinite(n)?Double.toString(n):"";}
    private static String quote(String value){if(value==null)return "";if(value.indexOf(';')<0&&value.indexOf('"')<0&&value.indexOf('\n')<0&&value.indexOf('\r')<0)return value;return '"'+value.replace("\"","\"\"")+'"';}
    public static int write(Store db,Writer target) throws IOException {
        BufferedWriter out=target instanceof BufferedWriter b?b:new BufferedWriter(target);out.write('\ufeff');out.write(String.join(";",HEADER));out.newLine();int count=0;
        for(Store.Row row:db.allMeasurements()){
            Measurement m=row.measurement();String[] cells={Instant.ofEpochSecond(m.unixSeconds).toString(),Integer.toString(m.user),cell(m.weight),cell(m.fat),cell(m.water),cell(m.musclePercent),cell(m.bone),cell(m.basal),m.address,row.state(),row.note()};
            for(int i=0;i<cells.length;i++){if(i>0)out.write(';');out.write(quote(cells[i]));}out.newLine();count++;
        }out.flush();return count;
    }
    public static Result read(Store db,Reader source,int fallbackUser) throws IOException {
        List<List<String>> records=parse(source);if(records.isEmpty())throw new IOException("Die CSV-Datei ist leer.");
        records=adaptVitaDock(records,fallbackUser);
        List<String> header=records.get(0);Map<String,Integer> columns=new HashMap<>();for(int i=0;i<header.size();i++)columns.put(normalize(header.get(i)),i);
        for(String required:new String[]{"timestamp","weight_kg"})if(!columns.containsKey(required))throw new IOException("Unbekanntes CSV-Format. Erwartet werden mindestens timestamp und weight_kg.");
        int imported=0,duplicates=0;android.database.sqlite.SQLiteDatabase transaction=db.getWritableDatabase();transaction.beginTransaction();
        try{for(int line=1;line<records.size();line++){List<String> row=records.get(line);if(row.stream().allMatch(String::isBlank))continue;
            try{
                long unix=Instant.parse(get(row,columns,"timestamp","")).getEpochSecond();int user=integer(get(row,columns,"user",""),fallbackUser);if(user<1||user>8)user=fallbackUser;if(user<1||user>8)throw new IllegalArgumentException("Speicherplatz fehlt");
                String address=get(row,columns,"address","csv-import");if(address.isBlank())address="csv-import";
                Measurement m=new Measurement(address,unix-Bs444Protocol.EPOCH,user,number(get(row,columns,"weight_kg",""),true),number(get(row,columns,"body_fat_percent",""),false),number(get(row,columns,"body_water_percent",""),false),number(get(row,columns,"muscle_percent",""),false),number(get(row,columns,"bone_kg",""),false),number(get(row,columns,"basal_kcal",""),false));
                if(db.insert(m)){String state=get(row,columns,"sync_state","pending");if(!Set.of("pending","sent","review").contains(state))state="pending";db.state(m.id(),state,get(row,columns,"note",""));imported++;}else duplicates++;
            }catch(Exception e){throw new IOException("Ungültige CSV-Zeile "+(line+1)+": "+e.getMessage(),e);}
        }transaction.setTransactionSuccessful();return new Result(imported,duplicates);
        }finally{transaction.endTransaction();}
    }
    private static List<List<String>> adaptVitaDock(List<List<String>> source,int fallbackUser) throws IOException {
        int header=-1;for(int i=0;i<source.size();i++){List<String> row=source.get(i);if(row.stream().map(CsvBackup::normalize).anyMatch("datum - uhrzeit"::equals)&&row.stream().map(CsvBackup::normalize).anyMatch("körpergewicht (kg)"::equals)){header=i;break;}}
        if(header<0)return source;
        List<String> names=source.get(header);Map<String,Integer> columns=new HashMap<>();for(int i=0;i<names.size();i++)columns.put(normalize(names.get(i)),i);
        List<List<String>> result=new ArrayList<>();result.add(Arrays.asList(HEADER));java.time.format.DateTimeFormatter time=java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu - HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT);
        for(int line=header+1;line<source.size();line++){List<String> row=source.get(line);if(row.stream().allMatch(String::isBlank))continue;
            try{
                long unix=java.time.LocalDateTime.parse(vita(row,columns,"datum - uhrzeit"),time).atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
                double weight=vitaNumber(vita(row,columns,"körpergewicht (kg)"),true),bonePercent=vitaNumber(vita(row,columns,"knochenmasse (%)"),false);
                String note=vita(row,columns,"anmerkungen");
                result.add(Arrays.asList(java.time.Instant.ofEpochSecond(unix).toString(),Integer.toString(fallbackUser),Double.toString(weight),vitaDecimal(vita(row,columns,"körperfett (%)")),vitaDecimal(vita(row,columns,"körperwasser (%)")),vitaDecimal(vita(row,columns,"muskelmasse (%)")),Double.isFinite(bonePercent)?Double.toString(weight*bonePercent/100.0):"","","vitadock-import","pending",note));
            }catch(Exception e){throw new IOException("Ungültige VitaDock+-Zeile "+(line+1)+": "+e.getMessage(),e);}
        }return result;
    }
    private static String vita(List<String> row,Map<String,Integer> columns,String name){Integer index=columns.get(name);return index==null||index>=row.size()?"":row.get(index).trim();}
    private static String vitaDecimal(String value){double n=vitaNumber(value,false);return Double.isFinite(n)?Double.toString(n):"";}
    private static double vitaNumber(String value,boolean required){if(value.isBlank()){if(required)throw new IllegalArgumentException("Gewicht fehlt");return Double.NaN;}return Double.parseDouble(value.replace(".","").replace(',','.'));}
    private static String normalize(String s){return s.replace("\ufeff","").trim().toLowerCase(Locale.ROOT);}
    private static String get(List<String> row,Map<String,Integer> cols,String name,String fallback){Integer i=cols.get(name);return i==null||i>=row.size()?fallback:row.get(i).trim();}
    private static int integer(String value,int fallback){return value.isBlank()?fallback:Integer.parseInt(value);}
    private static double number(String value,boolean required){if(value.isBlank()){if(required)throw new IllegalArgumentException("Gewicht fehlt");return Double.NaN;}double n=Double.parseDouble(value);if(!Double.isFinite(n))throw new IllegalArgumentException("Ungültiger Zahlenwert");if(required&&(n<1||n>300))throw new IllegalArgumentException("Gewicht außerhalb des gültigen Bereichs");return n;}
    private static List<List<String>> parse(Reader source) throws IOException {
        PushbackReader in=new PushbackReader(new BufferedReader(source),1);List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quoted=false;int read,total=0;
        while((read=in.read())!=-1){if(++total>10_000_000)throw new IOException("CSV-Datei ist größer als 10 MB.");char c=(char)read;
            if(quoted){if(c=='"'){int next=in.read();if(next=='"')cell.append('"');else{quoted=false;if(next!=-1)in.unread(next);}}else cell.append(c);}
            else if(c=='"'&&cell.length()==0)quoted=true;else if(c==';'){row.add(cell.toString());cell.setLength(0);}else if(c=='\n'){row.add(cell.toString());cell.setLength(0);rows.add(row);row=new ArrayList<>();if(rows.size()>100_001)throw new IOException("CSV-Datei enthält zu viele Zeilen.");}else if(c!='\r')cell.append(c);
        }if(quoted)throw new IOException("Nicht geschlossenes Anführungszeichen.");if(cell.length()>0||!row.isEmpty()){row.add(cell.toString());rows.add(row);}return rows;
    }
}
