#!/bin/bash
mkdir -p AppIcon.iconset
sips -z 16 16 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_16x16.png
sips -z 32 32 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_16x16@2x.png
sips -z 32 32 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_32x32.png
sips -z 64 64 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_32x32@2x.png
sips -z 128 128 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_128x128.png
sips -z 256 256 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_128x128@2x.png
sips -z 256 256 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_256x256.png
sips -z 512 512 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_256x256@2x.png
sips -z 512 512 src/main/resources/icons/app-icon-512.png --out AppIcon.iconset/icon_512x512.png
iconutil -c icns AppIcon.iconset -o src/main/resources/icons/app-icon.icns
rm -rf AppIcon.iconset