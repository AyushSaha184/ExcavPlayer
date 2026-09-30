#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status
set -e

SOURCE_LOGO="app/asset/Logo.webp"
RES_DIR="app/src/main/res"

if [ ! -f "$SOURCE_LOGO" ]; then
    echo "Error: Source logo not found at $SOURCE_LOGO"
    exit 1
fi

echo "Generating Android legacy and adaptive icons from $SOURCE_LOGO for grey theme (#101216)..."

# DPI: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi
# Legacy icon size: 48, 72, 96, 144, 192
# Adaptive foreground size: 108, 162, 216, 324, 432

DPI_LEVELS=("mdpi" "hdpi" "xhdpi" "xxhdpi" "xxxhdpi")
LEGACY_SIZES=(48 72 96 144 192)
FOREGROUND_SIZES=(108 162 216 324 432)

# Temporary directory for intermediate steps
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

# Convert source webp to 1024x1024 PNG master
magick "$SOURCE_LOGO" -resize 1024x1024 "$TEMP_DIR/logo_1024.png"

# Generate master adaptive foreground at 1024x1024
# Resizing target foreground content to 614x614 (60% safe zone) and centering on 1024x1024 transparent canvas
echo "Creating master adaptive foreground layer..."
magick "$TEMP_DIR/logo_1024.png" -fuzz 25% -transparent "#101216" -transparent "#07111C" -transparent "#080B11" -transparent black -resize 614x614 -background none -gravity center -extent 1024x1024 "$TEMP_DIR/adaptive_fg_master.png"

# Generate circular mask at 1024x1024 with antialiasing
magick -size 1024x1024 xc:transparent -fill white -draw "circle 512,512 512,1" "$TEMP_DIR/circle_mask.png"

for i in "${!DPI_LEVELS[@]}"; do
    DPI="${DPI_LEVELS[$i]}"
    LEGACY_SZ="${LEGACY_SIZES[$i]}"
    FG_SZ="${FOREGROUND_SIZES[$i]}"
    
    TARGET_MIPMAP_DIR="$RES_DIR/mipmap-$DPI"
    mkdir -p "$TARGET_MIPMAP_DIR"
    
    echo "Processing mipmap-$DPI..."
    
    # 1. Legacy Square Icon (ic_launcher.png)
    magick "$TEMP_DIR/logo_1024.png" -resize "${LEGACY_SZ}x${LEGACY_SZ}" "$TARGET_MIPMAP_DIR/ic_launcher.png"
    
    # 2. Legacy Round Icon (ic_launcher_round.png)
    magick "$TEMP_DIR/logo_1024.png" "$TEMP_DIR/circle_mask.png" -alpha Off -compose CopyOpacity -composite -resize "${LEGACY_SZ}x${LEGACY_SZ}" "$TARGET_MIPMAP_DIR/ic_launcher_round.png"
    
    # 3. Adaptive Foreground Icon (ic_launcher_foreground.png)
    magick "$TEMP_DIR/adaptive_fg_master.png" -resize "${FG_SZ}x${FG_SZ}" "$TARGET_MIPMAP_DIR/ic_launcher_foreground.png"
done

echo "All icons generated successfully!"
