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

import android.os.AsyncTask;
import android.util.Log;
import com.ab.droid.chesspad.layout.ProgressBarHolder;
import com.ab.pgn.Config;

public class CPAsyncTask extends AsyncTask<Void, Integer, Throwable> implements CpFile.ProgressObserver {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private final CPExecutor cpExecutor;
    private int oldProgress;
    private static ProgressBarHolder progressBarHolder;

    public static void setProgressBarHolder(ProgressBarHolder progressBarHolder) {
        CPAsyncTask.progressBarHolder = progressBarHolder;
    }

    public CPAsyncTask(CPExecutor cpExecutor) {
        this.cpExecutor = cpExecutor;
        CpFile.setProgressObserver(this);
    }

    public void execute() {
        oldProgress = 0;
        super.execute((Void)null);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if (progressBarHolder != null) {
            progressBarHolder.showProgressBar(true);
            progressBarHolder.updateProgressBar(oldProgress);
        }
    }

    @Override
    protected void onPostExecute(Throwable param) {
        super.onPostExecute(param);
        if (progressBarHolder != null) {
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
    protected Throwable doInBackground(Void... params) {
        try {
            cpExecutor.doInBackground(this);
        } catch (Throwable e) {
            Log.e(this.DEBUG_TAG, String.format("doInBackground, thread %s", Thread.currentThread().getName()), e);
            return e;
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if (progressBarHolder != null) {
            progressBarHolder.updateProgressBar(values[0]);
        }
    }

    @Override
    public void setProgress(int progress) {
        if (progress - oldProgress >= 1) {
            oldProgress = progress;
            if (progress > 100) {
                progress = 100;
            }
            super.publishProgress(progress);
        }
    }
}

interface ProgressPublisher {
    void publishProgress(int progress);
}

interface CPPostExecutor {
    void onPostExecute() throws Config.PGNException;
}

interface CPExecutor extends CPPostExecutor{
    void doInBackground(CpFile.ProgressObserver progressObserver) throws Config.PGNException;
    void onExecuteException(Throwable e);
}

