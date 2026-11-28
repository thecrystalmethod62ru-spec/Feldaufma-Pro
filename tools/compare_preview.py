from pathlib import Path
from PIL import Image, ImageChops, ImageOps, ImageDraw
import json

root = Path("ui-screenshots")
out = root / "comparison"
out.mkdir(parents=True, exist_ok=True)
report = {}
for name, filename in [("drawing", "04-full-portrait.png"), ("profile", "06-regelprofil.png")]:
    reference = Path("preview-reference") / (name + ".png")
    actual = root / filename
    if not reference.exists() or not actual.exists():
        report[name] = "Missing approved reference or actual screenshot"
        continue
    a = Image.open(reference).convert("RGB")
    b = Image.open(actual).convert("RGB")
    # Keep aspect ratio; pad rather than distort. Reference crops must contain only the app viewport.
    size = a.size
    b = ImageOps.pad(b, size, method=Image.Resampling.LANCZOS, color="black")
    overlay = Image.blend(a, b, 0.5)
    delta = ImageChops.difference(a, b).convert("L")
    heat = Image.merge("RGB", (delta.point(lambda x: min(255, x*3)), Image.new("L", size), Image.new("L", size)))
    for label, im in [("reference", a), ("actual-aligned", b), ("overlay", overlay), ("difference", heat)]:
        im.save(out / (name + "-" + label + ".png"))
    board = Image.new("RGB", (size[0]*4, size[1]+28), "#121212")
    draw = ImageDraw.Draw(board)
    for i, (label, im) in enumerate(zip(["Reference", "Actual", "50/50 overlay", "Pixel difference"], [a,b,overlay,heat])):
        draw.text((i*size[0]+5, 6), label, fill="white")
        board.paste(im, (i*size[0],28))
    board.save(out / (name + "-comparison.png"))
    report[name] = "Compared; pixel differences include content, scaling and system UI, not only layout defects"
(out / "report.json").write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
