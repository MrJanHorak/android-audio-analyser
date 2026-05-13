Branding assets and placement
================================

Drop the provided images into the project at the following locations (replace existing placeholders):

- Launcher adaptive icon foreground (recommended high-res PNG, 512x512):
  `app/src/main/res/mipmap-anydpi-v26/ic_launcher_foreground.png`
  (optional density folders: `mipmap-mdpi/`, `mipmap-hdpi/`, `mipmap-xhdpi/`, `mipmap-xxhdpi/`, `mipmap-xxxhdpi/`)

- Launcher adaptive icon background (solid or gradient PNG):
  `app/src/main/res/mipmap-anydpi-v26/ic_launcher_background.png`

- Header banner (used in the top bar):
  `app/src/main/res/drawable/header_banner.png` (use the attachment 2 image)

- Splash image (use the tall vertical attachment):
  `app/src/main/res/drawable-nodpi/splash_image.png` (use the attachment 3 image)

Notes and tips
--------------
- For best results provide a high-resolution square image (512x512 or larger) for the launcher foreground.
- Keep launcher foreground transparent where appropriate so the adaptive icon mask looks correct.
- Banner: aim for ~1200×200 px (landscape) or similar aspect ratio; Compose `Image` uses `ContentScale.Fit` so it will scale.
- Splash: using `drawable-nodpi` prevents automatic density scaling; supply the tall image at device screen resolution or larger.

Replace steps (quick commands for PowerShell)
-------------------------------------------
Copy the three files into the repo (example paths):
```powershell
# from your downloads folder, change filenames accordingly
Copy-Item C:\Users\You\Downloads\attachment1.png -Destination app\src\main\res\mipmap-anydpi-v26\ic_launcher_foreground.png
Copy-Item C:\Users\You\Downloads\attachment2.png -Destination app\src\main\res\drawable\header_banner.png
Copy-Item C:\Users\You\Downloads\attachment3.png -Destination app\src\main\res\drawable-nodpi\splash_image.png
```

Then recompile:
```powershell
./gradlew.bat :app:assembleDebug
```

If you want, I can also:
- Generate all mipmap density variants (need high-res source),
- Replace `drawable/header_banner.xml` placeholder with the PNG (I can remove the XML once you confirm),
- Commit & push the assets to git for you.
