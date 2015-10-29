# Copyright 2012 Google Inc.
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
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

WRAPPER_PATH := $(call my-dir)
LOCAL_PATH := $(WRAPPER_PATH)
BRLTTY_PATH := $(WRAPPER_PATH)/brltty

include $(WRAPPER_PATH)/build/driver.mk

# Uncomment the second line below and comment out the first one
# to get a smaller binary with less symbols.
VISIBILITY=
#VISIBILITY=-fvisibility=hidden

#----------------------------------------------------------------
# List of brltty drivers that are included.  If adding a new driver,
# include the directory name of the driver in the below list.

$(call build-braille-drivers,\
	Voyager \
	EuroBraille \
	FreedomScientific \
	HumanWare \
	Baum \
	Papenmeier \
	HIMS \
	Alva \
	Seika \
	HandyTech \
       )

#----------------------------------------------------------------
# brlttywrap

include $(CLEAR_VARS)

LOCAL_PATH := $(WRAPPER_PATH)
LOCAL_MODULE    := brlttywrap
LOCAL_LDFLAGS := $(BRLTTY_LDFLAGS)
LOCAL_LDLIBS := -llog
LOCAL_C_INCLUDES := $(LOCAL_PATH)/.. $(BRLTTY_PATH)/Programs
LOCAL_SRC_FILES := BrlttyWrapper.c
LOCAL_WHOLE_STATIC_LIBRARIES := libbrltty-android

include $(BUILD_SHARED_LIBRARY)

#----------------------------------------------------------------
# libbrltty-android

include $(CLEAR_VARS)

LOCAL_PATH := $(WRAPPER_PATH)

LOCAL_C_INCLUDES := $(BRLTTY_PATH) \
	$(BRLTTY_PATH)/Programs \
	$(LOCAL_PATH)

LOCAL_CFLAGS+=-DHAVE_CONFIG_H $(VISIBILITY)
LOCAL_CFLAGS+=-D__ANDROID__

LOCAL_SRC_FILES:= \
	libbrltty.c \
	bluetooth_android.c \
	sys_android.c

LOCAL_MODULE := brltty-android
LOCAL_WHOLE_STATIC_LIBRARIES := libbrltty
include $(BUILD_STATIC_LIBRARY)

#----------------------------------------------------------------
# libbrltty
include $(CLEAR_VARS)

LOCAL_PATH := $(BRLTTY_PATH)

LOCAL_C_INCLUDES:= $(BRLTTY_PATH) \
	$(BRLTTY_PATH)/Programs \
	$(WRAPPER_PATH)

LOCAL_CFLAGS+=-DHAVE_CONFIG_H $(VISIBILITY)
LOCAL_CFLAGS+=-D__ANDROID__

LOCAL_SRC_FILES:= \
	Programs/cmd.c \
	Programs/charset.c \
	Programs/charset_none.c \
	Programs/lock.c \
	Programs/drivers.c \
	Programs/driver.c \
	Programs/ttb_translate.c \
	Programs/ttb_compile.c \
	Programs/ttb_native.c

# Base objects
LOCAL_SRC_FILES+= \
	Programs/log.c \
	Programs/file.c \
	Programs/device.c \
	Programs/parse.c \
	Programs/timing.c \
	Programs/io_misc.c

# Braille objects
LOCAL_SRC_FILES+= \
	Programs/brl.c

# IO objects
LOCAL_SRC_FILES+= \
	Programs/io_generic.c

# Bluetooth objects
LOCAL_SRC_FILES+= \
	Programs/bluetooth.c \

# Other, not sure where they come from.
LOCAL_SRC_FILES+= \
	Programs/unicode.c \
	Programs/queue.c \
	Programs/serial.c \
	Programs/serial_none.c \
	Programs/usb.c \
	Programs/usb_none.c \
	Programs/usb_hid.c \
	Programs/usb_serial.c \
	Programs/ktb_translate.c \
	Programs/ktb_compile.c \
	Programs/async.c \
	Programs/datafile.c \
	Programs/dataarea.c \
	Programs/touch.c \
	Programs/hidkeys.c

LOCAL_MODULE := brltty
LOCAL_WHOLE_STATIC_LIBRARIES := $(DRIVER_MODULES)
include $(BUILD_STATIC_LIBRARY)
