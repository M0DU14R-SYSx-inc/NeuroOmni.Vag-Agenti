# Horizons — Tasker / Termux remote control

`HorizonsTaskerReceiver` exposes four actions you can fire from Tasker,
Termux, or `adb shell am broadcast`. Every action emits a result
broadcast on `com.horizons.action.RESULT` with extras
`source_action` and `body`. Optional `output_file` extra appends
the result to a file path of your choosing.

## Termux (or adb) examples

```sh
# 1. Reload engine
am broadcast \
  -a com.horizons.action.RELOAD_ENGINE \
  -n com.horizons/.tasker.HorizonsTaskerReceiver

# 2. Import a folder of model files (point tree_uri at the SAF tree)
am broadcast \
  -a com.horizons.action.IMPORT_FOLDER \
  -n com.horizons/.tasker.HorizonsTaskerReceiver \
  --es tree_uri "content://com.android.externalstorage.documents/tree/primary%3ADownload"

# 3. Send a prompt to the current engine
am broadcast \
  -a com.horizons.action.SEND_PROMPT \
  -n com.horizons/.tasker.HorizonsTaskerReceiver \
  --es text "what is 2+2?" \
  --es output_file /sdcard/Download/horizons_reply.txt

# 4. Get engine state (backend, status, error, staged)
am broadcast \
  -a com.horizons.action.GET_STATUS \
  -n com.horizons/.tasker.HorizonsTaskerReceiver \
  --es output_file /sdcard/Download/horizons_status.txt
```

The `-n com.horizons/.tasker.HorizonsTaskerReceiver` arg targets the
receiver directly — required on Android 8+ for implicit broadcasts to
exported receivers.

## Tasker — listening for the result

Profile → Event → System → **Intent Received**
- Action: `com.horizons.action.RESULT`
- The result body is in `%body`, the action that produced it in `%source_action`

## Tasker — firing a request

Task action: System → **Send Intent**
- Action: e.g. `com.horizons.action.SEND_PROMPT`
- Target: Broadcast Receiver
- Package: `com.horizons`
- Class: `com.horizons.tasker.HorizonsTaskerReceiver`
- Extra: `text:hello`, `output_file:/sdcard/Download/horizons_reply.txt`

## Termux:Tasker

Same `am broadcast` shell calls from a Termux script invoked by Tasker
via the Termux:Tasker plugin. Result polls the broadcast or reads the
`output_file` after a short sleep.

## Permissions

The receiver is `exported="true"` with no permission gate — any app on
the device can fire its actions. Acceptable for dev/sideload usage; if
this app ever ships to a wider audience, gate it behind a
`signature`-level custom permission.
