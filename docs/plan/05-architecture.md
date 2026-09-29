# 5. สถาปัตยกรรม

```mermaid
flowchart LR
  subgraph App["Android app · Kotlin + Compose"]
    AE["AlarmEngine<br/>setAlarmClock · receivers"]
    RS["RingService<br/>FGS + full-screen intent"]
    AU["AudioEngine<br/>Media3 · USAGE_ALARM"]
    ME["MissionEngine<br/>CameraX + ML Kit · steps"]
    BD["BedReturnDetector<br/>sensor heuristics"]
    CB["CompanionBrain<br/>mood state machine (one morning, D22)<br/>line selector (no intent matcher, D21)"]
    VI["VoiceIO<br/>on-device STT · clip player"]
    CV["CharacterView<br/>WebView: three.js + three-vrm"]
    DB[("Room + DataStore<br/>device-protected storage")]
  end
  subgraph Later["v1.1 · เปิดเมื่อมีงบ"]
    BE["Backend proxy<br/>Cloudflare Workers · TypeScript"]
    LLM["Claude API"]
    TTS["TTS API"]
  end
  AE --> RS --> AU
  RS --> CV
  CB --> CV
  CB --> VI
  ME --> CB
  BD --> CB
  CB -. feature flag .-> BE --> LLM
  BE --> TTS
```

## 5.1 หลักการที่ห้ามละเมิด
1. **เส้นทางการปลุกเป็น native ล้วน**: `setAlarmClock` → foreground service → เสียงแบบ `USAGE_ALARM` ต้องไม่พึ่งเน็ต WebView หรือ AI เลย ส่วนอื่นเป็นของเสริมที่พังได้โดยไม่กระทบการปลุก
2. **Offline-first**: ทุกฟีเจอร์ AI ต้องมีทางสำรองแบบออฟไลน์ที่ผู้ใช้แทบไม่รู้สึกว่ามีการสลับ
3. **AI เลือก ไม่ได้สร้าง**: LLM ตอบกลับเป็น JSON ว่าจะพูดอะไร ใช้อารมณ์ไหน และท่าทางไหน จากรายการที่กำหนดไว้ แอปเป็นคนเล่นแอนิเมชันเอง
4. **ข้อมูลอ่อนไหวอยู่ในเครื่อง**: รูปภาพและเสียงไม่ออกจากเครื่อง (v1.1 ส่งออกเฉพาะข้อความ)
