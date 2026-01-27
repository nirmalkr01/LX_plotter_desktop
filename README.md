# LX Plotter Desktop (Engineering Edition)

**LX Plotter Desktop** is a high-performance Kotlin application built with **Compose for Desktop**. It is specifically engineered to transform raw hydraulic survey data (CSV) into professional, industry-standard **river profile plots** (X-Sections and L-Sections) and **multi-page engineering reports**.

---

## 📥 Download & Updates

The application features an integrated **Auto-Update System**. It checks for the latest version on every startup to ensure you always have the most stable UI and calculation engine.

**Current Release:** `Latest Stable`

- **Direct Download (Windows MSI):** [Click here to download latest MSI](https://lx-plotter-app-mxd1.vercel.app/LXPlotter-1.0.3.msi)  
  *(Note: The link above serves the current stable version v1.0.3)*

- **Version Metadata:** https://lx-plotter-app-mxd1.vercel.app/version.json

- **Release Channel:** Hosted via **Vercel** for high-availability distribution.

---

## 🚀 Key Features

- **Engineering-Grade Plotting** Dynamic coordinate mapping with *thalweg (deepest point)* centering and manual zero-point overrides.

- **Interactive Designer** Drag-and-drop support for:
  - "RIVER" bank labels
  - Hydraulic blue lines
  - Custom text annotations

- **Responsive Workspace** Proportional UI design that scales automatically for laptops and high-resolution monitors using `BoxWithConstraints`.

- **Smart Partitioning** Automated logic to split long L-Section river profiles into printable segments across multiple pages.

- **DevOps Integration** Fully automated build-to-deploy pipeline that syncs versioning between **Gradle** and the **Vercel distribution server**.

---

## 🏗️ System Architecture

The application follows a **Modular Layered Architecture** to ensure data integrity and UI performance.

### 1. Presentation Layer (UI)
- **Main Screen** Orchestrates the Ribbon controls, Sidebar history, and the dual-view Workspace.

- **Designer Screen** Specialized environment for multi-page PDF layout management.

- **Interactive Canvas** Custom drawing engine using the Compose Canvas API for high-fidelity vector rendering.

### 2. Logic Layer (Business Rules)
- **Coordinate Mapper** Translates survey measurements into pixel-perfect engineering scales  
  (e.g., `1:2000 H`, `1:100 V`).

- **Data Processor** Handles shifting of relative distances based on hydraulic thalweg or manual reference points.

- **Partition Engine** Calculates optimal "Slots" for graphs based on paper size (A0–A4) and margins.

### 3. Distribution Layer (DevOps)
- **Gradle Build System** Automates MSI packaging and digital asset preparation.

- **Vercel Pipeline** Acts as a global CDN for delivering `version.json` metadata and binary updates.

---

## 🛠️ Tech Stack

| Component        | Technology |
|------------------|-----------|
| Language         | Kotlin 2.1.0 |
| Framework        | Compose for Desktop |
| PDF Engine       | Apache PDFBox 2.0.30 |
| Distribution     | Windows MSI (JPackage) |
| Hosting          | Vercel |
| Data Parsing     | org.json + Custom CSV Parser |

---

## 📁 Project Structure & Module Map

```plaintext
LX_plotter_desktop/
├── build.gradle.kts              # Build automation, versioning, MSI packaging
├── src/main/kotlin/
│   ├── Main.kt                   # Application entry point & auto-update bootstrap
│   ├── Models.kt                 # Core data models (CSV rows, points, layouts)
│
│   ├── Graph.kt                  # Main plotting engine (Compose Canvas)
│   ├── Logic.kt                  # Thalweg processing & coordinate mapping
│   ├── PartitionLogic.kt         # Page partition & slot calculation logic
│   ├── PageLayout.kt             # Page sizing, margins, and layout rules
│
│   ├── Components.kt             # Shared reusable UI components
│   ├── ElementBox.kt             # Drag & drop elements (labels, text, markers)
│   ├── SelectTool.kt             # Selection, transform & editing tools
│
│   ├── FilePanel.kt              # File & page management controller
│   ├── FilePanelUI.kt            # File panel main UI
│   ├── FilePanelComponents.kt    # File panel widgets
│   ├── FilePanelTools.kt         # File panel action tools
│
│   ├── ImagePanel.kt             # Interactive canvas overlay system
│   ├── ReportDownloadUI.kt       # Report export & preview UI
│   ├── Download.kt               # PDF generation (Apache PDFBox + AWT)
│
└── src/main/resources/           # Icons, images, static assets
🔧 Developer Workflow (Build & Deploy)
To push a new version to all users:

1. Update Version Increment the version number inside build.gradle.kts:
version = "1.0.3" // Example
2. Package & Stage Run the custom Gradle task. This builds the MSI and automatically updates the version.json with the correct download link:
gradlew releaseToVercel

Deploy to Vercel Push the generated distribution files to the repository:
cd D:/lxplotter-dist
git add .
git commit -m "Release v1.0.3"
git push origin main

Users will receive the update notification automatically on their next launch.

👨‍💻 Author
Nirmal Kumar

📄 License
This project is maintained for:

Professional engineering use

Academic & educational research

Commercial redistribution requires explicit permission.