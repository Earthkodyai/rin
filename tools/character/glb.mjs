// Minimal GLB reader/writer. It keeps the JSON as-is, so every index the VRM extensions hold (nodes, meshes,
// materials, textures, images) stays valid; only the bytes behind a bufferView can change.

const MAGIC = 0x46546c67; // "glTF"
const JSON_CHUNK = 0x4e4f534a;
const BIN_CHUNK = 0x004e4942;

/** @returns {{ json: any, views: Uint8Array[] }} every bufferView as its own byte array */
export function readGlb(bytes) {
  const b = Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (b.readUInt32LE(0) !== MAGIC) throw new Error('not a GLB file');
  if (b.readUInt32LE(4) !== 2) throw new Error(`GLB version ${b.readUInt32LE(4)}, expected 2`);
  const jsonLength = b.readUInt32LE(12);
  if (b.readUInt32LE(16) !== JSON_CHUNK) throw new Error('first chunk is not JSON');
  const json = JSON.parse(b.subarray(20, 20 + jsonLength).toString('utf8'));
  const binStart = 20 + jsonLength;
  let bin = new Uint8Array();
  if (binStart < b.length) {
    if (b.readUInt32LE(binStart + 4) !== BIN_CHUNK) throw new Error('second chunk is not BIN');
    bin = b.subarray(binStart + 8, binStart + 8 + b.readUInt32LE(binStart));
  }
  if ((json.buffers ?? []).length > 1 || json.buffers?.[0]?.uri) throw new Error('only one embedded buffer is supported');
  const views = (json.bufferViews ?? []).map((v) => bin.subarray(v.byteOffset ?? 0, (v.byteOffset ?? 0) + v.byteLength));
  return { json, views };
}

const pad4 = (n) => (n + 3) & ~3;

/** Packs the views back into one BIN chunk (4-byte aligned) and rewrites their offsets and lengths. */
export function writeGlb(json, views) {
  const out = structuredClone(json);
  let offset = 0;
  out.bufferViews = out.bufferViews.map((v, i) => {
    const view = { ...v, byteOffset: offset, byteLength: views[i].byteLength };
    offset = pad4(offset + views[i].byteLength);
    return view;
  });
  const bin = Buffer.alloc(offset);
  out.bufferViews.forEach((v, i) => bin.set(views[i], v.byteOffset));
  if (out.buffers?.length) out.buffers[0].byteLength = bin.length;

  const text = Buffer.from(JSON.stringify(out), 'utf8');
  const jsonChunk = Buffer.alloc(pad4(text.length), 0x20); // JSON pads with spaces
  text.copy(jsonChunk);
  const header = Buffer.alloc(12);
  header.writeUInt32LE(MAGIC, 0);
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(12 + 8 + jsonChunk.length + (bin.length ? 8 + bin.length : 0), 8);
  const chunk = (type, data) => {
    const h = Buffer.alloc(8);
    h.writeUInt32LE(data.length, 0);
    h.writeUInt32LE(type, 4);
    return [h, data];
  };
  return Buffer.concat([header, ...chunk(JSON_CHUNK, jsonChunk), ...(bin.length ? chunk(BIN_CHUNK, bin) : [])]);
}
