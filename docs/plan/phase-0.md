# เฟส 0 — เตรียมเครื่องมือ + ทดสอบความเป็นไปได้ (≈ 3–5 วัน)

> อ่านประกอบเมื่อจำเป็น: [06-tech-stack.md](06-tech-stack.md) · [12-token-workflow.md](12-token-workflow.md) · [05b-alarm-engine.md](05b-alarm-engine.md) (S1) · [05a-emotion-engine.md](05a-emotion-engine.md) (S2) · [05d-voice-dialogue.md](05d-voice-dialogue.md) (S3, S4) · [05c-missions-sensors.md](05c-missions-sensors.md) (S5)
- 0.1 ติดตั้งเครื่องมือตาม [06-tech-stack.md](06-tech-stack.md)
- 0.2 สร้าง repo ใน `D:\RinAlarm`: `git init`, `.gitignore` (Android/Node/secrets), gitleaks pre-commit, `LICENSE` (โค้ดใช้ MIT หรือ Apache-2.0 ส่วน asset ตัวละครและเสียงใช้ "All rights reserved"), ตั้ง Git identity (**ถามคุณก่อน**)
- 0.3 ตั้งค่า Claude Code: `CLAUDE.md` และ `docs/STATUS.md` (สร้างแล้ว), ติดตั้ง Android skills ของ Google, ปิด Unity plugin เฉพาะโปรเจกต์นี้ (ประหยัดราว 4–5k โทเคนต่อ session **ขออนุญาตก่อนแก้ settings**), ตั้ง allowlist ของคำสั่ง gradle/adb
- 0.4 บัญชีที่คุณต้องสมัครเอง (เพราะใช้ข้อมูลส่วนตัว): บัญชี limited distribution ที่ Android Developer Console และบัญชี GitHub ที่เปิด 2FA
- 0.5 **Spike** (ทำ prototype ที่ทิ้งได้ แล้วสรุปผล 1 หน้าลง `docs/spikes/`)

| Spike | ทดสอบอะไร | เกณฑ์ผ่าน |
|---|---|---|
| S1 ความน่าเชื่อถือของการปลุก | แอปเล็กที่ใช้ `setAlarmClock` + full-screen + `USAGE_ALARM` บนมือถือของคุณ ในกรณีจอดับ, Doze (`adb shell dumpsys deviceidle force-idle`), รีบูต, ปัดแอปทิ้ง, โหมดประหยัดแบต, DND และโหมดเงียบ อ่านยี่ห้อเครื่องด้วย `adb shell getprop ro.product.manufacturer` | ดังครบทุกกรณี คลาดไม่เกิน ±1 นาที |
| S2 ประสิทธิภาพของ WebView + three-vrm | โหลดโมเดลตัวอย่างจาก VRoid ใน WebView บนมือถือของคุณ วัด fps เวลาโหลด และหน่วยความจำ | fps เฉลี่ย ≥ 45 และโหลดไม่เกิน 2.5 วินาที → ใช้ Plan A ถ้าไม่ผ่าน → ประเมิน Plan B (Unity) |
| S3 STT ภาษาอังกฤษในเครื่อง | ใช้ได้หรือไม่ แม่นแค่ไหน และหน่วงเท่าไรเมื่อไม่มีเน็ต | จับเจตนาหลักได้ ≥ 90% |
| S4 คัดเสียงริน | สร้าง 10 ประโยคเดียวกันจาก Azure (2–3 เสียง/สไตล์) และ ElevenLabs Voice Design ให้คุณฟังแล้วเลือก พร้อมตรวจเงื่อนไขการใช้งานสำหรับแอปฟรีที่เผยแพร่สาธารณะ | คุณพอใจ และเงื่อนไขอนุญาต |
| S5 ML Kit ในบ้านของคุณ | สิ่งของที่ ML Kit จำได้แม่นในแสงตอนเช้า | มีอย่างน้อย 5 เป้าหมายที่ผ่าน ≥ 90% |

- **จบเฟสเมื่อ:** สรุป go/no-go ครบทั้ง 5 spike และอัปเดตตารางใน [01-decisions.md](01-decisions.md)
