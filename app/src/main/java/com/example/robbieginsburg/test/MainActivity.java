package com.example.robbieginsburg.test;

import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Process;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Color;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private final String TAG = "BRSPTERM." + this.getClass().getSimpleName();

    private int numData = 0;

    private Brsp _brsp;
    private BluetoothDevice _selectedDevice;

    LineChart heartRateChart;
    LineChart respirationRateChart;
    LineChart spo2Chart;
    private TextView ledLength;

    ArrayList<String> xValsHeart = new ArrayList<String>();
    ArrayList<String> xValsResp = new ArrayList<String>();
    ArrayList<String> xValsSPO2 = new ArrayList<String>();

    LineData heartData;
    LineData respData;
    LineData spo2Data;

    private BrspCallback _brspCallback = new BrspCallback() {

        @Override
        public void onSendingStateChanged(Brsp obj) {
            //Log.d(TAG, "onSendingStateChanged thread id:" + Process.myTid());
        }

        @Override
        public void onConnectionStateChanged(Brsp obj) {
            //Log.d(TAG, "onConnectionStateChanged state:" + obj.getConnectionState() + " thread id:" + Process.myTid());
            final Brsp brspObj = obj;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    invalidateOptionsMenu();
                    BluetoothDevice currentDevice = brspObj.getDevice();
                    if (currentDevice != null && brspObj.getConnectionState() == BluetoothProfile.STATE_CONNECTED) {
                        brspObj.readRssi();
                        // Log.d(TAG, "Creating bond for device:" +
                        // currentDevice.getAddress());
                        // currentDevice.createBond();
                    }
                }
            });
        }

        @Override
        public void onDataReceived(final int length, final int numGood, final int numBad) {
            //Log.d("led length" , "led length: " + length);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ledLength.setText("REDLED length: " + String.valueOf(length) +
                        "\nNum Good:        " + String.valueOf(numGood) +
                        "\nNum Bad:           " + String.valueOf(numBad));
                }
            });
            super.onDataReceived(length, numGood, numBad);
        }

        @Override
        public void onDataReceived(final double[] respHeartSpo2Rates) {
            //Log.d(TAG, "onDataReceived thread id:" + Process.myTid());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Log.d("RespHeartRates1", "Respiration Rate: " + respHeartRates[0]);
                    //Log.d("RespHeartRates2", "Heart Rate: " + respHeartRates[1]);

                    xValsHeart.add(String.valueOf(numData + 1));
                    xValsResp.add(String.valueOf(numData + 1));
                    xValsSPO2.add(String.valueOf(numData + 1));


                    heartData.addEntry(new Entry((float) respHeartSpo2Rates[0], numData), 0);
                    respData.addEntry(new Entry((float) respHeartSpo2Rates[1], numData), 0);
                    spo2Data.addEntry(new Entry((float) respHeartSpo2Rates[2], numData), 0);

                    // let the chart know it's data changed
                    heartRateChart.notifyDataSetChanged();
                    respirationRateChart.notifyDataSetChanged();
                    spo2Chart.notifyDataSetChanged();

                    // refresh the charts
                    heartRateChart.invalidate();
                    respirationRateChart.invalidate();
                    spo2Chart.invalidate();

                    numData++;
                }
            });
            super.onDataReceived(respHeartSpo2Rates);
        }

        @Override
        public void onError(Brsp obj, Exception e) {
            //Log.e(TAG, "onError:" + e.getMessage() + " thread id:" + Process.myTid());
            super.onError(obj, e);
            if (e instanceof UnstableException) {
                //Log.d(TAG, "Unstable Caught");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Bluetooth has become unstable!  Restarting Adapter...", Toast.LENGTH_LONG).show();
                        // Cycle the bluetooth here to try and recover from an unstable adapter
                        // recreate();
                    }
                });
            }
        }

        @Override
        public void onBrspModeChanged(Brsp obj) {
            super.onBrspModeChanged(obj);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    invalidateOptionsMenu();
                }
            });
        }

        @Override
        public void onRssiUpdate(Brsp obj) {
            //Log.d(TAG, "onRssiUpdate thread id:" + Process.myTid());
            super.onRssiUpdate(obj);
            //Log.d(TAG, "Remote device RSSI:" + obj.getLastRssi()); // Log RSSI
        }

        @Override
        public void onBrspStateChanged(Brsp obj) {
            super.onBrspStateChanged(obj);
            int currentState = obj.getBrspState();
            //Log.d(TAG, "onBrspStateChanged thread id:" + Process.myTid() + " State:" + currentState);
            obj.readRssi(); // read the RSSI once
            if (obj.getBrspState() == Brsp.BRSP_STATE_READY) {
                //Log.d(TAG, "BRSP READY");
                // Ready to write
                // _brsp.writeBytes("Test".getBytes());
            } else {
                //Log.d(TAG, "BRSP NOT READY");
            }
        }

    };


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Hack to prevent onCreate being called on orientation change
        // This probably should be done in a better way in a real app
        // http://stackoverflow.com/questions/456211/activity-restart-on-rotation-android
        super.onConfigurationChanged(newConfig);
    }

    //Function can be used to disable or enable the bluetooth
    private boolean setBluetooth(boolean enable) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean isEnabled = bluetoothAdapter.isEnabled();
        // Intent enableBtIntent = new
        // Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        if (enable) {
            // startActivityForResult(enableBtIntent, 1);
            return bluetoothAdapter.enable();
        } else {
            // startActivityForResult(enableBtIntent, 0);
            return bluetoothAdapter.disable();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                // Log.e(TAG, "STATE:" + state);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
//		    Log.d(TAG, "Enabling Bluetooth.  Result:" + setBluetooth(true));
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        // TODO: Disable user input and show restarting msg
                        break;
                    case BluetoothAdapter.STATE_ON:
                        // _brsp = new Brsp(_brspCallback, 10000, 10000);
                        // doScan();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        IntentFilter adapterStateFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        this.registerReceiver(mReceiver, adapterStateFilter);

        setContentView(R.layout.activity_main);

        ledLength = (TextView) findViewById(R.id.padding1);

        // Heart Rate Line Graph
        heartRateChart = (LineChart) findViewById(R.id.heartRateChart);
        heartRateChart.setDescription("Heart Rate");
        heartRateChart.setDescriptionPosition(625, 25);
        heartRateChart.setDescriptionTextSize(20f);
        heartRateChart.setDescriptionTypeface(Typeface.DEFAULT_BOLD);
        heartRateChart.setDescriptionColor(Color.RED);
        heartRateChart.setNoDataTextDescription("Inital Data Collection...\nThis may take up to 3 minutes");
        heartRateChart.setTouchEnabled(false);
        // x-axis
        XAxis xAxisHeart = heartRateChart.getXAxis();
        xAxisHeart.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisHeart.setTextSize(10f);
        xAxisHeart.setTextColor(Color.BLACK);
        xAxisHeart.setDrawAxisLine(true);
        xAxisHeart.setDrawGridLines(true);
        xAxisHeart.setAxisLineWidth(2f);
        // y-axis
        YAxis yAxisHeart = heartRateChart.getAxisLeft();
        heartRateChart.getAxisRight().setEnabled(false);
        yAxisHeart.setTextSize(10f);
        yAxisHeart.setTextColor(Color.BLACK);
        yAxisHeart.setDrawAxisLine(true);
        yAxisHeart.setDrawGridLines(true);
        yAxisHeart.setAxisLineWidth(2f);
        yAxisHeart.setAxisMinValue(0f);
        yAxisHeart.setAxisMaxValue(180f);
        // healthy resting min heart rate line
        LimitLine healthyMinHeart = new LimitLine(40f, "Healthy Resting Minimum Heart Rate");
        healthyMinHeart.setLineColor(Color.RED);
        healthyMinHeart.setLineWidth(2f);
        healthyMinHeart.setTextColor(Color.BLACK);
        healthyMinHeart.setTextSize(12f);
        healthyMinHeart.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
        yAxisHeart.addLimitLine(healthyMinHeart);
        // healthy resting max heart rate line
        LimitLine healthyMaxHeart = new LimitLine(100f, "Healthy Resting Maximum Heart Rate");
        healthyMaxHeart.setLineColor(Color.RED);
        healthyMaxHeart.setLineWidth(2f);
        healthyMaxHeart.setTextColor(Color.BLACK);
        healthyMaxHeart.setTextSize(12f);
        healthyMaxHeart.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        yAxisHeart.addLimitLine(healthyMaxHeart);
        // disable legend that appears at the bottom of the screen
        Legend heartLegend = heartRateChart.getLegend();
        heartLegend.setEnabled(false);
        // initialize the list holding the data for Heart Rate
        ArrayList<Entry> valsComp1 = new ArrayList<Entry>();
        // initialize the data into a data holder for the line graph
        LineDataSet setComp1 = new LineDataSet(valsComp1, "");
        setComp1.setAxisDependency(YAxis.AxisDependency.LEFT);
        setComp1.setColor(Color.RED);
        setComp1.setDrawCircles(false);
        setComp1.setDrawValues(false);
        // use the interface ILineDataSet
        ArrayList<ILineDataSet> dataSets1 = new ArrayList<ILineDataSet>();
        dataSets1.add(setComp1);
        // bind the data to the graph
        heartData = new LineData(xValsHeart, dataSets1);
        heartRateChart.setData(heartData);
        heartRateChart.invalidate(); // refresh the graph


        // Respiration Rate Line Graph
        respirationRateChart = (LineChart) findViewById(R.id.respirationRateChart);
        respirationRateChart.setDescription("Respiratory Rate");
        respirationRateChart.setDescriptionPosition(675, 25);
        respirationRateChart.setDescriptionTextSize(20f);
        respirationRateChart.setDescriptionColor(Color.BLUE);
        respirationRateChart.setDescriptionTypeface(Typeface.DEFAULT_BOLD);
        respirationRateChart.setTouchEnabled(false);
        // x-axis
        XAxis xAxisResp = respirationRateChart.getXAxis();
        xAxisResp.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisResp.setTextSize(10f);
        xAxisResp.setTextColor(Color.BLACK);
        xAxisResp.setDrawAxisLine(true);
        xAxisResp.setDrawGridLines(true);
        xAxisResp.setAxisLineWidth(2f);
        // y-axis
        YAxis yAxisResp = respirationRateChart.getAxisLeft();
        respirationRateChart.getAxisRight().setEnabled(false);
        yAxisResp.setTextSize(10f);
        yAxisResp.setTextColor(Color.BLACK);
        yAxisResp.setDrawAxisLine(true);
        yAxisResp.setDrawGridLines(true);
        yAxisResp.setAxisLineWidth(2f);
        yAxisResp.setAxisMinValue(0f);
        yAxisResp.setAxisMaxValue(40f);
        // healthy resting min respiratory rate line
        LimitLine healthyMinResp = new LimitLine(12f, "Healthy Resting Minimum Respiratory Rate");
        healthyMinResp.setLineColor(Color.BLUE);
        healthyMinResp.setLineWidth(2f);
        healthyMinResp.setTextColor(Color.BLACK);
        healthyMinResp.setTextSize(12f);
        healthyMinResp.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
        yAxisResp.addLimitLine(healthyMinResp);
        // healthy resting max respiratory rate line
        LimitLine healthyMaxResp = new LimitLine(25f, "Healthy Resting Maximum Respiratory Rate");
        healthyMaxResp.setLineColor(Color.BLUE);
        healthyMaxResp.setLineWidth(2f);
        healthyMaxResp.setTextColor(Color.BLACK);
        healthyMaxResp.setTextSize(12f);
        healthyMaxResp.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        yAxisResp.addLimitLine(healthyMaxResp);
        // disable legend that appears at the bottom of the screen
        Legend respLegend = respirationRateChart.getLegend();
        respLegend.setEnabled(false);
        // initialize the list holding the data for Heart Rate
        ArrayList<Entry> valsComp2 = new ArrayList<Entry>();
        // initialize the data into a data holder for the line graph
        LineDataSet setComp2 = new LineDataSet(valsComp2, "Respiration Rate");
        setComp2.setAxisDependency(YAxis.AxisDependency.LEFT);
        setComp2.setColor(Color.BLUE);
        setComp2.setDrawCircles(false);
        setComp2.setDrawValues(false);
        // use the interface ILineDataSet
        ArrayList<ILineDataSet> dataSets2 = new ArrayList<ILineDataSet>();
        dataSets2.add(setComp2);
        // bind the data to the graph
        respData = new LineData(xValsResp, dataSets2);
        respirationRateChart.setData(respData);
        respirationRateChart.invalidate(); // refresh the graph


        // SPO2 Line Graph
        spo2Chart = (LineChart) findViewById(R.id.spo2Chart);
        spo2Chart.setDescription("SPO2");
        spo2Chart.setDescriptionPosition(600, 25);
        spo2Chart.setDescriptionTextSize(20f);
        spo2Chart.setDescriptionColor(Color.GREEN);
        spo2Chart.setDescriptionTypeface(Typeface.DEFAULT_BOLD);
        spo2Chart.setTouchEnabled(false);
        // x-axis
        XAxis xAxisSPO = spo2Chart.getXAxis();
        xAxisSPO.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxisSPO.setTextSize(10f);
        xAxisSPO.setTextColor(Color.BLACK);
        xAxisSPO.setDrawAxisLine(true);
        xAxisSPO.setDrawGridLines(true);
        xAxisSPO.setAxisLineWidth(2f);
        // y-axis
        YAxis yAxisSPO2 = spo2Chart.getAxisLeft();
        spo2Chart.getAxisRight().setEnabled(false);
        yAxisSPO2.setTextSize(10f);
        yAxisSPO2.setTextColor(Color.BLACK);
        yAxisSPO2.setDrawAxisLine(true);
        yAxisSPO2.setDrawGridLines(true);
        yAxisSPO2.setAxisLineWidth(2f);
        yAxisSPO2.setAxisMinValue(0f);
        yAxisSPO2.setAxisMaxValue(1f);
        // healthy resting min respiratory rate line
        LimitLine healthyMinSPO2 = new LimitLine(.90f, "Healthy Resting Minimum SPO2 Rate");
        healthyMinSPO2.setLineColor(Color.GREEN);
        healthyMinSPO2.setLineWidth(2f);
        healthyMinSPO2.setTextColor(Color.BLACK);
        healthyMinSPO2.setTextSize(12f);
        healthyMinSPO2.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
        yAxisSPO2.addLimitLine(healthyMinSPO2);
        // disable legend that appears at the bottom of the screen
        Legend spo2Legend = spo2Chart.getLegend();
        spo2Legend.setEnabled(false);
        // initialize the list holding the data for SPO2
        ArrayList<Entry> valsComp3 = new ArrayList<Entry>();
        // initialize the data into a data holder for the line graph
        LineDataSet setComp3 = new LineDataSet(valsComp3, "SPO2");
        setComp3.setAxisDependency(YAxis.AxisDependency.LEFT);
        setComp3.setColor(Color.GREEN);
        setComp3.setDrawCircles(false);
        setComp3.setDrawValues(false);
        // use the interface ILineDataSet
        ArrayList<ILineDataSet> dataSets3 = new ArrayList<ILineDataSet>();
        dataSets3.add(setComp3);
        // bind the data to the graph
        spo2Data = new LineData(xValsSPO2, dataSets3);
        spo2Chart.setData(spo2Data);
        spo2Chart.invalidate(); // refresh the graph



        _brsp = new Brsp(_brspCallback, 10000, 10000);
        doScan();
    }

    @Override
    protected void onDestroy() {
        //Log.d(TAG, "onDestroy");
        doDisconnect();
        this.unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //Log.d(TAG, "onPrepareOptionsMenu");
        if (_selectedDevice != null) {
            MenuItem item;
            MenuItem connectStatusItem;
            String menuText;

            item = menu.findItem(R.id.menu_action_connect);
            connectStatusItem = menu.findItem(R.id.menu_action_connect_status);
            if (_selectedDevice != null) {
                item.setVisible(true);
                if (_brsp != null && _brsp.getConnectionState() == BluetoothGatt.STATE_CONNECTED) {
                    item.setIcon(R.drawable.connect);
                    connectStatusItem.setIcon(R.drawable.connect);
                    item.setTitle("Disconnect");
                } else {
                    item.setIcon(R.drawable.disconnect);
                    connectStatusItem.setIcon(R.drawable.disconnect);
                    item.setTitle("Connect");
                }

            } else {
                item.setVisible(false);
            }

            // Add item for changing brsp mode
            item = menu.findItem(R.id.menu_action_brspmode);
            if (_brsp != null && _brsp.getConnectionState() == BluetoothGatt.STATE_CONNECTED) {
                item.setVisible(true);
                switch (_brsp.getBrspMode()) {
                    case Brsp.BRSP_MODE_DATA:
                        menuText = "Data Mode";
                        break;
                    case Brsp.BRSP_MODE_COMMAND:
                        menuText = "Command Mode";
                        break;
                    default:
                        menuText = "";
                        // Not supported in this sample
                }
                item.setTitle(menuText);
            } else {
                item.setVisible(false);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (_brsp == null)
            return false;
        //Log.d(TAG, "onOptionsItemSelected");
        //Log.d(TAG, "Title selected = " + item.getTitle());
        switch (item.getItemId()) {
            case R.id.menu_action_connect:
            case R.id.menu_action_connect_status:
                if (_brsp != null && _brsp.getConnectionState() == BluetoothGatt.STATE_DISCONNECTED)
                    doConnect();
                else if (_brsp != null && _brsp.getConnectionState() == BluetoothGatt.STATE_CONNECTED)
                    doDisconnect();
                break;
            case R.id.menu_action_scan:
                doScan();
                break;
            case R.id.menu_action_brspmode:
                if (_brsp != null)
                    if (_brsp.getBrspMode() == Brsp.BRSP_MODE_DATA)
                        _brsp.setBrspMode(Brsp.BRSP_MODE_COMMAND);
                    else
                        _brsp.setBrspMode(Brsp.BRSP_MODE_DATA);
                break;
            case R.id.menu_action_exit:
                doQuit();
                break;
            case R.id.menu_action_clear_output:
                //_textViewOutput.setText("");
                break;
            case R.id.menu_action_version:
                PackageInfo pInfo;
                try {
                    pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    String versionMsg = "Version " + pInfo.versionName;
                    Toast.makeText(MainActivity.this, versionMsg, Toast.LENGTH_LONG).show();
                } catch (NameNotFoundException e) {
                    e.printStackTrace();
                }
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Log.d(TAG, "onKeyDown keyCode:" + keyCode + " event:" + event.toString());
        if (keyCode == KeyEvent.KEYCODE_BACK && isTaskRoot()) {
            doQuit();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Log.d(TAG, "onActivityResult requestCode:" + requestCode + " resultCode:" + resultCode);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case ScanActivity.REQUEST_SELECT_DEVICE:
                    if (resultCode == RESULT_OK) {
                        _selectedDevice = data.getParcelableExtra("device");
                        setTitle(data.getStringExtra("title"));
                        invalidateOptionsMenu();
                        doDisconnect();
                        doConnect();
                    }
                    break;
                default:
                    //Log.w(TAG, "Unknown requestCode encountered in onActivityResult.  Ignoring code:" + requestCode);
                    break;
            }

        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void hideSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);
    }

    private void doScan() {
        Intent i = new Intent(this, ScanActivity.class);
        startActivityForResult(i, ScanActivity.REQUEST_SELECT_DEVICE);
    }

    private void doConnect() {
        if (_selectedDevice != null && _brsp.getConnectionState() == BluetoothGatt.STATE_DISCONNECTED) {
            boolean result = false;

            String bondStateText = "";
            switch (_selectedDevice.getBondState()) {
                case BluetoothDevice.BOND_BONDED:
                    bondStateText = "BOND_BONDED";
                    break;
                case BluetoothDevice.BOND_BONDING:
                    bondStateText = "BOND_BONDING";
                    break;
                case BluetoothDevice.BOND_NONE:
                    bondStateText = "BOND_NONE";
                    break;
            }
            //Log.d(TAG, "Bond State:" + bondStateText);

            result = _brsp.connect(this.getApplicationContext(), _selectedDevice);
            //Log.d(TAG, "Connect result:" + result);
        }
    }

    private void doDisconnect() {
        if (_brsp != null && _brsp.getConnectionState() != BluetoothGatt.STATE_DISCONNECTED) {
            //Log.d(TAG, "Atempting to disconnect");
            _brsp.disconnect();
        }
    }

    private void doQuit() {
        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.msg_quit_title)
                .setMessage(R.string.msg_quit_detail).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        }).setNegativeButton(R.string.no, null).show();
    }
}