import sys
from PIL import Image
import os

img_path = sys.argv[1]
output_dir = sys.argv[2]

img = Image.open(img_path)
img = img.resize((192, 192), Image.Resampling.LANCZOS)
img.save(os.path.join(output_dir, "ic_launcher.png"), "PNG")
