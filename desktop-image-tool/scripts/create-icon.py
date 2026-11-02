"""Convert the existing 剧智创 brand mark into desktop PNG / multi-size ICO assets."""
from pathlib import Path
from PIL import Image, ImageOps

root = Path(__file__).resolve().parents[1] / 'assets'
source = root / 'juzhichuang-logo-mark.png'
mark = Image.open(source).convert('RGBA')
# Preserve the original mark's aspect ratio and alpha; only add transparent padding.
fitted = ImageOps.contain(mark, (960, 960), Image.Resampling.LANCZOS)
image = Image.new('RGBA', (1024, 1024))
image.alpha_composite(fitted, ((1024 - fitted.width) // 2, (1024 - fitted.height) // 2))
image.save(root / 'icon.png')
image.save(root / 'icon.ico', sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
print(root / 'icon.ico')
