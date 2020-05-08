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

srcdir="$scriptdir/keytables"
if [ ! -d $srcdir ]; then
  echo "Can't find original table directory $srcdir"
  exit 1
fi

dstdir="$basedir/res/raw"
mkdir -p $dstdir
cd $srcdir
echo "Creating archive..."
zip -9 -q -r "../$dstdir/keytables.zip" ./**/*.k?? ./*.k??

echo "Keyboard table archive successfully created."
