# Slap

WhatsApp stickers from your photos, GIFs and videos — without the white border, without an account, without ads.

Slap turns a picture, a GIF or a video clip into a WhatsApp sticker, groups stickers into packs and sends the packs straight to WhatsApp. Everything happens on the phone.

## Features

- **Static and animated packs.** Photos become static stickers; GIFs and video clips become animated ones. Packs follow WhatsApp's rules (3 to 30 stickers, 512×512 WebP, up to 100 KB static / 500 KB animated, up to 10 s).
- **Framing.** Drag, pinch and double-tap to fill or fit. The sticker comes out exactly as framed.
- **Background removal.**
  - *Flat color*: makes a plain background transparent, starting from the edges, with an adjustable tolerance.
  - *AI*: cuts a person, animal or object out of any background, on the device (ML Kit subject segmentation, downloaded from Google Play on first use).
- **Clip and speed** for animated stickers, with a looping preview.
- **Emojis** per sticker (WhatsApp uses them in search).
- **Share to create:** share an image, GIF or video from any app and pick "Create sticker".
- **Appearance:** System, Light, Dark and AMOLED themes, eight accent colors plus Material You, and animated backgrounds (Orbs, Aurora, Horizon, Radar, Depth or none).
- English and Brazilian Portuguese.

## Install

Download the APK from [Releases](https://github.com/tvnclr/Slap/releases). Android 9 or newer.

## Build

Requirements: JDK 17 and the Android SDK (platform 36).

```
./gradlew assembleRelease
```

Release signing reads `../Slap-signing/keystore.properties` (outside the repository); without it the build is unsigned.

`tvnclr-ui/` is the design system shared by tvnclr's apps (tokens, themes, components and backgrounds, in Jetpack Compose).
