#  Jarvis-Mobile

**Jarvis-Mobile** is a lightweight, fully open-source, and 100% offline voice and text assistant engineered specifically for Android devices. By utilizing local Small Language Models (SLMs) and on-device processing via a distilled Chinese tech breakout architecture, this application acts as a hands-free app launcher and creative messaging assistant without ever speaking to a cloud server. 

No tracking, no subscriptions, and **zero API costs**—making it entirely free to run forever.

---

###  Key Features

*   ** On-Device DeepSeek Brain:** Runs local GGUF models powered by the DeepSeek-R1 reasoning pipeline locally via Google's MediaPipe GenAI SDK.
*   ** Smart Intent Launcher:** Translates natural language instructions ("Hey Jarvis, open YouTube") into background Android intents to instantly switch apps.
*   ** 100% Offline & Private:** Works flawlessly on airplanes, while camping, or with mobile data disabled. No conversation data ever leaves the device.
*   ** Custom Text Engine:** Features a dedicated input panel backed by an optimized background prompt pipeline to generate highly creative, smooth text responses—automatically copied to the phone's clipboard for instant pasting into communication apps like Tinder.
*   ** Zero-Footprint Security:** Contains zero hardcoded API keys or web hooks, making the code entirely secure to host publicly without any credit card exposure.

---

###  Prerequisites & Model Setup

Because this app functions entirely locally, you must download the underlying AI weights manually and include them in your local compilation bundle.

1. Navigate to Hugging Face and download the following model file:
    **`huihui-ai/DeepSeek-R1-Distill-Llama-8B-abliterated`**
2. Locate the file ending precisely in **`.Q4_K_M.gguf`** (approximately 4.7 GB).
3. Create an `assets` folder inside your Android project directory (`app/src/main/assets/`).
4. Paste the downloaded `.gguf` file inside this folder and ensure it is named exactly `DeepSeek-R1-Distill-Llama-8B-abliterated.Q4_K_M.gguf`.

>  **Performance Optimization Note:** Running an 8-billion parameter model locally on mobile hardware requires a healthy amount of system memory. For the smoothest experience, use a mid-to-high-end Android phone and ensure the initialization code forces execution via the device **GPU** rather than the CPU to prevent overheating and battery drain.

---

###  Local Compilation Instructions

To build and compile the application manually using Android Studio:

1. Clone this repository to your local computer:
   ```bash
   git clone https://github.com/brolookslikeanfish67-hub/Jarvis-Mobile
   ```
2. Open Android Studio and choose **Open an Existing Project**, selecting the cloned folder.
3. Drop the downloaded DeepSeek `.gguf` model file into your project's assets directory as outlined in the setup step above.
4. Connect your Android device via USB debugging or spin up an Android Virtual Device (AVD) emulator.
5. Click **Run 'app'** (`Shift + F10`) to compile the APK and deploy it straight to your device.

---

### 📜 License

This project is licensed under the strict copyleft **GNU General Public License v3 (GPLv3)**. Any modifications, extensions, or redistribution of this software codebase must remain entirely open-source and free to the public domain under the exact same legal terms. 
