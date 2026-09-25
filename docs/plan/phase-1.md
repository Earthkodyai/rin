# เฟส 1 — แกนการปลุก (native ยังไม่มีตัวละคร) (≈ 6–8 วัน)

> อ่านประกอบเมื่อจำเป็น: [05-architecture.md](05-architecture.md) · [05b-alarm-engine.md](05b-alarm-engine.md) · [08-error-handling.md](08-error-handling.md)
- หน้าตั้งปลุก (Compose): เวลา, วันซ้ำ, ชื่อ, เสียงค่อยๆ ดัง, สั่น และกติกา snooze
- ระบบตั้งเวลา + receivers + foreground service ตอนดัง + activity เต็มจอ (`showWhenLocked`, `turnScreenOn`)
- ตั้งปลุกใหม่หลังรีบูต เปลี่ยนเวลา หรืออัปเดตแอป (รองรับ Direct Boot)
- Onboarding สิทธิ์ + หน้า Diagnostics (exact alarm, notifications, full-screen, สถานะการประหยัดแบต, คู่มือ autostart ตามยี่ห้อ, ระดับเสียง) + ปุ่มทดสอบปลุกใน 1 นาที
- Ring log
- เทสต์: unit test การคำนวณเวลาปลุกครั้งถัดไป (วันซ้ำ, timezone, ข้ามวัน) + instrumented test ของ receiver
- ตั้ง GitHub Actions CI
- **จบเฟสเมื่อ:** ring log ยืนยันว่าปลุกตรงเวลาติดกัน 14 คืนบนมือถือของคุณ (ระหว่างรอทำเฟส 2 คู่ขนานได้) และ CI เขียว
