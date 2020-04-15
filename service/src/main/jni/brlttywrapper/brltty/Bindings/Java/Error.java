/*
 * libbrlapi - A library providing access to braille terminals for applications.
 *
 * Copyright (C) 2006-2018 by
 *   Samuel Thibault <Samuel.Thibault@ens-lyon.org>
 *   Sébastien Hinderer <Sebastien.Hinderer@ens-lyon.org>
 *
 * libbrlapi comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.com/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

package org.a11y.BrlAPI;

public class Error extends java.lang.Error {
  static final long serialVersionUID = 0;
  int brlerrno;
  int libcerrno;
  int gaierrno;
  String errfun;

  public final native String toString();

  public Error(int brlerrno, int libcerrno, int gaierrno, String errfun) {
    this.brlerrno = brlerrno;
    this.libcerrno = libcerrno;
    this.gaierrno = gaierrno;
    this.errfun = errfun;
  }

  public static final int SUCCESS = 0; /* Success */
  public static final int NOMEM = 1; /* Not enough memory */
  public static final int TTYBUSY = 2; /* Already a connection running in this tty */
  public static final int DEVICEBUSY = 3; /* Already a connection using RAW mode */
  public static final int UNKNOWN_INSTRUCTION = 4; /* Not implemented in protocol */
  public static final int ILLEGAL_INSTRUCTION = 5; /* Forbiden in current mode */
  public static final int INVALID_PARAMETER = 6; /* Out of range or have no sense */
  public static final int INVALID_PACKET = 7; /* Invalid size */
  public static final int CONNREFUSED = 8; /* Connection refused */
  public static final int OPNOTSUPP = 9; /* Operation not supported */
  public static final int GAIERR = 10; /* Getaddrinfo error */
  public static final int LIBCERR = 11; /* Libc error */
  public static final int UNKNOWNTTY = 12; /* Couldn't find out the tty number */
  public static final int PROTOCOL_VERSION = 13; /* Bad protocol version */
  public static final int EOF = 14; /* Unexpected end of file */
  public static final int EMPTYKEY = 15; /* Too many levels of recursion */
  public static final int DRIVERERROR = 16; /* Packet returned by driver too large */
}
