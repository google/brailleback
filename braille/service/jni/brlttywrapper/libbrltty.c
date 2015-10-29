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
 * This is a library to expose a brlapi-like interface that can be linked
 * into another binary.  The intended use is on Android, compiled under
 * the NDK, meaning that some sytem and I/O abstractons must be
 * provided by the user of the library.
 */

#include "prologue.h"

#include "libbrltty.h"
#include "brl.h"
#include "cmd.h"
#include "file.h"
#include "ktb.h"
#include "ktbdefs.h"
#include "ktb_internal.h"
#include "ktb_inspect.h"
#include "log.h"
#include "parse.h"
#include "touch.h"

// TODO: Consider making these adjustable by the user
/* Initial delay before the first autorepeat and as long press timeout. */
#define AUTOREPEAT_INITIAL_DELAY_MS 500
/* Interval between autorepeats. */
#define AUTOREPEAT_INTERVAL_MS 300

// textStart and textCount are taken from Programs/brltty.c. We declare them
// here so we don't have to include the entire brltty.c file.
unsigned int textStart = 0;
unsigned int textCount;

/*
 * The global variable 'braille' is the driver struct with vtable etc.  It is
 * declared in brl.h and defined in brl.c.  It is used in this file to be
 * consistent with the rest of brltty, meaning that we can only have one
 * driver loaded per address space.  Therefore, we declare the rest of the
 * variables we need static for simplicity.
 */

/*
 * Set to non-NULL when shared objects are used.
 */
static void* brailleSharedObject = NULL;

/*
 * Display struct, containing data for a particular display
 * (dimensions, the display buffer etc).
 */
static BrailleDisplay brailleDisplay;
static RepeatState repeatState;

/*
 * Array of driver-specific parameters.
 */
static char** driverParameters = NULL;

/*
 * This is here to satisfy a dependency in a driver.
 * Propagating a message from the driver to the user's display is
 * tricky for many reasons.  We ignore the message since it shouldn't be used
 * often.
 */
int message (const char *mode, const char *text, short flags) {
  // Ignore.
}

static int
createEmptyDriverParameters (void);

static void
freeDriverParameters(void);

static int
compileKeys(const char* tablesDir);

static char *
getKeyTablePath(const char *tablesDir);

static int
listKeyContext(const KeyContext *context, const KeyTable* keyTable,
               KeyMapEntryCallback callback, void* data);
static int
listKeyBinding(const KeyBinding *binding, const KeyTable* keyTable,
               KeyMapEntryCallback callback, void* data);
static const char*
findKeyName(const KeyTable* keyTable, const KeyValue* value);

int
brltty_initialize (const char* driverCode, const char* brailleDevice,
                   const char* tablesDir) {
  int ret = 0;
  systemLogLevel = LOG_DEBUG;

  logMessage(LOG_DEBUG, "Loading braille driver %s", driverCode);
  braille = loadBrailleDriver(driverCode, &brailleSharedObject, NULL);
  if (!braille) {
    logMessage(LOG_ERR, "Couldn't load braille driver %s.", driverCode);
    goto out;
  }

  logMessage(LOG_DEBUG, "Initializing braille driver");
  initializeBrailleDisplay(&brailleDisplay);

  logMessage(LOG_DEBUG, "Identifying braille driver");
  identifyBrailleDriver(braille, 1);

  if (!createEmptyDriverParameters()) {
    goto unloadDriver;
  }

  logMessage(LOG_DEBUG, "Constructing braille driver");
  if (!braille->construct(&brailleDisplay, driverParameters, brailleDevice)) {
    logMessage(LOG_ERR, "Couldn't initialize braille driver %s on device %s",
               driverCode, driverCode);
    goto freeParameters;
  }

  if (brltty_getTextCells() > BRLTTY_MAX_TEXT_CELLS) {
    logMessage(LOG_ERR, "Unsupported display size: %d",
               brltty_getTextCells());
    goto destructBraille;
  }

  if (!compileKeys(tablesDir)) {
    goto destructBraille;
  }

  textCount = brltty_getTextCells();

  // TODO: Should set bufferResized to catch buffer size changes if we want to
  // singal those to the screen reader, which is probably useful.
  logMessage(LOG_DEBUG, "Allocating braille buffer");
  if (!ensureBrailleBuffer(&brailleDisplay, LOG_INFO)) {
    logMessage(LOG_ERR, "Couldn't allocate braille buffer");
    goto destructBraille;
  }

  resetRepeatState(&repeatState);

  logMessage(LOG_NOTICE, "Successfully initialized braille driver "
             "%s on device %s", driverCode, brailleDevice);
  ret = 1;
  goto out;

destructBraille:
  braille->destruct(&brailleDisplay);

freeParameters:
  freeDriverParameters();

unloadDriver:
  /* No unloading yet. */
  braille = NULL;

out:
  return ret;
}

int
brltty_destroy(void) {
  if (braille == NULL) {
    logMessage(LOG_CRIT, "Double destruction of braille driver");
    return;
  }
  braille->destruct(&brailleDisplay);
  freeDriverParameters();
  braille = NULL;
}

/*
 * Handles long press for CMD_ROUTE, updating the repeat state and
 * *cmd as appropriate.  Returns non-zero if the command was handled
 * by this function and zero if autorepeat should be handling the
 * current state and command.
 */
static int
handleLongPress(int* cmd) {
  TimeValue now;
  getCurrentTime(&now);

  /* Are we in the middle of a CMD_ROUTE? */
  if ((repeatState.command & BRL_MSK_BLK) == BRL_BLK_ROUTE
      && repeatState.timeout != 0) {
    /* Periodic check for long press timeout (or spurious read). */
    if (*cmd == EOF) {
      if (millisecondsBetween(&repeatState.time, &now) > repeatState.timeout) {
        /* Emit the long press and reset the repeat state to not
         * cause any further commands from this key press.
         */
        *cmd = repeatState.command | BRLTTY_ROUTE_ARG_FLG_LONG_PRESS;
        resetRepeatState(&repeatState);
      }
      return 1;
    }

    /* If we get the same command after a key press (without repeat
     * flags), the key was released before the timeout, so this
     * is a 'short press'.
     */
    if (*cmd == repeatState.command) {
      resetRepeatState(&repeatState);
      return 1;
    }
    // We were handling a routing key and got a different command.  Reset the
    // repeat state and let the autorepeat code handle the new keystroke.
    resetRepeatState(&repeatState);
    return 0;
  } else if (((*cmd & BRL_MSK_BLK) == BRL_BLK_ROUTE) &&
             !(*cmd & BRL_FLG_REPEAT_INITIAL)) {
    /* Not currently handling a route key press. */
    if ((*cmd & BRL_FLG_REPEAT_DELAY) != 0) {
      /* Initial event for the key press, set up the state
       * with the long press timeout. */
      repeatState.time = now;
      repeatState.timeout = AUTOREPEAT_INITIAL_DELAY_MS;
      repeatState.command = *cmd & ~BRL_FLG_REPEAT_MASK;
      repeatState.started = 0;
    } else {
      resetRepeatState(&repeatState);
    }
    *cmd = BRL_CMD_NOOP;
    return 1;
  }
  return 0;
}

/*
 * Handles autorepeat and long press.  Implements long press
 * support for the CMD_ROUTE command and calls through to brltty's
 * autorepeat handling code for other commands.
 */
static void
handleRepeatAndLongPress(int* cmd) {
  if (!handleLongPress(cmd)) {
    /* Fall back on brltty's autorepeat functionality.
     * The panning argument below reflects a preference in brltty
     * whether to autorepeat while panning or not.  Since we don't have that
     * preference in BrailleBack, this is always set to 1 here.
     */
    handleRepeatFlags(cmd, &repeatState, 1 /*panning*/,
                      AUTOREPEAT_INITIAL_DELAY_MS, AUTOREPEAT_INTERVAL_MS);
  }
}

int
brltty_readCommand(int *readDelayMillis) {
  if (braille == NULL) {
    return BRL_CMD_RESTARTBRL;
  }
  int cmd = readBrailleCommand(&brailleDisplay, KTB_CTX_DEFAULT);
  handleRepeatAndLongPress(&cmd);
  if (repeatState.timeout > 0) {
    *readDelayMillis = repeatState.timeout;
  }
  return cmd;
}

int
brltty_writeWindow(unsigned char *dotPattern, size_t patternSize) {
  if (braille == NULL) {
    return 0;
  }
  size_t bufSize = brailleDisplay.textColumns * brailleDisplay.textRows;
  if (patternSize > bufSize) {
    patternSize = bufSize;
  }
  memcpy(brailleDisplay.buffer, dotPattern, patternSize);
  if (patternSize < bufSize) {
    memset(brailleDisplay.buffer + patternSize, 0, bufSize - patternSize);
  }
  return braille->writeWindow(&brailleDisplay, NULL);
}


int
brltty_getTextCells(void) {
  return brailleDisplay.textColumns * brailleDisplay.textRows;
}

int
brltty_getStatusCells(void) {
  return brailleDisplay.statusRows * brailleDisplay.statusColumns;
}

/*
 * Creates an array of empty strings, storing a pointer to the array in
 * the global variable driverParameters.  The size of the array
 * corresponds to the number of parameters expected by the current
 * driver.
 */
static int
createEmptyDriverParameters (void) {
  const char *const *parameterNames = braille->parameters;
  int count = 0;
  int i;
  if (!parameterNames) {
    static const char *const noNames[] = {NULL};
    parameterNames = noNames;
  }

  char **setting;
  while (parameterNames[count] != NULL) {
    ++count;
  }
  if (!(driverParameters = malloc((count + 1) * sizeof(*driverParameters)))) {
    logMessage(LOG_ERR, "insufficient memory.");
    return 0;
  }
  for (i = 0; i < count; ++i) {
    driverParameters[i] = "";
  }
  return 1;
}

static void
freeDriverParameters() {
  free(driverParameters);
  driverParameters = NULL;
}

static int
compileKeys(const char *tablesDir) {
  if (brailleDisplay.keyNameTables != NULL) {
    char* path = getKeyTablePath(tablesDir);
    if (path == NULL) {
      logMessage(LOG_ERR, "Couldn't construct key table filename");
      return 0;
    }
    brailleDisplay.keyTable = compileKeyTable(path,
                                              brailleDisplay.keyNameTables);
    if (brailleDisplay.keyTable != NULL) {
      setKeyEventLoggingFlag(brailleDisplay.keyTable, "");
    } else {
      logMessage(LOG_ERR, "Couldn't compile key table %s", path);
    }
    free(path);
    return brailleDisplay.keyTable != NULL;
  } else {
    return 1;
  }
}

static char *
getKeyTablePath(const char *tablesDir) {
  char *fileName;
  const char *strings[] = {
    "brl-", braille->definition.code, "-", brailleDisplay.keyBindings,
    KEY_TABLE_EXTENSION
  };
  fileName = joinStrings(strings, ARRAY_COUNT(strings));
  if (fileName == NULL) {
    return NULL;
  }

  char *path = makePath(tablesDir, fileName);
  free(fileName);
  return path;
}

int
brltty_listKeyMap(KeyMapEntryCallback callback, void* data) {
  KeyTable* keyTable = brailleDisplay.keyTable;
  if (keyTable == NULL) {
    logMessage(LOG_ERR, "No key table to list");
    return 0;
  }
  const KeyContext *context = getKeyContext(keyTable, KTB_CTX_DEFAULT);
  if (context == NULL) {
    logMessage(LOG_ERR, "Can't get default key context");
    return 0;
  }
  return listKeyContext(context, keyTable, callback, data);
}

static int
listKeyContext(const KeyContext *context, const KeyTable* keyTable,
               KeyMapEntryCallback callback,
               void *data) {
  int i;
  for (i = 0; i < context->keyBindings.count; ++i) {
    const KeyBinding* binding = &context->keyBindings.table[i];
    if (binding->flags & KBF_HIDDEN) {
      continue;
    }
    if (!listKeyBinding(binding, keyTable, callback, data)) {
      return 0;
    }
  }
  return 1;
}

static int
listKeyBinding(const KeyBinding *binding, const KeyTable *keyTable,
               KeyMapEntryCallback callback, void *data) {
  // Allow room for all modifiers, the immediate key and a terminating NULL.
  const char *keys[MAX_MODIFIERS_PER_COMBINATION + 2];
  int i;
  const KeyCombination *combination = &binding->combination;
  for (i = 0; i < combination->modifierCount; ++i) {
    // Key values are sorted in this list for quick comparison,
    // the modifierPositions array is ordered according to how the
    // keys were entered in the keymap file and maps to the sort order.
    int position = combination->modifierPositions[i];
    const KeyValue* value = &combination->modifierKeys[position];
    const char *name = findKeyName(keyTable, value);
    if (name == NULL) {
      return 0;
    }
    keys[i] = name;
  }
  if (combination->flags & KCF_IMMEDIATE_KEY) {
    const char *name = findKeyName(keyTable, &combination->immediateKey);
    if (name == NULL) {
      return 0;
    }
    keys[i++] = name;
  }
  keys[i] = NULL;
  int ret = callback(binding->command, i, keys, 0 /*isLongPress*/, data);
  if (!ret) {
    return ret;
  }
  /* Since we implement long press automatically, add a corresponding
   * binding to the route command if this route command isn't already
   * a long press in the key table.
   * Only do this if immediatekey flag is not active since in that
   * case longpress isn't possible.
   */
  if (((binding->command & (BRL_MSK_BLK | BRLTTY_ROUTE_ARG_FLG_LONG_PRESS))
         == BRL_BLK_ROUTE) && !(combination->flags & KCF_IMMEDIATE_KEY)) {
    ret = callback(binding->command | BRLTTY_ROUTE_ARG_FLG_LONG_PRESS,
                   i, keys, 1 /*isLongPress*/, data);
  }
  return ret;
}

static int
compareValueToKeyNameEntry(const void *key, const void *element) {
  const KeyValue* value = key;
  const KeyNameEntry *const *nameEntry = element;
  return compareKeyValues(value, &(*nameEntry)->value);
}

static const char *
findKeyName(const KeyTable *keyTable, const KeyValue *value) {
  const KeyNameEntry **entries = keyTable->keyNameTable;
  int entryCount = keyTable->keyNameCount;
  const KeyNameEntry **entry = bsearch(value, entries, entryCount,
                                      sizeof(KeyNameEntry*),
                                      compareValueToKeyNameEntry);
  if (entry != NULL) {
    return (*entry)->name;
  } else {
    logMessage(LOG_ERR, "No key name for key [%d, %d]",
               value->set, value->key);
    return NULL;
  }
}
