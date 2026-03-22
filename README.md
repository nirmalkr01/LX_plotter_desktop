# LX Plotter Desktop (Engineering Edition) 🌊📉

**LX Plotter Desktop** is a high-performance, open-source Kotlin application built with **Compose for Desktop**. It is specifically engineered to transform raw hydraulic survey data (CSV) into professional, industry-standard **river profile plots** (X-Sections and L-Sections) and **multi-page engineering reports**.

Whether you are a civil engineer, hydrologist, or software contributor, this tool provides a lightweight, highly interactive alternative to heavy CAD software for cross-section analysis.

---

## 📥 Download & Updates

The application features an integrated **Auto-Update System**. It checks for the latest version on every startup to ensure you always have the most stable UI and calculation engine.

- **Production Environment:** [lx-plotter-app.vercel.app](https://lx-plotter-app.vercel.app)
- **Version Metadata:** [version.json](https://lx-plotter-app.vercel.app/version.json)
- **Distribution:** Hosted via **Vercel** for high-availability updates.

*(Check the Releases tab on GitHub to download the latest Windows MSI installer).*

---

## 🚀 Key Features

- **Engineering-Grade Plotting:** Dynamic coordinate mapping with *thalweg (deepest point)* centering and manual zero-point overrides.
- **Interactive Designer:** Drag-and-drop support for "RIVER" bank labels, hydraulic blue lines, and custom text annotations.
- **Responsive Workspace:** Proportional UI design that scales automatically for laptops and high-resolution monitors.
- **Smart Partitioning:** Automated logic to split long L-Section river profiles into printable segments across multiple pages (A0–A4 support).
- **CAD-Style PDF Export:** Generate high-fidelity, submission-ready PDF reports with standard engineering title blocks.

---

## 📊 Sample Data for Testing

To help you get started immediately, sample data is provided in the repository:
`sample_data/sampled_kotawali.csv`

When loading this CSV into the application, map the columns as follows in the prompt:
- **Chainage:** `distance`
- **Distance/Offset:** `distance_2`
- **Pre-Monsoon Level:** `premonsoon`
- **Post-Monsoon Level:** `post_monsoon`

---

## 🏗️ System Architecture & File Structure

The application follows a **Modular Layered Architecture** to ensure data integrity and UI performance.

```plaintext
src/main/kotlin/
├── Core & Logic
│   ├── Main.kt                   # App entry point & window management
│   ├── Models.kt                 # Data models (RiverPoint, ReportPageItem)
│   ├── Logic.kt                  # Thalweg processing & coordinate mapping
│   ├── PartitionLogic.kt         # Grid partition & slot calculation for reports
│   ├── PageLayout.kt             # Engineering title blocks & border drawing
│   └── Animation.kt              # Alignment guides & snapping logic
│
├── UI & Rendering
│   ├── UpperPage.kt              # Startup/Home screen UI
│   ├── Graph.kt                  # Core Compose Canvas plotting engine
│   ├── ImagePanel.kt             # Interactive canvas overlay (drag & drop)
│   ├── SelectTool.kt             # Selection bounding box math
│   ├── ElementBox.kt             # Vector shape rendering (Shapes, Arrows)
│   └── Components.kt             # Shared UI components (Headers, Tables)
│
├── Workspace Panels
│   ├── FilePanel.kt              # Main layout orchestrator & state holder
│   ├── FilePanelUI.kt            # Ribbon and panel UI framework
│   ├── FilePanelComponents.kt    # Scrollable preview lists
│   ├── FilePanelTools.kt         # Action tools and modifiers
│   └── HelpIcon.kt               # User guide & documentation dialogs
│
└── Export Engine
    ├── ReportDownloadUI.kt       # Report configuration screen
    └── Download.kt               # Apache PDFBox generation & CSV export