# Inter Font Bundle Instructions

`BatonTypography.kt` references the following font resource IDs.
Place the corresponding `.ttf` files in `src/main/res/font/` before building:

| Resource ID         | Font file            | Weight       |
|---------------------|----------------------|--------------|
| `inter_thin`        | inter_thin.ttf       | 100 – Thin   |
| `inter_light`       | inter_light.ttf      | 300 – Light  |
| `inter_regular`     | inter_regular.ttf    | 400 – Normal |
| `inter_medium`      | inter_medium.ttf     | 500 – Medium |
| `inter_semibold`    | inter_semibold.ttf   | 600 – SemiBold |
| `inter_bold`        | inter_bold.ttf       | 700 – Bold   |
| `inter_extrabold`   | inter_extrabold.ttf  | 800 – ExtraBold |

## Download

https://github.com/rsms/inter/releases — extract `extras/ttf/`, rename files to match the IDs above.

## Alternative: Google Fonts downloadable fonts API

Replace `InterFontFamily` in `BatonTypography.kt` with the downloadable fonts API
and add `implementation("androidx.compose.ui:ui-text-google-fonts")` to `core/ui/build.gradle.kts`.
