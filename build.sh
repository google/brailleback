#!/bin/bash

# Copyright 2015 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This shell script builds brailleback with its dependencies.
# If the external dependencies are not present, it tries
# to check them out first.
# This script has been tested on Ubuntu Linux 10.04.4.

set -e

function die() {
  echo "Error: $*" >&2
  exit 1
}

function ensureMidiLibDependency() {
  local name="$1"
  local dir="$2"
  local giturl="$3"
  if [ ! -d "${dir}" ]; then
    git clone "${giturl}" "${dir}"
    (cd "${dir}" && cp ../../braille/libraries/build.xml ./build.xml)
  else
    echo "Verified midilib available, Using existing ${name} directory."
  fi
}

scriptdir=$(dirname "$0")

brailledir="${scriptdir}/braille"

thirdpartydir="${scriptdir}/third_party"

brlttydir="${brailledir}/service/jni/brlttywrapper/brltty"
	
liblouisdir="${brailledir}/service/jni/liblouiswrapper/liblouis"

midilibgiturl="https://github.com/LeffelMania/android-midi-lib.git"
midilibdir="${thirdpartydir}/android-midi-lib"

apkname="${brailledir}/brailleback/bin/BrailleBack-debug.apk"

which android > /dev/null || \
  die "Make sure the 'android' tool from the android SDK is in your path"
which ndk-build > /dev/null || \
  die "Make sure the 'ndk-build' tool from the android NDK is in your path"
which ant > /dev/null || \
  die "Make sure the 'ant' tool (version 1.8 or later) is in your path"
if [ ! -f $ANDROID_HOME/extras/android/support/v4/android-support-v4.jar ]; then
  die "Make sure android support library v4 is installed"
fi

ensureMidiLibDependency "android-midi-lib" "${midilibdir}" "${midilibgiturl}"
(cd "${midilibdir}"; mkdir -p ../../braille/libraries/utils/libs; ant jar; cp build/jar/*.jar ../../braille/libraries/utils/libs)

for dir in libraries/compatutils libraries/utils \
  client service brailleback; do
  echo "setup up project dir: ${brailledir}/${dir}"
  android update project -p "${brailledir}/${dir}" --target android-18
  (cd "${brailledir}/${dir}" && ant clean)
done
if [ ! -f ${brailledir}/libraries/compatutils/libs/android-support-v4.jar ]; then
  (mkdir -p ${brailledir}/libraries/compatutils/libs && cp $ANDROID_HOME/extras/android/support/v4/android-support-v4.jar ${brailledir}/libraries/compatutils/libs)
fi
(cd "${brailledir}/service" && ndk-build clean && ndk-build -j16)
(cd "${brailledir}/brailleback" && ant debug)
if [ -f "${apkname}" ]; then
  echo "Successfully built ${apkname}"
  echo "Use the following command to install on a device:"
  echo "  adb install -r ${apkname}"
  echo "(If this fails, try uninstalling BrailleBack from the device first)."
else
  echo "Can't find ${apkname} after build"
fi

