# WebRTC + Java Agent integration (Windows 11)

## 1) Recommended webrtc-streamer launch command

Run `webrtc-streamer.exe` on the remote PC desktop session (not as a Windows service):

```bat
webrtc-streamer.exe ^
  -H 0.0.0.0:8000 ^
  -S 0.0.0.0:3478 ^
  -n stun:stun.l.google.com:19302 ^
  "screen://"
```

If `screen://` fails on your build, list devices and pick one:

```bat
webrtc-streamer.exe -l
```

Then use the exact source from the list, for example:

```bat
webrtc-streamer.exe -H 0.0.0.0:8000 "screen://0"
```

### Why you got HTTP 500 on `/api/call?url=screen://0`

Most common reasons on Windows:
- Process is running in session without interactive desktop.
- Invalid capture URL for your binary (`screen://0` vs `screen://`).
- GPU capture backend conflict (RDP/disconnected console session).

Quick health checks:
- Open `http://<pc-ip>:8000/api/getMediaList` and verify `screen://` appears.
- Open `http://<pc-ip>:8000/webrtcstreamer.html?video=screen://` (or exact listed URL).

## 2) Agent responsibilities after migration

After moving video to WebRTC, Java Agent should do only:
- register/auth;
- heartbeat;
- mouse/keyboard commands.

Disable frame uploader/timers in agent code to reduce CPU and network load.

## 3) Java agent code fixes

### `AgentApplication.java`
- remove custom inner `Map` class;
- use `java.util.Map.of(...)` directly;
- keep auth + websocket startup.

### `AgentWebSocketClient.java`
- remove `ScreenCapture` field;
- remove `screenTimer` and `startScreenCapture*` methods;
- in `onOpen()` call only `sendRegistration()` and `startHeartbeat()`;
- keep `settings` message handler as no-op or acknowledgement (video no longer controlled by agent).

Minimal behavior for `settings` after migration:

```java
else if ("settings".equals(type)) {
    System.out.println("Settings ignored: video handled by webrtc-streamer");
}
```

## 4) Browser page

Use `src/main/resources/static/watch.html` from this repo revision. It:
- pulls video from webrtc-streamer (`/webrtcstreamer.js` + `/libs/adapter.min.js`);
- keeps websocket control channel to your Java server (`/ws/client`).

Example URL:

```text
http://192.168.50.22:8080/watch.html?pcId=1&pcName=OfficePC&streamHost=192.168.50.22&streamPort=8000&stream=screen://
```

## 5) Validation checklist (30–60 FPS target)

1. Start backend and agent, verify PC online in `/pcs.html`.
2. Start `webrtc-streamer.exe` with command above.
3. Check direct page `http://192.168.50.22:8000/webrtcstreamer.html?video=screen://`.
4. Open integrated watch page on another LAN device.
5. Move mouse in browser and verify native cursor movement.
6. In Chrome `chrome://webrtc-internals`, verify:
   - stable `framesDecoded` growth,
   - target 30–60 fps,
   - packet loss ideally < 1–2% in LAN.

## 6) If webrtc-streamer still unstable

Production-grade fallback:
- OBS + `obs-webrtc` / Janus / mediasoup SFU pipeline.
- For pure LAN and minimal setup, Sunshine + custom input bridge is often more stable for high FPS.
