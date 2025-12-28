# GeoPharFinder

> **Desktop application for locating nearby pharmacies using OpenStreetMap**

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://adoptium.net/)
[![JavaFX](https://img.shields.io/badge/JavaFX-21.0.2-blue.svg)](https://openjfx.io/)
[![Maven](https://img.shields.io/badge/Maven-3.9.6-red.svg)](https://maven.apache.org/)

---

## 📋 Prerequisites

Before running GeoPharFinder, ensure you have the following installed:

- **Java 17 or higher** (OpenJDK recommended)
- **Apache Maven 3.9.6+**
- **Internet connection** (for initial pharmacy data and map tiles)

---

## 🚀 Installation & Launch

### Option 1: Automated Setup (Recommended for Windows)

#### Step 1: Check Prerequisites
Run the prerequisite checker script to verify your system has all required software:

```bash
.\PRE_LAUNCH.bat
```

**What it does:**
- Checks for Java 17+
- Checks for Maven 3.9.6+
- Checks for JavaFX 21.0.2
- Downloads and installs missing components automatically
- Displays a summary of your system readiness

#### Step 2: Build the Application
Once prerequisites are met, build the project:

```bash
.\BUILD.bat
```

**What it does:**
- Runs `mvn clean package -DskipTests`
- Compiles all source code
- Creates the executable JAR file: `target/geopharfinder-1.0.0.jar`
- Shows build success/failure status

#### Step 3: Launch the Application
Start GeoPharFinder:

```bash
.\RUN.bat
```

**What it does:**
- Launches the application
- Automatically detects your location via IP
- Loads nearby pharmacies
- Opens the interactive map interface

---

### Option 2: Manual Setup

#### Step 1: Verify Java Installation
Open a terminal/command prompt and check your Java version:

```bash
java -version
```

You should see something like:
```
openjdk version "17.0.x" or higher
```

**If Java is not installed:**
- Download from [Adoptium](https://adoptium.net/) (Eclipse Temurin JDK 17)
- Install and add to PATH
- Verify installation with `java -version`

#### Step 2: Verify Maven Installation
Check Maven version:

```bash
mvn -version
```

You should see:
```
Apache Maven 3.9.6 or higher
```

**If Maven is not installed:**
- Download from [Apache Maven](https://maven.apache.org/download.cgi)
- Extract to a directory (e.g., `C:\Program Files\Apache\maven`)
- Add `bin` directory to PATH
- Verify installation with `mvn -version`

#### Step 3: Build the Project
Navigate to the project directory and run:

```bash
cd pharma
mvn clean package -DskipTests
```

**Build Process:**
- Downloads all dependencies (first time only)
- Compiles Java source files
- Packages resources (FXML, CSS, images)
- Creates shaded JAR with all dependencies
- Output: `target/geopharfinder-1.0.0.jar`

**Build time:** ~15-30 seconds (first build may take longer due to dependency downloads)

#### Step 4: Launch the Application
Run the generated JAR file:

```bash
java -jar target/geopharfinder-1.0.0.jar
```

**Alternative launch methods:**

**Using Maven:**
```bash
mvn javafx:run
```

**Direct Java execution:**
```bash
java -cp target/geopharfinder-1.0.0.jar com.pharmalocator.MainApp
```

---

## 🔄 Quick Build Script

For convenience, use the provided build script:

```bash
.\QUICK_BUILD.bat
```

**What it does:**
- Runs `mvn package -DskipTests` (skips cleaning for faster builds)
- Shows build progress
- Displays success/failure status

---

## 📂 Project Files

```
pharma/
├── PRE_LAUNCH.bat          # Prerequisites checker and installer
├── BUILD.bat               # Full build script (clean + package)
├── QUICK_BUILD.bat         # Fast build script (package only)
├── RUN.bat                 # Application launcher
├── CLEAN_FOR_SUBMISSION.bat # Clean cache and logs before submission
├── pom.xml                 # Maven configuration
├── src/                    # Source code
├── target/                 # Compiled output
│   └── geopharfinder-1.0.0.jar  # Executable JAR
├── cache/                  # Cached map tiles and data
├── logs/                   # Application logs
└── README.md               # This file
```

---

## 🎯 First Launch

When you start GeoPharFinder for the first time:

1. **Location Detection** — App automatically detects your location using your IP address
2. **Map Loading** — Interactive map centers on your location
3. **Pharmacy Search** — Nearby pharmacies are loaded and displayed on the map
4. **Sidebar** — List of pharmacies sorted by distance appears on the left

**First launch takes:** 5-10 seconds (downloads map tiles and pharmacy data)  
**Subsequent launches:** 1-2 seconds (uses cached data)

---

## 📖 Basic Usage

### Search for Pharmacies
- Type a city name or address in the search bar
- Press `Enter` or click the search icon (🔍)
- Map updates with pharmacies in that location

### Pick a Custom Location
- Click the `Pick Location` button (➕) in the header
- Click anywhere on the map
- Confirm the location in the popup dialog
- Pharmacies near that point will be displayed

### View Pharmacy Details
- Click any pharmacy marker on the map **OR**
- Click any pharmacy in the sidebar list
- Detail panel shows: name, address, phone, hours, distance

### Sort & Filter
- Use the **Sort by** dropdown: Distance or Name
- Use the **Show** dropdown: 10, 20, 50, or 100 results

### Toggle Theme
- Click the moon/sun button (🌙/☀️) in the header
- Switches between dark and light mode

---

## 🔧 Troubleshooting

### "Java not found" error
**Solution:** Install Java 17 or higher and add to PATH
```bash
# Check if Java is in PATH
java -version

# If not found, download from:
https://adoptium.net/
```

### "mvn not recognized" error
**Solution:** Install Maven and add to PATH
```bash
# Check if Maven is in PATH
mvn -version

# If not found, download from:
https://maven.apache.org/download.cgi
```

### Build fails with "JAVA_HOME not set"
**Solution:** Set JAVA_HOME environment variable
```bash
# Windows
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.x.x

# Linux/Mac
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
```

### Application won't start
1. Run `PRE_LAUNCH.bat` to verify prerequisites
2. Check `logs/geopharfinder.log` for error messages
3. Try rebuilding: `.\BUILD.bat`
4. Ensure no other instance is running

### No pharmacies found
- Check your internet connection
- Wait 5-10 seconds (API may be slow)
- Try a different location
- App will use cached data if available

---

## 📚 Additional Documentation

For comprehensive technical documentation, architecture details, and API references, see:

**[📄 Full Technical Report](README_FULL_TECHNICAL_REPORT.md)**

---

## 👥 Team

**Team Exodia**
- **Developer:** Mouad Moustafid
- **Supervisor:** Pr. Abdelkhalak Bahri
- **Institution:** ENSAH - École Nationale des Sciences Appliquées d'Al Hoceima
- **University:** Abdelmalek Essaâdi University
- **Academic Year:** 2024-2025

---

## 📄 License

This project uses open-source technologies and OpenStreetMap data.

**Attribution:**
```
© OpenStreetMap contributors
Map data: https://www.openstreetmap.org/copyright
```

---

<div align="center">

**GeoPharFinder** • *Find nearby pharmacies instantly* 💊

Made with ❤️ by **Team Exodia** | ENSAH 2024-2025

</div>

