#!/usr/bin/env bash

basedir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

midilibgiturl="https://github.com/LeffelMania/android-midi-lib.git"
brlttygiturl="https://github.com/brltty/brltty.git"
liblouisgiturl="https://github.com/liblouis/liblouis.git"
git clone $midilibgiturl $basedir/third_party/midilib
git clone --branch v3.4.0 $liblouisgiturl $basedir/third_party/liblouislib
git clone --branch BRLTTY-5.6 $brlttygiturl $basedir/third_party/brlttylib
cp -r $basedir/third_party/midilib/src/main/java/* $basedir/braille/libraries/utils/src
cp $basedir/third_party/midilib/LICENSE $basedir/braille/libraries/utils
mkdir $basedir/braille/service/jni/brlttywrapper/brltty
cp -r $basedir/third_party/brlttylib/Drivers $basedir/braille/service/jni/brlttywrapper/brltty
cp -r $basedir/third_party/brlttylib/Headers $basedir/braille/service/jni/brlttywrapper/brltty
cp -r $basedir/third_party/brlttylib/Programs $basedir/braille/service/jni/brlttywrapper/brltty
mkdir $basedir/braille/service/jni/liblouiswrapper/liblouis
cp -r $basedir/third_party/liblouislib/liblouis $basedir/braille/service/jni/liblouiswrapper/liblouis
cp -r $basedir/third_party/liblouislib/tables $basedir/braille/service/jni/liblouiswrapper/liblouis
cd $basedir
patch -p1 < brltty.patch
patch -p1 < liblouis.patch
cd braille
./gradlew assembleDebug
