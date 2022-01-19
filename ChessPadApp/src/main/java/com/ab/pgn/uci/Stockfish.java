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


 * Stockfish JNI wrapper
 * Created by Alexander Bootman on 10/28/2021.

 cd app/src/main/java/com/ab/pgn/uci/
 javac -h . -d dummy Stockfish.java UCI.java
 rm -rf dummy
 */
package com.ab.pgn.uci;

import java.io.File;
import java.io.IOException;

public class Stockfish implements UCI.UCIImpl {
    private native void _launch();
    private native void _execute(String command);
    private native String _read();
    private native String _read_err();
    private native void _quit();

    protected final String
        nnueFileOption = "evalfile",
        useNNUEOption = "use nnue",
        dummy_str = null;

    public void loadLibrary() {
        System.loadLibrary("stockfish");
    }

    @Override
    public void launch() throws IOException {
        _launch();
    }

    @Override
    public void setOptions(UCI uci) {
        // todo for non-Android case
        File f = new File("x");
        System.out.println(f.getAbsolutePath());
        uci.setOption(useNNUEOption, false);
    }

    @Override
    public void execute(String command) {
        _execute(command);
    }

    @Override
    public String read() {
        return _read();
    }

    @Override
    public String read_err() {
        return _read_err();
    }

    @Override
    public void quit() {
        _quit();
    }
}
