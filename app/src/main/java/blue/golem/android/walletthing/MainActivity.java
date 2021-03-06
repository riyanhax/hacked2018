package blue.golem.android.walletthing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.polkapolka.bluetooth.le.BluetoothLeService;
import com.polkapolka.bluetooth.le.SampleGattAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import blue.golem.android.walletthing.devcomm.Command;
import blue.golem.android.walletthing.devcomm.GetValueCommand;
import blue.golem.android.walletthing.devcomm.GetValueResponse;
import blue.golem.android.walletthing.devcomm.Response;
import blue.golem.android.walletthing.devcomm.ResponseParser;
import blue.golem.android.walletthing.devcomm.SetDatabaseCommand;
import blue.golem.android.walletthing.devcomm.SetValueCommand;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;

    private Spinner fromSpinner;
    private Spinner toSpinner;
    private EditText fromAmountView;
    private EditText toAmountView;
    private boolean isEditMode;
    private Drawable fromEditableBackground;
    private Drawable toEditableBackground;
    private String prevFromCurrency;
    private String prevToCurrency;
    private String devFromAmount = "0.00";
    private String devToAmount = "0.00";
    private SharedPreferences prefs;
    private BluetoothLeService bleService;
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            bleService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!bleService.initialize()) {
                if (!bleService.isBluetoothAvailable()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }
                return;
            }
            // Automatically connects to the device upon successful start-up initialization.
            bleService.autoDetectSerialDevice();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bleService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_DEVICE_FOUND.equals(action)) {
                bleService.connect(bleService.getFoundBluetoothDeviceAddress());
            } else if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Snackbar.make(findViewById(R.id.mainLayout),
                        "Connected to wallet",
                        Snackbar.LENGTH_LONG).show();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                for (BluetoothGattService gattService : bleService.getSupportedGattServices()) {
                    String serviceUuid = gattService.getUuid().toString();
                    if (serviceUuid.equals(SampleGattAttributes.HM_10_CONF)) {
                        characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
                        characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
                        break;
                    }
                }
                if (characteristicTX != null){
                    sendSelectedDatabase();
                    sendGetValue();
                }
                bleService.setCharacteristicNotification(characteristicRX, true);
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                processResponse();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!CurrencyConverter.getInstance().getReady()) {
            startActivity(new Intent(this, StartupActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Find views
        fromSpinner = findViewById(R.id.fromSpinner);
        toSpinner = findViewById(R.id.toSpinner);
        fromAmountView = findViewById(R.id.fromAmountView);
        toAmountView = findViewById(R.id.toAmountView);
        fromEditableBackground = fromAmountView.getBackground();
        toEditableBackground = toAmountView.getBackground();

        // Restore instance variables
        if (savedInstanceState != null) {
            isEditMode = savedInstanceState.getBoolean("edit_mode");
        }

        // Setup some views
        TextView.OnEditorActionListener editorActionListener = new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                return MainActivity.this.onEditorAction(textView, i, keyEvent);
            }
        };
        fromAmountView.setOnEditorActionListener(editorActionListener);
        toAmountView.setOnEditorActionListener(editorActionListener);

        // Setup currency spinners
        Collection<String> currencies = CurrencyConverter.getInstance().getCurrencies();
        List<String> sortedCurrencies = new ArrayList<>(currencies);
        Collections.sort(sortedCurrencies);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, sortedCurrencies);
        fromSpinner.setAdapter(adapter);
        toSpinner.setAdapter(adapter);
        AdapterView.OnItemSelectedListener spinnerSelectedListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                MainActivity.this.onSpinnerItemSelected(adapterView, view, i, l);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        };
        fromSpinner.setOnItemSelectedListener(spinnerSelectedListener);
        toSpinner.setOnItemSelectedListener(spinnerSelectedListener);

        prefs = getSharedPreferences("app", MODE_PRIVATE);
        String savedFromCurrency = prefs.getString("from_curr", null);
        String savedToCurrency = prefs.getString("to_curr", null);
        String savedAmount = prefs.getString("cached_amount", null);
        if (savedFromCurrency != null) {
            int pos = ((ArrayAdapter<String>) fromSpinner.getAdapter()).getPosition(savedFromCurrency);
            if (pos >= 0) fromSpinner.setSelection(pos);
        }
        if (savedToCurrency != null) {
            int pos = ((ArrayAdapter<String>) toSpinner.getAdapter()).getPosition(savedToCurrency);
            if (pos >= 0) toSpinner.setSelection(pos);
        }
        if (savedAmount != null) {
            devFromAmount = savedAmount;
            fromAmountView.setText(savedAmount);
            convertHomeToForeign();
            devToAmount = toAmountView.getText().toString();
        }

        prevFromCurrency = (String) fromSpinner.getSelectedItem();
        prevToCurrency = (String) toSpinner.getSelectedItem();
        setEditMode(isEditMode);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bleService != null && bleService.getFoundBluetoothDeviceAddress() != null) {
            final boolean result = bleService.connect(bleService.getFoundBluetoothDeviceAddress());
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("edit_mode", isEditMode);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        bleService = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (bleService.initialize()) {
                bleService.autoDetectSerialDevice();
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DEVICE_FOUND);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
/*
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
*/
        return intentFilter;
    }

    public void onModeClick(View v) {
        setEditMode(!isEditMode);
    }

    public void onSetClick(View v) {
        sendSetValue(Double.parseDouble(toAmountView.getText().toString()));
        String fromAmount = fromAmountView.getText().toString();
        String toAmount = toAmountView.getText().toString();
        SharedPreferences.Editor e = prefs.edit();
        e.putString("cached_amount", fromAmountView.getText().toString());
        e.apply();
        devFromAmount = fromAmount;
        devToAmount = toAmount;
    }

    private void setEditMode(boolean editable) {
        isEditMode = editable;
        if (editable) {
            devFromAmount = fromAmountView.getText().toString();
            devToAmount = toAmountView.getText().toString();
        } else {
            fromAmountView.setText(devFromAmount);
            toAmountView.setText(devToAmount);
        }
        setTextViewEditable(fromAmountView, isEditMode, fromEditableBackground);
        setTextViewEditable(toAmountView, isEditMode, toEditableBackground);
        findViewById(R.id.setButton).setEnabled(editable);
    }

    private void setTextViewEditable(EditText v, boolean editable, Drawable editableBackground) {
        if (editable) {
            v.setBackground(editableBackground);
            v.setFocusableInTouchMode(true);
        } else {
            v.clearFocus();
            v.setBackground(null);
        }
        v.setFocusable(editable);
    }

    private boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE && event == null) {
            if (v == fromAmountView) {
                convertHomeToForeign();
                return true;
            } else if (v == toAmountView) {
                convertForeignToHome();
                return true;
            }
        }
        return false;
    }

    private void onSpinnerItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == fromSpinner) {
            String newCurrency = (String) fromSpinner.getItemAtPosition(position);
            convertAndSet(prevFromCurrency, newCurrency, fromAmountView, fromAmountView);
            prevFromCurrency = newCurrency;
            SharedPreferences.Editor e = prefs.edit();
            e.putString("from_curr", newCurrency);
            e.putString("cached_amount", fromAmountView.getText().toString());
            e.apply();
        } else if (parent == toSpinner) {
            String newCurrency = (String) toSpinner.getItemAtPosition(position);
            convertAndSet(prevToCurrency, newCurrency, toAmountView, toAmountView);
            prevToCurrency = newCurrency;
            SharedPreferences.Editor e = prefs.edit();
            e.putString("to_curr", newCurrency);
            e.apply();
            sendSetValue(Double.parseDouble(toAmountView.getText().toString()));
            sendSelectedDatabase();
        }
    }

    private void convertHomeToForeign() {
        String from = (String) fromSpinner.getSelectedItem();
        String to = (String) toSpinner.getSelectedItem();
        convertAndSet(from, to, fromAmountView, toAmountView);
    }

    private void convertForeignToHome() {
        String to = (String) fromSpinner.getSelectedItem();
        String from = (String) toSpinner.getSelectedItem();
        convertAndSet(from, to, toAmountView, fromAmountView);
    }

    private void convertAndSet(String from, String to, EditText srcView, EditText destView) {
        double amount = Double.parseDouble(srcView.getText().toString());
        BigDecimal fromDec = new BigDecimal(amount);
        BigDecimal toDec = CurrencyConverter.getInstance().convert(from, to, fromDec);
        destView.setText(toDec.toString());
    }

    private boolean sendSelectedDatabase() {
        String from = (String) fromSpinner.getSelectedItem();
        String to = (String) toSpinner.getSelectedItem();
        Map<Integer, int[][]> db = DetectionDatabase.getDatabaseForCurrency(to);
        if (db != null) {
            int[] vals = new int[db.size()];
            int[][][] database = new int[db.size()][][];
            int i = 0;
            for (Map.Entry<Integer, int[][]> entry : db.entrySet()) {
                vals[i] = entry.getKey();
                database[i] = entry.getValue();
                ++i;
            }
            SetDatabaseCommand cmd = new SetDatabaseCommand();
            cmd.setCurrencyValues(vals);
            cmd.setDatabase(database);
            cmd.setConversionRate(CurrencyConverter.getInstance().getConversionRate(to, from));
            return sendCommand(cmd);
        } else {
            return false;
        }
    }

    private boolean sendSetValue(double value) {
        SetValueCommand cmd = new SetValueCommand();
        cmd.setCurrencyValue(value);
        return sendCommand(cmd);
    }

    private boolean sendGetValue() {
        GetValueCommand cmd = new GetValueCommand();
        return sendCommand(cmd);
    }

    private boolean sendCommand(Command cmd) {
        if (bleService == null || !bleService.isBluetoothAvailable() || characteristicTX == null) return false;

        try {
            byte[] dataToSend = cmd.serialize();
            characteristicTX.setValue(dataToSend);
            bleService.writeCharacteristic(characteristicTX);
            bleService.setCharacteristicNotification(characteristicRX, true);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to send command.", ex);
            return false;
        }
    }

    private void processResponse() {
        try {
            byte[] msg = characteristicRX.getValue();
            Response r = ResponseParser.parse(msg);
            if (r != null) {
                if (r instanceof GetValueResponse)
                    processGetValueResponse((GetValueResponse) r);
            }
        } catch (IOException ex) {
            Log.e(TAG, "Failed to process BLE response.", ex);
        }
    }

    private void processGetValueResponse(GetValueResponse r) {
        if (r.getValue() < 0) {
            // Send cached value to device
            double amount = Double.parseDouble(toAmountView.getText().toString());
            sendSetValue(amount);
        } else {
            // Set cached value to device's value
            toAmountView.setText(Double.toString(r.getValue()));
            convertForeignToHome();
            SharedPreferences.Editor e = prefs.edit();
            e.putString("cached_amount", fromAmountView.getText().toString());
            e.apply();
        }
    }
}
