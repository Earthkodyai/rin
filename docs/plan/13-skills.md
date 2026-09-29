# 13. Skill ที่ต้องใช้

**Claude Code skills ที่มีอยู่แล้ว**

| Skill | ใช้เมื่อ |
|---|---|
| ~~`claude-api`~~ (ตัด D21) | ~~เฟส 7: เชื่อมต่อ Claude, prompt caching, structured output และ `build-eval`~~ |
| `security-review`, `code-review` | ก่อนปล่อยทุกเวอร์ชัน |
| `simplify` | เก็บกวาดโค้ดหลังจบแต่ละเฟส |
| `run` | สั่ง build และรันแอปเพื่อตรวจผล |
| `skill-creator` | สร้าง skill ประจำโปรเจกต์ ([12-token-workflow.md](12-token-workflow.md) ข้อ 11) |
| `fewer-permission-prompts` | ลดการเด้งขอสิทธิ์ของคำสั่ง gradle/adb |
| `unity:*` | ใช้เฉพาะ Plan B |

**ที่ต้องเพิ่ม:** Android skills ทางการของ Google ผ่าน Android CLI (`android skills`) เช่น edge-to-edge, Navigation 3 และการวิเคราะห์ R8 ส่วน skill ของชุมชน (เช่น claude-android-ninja, android-skills) ใช้ได้ **หลังอ่านเนื้อหาแล้วเท่านั้น**

**ทักษะของคุณที่จะได้ (และเล่าตอนสัมภาษณ์ได้):** Kotlin/Compose, Android components (AlarmManager, receivers, services, permissions), การเขียนเทสต์, TypeScript + three.js, VRoid/VRM, พื้นฐานเสียง (ความดังและฟอร์แมต), การออกแบบ prompt และ eval, security/privacy engineering และ release engineering (signing, Play Console)
