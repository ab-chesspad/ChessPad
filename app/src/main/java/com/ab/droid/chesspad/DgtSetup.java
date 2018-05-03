package com.ab.droid.chesspad;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Iterator;

/**
 *
 * Created by alex on 3/26/18.
 */

public class DgtSetup {

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR1)
    public static HashMap<String, UsbDevice> discover(AppCompatActivity context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
//        UsbDevice device = deviceList.get("deviceName");

        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            //your code
        }

        return deviceList;
    }
}
