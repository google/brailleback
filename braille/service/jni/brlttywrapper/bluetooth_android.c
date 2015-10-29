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
 * Bluetooth functionality that works on the Android NDK.
 */

#include "prologue.h"

#include "bluetooth_android.h"

#include <errno.h>

#include "io_bluetooth.h"
#include "bluetooth_internal.h"
#include "log.h"

static BluetoothAndroidConnection* globalConnection = NULL;

struct BluetoothConnectionExtensionStruct {
  BluetoothAndroidConnection* conn;
};

void
bluetoothAndroidSetConnection(BluetoothAndroidConnection* conn) {
  globalConnection = conn;
}


//////////////////////////////////////////////////////////////////////
// Implementation of system-specific bluetooth functions required
// by brltty I/O functions.
//////////////////////////////////////////////////////////////////////
BluetoothConnectionExtension *
bthConnect (uint64_t bda, uint8_t channel) {
  BluetoothConnectionExtension* bcx = NULL;
  if (!globalConnection) {
    logMessage(LOG_ERR, "Opening bluetooth without an andorid bluetooth "
               "conection");
    goto out;
  }
  if ((bcx = malloc(sizeof(*bcx))) == NULL) {
    logMessage(LOG_ERR, "Can't allocate android bluetooth extension struct");
    goto out;
  }
  bcx->conn = globalConnection;
out:
  return bcx;
}

void
bthDisconnect (BluetoothConnectionExtension *bcx) {
  if (bcx->conn != globalConnection) {
    logMessage(LOG_ERR, "Android bluetooth closed after a new connection "
               "was stablished");
  }
  free(bcx);
}

int
bthAwaitInput (BluetoothConnection *connection, int milliseconds) {
  BluetoothAndroidConnection *conn = connection->extension->conn;
  return awaitInput(conn->read_fd, milliseconds);
}

ssize_t
bthReadData (
  BluetoothConnection *connection, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  BluetoothAndroidConnection *conn = connection->extension->conn;
  return readData(conn->read_fd, buffer, size, initialTimeout,
                  subsequentTimeout);
}

ssize_t
bthWriteData (BluetoothConnection *connection, const void *buffer, size_t size) {
  BluetoothAndroidConnection *conn = connection->extension->conn;
  return (*conn->writeData)(conn, buffer, size);
}
