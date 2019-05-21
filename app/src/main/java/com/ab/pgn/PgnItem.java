package com.ab.pgn;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Concrete subclasses: PgnItem, Pgn, Zip, Dir
 * List directory/zip/pgn file, extract individual game (Item) and add/update/delete game
 *
 * Created by Alexander Bootman on 7/30/16.
 */
public abstract class PgnItem implements Comparable<PgnItem> {
    public static final String
        EXT_TEMP = ".tmp",
        COMMON_ITEM_NAME = "item",
        TAG_START = "[",
        TAG_END = "\"]",
        dummy_pub_str = null;

    private static final String
        EXT_PGN = ".pgn",
        EXT_ZIP = ".zip",
        WRONG_PGN = "/" + EXT_PGN,
        WRONG_ZIP = "/" + EXT_ZIP,
        dummy_prv_str = null;

    private static int i = -1;

    public enum PgnItemType {
        Item(++i),
        Pgn(++i),
        Dir(++i),
        Zip(++i),
        ;

        private final int value;
        private static PgnItemType[] values = PgnItemType.values();

        PgnItemType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    final static PgnLogger logger = PgnLogger.getLogger(PgnItem.class);

    private static File root = new File("/");

    protected PgnItem parent;
    protected File self;
    protected int index = -1;
    protected int length = 0;
    protected transient int offset = 0;

    public abstract List<PgnItem> getChildrenNames(ProgressObserver progressObserver) throws Config.PGNException;

    public static void setRoot(File root) {
        PgnItem.root = root;
    }

    public static File getRoot() {
        return root;
    }

    public static boolean isPgnOk(String path) {
        path = path.toLowerCase();
        return path.endsWith(EXT_PGN) && !path.endsWith(WRONG_PGN);
    }

    public static boolean isZipOk(String path) {
        return path.endsWith(EXT_ZIP) && !path.endsWith(WRONG_ZIP);
    }

    protected PgnItem() {}

    protected void init(String name) {
        if(name.equals(root.getAbsolutePath())) {
            this.self = new File(name);
            return;
        }
        if(this.self == null) {
            if(name.startsWith(root.getAbsolutePath())) {
                this.self = new File(name);
            } else {
                this.self = new File(root.getAbsolutePath(), name);
            }
        }
        File parentFile = self.getParentFile();
        if(parentFile == null) {
            parentFile = root;
        }

        if(this instanceof Pgn) {
            while(!parentFile.equals(root)) {
                if(parentFile.isDirectory()) {
                    this.parent = new Dir(self.getParentFile().getAbsolutePath());
                    break;
                }
                if(parentFile.getAbsolutePath().toLowerCase().endsWith(EXT_ZIP)) {
                    this.parent = new Zip(parentFile.getAbsolutePath());
                    break;
                } else if(!parentFile.exists()) {
                    // assime that it is a new directory
                    this.parent = new Dir(parentFile.getAbsolutePath());
                    break;
                }
                parentFile = parentFile.getParentFile();
                if(parentFile == null) {
                    return;
                }
            }
        } else if(this instanceof Item) {
            this.parent = new Pgn(parentFile.getAbsolutePath());
        } else {
            this.parent = new Dir(parentFile.getAbsolutePath());
        }
    }

    public PgnItem(PgnItem parent, String name) {
        if (parent == null) {
            this.init(name);
        } else {
            this.parent = parent;
            this.self = new File(parent.self.getAbsoluteFile(), name);
        }
    }

    public int getLength() {
        if(this.length == 0) {
            List<PgnItem> children;
            try {
                children = getChildrenNames(null);
                this.length = children.size();
            } catch (Config.PGNException e) {
                logger.error(String.format("%s, getChildrenNames(null)", this.getName()), e);
                e.printStackTrace();
            }
        }
        return this.length;
    }

    public int getOffset() {
        return this.offset;
    }

    public String getName() {
        return self.getName();
    }

    public String getAbsolutePath() {
        return self.getAbsolutePath();
    }

    public PgnItem getParent() {
        PgnItem parent = this.parent;
        if(parent == null) {
            parent = getRootDir();
        }
        return parent;
    }

    public void setParent(PgnItem parent) {
        this.parent = parent;
        self = new File(parent.getAbsolutePath(), this.getName());
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

    public boolean equals(PgnItem item) {
        if (item == null)
            return false;
        return getAbsolutePath().equals(item.getAbsolutePath());
    }

    public int parentIndex(PgnItem parent) {
        String myPath = getAbsolutePath();
        if(!myPath.startsWith(parent.getAbsolutePath())) {
            return -1;
        }

        String parentPath = parent.getAbsolutePath();
        String relativePath = myPath.substring(parentPath.length() + 1);
        if(parent.getType() == PgnItemType.Zip) {
            // SicilianGrandPrix.pgn/item
            String[] parts = relativePath.split("/");
            for(String part : parts) {
                if(PgnItem.isPgnOk(part)) {
                    relativePath = part;
                    break;
                }
            }
        }
        try {
            List<PgnItem> siblings = parent.getChildrenNames(null);
            for (int i = 0; i < siblings.size(); ++i) {
                PgnItem sibling = siblings.get(i);
                String siblingName = sibling.getName();
                if (relativePath.startsWith(siblingName)) {
                    if (relativePath.length() == siblingName.length() || relativePath.charAt(siblingName.length()) == '/') {
                        return i;
                    }
                }
            }
        } catch (Config.PGNException e) {
            logger.error(e.getMessage(), e);
        }
        return -1;      // should not be here;
    }

    protected void serializeBase(DataOutputStream os) throws Config.PGNException {
        try {
            os.write(getType().getValue()); // single byte
            os.writeInt(length);
            os.writeInt(offset);
            os.writeInt(index);
            Util.writeString(os, self.getAbsolutePath());
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    protected void serializeBase(BitStream.Writer writer) throws Config.PGNException {
        try {
            writer.write(getType().getValue(), 3);
            writer.write(length, 32);
            writer.write(offset, 32);
            writer.write(index, 16);
            writer.writeString(self.getAbsolutePath());
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public abstract void serialize(DataOutputStream os) throws Config.PGNException;

    public abstract void serialize(BitStream.Writer writer) throws Config.PGNException;
    protected abstract PgnItemType getType();

    private PgnItem(DataInputStream is) throws Config.PGNException {
        try {
            this.length = is.readInt();
            this.offset = is.readInt();
            this.index = is.readInt();
            init(Util.readString(is));  // absolute path
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private PgnItem(BitStream.Reader reader) throws Config.PGNException {
        try {
            this.length = reader.read(32);
            this.offset = reader.read(32);
            this.index = reader.read(16);
            if (index == 0x0ffff) {
                index = -1;
            }
            init(reader.readString());  // absolute path
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    @Override
    public int compareTo(PgnItem that) {
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

    public static int compareNames(String name1, String name2) {
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

    public static PgnItem unserialize(DataInputStream is) throws Config.PGNException {
        try {
            PgnItemType pgnItemType = PgnItemType.values[is.read()];    // single byte
            PgnItem unserialized = null;
            switch (pgnItemType) {
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

    public static PgnItem unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            PgnItemType pgnItemType = PgnItemType.values[reader.read(3)];
            PgnItem unserialized = null;
            switch (pgnItemType) {
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

    public static PgnItem fromFile(File file) {
        PgnItem pgnItem = null;
        String path = file.getAbsolutePath();
        if (file.isDirectory()) {
            pgnItem = new Dir(path);
        } else if (PgnItem.isPgnOk(path)) {
            pgnItem = new Pgn(path);
        } else if (PgnItem.isZipOk(path)) {
            pgnItem = new Zip(path);
        }
        return  pgnItem;
    }

    public static void parsePgnItems(PgnItem parent, BufferedReader br, EntryHandler entryHandler) throws Config.PGNException {
        parsePgnItems(parent, br, entryHandler, true);
    }

    // when parseItems == false return raw pgn item text in item.moveText
    public static void parsePgnItems(PgnItem parent, BufferedReader br, EntryHandler entryHandler, boolean parseItems) throws Config.PGNException {
        try {
            final String nameValueSep = " \"";
            int index = -1;
            Item item = new Item(parent, COMMON_ITEM_NAME);
            item.index = ++index;
            StringBuilder sb = new StringBuilder(Config.STRING_BUF_SIZE);
            boolean inText = false;
            String line;
            int totalLength = 0;
            if(parent != null) {
                totalLength = parent.getLength();
            }
            while ((line = br.readLine()) != null) {
                entryHandler.addOffset(line.length() + 1, totalLength);
                line = line.trim();
                if (line.startsWith(TAG_START) && line.endsWith(TAG_END)) {
                    if (inText) {
                        item.moveText = new String(sb);
                        if (!entryHandler.handle(item, null)) {
                            return;
                        }
                        sb.delete(0, sb.length());
                        if (parseItems) {
                            item = new Item(parent, COMMON_ITEM_NAME);
                        }
                        item.index = ++index;
                        inText = false;
                    }
                    if (parseItems) {
                        line = line.substring(TAG_START.length(), line.length() - TAG_END.length());
                        int i = line.indexOf(nameValueSep);
                        if (i > 0) {
                            String hName = unescapeTag(line.substring(0, i).trim());
                            String hValue = unescapeTag(line.substring(i + nameValueSep.length()).trim());
                            if(hName.equals(Config.HEADER_FEN)) {
                                item.setFen(hValue);
                            } else {
                                item.headers.add(new Pair<>(hName, hValue));
                            }
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
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    // https://en.wikipedia.org/wiki/Portable_Game_Notation#Tag_pairs
    // A quote inside a tag value is represented by the backslash immediately followed by a quote.
    // A backslash inside a tag value is represented by two adjacent backslashes.
    public static String unescapeTag(String src) {
        return src.replaceAll("(^|[^\\\\])\\\\(\"|\\\\)", "$1$2");
    }

    public static String escapeTag(String src) {
        return src.replaceAll("(\\\\|\\\")", "\\\\$1");
    }

    public static String getTitle(List<Pair<String, String>> headers, int index) {
        StringBuilder sb = new StringBuilder();
        if(index >= 0) {
            sb.append(String.format("%s. ", index + 1));
        }
        String sep = "";
        for (String h : Config.titleHeaders) {
            String v = null;
            for(Pair<String, String> lt : headers) {
                if(h.equals(lt.first)) {
                    v = lt.second;
                    break;
                }
            }
            if (v == null) {
                v = Config.HEADER_UNKNOWN_VALUE;
            }
            sb.append(sep).append(v);
            sep = " - ";
        }
        return new String(sb);
    }

    public static Item getPgnItem(PgnItem parent, int index) throws Config.PGNException {
        if (index < 0 || !(parent instanceof Pgn)) {
            throw new Config.PGNException("invalid parent type or invalid index");
        }
        Item item = new Item(parent, String.format("item%s", index));
        item.index = index;
        getPgnItem(item, null);
        return item;
    }

    // using item.parent and item.index
    public static void getPgnItem(final Item item, ProgressObserver progressObserver) throws Config.PGNException {
        if(item.index < 0 || item.getParent() == null || !(item.getParent() instanceof Pgn)) {
            throw new Config.PGNException(String.format("%s - invalid item type or invalid data", item.toString()));
        }

        final Pgn parent = (Pgn)item.getParent();
        if (!Dir.class.isAssignableFrom(parent.getParent().getClass())) {
            throw new Config.PGNException(String.format("%s, %s - invalid grandparent type", parent.getParent().getName(), item.toString()));
        }
        final ProgressNotifier progressNotifier = new ProgressNotifier(progressObserver);
        parent.offset = 0;
        Dir grandParent = (Dir)parent.getParent();
        grandParent.walkThroughGrandChildren(parent, new EntryHandler() {
            @Override
            public boolean handle(PgnItem entry, BufferedReader br) {
                if(entry.index != item.index) {
                    return true;
                }
                item.headers = ((Item)entry).headers;
                item.moveText = ((Item)entry).moveText;
                item.fen = ((Item)entry).fen;
                return false;
            }

            @Override
            public boolean getMoveText(PgnItem entry) {
                return entry.index == item.index;
            }

            public void addOffset(int length, int totalLength) {
                parent.offset += length;
                progressNotifier.setOffset(parent.offset, totalLength);
            }
        });
    }

    public static void copy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        copy(inStream, outStream);
        inStream.close();
        outStream.close();
    }

    public static void copy(FileInputStream inStream, FileOutputStream outStream) throws IOException {
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
    }

    public static Dir getRootDir() {
        return new Dir(root.getAbsolutePath());
    }

    // relative to rootDir
    public static String getRelativePath(PgnItem pgnItem) {
        if(pgnItem == null) {
            return "";          // sanity check
        }
        String rootPath = root.getAbsolutePath();
        String itemPath = pgnItem.getAbsolutePath();
        if(itemPath.startsWith(rootPath)) {
            itemPath = itemPath.substring(rootPath.length());
            if(itemPath.startsWith("/")) {
                itemPath = itemPath.substring(1);
            }
        }
        return itemPath;
    }

    // returns resulting number of entries
    static int modifyItem(final Item item, BufferedReader bufferedReader, final OutputStream os, final ProgressNotifier progressNotifier) throws Config.PGNException {
        try {
            final int[] count = {0};
            final int[] offset = {0};
            if (bufferedReader == null) {
                item.index = -1;
            } else {
                parsePgnItems(item.getParent(), bufferedReader, new EntryHandler() {
                    @Override
                    public boolean handle(PgnItem entry, BufferedReader bufferedReader) throws Config.PGNException {
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
                    public boolean getMoveText(PgnItem entry) {
                        return true;
                    }

                    @Override
                    public void addOffset(int length, int totalLength) {
                        offset[0] += length;
                        if (progressNotifier != null) {
                            progressNotifier.setOffset(offset[0], totalLength);
                        }
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

    public static void serialize(DataOutputStream os, List<Pair<String, String>> headers) throws Config.PGNException {
        try {
            if (headers == null) {
                os.write(0);
                return;
            }
            os.write(headers.size());
            for (Pair<String, String> header : headers) {
                Util.writeString(os, header.first);
                Util.writeString(os, header.second);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static void serialize(BitStream.Writer writer, List<Pair<String, String>> headers) throws Config.PGNException {
        try {
            if (headers == null) {
                writer.write(0, 8);
                return;
            }
            writer.write(headers.size(), 8);
            for (Pair<String, String> header : headers) {
                writer.writeString(header.first);
                writer.writeString(header.second);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static List<Pair<String, String>> unserializeHeaders(DataInputStream is) throws Config.PGNException {
        try {
            int totalHeaders = is.read();
            if (totalHeaders == 0) {
                return null;
            }
            List<Pair<String, String>> headers = new LinkedList<>();
            for (int i = 0; i < totalHeaders; ++i) {
                String label = Util.readString(is);
                String value = Util.readString(is);
                headers.add(new Pair<>(label, value));
            }
            return headers;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static List<Pair<String, String>> unserializeHeaders(BitStream.Reader reader) throws Config.PGNException {
        try {
            int totalHeaders = reader.read(8);
            if (totalHeaders == 0) {
                return null;
            }
            List<Pair<String, String>> headers = new LinkedList<>();
            for (int i = 0; i < totalHeaders; ++i) {
                String label = reader.readString();
                if(label == null) {
                    label = "";         // should never happen
                }
                String value = reader.readString();
                if(value == null) {
                    value = "";         // for editHeaders
                }
                headers.add(new Pair<>(label, value));
            }
            return headers;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static List<Pair<String, String>> cloneHeaders(List<Pair<String, String>> oldHeaders, String... skip) {
        List<Pair<String, String>> headers = new ArrayList<>();
clone:  for(Pair<String, String> header : oldHeaders) {
            if(skip != null) {
                for(String e : skip) {
                    if (header.first.equals(e)) {
                        continue clone;
                    }
                }
            }
            headers.add(new Pair<>(header.first, header.second));
        }
        return headers;
    }

    public static class Item extends PgnItem {
        protected List<Pair<String, String>> headers = new LinkedList<>();
        private String fen;
        private Map<String, String> headerMap;
        private String moveText = "";

        public Item(String name) {
            init(name);
        }

        public Item(PgnItem parent, String name) {
            super(parent, name);
        }

        @Override
        public void serialize(DataOutputStream os) throws Config.PGNException {
            serializeBase(os);
            serialize(os, this.headers);
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            serializeBase(writer);
            serialize(writer, this.headers);
            try {
                writer.writeString(this.fen);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
/*  11/18/2018 verify!!
            try {
                writer.writeString(this.moveText);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
//*/
        }

        @Override
        protected PgnItemType getType() {
            return PgnItemType.Item;
        }

        private Item(DataInputStream is) throws Config.PGNException {
            super(is);
            this.headers = unserializeHeaders(is);
        }

        private Item(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
                this.headers = unserializeHeaders(reader);
            try {
                this.fen = reader.readString();
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
/*  11/18/2018 verify!!
            try {
                this.moveText = reader.readString();
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
//*/
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
        public String toString() {
            return getTitle(this.headers, this.index);
        }

        public String toPgnString() {
            return toString(true, false);
        }

        public StringBuilder headersToString(boolean cr2Space, boolean escapeTags) {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (Pair<String, String> h : headers) {
                String hName = h.first;
                if (escapeTags) {
                    hName = escapeTag(hName);
                }
                String hValue = h.second;
                if (hValue == null || hValue.isEmpty()) {
                    hValue = Config.HEADER_UNKNOWN_VALUE;
                }
                if (escapeTags) {
                    hValue = escapeTag(hValue);
                }
                sb.append(sep).append("[").append(hName).append(" \"").append(hValue).append("\"]");
                sep = "\n";
            }
            if(this.fen != null) {
                sb.append(sep).append("[").append(Config.HEADER_FEN).append(" \"").append(this.fen).append("\"]");
            }
            return sb;
        }

        public String toString(boolean cr2Space, boolean escapeTags) {
            StringBuilder sb = headersToString(cr2Space, escapeTags);
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
        public List<PgnItem> getChildrenNames(ProgressObserver progressObserver) {
            throw new RuntimeException("Pgn.Item cannot contain children");
        }

        public void save(ProgressObserver progressObserver) throws Config.PGNException {
            final PgnItem parent = this.getParent();
            if (!(parent instanceof Pgn)) {
                throw new Config.PGNException(String.format("%s - invalid item type or invalid data", this.toString()));
            }

            Dir grandParent = (Dir) parent.getParent();
            if(grandParent == null) {
                grandParent = new Dir(parent.getAbsolutePath());
            }
            grandParent.saveGrandChild(this, new ProgressNotifier(progressObserver));
        }

        private Map<String, String> getHeaderMap() {
            if(headerMap == null) {
                headerMap = new HashMap<>();
                for(Pair<String, String> h : headers) {
                    headerMap.put(h.first, h.second);
                }
            }
            return headerMap;
        }

        public String getHeader(String label) {
            return getHeaderMap().get(label);
        }

        public Pair<String, String> getHeader(int index) {
            return headers.get(index);
        }

        public List<Pair<String, String>> getHeaders() {
            return headers;
        }

        public void setHeaders(List<Pair<String, String>> headers) {
            this.headers = cloneHeaders(headers);
            headerMap = null;
        }

        public void addHeader(Pair<String, String> h) {
            if(getHeader(h.first) == null) {
                headers.add(h);
                getHeaderMap().put(h.first, h.second);
            }
        }

        public List<Pair<String, String>> cloneHeaders() {
            return cloneHeaders(this.headers);
        }

        @Override
        public int parentIndex(PgnItem parent) {
            PgnItem thisParent = this.getParent();
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
        public void setParent(PgnItem parent) {
            this.parent = parent;
            self = new File(parent.getAbsolutePath(), COMMON_ITEM_NAME);
        }

    }

    public static class Pgn extends PgnItem {
        public Pgn(String name) {
            init(name);
        }

        public Pgn(PgnItem parent, String name) {
            super(parent, name);
        }

        @Override
        public void serialize(DataOutputStream os) throws Config.PGNException {
            serializeBase(os);
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            serializeBase(writer);
        }

        @Override
        protected PgnItemType getType() {
            return PgnItemType.Pgn;
        }

        private Pgn(DataInputStream is) throws Config.PGNException {
            super(is);
        }

        private Pgn(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        public int getLength() {
            if(this.length == 0) {
                this.length = (int)self.length();   // truncate
            }
            return this.length;
        }

        @Override
        public List<PgnItem> getChildrenNames(ProgressObserver progressObserver) throws Config.PGNException {
            final int[] offset = {0};
            final ProgressNotifier progressNotifier = new ProgressNotifier(progressObserver);
            final List<PgnItem> items = new LinkedList<>();
            ((Dir)getParent()).walkThroughGrandChildren(this, new EntryHandler() {
                @Override
                public boolean handle(PgnItem entry, BufferedReader br) {
                    entry.offset = offset[0];
                    items.add(entry);
                    return true;
                }

                @Override
                public boolean getMoveText(PgnItem entry) {
                    return false;
                }

                @Override
                public void addOffset(int length, int totalLength) {
                    offset[0] += length;
                    progressNotifier.setOffset(offset[0], totalLength);
                }
            });
            return items;
        }
    }

    public static class Dir extends PgnItem {

        public Dir(String name) {
            init(name);
        }

        public Dir(PgnItem parent, String name) {
            super(parent, name);
        }

        private Dir(DataInputStream is) throws Config.PGNException {
            super(is);
        }

        @Override
        public void serialize(DataOutputStream os) throws Config.PGNException {
            serializeBase(os);
        }

        private Dir(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            serializeBase(writer);
        }

        @Override
        protected PgnItemType getType() {
            return PgnItemType.Dir;
        }

        @Override
        public List<PgnItem> getChildrenNames(ProgressObserver progressObserver) throws Config.PGNException {
            final List<PgnItem> fileList = new ArrayList<>();
            walkThroughChildren(new EntryHandler() {
                @Override
                public boolean handle(PgnItem item, BufferedReader br) {
                    fileList.add(item);
                    return true;
                }

                @Override
                public boolean getMoveText(PgnItem entry) {
                    return false;
                }

                @Override
                public void addOffset(int length, int totalLength) {
                    logger.debug(String.format(Locale.getDefault(), "Dir addOffset %d, total %d", length, totalLength));
                }
            }, true);
            this.length = fileList.size();  // update
            Collections.sort(fileList);
            return fileList;
        }

        // does not use pgnOnly
        protected void walkThroughChildren(final EntryHandler handler, boolean pgnOnly) throws Config.PGNException {
            final int[] index = {-1};
            self.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    String name = file.getName();
                    if (name.startsWith("."))
                        return false;
                    PgnItem entry;
                    BufferedReader br = null;
                    if (file.isDirectory()) {
                        entry = new Dir(Dir.this, name);
                    } else if (PgnItem.isPgnOk(name)) {
                        entry = new Pgn(Dir.this, name);
                        try {
                            br = new BufferedReader(new FileReader(entry.self.getAbsoluteFile()), Config.MY_BUF_SIZE);
                        } catch (FileNotFoundException e) {
                            logger.debug(entry.self.getAbsoluteFile(), e);
                        }
                    } else if (PgnItem.isPgnOk(name)) {
                        entry = new Zip(Dir.this, name);
                    } else {
                        return false;
                    }
                    entry.index = ++index[0];
                    try {
                        handler.handle(entry, br);
                    } catch (Config.PGNException e) {
                        logger.debug(entry.self.getAbsoluteFile(), e);
                        return false;
                    }
                    return false;    // drop it, save space
                }
            });
        }

        public void walkThroughGrandChildren(final Pgn gChild, final EntryHandler entryHandler) throws Config.PGNException {
            this.offset = 0;
            walkThroughChildren(new EntryHandler() {
                @Override
                public boolean handle(PgnItem entry, BufferedReader br) throws Config.PGNException {
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
                    parsePgnItems(gChild, br, entryHandler);
                    return false;
                }

                @Override
                public boolean getMoveText(PgnItem entry) {
                    return entry.index == gChild.index;
                }

                @Override
                public void addOffset(int length, int totalLength) {
                    Dir.this.offset += length;
                    Dir.this.length = totalLength;
                }
            }, true);
        }

        PgnItem getRealFile(Item item) {
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
            try {
                // rename existing file to tmp
                PgnItem fileItem = getRealFile(item);
                String tmpFileName = fileItem.self.getAbsolutePath() + EXT_TEMP;
                File tmpFile = new File(tmpFileName);

                // create a new file with replaced item
                FileOutputStream fos = new FileOutputStream(tmpFile);
                int count = saveGrandChild(item, fos, progressNotifier);
                fos.flush();
                fos.close();

                // rename tmp to original name
                File oldFile = new File(fileItem.self.getAbsolutePath());
                oldFile.delete();
                if (count > 0) {
                    tmpFile.renameTo(oldFile);
                } else {
                    tmpFile.delete();
                    logger.debug(String.format("deleting %s", oldFile.getAbsoluteFile()));
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        // return entry count
        int saveGrandChild(Item item, FileOutputStream fos, ProgressNotifier progressNotifier) throws Config.PGNException {
            Pgn parent = (Pgn)item.getParent();
            BufferedReader bufferedReader = null;
            int count = 0;
            try {
                FileReader fr = new FileReader(parent.self.getAbsoluteFile());
                bufferedReader = new BufferedReader(fr, Config.MY_BUF_SIZE);
            } catch (Throwable t) {
                logger.debug(parent.self.getAbsoluteFile(), t);
            }
            count += modifyItem(item, bufferedReader, fos, progressNotifier);
            if(bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    throw new Config.PGNException(e);
                }
            }
            return count;
        }
    }

    public static class Zip extends Dir {

        public Zip(String name) {
            super(name);
        }

        public Zip(PgnItem parent, String name) {
            super(parent, name);
        }

        private Zip(DataInputStream is) throws Config.PGNException {
            super(is);
        }

        private Zip(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
        }

        @Override
        protected PgnItemType getType() {
            return PgnItemType.Zip;
        }

        @Override
        public void walkThroughChildren(EntryHandler zipEntryHandler, boolean pgnOnly) {
            try {
                ZipFile zipFile = new ZipFile(self.getAbsolutePath());
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                int index = -1;
                while (entries.hasMoreElements()) {
                    ++index;
                    ZipEntry ze = entries.nextElement();
                    if (pgnOnly && (ze.isDirectory() || !PgnItem.isPgnOk(ze.getName()))) {
                        continue;
                    }
                    Pgn item = new Pgn(Zip.this, ze.getName());
                    item.length = (int)ze.getSize();    // truncate!
                    item.index = index;
                    BufferedReader br = new BufferedReader(new InputStreamReader(zipFile.getInputStream(ze)), Config.MY_BUF_SIZE);
                    if (!zipEntryHandler.handle(item, br)) {
                        break;
                    }
                }
                zipFile.close();
            } catch (Throwable t) {
                logger.debug(self.getAbsoluteFile(), t);
            }
        }

        @Override
        PgnItem getRealFile(Item item) {
            return this;
        }

        @Override
        void saveGrandChild(Item item, ProgressNotifier progressNotifier) throws Config.PGNException {
            File dir = new File(this.getAbsolutePath()).getParentFile();
            dir.mkdirs();
            _saveGrandChild(item, progressNotifier);
        }

        /**
             * @param item to replace, set moveText = null to delete; set item.index = -1 to add a new Item
             * @param fos
             * @throws Config.PGNException
             */
        @Override
        int saveGrandChild(final Item item, FileOutputStream fos, final ProgressNotifier progressNotifier) throws Config.PGNException {
            try {
                final ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos));
                final Pgn parent = (Pgn) item.getParent();
                final char data[] = new char[Config.MY_BUF_SIZE];
                final boolean[] found = {false};
                final int[] count = {0};
                Zip.this.offset = 0;

                walkThroughChildren(new EntryHandler() {
                    @Override
                    public boolean handle(PgnItem entry, BufferedReader bufferedReader) throws Config.PGNException {
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
                                if(parent != null) {
                                    progressNotifier.setOffset(ProgressNotifier.SET_TOTAL_LENGTH, entry.getLength());
                                }
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
                    public boolean getMoveText(PgnItem entry) {
                        return false;
                    }

                    @Override
                    public void addOffset(int length, int totalLength) {
                        Zip.this.offset += length;
                    }
                }, false);
                if (!found[0]) {
                    ZipEntry zeOut = new ZipEntry(parent.getName());
                    zos.putNextEntry(zeOut);
                    modifyItem(item, null, zos, progressNotifier);
                    ++count[0];
                }
                zos.flush();
                zos.close();
                return count[0];
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

    public interface ProgressObserver {
        void setProgress(int progress);
    }

    static class ProgressNotifier {
        static final int SET_TOTAL_LENGTH = -1;
        ProgressObserver progressObserver;
        int offset = 0;
        int totalLength = 0;

        public ProgressNotifier(ProgressObserver progressObserver) {
            this.progressObserver = progressObserver;
        }

        public void setOffset(int offset, int totalLength) {
            if(offset == SET_TOTAL_LENGTH) {
                this.totalLength = totalLength;
                return;
            }
            if(totalLength == 0) {
                totalLength = this.totalLength;
            }
            this.offset = offset;
            if(progressObserver != null) {
                progressObserver.setProgress(getRelativeOffset(offset, totalLength));
            }
        }

        private int getRelativeOffset(int offset, int totalLength) {
            if(totalLength == 0) {
                return 0;
            }
            return offset * 100 / totalLength;
        }
    }

    public interface EntryHandler {
        // return false to break iteration
        boolean handle(PgnItem entry, BufferedReader bufferedReader) throws Config.PGNException;
        boolean getMoveText(PgnItem entry);
        void addOffset(int length, int totalLength);
    }
}
