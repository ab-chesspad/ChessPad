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
package com.ab.droid.chesspad.io;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.ab.droid.chesspad.MainActivity;
import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.io.FilAx;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DocFilAx implements FilAx {
    public static final String
        MIME_TYPE_PGN = "application/x-chess-pgn",
        MIME_TYPE_ZIP = "application/zip",
        URI_SEPARATOR = "%2F",
        SLASH = "/",
        str_dummy = null;
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private static Context context;
    private static DocumentFile rootDocumentFile;
    private DocumentFile documentFile;

    public static void setRoot(Context context, String uriString) throws Config.PGNException {
        DocFilAx.context = context;
        Uri uri = Uri.parse(uriString);
        rootDocumentFile = DocumentFile.fromTreeUri(context, uri);
        if (!rootDocumentFile.canWrite()) {
            rootDocumentFile = null;
            throw new Config.PGNException("Invalid root directory, must be writeable");
        }
    }

    public static DocumentFile getRoot() {
        return rootDocumentFile;
    }

    public static String getParentPath(String path) {
        String res = SLASH;
        String sep = "";
        if (path == null || path.isEmpty() || path.equals(SLASH)) {
            return res;
        }
        DocumentFile documentFile = rootDocumentFile;
        String[] names = path.split(SLASH);
        for (String name : names) {
            if (name.isEmpty()) {
                continue;
            }
            DocumentFile childDocumentFile = documentFile.findFile(name);
            if (childDocumentFile == null) {
                return res;
            }
            res += sep + childDocumentFile.getName();
            sep = SLASH;
            documentFile = childDocumentFile;
        }
        return res;
    }

    public DocFilAx(FilAx parent, String name) {
        init(((DocFilAx)parent).documentFile, name);
    }

    public DocFilAx(String path) {
        init(rootDocumentFile, path);
    }

    private void init(DocumentFile parentDocumentFile, String path) {
        documentFile = parentDocumentFile;
        if (path == null || path.isEmpty() || path.equals(SLASH)) {
            return;
        }

        String[] names = path.split(SLASH);
        for (String name : names) {
            if (name.isEmpty()) {
                continue;
            }
            DocumentFile childDocumentFile = documentFile.findFile(name);
            if (childDocumentFile != null) {
                documentFile = childDocumentFile;
                continue;
            }
            if (FilAx.isPgnOk(name)) {
                documentFile = documentFile.createFile(MIME_TYPE_PGN, name);
            } else if (FilAx.isZipOk(name)) {
                documentFile = documentFile.createFile(MIME_TYPE_ZIP, name);
            } else {
                documentFile = documentFile.createDirectory(name);
            }
        }
    }

    private DocFilAx(DocumentFile documentFile) {
        this.documentFile = documentFile;
    }

    @Override
    public FilAx getParent() {
        return new DocFilAx(documentFile.getParentFile());
    }

    public DocFilAx(BitStream.Reader reader) throws Config.PGNException {
        context = MainActivity.getContext();
        try {
            String uriString = reader.readString();
            setRoot(context, uriString);
            uriString = reader.readString();
            documentFile = fromUriString(uriString);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            writer.writeString(rootDocumentFile.getUri().toString());
            writer.writeString(documentFile.getUri().toString());
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private DocumentFile fromUriString(String uriString) throws Config.PGNException {
        if (uriString == null) {
            return null;
        }
        if (rootDocumentFile == null) {
            setRoot(context, uriString);
        }
        DocumentFile resultDocumentFile = rootDocumentFile;

        String parentPath = rootDocumentFile.getUri().toString();
        String relativePath = uriString.substring(parentPath.length());
        if (relativePath.isEmpty()) {
            return resultDocumentFile;
        }
        relativePath = relativePath.substring(URI_SEPARATOR.length());
        String[] names = relativePath.split(URI_SEPARATOR);
        for (String name : names) {
            DocumentFile[] childDocuments = resultDocumentFile.listFiles();
            for (DocumentFile childDocument : childDocuments) {
                if (name.equals(childDocument.getName())) {
                    resultDocumentFile = childDocument;
                    break;
                }
            }
        }
        Log.d(DEBUG_TAG, resultDocumentFile.getUri().getPath());
        return resultDocumentFile;
    }

    public static String getRootUri() {
        return rootDocumentFile.getUri().toString();
    }

    public boolean isWriteable() {
        return documentFile.canWrite();
    }

    @Override
    public String[] listFiles() {
        DocumentFile[] list = documentFile.listFiles();
        String[] res = new String[list.length];
        for (int i = 0; i < list.length; ++i) {
            res[i] = list[i].getName();
        }
        return res;
    }

    @Override
    public boolean isDirectory() {
        String type = documentFile.getType();
        return type == null;
    }

    @Override
    public OutputStream getOutputStream() throws FileNotFoundException {
        return context.getContentResolver().openOutputStream(documentFile.getUri(), "wt");
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException {
        return context.getContentResolver().openInputStream(documentFile.getUri());
    }

    @Override
    public boolean mkdirs() {
        return false;
    }

    @Override
    public boolean renameTo(String newName) {
        return documentFile.renameTo(newName);
    }

    @Override
    public boolean exists() {
        return documentFile.exists();
    }

    @Override
    public boolean delete() {
        return documentFile.delete();
    }

    @Override
    public int length() {
        return (int)documentFile.length();
    }

    @Override
    public String getName() {
        return documentFile.getName();
    }

    @Override
    public long lastModified() {
        return documentFile.lastModified();
    }
}
