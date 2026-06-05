const fs = require('fs');
const path = require('path');
const readline = require('readline');

const DEFAULT_SUMMARY_PATH = path.resolve(
  'k6/results/event-pipeline-user-behavior-events-summary.json'
);
const DEFAULT_OUTPUT_PATH = process.platform === 'win32'
  ? 'C:\\tmp\\aws-shop-event-pipeline\\user-behavior-events.jsonl'
  : '/tmp/aws-shop-event-pipeline/user-behavior-events.jsonl';

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const mode = options.mode || 'verify';
  const jsonlPath = path.resolve(options.file || process.env.EVENT_HADOOP_OUTPUT_PATH || DEFAULT_OUTPUT_PATH);

  if (mode === 'baseline') {
    const count = await countJsonLines(jsonlPath);
    console.log(count.totalLines);
    return;
  }

  const summaryPath = path.resolve(options.summary || DEFAULT_SUMMARY_PATH);
  const beforeCount = Number(options.beforeCount || process.env.EVENT_HADOOP_BEFORE_COUNT || 0);
  const summary = readJson(summaryPath);
  const count = await countJsonLines(jsonlPath);
  const appendedLines = Math.max(count.totalLines - beforeCount, 0);
  const acceptedEvents = Number(summary.acceptedEvents || 0);
  const failedEvents = Number(summary.failedEvents || 0);
  const totalRequests = Number(summary.totalRequests || acceptedEvents + failedEvents);
  const storageSuccessRatePercent = percentage(appendedLines, acceptedEvents);
  const apiSuccessRatePercent = percentage(acceptedEvents, totalRequests);
  const apiFailureRatePercent = percentage(failedEvents, totalRequests);

  const result = {
    summaryPath,
    jsonlPath,
    beforeCount,
    totalJsonlLines: count.totalLines,
    validJsonLines: count.validJsonLines,
    invalidJsonLines: count.invalidJsonLines,
    appendedLines,
    totalRequests,
    acceptedEvents,
    failedEvents,
    apiSuccessRatePercent,
    apiFailureRatePercent,
    storageSuccessRatePercent,
    missingStoredLines: Math.max(acceptedEvents - appendedLines, 0),
    extraStoredLines: Math.max(appendedLines - acceptedEvents, 0),
  };

  const outputPath = options.output || 'event-pipeline/test/results/hadoop-jsonl-count-summary.json';
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  fs.writeFileSync(outputPath, `${JSON.stringify(result, null, 2)}\n`);

  printResult(result, outputPath);

  if (options.failOnMismatch !== 'false' && appendedLines !== acceptedEvents) {
    process.exitCode = 1;
  }
}

function parseArgs(args) {
  const options = {};

  for (const arg of args) {
    if (!arg.startsWith('--')) {
      continue;
    }

    const [key, value = 'true'] = arg.slice(2).split('=');
    options[toCamelCase(key)] = value;
  }

  return options;
}

function toCamelCase(value) {
  return value.replace(/-([a-z])/g, (_, character) => character.toUpperCase());
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

async function countJsonLines(filePath) {
  if (!fs.existsSync(filePath)) {
    return {
      totalLines: 0,
      validJsonLines: 0,
      invalidJsonLines: 0,
    };
  }

  const stream = fs.createReadStream(filePath, { encoding: 'utf8' });
  const lines = readline.createInterface({
    input: stream,
    crlfDelay: Infinity,
  });
  let totalLines = 0;
  let validJsonLines = 0;
  let invalidJsonLines = 0;

  for await (const line of lines) {
    if (line.trim().length === 0) {
      continue;
    }

    totalLines += 1;
    try {
      JSON.parse(line);
      validJsonLines += 1;
    } catch {
      invalidJsonLines += 1;
    }
  }

  return {
    totalLines,
    validJsonLines,
    invalidJsonLines,
  };
}

function percentage(part, whole) {
  if (whole === 0) {
    return 0;
  }
  return (part / whole) * 100;
}

function printResult(result, outputPath) {
  console.log('');
  console.log('=== Hadoop JSONL Count Verification ===');
  console.log(`JSONL file: ${result.jsonlPath}`);
  console.log(`Before lines: ${result.beforeCount}`);
  console.log(`After lines: ${result.totalJsonlLines}`);
  console.log(`Appended lines: ${result.appendedLines}`);
  console.log(`Accepted events: ${result.acceptedEvents}`);
  console.log(`Failed events: ${result.failedEvents}`);
  console.log(`API success rate: ${result.apiSuccessRatePercent.toFixed(2)}%`);
  console.log(`API failure rate: ${result.apiFailureRatePercent.toFixed(2)}%`);
  console.log(`Storage success rate: ${result.storageSuccessRatePercent.toFixed(2)}%`);
  console.log(`Missing stored lines: ${result.missingStoredLines}`);
  console.log(`Extra stored lines: ${result.extraStoredLines}`);
  console.log(`Invalid JSON lines in file: ${result.invalidJsonLines}`);
  console.log(`Result file: ${outputPath}`);
  console.log('');
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
