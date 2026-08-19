#!/usr/bin/env python3
"""Render the deterministic README demo for AgentCompose 0.2.0."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


WIDTH, HEIGHT = 960, 540
OUT = Path(__file__).resolve().parents[1] / "docs" / "assets" / "agentcompose-demo.gif"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


def font(size: int, bold: bool = False, mono: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(MONO if mono else BOLD if bold else FONT, size)


def gradient() -> Image.Image:
    image = Image.new("RGB", (WIDTH, HEIGHT))
    pixels = image.load()
    start, end = (20, 14, 48), (40, 23, 84)
    for y in range(HEIGHT):
        for x in range(WIDTH):
            mix = (x / WIDTH) * 0.7 + (y / HEIGHT) * 0.3
            pixels[x, y] = tuple(int(a + (b - a) * mix) for a, b in zip(start, end))
    return image


def text(draw: ImageDraw.ImageDraw, xy: tuple[int, int], value: str, size: int,
         fill: str, bold: bool = False, mono: bool = False) -> None:
    draw.text(xy, value, font=font(size, bold=bold, mono=mono), fill=fill)


def wrapped(draw: ImageDraw.ImageDraw, xy: tuple[int, int], value: str, width: int,
            size: int, fill: str, bold: bool = False, spacing: int = 5) -> int:
    words, lines, current = value.split(), [], ""
    used_font = font(size, bold=bold)
    for word in words:
        candidate = f"{current} {word}".strip()
        if draw.textlength(candidate, font=used_font) <= width:
            current = candidate
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    x, y = xy
    for line in lines:
        draw.text((x, y), line, font=used_font, fill=fill)
        y += size + spacing
    return y


def pill(draw: ImageDraw.ImageDraw, xy: tuple[int, int, int, int], label: str,
         color: str, foreground: str = "#FFFFFF") -> None:
    draw.rounded_rectangle(xy, radius=13, fill=color)
    x1, y1, x2, y2 = xy
    label_font = font(12, bold=True)
    box = draw.textbbox((0, 0), label, font=label_font)
    tx = x1 + (x2 - x1 - (box[2] - box[0])) / 2
    ty = y1 + (y2 - y1 - (box[3] - box[1])) / 2 - 1
    draw.text((tx, ty), label, font=label_font, fill=foreground)


def phone(draw: ImageDraw.ImageDraw, stage: int, progress: float) -> None:
    px, py, pw, ph = 550, 20, 330, 500
    draw.rounded_rectangle((px - 5, py + 10, px + pw + 8, py + ph + 12), 46, fill="#0D0920")
    draw.rounded_rectangle((px, py, px + pw, py + ph), 42, fill="#1B1730", outline="#7462D8", width=2)
    draw.rounded_rectangle((px + 12, py + 12, px + pw - 12, py + ph - 12), 32, fill="#FAF8FF")
    draw.rounded_rectangle((px + 122, py + 17, px + 208, py + 27), 6, fill="#191527")

    sx, sy, sw = px + 26, py + 42, pw - 52
    text(draw, (sx, sy), "AgentCompose", 18, "#201A2C", bold=True)
    pill(draw, (sx + 173, sy - 2, sx + 260, sy + 24), "0.2.0", "#E9E1FF", "#5B3DB9")
    draw.line((sx, sy + 33, sx + sw, sy + 33), fill="#E7E0EC", width=1)

    # User prompt
    ux1, uy1, ux2, uy2 = sx + 60, sy + 48, sx + sw, sy + 94
    draw.rounded_rectangle((ux1, uy1, ux2, uy2), 15, fill="#EADDFF")
    text(draw, (ux1 + 13, uy1 + 12), "Build a secure agent UI", 13, "#38215E")

    if stage == 0:
        dots = "." * (1 + int(progress * 3) % 3)
        text(draw, (sx + 5, sy + 116), f"Streaming{dots}", 12, "#6F6680")
        return

    ay = sy + 110
    if stage >= 1:
        draw.rounded_rectangle((sx, ay, sx + sw, ay + 192), 16, fill="#F2ECF6")
        text(draw, (sx + 14, ay + 12), "Production ready", 15, "#21182D", bold=True)
        table_rows = [
            ("Firebase AI", "Cloud + tools"),
            ("ML Kit", "On-device"),
            ("Room", "Persistence"),
        ]
        visible = max(1, min(3, int(progress * 4)))
        text(draw, (sx + 14, ay + 42), "Module", 11, "#655A70", bold=True)
        text(draw, (sx + 128, ay + 42), "Mode", 11, "#655A70", bold=True)
        draw.line((sx + 12, ay + 62, sx + sw - 12, ay + 62), fill="#D8CFE0")
        for index, (name, mode) in enumerate(table_rows[:visible]):
            row_y = ay + 72 + index * 28
            text(draw, (sx + 14, row_y), name, 11, "#2A2231")
            text(draw, (sx + 128, row_y), mode, 11, "#2A2231")

        code_y = ay + 160
        draw.rounded_rectangle((sx + 10, code_y, sx + sw - 10, code_y + 64), 9, fill="#211B2C")
        text(draw, (sx + 20, code_y + 9), "kotlin", 9, "#AFA5BD", mono=True)
        text(draw, (sx + sw - 52, code_y + 9), "Copy", 9, "#BEA8FF", bold=True)
        snippet = "AgentChat(state = state)"
        chars = max(4, int(len(snippet) * progress))
        text(draw, (sx + 20, code_y + 34), snippet[:chars], 10, "#D6F7C5", mono=True)

    if stage >= 2:
        ty = py + 376
        draw.rounded_rectangle((sx, ty, sx + sw, ty + 82), 14, fill="#FFF7E4", outline="#E2B548")
        text(draw, (sx + 13, ty + 10), "Tool approval required", 12, "#4B3710", bold=True)
        text(draw, (sx + 13, ty + 31), "save_note  •  title: Ideas", 10, "#594C34", mono=True)
        if stage == 2:
            pill(draw, (sx + 120, ty + 53, sx + 183, ty + 77), "Deny", "#EEE7F0", "#4A424F")
            pill(draw, (sx + 190, ty + 53, sx + 259, ty + 77), "Approve", "#6750A4")
        else:
            pill(draw, (sx + 162, ty + 53, sx + 259, ty + 77), "✓ Saved", "#D9F4DD", "#256335")

    # Composer
    cy = py + ph - 49
    draw.rounded_rectangle((sx, cy, sx + sw, cy + 34), 17, outline="#978E9E", width=1)
    text(draw, (sx + 13, cy + 9), "Message the agent", 10, "#776E7E")
    draw.ellipse((sx + sw - 29, cy + 6, sx + sw - 6, cy + 29), fill="#6750A4")
    text(draw, (sx + sw - 23, cy + 7), "↑", 14, "#FFFFFF", bold=True)


def render() -> None:
    frames: list[Image.Image] = []
    stages = [(0, 6), (1, 13), (2, 7), (3, 8)]
    for stage, count in stages:
        for frame_index in range(count):
            image = gradient()
            draw = ImageDraw.Draw(image)

            # Brand panel
            draw.rounded_rectangle((52, 48, 108, 104), 17, fill="#7655E8")
            text(draw, (67, 61), "A", 28, "#FFFFFF", bold=True)
            text(draw, (52, 129), "AgentCompose", 34, "#FFFFFF", bold=True)
            text(draw, (53, 173), "PRODUCTION PACK", 13, "#BFAEFF", bold=True)
            wrapped(
                draw,
                (53, 207),
                "Build streaming Android AI experiences without rebuilding chat, tools, persistence, and tests.",
                405,
                19,
                "#EEE9FF",
                spacing=8,
            )
            features = ["Firebase AI", "Gemini Nano", "Room", "Testing"]
            for idx, feature in enumerate(features):
                col, row = idx % 2, idx // 2
                x, y = 53 + col * 150, 342 + row * 40
                draw.ellipse((x, y + 4, x + 16, y + 20), fill="#5ED6A7")
                text(draw, (x + 4, y + 3), "✓", 11, "#132B24", bold=True)
                text(draw, (x + 24, y + 2), feature, 14, "#FFFFFF", bold=True)
            pill(draw, (53, 449, 235, 481), "Apache-2.0 • Android", "#342763")
            text(draw, (53, 500), "github.com/mehulp89/agent-compose", 11, "#BDB3D8", mono=True)

            progress = (frame_index + 1) / count
            phone(draw, stage, progress)
            frames.append(image.quantize(colors=96, method=Image.Quantize.MEDIANCUT))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    durations = [110] * len(frames)
    durations[5] = 450
    durations[18] = 650
    durations[25] = 600
    durations[-1] = 1300
    frames[0].save(
        OUT,
        save_all=True,
        append_images=frames[1:],
        duration=durations,
        loop=0,
        optimize=True,
        disposal=2,
    )
    print(f"Rendered {OUT} ({OUT.stat().st_size / 1024:.0f} KiB)")


if __name__ == "__main__":
    render()
