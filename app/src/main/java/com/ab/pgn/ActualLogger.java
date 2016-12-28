package com.ab.pgn;

/**
 *
 * Created by abootman on 12/26/16.
 */
public interface ActualLogger {
    void debug(Object message);
    void error(Object message);
    void error(Object message, Throwable t);
}
