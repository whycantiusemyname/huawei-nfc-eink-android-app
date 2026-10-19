# Native Q ReaderMode adaptation

On the tested OnePlus PJZ110 Android 15 ROM, the NFC service includes
`NxpNfcAdapter.FLAG_READER_NFC_Q = 0x20` and maps it to the native polling
technology mask `0x100`. Its NXP host stack and SN220 controller accept Q poll
technology `0x71` in RF_DISCOVER.

The legacy `sendVendorNciMessage(1, 15, 112, {0x31, 1})` path returns `05` on this
ROM. That result describes that vendor command, not the native Q capability.

## Why the foreground path matters

This ROM's `setReaderMode` checks the caller UID's foreground state. A root helper
can return normally without applying ReaderMode. `NativeReaderSession` therefore
uses `NfcAdapter.enableReaderMode(Activity, ...)` in the foreground app process.
It needs the normal NFC manifest permission and the ROM vendor Q flag, not root.
A readable flag is used to choose an entry point; it is not treated as proof that
all firmware or screens will work. The fallback is the existing legacy backend,
used only when the native flag is not found.

The legacy backend separately resolves four- and five-argument `INfcAdapter`
methods before invoking them. Missing overloads trigger method lookup fallback;
exceptions thrown by an actual Binder invocation do not trigger a second call.

## Session ownership

The app keeps the screen on while working; the user must keep it unlocked and the
app in front. Leaving the page requests cancellation. An exclusive session file
lock covers scanning, protocol work and ReaderMode cleanup. Native callbacks
produce Android `Tag` objects in the same app process used by `PanelSession`.

Standard tests use NFC-A, while Q tests, identity reads, image sends and connection
rediscovery use the same native Q flags. No APDU is sent by a Q-only test. Protocol
checks, image hashes, same-UID reconnect and the existing no-write-replay rule
remain in place. If a different UID appears after reconnect, the session stops
before sending it an APDU. ReaderMode is released in `finally` before unlocking.

Native sessions do not read privileged HAL logs. Their UI states mean:

- BACKEND / START: the app selected and requested this route.
- TAG: the Android service delivered a tag callback; this alone does not identify
  the intended screen or confirm sufficient power for refreshing it.
- CAPABILITY with controller acceptance: only available when an observed Q
  RF_DISCOVER has its matching successful response; never inferred from a
  normal Java return or absence of a tag.
- RESTORED: the session's ReaderMode was released, not proof of physical screen
  communication or a completed display refresh.

## Validation

Pure Java regressions cover the existing protocol, both panel variants, image
mapping, cancellation, connection-only retry, four/five-argument method lookup,
Q/A/legacy flag selection, and RF_DISCOVER response correlation. In particular,
an ordinary A discovery response after a rejected Q request cannot set Q success.
The APK builds and its local debug signature verifies.

The actual `NativeReaderSession` and `QPollSupport` classes were exercised from a
separately signed, temporary foreground Activity with the normal NFC permission,
without replacing the installed upstream app. On PJZ110:

```text
native Q flag = 0x20
ReaderMode flags = 0xA0
TX 21 03 03 01 71 01
RX 41 03 01 00
(no target present; five-second timeout)
ReaderMode released
TX 21 03 0D 06 00 01 01 01 02 01 80 01 82 01 06 01
RX 41 03 01 00
```

Independent, temporary read-only hooks at the NXP HAL transmit/receive boundaries
captured these RF control packets. They did not alter the packets or call native
polling APIs during this foreground app test. The observer and test app were
removed afterward; no firmware or persistent NFC configuration was changed.
Raw device logs, personal identifiers, signing keys and instrumentation packages
are not included in this repository.

There is no physical target yet. Target activation, D1/ISO-DEP exchange, A0/A1
hardware variants, power delivery and image refresh remain unverified. A 00
RF_DISCOVER response proves acceptance of the polling configuration, not a
successful over-the-air transaction. Legacy hardware and five-argument ROMs also
still need device testing.
