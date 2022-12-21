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

 * Created by Alexander Bootman on 9/15/22.
 File accessor, to separate CP logic from the actual file manipulation
 needed to use androidx.documentfile.provider.DocumentFile
 */
package com.ab.pgn.io;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FilAx {
    String
        SLASH = "/",    // File.separator?
        EXT_TEMP = ".tmp",
        EXT_PGN = ".pgn",
        EXT_ZIP = ".zip",
        WRONG_PGN = SLASH + EXT_PGN,
        WRONG_ZIP = SLASH + EXT_ZIP,
        dummy_str = null;

    FilAx getParent();
    String[] listFiles();
    boolean isDirectory();
    OutputStream getOutputStream() throws FileNotFoundException;
    InputStream getInputStream() throws FileNotFoundException;
    boolean mkdirs();
    boolean renameTo(String newName);
    boolean exists();
    boolean delete();
    int length();
    String getName();
    long lastModified();

    static boolean isPgnOk(String path) {
        path = path.toLowerCase();
        return path.endsWith(EXT_PGN) && !path.endsWith(WRONG_PGN) && ! path.equals(EXT_PGN);
    }

    static boolean isZipOk(String path) {
        path = path.toLowerCase();
        return path.endsWith(EXT_ZIP) && !path.endsWith(WRONG_ZIP) && !path.equals(EXT_ZIP);
    }

    interface FilAxProvider {
        FilAx newFilAx(String path);
        FilAx newFilAx(FilAx parent, String name);
        String getRootPath();
    }
}
