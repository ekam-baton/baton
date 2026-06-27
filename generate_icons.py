import os
import glob
from PIL import Image

def create_icons(source_path, res_dir):
    try:
        img = Image.open(source_path).convert("RGBA")
    except Exception as e:
        print(f"Error opening image: {e}")
        return

    # Create a square canvas using the background color
    width, height = img.size
    bg_color = img.getpixel((0, 0))
    
    # We want the foreground to fit within the safe zone of adaptive icons (66% of the icon size).
    # For a 108x108 adaptive icon, the safe zone is a 72dp diameter circle.
    # The baton is tall, so we need to make sure its height fits within that safe zone.
    # Let's create a square image where the original image's height is scaled down to fit 50% of the new square's height.
    
    target_size = 1024
    square = Image.new("RGBA", (target_size, target_size), bg_color)
    
    # Scale the original image so its height is about 60% of target_size
    scale_factor = (target_size * 0.6) / height
    new_w = int(width * scale_factor)
    new_h = int(height * scale_factor)
    
    resized_img = img.resize((new_w, new_h), Image.LANCZOS)
    
    # Paste in the center
    paste_x = (target_size - new_w) // 2
    paste_y = (target_size - new_h) // 2
    square.paste(resized_img, (paste_x, paste_y), resized_img)
    
    # Also save a pure background for the adaptive background layer
    bg_layer = Image.new("RGBA", (target_size, target_size), bg_color)
    
    # Now generate the mipmap sizes
    # Sizes for adaptive icons (foreground and background):
    # mdpi: 108x108
    # hdpi: 162x162
    # xhdpi: 216x216
    # xxhdpi: 324x324
    # xxxhdpi: 432x432
    
    # Legacy icon sizes:
    # mdpi: 48x48
    # hdpi: 72x72
    # xhdpi: 96x96
    # xxhdpi: 144x144
    # xxxhdpi: 192x192
    
    sizes = {
        'mdpi': (108, 48),
        'hdpi': (162, 72),
        'xhdpi': (216, 96),
        'xxhdpi': (324, 144),
        'xxxhdpi': (432, 192)
    }
    
    for dpi, (adaptive_size, legacy_size) in sizes.items():
        dpi_dir = os.path.join(res_dir, f'mipmap-{dpi}')
        os.makedirs(dpi_dir, exist_ok=True)
        
        # Adaptive Foreground
        fg = square.resize((adaptive_size, adaptive_size), Image.LANCZOS)
        fg.save(os.path.join(dpi_dir, 'ic_launcher_foreground.png'))
        
        # Adaptive Background
        bg = bg_layer.resize((adaptive_size, adaptive_size), Image.LANCZOS)
        bg.save(os.path.join(dpi_dir, 'ic_launcher_background.png'))
        
        # Legacy Icon
        # Scale the original square to the legacy size
        legacy = square.resize((legacy_size, legacy_size), Image.LANCZOS)
        legacy.save(os.path.join(dpi_dir, 'ic_launcher.png'))
        legacy.save(os.path.join(dpi_dir, 'ic_launcher_round.png'))
        
    print("Icons generated successfully!")

if __name__ == "__main__":
    source = r"C:\Users\yrish\.gemini\antigravity\brain\7f8f3f0a-cb83-4984-a703-10ac0984b1f5\media__1782071992790.png"
    res_dir = r"C:\Users\yrish\.gemini\antigravity\scratch\baton\app\src\main\res"
    create_icons(source, res_dir)
