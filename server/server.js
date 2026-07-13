const crypto = require("crypto");
const { fork: forkProcess } = require("child_process");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const { URL } = require("url");

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || "0.0.0.0";
const SERVER_TOKEN = (process.env.SERVER_TOKEN || "879487").trim();
const DATA_DIR = path.join(__dirname, "data");
const DATA_FILE = path.join(DATA_DIR, "reservations.json");
const PU_BASE_URL = "https://apis.pocketuni.net";
const X_SIGN_KEY = Buffer.from([121, 121, 0, 19, 5, 49, 2, 43, 13, 17, 11, 9, 4, 29, 60, 11]);
const MAX_RETRY = Number(process.env.MAX_RETRY || 3);
const DISPATCH_INTERVAL_MS = Number(process.env.DISPATCH_INTERVAL_MS || 50);
const DUE_WINDOW_MS = Number(process.env.DUE_WINDOW_MS || 150);
const PRELOGIN_BEFORE_MS = Number(process.env.PRELOGIN_BEFORE_MS || 120000);
const PRELOGIN_INTERVAL_MS = Number(process.env.PRELOGIN_INTERVAL_MS || 10000);
const JOIN_CONCURRENCY = Number(process.env.JOIN_CONCURRENCY || 80);
const LOGIN_CONCURRENCY = Number(process.env.LOGIN_CONCURRENCY || 8);
const USE_FORK_WORKERS = process.env.USE_FORK_WORKERS === "1";
const WORKER_COUNT = Math.max(1, Number(process.env.WORKER_COUNT || Math.min(os.cpus().length || 1, 4)));
const WORKER_TASK_TIMEOUT_MS = Number(process.env.WORKER_TASK_TIMEOUT_MS || 30000);

let reservations = [];
let dispatching = false;
let preloginRunning = false;
let joinWorkerPool = null;

if (process.env.PU_JOIN_WORKER === "1") {
  startJoinWorker();
} else {
  startMain();
}

function startMain() {
  reservations = normalizeReservations(loadReservations());
  joinWorkerPool = USE_FORK_WORKERS ? createJoinWorkerPool() : null;

  setInterval(() => {
    dispatchDueReservations().catch((err) => console.error("dispatch failed:", err.message));
  }, DISPATCH_INTERVAL_MS).unref();

  setInterval(() => {
    preloginUpcomingReservations().catch((err) => console.error("prelogin failed:", err.message));
  }, PRELOGIN_INTERVAL_MS).unref();

  dispatchDueReservations().catch((err) => console.error("initial dispatch failed:", err.message));
  preloginUpcomingReservations().catch((err) => console.error("initial prelogin failed:", err.message));

  const server = http.createServer(async (req, res) => {
    try {
      const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
      if (!authorized(req)) {
        return sendJson(res, 401, { ok: false, message: "服务器密钥不正确" });
      }
      if (url.pathname === "/health") {
        return sendJson(res, 200, schedulerHealth());
      }
      if (req.method === "POST" && url.pathname === "/api/reservations") {
        return createReservation(req, res);
      }
      if (req.method === "GET" && url.pathname === "/api/reservations") {
        return listReservations(url, res);
      }
      const match = url.pathname.match(/^\/api\/reservations\/([^/]+)(?:\/(cancel))?$/);
      if (match && req.method === "GET" && !match[2]) {
        return getReservation(match[1], res);
      }
      if (match && req.method === "DELETE" && !match[2]) {
        return deleteReservation(match[1], res);
      }
      if (match && req.method === "POST" && match[2] === "cancel") {
        return cancelReservation(match[1], res);
      }
      sendJson(res, 404, { ok: false, message: "接口不存在" });
    } catch (err) {
      sendJson(res, 500, { ok: false, message: err.message || "服务器异常" });
    }
  });

  server.listen(PORT, HOST, () => {
    console.log(`PU reservation server listening on http://${HOST}:${PORT}`);
    console.log(`scheduler: interval=${DISPATCH_INTERVAL_MS}ms dueWindow=${DUE_WINDOW_MS}ms joinConcurrency=${JOIN_CONCURRENCY} preloginBefore=${PRELOGIN_BEFORE_MS}ms`);
    console.log(`workers: ${joinWorkerPool ? `fork enabled, count=${WORKER_COUNT}` : "fork disabled"}`);
  });
}

async function createReservation(req, res) {
  const body = await readJson(req);
  const sid = Number(body.sid || 0);
  const username = clean(body.username);
  const activityId = Number(body.activityId || 0);
  const runAt = Number(body.runAt || parseTime(body.joinStartTime));
  if (!sid || !username || !activityId || !runAt) {
    return sendJson(res, 400, { ok: false, message: "缺少 sid、username、activityId 或 runAt" });
  }
  const now = Date.now();
  const id = taskId(sid, username, activityId);
  let reservation = reservations.find((item) => item.id === id);
  const next = {
    id,
    accountKey: `${sid}:${username}`,
    schoolName: clean(body.schoolName),
    sid,
    username,
    password: clean(body.password),
    token: clean(body.token),
    cid: Number(body.cid || 0),
    yid: Number(body.yid || 0),
    activityId,
    activityName: clean(body.activityName) || `活动 #${activityId}`,
    runAt,
    status: "pending",
    lastResult: reservation && reservation.lastResult ? reservation.lastResult : "服务器已接收预约任务",
    retryCount: 0,
    createdAt: reservation ? reservation.createdAt : now,
    updatedAt: now,
    lastRunAt: 0,
    running: false,
    preloginAt: 0,
    preloginStatus: "waiting"
  };
  if (reservation) {
    Object.assign(reservation, next);
  } else {
    reservation = next;
    reservations.push(reservation);
  }
  saveReservations();
  sendJson(res, 200, { ok: true, reservation: publicReservation(reservation) });
}

function listReservations(url, res) {
  const sid = Number(url.searchParams.get("sid") || 0);
  const username = clean(url.searchParams.get("username"));
  const result = reservations
    .filter((item) => (!sid || item.sid === sid) && (!username || item.username === username))
    .sort((a, b) => a.runAt - b.runAt)
    .map(publicReservation);
  sendJson(res, 200, { ok: true, reservations: result });
}

function getReservation(id, res) {
  const reservation = reservations.find((item) => item.id === decodeURIComponent(id));
  if (!reservation) return sendJson(res, 404, { ok: false, message: "预约任务不存在" });
  sendJson(res, 200, { ok: true, reservation: publicReservation(reservation) });
}

function cancelReservation(id, res) {
  const reservation = reservations.find((item) => item.id === decodeURIComponent(id));
  if (!reservation) return sendJson(res, 404, { ok: false, message: "预约任务不存在" });
  if (reservation.running) {
    return sendJson(res, 409, { ok: false, message: "任务正在执行，暂时不能取消" });
  }
  reservation.status = "cancelled";
  reservation.lastResult = "服务器任务已取消";
  reservation.updatedAt = Date.now();
  reservation.running = false;
  saveReservations();
  sendJson(res, 200, { ok: true, reservation: publicReservation(reservation) });
}

function deleteReservation(id, res) {
  const decoded = decodeURIComponent(id);
  const index = reservations.findIndex((item) => item.id === decoded);
  if (index < 0) return sendJson(res, 404, { ok: false, message: "预约任务不存在" });
  const reservation = reservations[index];
  if (reservation.running) {
    return sendJson(res, 409, { ok: false, message: "任务正在执行，暂时不能删除" });
  }
  reservations.splice(index, 1);
  saveReservations();
  sendJson(res, 200, { ok: true, deleted: true, id: decoded });
}

async function dispatchDueReservations() {
  if (dispatching) return;
  dispatching = true;
  try {
    const now = Date.now();
    const dueTasks = reservations
      .filter((item) => item.status === "pending" && !item.running && item.runAt <= now)
      .sort((a, b) => a.runAt - b.runAt || a.createdAt - b.createdAt);
    if (dueTasks.length === 0) return;
    for (const task of dueTasks) {
      task.running = true;
      task.dispatchedAt = now;
      task.lastResult = task.runAt > now ? "已进入同批次执行队列" : task.lastResult;
    }
    saveReservations();
    if (joinWorkerPool) {
      await runForkedJoinBatch(dueTasks);
    } else {
      await runPool(dueTasks, JOIN_CONCURRENCY, executeReservation);
    }
    saveReservations();
  } finally {
    dispatching = false;
  }
}

async function runForkedJoinBatch(tasks) {
  const results = await joinWorkerPool.runBatch(tasks, JOIN_CONCURRENCY);
  const byId = new Map(results.map((result) => [result.id, result]));
  for (const task of tasks) {
    const result = byId.get(task.id);
    if (!result) {
      applyTaskFailure(task, new Error("worker 未返回执行结果"));
      continue;
    }
    if (result.ok) {
      task.status = "completed";
      task.lastResult = result.message || "报名请求已提交";
      task.updatedAt = Date.now();
      task.running = false;
      if (result.token) {
        task.token = result.token;
        task.preloginStatus = "ok";
      }
      continue;
    }
    applyTaskFailure(task, new Error(result.message || "网络或接口异常"), result.token);
  }
}

async function preloginUpcomingReservations() {
  if (preloginRunning) return;
  preloginRunning = true;
  try {
    const now = Date.now();
    const upcoming = reservations
      .filter((item) =>
        item.status === "pending" &&
        !item.running &&
        item.password &&
        item.runAt > now &&
        item.runAt - now <= PRELOGIN_BEFORE_MS &&
        (item.preloginStatus !== "ok" || !item.token) &&
        now - Number(item.preloginAt || 0) >= PRELOGIN_INTERVAL_MS
      )
      .sort((a, b) => a.runAt - b.runAt);
    if (upcoming.length === 0) return;
    await runPool(upcoming, LOGIN_CONCURRENCY, async (reservation) => {
      reservation.preloginAt = Date.now();
      try {
        const account = await login(accountFromReservation(reservation));
        reservation.token = account.token;
        reservation.preloginStatus = "ok";
        reservation.lastResult = "服务器已提前登录，等待到点执行";
        reservation.updatedAt = Date.now();
      } catch (err) {
        reservation.preloginStatus = "failed";
        reservation.lastResult = `提前登录失败：${err.message || "登录异常"}`;
        reservation.updatedAt = Date.now();
      }
    });
    saveReservations();
  } finally {
    preloginRunning = false;
  }
}

async function executeReservation(reservation) {
  if (reservation.status !== "pending") {
    reservation.running = false;
    return;
  }
  reservation.lastRunAt = Date.now();
  try {
    const account = await ensureToken(reservation);
    const message = await joinActivity(account, reservation.activityId);
    if (account.token) reservation.token = account.token;
    reservation.status = "completed";
    reservation.lastResult = message;
    reservation.updatedAt = Date.now();
  } catch (err) {
    applyTaskFailure(reservation, err);
  } finally {
    reservation.running = false;
  }
}

function applyTaskFailure(reservation, err, token) {
  if (token) {
    reservation.token = token;
    reservation.preloginStatus = "ok";
  }
  reservation.retryCount += 1;
  const message = err.message || "网络或接口异常";
  reservation.updatedAt = Date.now();
  if (reservation.retryCount <= MAX_RETRY) {
    reservation.status = "pending";
    reservation.lastResult = `第 ${reservation.retryCount}/${MAX_RETRY} 次尝试失败：${message}`;
    reservation.runAt = Date.now() + reservation.retryCount * 15000;
    reservation.preloginStatus = reservation.token ? "ok" : "waiting";
  } else {
    reservation.status = "failed";
    reservation.lastResult = `已重试 ${MAX_RETRY} 次仍失败：${message}`;
  }
  reservation.running = false;
}

async function ensureToken(reservation) {
  const account = accountFromReservation(reservation);
  if (account.token) return account;
  const logged = await login(account);
  reservation.token = logged.token;
  reservation.preloginStatus = "ok";
  reservation.updatedAt = Date.now();
  return logged;
}

function accountFromReservation(reservation) {
  return {
    schoolName: reservation.schoolName,
    sid: reservation.sid,
    username: reservation.username,
    password: reservation.password,
    token: reservation.token
  };
}

async function login(account) {
  if (!account.password) throw new Error("服务器任务缺少账号密码，无法刷新登录");
  const body = {
    userName: account.username,
    password: account.password,
    sid: account.sid,
    device: "pc"
  };
  const res = await puRequest("POST", "/uc/user/login", body, null, false);
  if (Number(res.code || 0) !== 0) {
    throw new Error(responseMessage(res, "登录失败"));
  }
  const data = res.data || {};
  return {
    ...account,
    token: data.token || ""
  };
}

async function joinActivity(account, activityId) {
  const body = { activityId };
  let res = await puRequest("POST", "/apis/activity/join", body, account, true);
  if (Number(res.code || 0) === 401) {
    const refreshed = await login(account);
    account.token = refreshed.token;
    res = await puRequest("POST", "/apis/activity/join", body, account, true);
  }
  if (Number(res.code || 0) !== 0) {
    throw new Error(responseMessage(res, "报名失败"));
  }
  return responseMessage(res, "报名请求已提交");
}

async function puRequest(method, endpoint, body, account, xSign) {
  const headers = {
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "zh-CN,zh;q=0.9",
    "DNT": "1",
    "Origin": "https://class.pocketuni.net",
    "Referer": "https://class.pocketuni.net/",
    "Sec-Fetch-Dest": "empty",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Site": "same-site",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0",
    "Content-Type": "application/json",
    "Authorization": account && account.token ? `Bearer ${account.token}:${account.sid}` : "Bearer :0"
  };
  if (account && xSign) headers["X-Sign"] = generateXSign(Date.now());
  const options = { method, headers };
  if (method === "POST") options.body = JSON.stringify(body || {});
  const response = await fetch(PU_BASE_URL + endpoint, options);
  const text = await response.text();
  let json;
  try {
    json = text ? JSON.parse(text) : {};
  } catch (err) {
    json = { code: response.status, msg: text || "接口返回不是 JSON" };
  }
  json._httpCode = response.status;
  return json;
}

async function runPool(items, limit, worker) {
  const concurrency = Math.max(1, Math.min(Number(limit || 1), items.length || 1));
  let nextIndex = 0;
  const workers = Array.from({ length: concurrency }, async () => {
    while (nextIndex < items.length) {
      const item = items[nextIndex++];
      await worker(item);
    }
  });
  return Promise.allSettled(workers);
}

function createJoinWorkerPool() {
  const workers = [];
  const pending = new Map();
  let nextWorker = 0;
  let nextJobId = 1;

  function spawn(index) {
    const child = forkProcess(__filename, [], {
      env: {
        ...process.env,
        PU_JOIN_WORKER: "1"
      },
      stdio: ["ignore", "inherit", "inherit", "ipc"]
    });
    child.on("message", (message) => {
      if (!message || message.type !== "join-result") return;
      const job = pending.get(message.jobId);
      if (!job) return;
      clearTimeout(job.timeout);
      pending.delete(message.jobId);
      job.resolve(message.result);
    });
    child.on("exit", (code, signal) => {
      for (const [jobId, job] of pending) {
        if (job.worker === child) {
          clearTimeout(job.timeout);
          pending.delete(jobId);
          job.resolve({ id: job.task.id, ok: false, message: `worker 退出：${signal || code}` });
        }
      }
      workers[index] = spawn(index);
    });
    return child;
  }

  for (let i = 0; i < WORKER_COUNT; i += 1) {
    workers.push(spawn(i));
  }

  function runTask(task) {
    return new Promise((resolve) => {
      const worker = workers[nextWorker % workers.length];
      nextWorker += 1;
      const jobId = nextJobId++;
      const timeout = setTimeout(() => {
        pending.delete(jobId);
        resolve({ id: task.id, ok: false, message: "worker 执行超时" });
      }, WORKER_TASK_TIMEOUT_MS);
      pending.set(jobId, { worker, task, timeout, resolve });
      worker.send({ type: "join", jobId, task: workerTask(task) }, (err) => {
        if (!err) return;
        clearTimeout(timeout);
        pending.delete(jobId);
        resolve({ id: task.id, ok: false, message: err.message || "worker 发送任务失败" });
      });
    });
  }

  async function runBatch(tasks, limit) {
    const results = [];
    await runPool(tasks, limit, async (task) => {
      results.push(await runTask(task));
    });
    return results;
  }

  return {
    runBatch,
    size: workers.length
  };
}

function workerTask(task) {
  return {
    id: task.id,
    sid: task.sid,
    username: task.username,
    password: task.password,
    token: task.token,
    activityId: task.activityId
  };
}

function startJoinWorker() {
  process.on("message", async (message) => {
    if (!message || message.type !== "join") return;
    const task = message.task || {};
    try {
      const account = {
        sid: task.sid,
        username: task.username,
        password: task.password,
        token: task.token
      };
      const messageText = await joinActivity(account, task.activityId);
      process.send({
        type: "join-result",
        jobId: message.jobId,
        result: {
          id: task.id,
          ok: true,
          message: messageText,
          token: account.token || task.token || ""
        }
      });
    } catch (err) {
      process.send({
        type: "join-result",
        jobId: message.jobId,
        result: {
          id: task.id,
          ok: false,
          message: err.message || "worker 执行异常",
          token: task.token || ""
        }
      });
    }
  });
}

function schedulerHealth() {
  const now = Date.now();
  const pending = reservations.filter((item) => item.status === "pending").length;
  const running = reservations.filter((item) => item.running).length;
  const nextRunAt = reservations
    .filter((item) => item.status === "pending")
    .reduce((min, item) => Math.min(min, item.runAt), Number.MAX_SAFE_INTEGER);
  return {
    ok: true,
    now,
    pending,
    running,
    nextRunAt: nextRunAt === Number.MAX_SAFE_INTEGER ? 0 : nextRunAt,
    config: {
      dispatchIntervalMs: DISPATCH_INTERVAL_MS,
      dueWindowMs: DUE_WINDOW_MS,
      preloginBeforeMs: PRELOGIN_BEFORE_MS,
      joinConcurrency: JOIN_CONCURRENCY,
      loginConcurrency: LOGIN_CONCURRENCY,
      useForkWorkers: Boolean(joinWorkerPool),
      workerCount: joinWorkerPool ? joinWorkerPool.size : 0,
      maxRetry: MAX_RETRY
    }
  };
}

function generateXSign(nowMillis) {
  const payload = JSON.stringify({
    echo: randomEcho(),
    timestamp: String(Math.floor(nowMillis / 1000)),
    client: "web"
  });
  const iv = crypto.randomBytes(16);
  const cipher = crypto.createCipheriv("aes-128-cbc", X_SIGN_KEY, iv);
  const encrypted = Buffer.concat([cipher.update(payload, "utf8"), cipher.final()]);
  return Buffer.concat([iv, encrypted]).toString("base64");
}

function randomEcho() {
  const chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  let text = "";
  for (let i = 0; i < 16; i += 1) {
    text += chars[crypto.randomInt(chars.length)];
  }
  return text;
}

function responseMessage(res, fallback) {
  const values = [res.msg, res.message, res.data, res.error];
  for (const value of values) {
    const text = clean(typeof value === "string" ? value : JSON.stringify(value || ""));
    if (text && text !== "{}" && text !== "[]") return text;
  }
  return fallback;
}

function publicReservation(item) {
  return {
    id: item.id,
    accountKey: item.accountKey,
    sid: item.sid,
    username: item.username,
    activityId: item.activityId,
    activityName: item.activityName,
    runAt: item.runAt,
    status: item.status,
    lastResult: item.lastResult,
    retryCount: item.retryCount,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
    running: Boolean(item.running),
    dispatchedAt: item.dispatchedAt || 0,
    lastRunAt: item.lastRunAt || 0,
    preloginStatus: item.preloginStatus || "waiting"
  };
}

function taskId(sid, username, activityId) {
  return crypto
    .createHash("sha1")
    .update(`${sid}:${username}:${activityId}`)
    .digest("hex")
    .slice(0, 20);
}

function parseTime(value) {
  if (!value) return 0;
  const text = String(value).trim().replace(/\./g, "-");
  const match = text.match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2})(?::(\d{2}))?)?/);
  if (!match) return 0;
  const year = Number(match[1]);
  const month = Number(match[2]) - 1;
  const day = Number(match[3]);
  const hour = Number(match[4] || 0);
  const minute = Number(match[5] || 0);
  const second = Number(match[6] || 0);
  return Date.UTC(year, month, day, hour - 8, minute, second);
}

function authorized(req) {
  if (!SERVER_TOKEN) return true;
  return clean(req.headers["x-server-token"]) === SERVER_TOKEN;
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (chunk) => {
      data += chunk;
      if (data.length > 1024 * 1024) {
        reject(new Error("请求体过大"));
        req.destroy();
      }
    });
    req.on("end", () => {
      if (!data.trim()) return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch (err) {
        reject(new Error("请求 JSON 格式不正确"));
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, statusCode, payload) {
  const text = JSON.stringify(payload);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(text),
    "Access-Control-Allow-Origin": "*"
  });
  res.end(text);
}

function loadReservations() {
  try {
    if (!fs.existsSync(DATA_FILE)) return [];
    const text = fs.readFileSync(DATA_FILE, "utf8");
    const parsed = JSON.parse(text);
    return Array.isArray(parsed) ? parsed : [];
  } catch (err) {
    console.error("Failed to load reservations:", err.message);
    return [];
  }
}

function normalizeReservations(items) {
  return items.map((item) => ({
    ...item,
    running: false,
    preloginAt: Number(item.preloginAt || 0),
    preloginStatus: item.preloginStatus || (item.token ? "ok" : "waiting")
  }));
}

function saveReservations() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
  const persisted = reservations.map((item) => ({
    ...item,
    running: false
  }));
  fs.writeFileSync(DATA_FILE, JSON.stringify(persisted, null, 2));
}

function clean(value) {
  if (value == null) return "";
  return String(value).trim();
}
