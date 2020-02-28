package com.ab.pgn.fics;

class AfterLoginCommands {
    private final String[] afterLoginCommands = {
        "iset defprompt 1",         // Setting ivariable defprompt forces the user's prompt to 'fics% '
        "iset gameinfo 1",          // Provides extra notifications when the game starts or becomes observed
        "iset ms 1",                // no description
        "iset allresults 1",        // Receive the result of games which are not in progress, such as the resigning or adjudication of stored games
        "iset startpos 1",          // Generate a board at the start of a move list if the move list does not start from the normal chess position
        "iset pendinfo 0",          // Do not get extra notifications of pending offers
        "iset nowrap 1",            // no description
        "iset smartmove 0",         // no description
        "iset premove 0",           // no description
        "iset lock 1",              // stop further changing of ivariables

        "set interface chesspad",   // that's us
        "set style 12",
        "set bell 0",
        "set ptime 0",              // bughouse
        "set bugopen 0",            // bughouse
    };

    String[] list() {
        return afterLoginCommands;
    }
}
