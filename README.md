# LX Plotter Desktop (Engineering Edition) 🌊📉

**LX Plotter Desktop** is a high-performance, open-source Kotlin application built with **Compose for Desktop**. It is specifically engineered to transform raw hydraulic survey data (CSV) into professional, industry-standard **river profile plots** (X-Sections and L-Sections) and **multi-page engineering reports**.

Whether you are a civil engineer, hydrologist, or software contributor, this tool provides a lightweight, highly interactive alternative to heavy CAD software for cross-section analysis.

---

## ☕ Support the Development

LX Plotter is built with passion and is completely free and open-source. If this tool has saved you hours of tedious AutoCAD plotting or helped you in your engineering projects, consider supporting my work! Your support helps keep the project maintained and updated.

**Support via UPI (India):** singhnirmalkr5@ibl  
*(You can enter this ID in GPay, PhonePe, Paytm, or any UPI app)*

---

## 📥 Download & Updates

The application features an integrated **Auto-Update System**. It checks for the latest version on every startup to ensure you always have the most stable UI and calculation engine.

- **Production Environment:** https://lx-plotter-app.vercel.app
- **Version Metadata:** https://lx-plotter-app.vercel.app/version.json
- **Distribution:** Hosted via **Vercel** for high-availability updates.

*(Check the Releases tab on GitHub to download the latest Windows MSI installer).*

---

## 🚀 Key Features

- **Engineering-Grade Plotting:** Dynamic coordinate mapping with thalweg (deepest point) centering and manual zero-point overrides.
- **Interactive Designer:** Drag-and-drop support for "RIVER" bank labels, hydraulic blue lines, and custom text annotations.
- **Responsive Workspace:** Proportional UI design that scales automatically for laptops and high-resolution monitors.
- **Smart Partitioning:** Automated logic to split long L-Section river profiles into printable segments across multiple pages (A0–A4 support).
- **CAD-Style PDF Export:** Generate high-fidelity, submission-ready PDF reports with standard engineering title blocks.

---

## 📊 Sample Data for Testing

To help you get started immediately, sample data is provided in the repository:
`sample_data/sampled_kotawali.csv`

When loading this CSV into the application, map the columns as follows in the prompt:
- **Chainage:** distance
- **Distance/Offset:** distance_2
- **Pre-Monsoon Level:** premonsoon
- **Post-Monsoon Level:** post_monsoon

---

## 🏗️ System Architecture & File Structure

The application follows a **Modular Layered Architecture** to ensure data integrity and UI performance.

```plaintext
src/main/kotlin/
├── Core & Logic
│   ├── Main.kt
│   ├── Models.kt
│   ├── Logic.kt
│   ├── PartitionLogic.kt
│   ├── PageLayout.kt
│   └── Animation.kt
│
├── UI & Rendering
│   ├── UpperPage.kt
│   ├── Graph.kt
│   ├── ImagePanel.kt
│   ├── SelectTool.kt
│   ├── ElementBox.kt
│   └── Components.kt
│
├── Workspace Panels
│   ├── FilePanel.kt
│   ├── FilePanelUI.kt
│   ├── FilePanelComponents.kt
│   ├── FilePanelTools.kt
│   └── HelpIcon.kt
│
└── Export Engine
    ├── ReportDownloadUI.kt
    └── Download.kt
🛠️ Tech Stack
Language: Kotlin 2.1.0

Framework: Jetpack Compose for Desktop

PDF Engine: Apache PDFBox 2.0.30

Data Parsing: Custom CSV Parser

Hosting/CDN: Vercel

🔧 Developer Workflow (Build & Deploy)
Update Version: Increment the version number inside build.gradle.kts.

Package:

Bash
./gradlew packageMsi
Deploy to Vercel: Push the generated distribution files to your connected Vercel repository.

🤝 Contributing (Open Source)
This project is fully open-source, and contributions from the community are highly encouraged.

Fork the Project

Create your Feature Branch (git checkout -b feature/AmazingFeature)

Commit your Changes (git commit -m 'Add some AmazingFeature')

Push to the Branch (git push origin feature/AmazingFeature)

Open a Pull Request

👨‍💻 Author
Nirmal Kumar

📄 License
This project is licensed under the MIT License. Free for professional engineering use, academic research, and commercial redistribution.

EOF


**Step 4: Add, Commit, and Push!**
```bash
git add README.md
git commit -m "Update README with open source details and UPI"
git push origin main