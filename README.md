# LX Plotter Desktop (Engineering Edition)

**LX Plotter Desktop** is a high-performance Kotlin application built with **Compose for Desktop**. It is specifically engineered to transform raw hydraulic survey data (CSV) into professional, industry-standard **river profile plots** (X-Sections and L-Sections) and **multi-page engineering reports**.

---

## 📥 Download & Updates

The application features an integrated **Auto-Update System**. It checks for the latest version on every startup to ensure you always have the most stable UI and calculation engine.

**Current Stable Version:** `1.0.1`

- **Direct Download (Windows MSI):**  
  https://lx-plotter-app.vercel.app/LXPlotter-1.0.1.msi

- **Version Metadata:**  
  https://lx-plotter-app.vercel.app/version.json

- **Release Channel:** Hosted via **Vercel** for high-availability distribution.

---

## 🚀 Key Features

- **Engineering-Grade Plotting**  
  Dynamic coordinate mapping with *thalweg (deepest point)* centering and manual zero-point overrides.

- **Interactive Designer**  
  Drag-and-drop support for:
    - "RIVER" bank labels
    - Hydraulic blue lines
    - Custom text annotations

- **Responsive Workspace**  
  Proportional UI design that scales automatically for laptops and high-resolution monitors using `BoxWithConstraints`.

- **Smart Partitioning**  
  Automated logic to split long L-Section river profiles into printable segments across multiple pages.

- **DevOps Integration**  
  Fully automated build-to-deploy pipeline that syncs versioning between **Gradle** and the **Vercel distribution server**.

---

## 🏗️ System Architecture

The application follows a **Modular Layered Architecture** to ensure data integrity and UI performance.

### 1. Presentation Layer (UI)
- **Main Screen**  
  Orchestrates the Ribbon controls, Sidebar history, and the dual-view Workspace.

- **Designer Screen**  
  Specialized environment for multi-page PDF layout management.

- **Interactive Canvas**  
  Custom drawing engine using the Compose Canvas API for high-fidelity vector rendering.

### 2. Logic Layer (Business Rules)
- **Coordinate Mapper**  
  Translates survey measurements into pixel-perfect engineering scales  
  (e.g., `1:2000 H`, `1:100 V`).

- **Data Processor**  
  Handles shifting of relative distances based on hydraulic thalweg or manual reference points.

- **Partition Engine**  
  Calculates optimal "Slots" for graphs based on paper size (A0–A4) and margins.

### 3. Distribution Layer (DevOps)
- **Gradle Build System**  
  Automates MSI packaging and digital asset preparation.

- **Vercel Pipeline**  
  Acts as a global CDN for delivering `version.json` metadata and binary updates.

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

1. Update Version
Increment version in:

build.gradle.kts
Example:

1.0.1 → 1.0.2
2. Package & Stage
Run the custom Gradle task:

gradlew releaseToVercel
This will:

Build MSI

Update version.json

Prepare distribution assets

3. Deploy to Vercel
cd D:/lxplotter-dist
git add .
git commit -m "Release v1.0.2: UI responsive fixes"
git push origin main
Users receive the update automatically on next launch.

👨‍💻 Author
Nirmal Kumar

📄 License
This project is maintained for:

Professional engineering use

Academic & educational research

Commercial redistribution requires explicit permission.


This README is already **production-grade** for:
- GitHub
- Vercel
- Engineering portfolio
- SaaS-style documentation