package com.example.robbieginsburg.test;

import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

/**
 * This class provides support for the BlueRadios Serial Port (BRSP) service on
 * AT.s modules. Each instance communicates with one BLE peripheral at a time.
 * <b>View this <a target="_blank" href="../../README.html">README</a> for
 * important information and limitations before using this API.</b>
 */
public class Brsp {
    private final String TAG = "BRSPLIB." + this.getClass().getSimpleName();
    private static final int DEFAULT_BUFFER_SIZE = 1024;
    private int _iBufferSize;
    private int _oBufferSize;
    private ArrayBlockingQueue<Double> _inputBuffer;
    private ArrayBlockingQueue<Double> _outputBuffer;

    SmoothFreq smoothFreq;

    private final int MAX_DATA = 1500;
    private final int FS = 50;

    // initialize freq array
    double[] freq = new double[MAX_DATA / 2];

    double[] REDLED = new double[0];
    double[] IRLED = new double[0];

    private int dataCount = 0;
    byte[] byteData;

    private BrspCallback _brspCallback;

    // private BluetoothGattService _brspGattService;

    // Used with writeWithResponse mode
    // true if a write was sent and no response has come back yet. NO when the
    // last response is received
    // This flag is used to prevent a write without receiving a response for the
    // previous write
    private boolean _sending;
    private byte[] _lastBytes; // Last bytes sent to remote device
    private int _lastRTS = 0;
    private static final int _packetSize = 20; // Max bytes to send to remote device on each write
    private long _securityLevel;
    private int _initState = 0; // Used for writing setup characteristics and descriptors.
    // The current init step
    private static final int _initStepCount = 3; // 0 based

    boolean _isClosing = false; // Used for hack to not call gatt.close() more than once

    // Initializes or reinitializes the object. Should be called on disconnect.
    private void init() {
        //debugLog("init()");
        boolean brspStateChanged = _brspState != 0;
        _initState = 0;
        _brspState = 0;
        _brspMode = 0;
        _lastRssi = 0;
        _lastRTS = 0;
        _securityLevel = 0;
        setBuffers(_inputBuffer.size() + _inputBuffer.remainingCapacity(), _outputBuffer.size() + _outputBuffer.remainingCapacity());
        if (brspStateChanged)
            _brspCallback.onBrspStateChanged(this);
    }

    /**
     * The BRSP Service UUID
     */
    public static final UUID BRSP_SERVICE_UUID = UUID.fromString("DA2B84F1-6279-48DE-BDC0-AFBEA0226079");

    private static final UUID BRSP_INFO_UUID = UUID.fromString("99564A02-DC01-4D3C-B04E-3BB1EF0571B2");
    private static final UUID BRSP_MODE_UUID = UUID.fromString("A87988B9-694C-479C-900E-95DFA6C00A24");
    private static final UUID BRSP_RX_UUID = UUID.fromString("BF03260C-7205-4C25-AF43-93B1C299D159");
    private static final UUID BRSP_TX_UUID = UUID.fromString("18CDA784-4BD3-4370-85BB-BFED91EC86AF");
    private static final UUID BRSP_CTS_UUID = UUID.fromString("0A1934F5-24B8-4F13-9842-37BB167C6AFF");
    private static final UUID BRSP_RTS_UUID = UUID.fromString("FDD6B4D3-046D-4330-BDEC-1FD0C90CB43B");

    /**
     * True if sending data to remote device
     *
     * @return true if output buffer is not empty
     */
    public boolean isSending() {
        //debugLog("isSending()");
        return !_outputBuffer.isEmpty();
    }

    private WeakReference<Context> _context;
    private BluetoothGatt _gatt = null;
    private int _lastRssi;

    /**
     * The last rssi value
     *
     * @return The last RSSI returned by a call to {@link #readRssi()}. Will be
     *         0 if a read was not performed during a connection
     */
    public int getLastRssi() {
        //debugLog("getLastRssi()");
        return _lastRssi;
    }

    /**
     * Returns the BluetoothGatt state of the remote device
     *
     * @return The BluetoothGatt current connection state
     */
    public int getConnectionState() {
        //debugLog("getCOnnectionState()");
        int returnVal = BluetoothGatt.STATE_DISCONNECTED;
        if (_gatt != null) {
            BluetoothManager manager = (BluetoothManager) _context.get().getSystemService(Context.BLUETOOTH_SERVICE);
            returnVal = manager.getConnectionState(_gatt.getDevice(), BluetoothGatt.GATT);
        } else {
            // Log.w(TAG, "Internal gatt object is null");
        }
        return returnVal;
    }

    private int _brspState = 0;
    /**
     * Service and characteristics have not been setup yet.
     */
    public static final int BRSP_STATE_NOT_READY = 0;
    /**
     * Ready to perform writes and reads
     */
    public static final int BRSP_STATE_READY = 1;

    /**
     * Gets the current state of the BRSP service. If {@link #BRSP_STATE_READY}
     * the service is ready for sending and receiving.
     *
     * @return {@link #BRSP_STATE_READY} or {@link #BRSP_STATE_NOT_READY}
     */
    public int getBrspState() {
        //debugLog("getBrspState()");
        return _brspState;
    }

    private int _brspMode = 0;
    /**
     * Idle mode.
     */
    public static final int BRSP_MODE_IDLE = 0;
    /**
     * Data pass-through mode.
     */
    public static final int BRSP_MODE_DATA = 1;
    /**
     * Remote command mode.
     */
    public static final int BRSP_MODE_COMMAND = 2;
    /**
     * Firmware update mode. Not supported at this time.
     */
    public static final int BRSP_MODE_FIRMWARE_UPDATE = 4;

    /**
     * Changes the BRSP mode of the remote device.
     *
     * @param mode
     *            The new mode. Currently supports {@link #BRSP_MODE_DATA} and
     *            {@link #BRSP_MODE_COMMAND}
     * @return true if a successful write request was sent.
     */
    public boolean setBrspMode(int mode) {
        //debugLog("setBrspMode()");
        if (mode != 0 && mode != 1 && mode != 2 && mode != 4) {
            sendError("setBrspMode failed because mode:" + mode + " is invalid.");
            return false;
        }

        if (mode == 4) {
            sendError("setBrspMode failed because mode:" + mode + " is not supported at this time.");
            return false;
        }

        boolean result = false;
        byte[] newMode = new byte[1];
        newMode[0] = (byte) mode;

        BluetoothGattCharacteristic characteristicMode = _gatt.getService(BRSP_SERVICE_UUID).getCharacteristic(BRSP_MODE_UUID);
        if (characteristicMode != null) {
            characteristicMode.setValue(newMode);
            result = _gatt.writeCharacteristic(characteristicMode);
        } else {
            sendError("Can't find characteristic for brsp mode");
        }
        return result;
    }

    /**
     * Gets the current BRSP mode
     *
     * @return The current BRSP mode. Currently supports {@link #BRSP_MODE_DATA}
     *         and {@link #BRSP_MODE_COMMAND}
     */
    public int getBrspMode() {
        //debugLog("getBrspMode()");
        return _brspMode;
    }

    /**
     * Gets the current remote device
     *
     * @return The current BluetoothDevice. Null if connect was never called
     *         with a valid device
     */
    public BluetoothDevice getDevice() {
        //debugLog("getDevice()");
        BluetoothDevice dev = null;
        if (_gatt != null)
            dev = _gatt.getDevice();
        return dev;
    }

    private BluetoothGattCallback _gattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            //debugLog("onCharacteristicChanged()");
            // debugLog("onCharacteristicChanged:" +
            // characteristic.getUuid().toString());
            if (_initState < _initStepCount) {
                doNextInitStep();
            } else {
                if (characteristic.getUuid().equals(BRSP_TX_UUID)) {
                    // Incoming data
                    byte[] rawBytes = characteristic.getValue();

                    // this will be comprised of a valid 6 bits data object to send
                    // when it is a good packet to send (see below), it will be added to
                    // the bytesToSend byte array
                    // *** the leftover bytes from the previous iteration of this method will
                    // *** still be stored in this variable because it is global
                    byteData = new byte[6];

                    // this will be comprised of all valid byte objects to send
                    // at most it will contain 3 valid byte objects
                    // each valid byte object is 6 bits, and only 20 can be sent at once
                    // 3 valid byte objects would equal 18 bits
                    // the '*'s and '\n's will be stripped off before being sent enabling it to be
                    // 18 bytes
                    byte[] validBytesToSend = null;

                    // represents the number of valid byte objects being sent in this iteration
                    // of the method
                    int validCount = 0;

                    // for each byte in the 20 bits that were sent in the packet
                    for(byte byteChar : rawBytes){

                        // if the byte is a star, reset the count and byte array of current object
                        if((char)byteChar == '*'){
                            dataCount = 0;
                            byteData = new byte[6];
                        }
                        // if byte is endline character
                        else if(((char)byteChar == '\n')){
                            // and the dataCount is at 6, that means there is a valid 6 bits object,
                            // and it can be saved fort transmission
                            if(dataCount == 6){
                                //Log.i("Tag2", "------ This is a good packet to send ------");

                                // if this is the first valid byte object of the 20 bits packet
                                if (validCount == 0){
                                    // store it in the byte array to be transmitted
                                    validBytesToSend = byteData;
                                    validCount++;
                                }
                                // if this is the second or third valid byte object of the 20 byte packet
                                else if (validCount >= 1){

                                    // create a tmp array that is the size of the two arrays
                                    byte[] tmp = new byte[byteData.length + validBytesToSend.length];

                                    // copy byteData into start of tmp
                                    System.arraycopy(byteData, 0, tmp, 0, byteData.length);

                                    // copy validBytesToSend into end of tmp
                                    System.arraycopy(validBytesToSend, 0, tmp, byteData.length, validBytesToSend.length);

                                    // puts tmp into the byte array to be transmitted
                                    validBytesToSend = tmp;

                                    validCount++;
                                }
                                // there can not be more than 3 valid byte objects (max 18 bits)
                            }

                            // reset
                            dataCount = 0;
                            byteData = new byte[6];
                        }
                        // this checks for bad data
                        else if(dataCount >= 6){
                            // reset
                            dataCount = 0;
                            byteData = new byte[6];
                        }
                        // adds the current byte to a tmp byte array that might be transmitted
                        // if it turns out to be a valid byte object (6 bits and follows the rules above)
                        else if((char)byteChar != '*' && (char)byteChar != '\n'){

                            byteData[dataCount] = byteChar;
                            dataCount++;
                        }
                    }

                    // addToBuffer(_inputBuffer, validBytesToSend); *****************************
                    if(validBytesToSend != null && (validBytesToSend.length == 6 ||
                            validBytesToSend.length == 12 || validBytesToSend.length == 18)){
                        addToLedArrays(validBytesToSend);
                    }

                    //_brspCallback.onDataReceived(Brsp.this);
                } else if (characteristic.getUuid().equals(BRSP_RTS_UUID)) {
                    _lastRTS = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                    sendPacket();
                }
            }
            super.onCharacteristicChanged(gatt, characteristic);
        }

        public void addToLedArrays(byte[] validBytes){
            //debugLog("addToLedArrays()");
            byte[] bytes = validBytes;

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

                //Log.d("REDLED Length", "REDLED Length" + REDLED.length);
                //Log.i("IRLED", IRLED.toString());

                // addLineToTextView(input);

            } else {
                // This occasionally happens but no data should be lost
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //debugLog("onCharacteristicRead()");
            // debugLog("onCharacteristicRead:" +
            // characteristic.getUuid().toString());
            if (_initState < _initStepCount) {
                doNextInitStep();
            }
            if (characteristic.getUuid().equals(BRSP_INFO_UUID)) {
                _securityLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
                debugLog("Current BRSP Security Level set to:" + _securityLevel);
            }
            super.onCharacteristicRead(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            //debugLog("onCharacteristicWrite()");
            debugLog("onCharacteristicWrite:" + characteristic.getUuid().toString() + " status:" + status);
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (_initState < _initStepCount) {
                doNextInitStep();
            }
            if (characteristic.getUuid().equals(BRSP_RX_UUID)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    _lastBytes = null;
                    if (_outputBuffer.isEmpty())
                        _brspCallback.onSendingStateChanged(Brsp.this);
                }
                _sending = false;
                sendPacket();
                // debugLog("RX characteristic wrote");
            } else if (characteristic.getUuid().equals(BRSP_MODE_UUID)) {
                _brspMode = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                _brspCallback.onBrspModeChanged(Brsp.this);
                if (_brspState != BRSP_STATE_READY) {
                    _brspState = BRSP_STATE_READY;
                    _brspCallback.onBrspStateChanged(Brsp.this);
                }
            }
            if (status != 0) {
                sendError("Exception occurred during characteristic write.  status:" + status);
                if (status == 15) {
                    // Can't figure out a fix to the pairing issues as of yet
                    // _gatt.getDevice().createBond();
                    // TODO: Resend last write once bonded?
                }
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            //debugLog("onConnectionStateChange()");
            super.onConnectionStateChange(gatt, status, newState);
            // debugLog("onConnectionStateChange status:" + status +
            // " newstate:" + newState);
            debugLog("onConnectionStateChange status:" + status + " getConnectionState:" + getConnectionState());

            // teena: it seems to be returning incorrect state
            // Once the bluetooth api gets unstable, the newstate value can not
            // be trusted to be correct.
            // getConnectionState() here instead
            final int connectionState = getConnectionState();

            if (_isClosing && connectionState == BluetoothGatt.STATE_CONNECTED) {
                _isClosing = false;
                String errMsg = "Internal Gatt Connection State changed to BluetoothGatt.STATE_CONNECTED after a disconnect sent.  Bluetooth may have become unstable!";
                sendError(new UnstableException(errMsg));
                return;
            }

            switch (connectionState) {
                case BluetoothGatt.STATE_CONNECTED:
                    // debugLog("Discovering services");
                    _gatt.discoverServices();
                    break;
                case BluetoothGatt.STATE_DISCONNECTED:
                    // Status for disconnected does not always seem to get fired and
                    // gatt close and recreate is needed due to bugs
                    // in the current google api
                    init();
                    // close();

                    // As a work around for some instability, no status update will
                    // be fired here for disconnect.
                    // This library will send disconnect state upon calling
                    // _gatt.close()
                    break;
                case BluetoothGatt.STATE_CONNECTING:
                case BluetoothGatt.STATE_DISCONNECTING:
                default:
                    break;
            }
            _brspCallback.onConnectionStateChanged(Brsp.this);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //debugLog("onDescriptorRead()");
            // debugLog("onDescriptorRead");
            if (_initState < _initStepCount) {
                doNextInitStep();
            }
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            //debugLog("onDescriptorWrite()");
            // debugLog("onDescriptorWrite");
            if (_initState < _initStepCount) {
                doNextInitStep();
            }
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            //debugLog("onReadRemoteRssi()");
            // debugLog("onReadRemoteRssi");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _lastRssi = rssi;
                _brspCallback.onRssiUpdate(Brsp.this);
            } else {
                if (status == BluetoothGatt.GATT_FAILURE)
                    sendError("Error occurred trying to retrieve RSSI");
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            //debugLog("onReliableWriteCompleted()");
            // debugLog("onReliableWriteCompleted");
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            //debugLog("onServicesDiscovered()");
            debugLog("onServicesDiscovered status:" + status);
            super.onServicesDiscovered(gatt, status);
            BluetoothGattService brspService = gatt.getService(BRSP_SERVICE_UUID);
            if (brspService != null) {
                // Call the first write descriptor for initializing the BRSP
                // serrvice.
                _gatt.setCharacteristicNotification(brspService.getCharacteristic(BRSP_RTS_UUID), true);
                BluetoothGattDescriptor RTS_CCCD = brspService.getCharacteristic(BRSP_RTS_UUID).getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                RTS_CCCD.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                _gatt.writeDescriptor(RTS_CCCD);
            } else {
                sendError("Can't locate the BRSP service.");
            }
        }
    };
    private boolean _firstConnect;

    // Clean up the way this init works
    private void doNextInitStep() {
        //debugLog("doNextInitStep()");
        _initState++;
        // debugLog("initState:" + _initState);
        BluetoothGattService brspService = _gatt.getService(BRSP_SERVICE_UUID);
        switch (_initState) {
            case 1:
                _gatt.setCharacteristicNotification(brspService.getCharacteristic(BRSP_TX_UUID), true);
                BluetoothGattDescriptor TX_CCCD = brspService.getCharacteristic(BRSP_TX_UUID).getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                TX_CCCD.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                _gatt.writeDescriptor(TX_CCCD);
                break;
            case 2:
                BluetoothGattCharacteristic brspInfo = brspService.getCharacteristic(BRSP_INFO_UUID);
                _gatt.readCharacteristic(brspInfo);
                break;
            case 3:
                setBrspMode(BRSP_MODE_DATA); // Important: Make sure this is the
                // last init step
                break;
            default:
                // UhOh
        }
        if (_initState == _initStepCount) {
            // _brspState = BRSP_STATE_READY;
            // _brspCallback.onBrspStateChanged(this);
        }
    }



    // async task that smooths and performs the freq function
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

                double[] respHeartRates = new double[2];
                respHeartRates[0] = respiratorRate;
                respHeartRates[1] = heartRate;

                // call addToBuffer to pass Respiration/Heart Rate to MainActivity
                //addToBuffer(_inputBuffer, respHeartRates);

                //Log.d("Respiration Rate: ", "Respiration Rate: " + respiratorRate);
                //Log.d("Heart Rate: ", "Heart Rate: " + heartRate);

                // call addToBuffer to pass Respiration/Heart Rate to MainActivity
                addToBuffer(_inputBuffer, respHeartRates);
                _brspCallback.onDataReceived(respHeartRates);
            }
            else{
                Log.d("redlength", "redledlength: " + REDLED.length);
            }

            return null;
        }

        @Override
        protected void onPostExecute(String result) {

            // do stuff
//            try {
//                Thread.sleep(2000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            // make the async task repeat itself
            smoothFreq = new SmoothFreq();
            smoothFreq.execute();
        }
    }




    /**
     * Base constructor. Buffer sizes will be set to 1024
     *
     * @param callback
     *            BrspCallback object
     * @throws IllegalArgumentException
     *             if an callback is null
     */
    public Brsp(BrspCallback callback) {
        this(callback, DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Constructor with optional parameters to set buffer sizes. Note: If either
     * buffer is 0 or negative, it will be set to the default size of 1024
     *
     * @param callback
     *            BrspCallback object
     * @param inputBufferSize
     *            Size in bytes for the input buffer
     * @param outputBufferSize
     *            Size in bytes for the output buffer
     * @throws IllegalArgumentException
     *             if an callback is null
     *
     */
    public Brsp(BrspCallback callback, int inputBufferSize, int outputBufferSize) {
        //debugLog("Brsp()");
        if (callback == null)
            throw new IllegalArgumentException("callback can not be null");
        inputBufferSize = (inputBufferSize < 1) ? DEFAULT_BUFFER_SIZE : inputBufferSize;
        inputBufferSize = (inputBufferSize < 1) ? DEFAULT_BUFFER_SIZE : inputBufferSize;

        setBuffers(inputBufferSize, outputBufferSize);
        _brspCallback = callback;
    }

    private void setBuffers(int inputBufferSize, int outputBufferSize) {
        //debugLog("setBuffers()");
        _iBufferSize = inputBufferSize;
        _oBufferSize = outputBufferSize;
        _inputBuffer = new ArrayBlockingQueue<Double>(inputBufferSize);
        _outputBuffer = new ArrayBlockingQueue<Double>(outputBufferSize);
    }

    /**
     * The total capacity of the buffer
     *
     * @return Total InputBuffer capacity this object was created with. Note:
     *         This can not be changed after instantiation.
     */
    public int getInputBufferSize() {
        //debugLog("getInputBufferSize()");
        return _iBufferSize;
    }

    /**
     * The total capacity of the buffer
     *
     * @return Total OutputBuffer capacity this object was created with. Note:
     *         This can not be changed after instantiation.
     */
    public int getOutputBufferSize() {
        //debugLog("getOutputBufferSize()");
        return _oBufferSize;
    }

    /**
     * The amount of bytes currently in the buffer
     *
     * @return Number of bytes in the InputBuffer
     */
    public int getInputBufferCount() {
        //debugLog("getInputBufferCount()");
        return _inputBuffer.size();
    }

    /**
     * The amount of bytes currently in the buffer
     *
     * @return Number of bytes in the OutputBuffer
     */
    public int getOutputBufferCount() {
        //debugLog("getOutputBufferCount()");
        return _outputBuffer.size();
    }

    /**
     * Bytes available before buffer is full
     *
     * @return Number of bytes left that can be retrieved from device
     */
    public int intputBufferAvailableBytes() {
        //debugLog("intputBufferAvailableBytes()");
        return _inputBuffer.remainingCapacity();
    }

    /**
     * Bytes available before buffer is full
     *
     * @return Number of bytes left that can be written via writes
     */
    public int outputBufferAvailableBytes() {
        //debugLog("outputBufferAvailableBytes()");
        return _outputBuffer.remainingCapacity();
    }

    /**
     * Atomically removes all of the elements from the input buffer. The buffer
     * will be empty after this call returns.
     */
    public void clearInputBuffer() {
        //debugLog("clearInputBuffer()");
        _inputBuffer.clear();
    }

    /**
     * Atomically removes all of the elements from the output buffer. The buffer
     * will be empty after this call returns.
     */
    public void clearOutputBuffer() {
        //debugLog("clearOutputBuffer()");
        boolean sendingChanged = isSending();
        _outputBuffer.clear();
        if (sendingChanged)
            _brspCallback.onSendingStateChanged(this);
    }

    /**
     * Retrieves and removes all bytes in the input buffer.
     *
     * @return All bytes in the input buffer. Will return null if input buffer
     *         is empty.
     */
    public byte[] readBytes() {
        //debugLog("readBytes1()");
        int byteCount = getInputBufferCount();
        return readBytes(byteCount);

    }

    /**
     * Retrieves and removes specified number of bytes from the buffer.
     *
     * @return Specified amount of bytes. If byteCount < 1 will return null. If
     *         byteCount greater than bufferCount, will return all bytes.
     */
    public byte[] readBytes(int byteCount) {
        //debugLog("readBytes2()");
        return readBuffer(_inputBuffer, byteCount);
    }

    private byte[] readBuffer(ArrayBlockingQueue<Double> queue, int byteCount) {
        //debugLog("readBuffer()");
        int bytesInBuffer = queue.size();
        int bCount = (bytesInBuffer < byteCount) ? bytesInBuffer : byteCount;
        if (bCount < 1)
            return null;
        byte[] bytes = new byte[bCount];
        for (int i = 0; i < bCount; i++) {
            bytes[i] = queue.poll().byteValue();
        }

        return bytes;
    }

    private void addToBuffer(ArrayBlockingQueue<Double> queue, double[] respHeartRates) {
        //debugLog("addToBuffer()");
        for (int i = 0; i < respHeartRates.length; i++) {
            try {
                queue.add(new Double(respHeartRates[i])); // ************************
            } catch (IllegalStateException e) {
                sendError(((queue.equals(_inputBuffer)) ? "Input Buffer" : "Output Buffer") + " could not be written.  Buffer full.");
            } catch (NullPointerException e) {
                // This should probably never happen
                sendError(((queue.equals(_inputBuffer)) ? "Input Buffer" : "Output Buffer") + " could not write null value.");
            }
        }
    }

    // Sends an error callback with a base Exception and writes an error message
    // to console
    private void sendError(String msg) {
        //debugLog("sendError1()");
        Log.e(TAG, msg);
        _brspCallback.onError(this, new Exception(msg));
    }

    // Sends an error callback with the passed Exception type and writes an
    // error message
    // to console
    private void sendError(Exception e) {
        //debugLog("sendError2()");
        Log.e(TAG, e.getMessage());
        _brspCallback.onError(this, e);
    }

//    /**
//     * Queues up bytes and sends them to the remote device FIFO order
//     *
//     * @param bytes
//     *            Bytes to send
//     * @return
//     */
//    public void writeBytesObj(Byte[] bytes) {
//        debugLog("writeBytesObj()");
//        int i = 0;
//        byte[] bs = new byte[bytes.length];
//        for (Byte b : bytes)
//            bs[i++] = b.byteValue();
//        writeBytes(bs);
//    }
//
//    /**
//     * Queues up bytes and sends them to the remote device FIFO order
//     *
//     * @param bytes
//     *            Bytes to send
//     */
//    public void writeBytes(byte[] bytes) {
//        debugLog("writeBytes()");
//        // Raise error if not BRSP_STATE_READY
//        if (_brspState != BRSP_STATE_READY)
//            throw new IllegalStateException("Can not write remote device until getBrspState() == BRSP_STATE_READY.");
//
//        boolean sendingChanged = !isSending();
//        addToBuffer(_outputBuffer, bytes);
//        if (sendingChanged)
//            _brspCallback.onSendingStateChanged(this);
//        sendPacket();
//    }

    private void sendPacket() {
        //debugLog("sendPacket()");

        if (_gatt == null)
            return; // teena: lets not try to send if _gatt became null

        byte[] bytes;

        if (_sending || _lastRTS != 0)
            return; // bail if already sending or not ready to send

        bytes = readBuffer(_outputBuffer, _packetSize);

        if (bytes == null)
            return;

        _sending = true;
        _lastBytes = bytes; // Store bytes
        BluetoothGattCharacteristic Rx = _gatt.getService(BRSP_SERVICE_UUID).getCharacteristic(BRSP_RX_UUID);
        Rx.setValue(bytes);
        _gatt.writeCharacteristic(Rx);
    }

    /**
     * Connects to the remote device and initializes the BRSP service
     *
     * @param context
     *            Context this object is associated with. (The Application
     *            Context is recommended)
     * @param device
     *            BluetoothDevice to connect to
     * @see BrspCallback#onConnectionStateChanged(Brsp)
     * @see BrspCallback#onBrspStateChanged(Brsp)
     * @return true if connection attempt was successful
     * @throws IllegalArgumentException
     *             if an argument is null
     */
    public boolean connect(Context context, BluetoothDevice device) {
        //debugLog("connect()");

        // calculates the frequencies
        for (int i = 0; i < freq.length; i++) {
            freq[i] = (((double) i * (double) FS) / (double) MAX_DATA);
        }

        smoothFreq = new SmoothFreq();
        smoothFreq.execute();

        // debugLog("connect()");
        if (_isClosing) {
            debugLog("Currently closing gatt.  Ignoring connect...");
            return false;
        }

        boolean connectResult = false;
        if (context == null)
            throw new IllegalArgumentException("Context can not be null");
        if (device == null)
            throw new IllegalArgumentException("BluetoothDevice can not be null");

        _context = new WeakReference<Context>(context);

        // The following conditional statements all do the same thing right now
        // because calling connect again (reconnect) has quite a significant
        // delay with this initial android release
        if (_gatt != null) {
            // mike: Note: This code should never get hit now because we set our
            // _gatt variable to null on after each close
            // This is a first connect or a reconnect
            if (device.getAddress().equals(_gatt.getDevice().getAddress())) {
                // This is a reconnect
                _gatt = device.connectGatt(context, false, _gattCallback);
                try {
                    connectResult = _gatt.connect();
                } catch (Exception e) {
                    connectResult = false;
                }

            } else {
                // This is a connect to a different device
                _gatt = device.connectGatt(context, false, _gattCallback);
                connectResult = _gatt.connect();
            }
        } else {
            // This is a first connect
            _firstConnect = true; // teena

            _gatt = device.connectGatt(context, false, _gattCallback);
            connectResult = _gatt.connect();

        }
        return connectResult;
    }

    /**
     * Disconnects from the remote device.
     */
    public void disconnect() {
        //debugLog("disconnect()");
        // debugLog("disconnect()");
        if (_gatt != null && !_isClosing) {
            _gatt.disconnect();
            close();
        } else {
            debugLog("Currently closing gatt.  Ignoring disconnect...");
            return;
        }
    }

    /**
     * Read the RSSI for the remote device. Will fire
     * {@link BrspCallback#onRssiUpdate(Brsp)}
     *
     * @see Brsp#getLastRssi()
     */
    public void readRssi() {
        //debugLog("readRssi()");
        boolean result = false;
        if (_gatt != null && !_isClosing) {
            try {
                result = _gatt.readRemoteRssi();
            } catch (NullPointerException e) {
                sendError("Read RSSI failed.  Null pointer Exception.");
                return;
            }
            if (!result)
                sendError("Read RSSI failed");
        } else {
            debugLog("Currently closing gatt.  Ignoring readRssi...");
            return;
        }
    }

    public void close() {
        //debugLog("close()");
        if (_gatt != null && !_isClosing) {
            _isClosing = true;
            init();
            closeGattAfterDelay();
            // _gatt.close();
            _brspCallback.onConnectionStateChanged(Brsp.this);
        } else {
            debugLog("Currently closing gatt.  Ignoring close...");
            return;
        }
    }

    private static final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();

    private void closeGattAfterDelay(long milliSeconds) {
        //debugLog("closeGattAfterDelay1()");
        Runnable task = new Runnable() {
            public void run() {
                // TODO: Add a try catch if needed. Docs don't state that this
                // method throws any type of error.
                _gatt.close();
                _gatt = null;
                _isClosing = false;
                // This is a hack to send a state change to disconnected
                _brspCallback.onConnectionStateChanged(Brsp.this);
            }
        };
        worker.schedule(task, milliSeconds, TimeUnit.MILLISECONDS);
    }

    // Defaults to specific value
    private void closeGattAfterDelay() {
        //debugLog("closeGattAfterDelay2()");
        closeGattAfterDelay(100);
    }

    private void debugLog(String str) {
        // if (BuildConfig.DEBUG) {
        Log.d(TAG, str);
        // }
    }

    private String getRawString(byte[] rawBytes) {
        //debugLog("getRawString()");

        String rawDataString = null;
        try {
            rawDataString = new String(rawBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return rawDataString;
    }
}
