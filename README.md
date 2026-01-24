# LX Plotter Desktop

LX Plotter Desktop is a Kotlin-based desktop application for plotting, visualizing, and managing graphical data with a modular UI architecture.

The project is built using **Kotlin + Gradle** and follows a structured separation of concerns between UI components, logic, and models.

---

## 📁 Project Structure

LX_plotter_desktop/
│
├── src/main/kotlin/
│ ├── Main.kt # Application entry point
│ ├── Logic.kt # Core business logic
│ ├── Models.kt # Data models
│ ├── Graph.kt # Graph plotting logic
│ ├── ImagePanel.kt # Image rendering panel
│ ├── FilePanel.kt # File explorer UI
│ ├── FilePanelUI.kt # File panel UI layout
│ ├── FilePanelTools.kt # File tools
│ ├── FilePanelComponents.kt # File UI components
│ ├── ElementBox.kt # UI element container
│ ├── Components.kt # Reusable UI components
│ ├── PageLayout.kt # Layout manager
│ ├── PartitionLogic.kt # Data partitioning logic
│ ├── SelectTool.kt # Selection tool
│ ├── Download.kt # Download functionality
│ └── ReportDownloadUI.kt # Report download UI
│
├── build/ # Gradle build output
├── .gradle/ # Gradle cache
└── README.md


---

## 🚀 Features

- Modular UI design
- Graph plotting and visualization
- File management panel
- Image rendering support
- Data partitioning logic
- Report export / download
- Clean separation of:
    - UI
    - Logic
    - Models

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **Build Tool:** Gradle
- **IDE:** IntelliJ IDEA (recommended)
- **Platform:** Desktop (JVM)

---

## ▶️ How to Run

### Using IntelliJ IDEA
1. Open the project in IntelliJ.
2. Let Gradle sync.
3. Run `Main.kt`.

### Using Command Line
```bash
gradlew run
(On Windows)

gradlew.bat run
📦 Build Project
gradlew build
The output will be in:

build/
🧹 .gitignore
This project ignores:

Gradle build files

IntelliJ configs

VS Code configs

OS junk files

So your repo stays clean and lightweight.

📌 Notes
All main source files are inside:

src/main/kotlin/
Entry point:

Main.kt
UI is component-driven and modular.

Logic is separated for easy maintenance and testing.

👨‍💻 Author
Nirmal Kumar
Computer Science Engineer
Kotlin | Desktop Apps | Systems & Tools

📄 License
This project is for educational and experimental use.
You may modify and extend it freely.


---

### Pro Tip (since you're a dev 😄)

You can auto-generate this file from CMD:

```bat
notepad README.md