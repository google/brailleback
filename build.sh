#!/usr/bin/env bash
mkdir third_party
midilibgiturl="https://github.com/LeffelMania/android-midi-lib.git"
brlttygiturl="https://github.com/brltty/brltty.git"
liblouisgiturl="https://github.com/liblouis/liblouis.git"
cd third_party
mkdir midilib
mkdir liblouislib
git clone $midilibgiturl midilib
git clone --branch v3.4.0 $liblouisgiturl liblouislib
cd liblouislib && ./autogen.sh && ./configure
cd ..
cp -r midilib/src/main/java/* ../libraries/utils/src/main/java
cp -r liblouislib/liblouis ../service/src/main/jni/liblouiswrapper
cd ..
./gradlew assembleDebug
