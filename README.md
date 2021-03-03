# Android Synth app

This is a fork and revival of the [music-synthesizer-for-android](https://github.com/google/music-synthesizer-for-android) project.

It has been updated to build with modern gradle and now uses CMake for building the c++ library.

I'm hoping to continue modernising it to the point of being able to target the current minimum API to get it back onto the Play store as well as looking to add new features and possibly make use of newer APIs (eg. dedicated android midi api vs current direct usb, using AASound vs OpenSL-ES, etc). 