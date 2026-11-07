# Sprite/font data is loaded with Class.getResourceAsStream("/assets/...");
# keep the game classes' names so resource lookups and stack traces stay readable.
-keep class org.digger.** { *; }
