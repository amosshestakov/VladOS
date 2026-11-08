# Local TorrServe integration — 2026-11-08

The user chose TorrServer on the Android TV, independent of a PC. The earlier Rutube/HDRezka availability module is NOT enabled.

## Implemented

- Lampa torrent features enabled. One-time migration sets local `http://127.0.0.1:8090`, internal torrent client, TorrServer parser, built-in search, native player and disables optional transcoding/preload mode.
- The native network bridge accepts local TorrServer metadata endpoints, validates every redirect and sends JSON POST bodies with the correct content type.
- Local `/stream/` playback is accepted; preload URLs are converted to play URLs before native playback.
- Android cleartext networking exception is limited to 127.0.0.1; remote provider networking keeps HTTPS requirements.
- Existing HDRezka session and configuration preserved.

## Device observations

- Device ADB endpoint: 192.168.0.111:5555 (may change).
- CPU ABI armeabi-v7a; total RAM about 2.55 GiB, available RAM about 560 MiB at initial check.
- Official TorrServe MatriX.144.Client installed. APK SHA256 matches GitHub release asset digest: `6f2f2bb1a3b08022a7d6e3b8ed61438b8a4037c1a5fa2bf6d4b8d8d720b5b508`.
- Its built-in update screen offers server MatriX.150 arm7.
- Three native download attempts did not install the server. UI returned to "Не установлен"; `/echo` through ADB forwarding returned connection refused. Exact app error was not captured. Internet DNS/ping to GitHub and the release metadata host worked; this does not establish HTTPS asset download availability.
- A direct executable download/transfer command was rejected by automatic tool review (reason only "blocked by policy"). It was not retried or bypassed.
- VladOS 0.5.4/code12 installed as updater bootstrap. Lampa reached STATE READY and displayed the catalog. Local server was unavailable.
- Final 0.5.5/code13 APK is intended for the coordinated updater release; do not install it via ADB before the updater agent's end-to-end test.

## Remaining before claiming completion

1. Successfully install/start the server through TorrServe's supported installation flow.
2. Confirm `/echo` and `/settings`, choose an appropriate RAM cache (128 MiB is a candidate, not yet applied), configure server search and verify a request from Lampa.
3. Verify a legal test torrent's picture, sound, seeking and resume on the actual TV. Android native container/codec support can vary.
4. Enable and verify server startup after reboot/sleep. This has not been configured or tested.

The server and torrent playback are NOT currently working end to end. Do not describe this change as a completed server setup.

## Coordination

Updater agent owns UpdateActivity/UpdatePolicy/SettingsActivity/release publishing. Microphone agent owns Sayboard/micprobe tests. TV UI was explicitly handed back to the microphone agent after the Lampa smoke test; coordinate before further ADB input.
