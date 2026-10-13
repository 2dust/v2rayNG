#!/bin/bash
set -e

# Base files
MASTER="/tmp/bng_logo_master.png"
# Create transparent foreground with glowing letters for adaptive icon foreground
# Re-use letters_slanted and glow_combined on transparent background
convert /tmp/glow_combined.png /tmp/letters_slanted.png -gravity center -composite /tmp/bng_logo_fg.png

# Copy full logo to drawable
cp "$MASTER" app/src/main/res/drawable/bng_logo.png
cp /tmp/bng_logo_fg.png app/src/main/res/drawable/bng_logo_fg.png

# Function to generate circular mask for round launcher icons
generate_round() {
    local src="$1"
    local size="$2"
    local out="$3"
    convert "$src" -resize "${size}x${size}" \
        \( +clone -alpha extract -draw "fill black polygon 0,0 0,${size} ${size},${size} ${size},0 fill white circle $((size/2)),$((size/2)) $((size/2)),1" \) \
        -alpha off -compose CopyOpacity -composite "$out"
}

# Sizes:
# mdpi: 48x48, fg: 108x108
convert "$MASTER" -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher.png
generate_round "$MASTER" 48 app/src/main/res/mipmap-mdpi/ic_launcher_round.png
convert /tmp/bng_logo_fg.png -resize 72x72 -gravity center -background none -extent 108x108 app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png

# hdpi: 72x72, fg: 162x162
convert "$MASTER" -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher.png
generate_round "$MASTER" 72 app/src/main/res/mipmap-hdpi/ic_launcher_round.png
convert /tmp/bng_logo_fg.png -resize 108x108 -gravity center -background none -extent 162x162 app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png

# xhdpi: 96x96, fg: 216x216
convert "$MASTER" -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher.png
generate_round "$MASTER" 96 app/src/main/res/mipmap-xhdpi/ic_launcher_round.png
convert /tmp/bng_logo_fg.png -resize 144x144 -gravity center -background none -extent 216x216 app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png

# xxhdpi: 144x144, fg: 324x324
convert "$MASTER" -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher.png
generate_round "$MASTER" 144 app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png
convert /tmp/bng_logo_fg.png -resize 216x216 -gravity center -background none -extent 324x324 app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png

# xxxhdpi: 192x192, fg: 432x432
convert "$MASTER" -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
generate_round "$MASTER" 192 app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png
convert /tmp/bng_logo_fg.png -resize 288x288 -gravity center -background none -extent 432x432 app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png

echo "All launcher icons generated successfully!"
