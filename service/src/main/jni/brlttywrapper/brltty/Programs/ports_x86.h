/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2018 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
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

unsigned char readPort1(unsigned short int port) {
  unsigned char v;
  __asm__ __volatile__("inb %w1,%0" : "=a"(v) : "Nd"(port));
  return v;
}

void writePort1(unsigned short int port, unsigned char value) {
  __asm__ __volatile__("outb %b0,%w1" : : "a"(value), "Nd"(port));
}