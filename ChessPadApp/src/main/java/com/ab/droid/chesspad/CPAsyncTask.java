/*
     Copyright (C) 2021-2022	Alexander Bootman, alexbootman@gmail.com

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

 * AsyncTask subclass with progress bar
 * Created by Alexander Bootman on 10/1/17.
*/
package com.ab.droid.chesspad;

import com.ab.pgn.io.CpFile;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.ab.droid.chesspad.layout.ProgressBarHolder;
import com.ab.pgn.Config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CPAsyncTask implements CpFile.ProgressObserver {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private final CPExecutor cpExecutor;
    private int oldProgress;
    private static ProgressBarHolder progressBarHolder;
    private static boolean progressBarShown;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static void setProgressBarHolder(ProgressBarHolder progressBarHolder) {
        CPAsyncTask.progressBarHolder = progressBarHolder;
    }

    public CPAsyncTask(CPExecutor cpExecutor) {
        this.cpExecutor = cpExecutor;
        CpFile.setProgressObserver(this);
    }

    public void execute() {
        oldProgress = 0;
        if (progressBarHolder != null) {
            progressBarShown = true;
            progressBarHolder.showProgressBar(true);
            progressBarHolder.updateProgressBar(oldProgress);
        }

        executor.execute(() -> {
            Throwable t = null;
            try {
                cpExecutor.doInBackground(this);
            } catch (Throwable e) {
                Log.e(this.DEBUG_TAG, String.format("doInBackground, thread %s", Thread.currentThread().getName()), e);
                t = e;
            }
            final Throwable param = t;
            handler.post(() -> {
                //UI Thread work here
                onPostExecute(param);
            });
        });
    }

    private void onPostExecute(Throwable param) {
        if (progressBarHolder != null) {
            progressBarShown = false;
            progressBarHolder.showProgressBar(false);
        }
        try {
            if (param == null) {
                cpExecutor.onPostExecute();
            } else {
                cpExecutor.onExecuteException(param);
            }
        } catch (Config.PGNException e) {
            Log.e(DEBUG_TAG, String.format("onPostExecute, thread %s", Thread.currentThread().getName()), e);
        }
    }

    @Override
    public void setProgress(int progress) {
        if (progress - oldProgress >= 1) {
            oldProgress = progress;
            if (progress > 100) {
                progress = 100;
            }
            final int newProgress = progress;
            if (progressBarHolder != null) {
                handler.post(() -> {
                    //UI Thread work here
                    progressBarHolder.updateProgressBar(newProgress);
                });
            }
        }
    }

    public static boolean isBGTaskRunning() {
        return progressBarShown;
    }
}

interface CPPostExecutor {
    void onPostExecute() throws Config.PGNException;
}

interface CPExecutor extends CPPostExecutor{
    void doInBackground(CpFile.ProgressObserver progressObserver) throws Config.PGNException;
    void onExecuteException(Throwable e);
}