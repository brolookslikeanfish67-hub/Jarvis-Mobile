# Jarvis-Mobile 

**Jarvis-Mobile** is a 100% offline, privacy-first voice/text assistant for Android, utilizing on-device MediaPipe GenAI and local Small Language Models (SLMs). It functions as a hands-free app launcher, smart assistant, and integrates web searches via DuckDuckGo. No tracking, zero API costs, and completely free to run.

---

##  Key Features

* ***On-Device DeepSeek Brain:** Runs local GGUF models (DeepSeek-R1-Distill-Llama-8B-abliterated) directly on your device.
* ***Hybrid Web Search:** Routes search commands directly to DuckDuckGo browser intents.
* ***Smart Intent Launcher:** Translates natural language into local Android intents to open apps.
* ***100% Private:** No conversation data ever leaves your device.
* ***Optimized Performance:** Uses Kotlin Coroutines for efficient on-device processing.

---

##  Prerequisites & Model Setup

1. **Download Model:** Download the `DeepSeek-R1-Distill-Llama-8B-abliterated.Q4_K_M.gguf` model from Hugging Face.
2. **Setup Assets:** Create `app/src/main/assets/` in your Android project.
3. **Move File:** Place the `.gguf` file in the assets folder.

>  **Note:** Requires a modern, mid-to-high-end Android device to run the 8B model effectively.

---

##  Local Compilation Instructions

1. **Clone:** `git clone https://github.com/brolookslikeanfish67-hub/Jarvis-Mobile`
2. **Open:** Open in Android Studio.
3. **Build:** Connect your device and run the app via USB debugging.

---

## 📜 License
This project is licensed under the **GNU General Public License v3 (GPLv3)**.
