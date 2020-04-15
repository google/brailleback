/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * NOTE: This file was generated on Linux using ./cfg-android and manually
 * tweaked to work on Android.
 * Manual modifications:
 *   - Undef ENABLE_SHARED_OBJECTS
 */
#ifndef BRLTTY_INCLUDED_CONFIG
#define BRLTTY_INCLUDED_CONFIG

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/* Define this if the host is big endian. */
/* #undef WORDS_BIGENDIAN */

/* Define this if the compiler doesn't fully support the const keyword. */
/* #undef const */

/* Define this if the compiler doesn't fully support the inline keyword. */
/* #undef inline */

/* Define this if the __alignof__ operator is supported. */
#define HAVE_OPERATOR_ALIGNOF 1
#ifndef HAVE_OPERATOR_ALIGNOF
#define __alignof__(x) 8
#endif /* HAVE_OPERATOR_ALIGNOF */

/* Define this if the printf format attribute is supported. */
#define HAVE_ATTRIBUTE_FORMAT_PRINTF 1

/* Define this if the noreturn attribute is supported. */
#define HAVE_ATTRIBUTE_NORETURN 1

/* Define this if the packed attribute is supported. */
#define HAVE_ATTRIBUTE_PACKED 1

/* Define this if the unused attribute is supported. */
#define HAVE_ATTRIBUTE_UNUSED 1

/* Define this if the header file alloca.h exists. */
#define HAVE_ALLOCA_H 1

/* Define this if the header file getopt.h exists. */
#define HAVE_GETOPT_H 1

/* Define this if the header file glob.h exists. */
/* #undef HAVE_GLOB_H */

/* Define this if the header file langinfo.h exists. */
/* #undef HAVE_LANGINFO_H */

/* Define this if the header file grp.h exists. */
#define HAVE_GRP_H 1

/* Define this if the header file wchar.h exists. */
#define HAVE_WCHAR_H 1

/* Define this if Unicode-based internationalization support is to be
 * included. */
/* #undef HAVE_ICU */

/* Define this if the header file iconv.h exists. */
/* #undef HAVE_ICONV_H */

/* Define this if the header file pwd.h exists. */
#define HAVE_PWD_H 1

/* Define this if the header file regex.h exists. */
#define HAVE_REGEX_H 1

/* Define this if the header file syslog.h exists. */
#define HAVE_SYSLOG_H 1

/* Define this if the header file execinfo.h exists. */
/* #undef HAVE_EXECINFO_H */

/* Define this if the header file sys/file.h exists. */
#define HAVE_SYS_FILE_H 1

/* Define this if the header file sys/socket.h exists. */
#define HAVE_SYS_SOCKET_H 1

/* Define this if the function time exists. */
#define HAVE_TIME 1

/* Define this if the function stime exists. */
/* #undef HAVE_STIME */

/* Define this if the function gettimeofday exists. */
#define HAVE_GETTIMEOFDAY 1

/* Define this if the function settimeofday exists. */
#define HAVE_SETTIMEOFDAY 1

/* Define this if the function clock_gettime exists. */
#define HAVE_CLOCK_GETTIME 1

/* Define this if the function clock_settime exists. */
#define HAVE_CLOCK_SETTIME 1

/* Define this if the function pthread_getname_np exists. */
/* #undef HAVE_PTHREAD_GETNAME_NP */

/* Define this if the function nanosleep exists. */
#define HAVE_NANOSLEEP 1

/* Define this if the function localtime_r is declared. */
#define HAVE_DECL_LOCALTIME_R 1

#ifndef __MINGW32__
/* Define this if the header file sys/poll.h exists. */
#define HAVE_SYS_POLL_H 1

/* Define this if the header file sys/select.h exists. */
#define HAVE_SYS_SELECT_H 1

/* Define this if the function select exists. */
#define HAVE_SELECT 1
#endif /* __MINGW32__ */

/* Define this if the header file signal.h exists. */
#define HAVE_SIGNAL_H 1

/* Define this if the header file sys/signalfd.h exists. */
/* #undef HAVE_SYS_SIGNALFD_H */

/* Define this if the function sigaction exists. */
#define HAVE_SIGACTION 1

/* Define this if the header file sys/wait.h exists,
 * but not for DOS since it wouldn't make sense.
 */
#ifndef __MSDOS__
#define HAVE_SYS_WAIT_H 1
#endif /* __MSDOS__ */

/* Define this if posix threads are supported. */
#define HAVE_POSIX_THREADS 1

/* Define this if thread-local variables are supported. */
#define THREAD_LOCAL __thread

/* Define this if the header file sys/io.h exists. */
/* #undef HAVE_SYS_IO_H */

/* Define this if the header file sys/modem.h exists. */
/* #undef HAVE_SYS_MODEM_H */

/* Define this if the header file machine/speaker.h exists. */
/* #undef HAVE_MACHINE_SPEAKER_H */

/* Define this if the header file dev/speaker/speaker.h exists. */
/* #undef HAVE_DEV_SPEAKER_SPEAKER_H */

/* Define this if the header file linux/vt.h exists. */
#define HAVE_LINUX_VT_H 1

/* Define this if the header file linux/input.h exists. */
#define HAVE_LINUX_INPUT_H 1

/* Define this if the header file linux/uinput.h exists. */
/* #undef HAVE_LINUX_UINPUT_H */

/* Define this if the function mempcpy exists. */
/* #undef HAVE_MEMPCPY */

/* Define this if the function wmempcpy exists. */
/* #undef HAVE_WMEMPCPY */

/* Define this if the function fchdir exists. */
#define HAVE_FCHDIR 1

/* Define this if the function fchmod exists. */
#define HAVE_FCHMOD 1

/* Define this if the function getaddrinfo exists. */
#define HAVE_GETADDRINFO 1

/* Define this if the function getnameinfo exists. */
#define HAVE_GETNAMEINFO 1

/* Define this if the function gai_strerror exists. */
#define HAVE_GAI_STRERROR 1

/* Define this if the type PROCESS_INFORMATION_CLASS exists. */
/* #undef HAVE_PROCESS_INFORMATION_CLASS */

/* Define this if the value ProcessUserModeIOPL exists. */
/* #undef HAVE_PROCESSUSERMODEIOPL */

/* Define this if the function getopt_long exists. */
#define HAVE_GETOPT_LONG 1

/* Define this if the function getpeereid exists. */
/* #undef HAVE_GETPEEREID */

/* Define this if the function getpeerucred exists. */
/* #undef HAVE_GETPEERUCRED */

/* Define this if the function getzoneid exists. */
/* #undef HAVE_GETZONEID */

/* Define this if the function hstrerror exists. */
#define HAVE_HSTRERROR 1

/* Define this if the function realpath exists. */
#define HAVE_REALPATH 1

/* Define this if the function shmget exists. */
/* #undef HAVE_SHMGET */

/* Define this to be a string containing the size of the wchar_t type. */
#define SIZEOF_WCHAR_T_STR "4"

/* Define this if the function shm_open exists. */
/* #undef HAVE_SHM_OPEN */

/* Define this if the function pause exists. */
#define HAVE_PAUSE 1

/* Define this if the function vsyslog exists. */
#define HAVE_VSYSLOG 1

/* Define this if the function shl_load is available. */
/* #undef HAVE_SHL_LOAD */

/* Define this to be a string containing the short name of the package. */
#define PACKAGE_TARNAME "brltty"

/* Define this to be a string containing the full name of the package. */
#define PACKAGE_NAME "BRLTTY"

/* Define this to be a string containing the version of the package. */
#define PACKAGE_VERSION "5.5"

/* Define this to be a string containing the full name and version of the
 * package. */
#define PACKAGE_STRING "BRLTTY 5.5"

/* Define this to be a string containing the URL of the home page of the
 * package. */
#define PACKAGE_URL "http://brltty.com/"

/* Define this to be a string containing the address where bug reports should be
 * sent. */
#define PACKAGE_BUGREPORT "brltty@mielke.cc"

/* Define this if BRLTTY is to be run as init. */
/* #undef INIT_PATH */

/* Define this if standard error is to be redirected to a file. */
/* #undef STDERR_PATH */

/* Define this to be a string containing the path to the locale directory. */
#define LOCALE_DIRECTORY "/usr/share/locale"

/* Define this to be a string containing the path to a writable directory. */
#define WRITABLE_DIRECTORY "/var/run/brltty"

/* Define this to be a string containing the path to the tables directory. */
#define TABLES_DIRECTORY "/etc/brltty"

/* Define this to be a string containing the path to the configuration
 * directory. */
#define CONFIGURATION_DIRECTORY "/etc"

/* Define this to be a string containing the name of the default configuration
 * file. */
#define CONFIGURATION_FILE "brltty.conf"

/* Define this to be a string containing the path to a directory which contains
 * files that can be updated. */
#define UPDATABLE_DIRECTORY "/var/lib/brltty"

/* Define this to be a string containing the name of the default preferences
 * file. */
#define PREFERENCES_FILE "brltty.prefs"

/* Define this to be a string containing the path to the drivers directory. */
#define DRIVERS_DIRECTORY "/lib/brltty"

/* Define this to be a string containing the base module name. */
#define MODULE_NAME "libbrltty"

/* Define this to be a string containing the module extension. */
#define MODULE_EXTENSION "so"

/* Define this to be a string containing the library extension. */
#define LIBRARY_EXTENSION "so"

/* Define this to be a string containing a list of the braille driver codes. */
#define BRAILLE_DRIVER_CODES                                                 \
  "al at bm ce eu fs hm ht hw ir mm md mt np pg pm sk vo bc bd bl bn cb ec " \
  "hd lt mb mn tn ts vd vs"

/* Define this to be a string containing the default braille driver
 * parameters. */
#define BRAILLE_PARAMETERS ""

/* Define this to be a string containing the path to the directory containing
 * the devices. */
#define DEVICE_DIRECTORY "/dev"

/* Define this to be a string containing the path to the default braille
 * device. */
#define BRAILLE_DEVICE "usb:"

/* Define this if the function tcdrain exists. */
/* #undef HAVE_TCDRAIN */

/* Define this to be a string containing the path to the first serial device. */
#define SERIAL_FIRST_DEVICE "ttyS0"

/* Define only one of the following program path packages. */
#define USE_PKG_PGMPATH_NONE 1
/* #undef USE_PKG_PGMPATH_LINUX */
/* #undef USE_PKG_PGMPATH_SOLARIS */
/* #undef USE_PKG_PGMPATH_WINDOWS */

/* Define only one of the following system service packages. */
#define USE_PKG_SERVICE_NONE 1
/* #undef USE_PKG_SERVICE_WINDOWS */

/* Define only one of the following boot parameters packages. */
#define USE_PKG_PARAMS_NONE 1
/* #undef USE_PKG_PARAMS_LINUX */

/* Define only one of the following dynamic loading packages. */
/* #undef USE_PKG_DYNLD_NONE */
#define USE_PKG_DYNLD_DLFCN 1
/* #undef USE_PKG_DYNLD_DYLD */
/* #undef USE_PKG_DYNLD_GRUB */
/* #undef USE_PKG_DYNLD_SHL */
/* #undef USE_PKG_DYNLD_WINDOWS */

/* Define only one of the following character set packages. */
#define USE_PKG_CHARSET_NONE 1
/* #undef USE_PKG_CHARSET_GRUB */
/* #undef USE_PKG_CHARSET_ICONV */
/* #undef USE_PKG_CHARSET_MSDOS */
/* #undef USE_PKG_CHARSET_WINDOWS */

/* Define only one of the following host command packages. */
#define USE_PKG_HOSTCMD_NONE 1
/* #undef USE_PKG_HOSTCMD_UNIX */
/* #undef USE_PKG_HOSTCMD_WINDOWS */

/* Define only one of the following mount point packages. */
#define USE_PKG_MNTPT_NONE 1
/* #undef USE_PKG_MNTPT_MNTENT */
/* #undef USE_PKG_MNTPT_MNTTAB */

/* Define only one of the following mount file system packages. */
#define USE_PKG_MNTFS_NONE 1
/* #undef USE_PKG_MNTFS_LINUX */

/* Define only one of the following keyboard packages. */
/* #undef USE_PKG_KBD_NONE */
#define USE_PKG_KBD_ANDROID 1
/* #undef USE_PKG_KBD_LINUX */

/* Define only one of the following console bell packages. */
#define USE_PKG_BELL_NONE 1
/* #undef USE_PKG_BELL_LINUX */

/* Define only one of the following keyboard LEDs packages. */
#define USE_PKG_LEDS_NONE 1
/* #undef USE_PKG_LEDS_LINUX */

/* Define only one of the following beeper packages. */
#define USE_PKG_BEEP_NONE 1
/* #undef USE_PKG_BEEP_LINUX */
/* #undef USE_PKG_BEEP_MSDOS */
/* #undef USE_PKG_BEEP_SOLARIS */
/* #undef USE_PKG_BEEP_SPKR */
/* #undef USE_PKG_BEEP_WINDOWS */
/* #undef USE_PKG_BEEP_WSKBD */

/* Define only one of the following PCM packages. */
/* #undef USE_PKG_PCM_NONE */
/* #undef USE_PKG_PCM_ALSA */
#define USE_PKG_PCM_ANDROID 1
/* #undef USE_PKG_PCM_AUDIO */
/* #undef USE_PKG_PCM_HPUX */
/* #undef USE_PKG_PCM_OSS */
/* #undef USE_PKG_PCM_QSA */
/* #undef USE_PKG_PCM_WINDOWS */

/* Define only one of the following MIDI packages. */
#define USE_PKG_MIDI_NONE 1
/* #undef USE_PKG_MIDI_ALSA */
/* #undef USE_PKG_MIDI_DARWIN */
/* #undef USE_PKG_MIDI_OSS */
/* #undef USE_PKG_MIDI_WINDOWS */

/* Define only one of the following FM packages. */
#define USE_PKG_FM_NONE 1
/* #undef USE_PKG_FM_ADLIB */

/* Define only one of the following serial I/O packages. */
/* #undef USE_PKG_SERIAL_NONE */
/* #undef USE_PKG_SERIAL_GRUB */
/* #undef USE_PKG_SERIAL_MSDOS */
#define USE_PKG_SERIAL_TERMIOS 1
/* #undef USE_PKG_SERIAL_WINDOWS */

/* Define only one of the following USB I/O packages. */
/* #undef USE_PKG_USB_NONE */
#define USE_PKG_USB_ANDROID 1
/* #undef USE_PKG_USB_DARWIN */
/* #undef USE_PKG_USB_FREEBSD */
/* #undef USE_PKG_USB_GRUB */
/* #undef USE_PKG_USB_KFREEBSD */
/* #undef USE_PKG_USB_LIBUSB */
/* #undef USE_PKG_USB_LIBUSB_1_0 */
/* #undef USE_PKG_USB_LINUX */
/* #undef USE_PKG_USB_NETBSD */
/* #undef USE_PKG_USB_OPENBSD */
/* #undef USE_PKG_USB_SOLARIS */

/* Define only one of the following Bluetooth I/O packages. */
/* #undef USE_PKG_BLUETOOTH_NONE */
#define USE_PKG_BLUETOOTH_ANDROID 1
/* #undef USE_PKG_BLUETOOTH_DARWIN */
/* #undef USE_PKG_BLUETOOTH_LINUX */
/* #undef USE_PKG_BLUETOOTH_WINDOWS */

/* Define only one of the following I/O ports packages. */
#define USE_PKG_PORTS_NONE 1
/* #undef USE_PKG_PORTS_GLIBC */
/* #undef USE_PKG_PORTS_GRUB */
/* #undef USE_PKG_PORTS_KFREEBSD */
/* #undef USE_PKG_PORTS_MSDOS */
/* #undef USE_PKG_PORTS_WINDOWS */

/* Define this if the Polkit authorization manager is to be used. */
/* #undef USE_POLKIT */

/* Define only one of the following curses packages. */
/* #undef HAVE_PKG_CURSES */
/* #undef HAVE_PKG_NCURSES */
/* #undef HAVE_PKG_NCURSESW */
/* #undef HAVE_PKG_PDCURSES */
/* #undef HAVE_PKG_PDCURSESU */
/* #undef HAVE_PKG_PDCURSESW */

/* Define only one of the following Xaw packages. */
/* #undef HAVE_PKG_XAW */
/* #undef HAVE_PKG_XAW3D */
/* #undef HAVE_PKG_NEXTAW */
/* #undef HAVE_PKG_XAWPLUS */
/* #undef HAVE_PKG_XM */

/* Define this if the function addmntent exists. */
/* #undef HAVE_ADDMNTENT */

/* Define this to be a string containing the path to the default text table. */
#define TEXT_TABLE "en-nabcc"

/* Define this to be a string containing the path to the default attributes
 * table. */
#define ATTRIBUTES_TABLE "left_right"

/* Define this to be a string containing a list of the speech driver codes. */
#define SPEECH_DRIVER_CODES "an"

/* Define this to be a string containing the default speech driver
 * parameters. */
#define SPEECH_PARAMETERS ""

/* Define this to be a string containing the default screen driver code. */
#define DEFAULT_SCREEN_DRIVER "an"

/* Define this to be a string containing a list of the screen driver codes. */
#define SCREEN_DRIVER_CODES "an"

/* Define this to be a string containing the default screen driver
 * parameters. */
#define SCREEN_PARAMETERS ""

/* Define this to include contraction table support. */
#define ENABLE_CONTRACTED_BRAILLE 1

/* Define this to include speech synthesizer support. */
#define ENABLE_SPEECH_SUPPORT 1

/* Define this to be a string containing the path to the root of the
 * FestivalLite package. */
/* #undef FLITE_ROOT */

/* Define this to be a string containing the path to the root of the Mikropuhe
 * package. */
/* #undef MIKROPUHE_ROOT */

/* Define this to be a string containing the path to the root of the
 * SpeechDispatcher package. */
/* #undef SPEECHD_ROOT */

/* Define this to be a string containing the path to the root of the Swift
 * package. */
/* #undef SWIFT_ROOT */

/* Define this to be a string containing the path to the root of the Theta
 * package. */
/* #undef THETA_ROOT */

/* Define this to be a string containing the path to the root of the ViaVoice
 * package. */
/* #undef VIAVOICE_ROOT */

/* Define this if internationalization support is to be included. */
/* #undef ENABLE_I18N_SUPPORT */

/* Define this if the application programming interface is to be included. */
#define ENABLE_API 1

/* Define this to be a string containing the default application programming
 * interface parameters. */
#define API_PARAMETERS ""

/* Define this if shared object support is to be included. */
/* undef ENABLE_SHARED_OBJECTS */

/* Define this if the header file legacy/dev/usb/usb.h exists. */
/* #undef HAVE_LEGACY_DEV_USB_USB_H */

/* Define this if the bluetooth library is available. */
/* #undef HAVE_LIBBLUETOOTH */

/* Define this if HP-UX audio support is available. */
/* #undef HAVE_HPUX_AUDIO */

/* Define this if GPM is to be used. */
/* #undef HAVE_LIBGPM */

/* Define this if X is not available. */
/* #undef X_DISPLAY_MISSING */

/* Define this if the header file X11/keysym.h exists. */
/* #undef HAVE_X11_KEYSYM_H */

/* Define this if the header file X11/extensions/XTest.h exists. */
/* #undef HAVE_X11_EXTENSIONS_XTEST_H */

/* Define this if the header file X11/extensions/XKB.h exists. */
/* #undef HAVE_X11_EXTENSIONS_XKB_H */

/* Define this if the function atspi_get_a11y_bus exists in atspi2. */
/* #undef HAVE_ATSPI_GET_A11Y_BUS */

/* Define this if the header file sdkddkver.h exists. */
/* #undef HAVE_SDKDDKVER_H */

/* Define this to be a string containing the subdirectory for text tables. */
#define TEXT_TABLES_SUBDIRECTORY "Text"

/* Define this to be a string containing the subdirectory for attributes
 * tables. */
#define ATTRIBUTES_TABLES_SUBDIRECTORY "Attributes"

/* Define this to be a string containing the subdirectory for contraction
 * tables. */
#define CONTRACTION_TABLES_SUBDIRECTORY "Contraction"

/* Define this to be a string containing the subdirectory for keyboard
 * tables. */
#define KEYBOARD_TABLES_SUBDIRECTORY "Keyboard"

/* Define this to be a string containing the subdirectory for input tables. */
#define INPUT_TABLES_SUBDIRECTORY "Input"

/* Define this to be a string containing the extension for text tables. */
#define TEXT_TABLE_EXTENSION ".ttb"

/* Define this to be a string containing the extension for text subtables. */
#define TEXT_SUBTABLE_EXTENSION ".tti"

/* Define this to be a string containing the extension for attributes
 * tables. */
#define ATTRIBUTES_TABLE_EXTENSION ".atb"

/* Define this to be a string containing the extension for attributes
 * subtables. */
#define ATTRIBUTES_SUBTABLE_EXTENSION ".ati"

/* Define this to be a string containing the extension for contraction
 * tables. */
#define CONTRACTION_TABLE_EXTENSION ".ctb"

/* Define this to be a string containing the extension for contraction
 * subtables. */
#define CONTRACTION_SUBTABLE_EXTENSION ".cti"

/* Define this to be a string containing the extension for key tables. */
#define KEY_TABLE_EXTENSION ".ktb"

/* Define this to be a string containing the extension for key subtables. */
#define KEY_SUBTABLE_EXTENSION ".kti"

/* Define this to be a string containing the extension for key help files. */
#define KEY_HELP_EXTENSION ".txt"

/* Define this to be a string containing the subdirectory for profiles. */
#define PROFILES_SUBDIRECTORY "Profiles"

/* Define this to be a string containing the extension for language profiles. */
#define LANGUAGE_PROFILE_EXTENSION ".lpf"

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CONFIG */
