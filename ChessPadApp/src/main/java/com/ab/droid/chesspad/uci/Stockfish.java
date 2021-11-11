/*
     Copyright (C) 2021	Alexander Bootman, alexbootman@gmail.com

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
*/

/**
 * Stockfish for Android
 * handles nnue file
 * Created by Alexander Bootman on 10/28/2021.
 */
package com.ab.droid.chesspad.uci;

import android.content.Context;
import android.content.res.AssetManager;

import com.ab.pgn.uci.UCI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Stockfish extends com.ab.pgn.uci.Stockfish {
    private Context context;
    private File nnueFile = null;

    public Stockfish(Context context) {
        this.context = context;
    }

    @Override
    public void setOptions(UCI uci) {
        if (nnueFile == null) {
            uci.setOption(useNNUEOption, false);
        } else {
            uci.setOption(nnueFileOption, nnueFile.getAbsolutePath());
        }
    }

    @Override
    public void launch() throws IOException {
        AssetManager assetManager = context.getAssets();
        String nnueName = null;
        try {
            for (String asset: assetManager.list("")) {
                if (asset.endsWith(".nnue")) {
                    nnueName = asset;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (nnueName != null) {
            System.out.printf("nnue file %s\n", nnueName);
            File nnueDir = new File(context.getFilesDir(), "nnue");
            nnueDir.mkdir();
            nnueFile = new File(nnueDir, nnueName);
            if (!nnueFile.exists()) {
                try (InputStream is = assetManager.open(nnueName);
                     OutputStream os = new FileOutputStream(nnueFile)) {
                    copyStream(is, os);
                }
            }
        }
        super.launch();
    }

    private void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buf = new byte[8192];
        while (true) {
            int len = is.read(buf);
            if (len <= 0)
                break;
            os.write(buf, 0, len);
        }
    }
}
