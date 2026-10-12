# RelayHub

一个用于集中管理第三方 API 中转站（new-api / one-api 系）与官方厂商配置的 Android 应用。把散落在各处的站点地址、API Key、账号备注收进一个列表，随时查余额、测连通性、一键复制。

## 功能

- **站点管理**：增删改查站点名称、地址、API Key、备注、标签，支持置顶（常用星标，卡片高亮 + 描边 + 抬升）
- **搜索与排序**：按名称 / 地址 / 备注 / 标签 / Key 搜索；按更新时间、名称、添加时间、状态排序
- **批量操作**：多选（全选 / 按条件选）后批量删除、导出、测活、查余额
- **测活**：区分超时、解析失败、证书错误等失败原因
- **余额查询**：
  - 官方厂商免登录查余额：DeepSeek、Moonshot、SiliconFlow、OpenRouter 等（按域名自动识别）
  - 中转站走网页登录令牌（Cookie / token），支持 new-api 系站点会话续期
  - 智谱控制台余额
- **内置令牌获取**：应用内 WebView 打开站点登录页，正常登录（含人工过验证码）后自动抓取 localStorage / Cookie 中的令牌并填入
- **复制**：Key / 地址 / 完整配置；敏感内容复制后 60 秒自动清空剪贴板
- **导入导出**：JSON 导入导出、备份到外部私有目录
- **界面**：Material You（MD3）动态取色，深浅主题、自定义强调色与配色风格、纯黑模式、密码打码开关
- **自定义面板**：设置 / 编辑 / 详情均为自绘浮层，支持下拉关闭与预测性返回

## 构建

```bash
export JAVA_HOME=<JDK 21 路径>
export ANDROID_HOME=<Android SDK 路径>
./gradlew :app:assembleRelease
```

- compileSdk 37 / minSdk 34 / targetSdk 36
- AGP 9.4.1 + Kotlin 2.4.20 + KSP + Compose BOM + Room + DataStore
- 签名信息从根目录 `signing.properties` 读取（该文件不入库）；文件不存在时产出未签名包，仍可正常编译

`signing.properties` 格式：

```properties
storeFile=keystore/relayhub.jks
storePassword=******
keyAlias=relayhub
keyPassword=******
```

## 说明

- 账号密码与令牌仅保存在本机 Room 数据库中，未做加密，请自行评估风险
- 本项目为个人自用工具，站点接口格式差异较大，余额查询对非官方站点的适配可能随时失效
