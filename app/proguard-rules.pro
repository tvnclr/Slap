# WhatsApp reads the sticker provider through its manifest declaration (kept by AAPT rules).
# Nothing else is accessed by reflection.

# Segmenters looks ML Kit up by name (the class is absent in the fdroid branch).
-keep class dev.tvnclr.slap.engine.MlKitSegmenter { public <init>(); }
-dontwarn dev.tvnclr.slap.engine.MlKitSegmenter
