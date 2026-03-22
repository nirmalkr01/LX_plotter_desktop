# LX Plotter Desktop

A high-performance, open-source desktop application for transforming raw hydraulic survey data into professional river profile plots and multi-page engineering reports.

Built with Kotlin and Compose for Desktop, it serves as a lightweight alternative to heavy CAD software for cross-section and longitudinal-section analysis.

---

## Features

- **Engineering-grade plotting** — Dynamic coordinate mapping with thalweg (deepest point) centering and manual zero-point overrides.
- **Interactive designer** — Drag-and-drop support for river bank labels, hydraulic blue lines, and custom text annotations.
- **Smart partitioning** — Automated logic to split long L-Section profiles into printable segments across multiple pages (A0–A4).
- **CAD-style PDF export** — High-fidelity, submission-ready PDF reports with standard engineering title blocks.
- **Auto-update system** — Checks for the latest version on every startup via a hosted version manifest.
- **Responsive workspace** — Proportional UI that scales for laptops and high-resolution monitors.

---

## Download

Pre-built Windows MSI installers are available on the [Releases](../../releases) page.

The application auto-updates on startup using the version manifest hosted at:
- Production: https://lx-plotter-app.vercel.app
- Version manifest: https://lx-plotter-app.vercel.app/version.json

---

## Getting Started with Sample Data

Sample data is included in the repository at `sample_data/sampled_kotawali.csv`.

When loading this file, map the columns as follows:

| Field | CSV Column |
|---|---|
| Chainage | `distance` |
| Distance / Offset | `distance_2` |
| Pre-Monsoon Level | `premonsoon` |
| Post-Monsoon Level | `post_monsoon` |

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 2.1.0 |
| UI Framework | Jetpack Compose for Desktop 1.7.0 |
| PDF Engine | Apache PDFBox 2.0.30 |
| JSON Parsing | org.json 20231013 |
| Distribution / CDN | Vercel |

---

## Project Structure

```
src/main/kotlin/
├── Core & Logic
│   ├── Main.kt            # Application entry point and update check
│   ├── Models.kt          # Data models
│   ├── Logic.kt           # Core calculation engine
│   ├── PartitionLogic.kt  # L-Section page partitioning
│   ├── PageLayout.kt      # Page layout definitions
│   └── Animation.kt       # UI animation utilities
│
├── UI & Rendering
│   ├── UpperPage.kt       # Top-level page composable
│   ├── Graph.kt           # Plot rendering engine
│   ├── ImagePanel.kt      # Image/canvas panel
│   ├── SelectTool.kt      # Interactive selection tool
│   ├── ElementBox.kt      # Draggable annotation elements
│   └── Components.kt      # Shared UI components
│
├── Workspace Panels
│   ├── FilePanel.kt       # File management panel
│   ├── FilePanelUI.kt     # File panel layout
│   ├── FilePanelComponents.kt
│   ├── FilePanelTools.kt
│   └── HelpIcon.kt
│
└── Export Engine
    ├── ReportDownloadUI.kt # Export dialog UI
    └── Download.kt         # PDF generation and file I/O
```

---

## Building from Source

**Prerequisites:** JDK 17+, Gradle

```bash
# Clone the repository
git clone https://github.com/your-username/lx-plotter-desktop.git
cd lx-plotter-desktop

# Build and package as Windows MSI
./gradlew packageMsi
```

The output MSI will be at `build/compose/binaries/main/msi/`.

### Release Deployment

To prepare a release for Vercel distribution:

1. Increment `version` in `build.gradle.kts`.
2. Run the release task:
   ```bash
   ./gradlew releaseToVercel
   ```
3. Push the generated files in your Vercel distribution repository.

---

## Contributing

Contributions are welcome. To get started:

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "Add your feature"`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request.

Please ensure your code follows the existing modular structure and that any new logic is placed in the appropriate layer.

---

## Support

LX Plotter is free and open-source. If it has saved you time on engineering projects, consider supporting development:

**UPI (India):** `singhnirmalkr5@ibl`
*(Compatible with GPay, PhonePe, Paytm, and any UPI app)*

---

## Author

**Nirmal Kumar**

---

## License

Licensed under the [MIT License](LICENSE). Free for professional, academic, and commercial use.
