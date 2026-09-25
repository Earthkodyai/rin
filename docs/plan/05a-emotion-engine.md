# 5.2 Emotion engine (ระบบอารมณ์)
- **สถานะอารมณ์ (mood):** `sleepy`, `cheerful`, `proud`, `worried`, `pouty`, `sulky`, `relieved`
- **อินพุต (event):** `alarm_fired`, `user_spoke(intent)`, `snooze`, `mission_started/completed/failed`, `back_to_bed_suspected/confirmed`, `emergency_dismiss` รวมถึง streak, bond และเวลาที่ใช้ตื่น
- **เอาต์พุตต่อหนึ่งจังหวะ:** `{ emotion, intensity 0–1, gesture, lineId | text }`
- **การแมปไปยัง VRM:** ใช้ preset `happy/angry/sad/relaxed/surprised` + `blink` + `lookAt` + รูปปาก `aa/ih/ou/ee/oh` ท่า "งอน" คือ `angry` ระดับเบาผสมท่ากอดอก (ตรวจ blendshape เพิ่มเติมที่ VRoid ให้มาในเฟส 2)
- **ความลื่น:** blend สีหน้าแบบ lerp 150–300 ms, crossfade แอนิเมชัน 200–400 ms, มีการหายใจ กะพริบตา และขยับตาเล็กๆ แบบสุ่มตลอดเวลา
- **แอนิเมชัน (VRMA):** idle หายใจ, หาว, บิดขี้เกียจ, โบกมือ, ปรบมือ, พยักหน้า, ส่ายหน้า, กอดอกงอน, ดีใจ
- **Lip sync:** คลิปในคลังมี timeline รูปปาก (viseme) ที่คำนวณไว้ตอน build ปากจึงตรงเสียงแม้ออฟไลน์ ถ้าไม่มี timeline ให้ใช้ความดังของเสียง (RMS) ขับรูปปาก `aa` แทน
- **งบประสิทธิภาพ:** เป้า 60 fps บนมือถือระดับกลาง ต่ำสุด 30 fps · โมเดล ≤ 10–15 MB (texture 1024–2048) · ใช้ `VRMUtils` รวม morph · ไม่มีเงาหรือ post-processing · จำกัด pixel ratio · หยุด render เมื่อไม่ได้แสดงผล
- **ภาพสำรอง:** เรนเดอร์ PNG 6–8 สีหน้าจากโมเดลเก็บไว้ ใช้เมื่อ WebView ล้มหรือโหลดช้า
