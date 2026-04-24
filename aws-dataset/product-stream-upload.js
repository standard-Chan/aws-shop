const fs = require('fs');
const http = require('http');
const https = require('https');
const path = require('path');
const { Transform } = require('stream');
const { pipeline } = require('stream/promises');
const zlib = require('zlib');

const DATASET_DIR = __dirname;
const DOWNLOAD_URLS_FILE = path.join(DATASET_DIR, 'meta-download-urls.txt');
const PROGRESS_FILE = path.join(DATASET_DIR, 'product-stream-upload-progress.json');
const DEFAULT_UPLOAD_URL = 'http://localhost:8080/api/data-import/upload';
const PROGRESS_LOG_INTERVAL_MS = 5000;

const uploadBaseUrl = process.env.UPLOAD_URL || DEFAULT_UPLOAD_URL;
const retryIncomplete = process.argv.includes('--retry-incomplete');
const selectedLineRange = parseSelectedLineRange(process.argv.slice(2));

function parseSelectedLineRange(args) {
  const line = readPositiveIntegerOption(args, '--line');
  const fromLine = readPositiveIntegerOption(args, '--from-line');
  const toLine = readPositiveIntegerOption(args, '--to-line');

  if (line && (fromLine || toLine)) {
    throw new Error('Use either --line or --from-line/--to-line, not both.');
  }

  if (line) {
    return {
      fromLine: line,
      toLine: line,
    };
  }

  if (fromLine && toLine && fromLine > toLine) {
    throw new Error('--from-line must be less than or equal to --to-line.');
  }

  return {
    fromLine,
    toLine,
  };
}

function readPositiveIntegerOption(args, optionName) {
  const optionIndex = args.indexOf(optionName);
  if (optionIndex === -1) {
    return null;
  }

  const value = args[optionIndex + 1];
  if (!value || value.startsWith('--')) {
    throw new Error(`${optionName} requires a positive integer value.`);
  }

  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1) {
    throw new Error(`${optionName} must be a positive integer.`);
  }

  return parsed;
}

function readDownloadUrls(filePath) {
  return fs.readFileSync(filePath, 'utf8')
    .split(/\r?\n/)
    .map((line, index) => ({
      lineNumber: index + 1,
      url: line.trim(),
    }))
    .filter((item) => item.url.length > 0 && !item.url.startsWith('#'));
}

function readProgress(filePath) {
  if (!fs.existsSync(filePath)) {
    return {
      uploadBaseUrl,
      sourceFile: path.basename(DOWNLOAD_URLS_FILE),
      startedAt: new Date().toISOString(),
      updatedAt: null,
      items: {},
    };
  }

  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeProgress(filePath, progress) {
  updateSummary(progress);
  progress.updatedAt = new Date().toISOString();

  const tempPath = `${filePath}.tmp`;
  fs.writeFileSync(tempPath, `${JSON.stringify(progress, null, 2)}\n`);
  fs.renameSync(tempPath, filePath);
}

function updateSummary(progress) {
  const items = Object.values(progress.items);
  const completedItems = items.filter((item) => item.status === 'completed');
  const failedItems = items.filter((item) => item.status === 'failed');
  const runningItems = items.filter((item) => item.status === 'running');

  progress.completedCount = completedItems.length;
  progress.failedCount = failedItems.length;
  progress.runningCount = runningItems.length;
  progress.lastStartedUrl = items
    .filter((item) => item.startedAt)
    .sort((left, right) => left.startedAt.localeCompare(right.startedAt))
    .at(-1)?.downloadUrl || null;
  progress.lastCompletedUrl = completedItems
    .filter((item) => item.completedAt)
    .sort((left, right) => left.completedAt.localeCompare(right.completedAt))
    .at(-1)?.downloadUrl || null;
  progress.totalDurationMs = completedItems.reduce((sum, item) => sum + (item.durationMs || 0), 0);
}

function filenameFromDownloadUrl(downloadUrl) {
  const pathname = new URL(downloadUrl).pathname;
  const basename = path.posix.basename(pathname);
  return basename.replace(/(\.jsonl)?\.gz$/i, '').replace(/\.jsonl$/i, '');
}

function buildUploadUrl(baseUrl, filename) {
  const uploadUrl = new URL(baseUrl);
  uploadUrl.searchParams.set('filename', filename);
  return uploadUrl.toString();
}

function requestClient(url) {
  return url.startsWith('https:') ? https : http;
}

function createDownloadRequest(downloadUrl, redirectCount = 0) {
  if (redirectCount > 5) {
    return Promise.reject(new Error(`Too many redirects: ${downloadUrl}`));
  }

  return new Promise((resolve, reject) => {
    const req = requestClient(downloadUrl).get(downloadUrl, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
        res.resume();
        const location = res.headers.location;
        if (!location) {
          reject(new Error(`Redirect response without location: ${downloadUrl}`));
          return;
        }

        const redirectedUrl = new URL(location, downloadUrl).toString();
        createDownloadRequest(redirectedUrl, redirectCount + 1).then(resolve, reject);
        return;
      }

      if (res.statusCode < 200 || res.statusCode >= 300) {
        res.resume();
        reject(new Error(`Download failed with status ${res.statusCode}: ${downloadUrl}`));
        return;
      }

      resolve(res);
    });

    req.on('error', reject);
  });
}

function createUploadRequest(uploadUrl) {
  const req = requestClient(uploadUrl).request(uploadUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/octet-stream',
      'Transfer-Encoding': 'chunked',
    },
  });

  req.responsePromise = new Promise((resolve, reject) => {
    req.on('response', (res) => {
      let body = '';

      res.setEncoding('utf8');
      res.on('data', (chunk) => {
        body += chunk;
      });
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(`Upload failed with status ${res.statusCode}: ${body}`));
          return;
        }

        resolve({
          statusCode: res.statusCode,
          body,
        });
      });
    });

    req.on('error', reject);
  });

  return req;
}

async function streamUpload(downloadUrl, uploadUrl) {
  const downloadRes = await createDownloadRequest(downloadUrl);
  const gunzip = zlib.createGunzip();
  const stats = createTransferStats();
  const compressedMeter = createByteMeter(stats, 'compressedBytes', 'firstCompressedByteAtMs');
  const decompressedMeter = createByteMeter(stats, 'decompressedBytes', 'firstDecompressedByteAtMs');
  const uploadReq = createUploadRequest(uploadUrl);
  const stopProgressLogger = startProgressLogger(stats);

  try {
    await pipeline(downloadRes, compressedMeter, gunzip, decompressedMeter, uploadReq);
    await uploadReq.responsePromise;
    stats.completedAtMs = Date.now();
    return stats;
  } finally {
    stopProgressLogger();
  }
}

function shouldSkip(item) {
  if (!item) {
    return false;
  }

  if (item.status === 'completed') {
    return true;
  }

  return !retryIncomplete && item.status === 'running';
}

async function run() {
  const allDownloadItems = readDownloadUrls(DOWNLOAD_URLS_FILE);
  const downloadItems = filterDownloadItemsByLineRange(allDownloadItems, selectedLineRange);
  const progress = readProgress(PROGRESS_FILE);

  progress.uploadBaseUrl = uploadBaseUrl;
  progress.sourceFile = path.basename(DOWNLOAD_URLS_FILE);
  progress.totalCount = allDownloadItems.length;
  progress.selectedLineRange = selectedLineRange;
  progress.selectedCount = downloadItems.length;

  for (const [index, item] of downloadItems.entries()) {
    const downloadUrl = item.url;
    const current = progress.items[downloadUrl];
    if (shouldSkip(current)) {
      console.log(`[skip] ${index + 1}/${downloadItems.length} line ${item.lineNumber} ${downloadUrl} (${current.status})`);
      continue;
    }

    const filename = filenameFromDownloadUrl(downloadUrl);
    const uploadUrl = buildUploadUrl(uploadBaseUrl, filename);
    const startedAtMs = Date.now();

    progress.items[downloadUrl] = {
      index: allDownloadItems.findIndex((candidate) => candidate.url === downloadUrl),
      selectedIndex: index,
      lineNumber: item.lineNumber,
      status: 'running',
      filename,
      downloadUrl,
      uploadUrl,
      startedAt: new Date(startedAtMs).toISOString(),
      completedAt: null,
      durationMs: null,
      error: null,
    };
    writeProgress(PROGRESS_FILE, progress);

    console.log(`[start] line=${item.lineNumber} file=${filename} uploadUrl=${uploadUrl}`);

    try {
      const transferStats = await streamUpload(downloadUrl, uploadUrl);

      const completedAtMs = Date.now();
      const summary = buildTransferSummary(transferStats, startedAtMs, completedAtMs);
      progress.items[downloadUrl] = {
        ...progress.items[downloadUrl],
        status: 'completed',
        completedAt: new Date(completedAtMs).toISOString(),
        durationMs: summary.totalElapsedMs,
        compressedBytes: transferStats.compressedBytes,
        decompressedBytes: transferStats.decompressedBytes,
        firstCompressedByteDelayMs: summary.firstCompressedDelayMs,
        firstDecompressedByteDelayMs: summary.firstDecompressedDelayMs,
        inflateStartupMs: summary.inflateStartupMs,
        downstreamAfterDecompressMs: summary.downstreamAfterDecompressMs,
        bottleneckStage: summary.bottleneckStage,
      };
      writeProgress(PROGRESS_FILE, progress);

      console.log(buildCompletedLog(item.lineNumber, filename, summary, transferStats));
    } catch (error) {
      const failedAtMs = Date.now();
      progress.items[downloadUrl] = {
        ...progress.items[downloadUrl],
        status: 'failed',
        completedAt: new Date(failedAtMs).toISOString(),
        durationMs: failedAtMs - startedAtMs,
        error: error.stack || error.message,
      };
      writeProgress(PROGRESS_FILE, progress);

      console.error(`[failed] ${index + 1}/${downloadItems.length} line ${item.lineNumber} ${filename}`);
      console.error(error);
      process.exitCode = 1;
      return;
    }
  }
}

function filterDownloadItemsByLineRange(items, lineRange) {
  const { fromLine, toLine } = lineRange;

  return items.filter((item) => {
    if (fromLine && item.lineNumber < fromLine) {
      return false;
    }

    if (toLine && item.lineNumber > toLine) {
      return false;
    }

    return true;
  });
}

function createTransferStats() {
  return {
    startedAtMs: Date.now(),
    compressedBytes: 0,
    decompressedBytes: 0,
    firstCompressedByteAtMs: null,
    firstDecompressedByteAtMs: null,
    completedAtMs: null,
  };
}

function createByteMeter(stats, byteField, firstByteField) {
  return new Transform({
    transform(chunk, encoding, callback) {
      if (stats[firstByteField] === null) {
        stats[firstByteField] = Date.now();
      }
      stats[byteField] += chunk.length;
      callback(null, chunk);
    },
  });
}

function startProgressLogger(stats) {
  const timer = setInterval(() => {
    console.log(buildProgressLog(stats));
  }, PROGRESS_LOG_INTERVAL_MS);

  if (typeof timer.unref === 'function') {
    timer.unref();
  }

  return () => clearInterval(timer);
}

function diffOrNull(startedAtMs, targetAtMs) {
  return targetAtMs === null ? null : targetAtMs - startedAtMs;
}

function diffBetweenOrNull(startedAtMs, endedAtMs) {
  return startedAtMs === null || endedAtMs === null ? null : endedAtMs - startedAtMs;
}

function buildTransferSummary(stats, startedAtMs, completedAtMs) {
  const totalElapsedMs = completedAtMs - startedAtMs;
  const firstCompressedDelayMs = diffOrNull(startedAtMs, stats.firstCompressedByteAtMs);
  const firstDecompressedDelayMs = diffOrNull(startedAtMs, stats.firstDecompressedByteAtMs);
  const inflateStartupMs = diffBetweenOrNull(
    stats.firstCompressedByteAtMs,
    stats.firstDecompressedByteAtMs,
  );
  const downstreamAfterDecompressMs = diffOrNull(stats.firstDecompressedByteAtMs, completedAtMs);
  const bottleneckStage = estimateBottleneckStage({
    firstCompressedDelayMs,
    inflateStartupMs,
    downstreamAfterDecompressMs,
  });

  return {
    totalElapsedMs,
    firstCompressedDelayMs,
    firstDecompressedDelayMs,
    inflateStartupMs,
    downstreamAfterDecompressMs,
    bottleneckStage,
  };
}

function buildProgressLog(stats) {
  const summary = buildTransferSummary(stats, stats.startedAtMs, Date.now());

  return `[progress] total=${formatDuration(summary.totalElapsedMs)} | download_wait=${formatOptionalDuration(summary.firstCompressedDelayMs)} | decompress_start=${formatOptionalDuration(summary.inflateStartupMs)} | after_decompress=${formatOptionalDuration(summary.downstreamAfterDecompressMs)} | compressed=${formatBytes(stats.compressedBytes)} @ ${formatRate(stats.compressedBytes, summary.totalElapsedMs)} | decompressed=${formatBytes(stats.decompressedBytes)} @ ${formatRate(stats.decompressedBytes, summary.totalElapsedMs)} | current_bottleneck=${summary.bottleneckStage}`;
}

function buildCompletedLog(lineNumber, filename, summary, stats) {
  return `[speed] line=${lineNumber} file=${filename} | total=${formatDuration(summary.totalElapsedMs)} | 1.download_wait=${formatOptionalDuration(summary.firstCompressedDelayMs)} | 2.decompress_start=${formatOptionalDuration(summary.inflateStartupMs)} | 3.after_decompress=${formatOptionalDuration(summary.downstreamAfterDecompressMs)} | compressed=${formatBytes(stats.compressedBytes)} @ ${formatRate(stats.compressedBytes, summary.totalElapsedMs)} | decompressed=${formatBytes(stats.decompressedBytes)} @ ${formatRate(stats.decompressedBytes, summary.totalElapsedMs)} | ratio=${formatCompressionRatio(stats.compressedBytes, stats.decompressedBytes)} | bottleneck=${summary.bottleneckStage}`;
}

function estimateBottleneckStage(stageDurations) {
  const candidates = [
    { name: 'download_wait', durationMs: stageDurations.firstCompressedDelayMs },
    { name: 'decompress_start', durationMs: stageDurations.inflateStartupMs },
    { name: 'after_decompress', durationMs: stageDurations.downstreamAfterDecompressMs },
  ].filter((candidate) => candidate.durationMs !== null);

  if (candidates.length === 0) {
    return 'unknown';
  }

  return candidates.reduce((max, current) => (
    current.durationMs > max.durationMs ? current : max
  )).name;
}

function formatDuration(durationMs) {
  return `${durationMs} ms / ${(durationMs / 1000).toFixed(3)} s / ${(durationMs / 60000).toFixed(2)} min`;
}

function formatOptionalDuration(durationMs) {
  return durationMs === null ? 'n/a' : formatDuration(durationMs);
}

function formatBytes(bytes) {
  return `${bytes} B / ${(bytes / (1024 * 1024)).toFixed(2)} MiB`;
}

function formatRate(bytes, durationMs) {
  if (durationMs <= 0) {
    return 'n/a';
  }

  const mibPerSecond = (bytes / (1024 * 1024)) / (durationMs / 1000);
  return `${mibPerSecond.toFixed(2)} MiB/s`;
}

function formatCompressionRatio(compressedBytes, decompressedBytes) {
  if (compressedBytes <= 0 || decompressedBytes <= 0) {
    return 'n/a';
  }

  return `${(decompressedBytes / compressedBytes).toFixed(2)}x`;
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
