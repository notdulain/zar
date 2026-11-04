"""Render the unchanged Figma Zar mark. Requires cairosvg and Pillow."""
from pathlib import Path
from io import BytesIO
import xml.etree.ElementTree as ET
import cairosvg
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[2]
res = root / 'app/src/main/res'
source = root / 'app/src/main/assets/brand/zar-mark.svg'
svg = ET.parse(source).getroot()
ns = {'s': 'http://www.w3.org/2000/svg'}
path = svg.find('.//s:path', ns).attrib['d']
# Preserve the source paths and rounded gold dot, with its original rotation.
mark = f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="64.08dp" android:height="105.12dp"
    android:viewportWidth="64.08" android:viewportHeight="105.12">
    <path android:fillColor="#FFFFFF" android:pathData="{path}" />
    <group android:rotation="-45" android:pivotX="37.2247" android:pivotY="15.2266">
        <path android:fillColor="#E2AE4F" android:pathData="M38.9527,15.2266 H49.8967 Q51.6247,15.2266 51.6247,16.9546 V27.8986 Q51.6247,29.6266 49.8967,29.6266 H38.9527 Q37.2247,29.6266 37.2247,27.8986 V16.9546 Q37.2247,15.2266 38.9527,15.2266 Z" />
    </group>
</vector>
'''
(res / 'drawable/zar_mark.xml').write_text(mark)
# Tall mark fits entirely inside the 66dp adaptive safe circle, including round/squircle masks.
foreground = mark.replace('android:width="64.08dp" android:height="105.12dp"', 'android:width="108dp" android:height="108dp"').replace('android:viewportWidth="64.08" android:viewportHeight="105.12"', 'android:viewportWidth="108" android:viewportHeight="108"').replace('    <path', '    <group android:scaleX="0.55" android:scaleY="0.55" android:translateX="36.378" android:translateY="25.092">\n    <path', 1).replace('</vector>', '    </group>\n</vector>')
(res / 'drawable/ic_launcher_foreground.xml').write_text(foreground)
(res / 'drawable/ic_launcher_monochrome.xml').write_text(foreground.replace('#E2AE4F', '#FFFFFF'))
(res / 'drawable/ic_launcher_background.xml').write_text('<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="#05070B" /></shape>\n')
for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    mark_height = round(size * .58)
    mark_width = round(mark_height * 64.08 / 105.12)
    image = Image.open(BytesIO(cairosvg.svg2png(url=str(source), output_width=mark_width, output_height=mark_height))).convert('RGBA')
    for name, radius in [('ic_launcher',size*.22), ('ic_launcher_round',size/2)]:
        icon = Image.new('RGBA', (size,size))
        ImageDraw.Draw(icon).rounded_rectangle((0,0,size-1,size-1), radius=radius, fill='#05070B')
        icon.alpha_composite(image, ((size-mark_width)//2,(size-mark_height)//2))
        icon.save(res / f'mipmap-{density}/{name}.webp', lossless=True)
