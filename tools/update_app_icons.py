import os
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFilter

# Locate repository root
repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
svg_path = os.path.join(repo_root, 'website', 'logo.svg')
tree = ET.parse(svg_path)
root = tree.getroot()
path_elem = root.find('.//{http://www.w3.org/2000/svg}path')
path_data = path_elem.get('d')

print('Loaded exact path data from repo, length:', len(path_data))

# 1. Update app/src/main/res/drawable/ic_launcher_foreground.xml
app_res = os.path.join(repo_root, 'app', 'src', 'main', 'res')
ic_launcher_fg_path = os.path.join(app_res, 'drawable', 'ic_launcher_foreground.xml')

launcher_fg_xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="512"
    android:viewportHeight="512">

    <!-- Baton Conductor Logo in Safe Zone -->
    <group
        android:pivotX="258"
        android:pivotY="258"
        android:scaleX="0.82"
        android:scaleY="0.82">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="{path_data}" />
    </group>
</vector>
'''

with open(ic_launcher_fg_path, 'w', encoding='utf-8') as f:
    f.write(launcher_fg_xml)
print('Updated ic_launcher_foreground.xml')

# 2. Update app/src/main/res/drawable/ic_logo_vector.xml
ic_logo_vector_path = os.path.join(app_res, 'drawable', 'ic_logo_vector.xml')
logo_vector_xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="512"
    android:viewportHeight="512">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="{path_data}" />
</vector>
'''
with open(ic_logo_vector_path, 'w', encoding='utf-8') as f:
    f.write(logo_vector_xml)
print('Updated ic_logo_vector.xml')

# 3. Update ic_baton_logo.xml in app/src/main/res/drawable/
app_baton_logo_xml = os.path.join(app_res, 'drawable', 'ic_baton_logo.xml')
with open(app_baton_logo_xml, 'w', encoding='utf-8') as f:
    f.write(logo_vector_xml)
print('Updated app drawable ic_baton_logo.xml')

# 4. Generate high-quality PNGs for ic_logo.png and ic_splash_logo.png
src_png_path = os.path.join(repo_root, 'website', 'ic_baton_logo.png')
base_png = Image.open(src_png_path).convert('RGBA')

# Save directly to ic_logo.png, ic_splash_logo.png
ic_logo_png_path = os.path.join(app_res, 'drawable', 'ic_logo.png')
ic_splash_logo_path = os.path.join(app_res, 'drawable', 'ic_splash_logo.png')
base_png.save(ic_logo_png_path)
base_png.save(ic_splash_logo_path)
print('Updated ic_logo.png and ic_splash_logo.png')

# 5. Generate Mipmap Icons
# Mipmap sizes for Android:
# adaptive foreground: (108, 162, 216, 324, 432)
# legacy / round: (48, 72, 96, 144, 192)
densities = {
    'mipmap-mdpi': (108, 48),
    'mipmap-hdpi': (162, 72),
    'mipmap-xhdpi': (216, 96),
    'mipmap-xxhdpi': (324, 144),
    'mipmap-xxxhdpi': (432, 192)
}

bg_color = (10, 15, 29, 255) # Cyber void #0A0F1D

for folder, (fg_size, icon_size) in densities.items():
    folder_path = os.path.join(app_res, folder)
    os.makedirs(folder_path, exist_ok=True)
    
    # 1. ic_launcher_foreground.png (transparent, baton scaled to 80% of safe circle)
    fg_canvas = Image.new('RGBA', (fg_size, fg_size), (0, 0, 0, 0))
    # baton height in safe zone (safe zone is 66% of fg_size)
    target_h = int(fg_size * 0.66 * 0.85)
    # aspect ratio of baton is 35w x 308h = ~1:8.8
    scale = target_h / 512.0
    scaled_w = int(512 * scale)
    scaled_h = int(512 * scale)
    resized_baton = base_png.resize((scaled_w, scaled_h), Image.Resampling.LANCZOS)
    
    px = (fg_size - scaled_w) // 2
    py = (fg_size - scaled_h) // 2
    fg_canvas.paste(resized_baton, (px, py), resized_baton)
    fg_canvas.save(os.path.join(folder_path, 'ic_launcher_foreground.png'))
    
    # 2. ic_launcher.png (square / squircle legacy icon on void background)
    icon_canvas = Image.new('RGBA', (icon_size, icon_size), bg_color)
    target_icon_h = int(icon_size * 0.75)
    scale_icon = target_icon_h / 512.0
    sw = int(512 * scale_icon)
    sh = int(512 * scale_icon)
    resized_icon_baton = base_png.resize((sw, sh), Image.Resampling.LANCZOS)
    ipx = (icon_size - sw) // 2
    ipy = (icon_size - sh) // 2
    icon_canvas.paste(resized_icon_baton, (ipx, ipy), resized_icon_baton)
    icon_canvas.save(os.path.join(folder_path, 'ic_launcher.png'))
    
    # 3. ic_launcher_round.png (circular masked legacy icon)
    round_canvas = Image.new('RGBA', (icon_size, icon_size), (0, 0, 0, 0))
    # Draw circular background
    mask = Image.new('L', (icon_size, icon_size), 0)
    draw_mask = ImageDraw.Draw(mask)
    draw_mask.ellipse((0, 0, icon_size - 1, icon_size - 1), fill=255)
    round_canvas.paste(icon_canvas, (0, 0), mask)
    round_canvas.save(os.path.join(folder_path, 'ic_launcher_round.png'))

print('All mipmap densities generated successfully!')
