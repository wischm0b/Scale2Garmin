package de.scalebridge;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.*;
import android.graphics.Color;
import android.content.res.Configuration;

/** Opens from the system Health Connect permission screen, without starting Bluetooth. */
public final class HealthPrivacyActivity extends Activity {
    @Override protected void attachBaseContext(Context c){super.attachBaseContext(Tr.wrap(c));}
    @Override public void onCreate(Bundle state){
        String mode=getSharedPreferences("settings",0).getString("theme","system");boolean dark=mode.equals("dark")||(mode.equals("system")&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);super.onCreate(state);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(24*getResources().getDisplayMetrics().density);box.setPadding(pad,pad,pad,pad);box.setBackgroundColor(Color.parseColor(dark?"#0F1714":"#F3F6F1"));
        box.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.systemBars());v.setPadding(pad+safe.left,pad+safe.top,pad+safe.right,pad+safe.bottom);return insets;});
        TextView title=new TextView(this);title.setText("Health Connect");title.setTextSize(24);title.setTextColor(Color.parseColor(dark?"#77DDB1":"#176C58"));box.addView(title);
        TextView body=new TextView(this);body.setText(Tr.text(this,"Scale2Garmin schreibt auf Wunsch die gespeicherten Waagenmessungen des ausgewählten Speicherplatzes in Health Connect: Gewicht, Körperfett, Knochenmasse, aus Gewicht und Wasseranteil berechnete Körperwassermasse sowie Grundumsatz. Muskelanteil wird nicht übertragen. Es werden keine Daten aus Health Connect gelesen. Die Daten bleiben dort auf deinem Gerät; andere Apps erhalten Zugriff nur nach deiner Freigabe in Health Connect. Die Garmin-Übertragung wird separat gesteuert. Du kannst die Synchronisation in Scale2Garmin ausschalten und Berechtigungen oder bereits exportierte Daten in Health Connect verwalten. Ausschalten löscht keine bisherigen Einträge."));body.setTextSize(16);body.setPadding(0,pad,0,pad);body.setTextColor(Color.parseColor(dark?"#E8F0EA":"#192C27"));box.addView(body);
        Button close=new Button(this);close.setText(Tr.text(this,"Schließen"));close.setAllCaps(false);close.setTextColor(Color.parseColor(dark?"#0F1714":"#FFFFFF"));close.setBackgroundTintList(null);android.graphics.drawable.GradientDrawable fill=new android.graphics.drawable.GradientDrawable();fill.setColor(Color.parseColor(dark?"#77DDB1":"#176C58"));fill.setCornerRadius(pad*.66f);close.setBackground(fill);close.setPadding(pad,pad/2,pad,pad/2);close.setOnClickListener(v->finish());box.addView(close);ScrollView scroll=new ScrollView(this);scroll.addView(box);setContentView(scroll);
    }
}
