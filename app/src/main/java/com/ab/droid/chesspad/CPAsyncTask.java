package com.ab.droid.chesspad;

import android.os.AsyncTask;
import android.util.Log;
import com.ab.pgn.Config;

/**
 *
 * Created by Alexander Bootman on 10/1/17.
 */

public class CPAsyncTask extends AsyncTask<Void, Integer, Config.PGNException> implements ProgressPublisher {
    protected final String DEBUG_TAG = this.getClass().getName();

    private CPExecutor cpExecutor;
    private ChessPadView.CpProgressBar cpProgressBar;
    private int oldProgress;

    public CPAsyncTask(ChessPadView.CpProgressBar cpProgressBar, CPExecutor cpExecutor) {
        this.cpProgressBar = cpProgressBar;
        this.cpExecutor = cpExecutor;
    }

    public void execute() {
        oldProgress = 0;
        super.execute((Void)null);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        if(cpProgressBar != null) {
            cpProgressBar.show(true);
            cpProgressBar.update(oldProgress);
        }
    }

    @Override
    protected void onPostExecute(Config.PGNException param) {
        super.onPostExecute(param);
        if(cpProgressBar != null) {
            cpProgressBar.show(false);
        }
        try {
            if(param == null) {
                cpExecutor.onPostExecute();
            } else {
                cpExecutor.onExecuteException(param);
            }
        } catch (Config.PGNException e) {
            Log.e(DEBUG_TAG, String.format("onPostExecute, thread %s", Thread.currentThread().getName()), e);
        }
    }

    @Override
    protected Config.PGNException doInBackground(Void... params) {
        try {
            cpExecutor.doInBackground(this);
        } catch (Config.PGNException e) {
            Log.e(this.DEBUG_TAG, String.format("doInBackground, thread %s", Thread.currentThread().getName()), e);
            return e;
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        if(cpProgressBar != null) {
            cpProgressBar.update(values[0]);
        }
    }

    @Override
    public void publishProgress(int progress) {
        if(progress - oldProgress >= 1) {
            super.publishProgress(progress);
            oldProgress = progress;
        }
    }

}

interface ProgressPublisher {
    void publishProgress(int progress);
}

interface CPPostExecutor {
    void onPostExecute() throws Config.PGNException;
    void onExecuteException(Config.PGNException e) throws Config.PGNException;
}

interface CPExecutor extends CPPostExecutor{
    void doInBackground(ProgressPublisher progressPublisher) throws Config.PGNException;
}
