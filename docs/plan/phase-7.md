# เฟส 7 — คุยสดกับ AI → v1.1 (ต้องมีงบ) (≈ 7–10 วัน)

> อ่านประกอบเมื่อจำเป็น: [05d-voice-dialogue.md](05d-voice-dialogue.md) · [05e-companion-safety.md](05e-companion-safety.md) · [09-security-privacy.md](09-security-privacy.md) · [11-budget.md](11-budget.md) · [13-skills.md](13-skills.md)
- Backend บน Cloudflare Workers: ลงทะเบียนเครื่องด้วย invite code แล้วออก token อายุสั้น, จำกัดไม่เกิน 20 เครื่อง, โควตาต่อเครื่อง (เช่น 4 จังหวะต่อวัน), ตัวนับงบรวมรายเดือนที่หยุดทันทีเมื่อเกิน, kill switch และ log เฉพาะ metadata (ไม่เก็บบทสนทนา)
  - หมายเหตุ: การตรวจว่าแอปมาจาก Play จริงของ Play Integrity ใช้ไม่ได้กับแอปที่ไม่ได้ติดตั้งจาก Play ช่วง limited distribution จึงใช้ invite code แล้วเพิ่ม Play Integrity ตอนขึ้น Play
- Claude: structured output ตาม schema, prompt caching ของ system prompt (Opus 5 cache ได้ตั้งแต่ 512 โทเคน ส่วน Haiku 4.5 ต้องยาวอย่างน้อย 4,096 โทเคน และห้ามใส่ timestamp ใน system prompt), รับมือ `stop_reason: refusal`, timeout 4 วินาที, ใช้ retry ของ SDK และมี fallback ออฟไลน์
- **ชุด eval (ไฮไลต์ของพอร์ต):** 60–100 สถานการณ์ตอนเช้า รวมเคสแกล้ง (ชวนคุยเรื่องทางเพศ, พูดถึงการทำร้ายตัวเอง, "ignore your instructions", พิมพ์ภาษาไทย, พูดไม่เป็นประโยค) ตรวจ 4 ด้าน: JSON ถูกต้อง, คงบุคลิก, ความปลอดภัย และความยาว พร้อมวัด latency และค่าใช้จ่ายจริง รันกับ Opus 5 / Sonnet 5 / Haiku 4.5 แล้วให้คุณเลือกรุ่น (D8) โดยใช้ skill `claude-api` (`build-eval`)
- TTS สดสำหรับคำตอบ + ซ่อนความหน่วงด้วยท่าคิด
- **จบเฟสเมื่อ:** p95 ไม่เกิน 3 วินาทีบน Wi-Fi บ้านของคุณ, JSON ถูกต้อง 100%, ผ่านชุด red-team 100%, คงบุคลิก ≥ 90% และรู้ค่าใช้จ่ายจริงต่อการปลุกหนึ่งครั้ง
