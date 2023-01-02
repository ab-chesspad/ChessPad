# ChessPad
PGN editor for Android

The full description can be found in ChessPadApp/src/main/assets/about/ directory

Uses Stockfish as git subproject. To clone the whole thing use:
git clone --recurse-submodules https://github.com/ab-chesspad/ChessPad.git
It's possible to build ChessPad without nnue for Stockfish. That will make the program x10 smaller
at the cost of some SF weakness. For that nnue file has to be removed from assets and the line:
execute_process(...)
in CMakeLists.txt needs to be commented.
