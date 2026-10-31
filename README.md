# 🛡️ SAFE MODE — Smart Anti-Theft & Intruder Alert App

**Safe Mode** ek intelligent mobile security aur anti-theft Android application hai jo phone ko bina permission uthane wale (intruders/chor) se protect karta hai.

---

## 🚀 Key Features Implemented (As per Blueprint)

1. **Idle State (Shant Avastha):**
   - Phone jab table, bed, ya kisi surface par rakha ho, app background me standby mode me battery-efficient rehta hai.
2. **Motion Trigger & Proximity Sensor:**
   - Jaise hi phone uthaya jaye ($\Delta A > 2.4\text{ m/s}^2$) aur phone pocket me na ho (Proximity Far), front camera automatically silent capture trigger karta hai.
3. **AI Face Recognition Engine (Google ML Kit):**
   - 3-Angles face registration (Straight, Left, Right).
   - Instant geometric facial landmark matching.
   - **Owner Matched:** Silent rehta hai aur phone normal unlock ho jata hai.
   - **Intruder / Face Mismatch:** Turant Intruder Protocol fire hota hai!
4. **Intruder Alert Protocol:**
   - **Full-Screen Red Strobe Flashing Overlay:** Screen par flashing warning aati hai:  
     `"YOU THE FUCKING HELL MAN GO TO HELL"` (ya aapka customized text).
   - **High-Decibel Dual-Tone Siren:** 1000Hz–2400Hz oscillating frequency hardware alarm play hota hai.
   - **100% Volume Lock Loop:** Har 120ms me alarm volume 100% force hoti hai aur volume keys block ho jati hain.
   - **Silent Photo Capture & Vault:** Chor ki photo battery percentage aur timestamp ke sath local vault me save ho jati hai.
   - **Optional Telegram Bot Sync:** Intruder ki photo automatically aapke Telegram par live bheji ja sakti hai.
5. **Emergency Fail-Safe:**
   - Secret 4-Digit PIN (Default: `1234`) daalkar ya camera ke samne owner face aane par alarm band ho jata hai.
6. **Low-Light Screen Flash Boost:**
   - Andhere me camera frame capture karne se pehle screen 300ms ke liye 100% white flash karegi taaki chehra clearly scan ho sake.
7. **Table Guard Mode (Stealth OLED Black Screen):**
   - Android 10+ background camera restrictions ko bypass karne ke liye stealth mode, jisme screen sleeping display ki tarah black rehti hai aur phone uthate hi bina delay ke instant face verification ho jati hai.

---

## 📦 GitHub se APK Kaise Banayein (GitHub Actions CI/CD)

Aapko apne computer par Android Studio ya heavy software install karne ki zaroorat nahi hai. Humne **GitHub Actions automated workflow** (`.github/workflows/build-apk.yml`) configure kar diya hai.

### Step 1: Is Folder ko GitHub Repository me Push karein

Apne terminal ya Git bash me ye commands run karein:

```bash
# 1. Git repository initialize karein (agar pehle se nahi hai)
git init

# 2. Main branch set karein
git branch -M main

# 3. Saari files add karein
git add .

# 4. Commit karein
git commit -m "Initial commit: Safe Mode Anti-Theft App"

# 5. Apna GitHub repo URL link karein (Apna username aur repo name daalein)
git remote add origin https://github.com/<YOUR-GITHUB-USERNAME>/<YOUR-REPO-NAME>.git

# 6. Push karein
git push -u origin main
```

---

### Step 2: GitHub Actions se APK Download karein

1. Apne GitHub repository page par jayein.
2. Top tabs me **"Actions"** tab par click karein.
3. Aapko **"Build Safe Mode APK"** workflow run hota dikhega (ya right side me **"Run workflow"** button par click karke manually start kar sakte hain).
4. Build complete hone ke baad (~2 minute me):
   - Us workflow run par click karein.
   - Page ke bottom me **"Artifacts"** section me **`SafeMode-AntiTheft-APK`** milega.
   - Click karke `.zip` file download karein jisme **`SafeMode-AntiTheft-v1.0-debug.apk`** hoga!
5. Agar workflow manually ya tag ke sath chalaenge, toh **Releases** section me directly downloadable APK file automatically upload ho jayegi!

---

### Step 3: Phone me Install aur Setup

1. APK ko apne Android phone me send karein aur install karein (*"Install from Unknown Sources"* allow karein).
2. App open karke **"Owner Face ID"** par click karein aur apne 3 angles (Center, Left, Right) register karein.
3. **Settings** me jakar apna Secret 4-digit PIN set karein (Default `1234`).
4. **"ACTIVATE SAFE MODE"** ya **"ENTER TABLE GUARD"** dabakar phone ko table par rakh dein!

Ab jaise hi koi phone ko uthayega, app turant face scan karke verify karega. Agar aap nahi hain, toh siren aur warning screen loop me bajegi!
