# Volume Manager

Control each app's volume independently. [Shizuku](https://shizuku.rikka.app/) is used to access privileged APIs. Requires Android 13+.

This is a fork, check the original repo for details.

## Changes in this fork

- Redesign the UI. The popup volume menu should look more like a typical stock interface.
- Change the media volume slider to have 32 steps, for finer precision. This is achieved by implementing "virtual volume" similar to how the per-app volume works in the original app. This volume is approximately reflected in the "real" volume and viceversa, so other apps behave as you'd expect. (Motive: my phone has 16 volume steps and sane ways to increase that limit were unsuccessful)
- Per-app volume sliders will now scale by perceived volume, so the higher steps aren't all the same.
- During a call, pressing the volume keys will change the media volume instead of the call volume, unless the phone's proximity sensor is covered in any way, which is pretty convenient. This niche feature is toggleable.
- Fix a bug where VoIP calls (such as discord) did not make the call volume slider appear.
- Volume will change on the first key press even when the sliders aren't visible instead of needing to press a second time.

## Appearance

<img width="1084" height="2412" alt="Screenshot_20260811-210653" src="https://github.com/user-attachments/assets/8bcb9042-bf80-449d-9e3c-8f27904af37c" />


## Download

There's a build on the releases page. If you had the original app you'll need to uninstall it first.

I'm not responsible for damages etc.
