/**
 * Self-contained, zero-dependency QR Code generator and terminal renderer.
 * Produces crisp, high-contrast Unicode half-block QR codes that render in
 * any terminal (PowerShell, cmd.exe, macOS Terminal, Linux).
 */

// Reed-Solomon Galois Field math
const GF256_EXP = new Uint8Array(512);
const GF256_LOG = new Uint8Array(256);

(function initGaloisField() {
  let val = 1;
  for (let i = 0; i < 255; i++) {
    GF256_EXP[i] = val;
    GF256_EXP[i + 255] = val;
    GF256_LOG[val] = i;
    val = (val << 1) ^ (val >= 128 ? 0x11d : 0);
  }
})();

function gfMultiply(x, y) {
  if (x === 0 || y === 0) return 0;
  return GF256_EXP[GF256_LOG[x] + GF256_LOG[y]];
}

function rsComputePoly(ecCount) {
  let poly = [1];
  for (let i = 0; i < ecCount; i++) {
    const factor = [1, GF256_EXP[i]];
    const newPoly = new Array(poly.length + 1).fill(0);
    for (let j = 0; j < poly.length; j++) {
      newPoly[j] ^= gfMultiply(poly[j], factor[0]);
      newPoly[j + 1] ^= gfMultiply(poly[j], factor[1]);
    }
    poly = newPoly;
  }
  return poly;
}

function rsCalculateRemainder(data, ecCount) {
  const genPoly = rsComputePoly(ecCount);
  const result = new Array(ecCount).fill(0);

  for (const byte of data) {
    const factor = byte ^ result.shift();
    result.push(0);
    if (factor !== 0) {
      for (let i = 0; i < ecCount; i++) {
        result[i] ^= gfMultiply(genPoly[i + 1], factor);
      }
    }
  }
  return result;
}

// Minimal standard QR versions table (byte mode, low/medium error correction)
// Version 1-10 capacities
const QR_VERSIONS = [
  null,
  { version: 1, size: 21, dataBytes: 19, ecBytes: 7 },
  { version: 2, size: 25, dataBytes: 34, ecBytes: 10 },
  { version: 3, size: 29, dataBytes: 55, ecBytes: 15 },
  { version: 4, size: 33, dataBytes: 80, ecBytes: 20 },
  { version: 5, size: 37, dataBytes: 108, ecBytes: 26 },
  { version: 6, size: 41, dataBytes: 136, ecBytes: 36 },
  { version: 7, size: 45, dataBytes: 156, ecBytes: 40 },
  { version: 8, size: 49, dataBytes: 194, ecBytes: 48 },
  { version: 9, size: 53, dataBytes: 232, ecBytes: 60 },
  { version: 10, size: 57, dataBytes: 274, ecBytes: 72 }
];

class SimpleQrEncoder {
  constructor(text) {
    this.text = text;
    this.rawBytes = Buffer.from(text, 'utf-8');
    this.versionInfo = this.selectVersion(this.rawBytes.length);
    this.size = this.versionInfo.size;
    this.modules = Array.from({ length: this.size }, () => Array(this.size).fill(null));
    this.isFunction = Array.from({ length: this.size }, () => Array(this.size).fill(false));
  }

  selectVersion(byteLength) {
    // Need 4 bits mode + 8/16 bits count + data
    const needed = byteLength + 2;
    for (let v = 1; v < QR_VERSIONS.length; v++) {
      if (QR_VERSIONS[v].dataBytes >= needed) {
        return QR_VERSIONS[v];
      }
    }
    // If larger than v10, pick v10 or fallback
    return QR_VERSIONS[10];
  }

  setModule(r, c, val, isFunc = true) {
    if (r >= 0 && r < this.size && c >= 0 && c < this.size) {
      this.modules[r][c] = val;
      if (isFunc) this.isFunction[r][c] = true;
    }
  }

  drawFinderPattern(startR, startC) {
    for (let r = -1; r <= 7; r++) {
      for (let c = -1; c <= 7; c++) {
        const nr = startR + r;
        const nc = startC + c;
        if (nr < 0 || nr >= this.size || nc < 0 || nc >= this.size) continue;
        const inOuter = r >= 0 && r <= 6 && c >= 0 && c <= 6;
        const inInner = r >= 2 && r <= 4 && c >= 2 && c <= 4;
        const inBorder = (r === 0 || r === 6 || c === 0 || c === 6);
        this.setModule(nr, nc, inInner || (inOuter && inBorder));
      }
    }
  }

  drawTimingPatterns() {
    for (let i = 0; i < this.size; i++) {
      const val = i % 2 === 0;
      if (!this.isFunction[6][i]) this.setModule(6, i, val);
      if (!this.isFunction[i][6]) this.setModule(i, 6, val);
    }
  }

  drawAlignmentPattern(centerR, centerC) {
    for (let r = -2; r <= 2; r++) {
      for (let c = -2; c <= 2; c++) {
        const isCenter = r === 0 && c === 0;
        const isBorder = Math.abs(r) === 2 || Math.abs(c) === 2;
        this.setModule(centerR + r, centerC + c, isCenter || isBorder);
      }
    }
  }

  addAlignmentPatterns() {
    if (this.versionInfo.version < 2) return;
    const positions = {
      2: [6, 18],
      3: [6, 22],
      4: [6, 26],
      5: [6, 30],
      6: [6, 34],
      7: [6, 22, 38],
      8: [6, 24, 42],
      9: [6, 26, 46],
      10: [6, 28, 50]
    }[this.versionInfo.version] || [6, this.size - 7];

    for (const r of positions) {
      for (const c of positions) {
        const isFinder = (r === 6 && c === 6) ||
                         (r === 6 && c === positions[positions.length - 1]) ||
                         (r === positions[positions.length - 1] && c === 6);
        if (!isFinder && !this.isFunction[r][c]) {
          this.drawAlignmentPattern(r, c);
        }
      }
    }
  }

  encodeData() {
    const bits = [];
    // Byte mode indicator: 0100
    bits.push(0, 1, 0, 0);

    // Character count indicator (8 bits for v1-9)
    const countBits = this.versionInfo.version <= 9 ? 8 : 16;
    for (let i = countBits - 1; i >= 0; i--) {
      bits.push((this.rawBytes.length >> i) & 1);
    }

    // Data bits
    for (const byte of this.rawBytes) {
      for (let i = 7; i >= 0; i--) {
        bits.push((byte >> i) & 1);
      }
    }

    // Terminator (up to 4 zeroes)
    const maxDataBits = this.versionInfo.dataBytes * 8;
    for (let i = 0; i < 4 && bits.length < maxDataBits; i++) {
      bits.push(0);
    }

    // Pad to byte boundary
    while (bits.length % 8 !== 0) {
      bits.push(0);
    }

    // Pad bytes
    const padBytes = [0xec, 0x11];
    let padIdx = 0;
    while (bits.length < maxDataBits) {
      const pad = padBytes[padIdx++ % 2];
      for (let i = 7; i >= 0; i--) {
        bits.push((pad >> i) & 1);
      }
    }

    // Convert bits to bytes
    const dataBytes = [];
    for (let i = 0; i < bits.length; i += 8) {
      let b = 0;
      for (let j = 0; j < 8; j++) {
        b = (b << 1) | bits[i + j];
      }
      dataBytes.push(b);
    }

    // Generate error correction
    const ecBytes = rsCalculateRemainder(dataBytes, this.versionInfo.ecBytes);
    const finalBytes = dataBytes.concat(ecBytes);

    // Convert all to bits
    const finalBits = [];
    for (const byte of finalBytes) {
      for (let i = 7; i >= 0; i--) {
        finalBits.push((byte >> i) & 1);
      }
    }
    return finalBits;
  }

  placeDataBits(dataBits) {
    let bitIdx = 0;
    let upwards = true;

    for (let c = this.size - 1; c > 0; c -= 2) {
      if (c === 6) c--; // Skip vertical timing pattern column

      const rows = upwards ?
        Array.from({ length: this.size }, (_, i) => this.size - 1 - i) :
        Array.from({ length: this.size }, (_, i) => i);

      for (const r of rows) {
        for (const col of [c, c - 1]) {
          if (!this.isFunction[r][col]) {
            const bit = bitIdx < dataBits.length ? dataBits[bitIdx++] : 0;
            // Mask pattern 0: (row + col) % 2 === 0
            const mask = (r + col) % 2 === 0;
            this.modules[r][col] = (bit === 1) !== mask;
          }
        }
      }
      upwards = !upwards;
    }
  }

  drawFormatInfo() {
    // Format bits for Error Correction Level L and Mask 0: 0x77c4 (15 bits)
    const formatBits = [1, 1, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 1, 0, 0];

    // Around top-left finder
    for (let i = 0; i <= 5; i++) this.setModule(8, i, formatBits[i] === 1);
    this.setModule(8, 7, formatBits[6] === 1);
    this.setModule(8, 8, formatBits[7] === 1);
    this.setModule(7, 8, formatBits[8] === 1);
    for (let i = 9; i < 15; i++) this.setModule(14 - i, 8, formatBits[i] === 1);

    // Around bottom-left / top-right
    for (let i = 0; i < 7; i++) this.setModule(this.size - 1 - i, 8, formatBits[i] === 1);
    for (let i = 7; i < 15; i++) this.setModule(8, this.size - 15 + i, formatBits[i] === 1);

    // Fixed dark module
    this.setModule(this.size - 8, 8, true);
  }

  generate() {
    // 1. Finder patterns
    this.drawFinderPattern(0, 0);
    this.drawFinderPattern(0, this.size - 7);
    this.drawFinderPattern(this.size - 7, 0);

    // 2. Timing patterns
    this.drawTimingPatterns();

    // 3. Alignment patterns
    this.addAlignmentPatterns();

    // 4. Reserve format info
    for (let i = 0; i < 9; i++) {
      if (i !== 6) {
        this.isFunction[8][i] = true;
        this.isFunction[i][8] = true;
      }
      this.isFunction[this.size - 1 - i][8] = true;
      this.isFunction[8][this.size - 1 - i] = true;
    }

    // 5. Data bits
    const dataBits = this.encodeData();
    this.placeDataBits(dataBits);

    // 6. Format info
    this.drawFormatInfo();

    return this.modules;
  }
}

/**
 * Renders a 2D boolean module array into crisp Unicode half-blocks:
 * Uses ▀ (upper half black), ▄ (lower half black), █ (full black), ' ' (white)
 */
function renderTerminalQr(matrix) {
  const quietZone = 2;
  const size = matrix.length;
  const paddedSize = size + quietZone * 2;

  function getPixel(r, c) {
    const mr = r - quietZone;
    const mc = c - quietZone;
    if (mr < 0 || mr >= size || mc < 0 || mc >= size) return false;
    return !!matrix[mr][mc];
  }

  const lines = [];
  for (let r = 0; r < paddedSize; r += 2) {
    let rowStr = '';
    for (let c = 0; c < paddedSize; c++) {
      const top = getPixel(r, c);
      const bottom = (r + 1 < paddedSize) ? getPixel(r + 1, c) : false;

      // Inverted for dark terminal backgrounds (white background, dark modules):
      // Black module is represented by foreground block.
      if (top && bottom) {
        rowStr += '█';
      } else if (top && !bottom) {
        rowStr += '▀';
      } else if (!top && bottom) {
        rowStr += '▄';
      } else {
        rowStr += ' ';
      }
    }
    lines.push(rowStr);
  }
  return lines.join('\n');
}

/**
 * Generates an SVG data string for the browser QR viewer
 */
function renderSvgQr(matrix, cellSize = 8) {
  const size = matrix.length;
  const quiet = 2;
  const total = (size + quiet * 2) * cellSize;

  let rects = '';
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (matrix[r][c]) {
        const x = (c + quiet) * cellSize;
        const y = (r + quiet) * cellSize;
        rects += `<rect x="${x}" y="${y}" width="${cellSize}" height="${cellSize}" fill="#00D2FF"/>\n`;
      }
    }
  }

  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${total} ${total}" width="${total}" height="${total}">
    <rect width="${total}" height="${total}" fill="#0A0F1D" rx="16"/>
    ${rects}
  </svg>`;
}

function generateQrCode(text) {
  const encoder = new SimpleQrEncoder(text);
  const matrix = encoder.generate();
  return {
    matrix,
    terminalText: renderTerminalQr(matrix),
    svgText: renderSvgQr(matrix)
  };
}

module.exports = {
  generateQrCode,
  renderTerminalQr,
  renderSvgQr
};
