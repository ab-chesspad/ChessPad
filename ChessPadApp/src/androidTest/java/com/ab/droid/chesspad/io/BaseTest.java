package com.ab.droid.chesspad.io;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.ab.droid.chesspad.MainActivity;
import com.ab.pgn.Config;

import org.junit.Assert;
import org.junit.BeforeClass;

public class BaseTest {
    public static final String
        ROOT_DIR = "/sdcard/Documents/ChessPad/",
        // on API 32
//        ROOT_URL_STRING = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FChessPad",
        // on API 28
        ROOT_URL_STRING = "content://com.android.externalstorage.documents/tree/home%3AChessPad/document/home%3AChessPad",
        dummy_string = null;

    static Context appContext;

    @BeforeClass
    public static void init() throws Config.PGNException {
        appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String packageName = MainActivity.class.getPackage().getName();
        Assert.assertEquals(packageName, appContext.getPackageName());
        DocFilAx.setRoot(appContext, ROOT_URL_STRING);
    }

}
