/*
    DroidFish - An Android chess program.
    Copyright (C) 2011-2014  Peter Österlund, peterosterlund2@gmail.com

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

    Alexander Bootman modified for ChessPad 03/08/2020
*/

package com.ab.droid.engine.stockfish;

import android.content.Context;
import android.os.Build;

import org.petero.droidfish.engine.UCIEngine;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class StockFishEngine extends UCIEngine {
    private static final String
        EXE_DIR = "engine",
        STOCK_FISH_FILE_NAME = "stockfish",
        STOCK_FISH_CRC_EXT = ".crc",
        str_dummy = null;

    static {
        System.loadLibrary("nativeutil");
    }

    /** Change the priority of a process. */
    public static native void reNice(int pid, int prio);

    /** Executes chmod on exePath. */
    public static native boolean chmod(String exePath, int mod);


    private final Context context;

    public StockFishEngine(Context context, EngineWatcher engineWatcher) throws IOException {
        super(engineWatcher);
        this.context = context;
    }

    @Override
    public void launch() throws IOException {
        super.launch();
        reNice();
    }

    @Override
    protected String getExecutablePath() throws IOException {
        File exeDir = new File(context.getFilesDir(), EXE_DIR);
        exeDir.mkdir();
        String exePath = copyAsset(exeDir);
        return exePath;
    }

    private String copyAsset(File exeDir) throws IOException {
        File exePath = new File(exeDir, STOCK_FISH_FILE_NAME);
        // The checksum test is to avoid writing to /data unless necessary,
        // on the assumption that it will reduce memory wear.
        File crcFile = new File(exePath.getAbsolutePath() + STOCK_FISH_CRC_EXT);
        long oldCSum = readCheckSum(crcFile);
        long newCSum = computeAssetsCheckSum(stockFishAssetName());
        if (oldCSum == newCSum) {
            return exePath.getAbsolutePath();
        }

        if (exePath.exists()) {
            exePath.delete();
        }
        exePath.createNewFile();

        try (InputStream is = context.getAssets().open(stockFishAssetName());
             OutputStream os = new FileOutputStream(exePath)) {
            byte[] buf = new byte[8192];
            while (true) {
                int len = is.read(buf);
                if (len <= 0)
                    break;
                os.write(buf, 0, len);
            }
        }
        chmod(exePath.getAbsolutePath(), 0744);
        writeCheckSum(crcFile, newCSum);
        return exePath.getAbsolutePath();
    }

    private long readCheckSum(File f) {
        try (InputStream is = new FileInputStream(f);
                DataInputStream dis = new DataInputStream(is)) {
            return dis.readLong();
        } catch (IOException e) {
            return 0;
        }
    }

    private void writeCheckSum(File f, long checkSum) {
        try (OutputStream os = new FileOutputStream(f);
             DataOutputStream dos = new DataOutputStream(os)) {
            dos.writeLong(checkSum);
        } catch (IOException ignore) {
            // ignore
        }
    }

    private long computeAssetsCheckSum(String sfExe) {
        try (InputStream is = context.getAssets().open(sfExe)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            while (true) {
                int len = is.read(buf);
                if (len <= 0)
                    break;
                md.update(buf, 0, len);
            }
            byte[] digest = md.digest(new byte[]{0});
            long ret = 0;
            for (int i = 0; i < 8; i++) {
                ret ^= ((long) digest[i]) << (i * 8);
            }
            return ret;
        } catch (IOException e) {
            return -1;
        } catch (NoSuchAlgorithmException e) {
            return -1;
        }
    }

    /**
     * Return file name of the internal stockfish executable.
     */
    private static String stockFishAssetName() {
        String abi = Build.CPU_ABI;
        if (!"x86".equals(abi) &&
                !"x86_64".equals(abi) &&
                !"arm64-v8a".equals(abi)) {
            abi = "armeabi-v7a"; // Unknown ABI, assume 32-bit arm
        }
        return abi + "/stockfish";
    }

    /** Try to lower the engine process priority. */
    private void reNice() {
        try {
            java.lang.reflect.Field f = engineProc.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            int pid = f.getInt(engineProc);
            reNice(pid, 10);
        } catch (Throwable ignore) {
        }
    }


}