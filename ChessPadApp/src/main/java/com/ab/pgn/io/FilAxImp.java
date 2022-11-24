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
 File accessor implementation, to separate CP logic from the actual file manipulation
 needed to use androidx.documentfile.provider.DocumentFile
 */
package com.ab.pgn.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class FilAxImp implements FilAx {
    private static FilAxProvider filAxProvider;
    private final File file;

    public static void setFilAxProvider(FilAxProvider filAxProvider) {
        FilAxImp.filAxProvider = filAxProvider;
    }

    public FilAxImp(FilAx parent, String name) {
        file = new File(((FilAxImp)parent).file, name);
    }

    public FilAxImp(String pathName) {
        file = new File(filAxProvider.getRootPath(), pathName);
    }

    public FilAxImp(String parentPath, String name) {
        file = new File(filAxProvider.getRootPath() + parentPath, name);
    }

    private FilAxImp(File file) {
        this.file = file;
    }

    @Override
    public FilAx getParent() {
        return new FilAxImp(file.getParentFile());
    }

    @Override
    public String[] listFiles() {
        File[] list = file.listFiles();
        if (list == null) {
            return null;
        }
        String[] res = new String[list.length];
        for (int i = 0; i < list.length; ++i) {
            res[i] = list[i].getName();
        }
        return res;
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public OutputStream getOutputStream() throws FileNotFoundException {
        return new FileOutputStream(file);
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException {
        return new FileInputStream(file);
    }

    @Override
    public boolean mkdirs() {
        return file.mkdirs();
    }

    public boolean renameTo(FilAx that) {
        return file.renameTo(((FilAxImp)that).file);
    }

    @Override
    public boolean renameTo(String newName) {
        File f = new File(file.getParentFile(), newName);
        return file.renameTo(f);
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public boolean delete() {
        return file.delete();
    }

    @Override
    public int length() {
        return (int)file.length();
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public long lastModified() {
        return file.lastModified();
    }
}
