package com.example.robbieginsburg.test;

import android.os.AsyncTask;
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
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.graphics.Color;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private final String TAG = "BRSPTERM." + this.getClass().getSimpleName();
    private static final int MAX_OUTPUT_LINES = 100 + 1;

    private final int MAX_DATA = 1500;
    private final int FS = 50;
    private int numSeconds = 5;

    private Brsp _brsp;
    private BluetoothDevice _selectedDevice;

    private TextView _textViewOutput;
    private ScrollView _scrollView;

    // initialize freq array
    double[] freq = new double[MAX_DATA / 2];

    double[] REDLED = new double[0];
    double[] IRLED = new double[0];

    SmoothFreq smoothFreq;

    LineChart heartRateChart;
    ArrayList<String> xVals = new ArrayList<String>();
    LineData data;

    private BrspCallback _brspCallback = new BrspCallback() {

        @Override
        public void onSendingStateChanged(Brsp obj) {
            Log.d(TAG, "onSendingStateChanged thread id:" + Process.myTid());
        }

        @Override
        public void onConnectionStateChanged(Brsp obj) {
            Log.d(TAG, "onConnectionStateChanged state:" + obj.getConnectionState() + " thread id:" + Process.myTid());
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
        public void onDataReceived(final Brsp obj) {
            //Log.d(TAG, "onDataReceived thread id:" + Process.myTid());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    byte[] bytes = obj.readBytes();

                    if (bytes != null) {

                        String input = new String(bytes);

                        double[] addToRED = null;
                        double[] addToIR = null;
                        double[] tmpRED = null;
                        double[] tmpIR = null;

                        if(input.length() == 6){
                            input = input.substring(0, 6) + "\n";

                            // get DC values
                            double val1 = (char) bytes[0] | ((char) bytes[1] << 8);
                            double val2 = (char) bytes[3] | ((char) bytes[4] << 8);

                            // convert DC values to AC
                            val1 *= (1.2/(2^15));
                            val2 *= (1.2/(2^15));

                            addToRED = new double[1];
                            addToRED[0] = val1;

                            addToIR = new double[1];
                            addToIR[0] = val2;

                            // create a tmp array that is the size of the two arrays
                            tmpRED = new double[REDLED.length + addToRED.length];
                            tmpIR = new double[IRLED.length + addToIR.length];
                        }
                        else if(input.length() == 12){
                            input = input.substring(0, 6) + "\n" + input.substring(6, 12) + "\n";

                            // get DC values
                            double val1 = (char) bytes[0] | ((char) bytes[1] << 8);
                            double val2 = (char) bytes[3] | ((char) bytes[4] << 8);
                            double val3 = (char) bytes[6] | ((char) bytes[7] << 8);
                            double val4 = (char) bytes[9] | ((char) bytes[10] << 8);

                            // convert DC values to AC
                            val1 *= (1.2/(2^15));
                            val2 *= (1.2/(2^15));
                            val3 *= (1.2/(2^15));
                            val4 *= (1.2/(2^15));

                            addToRED = new double[2];
                            addToRED[0] = val1;
                            addToRED[1] = val3;

                            addToIR = new double[2];
                            addToIR[0] = val2;
                            addToIR[1] = val4;

                            // create a tmp array that is the size of the two arrays
                            tmpRED = new double[REDLED.length + addToRED.length];
                            tmpIR = new double[IRLED.length + addToIR.length];
                        }
                        else if(input.length() == 18){
                            input = input.substring(0, 6) + "\n" + input.substring(6, 12) + "\n" + input.substring(12, 18) + "\n";

                            // get DC values
                            double val1 = (char) bytes[0] | ((char) bytes[1] << 8);
                            double val2 = (char) bytes[3] | ((char) bytes[4] << 8);
                            double val3 = (char) bytes[6] | ((char) bytes[7] << 8);
                            double val4 = (char) bytes[9] | ((char) bytes[10] << 8);
                            double val5 = (char) bytes[12] | ((char) bytes[13] << 8);
                            double val6 = (char) bytes[15] | ((char) bytes[15] << 8);

                            // convert DC values to AC
                            val1 *= (1.2/(2^15));
                            val2 *= (1.2/(2^15));
                            val3 *= (1.2/(2^15));
                            val4 *= (1.2/(2^15));
                            val5 *= (1.2/(2^15));
                            val6 *= (1.2/(2^15));

                            addToRED = new double[3];
                            addToRED[0] = val1;
                            addToRED[1] = val3;
                            addToRED[2] = val5;

                            addToIR = new double[3];
                            addToIR[0] = val2;
                            addToIR[1] = val4;
                            addToIR[2] = val6;

                            // create a tmp array that is the size of the two arrays
                            tmpRED = new double[REDLED.length + addToRED.length];
                            tmpIR = new double[IRLED.length + addToIR.length];
                        }

                        if(addToRED != null && tmpRED!= null){
                            //System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
                            // copy REDLED/IRLED into start of tmp
                            System.arraycopy(REDLED, 0, tmpRED, 0, REDLED.length);
                            System.arraycopy(IRLED, 0, tmpIR, 0, IRLED.length);

                            // copy addToRED/addToIR into end of tmp
                            System.arraycopy(addToRED, 0, tmpRED, REDLED.length, addToRED.length);
                            System.arraycopy(addToIR, 0, tmpIR, IRLED.length, addToIR.length);

                            // puts the now combined tmp array back into the original array
                            REDLED = tmpRED;
                            IRLED = tmpIR;
                        }

                        // will check to see if the arrays are >= 1500 after adding the value
                        // if so it will drop the oldest entrys
                        //System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
                        if(REDLED.length >= MAX_DATA){

                            int shiftBy = REDLED.length - MAX_DATA;

                            double[] tmpForShiftRED = new double[REDLED.length-shiftBy];
                            double[] tmpForShiftIR = new double[IRLED.length-shiftBy];

                            System.arraycopy(REDLED, shiftBy , tmpForShiftRED, 0, REDLED.length-shiftBy);
                            System.arraycopy(IRLED, shiftBy, tmpForShiftIR, 0, IRLED.length-shiftBy);

                            // puts the shifted tmp array back into the original array
                            REDLED = tmpForShiftRED;
                            IRLED = tmpForShiftIR;
                        }

                        Log.d("REDLED Length", "REDLED Length" + REDLED.length);
                        //Log.i("IRLED", IRLED.toString());

                        // addLineToTextView(input);

                    } else {
                        // This occasionally happens but no data should be lost
                    }
                }

                private void addLineToTextView(String lineText) {
                    _textViewOutput.append(lineText);
                    removeLinesFromTextView();
                    _scrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            _scrollView.fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    });
                }

                private void removeLinesFromTextView() {
                    int linesToRemove = _textViewOutput.getLineCount() - MAX_OUTPUT_LINES;
                    if (linesToRemove > 0) {
                        for (int i = 0; i < linesToRemove; i++) {
                            Editable text = _textViewOutput.getEditableText();
                            int lineStart = _textViewOutput.getLayout().getLineStart(0);
                            int lineEnd = _textViewOutput.getLayout().getLineEnd(0);
                            text.delete(lineStart, lineEnd);
                        }
                    }
                }

            });
            super.onDataReceived(obj);
        }

        @Override
        public void onError(Brsp obj, Exception e) {
            Log.e(TAG, "onError:" + e.getMessage() + " thread id:" + Process.myTid());
            super.onError(obj, e);
            if (e instanceof UnstableException) {
                Log.d(TAG, "Unstable Caught");
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
            Log.d(TAG, "onRssiUpdate thread id:" + Process.myTid());
            super.onRssiUpdate(obj);
            Log.d(TAG, "Remote device RSSI:" + obj.getLastRssi()); // Log RSSI
        }

        @Override
        public void onBrspStateChanged(Brsp obj) {
            super.onBrspStateChanged(obj);
            int currentState = obj.getBrspState();
            Log.d(TAG, "onBrspStateChanged thread id:" + Process.myTid() + " State:" + currentState);
            obj.readRssi(); // read the RSSI once
            if (obj.getBrspState() == Brsp.BRSP_STATE_READY) {
                Log.d(TAG, "BRSP READY");
                // Ready to write
                // _brsp.writeBytes("Test".getBytes());
            } else {
                Log.d(TAG, "BRSP NOT READY");
            }
        }

    };

    // async task that constantly gets the user's location
    private class SmoothFreq extends AsyncTask<String, Integer, String> {

        @Override
        // perform the smooth and freq functions
        protected String doInBackground(String... params) {
            //System.arraycopy(Object src, int srcPos, Object dest, int destPos, int length)

            //if(REDLED.length % 100 == 0) Log.d("REDLED.Length", "Length: " + REDLED.length);

            // checks to make sure the buffer is full
            if(REDLED.length == MAX_DATA) {

//                byte[] smooth = new byte[1560];
//                byte[] hanning = new byte[31];
//                byte[] afterShift = new byte[1530];
//
//                byte[] tmp = new byte[30];
//                byte[] tmp2 = new byte[30];
//
//                final int WINDOWLENGTH = 31;
//
//                // **************************************************************** smooth function
//                // gets the first 30 elements from REDLED and stores them in tmp
//                System.arraycopy(REDLED, 0, tmp, 0, WINDOWLENGTH - 1);
//                // gets the last 30 elements from REDLED and stores them in tmp2
//                System.arraycopy(REDLED, 1469, tmp2, 0, WINDOWLENGTH - 1);
//
//                // reverses them
//                // will be added to the smooth array
//                Collections.reverse(Arrays.asList(tmp));
//                Collections.reverse(Arrays.asList(tmp2));
//
//                // creates the smooth array
//                System.arraycopy(tmp, 0, smooth, 0, 0);
//                System.arraycopy(REDLED, 0, smooth, WINDOWLENGTH, 1500);
//                System.arraycopy(tmp2, 0, smooth, 1530, 30);
//
//                // creates the hanning array and gets the sum of all the elements in it
//                byte sum = 0;
//                for (int i = 0; i < WINDOWLENGTH; i++) {
//                    hanning[i] = (byte) (.5 - .5 * Math.cos((2 * Math.PI * i) / (WINDOWLENGTH - 1)));
//                    sum += hanning[i];
//                }
//
//                // convolute the above arrays
//                for (int i = 0; i < hanning.length; i++) {
//                    hanning[i] = (byte) (hanning[i] / sum);
//                }
//
//                //convolve the two arrays
//
//                // **************************************************************** smooth function
//
//                System.arraycopy(smooth, 31, afterShift, 0, afterShift.length);

                // **************************************************************** freq function
                // convert byte array to double array
                /*ByteBuffer buf = ByteBuffer.wrap(smooth);
                double[] smoothDouble = new double[smooth.length / 8];
                for (int i = 0; i < smoothDouble.length; i++)
                    smoothDouble[i] = buf.getLong(i*8);*/

                // start of fft transform ********************************************************
                // Computes the discrete Fourier transform (DFT) of the given vector.
                // All the array arguments must have the same length.
                double[] inReal = REDLED;
                double[] inImag = new double[REDLED.length];
                double[] fftOutput = new double[REDLED.length];

                int n = inReal.length;
                for (int i = 0; i < n; i++) {  // For each output element
                    double sumreal = 0;
                    //double sumimag = 0;
                    for (int j = 0; j < n; j++) {  // For each input element
                        double angle = (2 * Math.PI * j * i / n);
                        sumreal += inReal[j] * Math.cos(angle) + inImag[j] * Math.sin(angle);
                    }
                    fftOutput[i] = sumreal;
                }
                // end of fft transform ********************************************************

                // the indexes of the freq array where the values are between .2/.5 are 6/15
                // the indexes of the freq array where the values are between .9/1.4 are 27/42

                // find the index of the max value in the FFT array that corresponds to the first range     // respirator
                // find the index of the max value in the FFT array that corresponds to the second range     // heart rate
                double[] respiratorRange = new double[9];
                double[] heartRateRange = new double[15];
                System.arraycopy(fftOutput, 6, respiratorRange, 0, 9);
                System.arraycopy(fftOutput, 27, heartRateRange, 0, 15);

                // find the index of the max value in the respiratorRange array
                int respMaxIndex = 0;
                double respMax = respiratorRange[0];
                for(int i = 0; i < respiratorRange.length-1; i++){
                    if(respMax > respiratorRange[i+1]){
                        respMax = respiratorRange[i+1];
                        respMaxIndex = i + 1;
                    }
                }

                // find the index of the max value in the heartRateRange array
                int heartMaxIndex = 0;
                double heartMax = heartRateRange[0];
                for(int i = 0; i < heartRateRange.length-1; i++){
                    if(heartMax > heartRateRange[i+1]){
                        heartMax = heartRateRange[i+1];
                        heartMaxIndex = i + 1;
                    }
                }

                // takes indexes of max and look them up in freq array and multiply by 50
                double respiratorRate = freq[6 + respMaxIndex] * 50;
                double heartRate = freq[27 + heartMaxIndex] * 50;
                addEntry(heartRate);

                Log.d("Respiration Rate: ", "Respiration Rate: " + respiratorRate);
                Log.d("Heart Rate: ", "Heart Rate: " + heartRate);
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {

            // do stuff

            // make the async task repeat itself
            smoothFreq = new SmoothFreq();
            smoothFreq.execute();
        }
    }

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

    private void addEntry(double heartRate) {Log.i("here987", "here");

        xVals.add(String.valueOf(numSeconds+1));
        data.addEntry(new Entry((float) heartRate, numSeconds), 0);
        heartRateChart.notifyDataSetChanged(); // let the chart know it's data changed
        heartRateChart.invalidate(); // refresh
        numSeconds++;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // calculates the frequencies
        for (int i = 0; i < freq.length; i++) {
            freq[i] = (((double) i * (double) FS) / (double) MAX_DATA);
        }

        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        IntentFilter adapterStateFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        this.registerReceiver(mReceiver, adapterStateFilter);

        setContentView(R.layout.activity_main);
        /*_textViewOutput = (TextView) findViewById(R.id.textViewOutput);
        _scrollView = (ScrollView) findViewById(R.id.scrollView);
        _textViewOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSoftKeyboard();
            }
        });*/


        heartRateChart = (LineChart) findViewById(R.id.heartRateChart);
        heartRateChart.setDescription("");
        heartRateChart.setNoDataTextDescription("It takes up to 30 seconds to start collecting data from you.");
        heartRateChart.setTouchEnabled(false);

        // define info about x axis
        XAxis xAxis = heartRateChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(10f);
        xAxis.setTextColor(Color.BLUE);
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);

        // define info about y axis
        YAxis yAxis = heartRateChart.getAxisLeft();
        heartRateChart.getAxisRight().setEnabled(false);
        yAxis.setAxisMinValue(0);
        yAxis.setTextSize(10f);
        yAxis.setTextColor(Color.BLUE);
        yAxis.setDrawAxisLine(true);
        yAxis.setDrawGridLines(false);

        ArrayList<Entry> valsComp1 = new ArrayList<Entry>();
        ArrayList<Entry> valsComp2 = new ArrayList<Entry>();

        Entry c1e1 = new Entry(100.000f, 0); // 0 == quarter 1
        valsComp1.add(c1e1);
        Entry c1e2 = new Entry(107.000f, 1); // 1 == quarter 2
        valsComp1.add(c1e2);
        Entry c1e3 = new Entry(120.000f, 2); // 2 == quarter 3
        valsComp1.add(c1e3);
        Entry c1e4 = new Entry(123.000f, 3); // 3 == quarter 4
        valsComp1.add(c1e4);

        Entry c2e1 = new Entry(120.000f, 0); // 0 == quarter 1
        valsComp2.add(c2e1);
        Entry c2e2 = new Entry(110.000f, 1); // 1 == quarter 2
        valsComp2.add(c2e2);
        Entry c2e3 = new Entry(115.000f, 2); // 2 == quarter 3
        valsComp2.add(c2e3);
        Entry c2e4 = new Entry(132.000f, 3); // 3 == quarter 4
        valsComp2.add(c2e4);

        LineDataSet setComp1 = new LineDataSet(valsComp1, "Company 1");
        setComp1.setAxisDependency(YAxis.AxisDependency.LEFT);
        LineDataSet setComp2 = new LineDataSet(valsComp2, "Company 2");
        setComp2.setAxisDependency(YAxis.AxisDependency.LEFT);

        // use the interface ILineDataSet
        ArrayList<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
        dataSets.add(setComp1);
        dataSets.add(setComp2);

        //ArrayList<String> xVals = new ArrayList<String>();
        xVals.add("1.Q");
        xVals.add("2.Q");
        xVals.add("3.Q");
        xVals.add("4.Q");

        data = new LineData(xVals, dataSets);
        heartRateChart.setData(data);
        heartRateChart.invalidate(); // refresh

        // add entries to the "data" object
        xVals.add("5");
        data.addEntry(new Entry(169.000f, 4), 0);
        heartRateChart.notifyDataSetChanged(); // let the chart know it's data changed
        heartRateChart.invalidate(); // refresh

        _brsp = new Brsp(_brspCallback, 10000, 10000);
        doScan();

        // start asynctask that constantly runs the smooth and freq functions on the received data
        smoothFreq = new SmoothFreq();
        smoothFreq.execute();

    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
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
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
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
        Log.d(TAG, "onOptionsItemSelected");
        Log.d(TAG, "Title selected = " + item.getTitle());
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
                _textViewOutput.setText("");
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
        Log.d(TAG, "onKeyDown keyCode:" + keyCode + " event:" + event.toString());
        if (keyCode == KeyEvent.KEYCODE_BACK && isTaskRoot()) {
            doQuit();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult requestCode:" + requestCode + " resultCode:" + resultCode);
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
                    Log.w(TAG, "Unknown requestCode encountered in onActivityResult.  Ignoring code:" + requestCode);
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
            Log.d(TAG, "Bond State:" + bondStateText);

            result = _brsp.connect(this.getApplicationContext(), _selectedDevice);
            Log.d(TAG, "Connect result:" + result);
        }
    }

    private void doDisconnect() {
        if (_brsp != null && _brsp.getConnectionState() != BluetoothGatt.STATE_DISCONNECTED) {
            Log.d(TAG, "Atempting to disconnect");
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