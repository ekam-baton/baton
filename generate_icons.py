import os
from PIL import Image

def create_icons(input_path, output_dir):
    try:
        img = Image.open(input_path)
    except Exception as e:
        print(f"Error opening image: {e}")
        return

    # Convert to RGB if not
    if img.mode != 'RGB':
        img = img.convert('RGB')

    width, height = img.size
    print(f"Original size: {width}x{height}")

    # Center crop to square
    size = min(width, height)
    left = (width - size) / 2
    top = (height - size) / 2
    right = (width + size) / 2
    bottom = (height + size) / 2

    square_img = img.crop((left, top, right, bottom))

    # Also create a version with padding for adaptive foreground if needed
    # Wait, the user's image is a tall rectangle. If we center crop to square, 
    # we might cut off the top/bottom of the baton.
    # Let's check. Actually, instead of cropping, let's pad it to a square 
    # using the background color at the edge.
    
    # Get background color from top-left pixel
    bg_color = img.getpixel((0, 0))
    print(f"Background color detected: {bg_color}")
    
    # Create a new square image with the background color
    new_size = max(width, height)
    padded_img = Image.new('RGB', (new_size, new_size), bg_color)
    
    # Paste the original image in the center
    paste_x = (new_size - width) // 2
    paste_y = (new_size - height) // 2
    padded_img.paste(img, (paste_x, paste_y))

    # Now we have a perfect square with the entire baton visible
    
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    res_dir = os.path.join(output_dir, 'app', 'src', 'main', 'res')

    for mipmap, dim in sizes.items():
        folder = os.path.join(res_dir, mipmap)
        os.makedirs(folder, exist_ok=True)
        
        # Resize
        resized = padded_img.resize((dim, dim), Image.Resampling.LANCZOS)
        
        # Save ic_launcher.png
        resized.save(os.path.join(folder, 'ic_launcher.png'), 'PNG')
        
        # Save ic_launcher_round.png (we'll just use the same square but we can apply a circular mask)
        # For simplicity, we just save it as ic_launcher_round.png too (Android launcher can mask it)
        # Actually, let's create a circular mask
        mask = Image.new('L', (dim, dim), 0)
        from PIL import ImageDraw
        draw = ImageDraw.Draw(mask)
        draw.ellipse((0, 0, dim, dim), fill=255)
        
        round_img = resized.copy()
        round_img.putalpha(mask)
        round_img.save(os.path.join(folder, 'ic_launcher_round.png'), 'PNG')
        print(f"Created {mipmap} icons")

    # For adaptive icon (v26), we need a foreground and background.
    # We can create a foreground layer (padded image resized to 108x108)
    v26_dir = os.path.join(res_dir, 'mipmap-anydpi-v26')
    os.makedirs(v26_dir, exist_ok=True)
    
    fg_folder = os.path.join(res_dir, 'drawable')
    os.makedirs(fg_folder, exist_ok=True)
    
    fg_img = padded_img.resize((108, 108), Image.Resampling.LANCZOS)
    fg_img.save(os.path.join(fg_folder, 'ic_launcher_foreground.png'), 'PNG')
    print("Created ic_launcher_foreground.png")
    
    # Write ic_launcher.xml
    xml_content = f"""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""
    with open(os.path.join(v26_dir, 'ic_launcher.xml'), 'w') as f:
        f.write(xml_content)
    with open(os.path.join(v26_dir, 'ic_launcher_round.xml'), 'w') as f:
        f.write(xml_content)
        
    # Write colors.xml for background
    values_dir = os.path.join(res_dir, 'values')
    os.makedirs(values_dir, exist_ok=True)
    color_hex = f"#{bg_color[0]:02x}{bg_color[1]:02x}{bg_color[2]:02x}"
    colors_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">{color_hex}</color>
</resources>
"""
    with open(os.path.join(values_dir, 'ic_launcher_colors.xml'), 'w') as f:
        f.write(colors_xml)
        
    print("All icons successfully generated.")

if __name__ == "__main__":
    input_image = r"C:\Users\yrish\.gemini\antigravity\brain\7f8f3f0a-cb83-4984-a703-10ac0984b1f5\media__1781766309478.jpg"
    target_dir = r"C:\Users\yrish\.gemini\antigravity\scratch\baton"
    create_icons(input_image, target_dir)
