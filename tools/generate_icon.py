#!/usr/bin/env python3
"""
Generate LANboard app icon PNGs at all Android density buckets.

Replicates the design from lanboard-design.html:
- Ring renderer in "listening" state with curated spike positions
- Mic icon composited over the ring
- Dark gradient background with subtle cyan inner glow
- Adaptive icon layers (foreground + background)
"""

import math
from PIL import Image, ImageDraw, ImageFilter

# Curated spike positions from lanboard-design.html renderStaticIcon()
SPIKE_POSITIONS = [
    (8, 0.55), (14, 0.45), (22, 0.7), (35, 0.4),
    (48, 0.6), (58, 0.5), (71, 0.65), (85, 0.42),
    (95, 0.55), (108, 0.48), (118, 0.6),
]
NUM_SAMPLES = 128
MAX_SPIKE_FRAC = 0.32

# Colors from spec
CYAN = (0, 212, 255)
ELECTRIC_BLUE = (43, 127, 255)
DEEP_BLUE = (24, 81, 212)
BG_DARK = (10, 13, 16)       # #0a0d10
BG_LIGHT = (26, 34, 48)      # #1a2230
MIC_COLOR = (230, 237, 243)   # #e6edf3

# Android density buckets: name -> icon size in px
DENSITY_BUCKETS = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# Adaptive icon is 108dp; foreground safe zone is 72dp centered.
# We render at xxxhdpi (4x) so 432px canvas, 288px safe zone.
ADAPTIVE_SIZE = 432


def build_spikes():
    spikes = [0.0] * NUM_SAMPLES
    for idx, amp in SPIKE_POSITIONS:
        spikes[idx] = max(spikes[idx], amp)
        left = (idx - 1) % NUM_SAMPLES
        right = (idx + 1) % NUM_SAMPLES
        spikes[left] = max(spikes[left], amp * 0.55)
        spikes[right] = max(spikes[right], amp * 0.55)
    return spikes


def lerp_color(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))


def draw_gradient_bg(img):
    """Dark diagonal gradient background: #1a2230 top-left to #0a0d10 bottom-right."""
    draw = ImageDraw.Draw(img)
    w, h = img.size
    for y in range(h):
        for x in range(w):
            t = (x / w * 0.6 + y / h * 0.4)
            c = lerp_color(BG_LIGHT, BG_DARK, t)
            draw.point((x, y), fill=c)


def draw_gradient_bg_fast(size):
    """Faster gradient using line-by-line rendering."""
    img = Image.new("RGBA", (size, size), BG_DARK + (255,))
    draw = ImageDraw.Draw(img)
    for y in range(size):
        t = y / size
        base_t = t * 0.55 + 0.15
        c = lerp_color(BG_LIGHT, BG_DARK, base_t)
        draw.line([(0, y), (size - 1, y)], fill=c + (255,))
    return img


def draw_inner_glow(img, cx, cy, radius):
    """Subtle cyan radial glow in the center."""
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(glow)
    max_r = int(radius * 1.3)
    for r in range(max_r, 0, -1):
        t = r / max_r
        alpha = int(18 * (1 - t) ** 2)
        if alpha < 1:
            continue
        draw.ellipse(
            [cx - r, cy - r, cx + r, cy + r],
            fill=CYAN + (alpha,),
        )
    img.paste(Image.alpha_composite(Image.new("RGBA", img.size, (0, 0, 0, 0)), glow), (0, 0), glow)


def draw_ring(draw, cx, cy, base_radius, spikes, line_width, size):
    """Draw the spike-modulated ring with cyan-to-blue gradient."""
    max_spike_out = base_radius * MAX_SPIKE_FRAC

    points = []
    for i in range(NUM_SAMPLES):
        angle = (i / NUM_SAMPLES) * math.pi * 2 - math.pi / 2
        r = base_radius + spikes[i] * max_spike_out
        x = cx + math.cos(angle) * r
        y = cy + math.sin(angle) * r
        points.append((x, y))

    # Draw baseline circle (cyan, semi-transparent)
    for a in range(3600):
        angle = a / 3600 * math.pi * 2
        for offset in range(-max(1, line_width // 3), max(1, line_width // 3) + 1):
            r = base_radius + offset * 0.5
            x = cx + math.cos(angle) * r
            y = cy + math.sin(angle) * r
            draw.point((int(x), int(y)), fill=CYAN + (100,))

    # Draw spike-modulated path
    for i in range(len(points)):
        x1, y1 = points[i]
        x2, y2 = points[(i + 1) % len(points)]
        # Color gradient based on position along the ring
        t = i / len(points)
        if t < 0.5:
            c = lerp_color(CYAN, ELECTRIC_BLUE, t * 2)
        else:
            c = lerp_color(ELECTRIC_BLUE, DEEP_BLUE, (t - 0.5) * 2)
        draw.line([(x1, y1), (x2, y2)], fill=c + (220,), width=max(1, line_width))


def draw_ring_aa(img, cx, cy, base_radius, spikes, line_width):
    """Draw the ring on a 2x supersampled layer, then downscale for antialiasing."""
    size = img.size[0]
    ss = 2
    ss_size = size * ss
    layer = Image.new("RGBA", (ss_size, ss_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw_ring(draw, cx * ss, cy * ss, base_radius * ss, spikes, line_width * ss, ss_size)
    layer = layer.resize((size, size), Image.LANCZOS)
    return layer


def draw_mic_icon(size):
    """Draw the mic SVG as a raster image."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Scale factor: SVG viewBox is 24x24
    s = size / 24.0

    # Mic body: rounded rect from (9,3) to (15,14), rx=3
    body_x1, body_y1 = 9 * s, 3 * s
    body_x2, body_y2 = 15 * s, 14 * s
    body_rx = 3 * s
    draw.rounded_rectangle(
        [body_x1, body_y1, body_x2, body_y2],
        radius=body_rx,
        fill=MIC_COLOR + (255,),
    )

    # Mic arc: path d="M5 11v2a7 7 0 0 0 14 0v-2"
    arc_cx, arc_cy = 12 * s, 13 * s
    arc_r = 7 * s
    stroke_w = max(1, int(2 * s))
    draw.arc(
        [arc_cx - arc_r, arc_cy - arc_r, arc_cx + arc_r, arc_cy + arc_r],
        start=0, end=180,
        fill=MIC_COLOR + (255,),
        width=stroke_w,
    )
    # Vertical lines on sides
    draw.line([(5 * s, 11 * s), (5 * s, 13 * s)], fill=MIC_COLOR + (255,), width=stroke_w)
    draw.line([(19 * s, 11 * s), (19 * s, 13 * s)], fill=MIC_COLOR + (255,), width=stroke_w)

    # Stem: line from (12,20) to (12,22)
    draw.line([(12 * s, 20 * s), (12 * s, 22 * s)], fill=MIC_COLOR + (255,), width=stroke_w)

    return img


def generate_legacy_icon(size):
    """Generate a complete icon at the given pixel size."""
    spikes = build_spikes()

    # Background
    img = draw_gradient_bg_fast(size)

    # Inner glow
    cx, cy = size // 2, size // 2
    base_radius = int(size * 0.38)
    draw_inner_glow(img, cx, cy, base_radius)

    # Ring
    line_width = max(1, size // 48)
    ring_layer = draw_ring_aa(img, cx, cy, base_radius, spikes, line_width)
    img = Image.alpha_composite(img, ring_layer)

    # Add glow effect to ring
    glow_layer = ring_layer.filter(ImageFilter.GaussianBlur(radius=max(1, size // 30)))
    img = Image.alpha_composite(img, glow_layer)

    # Mic icon
    mic_size = int(size * 0.35)
    mic = draw_mic_icon(mic_size)
    mic_x = (size - mic_size) // 2
    mic_y = (size - mic_size) // 2
    img.paste(mic, (mic_x, mic_y), mic)

    return img.convert("RGBA")


def generate_adaptive_foreground():
    """Generate the adaptive icon foreground layer (ring + mic on transparent)."""
    size = ADAPTIVE_SIZE
    spikes = build_spikes()
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    cx, cy = size // 2, size // 2
    # Safe zone is 66.67% of 108dp = 72dp. At our resolution: 288px.
    # Ring fits within safe zone.
    base_radius = int(size * 0.25)  # ~108px, well within 144px safe radius

    line_width = 4
    ring_layer = draw_ring_aa(img, cx, cy, base_radius, spikes, line_width)
    img = Image.alpha_composite(img, ring_layer)

    glow_layer = ring_layer.filter(ImageFilter.GaussianBlur(radius=6))
    img = Image.alpha_composite(img, glow_layer)

    mic_size = int(size * 0.22)
    mic = draw_mic_icon(mic_size)
    mic_x = (size - mic_size) // 2
    mic_y = (size - mic_size) // 2
    img.paste(mic, (mic_x, mic_y), mic)

    return img


def generate_adaptive_background():
    """Generate the adaptive icon background layer (dark gradient + glow)."""
    size = ADAPTIVE_SIZE
    img = draw_gradient_bg_fast(size)
    cx, cy = size // 2, size // 2
    draw_inner_glow(img, cx, cy, int(size * 0.3))
    return img


def main():
    import os

    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    res_dir = os.path.join(base, "app", "src", "main", "res")

    # Generate legacy icons at all density buckets
    for density, size in DENSITY_BUCKETS.items():
        mipmap_dir = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(mipmap_dir, exist_ok=True)

        icon = generate_legacy_icon(size)
        icon_rgb = icon.convert("RGB")

        icon_rgb.save(os.path.join(mipmap_dir, "ic_launcher.png"))
        icon_rgb.save(os.path.join(mipmap_dir, "ic_launcher_round.png"))
        print(f"  {density}: {size}x{size} -> {mipmap_dir}")

    # Generate adaptive icon layers at xxxhdpi (432px for 108dp at 4x)
    fg = generate_adaptive_foreground()
    bg = generate_adaptive_background()

    # Save adaptive layers as PNGs in drawable directories
    for density, scale in [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]:
        layer_size = int(108 * scale)

        drawable_dir = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(drawable_dir, exist_ok=True)

        fg_resized = fg.resize((layer_size, layer_size), Image.LANCZOS)
        bg_resized = bg.resize((layer_size, layer_size), Image.LANCZOS)

        fg_resized.save(os.path.join(drawable_dir, "ic_launcher_foreground.png"))
        bg_resized.save(os.path.join(drawable_dir, "ic_launcher_background.png"))
        print(f"  {density}: adaptive layers {layer_size}x{layer_size}")

    print("Done! Icons generated at all density buckets.")


if __name__ == "__main__":
    main()
