# PU 预约服务器

这个服务端负责 24 小时保存预约任务，到报名时间自动向 PU 接口提交报名。手机 App 只需要填写服务器地址，预约时选择“服务器预约”，之后在“我的活动”里查看执行结果。

## 启动

```bash
cd server
npm start
```

默认监听 `0.0.0.0:8787`。可以用环境变量调整：

```bash
PORT=8787 SERVER_TOKEN=你的密钥 npm start
```

Windows PowerShell：

```powershell
$env:PORT="8787"
$env:SERVER_TOKEN="你的密钥"
npm start
```

App 设置里的服务器地址填写 `http://服务器IP:8787`，密钥填写和 `SERVER_TOKEN` 一样。默认密钥是 `879487`，也可以通过环境变量 `SERVER_TOKEN` 改掉。

## 一键安装为系统服务

把 `server` 文件夹上传到服务器后，进入该目录执行：

```bash
sudo bash install_service.sh
```

脚本会：

- 检查 Node.js 版本
- 创建 `/etc/pu-reservation-server.env`
- 创建 systemd 服务 `/etc/systemd/system/pu-reservation-server.service`
- 设置开机自启
- 启动服务
- 尝试用 firewalld/ufw 开放 `8787/tcp`

如果你想把程序固定安装到 `/opt/pu-server`：

```bash
sudo INSTALL_DIR=/opt/pu-server bash install_service.sh
```

如果你想放到 `/root/pu-server`：

```bash
sudo INSTALL_DIR=/root/pu-server bash install_service.sh
```

安装后常用命令：

```bash
systemctl status pu-reservation-server --no-pager
journalctl -u pu-reservation-server -f
systemctl restart pu-reservation-server
curl http://127.0.0.1:8787/health
```

## 并发执行参数

默认已经使用批量调度：服务端每 `50ms` 扫描一次到点任务，同一时间到点的任务会进入同一批并发执行，不再给每个预约单独开定时器。

常用环境变量：

```bash
DISPATCH_INTERVAL_MS=20      # 调度扫描间隔，越小越敏感
JOIN_CONCURRENCY=100         # 报名请求并发上限
PRELOGIN_BEFORE_MS=120000    # 抢前多久提前登录，默认 2 分钟
LOGIN_CONCURRENCY=8          # 提前登录并发上限
```

Linux 服务器可以开启预启动 fork worker 池：

```bash
USE_FORK_WORKERS=1 WORKER_COUNT=4 JOIN_CONCURRENCY=120 npm start
```

说明：

- fork worker 是提前启动的子进程池，不是在抢的时候临时 fork。
- 它能减少账号很多时单进程事件循环和加密计算造成的抖动。
- 它不能保证“同一纳秒”发送请求；实际仍取决于网络、PU 接口响应和对方限流。
- Windows 也能跑 Node 子进程，但这个模式主要建议 Linux/VPS 使用。

## 24 小时运行建议

在服务器/VPS 上建议用 `pm2` 或系统服务常驻：

```bash
npm install -g pm2
USE_FORK_WORKERS=1 WORKER_COUNT=4 JOIN_CONCURRENCY=120 pm2 start server.js --name pu-reservation-server
pm2 save
pm2 startup
```

任务数据保存在 `server/data/reservations.json`。服务器重启后会自动加载未执行任务并继续调度。
