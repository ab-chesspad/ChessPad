package com.ab.droid.chesspad;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import com.ab.pgn.Config;
import com.ab.pgn.dgtboard.DgtBoardIO;
import com.ab.pgn.dgtboard.DgtBoardProtocol;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * For Android version 5.0.2, Lollipop, API level 21:
 * if the device is attached before the program has been launched, inputStream and outputStream
 * become unusable, e.g. java.io.IOException: write failed: ENODEV (No such device)
 */

public class DgtBoardInterface extends DgtBoardIO {
    private static final boolean DEBUG = false;
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private final Object syncObject = new Object();
    private static final int REPEAT_COMMAND_AFTER_MSEC = 500;   // somehow commands are being missed

    private static final int baudRate = 9600; /*baud rate*/
    private static final byte stopBit = 1; /*1:1stop bits, 2:2 stop bits*/
    private static final byte dataBit = 8; /*8:8bit, 7: 7bit*/
    private static final byte parity = 0;  /* 0: none, 1: odd, 2: even, 3: mark, 4: space*/
    private static final byte flowControl = 0; /*0:none, 1: flow control(CTS,RTS)*/

    private static final String ManufacturerString = "mManufacturer=FTDI";
    private static final String ModelString1 = "mModel=FTDIUARTDemo";
    private static final String ModelString2 = "mModel=Android Accessory FT312D";
    private static final String VersionString = "mVersion=1.0";

    private FileInputStream inputStream;
    private FileOutputStream outputStream;

    private final String ACTION_USB_PERMISSION = this.getClass().getPackage().getName() + "USB_PERMISSION";
    private final UsbManager usbManager;
    private final PendingIntent permissionIntent;
    private ParcelFileDescriptor fileDescriptor;
    private boolean permissionRequestPending = false;

    private final StatusObserver statusObserver;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        @TargetApi(12)
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Toast.makeText(ChessPad.getContext(), "USB permission obtained", Toast.LENGTH_LONG).show();
                        setAccessory(accessory);
                    } else {
                        Toast.makeText(ChessPad.getContext(), "USB permission denied", Toast.LENGTH_LONG).show();
                        Log.d("LED", "USB permission denied for accessory "+ accessory);

                    }
                    permissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                close();
            }
        }
    };

    @TargetApi(12)
	public DgtBoardInterface(StatusObserver statusObserver){
        this.statusObserver = statusObserver;
		usbManager = (UsbManager) ChessPad.getContext().getSystemService(Context.USB_SERVICE);
		// Log.d("LED", "usbManager" +usbManager);
		permissionIntent = PendingIntent.getBroadcast(ChessPad.getContext(), 0, new Intent(ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        ChessPad.getContext().registerReceiver(usbReceiver, filter);
	}

    @TargetApi(12)
    private UsbAccessory getAccessory() throws IOException {
        UsbAccessory[] accessories = usbManager.getAccessoryList();
        if (accessories == null) {
            throw new IOException("Usb Manager did not find any accessories. Is DGT board connected?");
        }

        UsbAccessory accessory = accessories[0];
        if (accessory == null) {
            throw new IOException("List of accessories is empty. Is DGT board connected?");
        }
        return accessory;
    }

    private void config() throws IOException {
        config(baudRate, dataBit, stopBit, parity, flowControl);
    }

    private void config(int baud, byte dataBits, byte stopBits,
			byte parity, byte flowControl) throws IOException {
        byte[] data = new byte[8];

		data[0] = (byte)baud;
		data[1] = (byte)(baud >> 8);
		data[2] = (byte)(baud >> 16);
		data[3] = (byte)(baud >> 24);

		data[4] = dataBits;
		data[5] = stopBits;
		data[6] = parity;
		data[7] = flowControl;

		write(data);

		write(DgtBoardProtocol.DGT_END_DEBUG_MODE);
        write(DgtBoardProtocol.DGT_SEND_RESET);
        if(REPEAT_COMMAND_AFTER_MSEC == 0) {
            // sometimes does not work well
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            write(DgtBoardProtocol.DGT_SEND_RESET);
        }
        Log.d(DEBUG_TAG, "config finish");
	}

	@Override
    public void write(byte command) throws IOException {
        byte[] data = new byte[1];
        data[0] = command;
        write(data);
        if(REPEAT_COMMAND_AFTER_MSEC > 0) {
            try {
                Thread.sleep(REPEAT_COMMAND_AFTER_MSEC);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            write(data);
        }
    }

    private void write(byte[] data) throws IOException {
        if (outputStream == null) {
            return;   // writing before opening DgtBoardInterface
        }
        try{
            Thread.sleep(100);
        } catch(InterruptedException e){
            // ignore
        }
        outputStream.write(data);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if(inputStream == null) {
            if(DEBUG) {
                Log.d(DEBUG_TAG, "inputStream == null, return 0 bytes");
            }
            return 0;   // reading before opening DgtBoardInterface
        }
        if(buffer.length < 3) {
            Log.d(DEBUG_TAG, "buffer too short, return 0 bytes");
            return 0;   // buffer too short
        }

        int readCount = 0;
        while(readCount == 0) {
            try {
                Thread.sleep(50);
            }
            catch (InterruptedException e) {
                // ignore
            }
            if(inputStream == null) {
                if(DEBUG) {
                    Log.d(DEBUG_TAG, "inputStream == null, return 0 bytes 1");
                }
                return 0;   // closed when adapter detached
            }
            readCount = inputStream.read(buffer, offset, length);
            if(DEBUG) {
                Log.d(DEBUG_TAG, String.format("read %s bytes", readCount));
            }
        }
        return readCount;
    }

    @TargetApi(12)
	public void checkPermissionAndOpen() throws IOException {
        Log.d(DEBUG_TAG, String.format("checkPermissionAndOpen() API %s", Build.VERSION.SDK_INT));
	    synchronized (syncObject) {
            UsbAccessory accessory = getAccessory();

            if (!accessory.toString().contains(ManufacturerString)) {
                Toast.makeText(ChessPad.getContext(), "Manufacturer is not matched!", Toast.LENGTH_LONG).show();
            }

            if (!accessory.toString().contains(ModelString1) && !accessory.toString().contains(ModelString2)) {
                Toast.makeText(ChessPad.getContext(), "Model is not matched!", Toast.LENGTH_LONG).show();
            }

            if (!accessory.toString().contains(VersionString)) {
                Toast.makeText(ChessPad.getContext(), "Version is not matched!", Toast.LENGTH_LONG).show();
            }

            Toast.makeText(ChessPad.getContext(), "Accessory Attached", Toast.LENGTH_LONG).show();

            if (usbManager.hasPermission(accessory)) {
                Log.d(DEBUG_TAG, "have Permission");
                setAccessory(accessory);
            } else {
                synchronized (usbReceiver) {
                    Log.d(DEBUG_TAG, String.format("permissionRequestPending %b", permissionRequestPending));
                    if (!permissionRequestPending) {
                        Toast.makeText(ChessPad.getContext(), "Request USB Permission", Toast.LENGTH_LONG).show();
                        usbManager.requestPermission(accessory,
                                permissionIntent);
                        permissionRequestPending = true;
                    }
                }
            }
        }
	}

    private void setAccessory(UsbAccessory accessory) {
        if(statusObserver != null) {
            statusObserver.isAccessible(true);
        }
    }

    @TargetApi(12)
    public void open() throws IOException {
        UsbAccessory accessory = getAccessory();
        Log.d(DEBUG_TAG, String.format("open %s", accessory));
        fileDescriptor = usbManager.openAccessory(accessory);
        if(fileDescriptor == null){
            throw  new IOException(String.format("%s does not open", accessory.toString()));
        }
        FileDescriptor fd = fileDescriptor.getFileDescriptor();
        inputStream = new FileInputStream(fd);
        outputStream = new FileOutputStream(fd);
        Log.d(DEBUG_TAG, "open finish");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        config();
        init();
        if(statusObserver != null) {
            statusObserver.isOpen(true);
        }
    }

	public void close() {
        Log.d(DEBUG_TAG, "close accessory");
        try {
            Thread.sleep(10);
        } catch(InterruptedException e){ /* ignore */ }

		try {
            fileDescriptor.close();
		} catch (Exception e){ /* ignore */ }

		try {
            inputStream.close();
		} catch(Exception e){ /* ignore */ }

		try {
            outputStream.close();
		} catch(Exception e){ /* ignore */ }

		fileDescriptor = null;
		inputStream = null;
		outputStream = null;
        try {
            ChessPad.getContext().unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            // ignore, nothing to do
        }
        if(statusObserver != null) {
            statusObserver.isOpen(false);
            statusObserver.isAccessible(false);
        }
        Log.d(DEBUG_TAG, "accessory closed");
	}

	public interface StatusObserver {
        void isOpen(boolean open);
        void isAccessible(boolean accessible);
    }
}