package com.ab.droid.chesspad.io;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ab.droid.chesspad.Sample;
import com.ab.pgn.Config;
import com.ab.pgn.io.FilAx;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@Ignore("Need a way to automate this")
@RunWith(AndroidJUnit4.class)
public class DocFilAxTest extends BaseTest {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    public static final String
        ROOT_DIR = "/sdcard/Documents/ChessPad/",
        SAMPLE_FILE_NAME = "a/b/t.pgn",
        dummy_string = null;

/*
    @Test
    public void test() {
        File f = new File("etc/test/../../src/main/book/");
        System.out.println(f.getAbsolutePath());
        File[] children = f.listFiles();
        System.out.println(children.length);
    }
*/

    @Test
    public void testDocFile() throws IOException {
        final String NEW_NAME = "t1.pgn";
        String[] parts = SAMPLE_FILE_NAME.split(FilAx.SLASH);
        String fileName = parts[parts.length - 1];
        String path = "";
        String sep = FilAx.SLASH;
        for (int i = 0; i < parts.length - 1; ++i) {
            path += sep + parts[i];
            sep = FilAx.SLASH;
        }

        new Sample().createSample(SAMPLE_FILE_NAME);
        FilAx sample = new DocFilAx(SAMPLE_FILE_NAME);
        Assert.assertEquals(fileName, sample.getName());
        String newFilePath = path + FilAx.SLASH + NEW_NAME;

        FilAx newFile = new DocFilAx(newFilePath);
        Assert.assertEquals(NEW_NAME, newFile.getName());
        try (OutputStream os = newFile.getOutputStream();
                InputStream is = sample.getInputStream()
             ) {
            byte[] buf = new byte[2048];
            int len;
            while ((len = is.read(buf)) > 0) {
                os.write(buf, 0, len);
            }
        }
//*
        newFile.delete();
        String parentPath = DocFilAx.getParentPath(newFilePath);
        Assert.assertEquals(path, parentPath);
/*/
        newFile.getParent().getParent().delete();
        String parentPath = DocFilAx.getParentPath(NEW_FILE_PATH);
        Assert.assertEquals("/", parentPath);
//*/
        Log.d(DEBUG_TAG, "done");
    }
}