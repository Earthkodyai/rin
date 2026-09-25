# 5.3 Alarm engine (รายละเอียดที่มักพลาด)
- ประกาศ `USE_EXACT_ALARM` (Android 13+) คู่กับ `SCHEDULE_EXACT_ALARM` ที่ใส่ `maxSdkVersion="32"` สำหรับเครื่องรุ่นเก่า
- ต้องได้สิทธิ์แจ้งเตือน `POST_NOTIFICATIONS` (Android 13+) มิฉะนั้นหน้าจอเต็มที่มาทาง notification จะไม่ขึ้น onboarding จึงต้องขอให้ได้ และ Diagnostics ต้องเตือนถ้าไม่มี
- Full-screen intent: ตรวจด้วย `canUseFullScreenIntent()` ถ้าไม่ได้สิทธิ์ให้พาผู้ใช้ไปหน้าตั้งค่า
- Foreground service ตอนดัง: ต้องประกาศ type ใน manifest (คาดว่าเป็น `systemExempted` สำหรับแอปปลุกที่มีสิทธิ์ exact alarm หรือ `mediaPlayback`) — **ตรวจกับเอกสาร Android และฟอร์มของ Play ฉบับล่าสุดใน Spike S1**
- Android 17 จำกัดการเล่นเสียงเบื้องหลัง แต่ยกเว้นแอปที่มีสิทธิ์ exact alarm และเล่นเสียงแบบ `USAGE_ALARM` เสียงรินช่วงปลุกจึงต้องใช้ `USAGE_ALARM`
- ตั้งปลุกใหม่เมื่อเกิด `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` (Direct Boot + device-protected storage), `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED` และเมื่อสถานะสิทธิ์ exact alarm เปลี่ยน
- หยุดดังเองหลัง 15 นาทีถ้าไม่มีใครตอบ (กันกรณีลืมมือถือไว้แล้วดังไม่หยุด) และบันทึกลง log
- เก็บ ring log (เวลาที่ตั้ง เทียบกับเวลาที่ดังจริง) เพื่อพิสูจน์ความน่าเชื่อถือ และใช้เป็นตัวเลขในพอร์ต
