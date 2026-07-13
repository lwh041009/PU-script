#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-pu-reservation-server}"
INSTALL_DIR="${INSTALL_DIR:-$(pwd -P)}"
PORT="${PORT:-8787}"
SERVER_TOKEN="${SERVER_TOKEN:-879487}"
USE_FORK_WORKERS="${USE_FORK_WORKERS:-1}"
WORKER_COUNT="${WORKER_COUNT:-4}"
JOIN_CONCURRENCY="${JOIN_CONCURRENCY:-120}"
DISPATCH_INTERVAL_MS="${DISPATCH_INTERVAL_MS:-50}"
PRELOGIN_BEFORE_MS="${PRELOGIN_BEFORE_MS:-120000}"
RUN_USER="${RUN_USER:-root}"
ENV_FILE="/etc/${SERVICE_NAME}.env"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"

if [ "$(id -u)" -ne 0 ]; then
  echo "请用 root 运行：sudo bash install_service.sh"
  exit 1
fi

if [ ! -f "server.js" ] || [ ! -f "package.json" ]; then
  echo "请在 server 目录里运行这个脚本，当前目录缺少 server.js 或 package.json"
  exit 1
fi

install_node() {
  echo "正在安装 Node.js 20，请稍候..."

  if command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y ca-certificates curl
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
    apt-get install -y nodejs
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y ca-certificates curl
    curl -fsSL https://rpm.nodesource.com/setup_20.x | bash -
    dnf install -y nodejs
  elif command -v yum >/dev/null 2>&1; then
    yum install -y ca-certificates curl
    curl -fsSL https://rpm.nodesource.com/setup_20.x | bash -
    yum install -y nodejs
  elif command -v apk >/dev/null 2>&1; then
    apk add --no-cache nodejs npm
  else
    echo "无法识别当前 Linux 发行版，请手动安装 Node.js 18 或以上版本。"
    exit 1
  fi
}

if command -v node >/dev/null 2>&1; then
  NODE_MAJOR="$(node -p "Number(process.versions.node.split('.')[0])")"
  if [ "$NODE_MAJOR" -lt 18 ]; then
    echo "当前 Node.js 版本过低：$(node -v)，将自动升级。"
    install_node
  fi
else
  install_node
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Node.js 安装失败，请检查服务器网络和软件源。"
  exit 1
fi

NODE_MAJOR="$(node -p "Number(process.versions.node.split('.')[0])")"
if [ "$NODE_MAJOR" -lt 18 ]; then
  echo "Node.js 安装后版本仍低于 18：$(node -v)"
  exit 1
fi

if [ -z "$SERVER_TOKEN" ]; then
  echo "SERVER_TOKEN 不能为空。"
  exit 1
fi

CURRENT_DIR="$(pwd -P)"
if [ "$INSTALL_DIR" != "$CURRENT_DIR" ]; then
  mkdir -p "$INSTALL_DIR"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete --exclude data "$CURRENT_DIR"/ "$INSTALL_DIR"/
  else
    cp -a "$CURRENT_DIR"/. "$INSTALL_DIR"/
  fi
fi

cat > "$ENV_FILE" <<EOF
PORT=$PORT
HOST=0.0.0.0
SERVER_TOKEN=$SERVER_TOKEN
USE_FORK_WORKERS=$USE_FORK_WORKERS
WORKER_COUNT=$WORKER_COUNT
JOIN_CONCURRENCY=$JOIN_CONCURRENCY
DISPATCH_INTERVAL_MS=$DISPATCH_INTERVAL_MS
PRELOGIN_BEFORE_MS=$PRELOGIN_BEFORE_MS
EOF
chmod 600 "$ENV_FILE"

cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=PU Reservation Server
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$RUN_USER
WorkingDirectory=$INSTALL_DIR
EnvironmentFile=$ENV_FILE
ExecStart=$(command -v node) $INSTALL_DIR/server.js
Restart=always
RestartSec=3
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl restart "$SERVICE_NAME"

if command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
  firewall-cmd --permanent --add-port="${PORT}/tcp" >/dev/null
  firewall-cmd --reload >/dev/null
  echo "已通过 firewalld 开放端口 $PORT/tcp"
elif command -v ufw >/dev/null 2>&1 && ufw status | grep -qi "Status: active"; then
  ufw allow "${PORT}/tcp" >/dev/null
  echo "已通过 ufw 开放端口 $PORT/tcp"
else
  echo "未检测到启用的 firewalld/ufw。请在云服务器安全组里手动开放 $PORT/tcp。"
fi

echo
echo "安装完成。"
echo "服务名：$SERVICE_NAME"
echo "安装目录：$INSTALL_DIR"
echo "监听端口：$PORT"
echo
echo "查看状态：systemctl status $SERVICE_NAME --no-pager"
echo "查看日志：journalctl -u $SERVICE_NAME -f"
echo "健康检查：curl http://127.0.0.1:$PORT/health"
