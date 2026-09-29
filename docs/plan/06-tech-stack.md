# 6. Tech stack และเครื่องมือที่ต้องติดตั้ง

| ส่วน | เลือกใช้ | เหตุผล | ค่าใช้จ่าย/สัญญาอนุญาต |
|---|---|---|---|
| ภาษา/UI | Kotlin + Jetpack Compose | มาตรฐานงาน Android ปัจจุบัน | ฟรี |
| สถาปัตยกรรม | Single-activity, MVVM/UDF, Hilt, Coroutines/Flow, Room, DataStore | เทสต์ง่าย และเป็นที่นิยมในอุตสาหกรรม | ฟรี |
| เสียง | Media3 ExoPlayer | จัดการ audio focus และ audio stream ได้ครบ | Apache-2.0 |
| ตัวละคร | three.js + `@pixiv/three-vrm` + `@pixiv/three-vrm-animation`, Vite + TypeScript, โหลดผ่าน `WebViewAssetLoader` | เบา และปลอดภัยกว่าการเปิด `file://` | MIT |
| โมเดล | VRoid Studio 2.3.0 → VRM 1.0 | คุณเป็นเจ้าของโมเดลเอง | ฟรี (ตรวจเงื่อนไขของ VRoid อีกครั้งก่อนเผยแพร่) |
| กล้อง/วิชัน | CameraX + ML Kit Barcode Scanning (ภารกิจ QR) + MediaPipe Image Embedder (ภารกิจรูปของที่สอนเอง, ~15 MB arm64 → ใช้ feature module) | ฟรี ออฟไลน์ และเป็นส่วนตัว (ผล S5) | ฟรี |
| แปลงเสียงเป็นข้อความ | Android `SpeechRecognizer` (ประมวลผลในเครื่องถ้าเครื่องรองรับ) | ฟรี และไม่ส่งเสียงออก | ฟรี |
| สังเคราะห์เสียง (ตอน build) | Azure AI Speech หรือ ElevenLabs (คุณเลือกจากการฟังใน S4) | Azure มี viseme ในตัว | Azure มี free tier · ElevenLabs ต้องตรวจเงื่อนไขการใช้งาน |
| เซนเซอร์ | accelerometer + gyroscope (`AccelStepDetector`, วัดใน 3.1) | เดิมใช้ตรวจการลุกและการกลับไปนอน ตัดตาม D18 จึงไม่มีงานในแอปแล้ว | ฟรี |
| Backend (v1.1) | Cloudflare Workers + TypeScript + Anthropic TypeScript SDK | free tier 100k requests/วัน และไม่ต้องผูกบัตร | ฟรีในระดับทดสอบ |
| CI | GitHub Actions: lint (ktlint/detekt), unit test, build, gitleaks, CodeQL, dependency review | ฟรีสำหรับ repo สาธารณะ | ฟรี |

**เครื่องมือที่ต้องติดตั้ง (ตรวจเมื่อ 25 ก.ย. 2026)**

| เครื่องมือ | สถานะ | การดำเนินการ |
|---|---|---|
| VRoid Studio 2.3.0 | ✅ ยังติดตั้งอยู่ (winget id `pixivInc.VRoidStudio`) | ถ้าหายไปภายหลัง ติดตั้งใหม่ด้วย `winget install --id pixivInc.VRoidStudio` (ติดตั้งระดับผู้ใช้ ไม่ต้องใช้สิทธิ์แอดมิน) |
| Unity 6.3 LTS + Unity Hub + Unity CLI | ✅ ยังติดตั้งอยู่ (แต่โฟลเดอร์ `D:\UnityProjects` ไม่มีแล้ว) | ใช้เฉพาะ Plan B ถ้าลบไปแล้ว ให้ติดตั้งใหม่ก็ต่อเมื่อ Spike S2 ไม่ผ่าน |
| Node.js, Git | ✅ มีแล้ว | — |
| Android Studio (stable ล่าสุด) + SDK 36 + platform-tools (adb) + JDK ที่มากับ Studio | ❌ ยังไม่มี | ติดตั้งในเฟส 0 (ต้องใช้สิทธิ์แอดมิน Claude จะแจ้งก่อนรันทุกครั้ง เพราะเครื่องนี้ไม่เด้ง UAC เตือน) |
| Android CLI + Android skills ของ Google | ❌ | ติดตั้งจาก d.android.com/tools/agents |
| GitHub CLI (`gh`) | ตรวจในเฟส 0 | — |
| Blender (ไม่บังคับ) | — | ใช้แก้แอนิเมชันถ้าจำเป็น |
| Wrangler (Cloudflare CLI) | — | ใช้ในเฟส 7 |
