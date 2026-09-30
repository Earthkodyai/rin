# เฟส 6 — Hardening + แจกแบบ limited distribution → v1.0 (≈ 4–6 วัน)

> อ่านประกอบเมื่อจำเป็น: [09-security-privacy.md](09-security-privacy.md) · [10-policy-legal.md](10-policy-legal.md)
> **ย่อตาม D25:** 6.1 = `/security-review` + `/code-review` แก้เฉพาะที่ร้ายแรง · 6.2 = R8 + signing key นอก repo + ข้อความ 18+/AI/ความเป็นส่วนตัวในแอป (privacy notice เป็นไฟล์ใน repo แทน GitHub Pages) ติดตั้งบนเครื่องคุณเท่านั้น · crash reporting: ไม่มี (O9) · **ไม่แจก APK** (โมเดลตัวอย่าง VRoid ห้ามแจกต่อ) · limited distribution + feedback 7 วัน เลื่อนเป็นงานหลังส่ง

- รัน `/security-review` และ `/code-review`, เปิด R8/minify, ตั้งค่า signing (เก็บ key ไว้นอก repo), ทำ privacy notice ทั้งในแอปและบนเว็บ (GitHub Pages), หน้ายืนยันอายุ 18+, การแจ้งว่าเป็น AI, ~~crisis resources~~ (ตัด 2026-10-01) และทดสอบการกินแบต
- ตัดสินใจเรื่อง crash reporting (เช่น Firebase Crashlytics ซึ่งฟรี ถ้าใช้ต้องระบุในนโยบายความเป็นส่วนตัว)
- ลงทะเบียน package name และ signing certificate ในบัญชี limited distribution → build APK แบบ signed → แจกให้ผู้ทดสอบไม่เกิน 20 เครื่องพร้อมคู่มือผู้ทดสอบ
- **จบเฟสเมื่อ:** v1.0 อยู่บนเครื่องของคุณและเพื่อน และได้ feedback ครบ 7 วัน
