package com.manichord.synthesizer.android.ui;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.util.Log;

import com.manichord.synthesizer.R;

public class SettingsActivity extends PreferenceActivity {
  @SuppressWarnings("deprecation")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preferences);
    ListPreference keyboardTypePref = (ListPreference)findPreference("keyboard_type");
    updateListSummary(keyboardTypePref, keyboardTypePref.getValue());
    keyboardTypePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      public boolean onPreferenceChange(Preference pref, Object newVal) {
        updateListSummary(pref, newVal.toString());
        return true;
      }
    });
    ListPreference midiChannelPref = (ListPreference)findPreference("midi_channel");
    updateListSummary(midiChannelPref, midiChannelPref.getValue());
    Log.d("SettingsActivity", "mid channel:"+midiChannelPref.getValue());
    midiChannelPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      public boolean onPreferenceChange(Preference pref, Object newVal) {
        updateListSummary(pref, newVal.toString());
        return true;
      }
    });
    ListPreference touchDragActionPref = (ListPreference)findPreference("touch_drag_action");
    updateListSummary(touchDragActionPref, touchDragActionPref.getValue());
    Log.d("SettingsActivity", "touch drag action:"+touchDragActionPref.getValue());
    touchDragActionPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      public boolean onPreferenceChange(Preference pref, Object newVal) {
        updateListSummary(pref, newVal.toString());
        return true;
      }
    });
  }



  private void updateListSummary(Preference pref, String newVal) {
    ListPreference lp = (ListPreference)pref;
    int index = lp.findIndexOfValue(newVal);
    lp.setSummary(lp.getEntries()[index]);
  }
}

