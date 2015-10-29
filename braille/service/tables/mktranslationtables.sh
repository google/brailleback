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

set -e

scriptdir="$(dirname $0)"
basedir="$scriptdir/.."

tempdir="$basedir/translationtables-$$"
srcdir="$basedir/jni/liblouiswrapper/liblouis/tables"
if [ -d "$tempdir" ]; then
  echo "Please remove $tempdir"
  exit 1
fi
if [ ! -d $srcdir ]; then
  echo "Can't find original table directory $srcdir"
  exit 1
fi

dstdir="$basedir/res/raw"
mkdir -p $dstdir

function cleanup() {
  rm -rf "$tempdir"
}

mkdir -p "$tempdir/liblouis/tables"
trap cleanup exit

# Use the files refered in the table list resource file.  This depends on the
# lexical format of this file, notably the fileName attributes being
# on the same lines as their values and the values being quoted with
# double quotes.
tablefiles=$(egrep 'fileName ?=' $basedir/res/xml/tablelist.xml \
  |sed -re "s#^.*fileName ?= ?\"([^\"]+)\".*\$#${srcdir}/\1#")

echo "Copying translation tables..."
$scriptdir/copywithdeps.py $tablefiles $tempdir/liblouis/tables
echo "Creating archive..."
(cd "$tempdir" && zip -9 translationtables.zip liblouis/tables/*)
mv "$tempdir/translationtables.zip" "$dstdir"

echo "Translation table archive successfully created."
