# 自动任务助手

这是一个 Android 客户端 + Spring Boot 后台项目。客户端启动前需要输入激活码；激活码首次使用时会绑定当前设备，卸载重装后只要在同一台设备重新输入同一个激活码，仍然可以继续使用。

## 模块

- `app`：Android 客户端。
- `server`：Spring Boot + MySQL 激活码后台。

## 后台地址

本地启动后：

- 管理后台：`http://127.0.0.1:8080/admin/index.html`
- 激活接口：`http://127.0.0.1:8080/api/activation/activate`
- 验证接口：`http://127.0.0.1:8080/api/activation/verify`

默认后台账号：

- 用户名：`admin`
- 密码：`admin123`

生产环境请通过环境变量修改：

```powershell
$env:ADMIN_USERNAME="你的账号"
$env:ADMIN_PASSWORD="你的强密码"
```

## MySQL

先创建数据库：

```sql
CREATE DATABASE autotask_permission
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

默认连接配置在 `server/src/main/resources/application.yml`：

```yaml
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/autotask_permission
MYSQL_USERNAME=root
MYSQL_PASSWORD=root
```

也可以用环境变量覆盖：

```powershell
$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3306/autotask_permission?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:MYSQL_USERNAME="root"
$env:MYSQL_PASSWORD="你的密码"
```

## 启动后台

```powershell
.\gradlew.bat :server:bootRun
```

打开 `http://127.0.0.1:8080/admin/index.html` 后，可以新增、批量生成、禁用、启用、解绑、删除激活码。

## Android 激活地址

Android 默认后台地址在 `app/build.gradle.kts`：

```kotlin
buildConfigField("String", "ACTIVATION_API_BASE_URL", "\"http://10.0.2.2:8080\"")
```

- Android 模拟器访问电脑本机后台：保持 `http://10.0.2.2:8080`。
- 真机访问电脑后台：改成电脑局域网 IP，例如 `http://192.168.1.10:8080`。
- 上线服务器：建议改成 HTTPS 域名，例如 `https://api.example.com`。

## 构建

```powershell
.\gradlew.bat :server:compileJava
.\gradlew.bat :app:assembleDebug
```

当前项目使用：

- Gradle 9.5.0
- Android Gradle Plugin 9.3.0
- compileSdk 35
- Java 21 运行 Spring Boot 后台
