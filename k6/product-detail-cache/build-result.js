const fs = require('fs');

const args = parseArgs(process.argv.slice(2));
const before = readJsonLine(args.before);
const after = readJsonLine(args.after);
const samples = readJsonLines(args.samples);
const k6Summary = readJsonFile(args.k6Summary);

const durationSeconds = secondsBetween(before.timestampEpochMs, after.timestampEpochMs);
const mysqlIntervals = intervals(samples.length >= 2 ? samples : [before, after]);
const cpuIntervals = mysqlIntervals.filter((item) => item.cpu.available);

const questionsDelta = delta(after.mysql.questions, before.mysql.questions);
const queriesDelta = delta(after.mysql.queries, before.mysql.queries);
const comSelectDelta = delta(after.mysql.comSelect, before.mysql.comSelect);

const result = {
  title: `${args.scenario}-${args.timestamp}`,
  generatedAt: new Date().toISOString(),
  scenario: args.scenario,
  distribution: k6Summary.distribution,
  target: k6Summary.target,
  endpoint: k6Summary.endpoint,
  durationSeconds,
  k6: {
    productIdFile: k6Summary.productIdFile,
    productIdCount: k6Summary.productIdCount,
    seed: k6Summary.seed,
    configuredTps: k6Summary.tps,
    configuredDuration: k6Summary.duration,
    totalRequests: k6Summary.totalRequests,
    successfulRequests: k6Summary.successfulRequests,
    failedRequests: k6Summary.failedRequests,
    notFoundRequests: k6Summary.notFoundRequests,
    successRatePercent: k6Summary.successRatePercent,
    failureRatePercent: k6Summary.failureRatePercent,
    latencyMs: k6Summary.latencyMs,
  },
  mysql: {
    source: {
      host: args.dbHost,
      port: Number(args.dbPort),
      database: args.dbName,
      user: args.dbUser,
      cpuSource: 'Windows PowerShell Get-Process mysqld CPU',
    },
    queryRequests: {
      questions: questionsDelta,
      queries: queriesDelta,
      comSelect: comSelectDelta,
      qpsByQuestions: perSecond(questionsDelta, durationSeconds),
      qpsByQueries: perSecond(queriesDelta, durationSeconds),
      qpsByComSelect: perSecond(comSelectDelta, durationSeconds),
      sampledQpsByQuestions: summarize(mysqlIntervals.map((item) => item.questionsPerSecond)),
      sampledQpsByQueries: summarize(mysqlIntervals.map((item) => item.queriesPerSecond)),
    },
    cpu: cpuSummary(before, after, cpuIntervals, durationSeconds),
    status: {
      before: before.mysql,
      after: after.mysql,
      delta: {
        connections: delta(after.mysql.connections, before.mysql.connections),
        threadsConnected: after.mysql.threadsConnected,
        threadsRunning: after.mysql.threadsRunning,
        innodbRowsRead: delta(after.mysql.innodbRowsRead, before.mysql.innodbRowsRead),
        innodbBufferPoolReadRequests: delta(
          after.mysql.innodbBufferPoolReadRequests,
          before.mysql.innodbBufferPoolReadRequests
        ),
        innodbBufferPoolReads: delta(after.mysql.innodbBufferPoolReads, before.mysql.innodbBufferPoolReads),
        innodbDataReads: delta(after.mysql.innodbDataReads, before.mysql.innodbDataReads),
        innodbDataWrites: delta(after.mysql.innodbDataWrites, before.mysql.innodbDataWrites),
        createdTmpDiskTables: delta(after.mysql.createdTmpDiskTables, before.mysql.createdTmpDiskTables),
      },
      sampledThreadsConnected: summarize(samples.map((sample) => sample.mysql.threadsConnected)),
      sampledThreadsRunning: summarize(samples.map((sample) => sample.mysql.threadsRunning)),
    },
  },
};

fs.writeFileSync(args.resultFile, `${JSON.stringify(result, null, 2)}\n`);

function cpuSummary(beforeSnapshot, afterSnapshot, intervalRows, totalSeconds) {
  if (!beforeSnapshot.cpu.available || !afterSnapshot.cpu.available) {
    return {
      available: false,
      reason: beforeSnapshot.cpu.reason || afterSnapshot.cpu.reason || 'CPU sample is unavailable',
      processName: 'mysqld',
    };
  }

  const cpuSecondsDelta = delta(afterSnapshot.cpu.cpuSeconds, beforeSnapshot.cpu.cpuSeconds);
  const rawPercent = perSecond(cpuSecondsDelta, totalSeconds) * 100;
  const logicalProcessors = afterSnapshot.cpu.logicalProcessors || beforeSnapshot.cpu.logicalProcessors || null;
  const normalizedPercent = logicalProcessors ? rawPercent / logicalProcessors : null;

  return {
    available: true,
    processName: 'mysqld',
    logicalProcessors,
    cpuSecondsDelta,
    rawProcessCpuPercent: rawPercent,
    normalizedProcessCpuPercent: normalizedPercent,
    sampledRawProcessCpuPercent: summarize(intervalRows.map((item) => item.rawCpuPercent)),
    sampledNormalizedProcessCpuPercent: summarize(intervalRows.map((item) => item.normalizedCpuPercent)),
  };
}

function intervals(rows) {
  const result = [];
  for (let index = 1; index < rows.length; index += 1) {
    const previous = rows[index - 1];
    const current = rows[index];
    const intervalSeconds = secondsBetween(previous.timestampEpochMs, current.timestampEpochMs);
    const questionsDelta = delta(current.mysql.questions, previous.mysql.questions);
    const queriesDelta = delta(current.mysql.queries, previous.mysql.queries);
    const row = {
      questionsPerSecond: perSecond(questionsDelta, intervalSeconds),
      queriesPerSecond: perSecond(queriesDelta, intervalSeconds),
      cpu: {
        available: previous.cpu.available && current.cpu.available,
      },
    };

    if (row.cpu.available) {
      const cpuSecondsDelta = delta(current.cpu.cpuSeconds, previous.cpu.cpuSeconds);
      const rawCpuPercent = perSecond(cpuSecondsDelta, intervalSeconds) * 100;
      const logicalProcessors = current.cpu.logicalProcessors || previous.cpu.logicalProcessors;
      row.rawCpuPercent = rawCpuPercent;
      row.normalizedCpuPercent = logicalProcessors ? rawCpuPercent / logicalProcessors : null;
    }
    result.push(row);
  }
  return result;
}

function summarize(values) {
  const numbers = values.filter((value) => Number.isFinite(value));
  if (numbers.length === 0) {
    return {
      count: 0,
      avg: null,
      min: null,
      max: null,
    };
  }

  return {
    count: numbers.length,
    avg: numbers.reduce((sum, value) => sum + value, 0) / numbers.length,
    min: Math.min(...numbers),
    max: Math.max(...numbers),
  };
}

function secondsBetween(startEpochMs, endEpochMs) {
  return Math.max((endEpochMs - startEpochMs) / 1000, 0.001);
}

function delta(afterValue, beforeValue) {
  if (!Number.isFinite(afterValue) || !Number.isFinite(beforeValue)) {
    return null;
  }
  return afterValue - beforeValue;
}

function perSecond(value, seconds) {
  if (!Number.isFinite(value) || !Number.isFinite(seconds) || seconds <= 0) {
    return null;
  }
  return value / seconds;
}

function readJsonFile(path) {
  return JSON.parse(fs.readFileSync(path, 'utf8'));
}

function readJsonLine(path) {
  const lines = readJsonLines(path);
  if (lines.length === 0) {
    throw new Error(`JSON line file is empty: ${path}`);
  }
  return lines[lines.length - 1];
}

function readJsonLines(path) {
  if (!fs.existsSync(path)) {
    return [];
  }
  return fs.readFileSync(path, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line) => JSON.parse(line));
}

function parseArgs(values) {
  const result = {};
  for (let index = 0; index < values.length; index += 2) {
    const key = values[index]?.replace(/^--/, '');
    const value = values[index + 1];
    if (!key || value === undefined) {
      throw new Error(`Invalid argument near ${values[index]}`);
    }
    result[key] = value;
  }

  const required = [
    'scenario',
    'timestamp',
    'before',
    'after',
    'samples',
    'k6Summary',
    'resultFile',
    'dbHost',
    'dbPort',
    'dbName',
    'dbUser',
  ];
  for (const key of required) {
    if (!result[key]) {
      throw new Error(`Missing required argument: --${key}`);
    }
  }
  return result;
}
