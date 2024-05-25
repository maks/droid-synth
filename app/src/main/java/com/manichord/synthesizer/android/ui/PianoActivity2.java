/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.manichord.synthesizer.android.ui;

import java.io.InputStream;
import java.util.List;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.manichord.synthesizer.R;
import com.manichord.synthesizer.android.widgets.keyboard.KeyboardSpec;
import com.manichord.synthesizer.android.widgets.keyboard.KeyboardView;
import com.manichord.synthesizer.android.widgets.keyboard.ScrollStripView;
import com.manichord.synthesizer.android.widgets.knob.KnobListener;
import com.manichord.synthesizer.android.widgets.knob.KnobView;
import com.manichord.synthesizer.core.midi.MidiAdapter;
import com.manichord.synthesizer.core.midi.MidiListener;

/**
 * Activity for simply playing the piano.
 * This version is hacked up to send MIDI to the C++ engine. This needs to
 * be refactored to make it cleaner.
 */
public class PianoActivity2 extends SynthActivity implements OnSharedPreferenceChangeListener {
  private static final int VALUE_ENCODER  = 64;
  private static final int RESONANCE_DIAL  = 71;
  private static final int PITCH_DIAL  = 80;
  private static final int EDIT_DIAL  = 82;

  private int currentChannel = 0;
  private int currentDial;

  private Intent requestFileIntent;
  private ParcelFileDescriptor inputPFD;

  private static String TAG = PianoActivity2.class.toString();

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d("synth", "activity onCreate " + getIntent());
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.piano2);

    keyboard_ = (KeyboardView)findViewById(R.id.piano);
    ScrollStripView scrollStrip_ = (ScrollStripView)findViewById(R.id.scrollstrip);
    scrollStrip_.bindKeyboard(keyboard_);
    cutoffKnob_ = (KnobView)findViewById(R.id.cutoffKnob);
    resonanceKnob_ = (KnobView)findViewById(R.id.resonanceKnob);
    overdriveKnob_ = (KnobView)findViewById(R.id.overdriveKnob);
    presetSpinner_ = (Spinner)findViewById(R.id.presetSpinner);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
      setupUsbMidi(getIntent());
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.synth_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.settings:
        startActivity(new Intent(this, SettingsActivity.class));
        return true;
      case R.id.load:
        requestFileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        requestFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        requestFileIntent.setType("*/*");
        requestFile();
        return true;
      default:
        return super.onOptionsItemSelected(item);
    }
  }

  protected void requestFile() {
    startActivityForResult(requestFileIntent, 0);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode,
                               Intent returnIntent) {
    // If the selection didn't work
    if (resultCode != RESULT_OK) {
      Log.w(TAG, "invalid result code:"+resultCode);
    } else {
      // Get the file's content URI from the incoming Intent
      Uri returnUri = returnIntent.getData();
        try {
          InputStream patchIs =
                  getContentResolver().openInputStream(returnUri);
          byte[] patchData = new byte[4104];
          patchIs.read(patchData);
          Log.d(TAG, "syx read from url");
          if (synthesizerService_ != null) {
             synthesizerService_.loadPatchBank(patchData);
            Log.d(TAG, "Loaded Patches");
            // reload patch names dropdown
            loadPatchNameListUI();
          } else {
            Log.e(TAG, "missing Synth Service, cannot load patch bank");
          }
        } catch (Exception e) {
        Log.e(TAG, "error reading", e);
      }
    }
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "activity onDestroy");
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
      unregisterReceiver(usbReceiver_);
    }
    super.onDestroy();
  }

  @Override
  protected void onResume() {
    super.onResume();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    prefs.registerOnSharedPreferenceChangeListener(this);
    onSharedPreferenceChanged(prefs, "keyboard_type");
    onSharedPreferenceChanged(prefs, "vel_sens");
    onSharedPreferenceChanged(prefs, "midi_channel");
  }

  @Override
  protected void onPause() {
    super.onPause();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    prefs.unregisterOnSharedPreferenceChangeListener(this);

  }

  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    if (key.equals("keyboard_type")) {
      String keyboardType = prefs.getString(key, "2row");
      keyboard_.setKeyboardSpec(KeyboardSpec.make(keyboardType));
    } else if (key.equals("vel_sens") || key.equals("vel_avg")) {
      float velSens = prefs.getFloat("vel_sens", 0.5f);
      float velAvg = prefs.getFloat("vel_avg", 64);
      keyboard_.setVelocitySensitivity(velSens, velAvg);
    } else if (key.equals("midi_channel")) {
      currentChannel = Integer.parseInt(prefs.getString(key, "0"));
      if (synthesizerService_ != null) {
        synthesizerService_.setCurrentChannel(currentChannel);
      } else {
        Log.d("PianoActivity2", "cannot set current channel no Synth service");
      }
      Log.d("PianoActivity2", "set current channel:" + currentChannel);
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    Log.d(TAG, "activity onNewIntent " + intent);
    connectUsbFromIntent(intent);
  }

  boolean connectUsbMidi(UsbDevice device) {
    if (synthesizerService_ != null) {
      return synthesizerService_.connectUsbMidi(device);
    }
    usbDevicePending_ = device;
    return true;
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
  boolean connectUsbFromIntent(Intent intent) {
    if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
      UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
      return connectUsbMidi(device);
    } else {
      return false;
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
  void setupUsbMidi(Intent intent) {
    permissionIntent_ = PendingIntent.getBroadcast(this, 0, new Intent(
            ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_USB_PERMISSION);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(usbReceiver_, filter, RECEIVER_EXPORTED);
    } else {
      registerReceiver(usbReceiver_, filter);
    }
    connectUsbFromIntent(intent);
  }

  private static final String ACTION_USB_PERMISSION = "com.levien.synthesizer.USB_PERSMISSION";
  BroadcastReceiver usbReceiver_ = new BroadcastReceiver() {
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (ACTION_USB_PERMISSION.equals(action)) {
        synchronized (this) {
          UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
          if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
            connectUsbMidi(device);
          } else {
            Log.d(TAG, "permission denied for device " + device);
          }
          permissionRequestPending_ = false;
        }
      }
    }
  };

  public void sendMidiBytes(byte[] buf) {
    // TODO: in future we'll want to reflect MIDI to UI (knobs turn, keys press)
    if (synthesizerService_ != null) {
      synthesizerService_.sendRawMidi(buf);
    }
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
  protected void onSynthConnected() {
    final MidiListener synthMidi = synthesizerService_.getMidiListener();
    //piano_.bindTo(synthMidi);
    keyboard_.setMidiListener(synthMidi);

    cutoffKnob_.setKnobListener(new KnobListener() {
      public void onKnobChanged(double newValue) {
        int value = (int)Math.round(newValue * 127);
        synthMidi.onController(0, 1, value);
      }
    });
    resonanceKnob_.setKnobListener(new KnobListener() {
      public void onKnobChanged(double newValue) {
        int value = (int)Math.round(newValue * 127);
        synthMidi.onController(0, 2, value);
      }
    });
    overdriveKnob_.setKnobListener(new KnobListener() {
      public void onKnobChanged(double newValue) {
        int value = (int)Math.round(newValue * 127);
        synthMidi.onController(0, 3, value);
      }
    });

    // Connect controller changes to knob views
    synthesizerService_.setMidiListener(new MidiAdapter() {
      public void onNoteOn(final int channel, final int note, final int velocity) {
        Log.d("MAKS","NoteON:"+channel+"-"+note+":"+velocity);
        runOnUiThread(new Runnable() {
          public void run() {
            keyboard_.onNote(note, velocity);
          }
        });
      }

      public void onNoteOff(final int channel, final int note, final int velocity) {
        runOnUiThread(new Runnable() {
          public void run() {
            keyboard_.onNote(note, 0);
          }
        });
      }

      public void onController(final int channel, final int cc, final int value) {
        runOnUiThread(new Runnable() {
          public void run() {
            if (cc == PITCH_DIAL) {
              cutoffKnob_.setValue(value * (1.0 / 127));
              synthMidi.onController(0, 1, value);
            } else if (cc == RESONANCE_DIAL) {
              resonanceKnob_.setValue(value * (1.0 / 127));
              synthMidi.onController(0, 2, value);
            } else if (cc == EDIT_DIAL) {
              overdriveKnob_.setValue(value * (1.0 / 127));
              synthMidi.onController(0, 3, value);
            } else if (cc == 98) {
              currentDial = value;
            } else if (cc == 97) {
              switch (currentDial) {
                case VALUE_ENCODER:
                  int currentPos =  presetSpinner_.getSelectedItemPosition();
                  presetSpinner_.setSelection(currentPos-1);
                  break;
              }
            } else if (cc == 96) {
              switch (currentDial) {
                case VALUE_ENCODER:
                  int currentPos =  presetSpinner_.getSelectedItemPosition();
                  presetSpinner_.setSelection(currentPos+1);
                  break;
              }
            }
          }
        });
      }

      @Override
      public void onProgramChange(final int channel, final int program) {
        // Log.d(TAG, "onProgramChange: chan"+channel+" prog:"+program);
          runOnUiThread(new Runnable() {
            public void run() {
              if (program < 32) {
                presetSpinner_.setSelection(program);
              } else {
                Log.i(TAG, "out of range Program change:"+program);
              }
            }
          });
        }
    });

    // Populate patch names (note: we could update an existing list rather than
    // creating a new adapter, but it probably wouldn't save all that much).
    if (presetSpinner_.getAdapter() == null) {
      // Only set it once, which is a workaround that allows the preset
      // selection to persist for onCreate lifetime. Of course, it should
      // be persisted for real, instead.
      loadPatchNameListUI();
    }

    presetSpinner_.setOnItemSelectedListener(new OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        synthesizerService_.getMidiListener().onProgramChange(0, position);
      }
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    // Handle any pending USB device events
    if (usbDevicePending_ != null) {
      synthesizerService_.connectUsbMidi(usbDevicePending_);
      usbDevicePending_ = null;
    } else {
      UsbDevice device = synthesizerService_.usbDeviceNeedsPermission();
      if (device != null) {
        synchronized (usbReceiver_) {
          if (!permissionRequestPending_) {
            permissionRequestPending_ = true;
            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            usbManager.requestPermission(device, permissionIntent_);
          }
        }
      }
    }
  }

  private void loadPatchNameListUI() {
    List<String> patchNames = synthesizerService_.getPatchNames();
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            PianoActivity2.this, android.R.layout.simple_spinner_item, patchNames);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    presetSpinner_.setAdapter(adapter);
  }

  protected void onSynthDisconnected() {
    synthesizerService_.setMidiListener(null);
  }

  //private PianoView piano_;
  private KeyboardView keyboard_;
  private KnobView cutoffKnob_;
  private KnobView resonanceKnob_;
  private KnobView overdriveKnob_;
  private Spinner presetSpinner_;
  private PendingIntent permissionIntent_;
  private boolean permissionRequestPending_;
  private UsbDevice usbDevicePending_;
}
