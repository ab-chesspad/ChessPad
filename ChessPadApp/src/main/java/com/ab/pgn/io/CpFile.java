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

 * work with all relevant files
 * Concrete subclasses: PgnItem, PgnFile, Zip, Dir
 * List directory/zip/pgn files, extract individual game (PgnItem) and add/update/delete game
 * Created by Alexander Bootman on 7/30/16.
 */
package com.ab.pgn.io;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public abstract class CpFile implements Comparable<CpFile> {
    private static final boolean DEBUG = false;
    public static final String DEBUG_TAG = Config.DEBUG_TAG + "CpFile ";

    public static final String
        EXT_TEMP = ".tmp",
        TAG_START = "[",
        TAG_END = "\"]",
        SLASH = File.separator,
        EXT_PGN = ".pgn",
        EXT_ZIP = ".zip",
        WRONG_PGN = SLASH + EXT_PGN,
        WRONG_ZIP = SLASH + EXT_ZIP,
        PARENT_ZIP = EXT_ZIP + SLASH,
        TAG_NAME_VALUE_SEP = " \"",
        DUMMY_PGN_NAME = "dummy" + CpFile.EXT_PGN,
        dummy_prv_str = null;

    private final static PgnLogger logger = PgnLogger.getLogger(CpFile.class);
    private static int i = -1;

    public enum CpFileType {
        // in the order of sorting:
        Dir(++i),
        Zip(++i),
        Pgn(++i),
        Item(++i),
        ItemName(++i),
        ;

        private static final CpFileType[] values = CpFileType.values();
        private final int value;

        CpFileType(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

    // all paths, including absPath are relative to rootDir!
    private static final Dir rootDir = new Dir(null, "");
    private static String rootPath;

    private CpFile() {
    }

    public static void setRoot(String path) {
        rootPath = path;
    }

    public static String getRootPath() {
        return rootPath;
    }

    // todo: verify if the path is legal
    public static boolean isPgnOk(String path) {
        path = path.toLowerCase();
        return path.endsWith(EXT_PGN) && !path.endsWith(WRONG_PGN);
    }

    private static boolean isZipOk(String path) {
        path = path.toLowerCase();
        return path.endsWith(EXT_ZIP) && !path.endsWith(WRONG_ZIP);
    }

    public static String concat(String parent, String name) {
        if (parent.endsWith(SLASH)) {
            return parent + name;
        }
        return parent + SLASH + name;
    }

    public static File newFile(String path) {
        return new File(concat(getRootPath(), path));
    }

    public static File newFile(String parent, String name) {
        return new File(concat(getRootPath(), parent), name);
    }

    public abstract String getDisplayLength();

    private static String getDisplayLength(int length) {
        char suffix = 'B';
        double len = length;
        if (length >= 1000000) {
            suffix = 'M';
            len /= 1000000;
        } else if (length >= 1000) {
            suffix = 'K';
            len /= 1000;
        }
        String format = "%.0f %c";
        if (suffix != 'B' && len < 100) {
            format = "%.2f %c";
        }
        return String.format(Locale.getDefault(), format, len, suffix);
    }

    public static CpFile unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            CpFileType cpFileType = CpFileType.values[reader.read(3)];
            CpFile unserialized = null;
            switch (cpFileType) {
                case Item:
                    unserialized = new PgnItem(reader);
                    break;

                case Pgn:
                    unserialized = new PgnFile(reader);
                    break;

                case Zip:
                    unserialized = new Zip(reader);
                    break;

                case Dir:
                    unserialized = new Dir(reader);
                    if (((Dir)unserialized).absPath == null) {
                        unserialized = rootDir;
                    }
                    break;
            }
            return unserialized;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }
    // https://en.wikipedia.org/wiki/Portable_Game_Notation#Tag_pairs
    // A quote inside a tag value is represented by the backslash immediately followed by a quote.
    // A backslash inside a tag value is represented by two adjacent backslashes.
    static String unescapeTag(String src) {
        // looks like Regex on each tag lead to memory fragmentation
        StringBuilder sb = new StringBuilder(src.length());
        final String DELIMITERS = "\\";
        StringTokenizer st = new StringTokenizer(src, DELIMITERS, true);
        boolean delimFound = false;
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (token.equals(DELIMITERS)) {
                if (!delimFound) {
                    delimFound = true;
                    continue;
                }
            }
            delimFound = false;
            sb.append(token);
        }
        return sb.toString();
    }

    static String escapeTag(String src) {
        return src.replaceAll("([\\\\\"])", "\\\\$1");
    }

    // when parseItems == false return raw pgn item text in item.moveText
    public static synchronized List<CpFile.PgnItem> parsePgnFile(InputStream is, boolean parseItems) throws Config.PGNException {
        final List<CpFile.PgnItem> pgnItems = new LinkedList<>();
        if (is == null) {
            return pgnItems; // crashes otherwise
        }

        parsePgnFile(is, new CpFile.EntryHandler() {
            @Override
            public boolean addOffset(int length, int totalLength) {
                return false;
            }

            @Override
            public boolean handle(int index, CpFile.PgnItem entry) {
                pgnItems.add(entry);
                return true;
            }

            @Override
            public boolean getMovesText(int index) {
                return true;
            }

            @Override
            public boolean skip(int index) {
                return false;
            }
        }, parseItems);

        return pgnItems;
    }

    // when parseItems == false return raw pgn item text in item.moveText
    public static synchronized void parsePgnFile(InputStream is, EntryHandler entryHandler, boolean parseItems) throws Config.PGNException {
        if (is == null) {
            return; // crashes otherwise
        }

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            int totalLength = is.available();
            int index = 0;
            PgnItem item = new PgnItem((PgnFile)null);
            StringBuilder sb = new StringBuilder(Config.STRING_BUF_SIZE);
            boolean inText = false;
            String line;
            int fileOffset = 0, lineCount = 0;

            while ((line = br.readLine()) != null) {
                ++lineCount;
                fileOffset += line.length() + 1;
                line = line.trim();
                if (DEBUG) {
                    System.out.println(String.format("line %s, fileOffset %s", lineCount, fileOffset));
                    if (totalLength > 0 && fileOffset > totalLength) {
                        System.out.println(String.format("fileOffset %s > %s, \"%s\"", fileOffset, totalLength, line));
                    }
                }
                if (entryHandler.addOffset(line.length() + 1, totalLength)) {
                    break;
                }
                if (line.startsWith(TAG_START) && line.endsWith(TAG_END)) {
                    if (inText) {
                        item.moveText = new String(sb);
                        if (!entryHandler.handle(index, item)) {
                            return;
                        }
                        sb.delete(0, sb.length());
                        if (parseItems) {
                            item = new PgnItem((PgnFile)null);
                        }
                        ++index;
                        inText = false;
                    }
                    if (parseItems) {
                        if (!entryHandler.skip(index)) {
                            parseTag(item, line);
                        }
                    } else {
                        sb.append(line).append("\n");
                    }
                } else {
                    inText = true;
                    if (entryHandler.getMovesText(index)) {
                        sb.append(line).append("\n");
                    }
                }
            }
            item.moveText = new String(sb);
            entryHandler.handle(index, item);
        } catch (Throwable e) {
            logger.error(e);
            throw new Config.PGNException(e);
        }
    }

    static void parseTag(PgnItem item, String line) {
        int i = line.indexOf(TAG_NAME_VALUE_SEP);
        if (i > 0) {
            try {
                String tLabel = unescapeTag(line.substring(TAG_START.length(), i));
                String tValue = unescapeTag(line.substring(i + TAG_NAME_VALUE_SEP.length(), line.length() - TAG_END.length()));
                if (tLabel.equals(Config.TAG_FEN)) {
                    item.setFen(tValue);
                } else {
                    item.setTag(tLabel, tValue);
                }
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }

    public String getRelativePath() {
        return toString();
    }

    protected abstract CpFileType getType();
    public abstract CpParent getParent();
    public abstract void setParent(PgnFile parent);
    abstract void copy(CpFile trg);

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            writer.write(getType().getValue(), 3);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
        _serialize(writer);
    }

    protected void _serialize(BitStream.Writer writer) throws Config.PGNException {
        throw new RuntimeException("stub!");
    }

    @Override
    public int compareTo(CpFile that) {
        int res = this.getType().value - that.getType().value;
        if (res == 0) {
            res = this.getRelativePath().compareTo(that.getRelativePath());
        }
        return res;
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[16384];
        int len;
        while ((len = is.read(buffer)) > 0) {
            os.write(buffer, 0, len);
        }
    }

    public static class PgnItemName extends CpFile {
        // list of PgnItem can be rather large, tens of thousands entries
        // so the idea is to reduce the size of the entry as much as possible
        String name;

//        public PgnItemName(String name) {
//            this.name = name;
//        }

        public PgnItemName(int index, PgnItem pgnItem) {
            name = "" + ++index + ". " + pgnItem.toString();
        }

        @Override
        protected CpFileType getType() {
            return CpFileType.ItemName;
        }

        @Override
        public CpParent getParent() {
            throw new RuntimeException(DEBUG_TAG + "Invalid use");
        }

        @Override
        public void setParent(PgnFile parent) {
            throw new RuntimeException(DEBUG_TAG + "Invalid use");
        }

        @Override
        void copy(CpFile trg) {
            ((PgnItemName)trg).name = name;     // do I need this?
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public String getDisplayLength() {
            return "";
        }

    }

    public static class PgnItem extends CpFile {
        CpParent parent;
        private List<Pair<String, String>> tags = new ArrayList<>();
        private String fen;
        private String moveText = "";
        transient private Map<String, String> tagMap;

        public PgnItem(PgnFile parent) {
            setParent(parent);
        }

        protected void _serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.writeString(parent.absPath);
                writer.writeString(this.fen);
// do I need this?                writer.writeString(this.moveText);
                serializeTagList(writer, tags);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        private PgnItem(BitStream.Reader reader) throws Config.PGNException {
            try {
                this.parent = (PgnFile) CpParent.fromPath(reader.readString());
                this.fen = reader.readString();
                // moveText?
                this.tags = unserializeTagList(reader);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        public static void serializeTagList(BitStream.Writer writer, List<Pair<String, String>> tags) throws Config.PGNException {
            try {
                if (tags == null) {
                    writer.write(0, 8);
                    return;
                }
                writer.write(tags.size(), 8);
                for (Pair<String, String> tag : tags) {
                    writer.writeString(tag.first);
                    writer.writeString(tag.second);
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        public static List<Pair<String, String>> unserializeTagList(BitStream.Reader reader) throws Config.PGNException {
            try {
                int totalTags = reader.read(8);
                if (totalTags == 0) {
                    return null;
                }
                List<Pair<String, String>> tags = new ArrayList<>();
                for (int i = 0; i < totalTags; ++i) {
                    String label = reader.readString();
                    if (label == null) {
                        label = "";         // should never happen
                    }
                    String value = reader.readString();
                    if (value == null) {
                        value = "";         // for editTags
                    }
                    tags.add(new Pair<>(label, value));
                }
                return tags;
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        @Override
        public String getDisplayLength() {
            return "";
        }

        public CpParent getParent() {
            return parent;
        }

        @Override
        public void setParent(PgnFile parent) {
            if (parent == null) {
                parent = (PgnFile)CpParent.fromPath(DUMMY_PGN_NAME);
            }
            this.parent = parent;
        }

        @Override
        protected CpFileType getType() {
            return CpFileType.Item;
        }

        public String getFen() {
            return fen;
        }

        public void setFen(String fen) {
            this.fen = fen;
        }

        public String getMoveText() {
            return moveText;
        }

        public void setMoveText(String moveText) {
            this.moveText = moveText;
        }

        @Override
        public boolean equals(Object that) {
            if (that == null) {
                return false;
            }
            if (!this.getClass().getName().equals(that.getClass().getName())) {
                return false;
            }
            return this.toString().equals(that.toString());
        }

        @Override
        public String getRelativePath() {
            return toString();
        }

        private Map<String, String> getTagMap() {
            if (tagMap == null) {
                tagMap = new HashMap<>();
                for (Pair<String, String> h : tags) {
                    tagMap.put(h.first, h.second);
                }
            }
            return tagMap;
        }

        @Override
        public String toString() {
            return titleTagsToString(tags);
        }

        public static String titleTagsToString(List<Pair<String, String>> tags) {
            if (tags == null) {
                tags = new ArrayList<>();
            }
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (String h : Config.titleTags) {
                String v = null;
                for (Pair<String, String> lt : tags) {
                    if (h.equals(lt.first)) {
                        v = lt.second;
                        break;
                    }
                }
                if (v == null) {
                    v = Config.TAG_UNKNOWN_VALUE;
                }
                sb.append(sep).append(v);
                sep = " - ";
            }
            return new String(sb);
        }

        public StringBuilder tagsToString(boolean cr2Space, boolean escapeTags) {
            return tagsToString(cr2Space, escapeTags, false);
        }

        public StringBuilder tagsToString(boolean cr2Space, boolean escapeTags, boolean skipEmptySTR) {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (Pair<String, String> h : tags) {
                String hName = h.first;
                if (escapeTags) {
                    hName = escapeTag(hName);
                }
                String hValue = h.second;
                if (hValue == null || hValue.isEmpty()) {
                    hValue = Config.TAG_UNKNOWN_VALUE;
                }
                if (escapeTags) {
                    hValue = escapeTag(hValue);
                }
                sb.append(sep).append("[").append(hName).append(" \"").append(hValue).append("\"]");
                sep = "\n";
            }
            if (this.fen != null) {
                sb.append(sep).append("[").append(Config.TAG_FEN).append(" \"").append(this.fen).append("\"]");
            }
            return sb;
        }

        public String toString(boolean cr2Space, boolean escapeTags) {
            StringBuilder sb = tagsToString(cr2Space, escapeTags);
            if (moveText != null && !moveText.isEmpty()) {
                sb.append("\n");
                if (cr2Space) {
                    sb.append(moveText.replaceAll("\n", " ")).append("\n");
                } else {
                    sb.append(moveText);
                }
            }
            return new String(sb);
        }
        public void save(int index, ProgressObserver progressObserver) throws Config.PGNException {
            Dir grandParent = parent.getRealParent();
            grandParent.saveGrandChild((PgnFile)parent, index, this, new ProgressNotifier(progressObserver));
        }

        @Override
        public void copy(CpFile _trg) {
            //            throw new Config.PGNException("method not applicable!");
            PgnItem trg = (PgnItem) _trg;
            trg.tags = cloneTags();
            trg.fen = fen;
            trg.moveText = moveText;
        }

        public String getTag(String label) {
            return getTagMap().get(label);
        }

        public void setTag(String tagName, String tagValue) {
            if (getTag(tagName) == null) {
                tags.add(new Pair<>(tagName, tagValue));
                tagMap.put(tagName, tagValue);
            }
        }

        public List<Pair<String, String>> getTags() {
            return tags;
        }

        public void setTags(List<Pair<String, String>> tags) {
            this.tags = tags;
            tagMap = null;
        }

        // clone adding STR first, then the rest
        public static List<Pair<String, String>> cloneTags(List<Pair<String, String>> tags) {
            Map<String, String> tagMap = new HashMap<>();
            // 1. copy DTR
            for (String t : Config.STR) {
                tagMap.put(t, Config.TAG_UNKNOWN_VALUE);
            }
            // 2. Override with tags
            for (Pair<String, String> tag : tags) {
                tagMap.put(tag.first, tag.second);
            }
            List<Pair<String, String>> res = new ArrayList<>();
            // 3. put STR tags first
            for (String t : Config.STR) {
                String v = tagMap.remove(t);
                res.add(new Pair<>(t, v));
            }
            // 3. put the remaining tags preserving the order
            for (Pair<String, String> tag : tags) {
                if (tagMap.remove(tag.first) != null) {
                    res.add(tag);
                }
            }
            return res;
        }

        public List<Pair<String, String>> cloneTags() {
            return cloneTags(this.tags);
        }

        // if moveText == null -> delete item
        // if updIndex == -1 -> append item
        // returns number of all PgnItems
        int modifyItem(final PgnFile parent, final int updIndex, InputStream is, final OutputStream os,
                       final ProgressNotifier progressNotifier) throws Config.PGNException {
            try {
                final int[] count = {0};
                final int[] offset = {0};
                if (is == null) {
                    // brand new parent, no file yet
                    if (parent.totalChildren >= 0) {
                        ++parent.totalChildren;
                    } else {
                        parent.totalChildren = 1;
                    }
                    byte[] buf = this.toString(false, true).getBytes("UTF-8");
                    os.write(buf, 0, buf.length);
                    ++count[0];
                } else {
                    parsePgnFile(is, new EntryHandler() {
                        @Override
                        public boolean addOffset(int length, int totalLength) {
                            offset[0] += length;
                            if (progressNotifier != null) {
                                return progressNotifier.setOffset(offset[0], totalLength);
                            }
                            return false;
                        }

                        @Override
                        public boolean handle(int index, CpFile.PgnItem entry) throws Config.PGNException {
                            try {
                                if (updIndex == index) {
                                    if (PgnItem.this.moveText == null) {
                                        if (parent.totalChildren > 0) {
                                            --parent.totalChildren;
                                        }
                                        return true;        // skip item - delete it
                                    }
                                    entry.moveText = PgnItem.this.toString(false, true);    // the whole item
                                }
                                if (entry.moveText != null) {
                                    byte[] buf = entry.moveText.getBytes(StandardCharsets.UTF_8);
                                    os.write(buf, 0, buf.length);
                                    buf = "\n".getBytes(StandardCharsets.UTF_8);
                                    os.write(buf, 0, buf.length);
                                    ++count[0];
                                }
                                return true;
                            } catch (IOException e) {
                                throw new Config.PGNException(e);
                            }
                        }

                        @Override
                        public boolean getMovesText(int index) {
                            return updIndex != index;
                        }

                        @Override
                        public boolean skip(int index) {
                            return false;
                        }

                    }, false);

                    if (updIndex == -1) {
                        byte[] buf = this.toString(false, true).getBytes("UTF-8");
                        os.write(buf, 0, buf.length);
                        ++count[0];
                        if (parent.getTotalChildren() == -1) {
                            parent.setTotalChildren(count[0]);
                        }
                    }
                }
                return count[0];
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

    public abstract static class CpParent extends CpFile {
        CpParent parent;
        String absPath;     // relative to root
        int totalChildren = -1;
        int length = 0;
        transient public int offset = 0;

        public abstract List<CpFile> getChildrenNames(ProgressObserver progressObserver) throws Config.PGNException;

        public static CpParent fromPath(String path) {
            if (path == null || path.isEmpty() || path.equals(SLASH)) {
                return rootDir;
            }
            if (path.startsWith(SLASH)) {
                path = path.substring(SLASH.length());
            }

            String rootPath = getRootPath();
            String parentPath = getParentPath(path);
            String relativePath = path.substring(parentPath.length());
            if (relativePath.startsWith(SLASH)) {
                relativePath = relativePath.substring(SLASH.length());
            }
            CpParent parent = fromPath(parentPath);
            if (parent instanceof PgnFile) {
                // correction for paths like a/b/1.pgn/2.pgn
                Dir dir = new Dir(parent.parent, "");
                dir.absPath = parent.absPath;
                parent = dir;
            }
            CpParent res;
            if (new File(rootPath + parentPath, path).isDirectory()) {
                res = new Dir(parent, relativePath);
            } else if (isPgnOk(path)) {
                res = new PgnFile(parent, relativePath);
            } else if (isZipOk(path)) {
                res = new Zip(parent, relativePath);
            } else {
                res = new Dir(parent, relativePath);
            }
            return res;
        }

        static String getParentPath(String path) {
            if (path.endsWith(SLASH)) {
                path = path.substring(0, path.length() - SLASH.length());
            }
            path = concat(rootPath, path);
            int i = path.lastIndexOf(SLASH);
            path = path.substring(0, i);

            if (!new File(path).isDirectory()) {
                String[] parts = path.split(SLASH);
                path = "";
                String sep = "";
                for (String part : parts) {
                    path += sep + part;
                    if (!new File(path).isDirectory()) {
                        if (isZipOk(path)) {
                            break;
                        }
                    }
                    sep = SLASH;
                }
            }
            if (path.length() <= rootPath.length()) {
                return "";
            }
            return path.substring(rootPath.length());
        }

        private CpParent(CpParent parent, String name) {
            if (parent == null) {
                this.absPath = name;    // init root
            } else {
                this.parent = parent;
                String path = parent.getAbsolutePath();
                if (path.isEmpty()) {
                    path = name;
                } else {
                    path = concat(parent.getAbsolutePath(), name);
                }
                this.absPath = path;
            }
        }

        @Override
        protected void _serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.write(length, 32);
                writer.write(offset, 32);
                writer.write(totalChildren, 32);
                writer.writeString(getAbsolutePath());
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        private CpParent(BitStream.Reader reader) throws Config.PGNException {
            try {
                this.length = reader.read(32);
                this.offset = reader.read(32);
                this.totalChildren = reader.read(32);
                this.absPath = reader.readString();
                CpFile.CpParent tmp = fromPath(absPath);
                this.parent = tmp.parent;
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        @Override
        public CpParent getParent() {
            return parent;
        }

        @Override
        public void setParent(PgnFile parent) {
            if (parent == null) {
                parent = (PgnFile)CpParent.fromPath(DUMMY_PGN_NAME);
            }
            this.parent = parent;
        }

        @Override
        public String getRelativePath() {
            String parentPath = parent.getAbsolutePath();
            int len = parentPath.length();
            if (parentPath.startsWith(SLASH)) {
                ++len;
            }
            String res = this.absPath.substring(len);
            if (res.startsWith(SLASH)) {
                res = res.substring(SLASH.length());
            }
            return res;
        }

        protected Dir getRealParent() {
            Dir parent = (Dir) getParent();
            while (parent != null) {
                if (parent instanceof Zip) {
                    break;
                }
                parent = (Dir) parent.parent;
            }
            if (parent == null) {
                parent = (Dir) getParent();
            }
            return parent;
        }

        public boolean isRoot() {
            return this == rootDir;
        }

        public int getTotalChildren() {
            return totalChildren;
        }

        public void setTotalChildren(int totalChildren) {
            this.totalChildren = totalChildren;
        }

        public long lastModified() {
            File file = new File(getRootPath() + absPath);
            return file.lastModified();
        }

        public int getLength() throws Config.PGNException {
            if (this.length == 0) {
                List<CpFile> children = getChildrenNames(null);
                this.length = children.size();
            }
            return this.length;
        }

        public String getDisplayLength() {
            int len = 0;
            try {
                len = getLength();
            } catch (Config.PGNException e) {
                logger.error(e.getMessage(), e);
            }
            if (len > 0) {
                return "" + len;
            }
            return "";
        }

        public String getAbsolutePath() {
            return this.absPath;
        }

        public boolean differs(CpParent item) {
            if (item == null) {
                return true;
            }
            String absPath = getAbsolutePath();
            if (absPath == null) {
                absPath = "";
            }
            return !absPath.equals(item.getAbsolutePath());
        }

        @Override
        public boolean equals(Object that) {
            if (that == null) {
                return false;
            }
            if (!this.getClass().getName().equals(that.getClass().getName())) {
                return false;
            }
            if (!this.getAbsolutePath().equals(((CpParent) that).getAbsolutePath())) {
                return false;
            }
            if (this.parent == null) {
                return ((CpParent) that).parent == null;
            }
            return this.parent.equals(((CpParent) that).parent);
        }

        @Override
        void copy(CpFile _trg) {
            CpParent trg = (CpParent)_trg;
            trg.parent = parent;
            trg.absPath = absPath;
            trg.totalChildren = totalChildren;
            trg.length = length;
        }
    }

    public static class PgnFile extends CpParent {

        PgnFile(CpParent parent, String name) {
            super(parent, name);
        }

        private PgnFile(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        public String getAbsolutePath() {
            return this.absPath;
        }

        @Override
        protected CpFileType getType() {
            return CpFileType.Pgn;
        }

        @Override
        public int getLength() {
            if (this.length == 0) {
                File self = newFile(absPath);
                this.length = (int) self.length();   // truncate
            }
            return this.length;
        }

        @Override
        public String getDisplayLength() {
            return CpFile.getDisplayLength(getLength());
        }

        // using searchIndex find PgnItem
        // if not found, return the last entry
        public PgnItem getPgnItem(final int searchIndex, ProgressObserver progressObserver) throws Config.PGNException {
            final PgnItem[] pgnItem = {new PgnItem((PgnFile) null)};
            final ProgressNotifier progressNotifier = new ProgressNotifier(progressObserver);
            final PgnItem[] lastEntry = {new PgnItem((PgnFile) null)};
            Dir parent = getRealParent();
            parent.offset = 0;
            final int[] entryIndex = {0};
            parent.scrollGrandChildren(this, new EntryHandler() {
                public boolean addOffset(int length, int totalLength) {
                    parent.offset += length;
                    return progressNotifier.setOffset(parent.offset, totalLength);
                }

                @Override
                public boolean handle(int index, CpFile.PgnItem entry) {
                    if (PgnFile.this.totalChildren <= index) {
                        PgnFile.this.totalChildren = index + 1;
                    }
                    entry.parent = PgnFile.this;
                    lastEntry[0] = entry;
                    if (index != searchIndex) {
                        return true;    // continue
                    }
                    entryIndex[0] = searchIndex;
                    pgnItem[0] = entry;
                    pgnItem[0].parent = entry.parent;
                    return false;       // abort
                }

                @Override
                public boolean getMovesText(int index) {
                    return index == searchIndex;
                }

                @Override
                public boolean skip(int index) {
                    return index != searchIndex;
                }
            });
            // in case item is not found, return the last entry
            // todo: it does not contain any info, so m.b. return null??
            if (searchIndex != entryIndex[0]) {
                return lastEntry[0];
            }
            return pgnItem[0];
        }

        @Override
        public List<CpFile> getChildrenNames(ProgressObserver progressObserver) {
            final List<CpFile> items = new ArrayList<>();
            String path = concat(getRootPath(), parent.absPath);
            if (!new File(path).exists()) {
                // this check needed for zipped PgnFile
                return items;
            }

            final int[] offset = {0};
            final ProgressNotifier progressNotifier = new ProgressNotifier(progressObserver);
            Dir parent = getRealParent();

            try {
                parent.scrollGrandChildren(this, new EntryHandler() {
                    @Override
                    public boolean addOffset(int length, int totalLength) {
                        offset[0] += length;
                        if (offset[0] > totalLength) {
                            System.out.println(String.format("%s > %s", offset[0], totalLength));
                        }
                        return progressNotifier.setOffset(offset[0], totalLength);
                    }

                    @Override
                    public boolean handle(int index, CpFile.PgnItem entry) {
                        items.add(new PgnItemName(index, entry));
                        return true;
                    }

                    @Override
                    public boolean getMovesText(int index) {
                        return false;
                    }

                    @Override
                    public boolean skip(int index) {
                        return false;
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
                progressNotifier.setOffset(ProgressNotifier.LIST_TRUNCATION, 0);
            }
            totalChildren = items.size();
            return items;
        }

        @Override
        public long lastModified() {
            try {
                if (getParent() instanceof Zip) {
                    return ((Zip)getParent()).getChildTS(this);
                } else {
                    return super.lastModified();
                }
            } catch (Config.PGNException e) {
                logger.error(e.getMessage(), e);
            }
            return 0;
        }
    }

    public static class Dir extends CpParent {
        public Dir(CpParent parent, String name) {
            super(parent, name);
        }

        private Dir(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        protected CpFileType getType() {
            return CpFileType.Dir;
        }

        @Override
        public List<CpFile> getChildrenNames(ProgressObserver progressObserver) throws Config.PGNException {
            final List<CpFile> fileList = new ArrayList<>();
            scrollChildren(new EntryHandler() {
                @Override
                public boolean addOffset(int length, int totalLength) {
                    logger.debug(String.format(Locale.getDefault(), "Dir addOffset %d, total %d", length, totalLength));
                    return false;
                }

                @Override
                public boolean getMovesText(int index) {
                    return false;
                }

                @Override
                public boolean skip(int index) {
                    return false;
                }

                @Override
                public boolean handle(CpFile pgnFile, InputStream is) {
                    fileList.add(pgnFile);
                    return true;
                }

                @Override
                public boolean handle(int index, CpFile.PgnItem item) {
                    throw new RuntimeException(String.format(DEBUG_TAG + "Invalid entry %s in %s", item.toString(), Dir.this.absPath));
                }

            });
            this.totalChildren = fileList.size();
            Collections.sort(fileList);
            return fileList;
        }

        void scrollChildren(final EntryHandler handler) throws Config.PGNException {
            final int[] index = {-1};
            File self = newFile(absPath);
            File[] list = self.listFiles((file) -> {
                String name = file.getName();
                if (name.startsWith("."))
                    return false;
                CpParent entry;
                if (file.isDirectory()) {
                    entry = new Dir(Dir.this, name);
                } else if (CpFile.isPgnOk(name)) {
                    entry = new PgnFile(Dir.this, name);
                } else if (CpFile.isZipOk(name)) {
                    entry = new Zip(Dir.this, name);
                } else {
                    return false;
                }

                if (entry instanceof PgnFile || entry instanceof Zip) {
                    entry.length = (int)file.length();
                }

                ++index[0];
                try {
                    if (entry instanceof PgnFile) {
                        try (InputStream is = new FileInputStream(file)) {
                            handler.handle(entry, is);
                        } catch (IOException e) {
                            logger.debug(file.getAbsoluteFile(), e);
                        }
                    } else {
                        handler.handle(entry, null);
                    }
                } catch (Config.PGNException e) {
                    logger.debug(file.getAbsoluteFile(), e);
                }
                return false;    // drop it, save space
            });
            this.totalChildren = ++index[0];
        }

        // scroll PgnFile child's children
        // this is a kludgy way to reuse the code for directory and zip
        public void scrollGrandChildren(final PgnFile child, final EntryHandler entryHandler) throws Config.PGNException {
            this.offset = 0;
            scrollChildren(new EntryHandler() {
                @Override
                public boolean addOffset(int length, int totalLength) {
                    Dir.this.offset += length;
                    Dir.this.length = totalLength;
                    return false;
                }

                @Override
                public boolean getMovesText(int index) {
                    return true;
                }

                @Override
                public boolean skip(int index) {
                    return false;
                }

                @Override
                public boolean handle(CpFile entry, InputStream is) throws Config.PGNException {
                    if (child.absPath.equals(((CpParent) entry).absPath)) {
                        child.length = ((CpParent) entry).length;
                        parsePgnFile(is, entryHandler, true);
                        return false;       // abort
                    }
                    return true;            // continue
                }

                @Override
                public boolean handle(int index, CpFile.PgnItem item) {
                    throw new RuntimeException(String.format(DEBUG_TAG + "Invalid entry %s in %s", item.toString(), Dir.this.absPath));
                }
            });
        }

        void saveGrandChild(PgnFile pgnFile, int index, PgnItem pgnItem, ProgressNotifier progressNotifier) throws Config.PGNException {
            File dir = newFile(pgnFile.absPath).getParentFile();
            if (!dir.exists()) {
                boolean ok = dir.mkdirs();
                if (!ok) {
                    throw new Config.PGNException(String.format("Cannot create directory %s", dir.getAbsoluteFile()));
                }
            }
            File oldFile = CpFile.newFile(pgnFile.absPath);
            _saveGrandChild(pgnFile, index, pgnItem, oldFile, progressNotifier);
        }

        void _saveGrandChild(final PgnFile pgnFile, int index, final PgnItem pgnItem, File oldFile, ProgressNotifier progressNotifier) throws Config.PGNException {
            String tmpFileName = oldFile.getAbsolutePath() + EXT_TEMP;
            File tmpFile = new File(tmpFileName);
            int count = 0;  //
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                // create a new file with updated item
                count = saveGrandChild(pgnFile, index, pgnItem, fos, progressNotifier);

            } catch (IOException e) {
                throw new Config.PGNException(e);
            }

            if (oldFile.exists()) {
                // delete old file then rename tmp to original name
                if (!oldFile.delete()) {
                    throw new Config.PGNException("Cannot delete " + oldFile.getAbsolutePath());
                }
            }
            // rename tmp to original name
            boolean delete = this.totalChildren == 0 && pgnFile.totalChildren == 0 && pgnItem.moveText == null;
            if (delete) {
                boolean res = tmpFile.delete();
                logger.debug(String.format("deleting %s, %s", tmpFile.getAbsolutePath(), res));
                File rootFile = new File(CpFile.getRootPath());
                File dir = new File(CpFile.getRootPath() + this.absPath);
                while (!dir.getAbsolutePath().equals(rootFile.getAbsolutePath())) {
                    File parent = dir.getParentFile();
                    dir.delete();
                    dir = parent;
                }
            } else {
                boolean res = tmpFile.renameTo(oldFile);
                logger.debug(String.format("renaming %s, %s", oldFile.getAbsolutePath(), res));
            }
        }

        // return entry count
        int saveGrandChild(final PgnFile parent, int index, PgnItem item, FileOutputStream fos, ProgressNotifier progressNotifier) throws Config.PGNException {
            int count = 0;
            try (FileInputStream fis = new FileInputStream(concat(getRootPath(), parent.absPath))) {
                count += item.modifyItem(parent, index, fis, fos, progressNotifier);
                if (parent.totalChildren == 0) {
                    if (this.totalChildren > 0) {
                        --this.totalChildren;
                    }
                }
            } catch (FileNotFoundException e) {
                count += item.modifyItem(parent, index, null, fos, progressNotifier);
            } catch (IOException e) {
                logger.debug(parent.absPath, e);
            }
            return count;
        }
    }

    public static class Zip extends Dir {
        Zip(CpParent parent, String name) {
            super(parent, name);
        }

        private Zip(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        protected CpFileType getType() {
            return CpFileType.Zip;
        }

        @Override
        public int getLength() {
            if (this.length == 0) {
                File self = new File(getRootPath() + absPath);
                this.length = (int) self.length();   // truncate
            }
            return this.length;
        }

        @Override
        public String getDisplayLength() {
            return CpFile.getDisplayLength(getLength());
        }

        @Override
        void scrollChildren(EntryHandler zipEntryHandler) throws Config.PGNException {
            String path = concat(getRootPath(), absPath);
            File f = new File(path);
            if (!f.exists()) {
                return;
            }
            this.length = (int)f.length();
            try (ZipFile zipFile = new ZipFile(path)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                int index = -1;
                while (entries.hasMoreElements()) {
                    ++index;
                    ZipEntry ze = entries.nextElement();
                    if (ze.isDirectory() || !CpFile.isPgnOk(ze.getName())) {
                        continue;
                    }
                    PgnFile pgnFile = new PgnFile(Zip.this, ze.getName());
                    pgnFile.length = (int) ze.getSize();
                    pgnFile.length = (int) ze.getSize();    // truncate!
                    InputStream is = zipFile.getInputStream(ze);
                    if (!zipEntryHandler.handle(pgnFile, is)) {
                        break;
                    }
                }
            } catch (Throwable t) {
                throw new Config.PGNException(t);
            }
        }

        long getChildTS(CpFile child) throws Config.PGNException {
            long ts = 0;
            String name = child.toString();
            try (ZipFile zipFile = new ZipFile(getRootPath() + absPath)) {
                ZipEntry ze = zipFile.getEntry(name);
                ts = ze.getTime();
            } catch (IOException t) {
                throw new Config.PGNException(t);
            }
            return ts;
        }

        @Override
        void saveGrandChild(PgnFile pgnFile, int index, PgnItem pgnItem, ProgressNotifier progressNotifier) throws Config.PGNException {
            File dir = newFile(parent.absPath);
            if (!dir.exists()) {
                boolean ok = dir.mkdirs();
                if (!ok) {
                    throw new Config.PGNException(String.format("Cannot create directory %s", dir.getAbsoluteFile()));
                }
            }
            // call super:
            _saveGrandChild(pgnFile, index, pgnItem, newFile(this.absPath), progressNotifier);
        }

        @Override
        int saveGrandChild(final PgnFile pgnFile, final int updIndex, final PgnItem item, FileOutputStream fos, ProgressNotifier progressNotifier) throws Config.PGNException {
            final byte[] data = new byte[Config.MY_BUF_SIZE];
            final boolean[] found = {false};
            final int[] count = {0};
            Zip.this.offset = 0;
            try (final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {
                scrollChildren(new EntryHandler() {
                    @Override
                    public boolean addOffset(int length, int totalLength) {
                        Zip.this.offset += length;
                        return false;
                    }

                    @Override
                    public boolean handle(CpFile entry, InputStream is) throws Config.PGNException {
                        try {
                            if (pgnFile.absPath.equals(((CpParent)entry).absPath)) {
                                if (item.moveText == null && updIndex == 0 && pgnFile.totalChildren == 1) {
                                    // remove the last item
                                    item.modifyItem(pgnFile, updIndex, is, null, progressNotifier);
                                    logger.debug(pgnFile.getAbsolutePath() + " removing the last item!");
                                    pgnFile.length = 0;
                                    found[0] = true;
                                    if (pgnFile.parent.totalChildren > 0) {
                                        --pgnFile.parent.totalChildren;
                                    }
                                    ++count[0];     // need to remove old file and rename tmpFile
                                    return true;
                                }
                            }

                            String relativePath = entry.getRelativePath();
                            ZipEntry zeOut = new ZipEntry(relativePath);
                            zos.putNextEntry(zeOut);
                            if (pgnFile.absPath.equals(((CpParent)entry).absPath)) {
                                // mofified PgnFile
                                progressNotifier.setOffset(ProgressNotifier.SET_TOTAL_LENGTH, ((CpParent) entry).getLength());
                                count[0] += item.modifyItem(pgnFile, updIndex, is, zos, progressNotifier);
                                found[0] = true;
                            } else {
                                // blind copy
                                int _count;
                                while ((_count = is.read(data, 0, Config.MY_BUF_SIZE)) != -1) {
                                    zos.write(data, 0, _count);
                                }
                            }
                            zos.flush();
                            zos.closeEntry();
                            return true;
                        } catch (IOException e) {
                            throw new Config.PGNException(e);
                        }
                    }

                    @Override
                    public boolean handle(int index, CpFile.PgnItem entry) {
                        throw new RuntimeException(String.format(DEBUG_TAG + "Invalid entry %s, %s in %s", index, item.toString(), Zip.this.absPath));
                    }

                    @Override
                    public boolean getMovesText(int index) {
                        return false;
                    }

                    @Override
                    public boolean skip(int index) {
                        return false;
                    }
                });
                if (!found[0]) {
                    String relativePath = pgnFile.getRelativePath();
                    ZipEntry zeOut = new ZipEntry(relativePath);
                    zos.putNextEntry(zeOut);
                    count[0] += item.modifyItem(pgnFile, updIndex, null, zos, progressNotifier);
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
            return count[0];
        }
    }

    public static class ProgressNotifier {
        private static final int SET_TOTAL_LENGTH = -1;
        private static final int LIST_TRUNCATION = -2;
        private final ProgressObserver progressObserver;
        private int totalLength;

        public ProgressNotifier(ProgressObserver progressObserver) {
            this.progressObserver = progressObserver;
            totalLength = 0;
            if (DEBUG) {
                System.out.println(String.format("New ProgressNotifier %s", this));
            }
        }

        public boolean setOffset(int offset, int totalLength) {       // return true to abort
            if (offset == SET_TOTAL_LENGTH) {
                this.totalLength = totalLength;
                return false;
            }
            if (offset == LIST_TRUNCATION) {
                boolean done = false;
                if (progressObserver != null) {
                    done = progressObserver.setProgress(-1);
                }
                return done;
            }
            if (totalLength == 0) {
                totalLength = this.totalLength;
            }
            boolean done = false;
            if (progressObserver != null) {
                done = progressObserver.setProgress(getRelativeOffset(offset, totalLength));
            }
            return done;
        }

        private int getRelativeOffset(int offset, int totalLength) {
            if (totalLength == 0) {
                return 0;
            }
            if (offset > totalLength) {
                offset = totalLength;
            }
            return (int) ((long) offset * 100 / (long) totalLength);
        }
    }

    public interface ProgressObserver {
        boolean setProgress(int progress);  // return true to abort
    }

    public interface EntryHandler {
        boolean addOffset(int length, int totalLength);     // return true to abort
        boolean getMovesText(int index);
        boolean skip(int index);

/*
        default boolean skip(CpFile cpFile) {               // return true to skip
            return false;
        }
        default boolean skip(int index, CpFile cpFile) {    // return true to skip
            return false;
        }
*/

        boolean handle(int index, CpFile.PgnItem entry) throws Config.PGNException;    // return false to break iteration

        default boolean handle(CpFile entry, InputStream is) throws Config.PGNException {   // return false to break iteration
            return true;
        }
    }
}
