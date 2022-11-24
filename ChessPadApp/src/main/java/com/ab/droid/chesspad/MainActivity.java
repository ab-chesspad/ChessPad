/*
     Copyright (C) 2022	Alexander Bootman, alexbootman@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 * main activity
 * Created by Alexander Bootman on 8/20/22.
 */
package com.ab.droid.chesspad;

import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements ComponentCallbacks2 {
    private final String DEBUG_TAG = "cpfilemanager." + this.getClass().getSimpleName();
    private static final boolean DEBUG_RESOURCES = true;

    private ChessPad chessPad;

    transient private static AppCompatActivity instance;
    public static AppCompatActivity getInstance() {
        return instance;
    }
    public static Context getContext() {
        return instance;
    }

    // Always followed by onStart()
    // @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(DEBUG_TAG, "onCreate");
        instance = this;

        if (DEBUG_RESOURCES) {
            try {
                Class.forName("dalvik.system.CloseGuard")
                        .getMethod("setEnabled", boolean.class)
                        .invoke(null, true);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        chessPad = new ChessPad();
    }

//    // Called when the activity will start interacting with the user
//    // after onStart() or onPause()
//    // @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR1)
//    @Override
//    public void onResume() {
//        super.onResume();
//        chessPad.onResume();
//    }

//    // called after onStop()
//    // Always followed by onStart()
//    @Override
//    protected void onRestart() {
//        super.onRestart();
//        Log.d(DEBUG_TAG, "onRestart");
//    }

//    // called after onCreate() or onRestart()
//    @Override
//    protected void onStart() {
//        super.onStart();
//        Log.d(DEBUG_TAG, "onStart");
//        chessPad.launch();
//    }

//    // another activity comes to foreground
//    // followed by onResume() if the activity comes to the foreground, or onStop() if it becomes hidden.
//    @Override
//    public void onPause() {
//        super.onPause();
//        Log.d(DEBUG_TAG, "onPause()");
//        serialize(chessPad);
//    }

    // Followed by either onRestart() if this activity is coming back to interact with the user, or onDestroy() if this activity is going away
    // becomes killable
    @Override
    protected void onStop() {
        super.onStop();
        chessPad.serialize();
        Log.d(DEBUG_TAG, "onStop");
    }

    // The final call you receive before your activity is destroyed
    // becomes killable
    @Override
    protected void onDestroy() {
        chessPad.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        chessPad.onConfigurationChanged();
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     * This is in theory. In practice the method is being called only with ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN.
     * Even when the program consumes huge amts of memory, it crashes with OOM because of fragmentation, never calling onTrimMemory.
     * @param level the memory-related event that was raised.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(DEBUG_TAG, "onTrimMemory() " + level);
        // Determine which lifecycle or system event was raised.
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                /*
                   Release any UI objects that currently hold memory.

                   "release your UI resources" is actually about things like caches.
                   You usually don't have to worry about managing views or UI components because the OS
                   already does that, and that's why there are all those callbacks for creating, starting,
                   pausing, stopping and destroying an activity.
                   The user interface has moved to the background.
                */
                break;

            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:

                /*
                   Release any memory that your app doesn't need to run.

                   The device is running low on memory while the app is running.
                   The event raised indicates the severity of the memory-related event.
                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
                   begin killing background processes.
                */

                break;

            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:

                /*
                   Release as much memory as the process can.
                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */

                break;

            default:
                /*
                  Release any non-critical data structures.
                  The app received an unrecognized memory level value
                  from the system. Treat this as a generic low-memory message.
                */
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (chessPad != null) {
            chessPad.onActivityResult(requestCode, resultCode, intent);
        }
    }
}
