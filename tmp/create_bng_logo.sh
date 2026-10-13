#!/bin/bash
set -e

FONT="/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SIZE=512

# 1. Create Background (Radial gradient with dark navy glow)
convert -size 512x512 radial-gradient:'#141D3A'-'#060911' /tmp/bg.png

# 2. Render each letter with individual gradient and styling on transparent canvas
# Letter B (Cyan/Electric Blue)
convert -size 512x512 xc:none \
  -font "$FONT" -pointsize 135 \
  -fill '#00F0FF' \
  -stroke '#38E8FF' -strokewidth 4 \
  -annotate +70+310 "B" \
  /tmp/letter_b.png

# Letter N (Blue-Violet to Purple)
convert -size 512x512 xc:none \
  -font "$FONT" -pointsize 135 \
  -fill '#8A2BE2' \
  -stroke '#A855F7' -strokewidth 4 \
  -annotate +190+310 "N" \
  /tmp/letter_n.png

# Letter G (Magenta / Hot Pink)
convert -size 512x512 xc:none \
  -font "$FONT" -pointsize 135 \
  -fill '#FF1493' \
  -stroke '#FF66B2' -strokewidth 4 \
  -annotate +325+310 "G" \
  /tmp/letter_g.png

# 3. Combine letters into single layer
convert /tmp/letter_b.png /tmp/letter_n.png -composite /tmp/letter_g.png -composite /tmp/letters.png

# 4. Apply 12-degree italic shear to match the slanted BNG esport style
convert /tmp/letters.png -affine 1,0,-0.22,1,0,0 -transform /tmp/letters_slanted.png

# 5. Create multi-stage neon glow layers
# Broad atmospheric glow
convert /tmp/letters_slanted.png \( +clone -background '#00E5FF' -shadow 90x20+0+0 \) +swap -composite /tmp/glow1.png
convert /tmp/glow1.png \( +clone -background '#FF007F' -shadow 90x28+0+0 \) +swap -composite /tmp/glow2.png
# Crisp intense core glow
convert /tmp/glow2.png \( +clone -background white -shadow 80x6+0+0 \) +swap -composite /tmp/glow_combined.png

# 6. Composite the letters on top of glow and background
convert /tmp/bg.png /tmp/glow_combined.png -gravity center -composite /tmp/step1.png
convert /tmp/step1.png /tmp/letters_slanted.png -gravity center -composite /tmp/bng_logo_master.png

echo "Master logo generated successfully!"
ls -lh /tmp/bng_logo_master.png
