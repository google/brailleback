# Copyright 2012 Google Inc. All Rights Reserved.
# Author: plundblad@google.com (Peter Lundblad)
#
# Description:
#   BRLTTY is a background process (daemon) providing access to the Linux/Unix
#   console (when in text mode) for a blind person using a refreshable braille
#   display.  It contains drivers for many refreshable braille displays.

load("//mobile/build:build_defs.bzl", "android_only")
load("//third_party/brltty:allowed_users.bzl", "BRLTTY_ALLOWED_USERS")

licenses(["restricted"])  # GPL v2, parts LGPL v2.1

package(
    # DO NOT add to the whitelist here; instead edit allowed_users.bzl.
    default_visibility = BRLTTY_ALLOWED_USERS,
)

exports_files(["LICENSE"])

filegroup(
    name = "tables_brailleback",
    srcs = glob([
        "Tables.brailleback/**/*.ktb",
        "Tables.brailleback/**/*.kti",
    ]),
)

# Library including only files used by BrailleBack and TalkBack, for android.
# blaze build --config=android  //third_party/brltty:brltty_c_lib
# See http://go/ndk-in-google3
cc_library(
    name = "brltty_c_lib",
    srcs = [":src_files"],
    copts = [
        "-Ithird_party/brltty/for_talkback",
        "-Ithird_party/brltty/Headers",
        "-Ithird_party/brltty/Programs",
        "-Wno-format",  # Code uses ssize_t/int in place of long.
    ] + select({
        "//tools/cc_target_os:android": ["-std=c99"],
        "//conditions:default": [],
    }),
    defines = [
        "HAVE_CONFIG_H",
    ] + select({
        "//tools/cc_target_os:android": ["__ANDROID__"],
        "//conditions:default": [],
    }),
    hdrs_check = "strict",
    linkopts = [
        "-no-canonical-prefixes",
    ],
    nocopts = "-Wvla",  # Code uses variable length arrays.
    textual_hdrs = [":header_files"],  # Code relies on caller to include headers in correct order.
)

filegroup(
    name = "header_files",
    srcs = glob([
        "for_talkback/*.h",
        "Headers/*.h",
        "Programs/*.h",
    ]),
)

# C files used by BrailleBack and TalkBack.
filegroup(
    name = "src_files",
    srcs = [
        "Programs/cmd.c",
        "Programs/charset.c",
        "Programs/charset_none.c",
        "Programs/lock.c",
        "Programs/drivers.c",
        "Programs/driver.c",
        "Programs/ttb_translate.c",
        "Programs/ttb_compile.c",
        "Programs/ttb_native.c",

        # Base objects
        "Programs/addresses.c",
        "Programs/dynld_none.c",
        "Programs/log.c",
        "Programs/file.c",
        "Programs/device.c",
        "Programs/parse.c",
        "Programs/timing.c",
        "Programs/variables.c",
        "Programs/thread.c",
        "Programs/report.c",

        # Braille objects
        "Programs/brl.c",
        "Programs/brl_base.c",
        "Programs/brl_driver.c",
        "Programs/brl_utils.c",

        # IO objects
        "Programs/io_misc.c",
        "Programs/gio.c",
        "Programs/gio_null.c",
        "Programs/gio_serial.c",
        "Programs/gio_usb.c",
        "Programs/gio_bluetooth.c",

        # Bluetooth objects
        "Programs/bluetooth.c",
        "Programs/bluetooth_names.c",

        # Other
        "Programs/unicode.c",
        "Programs/queue.c",
        "Programs/serial.c",
        "Programs/serial_none.c",
        "Programs/usb.c",
        "Programs/usb_none.c",
        "Programs/usb_hid.c",
        "Programs/usb_serial.c",
        "Programs/usb_ftdi.c",
        "Programs/usb_belkin.c",
        "Programs/usb_cp2101.c",
        "Programs/usb_cp2110.c",
        "Programs/usb_cdc_acm.c",
        "Programs/usb_adapters.c",
        "Programs/ktb_translate.c",
        "Programs/ktb_compile.c",
        "Programs/ktb_list.c",
        "Programs/ktb_cmds.c",
        "Programs/async_io.c",
        "Programs/async_alarm.c",
        "Programs/async_event.c",
        "Programs/async_handle.c",
        "Programs/async_wait.c",
        "Programs/async_task.c",
        "Programs/async_data.c",
        "Programs/async_signal.c",
        "Programs/datafile.c",
        "Programs/dataarea.c",
        "Programs/cmd_queue.c",
        "Programs/hidkeys.c",
        "Programs/system_java.c",
        "Programs/log_history.c",
    ],
)

##############################################################################
# Braille driver builds, derived from Drivers/Braille/*/Makefile.in
# ADD_DEVICE_SUPPORT

DRIVER_COPTS = [
    "-Ithird_party/brltty/for_talkback",
    "-Ithird_party/brltty/Headers",
    "-Ithird_party/brltty/Programs",
    "-Wno-initializer-overrides",  # Code uses initializer overrides.
] + android_only("-std=c99")

DRIVER_NOCOPTS = "-Wvla"  # Code uses variable length arrays.

DRIVER_DEFINES = ["HAVE_CONFIG_H"] + android_only("__ANDROID__")

DRIVER_VISIBILITY = BRLTTY_ALLOWED_USERS

cc_library(
    name = "driver_al",
    srcs = ["Drivers/Braille/Alva/braille.c"],
    hdrs = ["Drivers/Braille/Alva/brldefs-al.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=al",
        "DRIVER_NAME=Alva",
        "DRIVER_COMMENT=\\\"ABT(3nn),\\ Delphi(4nn),\\ Satellite(5nn),\\ Braille\\ System\\ 40,\\ Braille\\ Controller\\ 640/680,\\ Easy\\ Link\\ 12\\\"",
        "DRIVER_VERSION=\\\"2.2\\\"",
        "DRIVER_DEVELOPERS=\\\"Nicolas\\ Pitre\\ <nico@fluxnic.net>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"] + ["Drivers/Braille/Alva/braille.h"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_bm",
    srcs = ["Drivers/Braille/Baum/braille.c"],
    hdrs = ["Drivers/Braille/Baum/brldefs-bm.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=bm",
        "DRIVER_NAME=Baum",
        "DRIVER_COMMENT=\\\"BrailleConnect\\ 12/24/32/40/64/80,\\ Brailliant\\ 24/32/40/64/80,\\ Conny\\ 12,\\ DM80\\ Plus,\\ EcoVario\\ 24/32/40/64/80,\\ Inka,\\ Orbit\\ 20,\\ PocketVario\\ 24,\\ Pronto!\\ V3\\ 18/40,\\ Pronto!\\ V4\\ 18/40,\\ RBT\\ 40/80,\\ Refreshabraille\\ 18,\\ SuperVario\\ 32/40/64/80,\\ Vario\\ 40/80,\\ VarioConnect\\ 12/24/32/40/64/80,\\ VarioPro\\ 40/64/80,\\ VarioUltra\\ 20/32/40\\\"",
        "DRIVER_VERSION=\\\"\\\"",
        "DRIVER_DEVELOPERS=\\\"Dave\\ Mielke\\ <dave@mielke.cc>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_eu",
    srcs = [
        "Drivers/Braille/EuroBraille/eu_braille.c",
        "Drivers/Braille/EuroBraille/eu_clio.c",
        "Drivers/Braille/EuroBraille/eu_esysiris.c",
    ],
    hdrs = [
        "Drivers/Braille/EuroBraille/brldefs-eu.h",
        "Drivers/Braille/EuroBraille/eu_protocoldef.h",
    ],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=eu",
        "DRIVER_NAME=EuroBraille",
        "DRIVER_COMMENT=\\\"AzerBraille,\\ Clio,\\ Esys,\\ Iris,\\ NoteBraille,\\ Scriba\\\"",
        "DRIVER_VERSION=\\\"2.0\\\"",
        "DRIVER_DEVELOPERS=\\\"Yannick\\ PLASSIARD\\ <yan@mistigri.org>,\\ Olivier\\ BERT\\ <obert01@mistigri.org>,\\ Nicolas\\ PITRE\\ <nico@fluxnic.net>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"] + ["Drivers/Braille/EuroBraille/eu_protocol.h"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_fs",
    srcs = ["Drivers/Braille/FreedomScientific/braille.c"],
    hdrs = ["Drivers/Braille/FreedomScientific/brldefs-fs.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=fs",
        "DRIVER_NAME=FreedomScientific",
        "DRIVER_COMMENT=\\\"Focus\\ 1\\ 44/70/84,\\ Focus\\ 2\\ 40/80,\\ Focus\\ Blue\\ 14/40,\\ PAC\\ Mate\\ 20/40\\\"",
        "DRIVER_VERSION=\\\"\\\"",
        "DRIVER_DEVELOPERS=\\\"Dave\\ Mielke\\ <dave@mielke.cc>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_hm",
    srcs = ["Drivers/Braille/HIMS/braille.c"],
    hdrs = ["Drivers/Braille/HIMS/brldefs-hm.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=hm",
        "DRIVER_NAME=HIMS",
        "DRIVER_COMMENT=\\\"Braille\\ Sense,\\ SyncBraille,\\ Braille\\ Edge,\\ Smart\\ Beetle\\\"",
        "DRIVER_VERSION=\\\"\\\"",
        "DRIVER_DEVELOPERS=\\\"Dave\\ Mielke\\ <dave@mielke.cc>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_ht",
    srcs = ["Drivers/Braille/HandyTech/braille.c"],
    hdrs = ["Drivers/Braille/HandyTech/brldefs-ht.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=ht",
        "DRIVER_NAME=HandyTech",
        "DRIVER_COMMENT=\\\"Modular\\ 20/40/80,\\ Modular\\ Evolution\\ 64/88,\\ Modular\\ Connect\\ 88,\\ Active\\ Braille,\\ Active\\ Star\\ 40,\\ Actilino,\\ Basic\\ Braille\\ 16/20/32/40/48/64/80,\\ Braillino,\\ Braille\\ Wave,\\ Easy\\ Braille,\\ Braille\\ Star\\ 40/80,\\ Connect\\ Braille\\ 40,\\ Bookworm\\\"",
        "DRIVER_VERSION=\\\"0.6\\\"",
        "DRIVER_DEVELOPERS=\\\"Andreas\\ Gross\\ <andi.gross@gmx.de>,\\ Dave\\ Mielke\\ <dave@mielke.cc>,\\ Mario\\ Lang\\ <mlang@delysid.org>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_hw",
    srcs = ["Drivers/Braille/HumanWare/braille.c"],
    hdrs = ["Drivers/Braille/HumanWare/brldefs-hw.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=hw",
        "DRIVER_NAME=HumanWare",
        "DRIVER_COMMENT=\\\"Brailliant\\ 32/40/80\\ bi\\\"",
        "DRIVER_VERSION=\\\"\\\"",
        "DRIVER_DEVELOPERS=\\\"Dave\\ Mielke\\ <dave@mielke.cc>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_pm",
    srcs = ["Drivers/Braille/Papenmeier/braille.c"],
    hdrs = ["Drivers/Braille/Papenmeier/brldefs-pm.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=pm",
        "DRIVER_NAME=Papenmeier",
        "DRIVER_COMMENT=\\\"Compact\\ 486,\\ Compact/Tiny,\\ IB\\ 80\\ CR\\ Soft,\\ 2D\\ Lite\\ (plus),\\ 2D\\ Screen\\ Soft,\\ EL\\ 80,\\ EL\\ 2D\\ 40/66/80,\\ EL\\ 40/66/70/80\\ S,\\ EL\\ 40/60/80\\ C,\\ EL\\ 2D\\ 80\\ S,\\ EL\\ 40\\ P,\\ EL\\ 80\\ II,\\ Elba\\ 20/32,\\ Trio\\ 40/Elba20/Elba32,\\ Live\\ 20/40\\\"",
        "DRIVER_VERSION=\\\"\\\"",
        "DRIVER_DEVELOPERS=\\\"August\\ H\303\266randl\\ <august.hoerandl@gmx.at>,\\ Heimo\\ Sch\303\266n\\ <heimo.schoen@gmx.at>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"] + ["Drivers/Braille/Papenmeier/models.h"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_sk",
    srcs = ["Drivers/Braille/Seika/braille.c"],
    hdrs = ["Drivers/Braille/Seika/brldefs-sk.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=sk",
        "DRIVER_NAME=Seika",
        "DRIVER_COMMENT=\\\"3/4/5\\ (40),\\ 80,\\ Mini\\ (16)\\\"",
        "DRIVER_VERSION=\\\"\\\"",
        "DRIVER_DEVELOPERS=\\\"Dave\\ Mielke\\ <dave@mielke.cc>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"],
    visibility = DRIVER_VISIBILITY,
)

cc_library(
    name = "driver_vo",
    srcs = ["Drivers/Braille/Voyager/braille.c"],
    hdrs = ["Drivers/Braille/Voyager/brldefs-vo.h"],
    copts = DRIVER_COPTS,
    defines = DRIVER_DEFINES + [
        "DRIVER_CODE=vo",
        "DRIVER_NAME=Voyager",
        "DRIVER_COMMENT=\\\"44/70,\\ Part232\\ (serial\\ adapter),\\ BraillePen/EasyLink\\\"",
        "DRIVER_VERSION=\\\"0.3\\ (June\\ 2009)\\\"",
        "DRIVER_DEVELOPERS=\\\"St\303\251phane\\ Doyon\\ <s.doyon@videotron.ca>\\\"",
    ],
    nocopts = DRIVER_NOCOPTS,
    textual_hdrs = [":header_files"],
    visibility = DRIVER_VISIBILITY,
)
