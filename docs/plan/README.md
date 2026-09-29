# แผนงาน RinAlarm — สารบัญ

> เวอร์ชัน 1.0 · 26 ก.ย. 2026 · สถานะ: **รอคุณอ่านและยืนยัน**
> แยกจาก `docs/PLAN.md` ไฟล์เดียวเมื่อ 26 ก.ย. 2026 เนื้อหาเหมือนเดิม เพิ่มแค่ลิงก์ข้ามไฟล์
> Claude อ่าน `docs/STATUS.md` แล้วเปิดเฉพาะ `phase-N.md` ของเฟสที่กำลังทำ ไม่ต้องอ่านไฟล์นี้ทุกครั้ง

## ทำเฟสไหน อ่านไฟล์ไหน
| เฟส | ไฟล์หลัก | อ่านประกอบเมื่อจำเป็น |
|---|---|---|
| 0 เตรียมเครื่องมือ + spike | [phase-0.md](phase-0.md) | 06, 12 และไฟล์ 05x ของ spike ที่ทำอยู่ |
| 1 แกนการปลุก | [phase-1.md](phase-1.md) | 05, 05b, 08 |
| 2 ตัวละคร | [phase-2.md](phase-2.md) | 05, 05a, 09 |
| 3 ภารกิจ + เซนเซอร์ | [phase-3.md](phase-3.md) | 05c, 08 |
| 4 เสียง + บทสนทนาออฟไลน์ | [phase-4.md](phase-4.md) | 05d |
| 5 อารมณ์ + Bond | [phase-5.md](phase-5.md) | 05a, 05e |
| 6 Hardening + v1.0 | [phase-6.md](phase-6.md) | 09, 10 |
| 7 คุยสดกับ AI (v1.1) | [phase-7.md](phase-7.md) | 05d, 05e, 09, 11, 13 |
| 8 Google Play (v1.2) | [phase-8.md](phase-8.md) | 10, 09 |
| 9 จัดพอร์ต | [phase-9.md](phase-9.md) | 01, 02 |

## ไฟล์ทั้งหมด
| ไฟล์ | เนื้อหา |
|---|---|
| [01-decisions.md](01-decisions.md) | การตัดสินใจ D1–D12 และทางเลือกที่ไม่เลือก |
| [02-market.md](02-market.md) | คู่แข่งและสิ่งที่เรียนรู้ |
| [03-mvp.md](03-mvp.md) | ขอบเขต v1.0 / v1.1 / v1.2 |
| [04-morning-flow.md](04-morning-flow.md) | ลำดับเหตุการณ์หนึ่งเช้า |
| [05-architecture.md](05-architecture.md) | แผนภาพสถาปัตยกรรม + หลักการที่ห้ามละเมิด |
| [05a-emotion-engine.md](05a-emotion-engine.md) | ระบบอารมณ์ แอนิเมชัน lip sync และงบประสิทธิภาพ |
| [05b-alarm-engine.md](05b-alarm-engine.md) | รายละเอียดการปลุกที่มักพลาด |
| [05c-missions-sensors.md](05c-missions-sensors.md) | ภารกิจ/เกม (การตรวจว่ากลับไปนอนตัดตาม D18) |
| [05d-voice-dialogue.md](05d-voice-dialogue.md) | คลังเสียง บทสนทนาออฟไลน์ และการคุยสด |
| [05e-companion-safety.md](05e-companion-safety.md) | ความปลอดภัยของตัวละคร + จริยธรรมของ Bond |
| [06-tech-stack.md](06-tech-stack.md) | stack + เครื่องมือที่ต้องติดตั้ง |
| [07-phases.md](07-phases.md) | วิธีอ่านเวลาประมาณการ + เวลารวม |
| [08-error-handling.md](08-error-handling.md) | ตารางรับมือข้อผิดพลาด |
| [09-security-privacy.md](09-security-privacy.md) | threat model + เอกสารที่ต้องมีก่อนเผยแพร่ |
| [10-policy-legal.md](10-policy-legal.md) | checklist นโยบาย Play และกฎหมาย |
| [11-budget.md](11-budget.md) | งบประมาณ + ประมาณการค่า LLM |
| [12-token-workflow.md](12-token-workflow.md) | วิธีทำงานกับ Claude Code แบบประหยัดโทเคน |
| [13-skills.md](13-skills.md) | skill ที่ใช้ |
| [14-risks.md](14-risks.md) | ความเสี่ยงหลัก |
| [15-open-decisions.md](15-open-decisions.md) | เรื่องที่ยังต้องตัดสินใจ |
| [16-sources.md](16-sources.md) | แหล่งอ้างอิง |
