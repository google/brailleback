/*
 * NOTE: Manually generated on Linux and added to version control to be used
 * when building under the Android NDK.
 * This file is created by the native brltty build system.
 */
{
  .name = "NOOP",
  .code = BRL_CMD_NOOP,
  .description = strtext("do nothing")
}
,
{
  .name = "LNUP",
  .code = BRL_CMD_LNUP,
  .isMotion = 1,
  .description = strtext("go up one line")
}
,
{
  .name = "LNDN",
  .code = BRL_CMD_LNDN,
  .isMotion = 1,
  .description = strtext("go down one line")
}
,
{
  .name = "WINUP",
  .code = BRL_CMD_WINUP,
  .isMotion = 1,
  .description = strtext("go up several lines")
}
,
{
  .name = "WINDN",
  .code = BRL_CMD_WINDN,
  .isMotion = 1,
  .description = strtext("go down several lines")
}
,
{
  .name = "PRDIFLN",
  .code = BRL_CMD_PRDIFLN,
  .isMotion = 1,
  .description = strtext("go up to nearest line with different content")
}
,
{
  .name = "NXDIFLN",
  .code = BRL_CMD_NXDIFLN,
  .isMotion = 1,
  .description = strtext("go down to nearest line with different content")
}
,
{
  .name = "ATTRUP",
  .code = BRL_CMD_ATTRUP,
  .isMotion = 1,
  .description = strtext("go up to nearest line with different highlighting")
}
,
{
  .name = "ATTRDN",
  .code = BRL_CMD_ATTRDN,
  .isMotion = 1,
  .description = strtext("go down to nearest line with different highlighting")
}
,
{
  .name = "TOP",
  .code = BRL_CMD_TOP,
  .isMotion = 1,
  .description = strtext("go to top line")
}
,
{
  .name = "BOT",
  .code = BRL_CMD_BOT,
  .isMotion = 1,
  .description = strtext("go to bottom line")
}
,
{
  .name = "TOP_LEFT",
  .code = BRL_CMD_TOP_LEFT,
  .isMotion = 1,
  .description = strtext("go to beginning of top line")
}
,
{
  .name = "BOT_LEFT",
  .code = BRL_CMD_BOT_LEFT,
  .isMotion = 1,
  .description = strtext("go to beginning of bottom line")
}
,
{
  .name = "PRPGRPH",
  .code = BRL_CMD_PRPGRPH,
  .isMotion = 1,
  .description = strtext("go up to last line of previous paragraph")
}
,
{
  .name = "NXPGRPH",
  .code = BRL_CMD_NXPGRPH,
  .isMotion = 1,
  .description = strtext("go down to first line of next paragraph")
}
,
{
  .name = "PRPROMPT",
  .code = BRL_CMD_PRPROMPT,
  .isMotion = 1,
  .description = strtext("go up to previous command prompt")
}
,
{
  .name = "NXPROMPT",
  .code = BRL_CMD_NXPROMPT,
  .isMotion = 1,
  .description = strtext("go down to next command prompt")
}
,
{
  .name = "PRSEARCH",
  .code = BRL_CMD_PRSEARCH,
  .description = strtext("search backward for clipboard text")
}
,
{
  .name = "NXSEARCH",
  .code = BRL_CMD_NXSEARCH,
  .description = strtext("search forward for clipboard text")
}
,
{
  .name = "CHRLT",
  .code = BRL_CMD_CHRLT,
  .isMotion = 1,
  .description = strtext("go left one character")
}
,
{
  .name = "CHRRT",
  .code = BRL_CMD_CHRRT,
  .isMotion = 1,
  .description = strtext("go right one character")
}
,
{
  .name = "HWINLT",
  .code = BRL_CMD_HWINLT,
  .isMotion = 1,
  .description = strtext("go left half a window")
}
,
{
  .name = "HWINRT",
  .code = BRL_CMD_HWINRT,
  .isMotion = 1,
  .description = strtext("go right half a window")
}
,
{
  .name = "FWINLT",
  .code = BRL_CMD_FWINLT,
  .isMotion = 1,
  .description = strtext("go left one window")
}
,
{
  .name = "FWINRT",
  .code = BRL_CMD_FWINRT,
  .isMotion = 1,
  .description = strtext("go right one window")
}
,
{
  .name = "FWINLTSKIP",
  .code = BRL_CMD_FWINLTSKIP,
  .isMotion = 1,
  .description = strtext("go left to nearest non-blank window")
}
,
{
  .name = "FWINRTSKIP",
  .code = BRL_CMD_FWINRTSKIP,
  .isMotion = 1,
  .description = strtext("go right to nearest non-blank window")
}
,
{
  .name = "LNBEG",
  .code = BRL_CMD_LNBEG,
  .isMotion = 1,
  .description = strtext("go to beginning of line")
}
,
{
  .name = "LNEND",
  .code = BRL_CMD_LNEND,
  .isMotion = 1,
  .description = strtext("go to end of line")
}
,
{
  .name = "HOME",
  .code = BRL_CMD_HOME,
  .isMotion = 1,
  .description = strtext("go to cursor")
}
,
{
  .name = "BACK",
  .code = BRL_CMD_BACK,
  .isMotion = 1,
  .description = strtext("go back after cursor tracking")
}
,
{
  .name = "RETURN",
  .code = BRL_CMD_RETURN,
  .isMotion = 1,
  .description = strtext("go to cursor or go back after cursor tracking")
}
,
{
  .name = "FREEZE",
  .code = BRL_CMD_FREEZE,
  .description = strtext("freeze/unfreeze screen image")
}
,
{
  .name = "DISPMD",
  .code = BRL_CMD_DISPMD,
  .isToggle = 1,
  .description = strtext("set display mode attributes/text")
}
,
{
  .name = "SIXDOTS",
  .code = BRL_CMD_SIXDOTS,
  .isToggle = 1,
  .description = strtext("set text style 6-dot/8-dot")
}
,
{
  .name = "SLIDEWIN",
  .code = BRL_CMD_SLIDEWIN,
  .isToggle = 1,
  .description = strtext("set sliding window on/off")
}
,
{
  .name = "SKPIDLNS",
  .code = BRL_CMD_SKPIDLNS,
  .isToggle = 1,
  .description = strtext("set skipping of lines with identical content on/off")
}
,
{
  .name = "SKPBLNKWINS",
  .code = BRL_CMD_SKPBLNKWINS,
  .isToggle = 1,
  .description = strtext("set skipping of blank windows on/off")
}
,
{
  .name = "CSRVIS",
  .code = BRL_CMD_CSRVIS,
  .isToggle = 1,
  .description = strtext("set cursor visibility on/off")
}
,
{
  .name = "CSRHIDE",
  .code = BRL_CMD_CSRHIDE,
  .isToggle = 1,
  .description = strtext("set hidden cursor on/off")
}
,
{
  .name = "CSRTRK",
  .code = BRL_CMD_CSRTRK,
  .isToggle = 1,
  .description = strtext("set cursor tracking on/off")
}
,
{
  .name = "CSRSIZE",
  .code = BRL_CMD_CSRSIZE,
  .isToggle = 1,
  .description = strtext("set cursor style block/underline")
}
,
{
  .name = "CSRBLINK",
  .code = BRL_CMD_CSRBLINK,
  .isToggle = 1,
  .description = strtext("set cursor blinking on/off")
}
,
{
  .name = "ATTRVIS",
  .code = BRL_CMD_ATTRVIS,
  .isToggle = 1,
  .description = strtext("set attribute underlining on/off")
}
,
{
  .name = "ATTRBLINK",
  .code = BRL_CMD_ATTRBLINK,
  .isToggle = 1,
  .description = strtext("set attribute blinking on/off")
}
,
{
  .name = "CAPBLINK",
  .code = BRL_CMD_CAPBLINK,
  .isToggle = 1,
  .description = strtext("set capital letter blinking on/off")
}
,
{
  .name = "TUNES",
  .code = BRL_CMD_TUNES,
  .isToggle = 1,
  .description = strtext("set alert tunes on/off")
}
,
{
  .name = "AUTOREPEAT",
  .code = BRL_CMD_AUTOREPEAT,
  .isToggle = 1,
  .description = strtext("set autorepeat on/off")
}
,
{
  .name = "AUTOSPEAK",
  .code = BRL_CMD_AUTOSPEAK,
  .isToggle = 1,
  .description = strtext("set autospeak on/off")
}
,
{
  .name = "HELP",
  .code = BRL_CMD_HELP,
  .description = strtext("enter/leave help display")
}
,
{
  .name = "INFO",
  .code = BRL_CMD_INFO,
  .description = strtext("enter/leave status display")
}
,
{
  .name = "LEARN",
  .code = BRL_CMD_LEARN,
  .description = strtext("enter/leave command learn mode")
}
,
{
  .name = "PREFMENU",
  .code = BRL_CMD_PREFMENU,
  .description = strtext("enter/leave preferences menu")
}
,
{
  .name = "PREFSAVE",
  .code = BRL_CMD_PREFSAVE,
  .description = strtext("save preferences to disk")
}
,
{
  .name = "PREFLOAD",
  .code = BRL_CMD_PREFLOAD,
  .description = strtext("restore preferences from disk")
}
,
{
  .name = "MENU_FIRST_ITEM",
  .code = BRL_CMD_MENU_FIRST_ITEM,
  .isMotion = 1,
  .description = strtext("go to first item")
}
,
{
  .name = "MENU_LAST_ITEM",
  .code = BRL_CMD_MENU_LAST_ITEM,
  .isMotion = 1,
  .description = strtext("go to last item")
}
,
{
  .name = "MENU_PREV_ITEM",
  .code = BRL_CMD_MENU_PREV_ITEM,
  .isMotion = 1,
  .description = strtext("go to previous item")
}
,
{
  .name = "MENU_NEXT_ITEM",
  .code = BRL_CMD_MENU_NEXT_ITEM,
  .isMotion = 1,
  .description = strtext("go to next item")
}
,
{
  .name = "MENU_PREV_SETTING",
  .code = BRL_CMD_MENU_PREV_SETTING,
  .description = strtext("select previous choice")
}
,
{
  .name = "MENU_NEXT_SETTING",
  .code = BRL_CMD_MENU_NEXT_SETTING,
  .description = strtext("select next choice")
}
,
{
  .name = "MUTE",
  .code = BRL_CMD_MUTE,
  .description = strtext("stop speaking")
}
,
{
  .name = "SPKHOME",
  .code = BRL_CMD_SPKHOME,
  .isMotion = 1,
  .description = strtext("go to current speech position")
}
,
{
  .name = "SAY_LINE",
  .code = BRL_CMD_SAY_LINE,
  .description = strtext("speak current line")
}
,
{
  .name = "SAY_ABOVE",
  .code = BRL_CMD_SAY_ABOVE,
  .description = strtext("speak from top of screen through current line")
}
,
{
  .name = "SAY_BELOW",
  .code = BRL_CMD_SAY_BELOW,
  .description = strtext("speak from current line through bottom of screen")
}
,
{
  .name = "SAY_SLOWER",
  .code = BRL_CMD_SAY_SLOWER,
  .description = strtext("decrease speech rate")
}
,
{
  .name = "SAY_FASTER",
  .code = BRL_CMD_SAY_FASTER,
  .description = strtext("increase speech rate")
}
,
{
  .name = "SAY_SOFTER",
  .code = BRL_CMD_SAY_SOFTER,
  .description = strtext("decrease speech volume")
}
,
{
  .name = "SAY_LOUDER",
  .code = BRL_CMD_SAY_LOUDER,
  .description = strtext("increase speech volume")
}
,
{
  .name = "SWITCHVT_PREV",
  .code = BRL_CMD_SWITCHVT_PREV,
  .description = strtext("switch to previous virtual terminal")
}
,
{
  .name = "SWITCHVT_NEXT",
  .code = BRL_CMD_SWITCHVT_NEXT,
  .description = strtext("switch to next virtual terminal")
}
,
{
  .name = "CSRJMP_VERT",
  .code = BRL_CMD_CSRJMP_VERT,
  .isRouting = 1,
  .description = strtext("bring cursor to line")
}
,
{
  .name = "PASTE",
  .code = BRL_CMD_PASTE,
  .description = strtext("insert clipboard text at cursor")
}
,
{
  .name = "RESTARTBRL",
  .code = BRL_CMD_RESTARTBRL,
  .description = strtext("restart braille driver")
}
,
{
  .name = "RESTARTSPEECH",
  .code = BRL_CMD_RESTARTSPEECH,
  .description = strtext("restart speech driver")
}
,
{
  .name = "OFFLINE",
  .code = BRL_CMD_OFFLINE,
  .description = strtext("braille display temporarily unavailable")
}
,
{
  .name = "SHIFT",
  .code = BRL_CMD_SHIFT,
  .isToggle = 1,
  .description = strtext("set shift modifier of next typed character or emulated key on/off")
}
,
{
  .name = "UPPER",
  .code = BRL_CMD_UPPER,
  .isToggle = 1,
  .description = strtext("set upper modifier of next typed character or emulated key on/off")
}
,
{
  .name = "CONTROL",
  .code = BRL_CMD_CONTROL,
  .isToggle = 1,
  .description = strtext("set control modifier of next typed character or emulated key on/off")
}
,
{
  .name = "META",
  .code = BRL_CMD_META,
  .isToggle = 1,
  .description = strtext("set meta modifier of next typed character or emulated key on/off")
}
,
{
  .name = "TIME",
  .code = BRL_CMD_TIME,
  .description = strtext("show the current date and time")
}
,
{
  .name = "MENU_PREV_LEVEL",
  .code = BRL_CMD_MENU_PREV_LEVEL,
  .isMotion = 1,
  .description = strtext("go to previous menu level")
}
,
{
  .name = "ASPK_SEL_LINE",
  .code = BRL_CMD_ASPK_SEL_LINE,
  .isToggle = 1,
  .description = strtext("set autospeak selected line on/off")
}
,
{
  .name = "ASPK_SEL_CHAR",
  .code = BRL_CMD_ASPK_SEL_CHAR,
  .isToggle = 1,
  .description = strtext("set autospeak selected character on/off")
}
,
{
  .name = "ASPK_INS_CHARS",
  .code = BRL_CMD_ASPK_INS_CHARS,
  .isToggle = 1,
  .description = strtext("set autospeak inserted characters on/off")
}
,
{
  .name = "ASPK_DEL_CHARS",
  .code = BRL_CMD_ASPK_DEL_CHARS,
  .isToggle = 1,
  .description = strtext("set autospeak deleted characters on/off")
}
,
{
  .name = "ASPK_REP_CHARS",
  .code = BRL_CMD_ASPK_REP_CHARS,
  .isToggle = 1,
  .description = strtext("set autospeak replaced characters on/off")
}
,
{
  .name = "ASPK_CMP_WORDS",
  .code = BRL_CMD_ASPK_CMP_WORDS,
  .isToggle = 1,
  .description = strtext("set autospeak completed words on/off")
}
,
{
  .name = "SPEAK_CURR_CHAR",
  .code = BRL_CMD_SPEAK_CURR_CHAR,
  .description = strtext("speak current character")
}
,
{
  .name = "SPEAK_PREV_CHAR",
  .code = BRL_CMD_SPEAK_PREV_CHAR,
  .isMotion = 1,
  .description = strtext("go to and speak previous character")
}
,
{
  .name = "SPEAK_NEXT_CHAR",
  .code = BRL_CMD_SPEAK_NEXT_CHAR,
  .isMotion = 1,
  .description = strtext("go to and speak next character")
}
,
{
  .name = "SPEAK_CURR_WORD",
  .code = BRL_CMD_SPEAK_CURR_WORD,
  .description = strtext("speak current word")
}
,
{
  .name = "SPEAK_PREV_WORD",
  .code = BRL_CMD_SPEAK_PREV_WORD,
  .isMotion = 1,
  .description = strtext("go to and speak previous word")
}
,
{
  .name = "SPEAK_NEXT_WORD",
  .code = BRL_CMD_SPEAK_NEXT_WORD,
  .isMotion = 1,
  .description = strtext("go to and speak next word")
}
,
{
  .name = "SPEAK_CURR_LINE",
  .code = BRL_CMD_SPEAK_CURR_LINE,
  .description = strtext("speak current line")
}
,
{
  .name = "SPEAK_PREV_LINE",
  .code = BRL_CMD_SPEAK_PREV_LINE,
  .isMotion = 1,
  .description = strtext("go to and speak previous line")
}
,
{
  .name = "SPEAK_NEXT_LINE",
  .code = BRL_CMD_SPEAK_NEXT_LINE,
  .isMotion = 1,
  .description = strtext("go to and speak next line")
}
,
{
  .name = "SPEAK_FRST_CHAR",
  .code = BRL_CMD_SPEAK_FRST_CHAR,
  .isMotion = 1,
  .description = strtext("go to and speak first non-blank character on line")
}
,
{
  .name = "SPEAK_LAST_CHAR",
  .code = BRL_CMD_SPEAK_LAST_CHAR,
  .isMotion = 1,
  .description = strtext("go to and speak last non-blank character on line")
}
,
{
  .name = "SPEAK_FRST_LINE",
  .code = BRL_CMD_SPEAK_FRST_LINE,
  .isMotion = 1,
  .description = strtext("go to and speak first non-blank line on screen")
}
,
{
  .name = "SPEAK_LAST_LINE",
  .code = BRL_CMD_SPEAK_LAST_LINE,
  .isMotion = 1,
  .description = strtext("go to and speak last non-blank line on screen")
}
,
{
  .name = "DESC_CURR_CHAR",
  .code = BRL_CMD_DESC_CURR_CHAR,
  .description = strtext("describe current character")
}
,
{
  .name = "SPELL_CURR_WORD",
  .code = BRL_CMD_SPELL_CURR_WORD,
  .description = strtext("spell current word")
}
,
{
  .name = "ROUTE_CURR_LOCN",
  .code = BRL_CMD_ROUTE_CURR_LOCN,
  .isRouting = 1,
  .description = strtext("bring cursor to speech location")
}
,
{
  .name = "SPEAK_CURR_LOCN",
  .code = BRL_CMD_SPEAK_CURR_LOCN,
  .description = strtext("speak speech location")
}
,
{
  .name = "SHOW_CURR_LOCN",
  .code = BRL_CMD_SHOW_CURR_LOCN,
  .isToggle = 1,
  .description = strtext("set speech location visibility on/off")
}
,
{
  .name = "ROUTE",
  .code = BRL_BLK_ROUTE,
  .isRouting = 1,
  .isColumn = 1,
  .description = strtext("bring cursor to character")
}
,
{
  .name = "CLIP_NEW",
  .code = BRL_BLK_CLIP_NEW,
  .isColumn = 1,
  .description = strtext("start new clipboard at character")
}
,
{
  .name = "CLIP_ADD",
  .code = BRL_BLK_CLIP_ADD,
  .isColumn = 1,
  .description = strtext("append to clipboard from character")
}
,
{
  .name = "COPY_RECT",
  .code = BRL_BLK_COPY_RECT,
  .isColumn = 1,
  .description = strtext("rectangular copy to character")
}
,
{
  .name = "COPY_LINE",
  .code = BRL_BLK_COPY_LINE,
  .isColumn = 1,
  .description = strtext("linear copy to character")
}
,
{
  .name = "SWITCHVT",
  .code = BRL_BLK_SWITCHVT,
  .isOffset = 1,
  .description = strtext("switch to virtual terminal")
}
,
{
  .name = "PRINDENT",
  .code = BRL_BLK_PRINDENT,
  .isMotion = 1,
  .isColumn = 1,
  .description = strtext("go up to nearest line with less indent than character")
}
,
{
  .name = "NXINDENT",
  .code = BRL_BLK_NXINDENT,
  .isMotion = 1,
  .isColumn = 1,
  .description = strtext("go down to nearest line with less indent than character")
}
,
{
  .name = "DESCCHAR",
  .code = BRL_BLK_DESCCHAR,
  .isColumn = 1,
  .description = strtext("describe character")
}
,
{
  .name = "SETLEFT",
  .code = BRL_BLK_SETLEFT,
  .isColumn = 1,
  .description = strtext("place left end of window at character")
}
,
{
  .name = "SETMARK",
  .code = BRL_BLK_SETMARK,
  .isOffset = 1,
  .description = strtext("remember current window position")
}
,
{
  .name = "GOTOMARK",
  .code = BRL_BLK_GOTOMARK,
  .isMotion = 1,
  .isOffset = 1,
  .description = strtext("go to remembered window position")
}
,
{
  .name = "GOTOLINE",
  .code = BRL_BLK_GOTOLINE,
  .isMotion = 1,
  .isRow = 1,
  .description = strtext("go to selected line")
}
,
{
  .name = "PRDIFCHAR",
  .code = BRL_BLK_PRDIFCHAR,
  .isMotion = 1,
  .isColumn = 1,
  .description = strtext("go up to nearest line with different character")
}
,
{
  .name = "NXDIFCHAR",
  .code = BRL_BLK_NXDIFCHAR,
  .isMotion = 1,
  .isColumn = 1,
  .description = strtext("go down to nearest line with different character")
}
,
{
  .name = "CLIP_COPY",
  .code = BRL_BLK_CLIP_COPY,
  .isRange = 1,
  .description = strtext("copy characters to clipboard")
}
,
{
  .name = "CLIP_APPEND",
  .code = BRL_BLK_CLIP_APPEND,
  .isRange = 1,
  .description = strtext("append characters to clipboard")
}
,
{
  .name = "PWGEN",
  .code = BRL_BLK_PWGEN,
  .isOffset = 1,
  .description = strtext("put random password into clipboard")
}
,
{
  .name = "PASSKEY",
  .code = BRL_BLK_PASSKEY,
  .isInput = 1,
  .description = strtext("emulate special key")
}
,
{
  .name = "KEY_ENTER",
  .code = BRL_BLK_PASSKEY+BRL_KEY_ENTER,
  .isInput = 1,
  .description = strtext("enter key")
}
,
{
  .name = "KEY_TAB",
  .code = BRL_BLK_PASSKEY+BRL_KEY_TAB,
  .isInput = 1,
  .description = strtext("tab key")
}
,
{
  .name = "KEY_BACKSPACE",
  .code = BRL_BLK_PASSKEY+BRL_KEY_BACKSPACE,
  .isInput = 1,
  .description = strtext("backspace key")
}
,
{
  .name = "KEY_ESCAPE",
  .code = BRL_BLK_PASSKEY+BRL_KEY_ESCAPE,
  .isInput = 1,
  .description = strtext("escape key")
}
,
{
  .name = "KEY_CURSOR_LEFT",
  .code = BRL_BLK_PASSKEY+BRL_KEY_CURSOR_LEFT,
  .isInput = 1,
  .description = strtext("cursor-left key")
}
,
{
  .name = "KEY_CURSOR_RIGHT",
  .code = BRL_BLK_PASSKEY+BRL_KEY_CURSOR_RIGHT,
  .isInput = 1,
  .description = strtext("cursor-right key")
}
,
{
  .name = "KEY_CURSOR_UP",
  .code = BRL_BLK_PASSKEY+BRL_KEY_CURSOR_UP,
  .isInput = 1,
  .description = strtext("cursor-up key")
}
,
{
  .name = "KEY_CURSOR_DOWN",
  .code = BRL_BLK_PASSKEY+BRL_KEY_CURSOR_DOWN,
  .isInput = 1,
  .description = strtext("cursor-down key")
}
,
{
  .name = "KEY_PAGE_UP",
  .code = BRL_BLK_PASSKEY+BRL_KEY_PAGE_UP,
  .isInput = 1,
  .description = strtext("page-up key")
}
,
{
  .name = "KEY_PAGE_DOWN",
  .code = BRL_BLK_PASSKEY+BRL_KEY_PAGE_DOWN,
  .isInput = 1,
  .description = strtext("page-down key")
}
,
{
  .name = "KEY_HOME",
  .code = BRL_BLK_PASSKEY+BRL_KEY_HOME,
  .isInput = 1,
  .description = strtext("home key")
}
,
{
  .name = "KEY_END",
  .code = BRL_BLK_PASSKEY+BRL_KEY_END,
  .isInput = 1,
  .description = strtext("end key")
}
,
{
  .name = "KEY_INSERT",
  .code = BRL_BLK_PASSKEY+BRL_KEY_INSERT,
  .isInput = 1,
  .description = strtext("insert key")
}
,
{
  .name = "KEY_DELETE",
  .code = BRL_BLK_PASSKEY+BRL_KEY_DELETE,
  .isInput = 1,
  .description = strtext("delete key")
}
,
{
  .name = "KEY_FUNCTION",
  .code = BRL_BLK_PASSKEY+BRL_KEY_FUNCTION,
  .isInput = 1,
  .isOffset = 1,
  .description = strtext("function key")
}
,
{
  .name = "PASSCHAR",
  .code = BRL_BLK_PASSCHAR,
  .isInput = 1,
  .isCharacter = 1,
  .description = strtext("type unicode character")
}
,
{
  .name = "PASSDOTS",
  .code = BRL_BLK_PASSDOTS,
  .isInput = 1,
  .isBraille = 1,
  .description = strtext("type braille character")
}
,
{
  .name = "PASSAT",
  .code = BRL_BLK_PASSAT,
  .isKeyboard = 1,
  .description = strtext("AT (set 2) keyboard scan code")
}
,
{
  .name = "PASSXT",
  .code = BRL_BLK_PASSXT,
  .isKeyboard = 1,
  .description = strtext("XT (set 1) keyboard scan code")
}
,
{
  .name = "PASSPS2",
  .code = BRL_BLK_PASSPS2,
  .isKeyboard = 1,
  .description = strtext("PS/2 (set 3) keyboard scan code")
}
,
{
  .name = "CONTEXT",
  .code = BRL_BLK_CONTEXT,
  .isOffset = 1,
  .description = strtext("switch to command context")
}
,
