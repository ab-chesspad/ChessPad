# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

#############
# 'Rebuild Project' command in Android Studio does not always work.
# Delete .cxx directory manually to execute this script, e.g.
# rm -rf ./ChessPadApp/.cxx
#############

cmake_minimum_required(VERSION 3.18.1)

# Declares and names the project.
project("stockfishlib")

execute_process (
    COMMAND make net
    WORKING_DIRECTORY ../Stockfish/src
)
# 'COMMAND' treats '*' as an actual character, thus this weird way to move nnue file
execute_process (
    COMMAND ./move-nnue ../Stockfish/src src/main/assets
)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DNNUE_EMBEDDING_OFF -Dshared_lib")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

set(sf_dir ../../../../Stockfish/src)
file(GLOB SOURCES ${sf_dir}/jni/*.cpp ${sf_dir}/*.cpp ${sf_dir}/syzygy/*.cpp
        ${sf_dir}/nnue/*.cpp ${sf_dir}/nnue/features/*.cpp
        ${sf_dir}/nnue/layers/*.cpp ${sf_dir}/incbin/*.cpp)

add_library( # Sets the name of the library.
        stockfish

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        ${SOURCES}
#        stockfishlib.cpp
        )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        stockfish

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})