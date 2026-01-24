# LX Plotter Desktop

**LX Plotter Desktop** is a Kotlin-based engineering application designed for plotting, visualizing, and managing hydraulic river data. It bridges the gap between raw CSV survey data and professional engineering reports with a modular UI architecture.

The project is built using **Compose for Desktop (Kotlin + Gradle)** and follows a structured separation of concerns between UI components, logic, and models.

> ⚠️ **Development Status**: The PDF Report Download feature is currently in **Beta**. You may encounter minor rendering issues or incomplete exports as this module is under active development.

---

## 🚀 Features

* **Engineering-Grade Plotting**: Dynamic coordinate mapping for River X-Sections and L-Sections.
* **Modular UI Design**: Clean separation of File Explorer, Image Panel, and Graphing Canvas.
* **Interactive Editing**: Drag-and-drop support for text labels ("RIVER"), hydraulic lines, and annotations.
* **Data Partitioning**: Logic to automatically split long profiles into printable segments.
* **Report Designer**: Grid-based layout system for creating multi-page engineering reports.
* **File Management**: Integrated file explorer for quick access to survey data.

---

## 🛠️ Tech Stack

* **Language**: Kotlin
* **UI Framework**: Compose for Desktop (Jetpack Compose)
* **Build Tool**: Gradle 8.x
* **PDF Engine**: Apache PDFBox
* **IDE**: IntelliJ IDEA (Recommended)
* **Platform**: Windows / macOS / Linux (JVM)

---

## 🏗️ Installation & Setup

### 1. Prerequisites
Before running or building the project, ensure you have:
* **JDK 17** or higher installed.
* **IntelliJ IDEA** (Community or Ultimate) is recommended for development.

### 2. Run in Development Mode
To run the application directly from the source code:

**Using IntelliJ IDEA:**
1.  Open the project folder in IntelliJ.
2.  Wait for Gradle to sync dependencies.
3.  Navigate to `src/main/kotlin/Main.kt` and click the **Run** (▶) button.

**Using Command Line:**
```bash
# Windows
gradlew.bat run

# Mac/Linux
./gradlew run
3. Create a Standalone Installer (Windows MSI)
To package the application into an installable file (.msi) for distribution:

Open your terminal in the project root directory.

Run the packaging task:

Bash
gradlew packageMsi
(Note: This process may take a few minutes as it downloads necessary binaries).

Locate the Installer: Once the build is successful, navigate to: LX_plotter_desktop/build/compose/binaries/main/msi/

Install: Double-click the LXPlotter-1.0.0.msi file to install the app on your machine. It will appear in your Start Menu and Desktop shortcuts.

📁 Project Structure
Plaintext
LX_plotter_desktop/
│
├── src/main/kotlin/
│   ├── Main.kt                 # Application entry point
│   ├── Logic.kt                # Core business logic & Data Processing
│   ├── Models.kt               # Data classes (RiverPoint, etc.)
│   ├── Graph.kt                # Core engineering graph plotting logic
│   ├── ImagePanel.kt           # Interactive rendering & editing panel
│   ├── FilePanel.kt            # File explorer & Page management UI
│   ├── FilePanelUI.kt          # Ribbon & Toolbar UI layouts
│   ├── FilePanelTools.kt       # Helper tools for the file panel
│   ├── FilePanelComponents.kt  # UI components for file operations
│   ├── ElementBox.kt           # Draggable UI element container
│   ├── Components.kt           # Reusable shared UI widgets
│   ├── PageLayout.kt           # Standard Engineering Footer/Header layout
│   ├── PartitionLogic.kt       # Algorithms for splitting graphs across pages
│   ├── SelectTool.kt           # Logic for selection & group operations
│   ├── Download.kt             # PDF Generation logic (PDFBox)
│   └── ReportDownloadUI.kt     # Main Report Designer Screen
│
├── src/main/resources/         # Assets (Icons, Images)
├── build/                      # Gradle build output (Installers live here)
├── .gradle/                    # Gradle cache
└── README.md                   # Project Documentation
🧹 .gitignore Rules
To keep the repository clean and lightweight, the following files are ignored:

Gradle build artifacts (build/, .gradle/)

IntelliJ/IDE configuration files (.idea/, *.iml)

OS-specific system files (.DS_Store)

👨‍💻 Author
Nirmal Kumar  

📄 License
This project is for educational and experimental use. You may modify and extend it freely.