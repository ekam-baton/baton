import os
import shutil
import xml.etree.ElementTree as ET
from PIL import Image

scratch_dir = r'C:\Users\yrish\.gemini\antigravity\scratch'
brain_dir = r'C:\Users\yrish\.gemini\antigravity\brain\2bf7cb47-1def-49fd-a5b8-7fc094cece6e'
master_png = os.path.join(brain_dir, '.user_uploaded', 'media_1791874372144_a098ab02.png')
master_jpg = os.path.join(brain_dir, '.user_uploaded', 'media_1791874387698_fd611d11.jpg')

repo_baton = os.path.join(scratch_dir, 'baton')
with open(os.path.join(repo_baton, 'website', 'logo.svg'), 'r', encoding='utf-8') as f:
    baton_svg_raw = f.read()

tree = ET.fromstring(baton_svg_raw)
path_elem = tree.find('.//{http://www.w3.org/2000/svg}path')
path_d = path_elem.get('d')

tile_svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <rect width="512" height="512" rx="96" fill="#000000"/>
  <path fill="#FFFFFF" d="{path_d}"/>
</svg>'''

trans_svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <path fill="#FFFFFF" d="{path_d}"/>
</svg>'''

# 1. Update baton-docs (powers https://ekam-baton.github.io/baton-docs/)
docs_dir = os.path.join(scratch_dir, 'baton-docs')
if os.path.exists(docs_dir):
    with open(os.path.join(docs_dir, 'src', 'assets', 'baton-logo.svg'), 'w', encoding='utf-8') as f:
        f.write(tile_svg)
    with open(os.path.join(docs_dir, 'public', 'favicon.svg'), 'w', encoding='utf-8') as f:
        f.write(tile_svg)
    with open(os.path.join(docs_dir, 'public', 'baton-logo.svg'), 'w', encoding='utf-8') as f:
        f.write(tile_svg)
    
    shutil.copy2(master_png, os.path.join(docs_dir, 'public', 'baton_logo.png'))
    shutil.copy2(master_png, os.path.join(docs_dir, 'public', 'baton-logo.png'))
    shutil.copy2(master_jpg, os.path.join(docs_dir, 'public', 'baton-logo.jpg'))
    print('Updated baton-docs assets!')

# 2. Update baton-Desktop-App (desktop-ui)
desktop_dir = os.path.join(scratch_dir, 'baton-Desktop-App', 'desktop-ui')
if os.path.exists(desktop_dir):
    shutil.copy2(master_png, os.path.join(desktop_dir, 'public', 'logo.png'))
    shutil.copy2(master_jpg, os.path.join(desktop_dir, 'public', 'logo.jpg'))
    if os.path.exists(os.path.join(desktop_dir, 'src', 'assets')):
        shutil.copy2(master_png, os.path.join(desktop_dir, 'src', 'assets', 'logo.png'))
    
    # Tauri icons
    tauri_icons = os.path.join(desktop_dir, 'src-tauri', 'icons')
    if os.path.exists(tauri_icons):
        base_img = Image.open(master_jpg).convert('RGBA')
        sizes = {
            '32x32.png': (32, 32),
            '64x64.png': (64, 64),
            '128x128.png': (128, 128),
            '128x128@2x.png': (256, 256),
            'icon.png': (512, 512),
            'Square30x30Logo.png': (30, 30),
            'Square44x44Logo.png': (44, 44),
            'Square71x71Logo.png': (71, 71),
            'Square89x89Logo.png': (89, 89),
            'Square107x107Logo.png': (107, 107),
            'Square142x142Logo.png': (142, 142),
            'Square150x150Logo.png': (150, 150),
            'Square284x284Logo.png': (284, 284),
            'Square310x310Logo.png': (310, 310),
            'StoreLogo.png': (50, 50)
        }
        for name, sz in sizes.items():
            resized = base_img.resize(sz, Image.Resampling.LANCZOS)
            resized.save(os.path.join(tauri_icons, name))
        
        # icon.ico
        base_img.save(os.path.join(tauri_icons, 'icon.ico'), format='ICO', sizes=[(16,16), (32,32), (48,48), (64,64), (128,128), (256,256)])
    print('Updated baton-Desktop-App icons!')

# 3. Update baton-marketing-site
mkt_dir = os.path.join(scratch_dir, 'baton-marketing-site')
if os.path.exists(mkt_dir):
    with open(os.path.join(mkt_dir, 'public', 'favicon.svg'), 'w', encoding='utf-8') as f:
        f.write(tile_svg)
    print('Updated baton-marketing-site favicon!')

# 4. Update baton-web-client
web_dir = os.path.join(scratch_dir, 'baton-web-client')
if os.path.exists(web_dir):
    with open(os.path.join(web_dir, 'public', 'favicon.svg'), 'w', encoding='utf-8') as f:
        f.write(tile_svg)
    print('Updated baton-web-client favicon!')

print('ALL CROSS-PLATFORM LOGOS UPDATED SUCCESSFULLY!')
