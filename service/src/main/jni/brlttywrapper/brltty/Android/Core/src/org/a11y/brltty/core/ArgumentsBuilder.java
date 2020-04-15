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

package org.a11y.brltty.core;

import java.util.ArrayList;
import java.util.List;

/* options not yet supported:
 * -E           --environment-variables
 * -A arg,...   --api-parameters=
 * -N           --no-api
 * -B arg,...   --braille-parameters=
 * -r           --release-device
 * -K arg,...   --keyboard-properties=
 * -s driver    --speech-driver=
 * -S arg,...   --speech-parameters=
 * -i file      --speech-input=
 * -x driver    --screen-driver=
 * -X arg,...   --screen-parameters=
 * -p device    --pcm-device=
 * -m device    --midi-device=
 * -U csecs     --update-interval=
 * -M csecs     --message-delay=
 * -e           --standard-error
 * -v           --verify
 */

public class ArgumentsBuilder {
  private boolean foregroundExecution = false;

  public boolean setForegroundExecution(boolean newValue) {
    boolean oldValue = foregroundExecution;
    foregroundExecution = newValue;
    return oldValue;
  }

  private boolean releaseDevice = false;

  public boolean setReleaseDevice(boolean newValue) {
    boolean oldValue = releaseDevice;
    releaseDevice = newValue;
    return oldValue;
  }

  private String logLevel = null;

  public String setLogLevel(String newValue) {
    String oldValue = logLevel;
    logLevel = newValue;
    return oldValue;
  }

  private String logFile = null;

  public String setLogFile(String newValue) {
    String oldValue = logFile;
    logFile = newValue;
    return oldValue;
  }

  private String configurationFile = null;

  public String setConfigurationFile(String newValue) {
    String oldValue = configurationFile;
    configurationFile = newValue;
    return oldValue;
  }

  private String preferencesFile = null;

  public String setPreferencesFile(String newValue) {
    String oldValue = preferencesFile;
    preferencesFile = newValue;
    return oldValue;
  }

  private String driversDirectory = null;

  public String setDriversDirectory(String newValue) {
    String oldValue = driversDirectory;
    driversDirectory = newValue;
    return oldValue;
  }

  private String writableDirectory = null;

  public String setWritableDirectory(String newValue) {
    String oldValue = writableDirectory;
    writableDirectory = newValue;
    return oldValue;
  }

  private String updatableDirectory = null;

  public String setUpdatableDirectory(String newValue) {
    String oldValue = updatableDirectory;
    updatableDirectory = newValue;
    return oldValue;
  }

  private String tablesDirectory = null;

  public String setTablesDirectory(String newValue) {
    String oldValue = tablesDirectory;
    tablesDirectory = newValue;
    return oldValue;
  }

  private String textTable = null;

  public String setTextTable(String newValue) {
    String oldValue = textTable;
    textTable = newValue;
    return oldValue;
  }

  private String attributesTable = null;

  public String setAttributesTable(String newValue) {
    String oldValue = attributesTable;
    attributesTable = newValue;
    return oldValue;
  }

  private String contractionTable = null;

  public String setContractionTable(String newValue) {
    String oldValue = contractionTable;
    contractionTable = newValue;
    return oldValue;
  }

  private String keyboardTable = null;

  public String setKeyboardTable(String newValue) {
    String oldValue = keyboardTable;
    keyboardTable = newValue;
    return oldValue;
  }

  private String brailleDriver = null;

  public String setBrailleDriver(String newValue) {
    String oldValue = brailleDriver;
    brailleDriver = newValue;
    return oldValue;
  }

  private String brailleDevice = null;

  public String setBrailleDevice(String newValue) {
    String oldValue = brailleDevice;
    brailleDevice = newValue;
    return oldValue;
  }

  private String speechDriver = null;

  public String setSpeechDriver(String newValue) {
    String oldValue = speechDriver;
    speechDriver = newValue;
    return oldValue;
  }

  private boolean quietIfNoBraille = false;

  public boolean setQuietIfNoBraille(boolean newValue) {
    boolean oldValue = quietIfNoBraille;
    quietIfNoBraille = newValue;
    return oldValue;
  }

  private boolean apiEnabled = false;

  public boolean setApiEnabled(boolean newValue) {
    boolean oldValue = apiEnabled;
    apiEnabled = newValue;
    return oldValue;
  }

  private String apiParameters = null;

  public String setApiParameters(String newValue) {
    String oldValue = apiParameters;
    apiParameters = newValue;
    return oldValue;
  }

  protected void addOption(List<String> arguments, String option, String value) {
    if (value != null) {
      if (value.length() > 0) {
        arguments.add(option);
        arguments.add(value);
      }
    }
  }

  protected void addOption(List<String> arguments, String option, Enum value) {
    if (value != null) {
      addOption(arguments, option, value.name());
    }
  }

  protected void addOption(List<String> arguments, String option, boolean value) {
    if (value) {
      arguments.add(option);
    }
  }

  public String[] getArguments() {
    List<String> arguments = new ArrayList<String>();
    addOption(arguments, "-n", foregroundExecution);

    addOption(arguments, "-l", logLevel);
    addOption(arguments, "-L", logFile);

    addOption(arguments, "-f", configurationFile);
    addOption(arguments, "-F", preferencesFile);

    addOption(arguments, "-D", driversDirectory);
    addOption(arguments, "-W", writableDirectory);
    addOption(arguments, "-U", updatableDirectory);

    addOption(arguments, "-T", tablesDirectory);
    addOption(arguments, "-t", textTable);
    addOption(arguments, "-a", attributesTable);
    addOption(arguments, "-c", contractionTable);
    addOption(arguments, "-k", keyboardTable);

    addOption(arguments, "-b", brailleDriver);
    addOption(arguments, "-d", brailleDevice);
    addOption(arguments, "-r", releaseDevice);

    addOption(arguments, "-s", speechDriver);
    addOption(arguments, "-Q", quietIfNoBraille);

    addOption(arguments, "-N", !apiEnabled);
    addOption(arguments, "-A", apiParameters);

    return arguments.toArray(new String[arguments.size()]);
  }
}
