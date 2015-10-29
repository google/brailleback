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

WRAPPER_PATH := $(call my-dir)
LOCAL_PATH := $(WRAPPER_PATH)
LIBLOUIS_PATH := $(WRAPPER_PATH)/liblouis

#----------------------------------------------------------------
# liblouiswrap

include $(CLEAR_VARS)

LOCAL_PATH := $(WRAPPER_PATH)
LOCAL_LDFLAGS := $(LIBLOUIS_LDFLAGS)
LOCAL_LDLIBS := -llog -landroid
LOCAL_MODULE := louiswrap
LOCAL_SRC_FILES := LibLouisWrapper.c
LOCAL_C_INCLUDES := $(WRAPPER_PATH)/.. $(LIBLOUIS_PATH)
LOCAL_WHOLE_STATIC_LIBRARIES := liblouis

include $(BUILD_SHARED_LIBRARY)

#----------------------------------------------------------------
# liblouis

include $(CLEAR_VARS)

LOCAL_PATH := $(LIBLOUIS_PATH)
LOCAL_LDLIBS := -llog -landroid
LOCAL_MODULE := louis
LOCAL_SRC_FILES := \
	liblouis/compileTranslationTable.c \
	liblouis/logging.c \
	liblouis/lou_backTranslateString.c \
	liblouis/lou_translateString.c \
	liblouis/wrappers.c
LOCAL_C_INCLUDES := $(WRAPPER_PATH)/.. $(WRAPPER_PATH)

include $(BUILD_STATIC_LIBRARY)
