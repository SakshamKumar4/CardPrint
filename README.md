# CardPrint Pro

CardPrint Pro is a lightweight, fully offline Android application designed to simplify the process of printing identity cards such as Aadhaar, PAN, Driving License, Voter ID, Passport, and other document photos.

The app automatically helps users crop, adjust, enhance, and arrange ID card images on A4-sized sheets using multiple print layouts. It is built for cyber cafés, CSC centers, photocopy shops, KYC operators, banking correspondents, and anyone who frequently handles identity document printing.

## Features

* Completely Offline (No Internet Required)
* Automatic & Manual Card Cropping
* Perspective Correction & Image Enhancement
* Multiple Print Layouts

  * Single Card
  * Side-by-Side
  * Top & Bottom
  * 4-Up Layout
  * 6-Up Layout
  * Passport Style Copies
* Built-in Filters

  * Black & White
  * Scanner Mode
  * High Contrast
  * Sharpen
  * Grayscale
  * Vintage
* Brightness & Contrast Controls
* Export as PDF
* Export as High-Quality Image
* Direct Share & Print Support
* Optimized for Android 8.0+
* Low Memory & Small APK Size
* No Ads
* No Tracking
* Privacy First – All Processing Happens On Device

## Use Cases

* Aadhaar Card Printing
* PAN Card Printing
* KYC Document Processing
* CSC & Cyber Café Operations
* Photocopy & Print Shops
* Document Archiving

## Tech Stack

* Native Android (Java/Kotlin)
* OpenCV
* AndroidX
* Material Design 3
* PDFDocument API
* UCrop
* MVVM Architecture

## Privacy

CardPrint Pro processes all images locally on the device. No files are uploaded to any server, ensuring complete privacy and security of sensitive identity documents.

## Future Roadmap

* AI-based Auto Card Detection
* OCR Text Extraction
* Batch Processing
* Custom Layout Builder
* Smart Print Optimization
* Project Backup & Restore

Built to make ID card printing faster, easier, and more professional.

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/61824977-1564-43d2-b39e-5c35528f69ed

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
