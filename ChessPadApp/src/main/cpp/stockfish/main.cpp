/*
  Stockfish, a UCI chess playing engine derived from Glaurung 2.1
  Copyright (C) 2004-2021 The Stockfish developers (see AUTHORS file)

  Stockfish is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Stockfish is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

#include <iostream>

#include "bitboard.h"
#include "endgame.h"
#include "position.h"
#include "psqt.h"
#include "search.h"
#include "thread.h"
#include "tt.h"
#include "uci.h"

using namespace Stockfish;

Outstream outstream, errstream;
std::atomic<int> done = 0;

int sf_init() {
  sync_cout << engine_info() << sync_endl;
  UCI::init(Options);
  Tune::init();
  PSQT::init();
  Bitboards::init();
  Position::init();
  Bitbases::init();
  Endgames::init();
  Threads.set(size_t(Options["Threads"]));
  Search::clear(); // After threads are up
  Eval::NNUE::init();
  UCI::init_pos();
  return 0;
}

void unblock_readers() {
    done = 1;
    outstream.finish();
    errstream.finish();
}

#ifndef shared_lib
/// input_reader() waits for a command from stdin and invokes UCI::execute()
/// Also intercepts EOF from stdin to ensure gracefully exiting if the
/// GUI dies unexpectedly.
void input_reader() {
    std::string cmd;
    while (getline(std::cin, cmd)) {
        UCI::execute(cmd);
        if (cmd == "quit")
            break;
    }
}

static std::mutex mutex_;

void output_reader() {
    while (!done) {
        std::string res;
        int len = outstream.read(res);
        if (len < 0) {
            break;
        }
        mutex_.lock();
        std::cout << res;
        mutex_.unlock();
    }
}

void error_reader() {
    while (!done) {
        std::string res;
        int len = errstream.read(res);
        if (len < 0) {
            break;
        }
        mutex_.lock();
        std::cerr << res;
        mutex_.unlock();
    }
}

/// When SF is called with some command line arguments, e.g. to
/// run 'bench', once the command is executed the program stops.
int main(int argc, char* argv[]) {
    std::thread output_reader_thread(output_reader);
    std::thread error_reader_thread(error_reader);

    int res = sf_init();

    if (argc > 1) {
        std::string cmd;
        for (int i = 1; i < argc; ++i)
            cmd += std::string(argv[i]) + " ";
        UCI::execute(cmd);
    } else {
        std::thread input_reader_thread(input_reader);
        input_reader_thread.join();
    }
    unblock_readers();
    output_reader_thread.join();
    error_reader_thread.join();
    return res;
}
#endif
