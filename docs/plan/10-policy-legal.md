# 10. นโยบาย Play Store และกฎหมาย (checklist)

- [ ] Target API 36 (บังคับตั้งแต่ 31 ส.ค. 2026)
- [ ] `USE_EXACT_ALARM` เป็นสิทธิ์ที่ Play ตรวจ (แอปปลุกผ่านเกณฑ์) + declaration
- [ ] `USE_FULL_SCREEN_INTENT` declaration (หน้าที่หลักของแอปคือการปลุก)
- [ ] ประกาศ FGS types ทั้งใน manifest และใน Play Console
- [ ] Android 17 background audio: ใช้ `USAGE_ALARM` ร่วมกับสิทธิ์ exact alarm
- [ ] นโยบาย AI-Generated Content: ป้องกันเนื้อหาต้องห้าม และมีปุ่ม report ในแอป
- [ ] นโยบาย User Data ครอบคลุมถึง AI ของบุคคลที่สามด้วย (ประกาศเมื่อ ก.ค. 2026)
- [ ] Content rating (IARC) — Play ไม่อนุญาตแอปที่ไม่มี rating
- [ ] Target audience 18+
- [ ] Developer verification: ไทยเริ่มบังคับ 30 ก.ย. 2026 → ใช้บัญชี limited distribution ตอนนี้ แล้วยืนยันตัวตนกับ Play ในเฟส 8
- [ ] ทดสอบปิด 12 คน × 14 วัน (บัญชีส่วนตัวใหม่)
- [ ] Privacy policy URL และ Data safety ต้องตรงกัน
- [ ] ขอสิทธิ์ตามบริบทพร้อมคำอธิบาย (camera, mic, activity recognition, notifications)
- [ ] กฎหมาย: ปฏิบัติตาม PDPA ของไทยตั้งแต่วันแรก ถ้าขยายตลาดต้องดู California SB 243 (แจ้งว่าเป็น AI, crisis protocol, ข้อกำหนดเกี่ยวกับผู้เยาว์), กฎหมาย AI companion ของนิวยอร์ก และ EU AI Act (ต้องแจ้งว่าเป็น AI) แกนหลักของกฎหมายเหล่านี้ (แจ้งว่าเป็น AI + crisis protocol) เราทำตั้งแต่ v1.0 เพราะต้นทุนต่ำ
