// 아주 작은 .xlsx 읽기 도구 (외부 라이브러리 없이 첫 번째 시트만 읽는다).
// .xlsx는 zip 파일이고, 안에 xl/worksheets/sheet1.xml(셀)과 xl/sharedStrings.xml(글자)이 들어 있다.
// 정류소 위치정보 파일처럼 단순한 표 하나를 읽는 용도로만 쓴다.
const fs = require('fs');
const zlib = require('zlib');

// zip의 중앙 디렉터리를 읽어 { 파일이름: 압축 풀린 Buffer }를 돌려준다
function unzip(buf, wanted) {
  // 파일 끝쪽에서 End Of Central Directory(0x06054b50)를 찾는다
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('xlsx(zip) 형식이 아님');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const out = {};
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('zip 중앙 디렉터리 손상');
    const method = buf.readUInt16LE(p + 10);
    const compSize = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const localOff = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLen);
    if (wanted.includes(name)) {
      const lNameLen = buf.readUInt16LE(localOff + 26);
      const lExtraLen = buf.readUInt16LE(localOff + 28);
      const start = localOff + 30 + lNameLen + lExtraLen;
      const data = buf.subarray(start, start + compSize);
      out[name] = method === 0 ? data : zlib.inflateRawSync(data);
    }
    p += 46 + nameLen + extraLen + commentLen;
  }
  return out;
}

function decodeXml(s) {
  return s
    .replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/&#(\d+);/g, (_, d) => String.fromCodePoint(Number(d)))
    .replace(/&amp;/g, '&');
}

// "C12" → 열 번호 2 (A=0)
function colIndex(ref) {
  const letters = ref.match(/^[A-Z]+/)[0];
  let n = 0;
  for (const ch of letters) n = n * 26 + (ch.charCodeAt(0) - 64);
  return n - 1;
}

// 첫 번째 시트를 2차원 배열(행 × 열)로 읽는다. 숫자 셀은 Number, 글자 셀은 String.
function readFirstSheet(path) {
  const files = unzip(fs.readFileSync(path), ['xl/sharedStrings.xml', 'xl/worksheets/sheet1.xml']);
  const sheetXml = files['xl/worksheets/sheet1.xml'];
  if (!sheetXml) throw new Error('첫 번째 시트(sheet1.xml)를 찾지 못함: ' + path);

  const shared = [];
  if (files['xl/sharedStrings.xml']) {
    const ss = files['xl/sharedStrings.xml'].toString('utf8');
    for (const si of ss.matchAll(/<si>([\s\S]*?)<\/si>/g)) {
      // 서식이 섞인 글자(<r><t>..</t></r> 여러 개)도 이어 붙인다
      const parts = [...si[1].matchAll(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/g)].map((m) => m[1]);
      shared.push(decodeXml(parts.join('')));
    }
  }

  const rows = [];
  const xml = sheetXml.toString('utf8');
  for (const rowM of xml.matchAll(/<row\b[^>]*>([\s\S]*?)<\/row>/g)) {
    const row = [];
    for (const c of rowM[1].matchAll(/<c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/c>)/g)) {
      const attrs = c[1];
      const inner = c[2] || '';
      const ref = (attrs.match(/\br="([A-Z]+\d+)"/) || [])[1];
      const type = (attrs.match(/\bt="(\w+)"/) || [])[1];
      const v = (inner.match(/<v>([\s\S]*?)<\/v>/) || [])[1];
      let value = null;
      if (type === 's') value = shared[Number(v)];
      else if (type === 'inlineStr') value = decodeXml([...inner.matchAll(/<t(?:\s[^>]*)?>([\s\S]*?)<\/t>/g)].map((m) => m[1]).join(''));
      else if (type === 'str') value = v == null ? null : decodeXml(v);
      else if (v != null) value = Number(v);
      row[ref ? colIndex(ref) : row.length] = value;
    }
    rows.push(row);
  }
  return rows;
}

module.exports = { readFirstSheet };
