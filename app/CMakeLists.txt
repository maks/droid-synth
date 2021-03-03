# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)


# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

add_library( # Specifies the name of the library.
             synth

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             src/main/cpp/android_glue.cc
        src/main/cpp/dx7note.cc
        src/main/cpp/env.cc
        src/main/cpp/exp2.cc
        src/main/cpp/fir.cc
        src/main/cpp/fm_core.cc
        src/main/cpp/fm_op_kernel.cc
        src/main/cpp/freqlut.cc
        src/main/cpp/lfo.cc
        src/main/cpp/log2.cc
        src/main/cpp/patch.cc
        src/main/cpp/pitchenv.cc
        src/main/cpp/resofilter.cc
        src/main/cpp/ringbuffer.cc
        src/main/cpp/sawtooth.cc
        src/main/cpp/sin.cc
        src/main/cpp/synth_unit.cc
        )

find_library( # Defines the name of the path variable that stores the
              # location of the NDK library.
              log-lib

              # Specifies the name of the NDK library that
              # CMake needs to locate.
              log )

find_library( # Defines the name of the path variable that stores the
              # location of the NDK library.
              openSL-ES

              # Specifies the name of the NDK library that
              # CMake needs to locate.
              OpenSLES )

target_compile_options(synth PRIVATE -Wno-c++11-narrowing)

include_directories("include")

# Links your native library against one or more other native libraries.
target_link_libraries( # Specifies the target library.
        synth

        # Links the log library to the target library.
        ${log-lib} )

target_link_libraries( # Specifies the target library.
                       synth

                       # Links the log library to the target library.
                       ${openSL-ES} )
