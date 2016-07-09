package com.example.robbieginsburg.test;

/**
 * This Exception is sent to BrspCallback.onError() when the Brsp detects a
 * possible unstable state in the BLE API. One example of an invalid state is getting a
 * BluetoothGatt.STATE_CONNECTED immediately after calling disconnect().
 */
public class UnstableException extends Exception {
    private static final long serialVersionUID = 1L;

    public UnstableException(String message) {
        super(message);
    }

    public UnstableException() {
        super("Internal Bluetooth may have become unstable.  Cycling bluetooth and recreating Brsp now is highly recommended.");
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }

    @Override
    public String toString() {
        return super.toString();
    }

}
