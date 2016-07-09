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
    private ArrayBlockingQueue<Byte> _inputBuffer;
    private ArrayBlockingQueue<Byte> _outputBuffer;

    private int byteCount = 1;

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
        return _lastRssi;
    }

    /**
     * Returns the BluetoothGatt state of the remote device
     *
     * @return The BluetoothGatt current connection state
     */
    public int getConnectionState() {
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
        return _brspMode;
    }

    /**
     * Gets the current remote device
     *
     * @return The current BluetoothDevice. Null if connect was never called
     *         with a valid device
     */
    public BluetoothDevice getDevice() {
        BluetoothDevice dev = null;
        if (_gatt != null)
            dev = _gatt.getDevice();
        return dev;
    }

    private BluetoothGattCallback _gattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // debugLog("onCharacteristicChanged:" +
            // characteristic.getUuid().toString());
            if (_initState < _initStepCount) {
                doNextInitStep();
            } else {
                if (characteristic.getUuid().equals(BRSP_TX_UUID)) {
                    // Incoming data
                    byte[] rawBytes = characteristic.getValue();
                    String rawString = getRawString(rawBytes);
                    debugLog("IncoimgData:" + rawString);
                    addToBuffer(_inputBuffer, rawBytes);
                    _brspCallback.onDataReceived(Brsp.this);
                } else if (characteristic.getUuid().equals(BRSP_RTS_UUID)) {
                    _lastRTS = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                    sendPacket();
                }
            }
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
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
            // debugLog("onDescriptorRead");
            if (_initState < _initStepCount) {
                doNextInitStep();
            }
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            // debugLog("onDescriptorWrite");
            if (_initState < _initStepCount) {
                doNextInitStep();
            }
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
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
            // debugLog("onReliableWriteCompleted");
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
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
        if (callback == null)
            throw new IllegalArgumentException("callback can not be null");
        inputBufferSize = (inputBufferSize < 1) ? DEFAULT_BUFFER_SIZE : inputBufferSize;
        inputBufferSize = (inputBufferSize < 1) ? DEFAULT_BUFFER_SIZE : inputBufferSize;

        setBuffers(inputBufferSize, outputBufferSize);
        _brspCallback = callback;
    }

    private void setBuffers(int inputBufferSize, int outputBufferSize) {
        _iBufferSize = inputBufferSize;
        _oBufferSize = outputBufferSize;
        _inputBuffer = new ArrayBlockingQueue<Byte>(inputBufferSize);
        _outputBuffer = new ArrayBlockingQueue<Byte>(outputBufferSize);
    }

    /**
     * The total capacity of the buffer
     *
     * @return Total InputBuffer capacity this object was created with. Note:
     *         This can not be changed after instantiation.
     */
    public int getInputBufferSize() {
        return _iBufferSize;
    }

    /**
     * The total capacity of the buffer
     *
     * @return Total OutputBuffer capacity this object was created with. Note:
     *         This can not be changed after instantiation.
     */
    public int getOutputBufferSize() {
        return _oBufferSize;
    }

    /**
     * The amount of bytes currently in the buffer
     *
     * @return Number of bytes in the InputBuffer
     */
    public int getInputBufferCount() {
        return _inputBuffer.size();
    }

    /**
     * The amount of bytes currently in the buffer
     *
     * @return Number of bytes in the OutputBuffer
     */
    public int getOutputBufferCount() {
        return _outputBuffer.size();
    }

    /**
     * Bytes available before buffer is full
     *
     * @return Number of bytes left that can be retrieved from device
     */
    public int intputBufferAvailableBytes() {
        return _inputBuffer.remainingCapacity();
    }

    /**
     * Bytes available before buffer is full
     *
     * @return Number of bytes left that can be written via writes
     */
    public int outputBufferAvailableBytes() {
        return _outputBuffer.remainingCapacity();
    }

    /**
     * Atomically removes all of the elements from the input buffer. The buffer
     * will be empty after this call returns.
     */
    public void clearInputBuffer() {
        _inputBuffer.clear();
    }

    /**
     * Atomically removes all of the elements from the output buffer. The buffer
     * will be empty after this call returns.
     */
    public void clearOutputBuffer() {
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
        return readBuffer(_inputBuffer, byteCount);
    }

    private byte[] readBuffer(ArrayBlockingQueue<Byte> queue, int byteCount) {
        int bytesInBuffer = queue.size();
        int bCount = (bytesInBuffer < byteCount) ? bytesInBuffer : byteCount;
        if (bCount < 1)
            return null;
        byte[] bytes = new byte[bCount];
        for (int i = 0; i < bCount; i++) {
            bytes[i] = queue.poll().byteValue();
        }

        String str = new String(bytes, StandardCharsets.UTF_8);
        Log.d("Full String", "" + str);

        for (char ch : str.toCharArray()){
            if(ch == '*'){
                Log.i("Tag1", "--- * ---");
                byteCount = 1;
            }
            if(ch == '\n'){
                Log.i("Tag2", "---NEW LINE---");
            }
            if(ch != '*' && ch != '\n'){
                byteCount++;
            }

            Log.i("Tag3", "" + byteCount);
        }

        return bytes;
    }

    private void addToBuffer(ArrayBlockingQueue<Byte> queue, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            try {
                queue.add(new Byte(bytes[i]));
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
        Log.e(TAG, msg);
        _brspCallback.onError(this, new Exception(msg));
    }

    // Sends an error callback with the passed Exception type and writes an
    // error message
    // to console
    private void sendError(Exception e) {
        Log.e(TAG, e.getMessage());
        _brspCallback.onError(this, e);
    }

    /**
     * Queues up bytes and sends them to the remote device FIFO order
     *
     * @param bytes
     *            Bytes to send
     * @return
     */
    public void writeBytesObj(Byte[] bytes) {
        int i = 0;
        byte[] bs = new byte[bytes.length];
        for (Byte b : bytes)
            bs[i++] = b.byteValue();
        writeBytes(bs);
    }

    /**
     * Queues up bytes and sends them to the remote device FIFO order
     *
     * @param bytes
     *            Bytes to send
     */
    public void writeBytes(byte[] bytes) {
        // Raise error if not BRSP_STATE_READY
        if (_brspState != BRSP_STATE_READY)
            throw new IllegalStateException("Can not write remote device until getBrspState() == BRSP_STATE_READY.");

        boolean sendingChanged = !isSending();
        addToBuffer(_outputBuffer, bytes);
        if (sendingChanged)
            _brspCallback.onSendingStateChanged(this);
        sendPacket();
    }

    private void sendPacket() {

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
        closeGattAfterDelay(100);
    }

    private void debugLog(String str) {
        // if (BuildConfig.DEBUG) {
        Log.d(TAG, str);
        // }
    }

    private String getRawString(byte[] rawBytes) {

        String rawDataString = null;
        try {
            rawDataString = new String(rawBytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return rawDataString;
    }
}
