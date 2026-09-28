// Reads a voice clip as mono float PCM: WAV (16-bit PCM or 32-bit float) directly, MP3 through mpg123-decoder
// (MIT wrapper; mpg123 itself is LGPL-2.1, fine for a build tool that never ships in the app).
import { readFileSync } from 'node:fs';

/** @returns {{ samples: Float32Array, rate: number }} */
export function readWav(bytes) {
  const b = Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (b.toString('ascii', 0, 4) !== 'RIFF' || b.toString('ascii', 8, 12) !== 'WAVE') throw new Error('not a WAV file');
  let offset = 12;
  let fmt = null;
  while (offset + 8 <= b.length) {
    const id = b.toString('ascii', offset, offset + 4);
    const size = b.readUInt32LE(offset + 4);
    const body = offset + 8;
    if (id === 'fmt ') {
      fmt = { format: b.readUInt16LE(body), channels: b.readUInt16LE(body + 2), rate: b.readUInt32LE(body + 4), bits: b.readUInt16LE(body + 14) };
    } else if (id === 'data') {
      if (!fmt) throw new Error('WAV data before fmt');
      const { format, channels, rate, bits } = fmt;
      const width = bits / 8;
      const count = Math.floor(size / width / channels);
      const samples = new Float32Array(count);
      for (let i = 0; i < count; i++) {
        let sum = 0;
        for (let c = 0; c < channels; c++) {
          const at = body + (i * channels + c) * width;
          if (format === 1 && bits === 16) sum += b.readInt16LE(at) / 32768;
          else if (format === 3 && bits === 32) sum += b.readFloatLE(at);
          else throw new Error(`unsupported WAV: format ${format}, ${bits} bits`);
        }
        samples[i] = sum / channels;
      }
      return { samples, rate };
    }
    offset = body + size + (size % 2);
  }
  throw new Error('WAV without data');
}

export async function readMp3(bytes) {
  const { MPEGDecoder } = await import('mpg123-decoder');
  const decoder = new MPEGDecoder();
  await decoder.ready;
  const { channelData, sampleRate } = decoder.decode(new Uint8Array(bytes));
  decoder.free();
  const samples = new Float32Array(channelData[0].length);
  for (const channel of channelData) for (let i = 0; i < samples.length; i++) samples[i] += channel[i] / channelData.length;
  return { samples, rate: sampleRate };
}

export async function readAudio(path) {
  const bytes = readFileSync(path);
  if (/\.wav$/i.test(path)) return readWav(bytes);
  if (/\.mp3$/i.test(path)) return readMp3(bytes);
  throw new Error(`unsupported audio file: ${path}`);
}
