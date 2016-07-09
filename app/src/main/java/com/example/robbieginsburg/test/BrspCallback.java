package com.example.robbieginsburg.test;

public abstract class BrspCallback {

    /**
     * Required. Called when the connection status changes
     *
     * @param obj
     *            Brsp object
     * @see Brsp#getConnectionState()
     */
    public abstract void onConnectionStateChanged(Brsp obj);

    /**
     * Required. Called when the isSending() status changes
     *
     * @see Brsp#isSending()
     * @param obj
     *            Brsp object
     */
    public abstract void onSendingStateChanged(Brsp obj);

    /**
     * Called when data is received from the remote device
     *
     * @param obj
     */
    public void onDataReceived(Brsp obj) {

    }

    /**
     * Called when an internal exception occurs
     *
     * @param obj
     *            Brsp object
     * @param e
     *            Exception containing the error
     */
    public void onError(Brsp obj, Exception e) {

    }

    /**
     * Called when brsp mode changes
     *
     * @param obj
     *            Brsp object
     */
    public void onBrspModeChanged(Brsp obj) {

    }

    /**
     * Called when rssi has been updated by a prior call to
     * {@link Brsp#readRssi()}
     *
     * @param obj
     *            Brsp object
     */
    public void onRssiUpdate(Brsp obj) {

    }

    /**
     * Brsp ready state changed. Writes and reads are possible when
     * {@link Brsp#getBrspState()} == {@link Brsp#BRSP_STATE_READY}.
     *  writeBytes should not be called unless object is in a ready state
     * @see Brsp#getBrspState()
     * @param obj
     *            Brsp object
     */
    public void onBrspStateChanged(Brsp obj) {

    }

}
