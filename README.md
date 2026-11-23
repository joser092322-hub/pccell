# PCcell

Remote control and integration between a Windows PC and an Android smartphone over local network (Wi-Fi) or USB cable.

This project was developed with the assistance of Artificial Intelligence.

---

## Features

- Real-time screen streaming (60 FPS) with touch control and dedicated left/right click buttons.
- Virtual gamepad designed for landscape mode with D-Pad, ABXY buttons, L1/R1/L2/R2 bumpers, and L3/R3 analog clicks.
- Bidirectional file transfer and remote PC directory browser.
- Clipboard synchronization (text and instant photo pasting directly into Windows Ctrl+V).
- Wireless webcam streaming from phone camera to PC.
- Wireless microphone streaming real-time PCM audio to PC.
- System macro deck (panic mode to mute/minimize all, native Task Manager shortcut, browser, media controls).
- Hardware monitor (CPU, RAM, and Disk usage) and remote power controls (shutdown, restart, sleep).

---

## Architecture

- PC (Java 21): Multi-threaded TCP server for command execution and Robot inputs, plus UDP server for local network discovery.
- Mobile (Android Java): TCP client with configuration change handling to maintain uninterrupted connections across screen rotations.

Default communication port: 5050 (TCP/UDP).

---

## Getting Started

### PC Server
```bash
javac --release 21 -d pccell-PC/out/production/project pccell-PC/project/src/*.java
java -cp pccell-PC/out/production/project Main
```

### Android App
```bash
cd pccell-cell
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Optional: connect via USB cable without Wi-Fi
adb reverse tcp:5050 tcp:5050
```

---

## License

MIT
