# PU 脚本

一个面向 PU 口袋校园的原生 Android 活动管理与预约报名脚本，使用 Java + Android 原生 View/XML 开发，不使用 WebView 套壳。

它把 PU 口袋校园里的活动浏览、报名、我的活动、分数查询和预约执行整合到一个手机 App 中，并提供可选的 Linux 服务器预约端。

## 功能

- 学校、账号、密码登录，支持本地保存多个账号
- 活动列表、活动详情、活动状态和类型筛选
- 活动报名、取消报名、我的活动
- 活动学分、申请学分和诚信分查询
- 本地预约报名，到点自动执行并推送结果
- 可选的服务器预约模式，由 Linux 服务器 24 小时保存和执行任务
- 北京时间同步、预约状态记录、失败原因和重试记录
- 每小时检查新版本，Gitee 为主要更新源，GitHub/JSDelivr 为备用源
- 字体大小设置和多种界面显示优化

## 项目结构

```text
app/                 Android App 源码
server/              可选的 Node.js 预约服务器
update/latest.json   版本更新清单
tools/               海报等辅助工具
build_apk.bat        Windows APK 构建脚本
```

## App 构建

需要 Android SDK、JDK 和 Gradle 环境。在 Windows 下可以直接运行：

```powershell
.\build_apk.bat
```

构建产物位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

根目录的 `PU-script.apk` 是最近一次构建的 APK。正式分发前建议使用正式签名密钥构建 Release 版本。

## 普通使用

普通活动浏览、报名、我的活动和本地预约不需要部署本项目的服务器。App 仍然需要联网访问 PU 官方接口。

安装 APK 后登录学校账号，在活动详情页选择报名或本地预约即可。

## 服务器预约

服务器端是可选组件，适合希望手机关闭后仍由 VPS 持续保存和执行预约任务的情况。

### 环境要求

- Ubuntu/Debian、CentOS/RHEL 或 Alpine Linux
- 公网可访问的 VPS 或云服务器
- 服务器可以访问互联网

### 部署

把 `server` 文件夹上传到服务器，进入该目录执行：

```bash
chmod +x install_service.sh
SERVER_TOKEN='请替换成随机长密钥' bash install_service.sh
```

安装脚本会自动完成：

- 修复 Ubuntu/Debian 中断的软件包配置
- 安装或升级 Node.js 20
- 创建 systemd 服务
- 设置开机启动并启动服务
- 尝试配置系统防火墙

默认服务名为 `pu-reservation-server`，监听端口为 `8787`。云服务器还需要在安全组中放行 TCP `8787`，或通过 Nginx/Caddy 配置 HTTPS 反向代理。

常用维护命令：

```bash
systemctl status pu-reservation-server --no-pager
systemctl restart pu-reservation-server
journalctl -u pu-reservation-server -f
curl -i -H "X-Server-Token: 你的密钥" http://127.0.0.1:8787/health
```

安装完成后，在 App 的“设置”里填写：

```text
服务器地址：http://服务器公网 IP:8787
服务器密钥：SERVER_TOKEN 的值
```

预约时选择“服务器预约”。任务会保存到 `server/data/reservations.json`，服务器重启后会自动加载未完成任务。

## 安全说明

服务器预约需要保存用于重新登录 PU 的账号信息，`server/data/reservations.json` 含有敏感数据。请务必：

- 使用随机且足够长的 `SERVER_TOKEN`
- 不要使用默认密钥 `879487`
- 正式部署时使用 HTTPS，不要在公网长期使用明文 HTTP
- 限制服务器端口访问范围，并定期备份和保护 `server/data`
- 不要把账号密码、服务器密钥或正式签名密钥提交到 Git 仓库

## 更新机制

版本清单位于 `update/latest.json`。当前 APK 优先读取 Gitee 更新仓库：

<https://gitee.com/luo-wanhong/PU-script-release>

GitHub 主仓库：

<https://github.com/lwh041009/PU-script>

## 免责声明

本项目仅供学习、研究和个人使用。PU 官方接口、活动规则和网络环境可能发生变化，使用者应遵守相关平台规则和学校管理要求。
