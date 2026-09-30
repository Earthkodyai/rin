# เฟส 9 — จัดพอร์ต (ทำคู่ขนานตั้งแต่เฟส 1 และเก็บงานสุดท้าย ≈ 3–4 วัน)

> อ่านประกอบเมื่อจำเป็น: [01-decisions.md](01-decisions.md) · [02-market.md](02-market.md)
> **ตาม D25 (พรุ่งนี้):** 9.1 = README + ADR 0005 (D25) · 9.2 = วิดีโอเดโม 60–90 วินาที (Claude เขียน shot list คุณอัดจอ) + GIF ใน README · เรื่องเล่าสัมภาษณ์ทำหลังส่ง · README ต้องมีหัวข้อ "Known limits / not done" ที่ระบุงานที่เลื่อนตาม D25 ตรงๆ · README และท้ายวิดีโอต้องให้เครดิต "AvatarSample_O © pixiv VRoid Project" (meta ของโมเดลตั้ง `creditNotation: required`) และบอกว่าเป็นโมเดลชั่วคราว ห้ามแจกต่อ (`allowRedistribution: false`, `personalNonProfit`)

- README ภาษาอังกฤษ: ปัญหา → แนวทาง → GIF/วิดีโอเดโม → แผนภาพสถาปัตยกรรม → ความท้าทายเชิงวิศวกรรม (ความน่าเชื่อถือของการปลุกข้ามยี่ห้อ, offline-first, graceful degradation, emotion engine, ความเป็นส่วนตัวในเครื่อง, ~~AI ที่คุมงบ~~ ตัดตาม D21) → ตัวเลขที่วัดได้ → badge ของ test/CI → ลิงก์ ADR และ threat model → สิ่งที่จะทำต่อ
- ADR ใน `docs/adr/` สำหรับทุกการตัดสินใจใหญ่ (เริ่มจาก D1–D12 และทางเลือกที่ไม่เลือกใน [01-decisions.md](01-decisions.md))
- วิดีโอเดโมยาว 60–90 วินาที
- เตรียมเรื่องเล่าตอนสัมภาษณ์ 5 เรื่อง: WebView vs Unity, offline-first, การรับมือกับ OEM, ~~การทำ eval ของ LLM~~ (ตัดตาม D21: เล่าเรื่องการตัดฟีเจอร์ตามการทดสอบกับผู้ใช้แทน) และความเป็นส่วนตัว
