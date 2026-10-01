# เฟส 8 — ขึ้น Google Play → v1.2 (งานจริง ≈ 3–5 วัน + รอทดสอบปิด 14 วัน)

> **บัญชี Play Console สร้างแล้ว 2026-10-01** (บัญชีส่วนตัว earth3joy@gmail.com, จ่าย $25 แล้ว, ประวัติ: ระบุว่าเป็นแอปแรก, เว็บไซต์ earthkodyai.github.io/rin) · **สร้างรายการแอปแล้ว 2026-10-01:** ชื่อ RinAlarm (เปลี่ยนได้, O4), package `io.github.earthkodyai.rinalarm` (จองแล้ว เปลี่ยนไม่ได้), ฟรี (เปลี่ยนเป็นเสียเงินไม่ได้), ยอมรับ Developer Program Policies + Play App Signing (key ใน D:\RinAlarm-keys จะเป็น upload key) + US export laws · ยังไม่ทำ checklist ตั้งค่าแอปและยังไม่อัปโหลด · ขั้นต่อไปของคุณ: ยืนยันตัวตน/อุปกรณ์ตามที่แดชบอร์ดขอ · **ห้ามอัป build ปัจจุบันขึ้น Play แม้แค่ internal test** เพราะมีโมเดลตัวอย่าง VRoid (ห้ามแจกต่อ) และ release ในเครื่องนี้ใส่โมเดลนี้ไว้ (`rin.devModelInRelease`) · สร้างรายการแอปและอัปโหลดหลังโมเดลรินของคุณมาแล้วเท่านั้น
> **เลื่อนทั้งเฟสเป็นงานหลังส่งตาม D25:** ทดสอบปิด 12 คน 14 วันทำไม่ได้ใน 2 วัน และต้องมีโมเดลรินของคุณก่อน (โมเดลตัวอย่างห้ามแจก)

> อ่านประกอบเมื่อจำเป็น: [10-policy-legal.md](10-policy-legal.md) · [09-security-privacy.md](09-security-privacy.md)
- สมัคร Play Console แบบบัญชีส่วนตัว ($25 ยืนยันด้วยบัตร ที่อยู่ และเบอร์โทร) ขั้นนี้คุณต้องทำเอง
- App content: privacy policy URL, Data safety, content rating (IARC), target audience 18+, declaration ของ `USE_EXACT_ALARM`, full-screen intent และ FGS types, ปุ่ม report เนื้อหา AI ในแอป (ตามนโยบาย AI-Generated Content) และคำอธิบายการใช้สิทธิ์ camera/mic/activity recognition
- ทดสอบปิดกับ 12 คนต่อเนื่อง 14 วัน (ย้ายผู้ทดสอบจาก limited distribution มาได้ และเริ่มหาคนตั้งแต่เฟส 6)
- ขอสิทธิ์ production → staged rollout 10% → 50% → 100% → ติดตาม Android vitals
- Store listing ห้ามสื่อว่ารินเป็นคนจริง และห้ามใช้เนื้อหาที่ติดลิขสิทธิ์
- **จบเฟสเมื่อ:** แอปอยู่บน Play Store ประเทศไทย และนำลิงก์ไปใส่พอร์ตได้
