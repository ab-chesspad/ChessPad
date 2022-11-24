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

 * Created by Alexander Bootman on 7/30/16.
 */
package com.ab.pgn.io;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnLogger;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Concrete subclasses: PgnFile, Zip, Dir, PgnItem
 * List directory/zip/pgn file, extract individual game (PgnItem) and add/update/delete game
 */
public abstract class CpFile implements Comparable<CpFile> {
    private static final boolean DEBUG = false;
    public static final String DEBUG_TAG = Config.DEBUG_TAG + "CpFile ";

    public static final String
        COMMON_ITEM_NAME = "item",
        TAG_START = "[",
        TAG_END = "\"]",
        PARENT_ZIP = FilAx.EXT_ZIP + FilAx.SLASH,
        TAG_NAME_VALUE_SEP = " \"",
        DUMMY_PGN_NAME = ".dummy" + FilAx.EXT_PGN,
        dummy_prv_str = null;

    private final static PgnLogger logger = PgnLogger.getLogger(CpFile.class);
    private static int i = -1;
    public enum CpFileType {
        // in the order of sorting:
        Dir(++i),
        Zip(++i),
        Pgn(++i),
        Item(++i),
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

    static FilAx.FilAxProvider filAxProvider;
    // all paths, including absPath are relative to rootDir!
    @SuppressWarnings("StaticInitializerReferencesSubClass")
    private static final Dir rootDir = new Dir(null, "");

    public static final ProgressNotifier progressNotifier = new ProgressNotifier();

    CpFile parent;
    private String absPath;
    int length = 0;
    int totalChildren = -1;
    protected int index = -1;

    CpFile() {}

/* uncomment to emulate OOM
    int[] debugData = new int[4 * 1024];
//*/

    public static void setFilAxProvider(FilAx.FilAxProvider filAxProvider) {
        CpFile.filAxProvider = filAxProvider;
    }

    public static FilAx.FilAxProvider getFilAxProvider() {
        return filAxProvider;
    }

    public static void setProgressObserver(ProgressObserver progressObserver) {
        progressNotifier.setProgressObserver(progressObserver);
    }

    protected CpFile(CpFile parent, String name) {
        if (parent == null) {
            this.parent = rootDir;
            this.setAbsolutePath(name);    // init root
        } else {
            this.parent = parent;
            this.setAbsolutePath(concat(parent.getAbsolutePath(), name));
        }
    }

    private static String concat(String parent, String name) {
        if (parent.endsWith(FilAx.SLASH)) {
            return parent + name;
        }
        return parent + FilAx.SLASH + name;
    }

    public static FilAx newFile(String path) {
        return filAxProvider.newFilAx(path);
    }

    private static String getDisplayLength(int length) {
        if (length <= 0) {
            return "";
        }
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

    private static int compareNames(String name1, String name2) {
        Pattern p = Pattern.compile("^(\\d+)");
        Matcher m1 = p.matcher(name1);
        Matcher m2 = p.matcher(name2);
        int res;
        if (m1.find() && m2.find()) {
            String g1 = m1.group(1);
            String g2 = m2.group(1);
            res = g1.length() - g2.length();
            if (res != 0) {
                return res;
            }
        }
        res = name1.compareTo(name2);
        return res;
    }

    private CpFile(BitStream.Reader reader) throws Config.PGNException {
        try {
            this.length = reader.read(32);
            this.index = reader.read(24);
            if (index == 0x0ffffff) {
                index = -1;
            }
            this.totalChildren = reader.read(32);
            this.setAbsolutePath(reader.readString());
            CpFile tmp = fromPath(getAbsolutePath());
            this.parent = tmp.parent;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
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
                    if (isRoot(unserialized.absPath)) {
                        unserialized = rootDir;
                    }
                    break;
            }
            return unserialized;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static CpFile fromPath(String path) {
        if (isRoot(path)) {
            return rootDir;
        }
        String[] names = path.split(FilAx.SLASH);
        CpFile res = rootDir;
        String inZipPath = null;
        String zipPathSep = "";
        for (String name : names) {
            if (name.isEmpty()) {
                continue;
            }
            if (inZipPath == null) {
                if (res instanceof PgnFile) {
                    // e.g. /a/b/1.pgn/2.pgn, correct
                    res = new Dir(res.parent, res.getName());
                }
                FilAx filAx = filAxProvider.newFilAx(concat(res.getAbsolutePath(), name));
                if (filAx.isDirectory()) {
                    res = new Dir(res, name);
                } else if (FilAx.isPgnOk(name)) {
                    res = new PgnFile(res, name);
                } else if (FilAx.isZipOk(name)) {
                    res = new Zip(res, name);
                    inZipPath = "";
                } else {
                    res = new Dir(res, name);
                }
            } else {
                inZipPath += zipPathSep + name;
                zipPathSep = FilAx.SLASH;
            }
        }
        if (inZipPath != null && !inZipPath.isEmpty()) {
            if (FilAx.isPgnOk(inZipPath)) {
               res = new PgnFile(res, inZipPath);
            } else {
                res = new Dir(res, inZipPath);
            }
        }
        return res;
    }

    // when parseItems == false return raw pgn item text in item.moveText
    public static synchronized void parsePgnFile(CpFile parent, InputStream is, EntryHandler entryHandler, boolean parseItems) throws Config.PGNException {
        if (is == null) {
            return; // crashes otherwise
        }

        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            int totalLength = is.available();
            if (parent != null) {
                totalLength = parent.getLength();
            }
            progressNotifier.setTotalLength(totalLength);
            int index = 0;

            PgnItem pgnItem = new PgnItem(parent);
            StringBuilder sb = new StringBuilder(Config.STRING_BUF_SIZE);
            boolean inText = false;
            String line;
            int fileOffset = 0, lineCount = 0;

            while ((line = br.readLine()) != null) {
                ++lineCount;
                fileOffset += line.length() + 1;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (DEBUG) {
                    System.out.println(String.format("line %s, fileOffset %s", lineCount, fileOffset));
                    if (totalLength > 0 && fileOffset > totalLength) {
                        System.out.println(String.format("fileOffset %s > %s, \"%s\"", fileOffset, totalLength, line));
                    }
                }
                progressNotifier.setOffset(fileOffset);
                if (line.startsWith(TAG_START) && line.endsWith(TAG_END)) {
                    if (inText) {
                        pgnItem.moveText = new String(sb);
                        pgnItem.index = index;
                        if (!entryHandler.handle(index, pgnItem)) {
                            return;
                        }
                        sb.delete(0, sb.length());
                        if (parseItems) {
                            pgnItem = new PgnItem(parent);
                        }
                        ++index;
                        inText = false;
                    }
                    if (parseItems) {
                        if (!entryHandler.skip(index)) {
                            parseTag(pgnItem, line);
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
            pgnItem.moveText = new String(sb);
            pgnItem.index = index;
            entryHandler.handle(index, pgnItem);
        } catch (OutOfMemoryError e) {
            throw e;
        } catch (Throwable e) {
            if (e.getCause() != null) {
                e = e.getCause();
            }
            logger.error(e);
            if (e instanceof OutOfMemoryError) {
                throw (OutOfMemoryError)e;
            } else {
                throw new Config.PGNException(e);
            }
        }
    }

    public static void parseTag(PgnItem pgnItem, String line) {
        int i = line.indexOf(TAG_NAME_VALUE_SEP);
        if (i > 0) {
            try {
                String tLabel = unescapeTag(line.substring(TAG_START.length(), i));
                String tValue = unescapeTag(line.substring(i + TAG_NAME_VALUE_SEP.length(), line.length() - TAG_END.length()));
                if (tLabel.equals(Config.TAG_FEN)) {
                    pgnItem.setFen(tValue);
                } else {
                    pgnItem.setTag(tLabel, tValue);
                }
            } catch (Exception e) {
                logger.error(e);
            }
        }
    }

    // https://en.wikipedia.org/wiki/Portable_Game_Notation#Tag_pairs
    // A quote inside a tag value is represented by the backslash immediately followed by a quote.
    // A backslash inside a tag value is represented by two adjacent backslashes.
    public static String unescapeTag(String src) {
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

    public static String escapeTag(String src) {
        return src.replaceAll("([\\\\\"])", "\\\\$1");
    }

    public static String getTitle(List<Pair<String, String>> tags) {
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

    public static void copy(FilAx src, FilAx dst) throws IOException {
        try (InputStream inStream = src.getInputStream();
                OutputStream outStream = dst.getOutputStream()) {
            copy(inStream, outStream);
        }
    }

    public static void copy(InputStream inStream, OutputStream outStream) throws IOException {
        byte[] buffer = new byte[16384];
        while (true) {
            int len = inStream.read(buffer);
            if (len <= 0)
                break;
            outStream.write(buffer, 0, len);
        }
    }

    // relative to parent
    public String getRelativePath() {
        if (this == rootDir) {
            return "";
        }
        String parentPath = parent.getAbsolutePath();
        int len = parentPath.length();
        String res = this.getAbsolutePath().substring(len);
        if (res.startsWith(FilAx.SLASH)) {
            res = res.substring(FilAx.SLASH.length());
        }
        return res;
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
            List<Pair<String, String>> tags = new LinkedList<>();
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

    public abstract List<CpFile> getChildrenNames() throws Config.PGNException;

    protected Dir getRealParent() {
        Dir parent = (Dir)getParent();
        while (parent != null) {
            if (parent instanceof Zip) {
                break;
            }
            parent = (Dir)parent.parent;
        }
        if (parent == null) {
            parent = (Dir)getParent();
        }
        return parent;
    }

    public boolean isRoot() {
        return this == rootDir;
    }

    public static boolean isRoot(String path) {
        return path == null || path.isEmpty() || path.equals(FilAx.SLASH);
    }

    public int getLength() {
        if (this.length == 0) {
            List<CpFile> children;
            try {
                children = getChildrenNames();
                this.length = children.size();
            } catch (Config.PGNException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return this.length;
    }

    public String getDisplayLength() {
        int len = getLength();
        if (len > 0) {
            return "" + len;
        }
        return "";
    }

    public String getName() {
        String absPath = getAbsolutePath();
        int slash = absPath.lastIndexOf(FilAx.SLASH);
        if (slash < 0) {
            return absPath;
        }
        return absPath.substring(slash + 1);
    }

    // path relative to root
    public void setAbsolutePath(String absPath) {
        this.absPath = absPath;
    }

    // path relative to root
    public String getAbsolutePath() {
        return this.absPath;
    }

    public CpFile getParent() {
        return parent;
    }

    void setParent(CpFile parent) {
        this.parent = parent;
        String name = getName();
        this.setAbsolutePath(concat(parent.getAbsolutePath(), name));
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public String toString() {
        return getName();
    }

    public boolean differs(CpFile item) {
        if (item == null) {
            return true;
        }
        String thisPath = getAbsolutePath();
        String thatPath = item.getAbsolutePath();
        if (thisPath == null) {
            return thatPath == null;
        }
        return !getAbsolutePath().equals(item.getAbsolutePath());
    }

    @Override
    public boolean equals(Object that) {
        if (that == null) {
            return false;
        }
        if (!this.getClass().getName().equals(that.getClass().getName())) {
            return false;
        }
        String thisAbsPath = this.getAbsolutePath();
        String thatAbsPath = ((CpFile)that).getAbsolutePath();
        boolean res = true;
        if (thisAbsPath == null) {
            if (thatAbsPath != null) {
                return false;
            }
        } else if (!this.getAbsolutePath().equals(((CpFile)that).getAbsolutePath())) {
            return false;
        }
        if (this.parent == null) {
            return ((CpFile)that).parent == null;
        }
        return this.parent.equals(((CpFile) that).parent);
    }

    public int parentIndex(CpFile parent) throws Config.PGNException {
        String myPath = getAbsolutePath();
        if (myPath == null || parent == null || parent.getAbsolutePath() == null) {
            return -1;
        }
        if (!myPath.startsWith(parent.getAbsolutePath())) {
            return -1;
        }

        String parentPath = parent.getAbsolutePath();
        String relativePath = myPath.substring(parentPath.length() + 1);
        if (parent.getType() == CpFileType.Zip) {
            // SicilianGrandPrix.pgn/item
            String[] parts = relativePath.split("/");
            for (String part : parts) {
                if (FilAx.isPgnOk(part)) {
                    relativePath = part;
                    break;
                }
            }
        }
        List<CpFile> siblings = parent.getChildrenNames();
        for (int i = 0; i < siblings.size(); ++i) {
            CpFile sibling = siblings.get(i);
            String siblingName = sibling.getName();
            if (relativePath.startsWith(siblingName)) {
                if (relativePath.length() == siblingName.length() || relativePath.charAt(siblingName.length()) == '/') {
                    return i;
                }
            }
        }
        return -1;      // should not be here;
    }

    void serializeBase(BitStream.Writer writer) throws Config.PGNException {
        try {
            writer.write(getType().getValue(), 3);
            writer.write(length, 32);
            writer.write(index, 24);
            writer.write(totalChildren, 32);
            writer.writeString(getAbsolutePath());
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public abstract void serialize(BitStream.Writer writer) throws Config.PGNException;

    protected abstract CpFileType getType();

    @Override
    public int compareTo(CpFile that) {
        int res = this.getType().value - that.getType().value;
        if (res == 0) {
            res = this.getRelativePath().compareTo(that.getRelativePath());
        }
        return res;
    }

    public int getTotalChildren() {
        return totalChildren;
    }

    public void setTotalChildren(int totalChildren) {
        this.totalChildren = totalChildren;
    }

    void copy(CpFile trg) {
        trg.parent = parent;
        trg.setAbsolutePath(getAbsolutePath());
        trg.length = length;
        trg.totalChildren = totalChildren;
        trg.index = index;
    }

    public static class PgnItem extends CpFile {
        private String[] tagArray;
        private String fen;
        private String moveText = "";

        @Override
        public void setAbsolutePath(String absPath) {
            // do nothing
        }

        @Override
        public String getAbsolutePath() {
            return null;
        }

        public PgnItem(CpFile parent) {
            super(parent, COMMON_ITEM_NAME);
            if (parent == null) {
                this.parent = new CpFile.PgnFile(null, DUMMY_PGN_NAME);
            }
            initTags();
        }

        private PgnItem(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
            try {
                String parentPath = reader.readString();
                parent = fromPath(parentPath);
                parent.totalChildren = reader.read(32);
                initTags();
                int size = reader.read(6);
                for (int i = 0; i < size; ++i) {
                    String name = reader.readString();
                    String value = reader.readString();
                    setTag(name, value);
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                serializeBase(writer);
                writer.writeString(parent.getAbsolutePath());
                writer.write(parent.totalChildren, 32);
                List<Pair<String, String>>  tags = getTags();
                writer.write(tags.size(), 6);
                for (Pair<String, String>  tag : tags) {
                    writer.writeString(tag.first);
                    writer.writeString(tag.second);
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        private void initTags() {
            if (this.parent instanceof PgnFile) {
                PgnFile parent = (PgnFile)this.parent;
                tagArray = new String[parent.totalTags()];
            } else {
                tagArray = new String[Config.STR.size()];
            }
            Arrays.fill(tagArray, Config.TAG_UNKNOWN_VALUE);
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

        public String getTitle() {
            StringBuilder sb = new StringBuilder();
            if (index >= 0) {
                sb.append(String.format("%s. ", index + 1));
            }
            String sep = "";
            for (int i : Config.titleTagIndexes) {
                String value = null;
                if (i < this.tagArray.length) {
                    value = this.tagArray[i];
                }
                if (value == null) {
                    value = Config.TAG_UNKNOWN_VALUE;
                }
                sb.append(sep).append(value);
                sep = " - ";
            }
            return new String(sb);
        }

        @Override
        public String toString() {
            return getTitle();
        }

        public String toPgnString() {
            return toString(true, false);
        }

        public StringBuilder tagsToString(boolean cr2Space, boolean escapeTags) {
            return tagsToString(cr2Space, escapeTags, false);
        }

        public StringBuilder tagsToString(boolean cr2Space, boolean escapeTags, boolean skipEmptySTR) {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            PgnFile parent = (PgnFile)this.parent;
            for (int i = 0; i < tagArray.length; ++i) {
                String tValue = tagArray[i];
                if (tValue == null || tValue.isEmpty() || tValue.equals(Config.TAG_UNKNOWN_VALUE)) {
                    if (skipEmptySTR || i >= Config.STR.size()) {
                        continue;       // skip empty non-STR tags
                    }
                    tValue = Config.TAG_UNKNOWN_VALUE;
                }
                if (escapeTags) {
                    tValue = escapeTag(tValue);
                }
                String tName = parent.tagIndexes.get(i);
                if (escapeTags) {
                    tName = escapeTag(tName);
                }
                sb.append(sep).append("[").append(tName).append(" \"").append(tValue).append("\"]");
                sep = "\n";
            }
            if (this.fen != null) {
                sb.append(sep).append("[").append(Config.TAG_FEN).append(" \"").append(this.fen).append("\"]");
            }
            return sb;
        }

        String toString(boolean cr2Space, boolean escapeTags) {
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

        @Override
        public int getLength() {
            return this.length;
        }

        @Override
        public List<CpFile> getChildrenNames() {
            throw new RuntimeException("PgnFile.PgnItem cannot contain children");
        }

        public void save() throws Config.PGNException {
            Dir grandParent = parent.getRealParent();
            grandParent.saveGrandChild(this);
        }

        public void setTag(String tagName, String tagValue) {
            int i = ((PgnFile)this.parent).getTagIndex(tagName);
            if (i >= this.tagArray.length) {
                String[] newTagArray = new String[2 * this.tagArray.length];    // todo: check if it's not too wasteful
                System.arraycopy(this.tagArray, 0, newTagArray, 0, this.tagArray.length);
                for (int j = this.tagArray.length; j < newTagArray.length; ++j) {
                    newTagArray[j] = Config.TAG_UNKNOWN_VALUE;
                }
                this.tagArray = newTagArray;
            }
            this.tagArray[i] = tagValue;
        }

        public String getTag(String tagName) {
            int i = ((PgnFile)this.parent).getTagIndex(tagName);
            if (i >= this.tagArray.length) {
                return Config.TAG_UNKNOWN_VALUE;
            }
            return this.tagArray[i];    // check on null?
        }
        public List<Pair<String, String>> getTags() {
            List<Pair<String, String>> tags = new LinkedList<>();
            PgnFile parent = (PgnFile)this.parent;
            for (int i = 0; i < tagArray.length; ++i) {
                String tValue = tagArray[i];
                if (tValue == null || tValue.isEmpty() || tValue.equals(Config.TAG_UNKNOWN_VALUE)) {
                    if (i >= Config.STR.size()) {
                        continue;       // skip empty non-STR tags
                    }
                    tValue = Config.TAG_UNKNOWN_VALUE;
                }
                String tName = parent.tagIndexes.get(i);
                tags.add(new Pair<>(tName, tValue));
            }
            return tags;
        }

        public void setTags(List<Pair<String, String>> tags) {
            // remove old values
            Arrays.fill(tagArray, Config.TAG_UNKNOWN_VALUE);
            for (Pair<String, String> tag : tags) {
                setTag(tag.first, tag.second);
            }
        }

        @Override
        public int parentIndex(CpFile parent) throws Config.PGNException {
            CpFile thisParent = this.getParent();
            if (thisParent == null ||
                    !thisParent.getAbsolutePath().startsWith(parent.getAbsolutePath())) {
                return -1;
            }
            if (thisParent.getAbsolutePath().equals(parent.getAbsolutePath())) {
                return index;
            }
            return thisParent.parentIndex(parent);
        }

        @Override
        public void copy(CpFile trg) {
            super.copy(trg);
            if (trg instanceof PgnItem) {
                PgnItem pgnItem = (PgnItem)trg;
                pgnItem.tagArray = tagArray;
                pgnItem.fen = fen;
                pgnItem.moveText = moveText;
            }
        }

        @Override
        public void setParent(CpFile parent) {
            List<Pair<String, String>> tags = getTags();
            this.parent = parent;
            setTags(tags);
        }

        // if moveText == null -> delete item
        // if updIndex == -1 -> append item
        // returns number of all PgnItems
        int modifyItem(InputStream is, final OutputStream os) throws Config.PGNException {
            final int updIndex = this.index;
            try {
                final int[] count = {0};
                if (is == null) {
                    // brand new parent, no file yet
                    if (parent.totalChildren >= 0) {
                        ++parent.totalChildren;
                    } else {
                        parent.totalChildren = 1;
                    }
                    byte[] buf = this.toString(false, true).getBytes("UTF-8");
                    os.write(buf, 0, buf.length);
                    this.index = parent.totalChildren - 1;
                    ++count[0];
                } else {
                    parsePgnFile(parent, is, new EntryHandler() {
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

                    }, false);

                    if (updIndex == -1) {
                        byte[] buf = this.toString(false, true).getBytes("UTF-8");
                        os.write(buf, 0, buf.length);
                        ++count[0];
                        if (parent.getTotalChildren() == -1) {
                            parent.setTotalChildren(count[0]);
                        }
                        this.index = parent.totalChildren - 1;
                    }
                }
                return count[0];
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

    }

    public static class PgnFile extends CpFile {
        private final Map<String, Integer> tagNames = new HashMap<>(Config.STR.size() + 1);
        private final List<String> tagIndexes = new ArrayList<>(Config.STR.size() + 1);

        PgnFile(CpFile parent, String name) {
            super(parent, name);
            initPgn();
        }

        private PgnFile(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
            try {
                int totalTagIndexes = reader.read(6);
                for (int i = 0; i < totalTagIndexes; ++i) {
                    String tagName = reader.readString();
                    tagIndexes.add(tagName);
                    tagNames.put(tagName, i);
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }

        private void initPgn() {
            for (int i = 0; i < Config.STR.size(); ++i) {
                String tagName = Config.STR.get(i);
                tagIndexes.add(tagName);
                tagNames.put(tagName, i);
            }
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            serializeBase(writer);
            try {
                writer.write(tagIndexes.size(), 6);
                for (String tagName : tagIndexes) {
                    writer.writeString(tagName);
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }

        @Override
        protected CpFileType getType() {
            return CpFileType.Pgn;
        }

        public int getTagIndex(String tagName) {
            Integer index = tagNames.get(tagName);
            if (index == null) {
                index = tagNames.size();
                tagNames.put(tagName, index);
                tagIndexes.add(tagName);
            }
            return index;
        }

        String getTagName(int index) {
            return tagIndexes.get(index);
        }

        int totalTags() {
            return tagNames.size();
        }

        @Override
        public int getLength() {
            if (this.parent instanceof Zip) {
                return -1;
            }
            if (this.length == 0) {
                FilAx self = newFile(getAbsolutePath());
                this.length = (int) self.length();   // truncate
            }
            return this.length;
        }

        @Override
        public String getDisplayLength() {
            return CpFile.getDisplayLength(getLength());
        }

        // using pgnItem.parent and pgnItem.index find the rest
        // if not found (index is too large), return the last entry
        public PgnItem getPgnItem(final int searchIndex) throws Config.PGNException {
            final PgnItem lastEntry = new PgnItem(this);
            final int[] entryIndex = {-1};
            final int[] lastIndex = {-1};
            Dir grandParent = (Dir)this.getRealParent();
            long start0 = System.currentTimeMillis();
            grandParent.scrollGrandChildren(this, new EntryHandler() {
                @Override
                public boolean skip(int index) {
                    return searchIndex != index;
                }

                @Override
                public boolean getMovesText(int index) {
                    return index == searchIndex;
                }

                @Override
                public boolean handle(int index, CpFile.PgnItem entry) {
                    if (PgnFile.this.totalChildren <= index) {
                        PgnFile.this.totalChildren = index + 1;
                    }
                    entry.parent = PgnFile.this;
                    entry.copy(lastEntry);
                    if (index != searchIndex) {
                        return true;    // continue
                    }
                    entryIndex[0] = searchIndex;
                    return false;       // abort
                }
            });

            if (PgnFile.this.totalChildren < 0) {
                PgnFile.this.totalChildren = lastIndex[0];
            }
            long dur0 = System.currentTimeMillis() - start0;
            logger.debug(String.format("getPgnItem %s, msec=%d", parent.getName(), dur0));
            return lastEntry;
        }

        @Override
        public List<CpFile> getChildrenNames() {
            final List<CpFile> items = new LinkedList<>();
            if (!newFile(parent.getAbsolutePath()).exists()) {
                // this check needed for zipped PgnFile
                return items;
            }

            final int[] offset = {0};
            Dir grandParent = getRealParent();
            try {
                grandParent.scrollGrandChildren(this, new EntryHandler() {
                    @Override
                    public boolean handle(int index, CpFile.PgnItem entry) {
                        items.add(entry);
                        return true;
                    }

                    @Override
                    public boolean getMovesText(int index) {
                        return false;
                    }

                });
            } catch (OutOfMemoryError e) {
                throw e;
            } catch (Throwable e) {
                if (e.getCause() != null) {
                    e = e.getCause();
                }
                if (e instanceof OutOfMemoryError) {
                    throw (OutOfMemoryError)e;
                }
                e.printStackTrace();
            }
            totalChildren = items.size();
            return items;
        }

        public long lastModified() {
            FilAx self;
            if (parent instanceof Zip) {
                self = newFile(parent.getAbsolutePath());
            } else {
                self = newFile(getAbsolutePath());
            }
            return self.lastModified();
        }

        @Override
        public String getName() {
            if (parent instanceof Zip) {
                String absPath = getAbsolutePath();
                int i = absPath.indexOf(PARENT_ZIP);
                return absPath.substring(parent.getAbsolutePath().length() + 1);
            }
            return super.getName();
        }
    }

    public static class Dir extends CpFile {

        public Dir(CpFile parent, String name) {
            super(parent, name);
        }

        private Dir(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            serializeBase(writer);
        }

        @Override
        protected CpFileType getType() {
            return CpFileType.Dir;
        }

        @Override
        public List<CpFile> getChildrenNames() throws Config.PGNException {
            final List<CpFile> fileList = new ArrayList<>();
            scrollChildren(new EntryHandler() {
                @Override
                public boolean getMovesText(int index) {
                    return false;
                }

                @Override
                public boolean handle(CpFile pgnFile, InputStream is) {
                    fileList.add(pgnFile);
                    return true;
                }

                @Override
                public boolean handle(int index, CpFile.PgnItem item) {
                    throw new RuntimeException(String.format(DEBUG_TAG + "Invalid entry %s in %s", item.toString(), Dir.this.getAbsolutePath()));
                }

            });
            this.totalChildren = fileList.size();
            Collections.sort(fileList);
            return fileList;
        }

        void scrollChildren(final EntryHandler handler) throws Config.PGNException {
            final int[] index = {-1};
            FilAx self = newFile(getAbsolutePath());
            String[] list = self.listFiles();
            if (list == null) {
                return;
            }

            for (String name : list) {
                if (name.startsWith("."))
                    continue;   // skip

                FilAx filAx = filAxProvider.newFilAx(self, name);
                CpFile entry;
                if (filAx.isDirectory()) {
                    entry = new Dir(Dir.this, name);
                } else if (FilAx.isPgnOk(name)) {
                    entry = new PgnFile(Dir.this, name);
                } else if (FilAx.isZipOk(name)) {
                    entry = new Zip(Dir.this, name);
                } else {
                    continue;   // skip
                }

                if (entry instanceof PgnFile || entry instanceof Zip) {
                    entry.length = filAx.length();
                }

                ++index[0];
                try {
                    if (entry instanceof PgnFile) {
                        try (InputStream is = filAx.getInputStream()) {
                            entry.index = index[0];
                            handler.handle(entry, is);
                        } catch (IOException e) {
                            logger.error(filAx.toString(), e);
                        } catch (OutOfMemoryError e) {
                            throw e;
                        } catch (Throwable e) {
                            if (e.getCause() != null) {
                                e = e.getCause();
                            }
                            if (e instanceof OutOfMemoryError) {
                                throw (OutOfMemoryError)e;
                            } else {
                                throw new Config.PGNException(e);
                            }
                        }
                    } else {
                        entry.index = index[0];
                        handler.handle(entry, null);
                    }
                } catch (Config.PGNException e) {
                    throw new RuntimeException(e);
                }
            }
            this.totalChildren = ++index[0];
        }

        // scroll PgnFile child's children
        // this is a kludgy way to reuse the code for directory and zip
        public void scrollGrandChildren(final PgnFile gChild, final EntryHandler entryHandler) throws Config.PGNException {
            long start0 = System.currentTimeMillis();
            scrollChildren(new EntryHandler() {
                @Override
                public boolean getMovesText(int index) {
                    return true;
                }

                @Override
                public boolean handle(CpFile entry, InputStream is) throws Config.PGNException {
                    if (gChild.getAbsolutePath().equals(entry.getAbsolutePath())) {
                        gChild.length = entry.length;
                        parsePgnFile(gChild, is, entryHandler, true);
                        return false;       // abort
                    }
                    return true;            // continue
                }

                @Override
                public boolean handle(int index, CpFile.PgnItem item) {
                    throw new RuntimeException(String.format(DEBUG_TAG + "Invalid entry %s in %s", item.toString(), Dir.this.getAbsolutePath()));
                }
            });
            long dur0 = System.currentTimeMillis() - start0;
            logger.debug(String.format(Locale.getDefault(), "scrollGrandChildren %s, msec=%d", gChild.getRelativePath(), dur0));
        }

        void saveGrandChild(PgnItem pgnItem) throws Config.PGNException {
            FilAx dir = newFile(this.getAbsolutePath());
            if (!dir.exists()) {
                boolean ok = dir.mkdirs();
                if (!ok) {
                    throw new Config.PGNException(String.format("Cannot create %s directory", dir.getName()));
                }
            }
            FilAx oldFile = CpFile.newFile(pgnItem.getParent().getAbsolutePath());
            _saveGrandChild(pgnItem, oldFile);
        }

        void _saveGrandChild(PgnItem pgnItem, FilAx oldFile) throws Config.PGNException {
            String oldName = oldFile.getName();
            String tmpFileName = oldName + FilAx.EXT_TEMP;
            if (FilAx.isPgnOk(oldName)) {
                tmpFileName += FilAx.EXT_PGN;
            } else if (FilAx.isZipOk(oldName)) {
                tmpFileName += FilAx.EXT_ZIP;
            }
            FilAx tmpFile = filAxProvider.newFilAx(oldFile.getParent(), tmpFileName);
            int count = 0;
            try (OutputStream fos = tmpFile.getOutputStream()) {
                // create a new file with updated pgnItem
                count = saveGrandChild(pgnItem, fos);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }

            if (oldFile.exists()) {
                // delete old file then rename tmp to original name
                if (!oldFile.delete()) {
                    throw new Config.PGNException("Cannot delete " +  oldName);
                }
            }

            boolean res;
            boolean delete = this.totalChildren == 0 && pgnItem.getParent().totalChildren == 0 && pgnItem.moveText == null;
            if (delete) {
                res = tmpFile.delete();
                logger.debug(String.format("deleting %s, %s", oldName, res));
                CpFile cpFile = this;
                FilAx dir = newFile(cpFile.getAbsolutePath());
                String[] children;
                while ((children = dir.listFiles()) == null || children.length == 0) {
                    if (cpFile == rootDir) {
                        break;
                    }
                    dir.delete();
                    cpFile = cpFile.getParent();
                    dir = newFile(cpFile.getAbsolutePath());
                }
                logger.debug("deleted all empty directories");
            } else {
                res = tmpFile.renameTo(oldName);
                logger.debug(String.format("renaming %s, %s", oldName, res));
            }
        }

        // return number of entries
        int saveGrandChild(PgnItem pgnItem, OutputStream fos) throws Config.PGNException {
            PgnFile parent = (PgnFile)pgnItem.getParent();
            int count = 0;
            FilAx f = filAxProvider.newFilAx(parent.getAbsolutePath());
            try (InputStream fis = f.getInputStream()) {
                count += pgnItem.modifyItem(fis, fos);
                if (parent.totalChildren == 0) {
                    if (this.totalChildren > 0) {
                        --this.totalChildren;
                    }
                }
            } catch (FileNotFoundException e) {
                count += pgnItem.modifyItem(null, fos);
            } catch (IOException e) {
                logger.debug(parent.getAbsolutePath(), e);
            }
            return count;
        }
    }

    public static class Zip extends Dir {

        Zip(CpFile parent, String name) {
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
                FilAx self = newFile(getAbsolutePath());
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
            FilAx f = newFile(getAbsolutePath());
            if (!f.exists()) {
                return;
            }
            this.length = (int)f.length();
//* have to use it in Android in spite of the Java bug
            try (ZipInputStream zipInputStream = new ZipInputStream(f.getInputStream())) {
                ZipEntry ze;
                while ((ze = zipInputStream.getNextEntry()) != null) {
                    if (ze.isDirectory() || !FilAx.isPgnOk(ze.getName())) {
                        continue;
                    }
                    PgnFile pgnFile = new PgnFile(Zip.this, ze.getName());
                    pgnFile.length = (int) ze.getSize();    // Java bug, always -1
                    pgnFile.index = index;
                    if (!zipEntryHandler.handle(pgnFile, zipInputStream)) {
                        break;
                    }
                }
            } catch (Throwable t) {
                throw new Config.PGNException(t);
            }
        }

        @Override
        void saveGrandChild(PgnItem pgnItem) throws Config.PGNException {
            FilAx dir = newFile(parent.getAbsolutePath());
            boolean res = dir.mkdirs();
            _saveGrandChild(pgnItem, newFile(this.getAbsolutePath()));
        }

        /**
         * @param pgnItem to replace, set moveText = null to delete; set pgnItem.index = -1 to add a new PgnItem
         * @param fos  resulting OutputStream
         * @throws Config.PGNException
         */
        @Override
        int saveGrandChild(final PgnItem pgnItem, OutputStream fos) throws Config.PGNException {
            final PgnFile pgnFile = (PgnFile)pgnItem.getParent();
            final int updIndex = pgnItem.getIndex();
            final byte[] data = new byte[Config.MY_BUF_SIZE];
            final boolean[] found = {false};
            final int[] count = {0};
            try (final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {
                scrollChildren(new EntryHandler() {
                    @Override
                    public boolean handle(CpFile entry, InputStream is) throws Config.PGNException {
                        try {
                            if (pgnFile.getAbsolutePath().equals(entry.getAbsolutePath())) {
                                if (pgnItem.moveText == null && updIndex == 0 && pgnFile.totalChildren == 1) {
                                    // remove the last item
                                    pgnItem.modifyItem(is, null);
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
                            if (pgnFile.getAbsolutePath().equals(entry.getAbsolutePath())) {
                                // modified PgnFile
                                count[0] += pgnItem.modifyItem(is, zos);
                                found[0] = true;
                            } else {
                                // blind copy
                                int _count;
                                while ((_count = is.read(data, 0, Config.MY_BUF_SIZE)) != -1) {
                                    zos.write(data, 0, _count);
                                }
                            }
                            return true;
                        } catch (IOException e) {
                            throw new Config.PGNException(e);
                        }
                    }

                    @Override
                    public boolean handle(int index, CpFile.PgnItem entry) {
                        throw new RuntimeException(String.format(DEBUG_TAG + "Invalid entry %s, %s in %s", index, pgnItem.toString(), Zip.this.getAbsolutePath()));
                    }

                    @Override
                    public boolean getMovesText(int index) {
                        return false;
                    }
                });
                if (!found[0]) {
                    String relativePath = pgnFile.getRelativePath();
                    ZipEntry zeOut = new ZipEntry(relativePath);
                    zos.putNextEntry(zeOut);
                    count[0] += pgnItem.modifyItem(null, zos);
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
            return count[0];
        }

    }

    public static class ProgressNotifier {
        private ProgressObserver progressObserver;
        private int totalLength, previousProgress, previousOffset;

        public ProgressNotifier() {
            totalLength = 0;
            if (DEBUG) {
                System.out.println(String.format("New ProgressNotifier %s", this));
            }
        }

        public void setProgressObserver(ProgressObserver progressObserver) {
            this.progressObserver = progressObserver;
            totalLength = 0;
        }

        public void setTotalLength(int totalLength) {
            this.totalLength = totalLength;
            previousOffset = 0;
        }

        public void addOffset(int increment) {
            setOffset(previousOffset + increment);
        }

        public void setOffset(int offset) {
            if (progressObserver != null) {
                int progress = getProgress(offset, totalLength);
                if (previousProgress != progress) {
                    progressObserver.setProgress(progress);
                    previousProgress = progress;
                }
                previousOffset = offset;
            }
        }

        private int getProgress(int offset, int totalLength) {
            if (totalLength == 0) {
                return 0;
            }
            int res = (int)((long)offset * 100 / (long)totalLength);
            if (res > 100) {
                return 100;
            }
            return res;
        }
    }

    public interface ProgressObserver {
        void setProgress(int progress);
    }

    public interface EntryHandler {
        boolean getMovesText(int index);
        default boolean skip(int index) {               // return true to skip
            return false;
        }
        boolean handle(int index, CpFile.PgnItem entry) throws Config.PGNException;         // return false to break iteration
        default boolean handle(CpFile entry, InputStream is) throws Config.PGNException {   // return false to break iteration
            return true;
        }
    }

/* should we do it to save memory?
    public static class CpName {
        final String name;
        final int length;

        public CpName(String name) {
            this.name = name;
            this.length = 0;
        }

        public CpName(String name, int length) {
            this.name = name;
            this.length = length;
        }

        String getName() {
            return name;
        }

        int getLength() {
            return length;
        }
    }
*/
}
