/** Read bounded UTF-8 JSON. Neither client nor provider error bodies are ever logged. */
export async function readJson(response: Request | Response, maxBytes: number): Promise<unknown> {
  const size = response.headers.get('content-length');
  if (size && Number(size) > maxBytes) throw new Error('BODY_TOO_LARGE');
  if (!response.body) throw new Error('EMPTY_BODY');
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxBytes) throw new Error('BODY_TOO_LARGE');
      chunks.push(value);
    }
  } finally {
    await reader.cancel();
    reader.releaseLock();
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return JSON.parse(new TextDecoder('utf-8', { fatal: true, ignoreBOM: false }).decode(bytes));
}
