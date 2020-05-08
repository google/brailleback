#!/usr/bin/python

# Copyright 2012 Google Inc.  All Rights Reserved.
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
# USA.

# Helper script that copies files specified on the command line
# to a destination directory.  Files included in any of the specified files
# will also be copied.  The syntax for including files is the one used
# by brltty and liblous:
# include <filename>
# on a line by itself.

"""Copies files and dependencies for bundling as a raw resource."""

from __future__ import print_function
import fnmatch
import getopt
import os.path
import re
import shutil
import sys

# Matches the include statement.
ASSIGN_RE = re.compile(r"\s*assign\s+([^# ]+)(\s+([^# ]+))?")
SUBST_RE = re.compile(r"\\\{([a-zA-Z0-9]+)\}")
INCLUDE_RE = re.compile(r"^\s*include\s+([^# ]+)")


def main():
  try:
    opts, args = getopt.getopt(sys.argv[1:], "nX:",
                               ["dry-run", "exclude="])
  except getopt.GetoptError as msg:
    Usage(msg)
  dryrun = False
  excluded = []
  for opt, val in opts:
    if opt in ("-n", "--dry-run"):
      dryrun = True
    if opt in ("-X", "--exclude"):
      excluded.append(val)
  if len(args) < 3:
    Usage("too few arguments")
  destdir = args[-1]
  if not os.path.isdir(destdir):
    Die("Not a directory: " + destdir)

  tocopy = set()
  for filename in args[:-1]:
    if MatchesAny(os.path.basename(filename), excluded):
      print("Skipping:", filename)
      continue
    tocopy.add(filename)
    tocopy.update(GetDeps(filename))
  for filename in tocopy:
    print("Copying:", filename)
    if not dryrun:
      shutil.copy(filename, destdir)


def Die(why):
  """Print an error message and exit."""
  print(why, file=sys.stderr)
  sys.exit(1)


def Usage(msg=None):
  """Print the usage and exit."""
  usage = ("Usage: %s [-n | --dry-run] [-X | --exclude=GLOB] FILE... DESTDIR" %
           sys.argv[0])
  if msg:
    Die("%s\n\n%s" % (msg, usage))
  else:
    Die(usage)


def GetDeps(filename, assigns=None):
  """Retursn the recursive dependencies of a file as a list."""
  result = []
  if assigns is None:
    assigns = dict()
  else:
    assigns = dict(assigns)
  directory = os.path.dirname(filename)
  f = open(filename, "r")
  try:
    for line in f.xreadlines():
      line = Substitute(assigns, line.rstrip(), filename)
      m = ASSIGN_RE.match(line)
      if m:
        name = m.group(1)
        value = m.group(3)
        if value:
          assigns[name] = value
        else:
          del assigns[name]
      m = INCLUDE_RE.match(line)
      if m:
        name = os.path.join(directory, m.group(1))
        result.append(name)
        result.extend(GetDeps(name, assigns))
  finally:
    f.close()
  return result


def MatchesAny(filename, globs):
  """Returns True if the filename matches any of the globs."""
  for glob in globs:
    if fnmatch.fnmatch(filename, glob):
      return True
  return False


def Substitute(assigns, line, filename):
  while True:
    m = SUBST_RE.search(line)
    if not m:
      return line
    try:
      value = assigns[m.group(1)]
    except KeyError:
      Die("Undefined substitution on line: %s in file %s" % (line, filename))
    line = line[0:m.start(0)] + value + line[m.end(0):]


main()
