# เฟส 2 — ร่างกายของริน (character layer) (≈ 8–10 วัน)

> อ่านประกอบเมื่อจำเป็น: [05-architecture.md](05-architecture.md) · [05a-emotion-engine.md](05a-emotion-engine.md) · [09-security-privacy.md](09-security-privacy.md)
- คุณออกแบบรินใน VRoid: เป็นตัวละคร original ที่ดูเป็นผู้ใหญ่ชัดเจน ใส่ชุดลำลองหรือชุดนอนที่สุภาพ export เป็น VRM 1.0 โดยลดขนาด texture และตั้ง license ใน VRM meta เป็น "ห้ามแจกจ่ายต่อ"
- Character sheet: บุคลิก, สไตล์การพูดภาษาอังกฤษ, คำติดปาก, สิ่งที่รินทำและไม่ทำ (Claude ร่าง คุณแก้)
- `web/character/`: Vite + TypeScript + three-vrm แล้ว bundle ลง assets ของแอป Kotlin กับ JavaScript สื่อสารกันผ่าน `WebViewCompat.addWebMessageListener` ที่จำกัด origin
- พฤติกรรม: หายใจ, กะพริบตา, มองตาม, blend อารมณ์, ท่าทาง VRMA, lip sync และแตะหัวแล้วดีใจ
- จัดการ `onRenderProcessGone` (ถ้า WebView ล้ม แอปต้องไม่ล้มตาม) + ภาพนิ่งสำรอง
- ⚠️ **ไลเซนส์แอนิเมชัน:** ห้ามใส่ไฟล์แอนิเมชันของบุคคลที่สามลง repo สาธารณะจนกว่าจะตรวจเงื่อนไขแล้ว (เช่น Mixamo ไม่อนุญาตให้แจกไฟล์แอนิเมชันเป็นไฟล์เดี่ยว) ทางที่ปลอดภัยคือทำเองหรือใช้แพ็กที่อนุญาตชัดเจน
- **จบเฟสเมื่อ (ปรับ 2026-09-28 ตามที่คุณเลือก):** บนมือถือของคุณ โหลดไม่เกิน 2.5 วิ · fps สองเกณฑ์: ที่เพดาน 30 fps เฉลี่ย ≥ 29 และไม่มีเฟรมเกิน 50 ms ขณะทำท่า+พูด และเมื่อยกเพดานเป็น 120 เฉลี่ย ≥ 45 (เกณฑ์ S2 = เผื่อเครื่องที่ช้ากว่า) · เปลี่ยนอารมณ์ภายใน 300 ms · WebView ล้มแล้วสลับเป็นภาพนิ่งได้ · แขนไม่จมเข้าลำตัวเกิน 10 mm ทุกท่า (`./gradlew checkGestures`) · วัดทั้งชุดด้วย `tools/character/phase-exit.sh`
- **ผล (2.5, โมเดลตัวอย่าง VRoid):** ผ่านทุกข้อ ✅ รายละเอียดใน 05a · เมื่อโมเดลรินมา: `./gradlew checkGestures -PwriteBody` → build → `./gradlew checkGestures` → `phase-exit.sh` อีกรอบ
