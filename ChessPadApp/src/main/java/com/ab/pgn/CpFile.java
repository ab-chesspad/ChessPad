package com.ab.pgn;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Concrete subclasses: CpFile, Pgn, Zip, Dir
 * List directory/zip/pgn file, extract individual game (Item) and add/update/delete game
 *
 * Created by Alexander Bootman on 7/30/16.
 */
public abstract class CpFile implements Comparable<CpFile> {
    private static final boolean DEBUG = false;

    public static final String
        EXT_TEMP = ".tmp",
        COMMON_ITEM_NAME = "item",
        TAG_START = "[",
        TAG_END = "\"]",
        SLASH = File.separator,
        EXT_PGN = ".pgn",
        EXT_ZIP = ".zip",
        WRONG_PGN = SLASH + EXT_PGN,
        WRONG_ZIP = SLASH + EXT_ZIP,
        PARENT_ZIP = EXT_ZIP + SLASH ,
        TAG_NAME_VALUE_SEP = " \"",
        DUMMY_PGN_NAME = "dummy" + CpFile.EXT_PGN,
        dummy_prv_str = null;

    private final static PgnLogger logger = PgnLogger.getLogger(CpFile.class);
    private static int i = -1;
    public enum PgnFileType {
        Item(++i),
        Pgn(++i),
        Dir(++i),
        Zip(++i),
        ;

        private static final PgnFileType[] values = PgnFileType.values();
        private final int value;

        PgnFileType(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

    private static File root = new File("/");
    private static Dir rootCpFile = new Dir(null, root.getAbsolutePath());
    CpFile parent;
    String absPath;
    int index = -1;
    int length = 0;
    int totalChildren = -1;
    transient int offset = 0;
    CpFile() {}

//    private int[] debugData = new int[1000];    // to imitate OOM

    CpFile(CpFile parent, String name) {
        if (parent == null) {
            this.absPath = name;    // init root
        } else {
            this.parent = parent;
            this.absPath = concat(parent.getAbsolutePath(), name);
        }
    }

    private CpFile(DataInputStream is) throws Config.PGNException {
        if(!Config.USE_BIT_STREAMS) {
            try {
                this.length = is.readInt();
                this.offset = is.readInt();
                this.index = is.readInt();
                this.totalChildren = is.readInt();
                getParentFromPath(Util.readString(is));  // absolute path
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

    private CpFile(BitStream.Reader reader) throws Config.PGNException {
        try {
            this.length = reader.read(32);
            this.offset = reader.read(32);
            this.index = reader.read(24);
            if (index == 0x0ffffff) {
                index = -1;
            }
            this.totalChildren = reader.read(32);
            getParentFromPath(reader.readString());  // absolute path
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private static File getRoot() {
        return root;
    }

    public static String getRootPath() {
        return root.getAbsolutePath();
    }

    static boolean isPgnOk(String path) {
        path = path.toLowerCase();
        return path.endsWith(EXT_PGN) && !path.endsWith(WRONG_PGN);
    }

    private static boolean isZipOk(String path) {
        path = path.toLowerCase();
        return path.endsWith(EXT_ZIP) && !path.endsWith(WRONG_ZIP);
    }

    private static String concat(String parent, String name) {
        if(parent.endsWith(SLASH)) {
            return parent + name;
        }
        return parent + SLASH + name;
    }

    private static String getDisplayLength(int length) {
        char suffix = 'B';
        double len = length;
        if(length >= 1000000) {
            suffix = 'M';
            len /= 1000000;
        } else if(length >= 1000) {
            suffix = 'K';
            len /= 1000;
        }
        String format = "%.0f %c";
        if(suffix != 'B' && len < 100) {
            format = "%.2f %c";
        }
        return String.format(Locale.getDefault(), format, len, suffix);
    }

    private static int compareNames(String name1, String name2) {
        Pattern p = Pattern.compile("^(\\d+)");
        Matcher m1 = p.matcher(name1);
        Matcher m2 = p.matcher(name2);
        int res;
        if(m1.find() && m2.find()) {
            String g1 = m1.group(1);
            String g2 = m2.group(1);
            res = g1.length() - g2.length();
            if(res != 0) {
                return res;
            }
        }
        res = name1.compareTo(name2);
        return res;
    }

    static CpFile unserialize(DataInputStream is) throws Config.PGNException {
        try {
            PgnFileType pgnFileType = PgnFileType.values[is.read()];    // single byte
            CpFile unserialized = null;
            switch (pgnFileType) {
                case Item:
                    unserialized = new Item(is);
                    break;

                case Pgn:
                    unserialized = new Pgn(is);
                    break;

                case Zip:
                    unserialized = new Zip(is);
                    break;

                case Dir:
                    unserialized = new Dir(is);
                    break;
            }
            return unserialized;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }

    }

    public static CpFile unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            PgnFileType pgnFileType = PgnFileType.values[reader.read(3)];
            CpFile unserialized = null;
            switch (pgnFileType) {
                case Item:
                    unserialized = new Item(reader);
                    break;

                case Pgn:
                    unserialized = new Pgn(reader);
                    break;

                case Zip:
                    unserialized = new Zip(reader);
                    break;

                case Dir:
                    unserialized = new Dir(reader);
                    break;
            }
            return unserialized;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static CpFile fromFile(File file) {
        String path = file.getAbsolutePath();
        if(!path.startsWith(root.getAbsolutePath())) {
            throw new RuntimeException(String.format("Invalid file %s, must reside within root directory %s", path, root.getAbsolutePath()));
        }
        CpFile cpFile = null;
        if (file.isDirectory()) {
            cpFile = new Dir(path);
        } else if (CpFile.isPgnOk(path)) {
            cpFile = new Pgn(path);
        } else if (CpFile.isZipOk(path)) {
            cpFile = new Zip(path);
        } else if (!file.exists()) {
            File parentFile = file.getParentFile();
            CpFile parent = fromFile(parentFile);
            if(parent instanceof Pgn) {
                cpFile = new Item(parent);
            } else {
                cpFile = new Dir(path);
            }
        }
        return cpFile;
    }

    public static void parsePgnFiles(CpFile parent, BufferedReader br, EntryHandler entryHandler) throws Config.PGNException {
        parsePgnFiles(parent, br, entryHandler, true);
    }

    // when parseItems == false return raw pgn item text in item.moveText
    private static synchronized void parsePgnFiles(CpFile parent, BufferedReader br, EntryHandler entryHandler, boolean parseItems) throws Config.PGNException {
        if(br == null) {
            return; // crashes otherwise
        }

        int index = -1;
        int totalLength = 0;
        if(parent == null) {
            // create dummy parent to store tab names
            parent = new Pgn(DUMMY_PGN_NAME);
        } else {
            totalLength = parent.getLength();
        }
        Item item = new Item(parent);
        item.index = ++index;
        StringBuilder sb = new StringBuilder(Config.STRING_BUF_SIZE);
        boolean inText = false;
        String line;
        int fileOffset = 0, lineCount = 0;

        try {
            while ((line = br.readLine()) != null) {
                ++lineCount;
                fileOffset += line.length() + 1;
                line = line.trim();
                if(DEBUG) {
                    System.out.println(String.format("line %s, fileOffset %s", lineCount, fileOffset));
                    if (totalLength > 0 && fileOffset > totalLength) {
                        System.out.println(String.format("fileOffset %s > %s, \"%s\"", fileOffset, totalLength, line));
                    }
                }
                if(entryHandler.addOffset(line.length() + 1, totalLength)) {
                    break;
                }
                if (line.startsWith(TAG_START) && line.endsWith(TAG_END)) {
                    if (inText) {
                        item.moveText = new String(sb);
                        if (!entryHandler.handle(item, null)) {
                            return;
                        }
                        sb.delete(0, sb.length());
                        if (parseItems) {
                            item = new Item(parent);
                        }
                        item.index = ++index;
                        inText = false;
                    }
                    if (parseItems) {
                        if(!entryHandler.skip(item)) {
                            parseTag(item, line);
                        }
                    } else {
                        sb.append(line).append("\n");
                    }
                } else {
                    inText = true;
                    if (entryHandler.getMoveText(item)) {
                        sb.append(line).append("\n");
                    }
                }
            }
            item.moveText = new String(sb);
            entryHandler.handle(item, null);
        } catch (Throwable e) {
            logger.error(e);
            throw new Config.PGNException(e);
        }
    }

    static void parseTag(Item item, String line) {
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
            if(token.equals(DELIMITERS)) {
                if(!delimFound) {
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

    static String getTitle(List<Pair<String, String>> tags) {
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (String h : Config.titleTags) {
            String v = null;
            for(Pair<String, String> lt : tags) {
                if(h.equals(lt.first)) {
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

    // using item.parent and item.index find the rest
    // if not found (index is too large), return the last entry
    public static void getPgnFile(final Item item, ProgressObserver progressObserver) throws Config.PGNException {
        if(item.index == -1  || item.getParent() == null || !(item.getParent() instanceof Pgn)) {
            throw new Config.PGNException(String.format("%s - invalid item type or invalid data", item.toString()));
        }
        final Pgn parent = (Pgn)item.getParent();
        if (!Dir.class.isAssignableFrom(parent.getParent().getClass())) {
            throw new Config.PGNException(String.format("%s, %s - invalid grandparent type", parent.getParent().getName(), item.toString()));
        }
        final ProgressNotifier progressNotifier = new ProgressNotifier(progressObserver);
        final CpFile.Item lastEntry = new Item(parent);
        parent.offset = 0;
        Dir grandParent = (Dir)parent.getParent();
        grandParent.walkThroughGrandChildren(parent, new EntryHandler() {
            @Override
            public boolean handle(CpFile entry, BufferedReader br) {
                entry.copy(lastEntry);
                if(entry.index != item.index) {
                    return true;    // continue
                }
                entry.copy(item);
                return false;       // abort
            }

            @Override
            public boolean getMoveText(CpFile entry) {
                return entry.index == item.index;
            }

            public boolean addOffset(int length, int totalLength) {
                parent.offset += length;
                return progressNotifier.setOffset(parent.offset, totalLength);
            }

            @Override
            public boolean skip(CpFile entry) {
                return entry.index != item.index;
            }
        });
        // in case item is not found, store the last entry
        if(item.index != lastEntry.index) {
            lastEntry.copy(item);
        }
    }

    public static void copy(File src, File dst) throws IOException {
        try (FileInputStream inStream = new FileInputStream(src);
                FileOutputStream outStream = new FileOutputStream(dst)) {
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

    public static Dir getRootDir() {
        return new Dir(root.getAbsolutePath());
    }

    // relative to rootDir
    public static String getRelativePath(CpFile cpFile) {
        if(cpFile == null) {
            return "";          // sanity check
        }
        String rootPath = root.getAbsolutePath();
        String itemPath = cpFile.getAbsolutePath();
        if(itemPath.startsWith(rootPath)) {
            itemPath = itemPath.substring(rootPath.length());
            if(itemPath.startsWith("/")) {
                itemPath = itemPath.substring(1);
            }
        }
        return itemPath;
    }

    // returns resulting number of entries
    private static int modifyItem(final Item item, BufferedReader bufferedReader, final OutputStream os, final ProgressNotifier progressNotifier) throws Config.PGNException {
        try {
            final int[] count = {0};
            final int[] offset = {0};
            if (bufferedReader == null) {
                item.index = -1;
            } else {
                parsePgnFiles(item.getParent(), bufferedReader, new EntryHandler() {
                    @Override
                    public boolean handle(CpFile entry, BufferedReader bufferedReader) throws Config.PGNException {
                        try {
                            Item src = (Item) entry;
                            if (item.index == src.index) {
                                if(item.moveText == null) {
                                    return true;        // skip item - delete it
                                }
                                src.moveText = item.toString(false, true);    // the whole item
                            }
                            if (src.moveText != null) {
                                String moveText = src.moveText + "\n";
                                byte[] buf = moveText.getBytes("UTF-8");
                                os.write(buf, 0, buf.length);
                                ++count[0];
                            }
                            return true;
                        } catch (IOException e) {
                            throw new Config.PGNException(e);
                        }
                    }

                    @Override
                    public boolean getMoveText(CpFile entry) {
                        return true;
                    }

                    @Override
                    public boolean addOffset(int length, int totalLength) {
                        offset[0] += length;
                        if (progressNotifier != null) {
                            return progressNotifier.setOffset(offset[0], totalLength);
                        }
                        return false;
                    }

                    @Override
                    public boolean skip(CpFile entry) {
                        return item.index != ((Item)entry).index;
                    }
                }, false);
            }

            if (item.index == -1) {
                byte[] buf = item.toString(false, true).getBytes("UTF-8");
                os.write(buf, 0, buf.length);
                item.setIndex(count[0]);
                ++count[0];
            }
            return count[0];
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private static void serialize(DataOutputStream os, String[] tags) throws Config.PGNException {
        if(!Config.USE_BIT_STREAMS) {
            try {
                if (tags == null) {
                    os.write(0);
                    return;
                }
                os.write(tags.length);
                for (String tag : tags) {
                    Util.writeString(os, tag);
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

    private static String[] unserializeTags(DataInputStream is) throws Config.PGNException {
        if(Config.USE_BIT_STREAMS) {
            return null;
        } else {
            try {
                int totalTags = is.read();
                if (totalTags == 0) {
                    return null;
                }
                String[] tags = new String[totalTags];
                for (int i = 0; i < totalTags; ++i) {
                    tags[i] = Util.readString(is);
                }
                return tags;
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

/*
    private static void serialize(BitStream.Writer writer, String[] tags) throws Config.PGNException {
        try {
            if (tags == null) {
                writer.write(0, 8);
                return;
            }
            writer.write(tags.length, 8);
            for (String tag : tags) {
                writer.writeString(tag);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }
*/

    public static List<Pair<String, String>> unserializeTagList(DataInputStream is) throws Config.PGNException {
        if(Config.USE_BIT_STREAMS) {
            return null;
        } else {
            try {
                int totalTags = is.read();
                if (totalTags == 0) {
                    return null;
                }
                List<Pair<String, String>> tags = new LinkedList<>();
                for (int i = 0; i < totalTags; ++i) {
                    String label = Util.readString(is);
                    String value = Util.readString(is);
                    tags.add(new Pair<>(label, value));
                }
                return tags;
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

/*
    private static String[] unserializeTags(BitStream.Reader reader) throws Config.PGNException {
        try {
            int totalTags = reader.read(8);
            if (totalTags == 0) {
                return null;
            }
            String[] tags = new String[totalTags];
            for (int i = 0; i < totalTags; ++i) {
                tags[i] = reader.readString();
            }
            return tags;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }
*/

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
                if(label == null) {
                    label = "";         // should never happen
                }
                String value = reader.readString();
                if(value == null) {
                    value = "";         // for editTags
                }
                tags.add(new Pair<>(label, value));
            }
            return tags;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public abstract List<CpFile> getChildrenNames(ProgressObserver progressObserver) throws Config.PGNException;

    void getParentFromPath(String name) {
        String rootPath = getRootPath();
        if(name.startsWith(rootPath) || name.startsWith(SLASH)) {
            this.absPath = name;
            name = name.substring(rootPath.length());
            if(name.startsWith(File.separator)) {
                name = name.substring(File.separator.length());
            }
        } else {
            this.absPath = concat(rootPath, name);
        }
        String[] parts = name.split(File.separator);
        String path = rootPath;
        CpFile parent = rootCpFile;
        for(int i = 0; i < parts.length - 1; ++i) {
            String part = parts[i];
            path += File.separator + part;
            File file = new File(path);
            if (file.exists()) {
                if (file.isDirectory()) {
                    parent = new Dir(parent, part);
                } else if (isPgnOk(path)) {
                    parent = new Pgn(parent, part);
                } else if (isZipOk(path)) {
                    parent = new Zip(parent, part);
                    break;
                } else {
                    parent = null;   // exception?
                }
            } else {
                if (this instanceof Item) {
                    parent = new Pgn(parent, part);
                } else {
                    parent = new Dir(parent, part);     // verify
                }
            }
        }
        this.parent = parent;
    }

    boolean isRoot() {
        return absPath.equals(root.getAbsolutePath());
    }

    public static void setRoot(File root) {
        CpFile.root = root;
        rootCpFile = new Dir(null, root.getAbsolutePath());
    }

    public int getLength() throws Config.PGNException {
        if(this.length == 0) {
            List<CpFile> children;
            children = getChildrenNames(null);
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
        if(len > 0) {
            return "" + len;
        }
        return "";
    }

    public int getOffset() {
        return this.offset;
    }

    public String getName() {
        int slash = absPath.lastIndexOf(SLASH);
        if(slash < 0) {
            return absPath;
        }
        return absPath.substring(slash + 1);
    }

    public String getAbsolutePath() {
        return this.absPath;
    }

    public CpFile getParent() {
        CpFile parent = this.parent;
        if(parent == null) {
            parent = getRootDir();
        }
        return parent;
    }

    void setParent(CpFile parent) {
        this.parent = parent;
        String name = getName();
        this.absPath = concat(parent.getAbsolutePath(), name);
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

    public boolean equals(String name) {
        return getName().equals(name);
    }

    public boolean equals(CpFile item) {
        if (item == null)
            return false;
        return getAbsolutePath().equals(item.getAbsolutePath());
    }

    public int parentIndex(CpFile parent) throws Config.PGNException {
        String myPath = getAbsolutePath();
        if(!myPath.startsWith(parent.getAbsolutePath())) {
            return -1;
        }

        String parentPath = parent.getAbsolutePath();
        String relativePath = myPath.substring(parentPath.length() + 1);
        if(parent.getType() == PgnFileType.Zip) {
            // SicilianGrandPrix.pgn/item
            String[] parts = relativePath.split("/");
            for(String part : parts) {
                if(CpFile.isPgnOk(part)) {
                    relativePath = part;
                    break;
                }
            }
        }
        List<CpFile> siblings = parent.getChildrenNames(null);
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

    void serializeBase(DataOutputStream os) throws Config.PGNException {
        if(!Config.USE_BIT_STREAMS) {
            try {
                os.write(getType().getValue()); // single byte
                os.writeInt(length);
                os.writeInt(offset);
                os.writeInt(index);
                os.writeInt(totalChildren);
                Util.writeString(os, absPath);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

    void serializeBase(BitStream.Writer writer) throws Config.PGNException {
        try {
            writer.write(getType().getValue(), 3);
            writer.write(length, 32);
            writer.write(offset, 32);
            writer.write(index, 24);
            writer.write(totalChildren, 32);
            writer.writeString(absPath);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public abstract void serialize(DataOutputStream os) throws Config.PGNException;

    public abstract void serialize(BitStream.Writer writer) throws Config.PGNException;

    protected abstract PgnFileType getType();

    @Override
    public int compareTo(CpFile that) {
        int res;
        if(this.getClass().equals(Dir.class)) {
            if(!that.getClass().equals(Dir.class)) {
                res = -1;
            } else {
                res = compareNames(this.getName(), that.getName());
            }
        } else if(this.getClass().equals(Zip.class)) {
            if((that.getClass().equals(Dir.class))) {
                res = 1;
            } else if(!that.getClass().equals(Zip.class)) {
                res = -1;
            } else {
                res = compareNames(this.getName(), that.getName());
            }
        } else if(this.getClass().equals(Pgn.class)) {
            if(that instanceof Dir) {
                res = 1;
            } else if(!that.getClass().equals(Pgn.class)) {
                res = -1;
            } else {
                res = compareNames(this.getName(), that.getName());
            }
        } else {
            if(!(that instanceof Item)) {
                res = 1;
            } else {
                res = 0;
            }
        }
        return res;
    }

    public int getTotalChildren() {
        return totalChildren;
    }

    public void setTotalChildren(int totalChildren) {
        this.totalChildren = totalChildren;
    }

    public long lastModified() {
        File file = new File(absPath);
        return file.lastModified();
    }

    void copy(CpFile trg) {
        trg.parent = parent;
        trg.absPath = absPath;
        trg.length = length;
        trg.totalChildren = totalChildren;
        trg.index = index;
    }

    public interface ProgressObserver {
        boolean setProgress(int progress);  // return true to abort
    }

    public interface EntryHandler {
        boolean handle(CpFile entry, BufferedReader bufferedReader) throws Config.PGNException;    // return false to break iteration
        boolean getMoveText(CpFile entry);
        boolean addOffset(int length, int totalLength);     // return true to abort
        boolean skip(CpFile entry);                         // return true to skip
    }

    public static class Item extends CpFile {
        private String[] tagArray;
        private String fen;
        private String moveText = "";

        Item(String name) {
            String parentName = DUMMY_PGN_NAME;
            int i = name.lastIndexOf(File.separator);
            if( i > 0) {
                parentName = name.substring(0, i);
                name = name.substring(i + File.separator.length());
            }
            parent = new Pgn(parentName);
            absPath = parent.absPath + SLASH + name;
            initTags();
        }

        public Item(CpFile parent) {
            super(parent, COMMON_ITEM_NAME);
            initTags();
        }

        private Item(DataInputStream is) throws Config.PGNException {
            super(is);
            if(!Config.USE_BIT_STREAMS) {
                this.tagArray = unserializeTags(is);
            }
        }

        private Item(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
            initTags();
            try {
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

        private void initTags() {
            if(this.parent instanceof Pgn) {
                Pgn parent = (Pgn)this.parent;
                tagArray = new String[parent.totalTags()];
            } else {
                tagArray = new String[Config.STR.size()];
            }
            Arrays.fill(tagArray, Config.TAG_UNKNOWN_VALUE);
        }

        @Override
        public void serialize(DataOutputStream os) throws Config.PGNException {
            if(!Config.USE_BIT_STREAMS) {
                serializeBase(os);
                CpFile.serialize(os, this.tagArray);
            }
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            serializeBase(writer);
            List<Pair<String, String>>  tags = getTags();
            try {
                writer.write(tags.size(), 6);
                for (Pair<String, String>  tag : tags) {
                    writer.writeString(tag.first);
                    writer.writeString(tag.second);
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        @Override
        protected PgnFileType getType() {
            return PgnFileType.Item;
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

        String getTitle() {
            StringBuilder sb = new StringBuilder();
            if(index >= 0) {
                sb.append(String.format("%s. ", index + 1));
            }
            String sep = "";
            for( int i : Config.titleTagIndexes) {
                String value = null;
                if(i < this.tagArray.length) {
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
            StringBuilder sb = new StringBuilder();
            String sep = "";
            Pgn parent = (Pgn)this.parent;
            for(int i = 0; i < tagArray.length; ++i) {
                String tValue = tagArray[i];
                if (tValue == null || tValue.isEmpty() || tValue.equals(Config.TAG_UNKNOWN_VALUE)) {
                    if(i >= Config.STR.size()) {
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
            if(this.fen != null) {
                sb.append(sep).append("[").append(Config.TAG_FEN).append(" \"").append(this.fen).append("\"]");
            }
            return sb;
        }

        String toString(boolean cr2Space, boolean escapeTags) {
            StringBuilder sb = tagsToString(cr2Space, escapeTags);
            if(moveText != null && !moveText.isEmpty()) {
                sb.append("\n");
                if(cr2Space) {
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
        public List<CpFile> getChildrenNames(ProgressObserver progressObserver) {
            throw new RuntimeException("Pgn.Item cannot contain children");
        }

        @Override
        public long lastModified() {
            return getParent().lastModified();
        }

        void save(ProgressObserver progressObserver) throws Config.PGNException {
            final CpFile parent = this.getParent();
            if (!(parent instanceof Pgn)) {
                throw new Config.PGNException(String.format("%s - invalid item type or invalid data", this.toString()));
            }

            Dir grandParent = (Dir) parent.getParent();
            if(grandParent == null) {
                grandParent = new Dir(parent.getAbsolutePath());
            }
            grandParent.saveGrandChild(this, new ProgressNotifier(progressObserver));
        }

        public void setTag(String tagName, String tagValue) {
            int i = ((Pgn)this.parent).getTagIndex(tagName);
            if(i >= this.tagArray.length) {
                String[] newTagArray = new String[2 * this.tagArray.length];    // todo: check if it's not too wasteful
                System.arraycopy(this.tagArray, 0, newTagArray, 0, this.tagArray.length);
                for(int j = this.tagArray.length; j < newTagArray.length; ++j) {
                    newTagArray[j] = Config.TAG_UNKNOWN_VALUE;
                }
                this.tagArray = newTagArray;
            }
            this.tagArray[i] = tagValue;
        }

        public String getTag(String tagName) {
            int i = ((Pgn)this.parent).getTagIndex(tagName);
            if(i >= this.tagArray.length) {
                return Config.TAG_UNKNOWN_VALUE;
            }
            return this.tagArray[i];    // check on null?
        }
        public List<Pair<String, String>> getTags() {
            List<Pair<String, String>> tags = new LinkedList<>();
            Pgn parent = (Pgn)this.parent;
            for(int i = 0; i < tagArray.length; ++i) {
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
            for(Pair<String, String> tag : tags) {
                setTag(tag.first, tag.second);
            }
        }

        @Override
        public int parentIndex(CpFile parent) throws Config.PGNException {
            CpFile thisParent = this.getParent();
            if(thisParent == null ||
                    !thisParent.getAbsolutePath().startsWith(parent.getAbsolutePath())) {
                return -1;
            }
            if(thisParent.getAbsolutePath().equals(parent.getAbsolutePath())) {
                return index;
            }
            return super.parentIndex(parent);
        }

        @Override
        public void copy(CpFile trg) {
            super.copy(trg);
            if(trg instanceof Item) {
                Item item = (Item)trg;
                item.tagArray = tagArray;
                item.fen = fen;
                item.moveText = moveText;
            }
        }

        @Override
        public void setParent(CpFile parent) {
            List<Pair<String, String>> tags = getTags();
            super.setParent(parent);
            setTags(tags);
        }
    }

    public static class Pgn extends CpFile {
        private final Map<String, Integer> tagNames = new HashMap<>(Config.STR.size() + 1);
        private final List<String> tagIndexes = new ArrayList<>(Config.STR.size() + 1);

        public Pgn(String name) {
            getParentFromPath(name);
            initPgn();
        }

        Pgn(CpFile parent, String name) {
            super(parent, name);
            initPgn();
        }

        private Pgn(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
            try {
                int totalTagIndexes = reader.read(6);
                for(int i = 0; i < totalTagIndexes; ++i) {
                    String tagName = reader.readString();
                    tagIndexes.add(tagName);
                    tagNames.put(tagName, i);
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }

        private Pgn(DataInputStream is) throws Config.PGNException {
            super(is);
            if (!Config.USE_BIT_STREAMS) {
                initPgn();
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
        public void serialize(DataOutputStream os) throws Config.PGNException {
            if (!Config.USE_BIT_STREAMS) {
                serializeBase(os);
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
        protected PgnFileType getType() {
            return PgnFileType.Pgn;
        }

        int getTagIndex(String tagName) {
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
            if (this.length == 0) {
                File self = new File(absPath);
                this.length = (int) self.length();   // truncate
            }
            return this.length;
        }

        @Override
        public String getDisplayLength() {
            return CpFile.getDisplayLength(getLength());
        }

        @Override
        public List<CpFile> getChildrenNames(ProgressObserver progressObserver) {
            final int[] offset = {0};
            final ProgressNotifier progressNotifier = new ProgressNotifier(progressObserver);
            final List<CpFile> items = new LinkedList<>();
            try {
                ((Dir) getParent()).walkThroughGrandChildren(this, new EntryHandler() {
                    @Override
                    public boolean handle(CpFile entry, BufferedReader br) {
                        entry.offset = offset[0];
                        items.add(entry);
                        return true;
                    }

                    @Override
                    public boolean getMoveText(CpFile entry) {
                        return false;
                    }

                    @Override
                    public boolean addOffset(int length, int totalLength) {
                        offset[0] += length;
                        if (offset[0] > totalLength) {
                            System.out.println(String.format("%s > %s", offset[0], totalLength));
                        }
                        return progressNotifier.setOffset(offset[0], totalLength);
                    }

                    @Override
                    public boolean skip(CpFile entry) {
                        return false;
                    }
                });
            } catch (Throwable e) {
//                logger.error(e.getMessage(), e);      // log crashes on OOM
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
                    return ((Zip) getParent()).getChildTS(this);
                } else {
                    return super.lastModified();
                }
            } catch (Config.PGNException e) {
                logger.error(e.getMessage(), e);
            }
            return 0;
        }

        @Override
        public String getName() {
            if(parent instanceof Zip) {
                int i = absPath.indexOf(PARENT_ZIP);
                return absPath.substring(parent.absPath.length() + 1);
            }
            return super.getName();
        }
    }

    public static class Dir extends CpFile {

        public Dir(String name) {
            getParentFromPath(name);
        }

        public Dir(CpFile parent, String name) {
            super(parent, name);
        }

        private Dir(DataInputStream is) throws Config.PGNException {
            super(is);
        }

        private Dir(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        public void serialize(DataOutputStream os) throws Config.PGNException {
            if(!Config.USE_BIT_STREAMS) {
                serializeBase(os);
            }
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            serializeBase(writer);
        }

        @Override
        protected PgnFileType getType() {
            return PgnFileType.Dir;
        }

        @Override
        public List<CpFile> getChildrenNames(ProgressObserver progressObserver) throws Config.PGNException {
            final List<CpFile> fileList = new ArrayList<>();
            walkThroughChildren(new EntryHandler() {
                @Override
                public boolean handle(CpFile item, BufferedReader br) {
                    fileList.add(item);
                    return true;
                }

                @Override
                public boolean getMoveText(CpFile entry) {
                    return false;
                }

                @Override
                public boolean addOffset(int length, int totalLength) {
                    logger.debug(String.format(Locale.getDefault(), "Dir addOffset %d, total %d", length, totalLength));
                    return false;
                }

                @Override
                public boolean skip(CpFile entry) {
                    return false;
                }
            }, true);
            this.length = fileList.size();  // update
            Collections.sort(fileList);
            return fileList;
        }

        // does not use pgnOnly
        void walkThroughChildren(final EntryHandler handler, boolean pgnOnly) throws Config.PGNException {
            final int[] index = {-1};
            File self = new File(absPath);
            File[] list = self.listFiles((file) -> {
                String name = file.getName();
                if (name.startsWith("."))
                    return false;
                CpFile entry;
                if (file.isDirectory()) {
                    entry = new Dir(Dir.this, name);
                } else if (CpFile.isPgnOk(name)) {
                    entry = new Pgn(Dir.this, name);
                } else if (CpFile.isZipOk(name)) {
                    entry = new Zip(Dir.this, name);
                } else {
                    return false;
                }
                entry.index = ++index[0];

            try {
                if (entry instanceof Pgn) {
                    try (BufferedReader br = new BufferedReader(new FileReader(entry.absPath), Config.MY_BUF_SIZE)) {
                        handler.handle(entry, br);
                    } catch (IOException e) {
                        logger.debug(entry.absPath, e);
                    }
                } else {
                    handler.handle(entry, null);
                }
            } catch (Config.PGNException e) {
                logger.debug(entry.absPath, e);
            }
                return false;    // drop it, save space
            });
        }

        void walkThroughGrandChildren(final Pgn gChild, final EntryHandler entryHandler) throws Config.PGNException {
            this.offset = 0;
            walkThroughChildren(new EntryHandler() {
                @Override
                public boolean handle(CpFile entry, BufferedReader br) throws Config.PGNException {
                    boolean _found;
                    if(gChild.index == -1) {
                        _found = gChild.getName().equals(entry.getName());
                    } else {
                        _found = gChild.index == entry.index;
                    }
                    if(!_found) {
                        return true;
                    }
                    gChild.length = entry.length;
                    parsePgnFiles(gChild, br, entryHandler);
                    return false;
                }

                @Override
                public boolean getMoveText(CpFile entry) {
                    return entry.index == gChild.index;
                }

                @Override
                public boolean addOffset(int length, int totalLength) {
                    Dir.this.offset += length;
                    Dir.this.length = totalLength;
                    return false;
                }

                @Override
                public boolean skip(CpFile entry) {
                    return entry.index != gChild.index;
                }
            }, true);
        }

        CpFile getRealFile(Item item) {
            return item.getParent();
        }

        void saveGrandChild(Item item, ProgressNotifier progressNotifier) throws Config.PGNException {
            File dir = new File(this.getAbsolutePath());
            if(!dir.exists()) {
                boolean ok = dir.mkdirs();
                if(!ok) {
                    throw new Config.PGNException(String.format("Cannot create %s directory", dir.getAbsoluteFile()));
                }
            }
            _saveGrandChild(item, progressNotifier);
        }

        void _saveGrandChild(Item item, ProgressNotifier progressNotifier) throws Config.PGNException {
            // rename existing file to tmp
            CpFile fileItem = getRealFile(item);
            String tmpFileName = fileItem.absPath + EXT_TEMP;
            File tmpFile = new File(tmpFileName);
            int count;
            try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
                // create a new file with replaced item
                count = saveGrandChild(item, fos, progressNotifier);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }

            // rename tmp to original name
            File oldFile = new File(fileItem.absPath);
            boolean res = oldFile.delete();
            if (count > 0) {
                res = tmpFile.renameTo(oldFile);
                logger.debug(String.format("renaming %s, %s", oldFile.getAbsoluteFile(), res));
            } else {
                res = tmpFile.delete();
                logger.debug(String.format("deleting %s, %s", oldFile.getAbsoluteFile(), res));
            }
        }

        // return entry count
        int saveGrandChild(Item item, FileOutputStream fos, ProgressNotifier progressNotifier) throws Config.PGNException {
            Pgn parent = (Pgn)item.getParent();
            int count = 0;
            try (FileReader fr = new FileReader(parent.absPath);
                 BufferedReader bufferedReader = new BufferedReader(fr, Config.MY_BUF_SIZE)) {
                count += modifyItem(item, bufferedReader, fos, progressNotifier);
            } catch (FileNotFoundException e) {
                count += modifyItem(item, null, fos, progressNotifier);
            } catch (IOException e) {
                logger.debug(parent.absPath, e);
            }
            return count;
        }
    }

    public static class Zip extends Dir {

        Zip(String name) {
            super(name);
        }

        Zip(CpFile parent, String name) {
            super(parent, name);
        }

        private Zip(DataInputStream is) throws Config.PGNException {
            super(is);
        }

        private Zip(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        protected PgnFileType getType() {
            return PgnFileType.Zip;
        }

        @Override
        public int getLength() {
            if (this.length == 0) {
                File self = new File(absPath);
                this.length = (int) self.length();   // truncate
            }
            return this.length;
        }

        @Override
        public String getDisplayLength() {
            return CpFile.getDisplayLength(getLength());
        }

        @Override
        void walkThroughChildren(EntryHandler zipEntryHandler, boolean pgnOnly) throws Config.PGNException {
            try (ZipFile zipFile = new ZipFile(absPath)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                int index = -1;
                while (entries.hasMoreElements()) {
                    ++index;
                    ZipEntry ze = entries.nextElement();
                    if (pgnOnly && (ze.isDirectory() || !CpFile.isPgnOk(ze.getName()))) {
                        continue;
                    }
                    Pgn item = new Pgn(Zip.this, ze.getName());
                    item.length = (int) ze.getSize();    // truncate!
                    item.index = index;
                    BufferedReader br = new BufferedReader(new InputStreamReader(zipFile.getInputStream(ze)), Config.MY_BUF_SIZE);
                    if (!zipEntryHandler.handle(item, br)) {
                        break;
                    }
                }
            } catch (Throwable t) {
                throw new Config.PGNException(t);
            }
        }

        long getChildTS(CpFile child) throws Config.PGNException {
            long ts = 0;
            String name = child.getName();
            try (ZipFile zipFile = new ZipFile(absPath)) {
                ZipEntry ze = zipFile.getEntry(name);
                ts = ze.getTime();
            } catch (IOException t) {
                throw new Config.PGNException(t);
            }
            return ts;
        }

        @Override
        CpFile getRealFile(Item item) {
            return this;
        }

        @Override
        void saveGrandChild(Item item, ProgressNotifier progressNotifier) throws Config.PGNException {
            File dir = new File(this.getAbsolutePath()).getParentFile();
            boolean res = dir.mkdirs();
            _saveGrandChild(item, progressNotifier);
        }

        /**
         * @param item to replace, set moveText = null to delete; set item.index = -1 to add a new Item
         * @param fos  resulting FileOutputStream
         * @throws Config.PGNException
         */
        @Override
        int saveGrandChild(final Item item, FileOutputStream fos, final ProgressNotifier progressNotifier) throws Config.PGNException {
            final Pgn parent = (Pgn) item.getParent();
            final char[] data = new char[Config.MY_BUF_SIZE];
            final boolean[] found = {false};
            final int[] count = {0};
            Zip.this.offset = 0;
            try (final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {
                walkThroughChildren(new EntryHandler() {
                    @Override
                    public boolean handle(CpFile entry, BufferedReader bufferedReader) throws Config.PGNException {
                        try {
                            ZipEntry zeOut = new ZipEntry(entry.getName());
                            zos.putNextEntry(zeOut);
                            boolean _found;
                            if (parent.index == -1) {
                                _found = parent.getName().equals(entry.getName());
                            } else {
                                _found = parent.index == entry.index;
                            }

                            if (_found) {
                                // mofified Pgn
                                progressNotifier.setOffset(ProgressNotifier.SET_TOTAL_LENGTH, entry.getLength());
                                count[0] += modifyItem(item, bufferedReader, zos, progressNotifier);
                                found[0] = true;
                            } else {
                                // blind copy
                                int _count;
                                while ((_count = bufferedReader.read(data, 0, Config.MY_BUF_SIZE)) != -1) {
                                    byte[] buf = new String(data).getBytes("UTF-8");
                                    zos.write(buf, 0, _count);
                                }
                                ++count[0];
                            }
                            return true;
                        } catch (IOException e) {
                            throw new Config.PGNException(e);
                        }
                    }

                    @Override
                    public boolean getMoveText(CpFile entry) {
                        return false;
                    }

                    @Override
                    public boolean addOffset(int length, int totalLength) {
                        Zip.this.offset += length;
                        return false;
                    }

                    @Override
                    public boolean skip(CpFile entry) {
                        return false;
                    }
                }, false);
                if (!found[0]) {
                    ZipEntry zeOut = new ZipEntry(parent.getName());
                    zos.putNextEntry(zeOut);
                    modifyItem(item, null, zos, progressNotifier);
                    ++count[0];
                }
                return count[0];
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

    static class ProgressNotifier {
        private static final int SET_TOTAL_LENGTH = -1;
        private static final int LIST_TRUNCATION = -2;
        private final ProgressObserver progressObserver;
        private int totalLength;

        ProgressNotifier(ProgressObserver progressObserver) {
            this.progressObserver = progressObserver;
            totalLength = 0;
            if(DEBUG) {
                System.out.println(String.format("New ProgressNotifier %s", this));
            }
        }

        boolean setOffset(int offset, int totalLength) {       // return true to abort
            if(offset == SET_TOTAL_LENGTH) {
                this.totalLength = totalLength;
                return false;
            }
            if(offset == LIST_TRUNCATION) {
                boolean done = false;
                if(progressObserver != null) {
                    done = progressObserver.setProgress(-1);
                }
                return done;
            }
            if(totalLength == 0) {
                totalLength = this.totalLength;
            }
            boolean done = false;
            if(progressObserver != null) {
                done = progressObserver.setProgress(getRelativeOffset(offset, totalLength));
            }
            return done;
        }

        private int getRelativeOffset(int offset, int totalLength) {
            if(totalLength == 0) {
                return 0;
            }
            return (int)((long)offset * 100 / (long)totalLength);
        }
    }
}
