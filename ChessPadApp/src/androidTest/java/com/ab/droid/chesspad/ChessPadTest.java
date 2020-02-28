package com.ab.droid.chesspad;

//import android.support.test.runner.AndroidJUnit4;

import android.util.Log;

import com.ab.pgn.Config;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

/**
 * unit tests
 * Created by Alexander Bootman on 8/31/16.
 */

//@RunWith(AndroidJUnit4.class)
@RunWith(MockitoJUnitRunner.class)
public class ChessPadTest {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testOnCreate() throws Exception {
        Log.d(DEBUG_TAG, "testOnCreate");
    }

    @Test
    public void testOnResume() throws Exception {

    }

    @Test
    public void testOpenPgnItem() throws Exception {

    }

    @Test
    public void testOnConfigurationChanged() throws Exception {

    }

    @Test
    public void testOnTouchEvent() throws Exception {

    }

    @Test
    public void testOnButtonClick() throws Exception {

    }

    @Test
    public void testOnSquareClick() throws Exception {

    }

    @Test
    public void testSelectFromList() throws Exception {

    }
}