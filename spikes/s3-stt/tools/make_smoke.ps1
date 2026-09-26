# Pipeline smoke test only: Windows SAPI voices read fixed phrases, saved as 16 kHz mono 16-bit WAV
# plus manifest.jsonl in the same format the app records. Not a substitute for the tester's voice.
param([Parameter(Mandatory)][string]$OutDir)
Add-Type -AssemblyName System.Speech
$phrases = [ordered]@{
  AFFIRM  = @("Yeah, I'm awake.", "Okay, let's go.")
  DENY    = @("No, not really.", "Nope.")
  SNOOZE  = @("Five more minutes, please.", "Let me sleep a bit longer.")
  TIRED   = @("I'm so tired today.", "I didn't sleep well.")
  SICK    = @("I feel sick.", "I have a headache.")
  GREET   = @("Good morning, Rin.", "Hey there.")
  THANKS  = @("Thank you so much.", "Thanks, Rin.")
  UNKNOWN = @("What's the weather like?", "Where did I put my phone?")
}
$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$lines = @()
foreach ($voice in @("Microsoft David Desktop", "Microsoft Zira Desktop")) {
  $tag = ($voice -split ' ')[1].ToLower()
  foreach ($intent in $phrases.Keys) {
    $i = 0
    foreach ($p in $phrases[$intent]) {
      $i++
      $id = "$($intent.ToLower())-$tag-$i"
      $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
      $s.SelectVoice($voice)
      $s.SetOutputToWaveFile((Join-Path $OutDir "$id.wav"), $fmt)
      $s.Speak($p); $s.Dispose()
      $lines += (@{ id = $id; intent = $intent; cond = "tts-$tag"; question = ""; file = "$id.wav"; ref = $p } | ConvertTo-Json -Compress)
    }
  }
}
[System.IO.File]::WriteAllLines((Join-Path $OutDir "manifest.jsonl"), $lines)
Write-Output "made $($lines.Count) clips"
