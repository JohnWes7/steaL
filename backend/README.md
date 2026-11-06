# backend

steaL 的「促销活动日历」后端：定期抓各商店官方公布的全站促销日期，用 DeepSeek 抄出日期，校验后存成 JSON，
再由一个只读接口 `GET /v1/sale-events` 合并返回。目前只在本地运行，云厂商还没定；代码尽量不依赖任何一家云。

## 运行

需要 [uv](https://docs.astral.sh/uv/) 和 Python 3.12。命令都在 `backend/` 里执行。

```sh
cp .env.example .env                                   # 填 DEEPSEEK_API_KEY
uv run python scripts/refresh_local.py --text-only     # 只看预处理后的正文，不调 AI
uv run python scripts/refresh_local.py                 # 刷新全部商店，写进 .data/sale-events/
uv run python scripts/refresh_local.py --store epic    # 只刷新一个商店（steam / epic / itchio / gog）
uv run python scripts/serve_local.py                   # http://127.0.0.1:8000/v1/sale-events
uv run pytest                                          # 测试不联网、不调 AI、不碰真 AWS（S3 用 moto 模拟）
scripts/build_refresh_lambda.sh                        # 打包 refresh Lambda（Linux arm64）到 build/refresh/
```

## 目录

```
src/sale_events/        刷新逻辑（一个包）
  steam.py              Steam：抓页面、预处理、prompt、补时刻
  epic.py               Epic：找季度指南、预处理、prompt、补时刻
  itchio.py             itch.io：读官方博客 RSS、预处理、prompt、补时刻
  agent.py              通用的找活动 agent：自己搜索、打开网页、提交带引文的活动，代码逐条核对
  gog.py                GOG：只有一份 agent 配置
  extraction.py         通用的 DeepSeek 调用（prompt 和输出结构由商店模块传入）
  html_text.py          通用的 HTML 转纯文本（Epic、itch.io 共用）
  validation.py         校验规则
  storage.py            存储接口 + 本地文件实现
  refresh.py            每个商店一次刷新的编排
  runner.py             按商店名接好抓取 / AI / 存储，本地脚本与 Lambda 共用
  models.py / normalize.py   数据模型、时区换算与 id
functions/sale_events_api/api_core.py             读接口的核心逻辑（不是入口）：只用标准库，与云无关
functions/sale_events_api/api_handler.py          AWS 专用：Lambda 入口，从 S3 读文件交给 api_core.py
functions/sale_events_refresh/refresh_handler.py  AWS 专用：Lambda 入口，每次刷新事件里的一个商店，没写成就抛错
functions/sale_events_refresh/s3_store.py         AWS 专用：EventStore 的 S3 实现
scripts/                本地入口（不打包上云）
tests/                  pytest；fixtures/ 是存下来的真实页面
```

## 一次刷新做什么

1. 抓页面 → 2. 预处理成纯文本 → 3. DeepSeek 抄出名字和日期 → 4. 代码补上时刻、换成 UTC、生成 id →
5. 校验 → 6. 整份替换这个商店的文件。

AI 只负责抄日期；时区、id、来源网址都由代码处理。任何一步校验不通过，这次结果整份作废，旧数据保留，
AI 的输出存进 `.data/sale-events/rejected/`，方便排查。

Steam 和 Epic 是完整的日程表，一条都没有、或比上一份少一半以上，都算出错；itch.io 只在促销期间有公告，空列表是正常的。
GOG 走 agent，流程不同，见下面「GOG（agent）」。

## 数据来源

### Steam

固定抓 Steamworks 的 [Upcoming Steam Events](https://partner.steamgames.com/doc/marketing/upcoming_events)，
这是 Valve 写给开发者的完整日程，大约能看到半年后。开始和结束都是**太平洋时间上午 10 点**（各活动子页写明了）。

### Epic

Epic 员工每个季度会在开发者论坛发一篇「`YYYY Qn Promotional Guide`」，列出这个季度全部全站促销的日期，
一般提前 1 到 1.5 个月发，例如 [2026 Q4 Promotional Guide](https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185)。

**帖子网址每季度都不一样，所以代码里不写死网址**，每次刷新都重新找（`epic.fetch_guides`）：

1. 请求论坛（Discourse）的搜索接口 `search.json?q="Promotional Guide" order:latest`；
2. 只留标题以 `YYYY Qn Promotional Guide` 开头的帖子，从标题读出年份和季度；
3. 按年份 + 季度排序，取最新两篇（当前季度 + 下一季度）；
4. 用帖子 id 请求 `/t/{id}.json` 拿正文，**只认 Epic 员工发的帖子**（论坛谁都能发帖，防假指南）；
5. 来源网址用这次拿到的 slug 和 id 拼成 `/t/{slug}/{id}`。

新一季的指南发出来后，下一次刷新会自动选中它，不用改代码。

时刻：指南只写日期，时刻按**太平洋时间早上 8 点**（美东 11 点）处理。这是推断的：
2025 年 Holiday Sale 指南写 12/11 – 1/8，Epic 日本官方新闻稿写日本时间 12/12 凌晨 1 点 – 1/9 凌晨 1 点，换算正好是两天的 8:00 PST。

Epic 的活动不分类，`kind` 都是 `sale`。

### itch.io

读 itch.io 官方博客的 RSS [`itch.io/blog.rss`](https://itch.io/blog.rss)，里面只有 itch.io 自己的帖子，
全站促销（Summer / Halloween / Winter / Black Friday 等）都在这里公告，一般在开始当天或提前几天发。没有提前公布的日程。

1. 读 RSS，取最近 45 天发的帖子（不按标题筛，交给 AI 判断是不是促销）；
2. 下载每篇帖子页，只取正文 `section.post_body`（不含评论）；
3. 每篇开头加上标题和发布日期，交给 DeepSeek，**只收 itch.io 官方办的全站促销**，Creator Day、合集包、个人开发者的促销都不收；
4. 同一场促销被两篇帖子提到时（预告、开始、延长），以新帖子为准。

时刻：帖子写了太平洋时间（如 `10:00 AM PT`）就照抄；没写就用 10:00 PT。文章说「现在已开始」但没写开始日期时，用发布日期。
`kind` 都是 `sale`。

### GOG（agent）

GOG 没有提前公布的日程，官方新闻只在促销开始时发，也没有可用的新闻列表（`gog.com/news` 会跳到首页），
所以交给通用的 agent（`agent.py`）。`gog.py` 里只有一份配置：要找什么、只认哪些域名（`gog.com`）、网页没写时刻时用什么（8:00 UTC）。

一次运行：

1. DeepSeek 用 tool calling 自己决定搜什么（DeepSeek Search）、打开哪些网页（DeepSeek Fetch），最多 8 轮、6 个网页；
   最后一轮只给它 `submit_events` 一个工具，逼它交结果；
2. 每条活动要带：名字、日期、可选的时刻和时区（照抄网页上的 `CET` / `UTC` / `PT` 等，代码换算）、来源网址、一句原文引文；
3. 代码逐条核对：来源是白名单域名、**这次真的打开过**、引文原样出现在那一页、结束日期在引文里；再过一遍通用校验。
   **不通过的只丢这一条**，其他照常写入，丢掉的原因存进 `rejected/`；
4. **按条更新（upsert）**：这次没搜到不代表活动没了，所以旧数据里还没结束的保留。
   **同一商店、结束日期（UTC）相同就算同一场**，用新的覆盖旧的：AI 每次给的名字可能不同（id 跟着变），而结束日期核对过，最稳定。

以后别的网站也没有固定来源时，写一份 `AgentSource` 配置就能复用，不用改 agent 本身。
`kind` 都是 `sale`。

## 接口

`GET /v1/sale-events` 合并所有商店的文件，去掉已结束的，按开始时间排序：

```json
{
  "refreshedAt": "2026-11-01T10:36:27Z",
  "events": [
    {
      "id": "epic-2026-12-10-holiday-sale",
      "store": "epic",
      "kind": "sale",
      "name": "Holiday Sale",
      "start": "2026-12-10T16:00:00Z",
      "end": "2027-01-07T16:00:00Z",
      "sourceURL": "https://forums.unrealengine.com/t/2026-q4-promotional-guide-egs-promotions/2746185"
    }
  ]
}
```

- `refreshedAt` 取各商店里最早的那次成功刷新，表示最旧的那部分数据有多旧。
- 时刻都是 UTC、整秒（不带小数秒，所有时刻一个格式），`end` 是结束的那一刻（不含）。
- 还没有任何数据或读不到存储时返回 503，数据损坏返回 500，错误体是 RFC 9457 `application/problem+json`。
- 线上还有两种网关生成的错误（响应体是 `{"message": "..."}`，不是 problem+json）：超过限流（每秒 10 次、突发 20）返回 429；Lambda 并发打满返回 503。两种都应稍后重试。
- 完整契约见仓库根的 [`api/openapi.yaml`](../api/openapi.yaml)（OpenAPI 3.1）：改了接口要一起改它。

## 部署（AWS，进行中）

部署到 AWS 香港（`ap-east-1`）：API Gateway HTTP API → `sale_events_api` Lambda 读 S3；EventBridge Scheduler 每个商店一个定时器
（Steam / Epic 每周、itch.io / GOG 每天）→ `sale_events_refresh` Lambda 写 S3；刷新失败经 SNS 发邮件。Terraform 在 `infra/`：

```sh
aws login --profile steal                     # 会话最长 12 小时
cd infra
cp terraform.tfvars.example terraform.tfvars  # 填云上专用的 DeepSeek key（不提交）
terraform init
terraform plan                                # 先看会建什么
terraform apply
```

已部署（2026-11-04）：`https://1gfwpsgqnc.execute-api.ap-east-1.amazonaws.com/v1/sale-events`。

手动刷新一个商店（`store` 可选 `steam` / `epic` / `itchio` / `gog`）：

```sh
# 同步调用：等函数跑完，返回值写进 out.json（GOG 要半分钟左右）
aws lambda invoke --profile steal --region ap-east-1 --function-name steal-sale-events-refresh \
  --cli-binary-format raw-in-base64-out --payload '{"store":"steam"}' --cli-read-timeout 700 out.json
cat out.json

# 看日志（实时跟踪；不加 --follow 就看最近的）
aws logs tail /aws/lambda/steal-sale-events-refresh --profile steal --region ap-east-1 --since 30m --follow

# 看接口合并后的结果
curl -s https://1gfwpsgqnc.execute-api.ap-east-1.amazonaws.com/v1/sale-events
```

加 `--invocation-type Event` 就是异步调用，和定时器的调用方式一样：命令马上返回 202，结果只能看日志。

定时器（2026-11-06 起打开，`schedules_enabled = true`）：Steam 周一 00:00、Epic 周一 00:10、itch.io 每天 01:00、GOG 每天 01:10（UTC）。
EventBridge Scheduler 没有「立即运行」，手动测试就用上面的 `aws lambda invoke`。要停掉就把 `terraform.tfvars` 里的 `schedules_enabled` 改成 `false` 再 `terraform apply`。
刷新失败目前只记在 CloudWatch Logs 里（还没有邮件通知）。

为了不绑定某一家云：`sale_events` 包和 `api_core.py` 不依赖任何云 SDK，AWS 专用的只有上面三个文件。换云时只换它们和 Terraform。

## 已知限制

- **Epic 指南的标题换了格式就认不出来**（比如改成 `Q1 2027 Promotional Guide`）：新帖子会被过滤掉，代码继续用旧的两篇。
  旧指南里还有没结束的活动时，校验照常通过，新季度的活动就悄悄缺了；等旧活动全部结束才会报「no upcoming events left」。
  不会写坏数据，但会有一段时间缺数据。可以补的检查：选中的指南里必须有当前或下一季度的那篇，否则直接报错。
- **刷新失败还没有通知**：失败只记日志、存 rejected，要上云时再接告警。
- **Epic 只能看到 1 到 1.5 个月后**，Steam 大约能看到半年后。
- **校验只核对名字，不核对日期**：名字必须原样出现在正文里，防止 AI 编造活动；日期抄错但看起来合理（结束晚于开始、不超过 31 天）时拦不住。
  Epic 指南写了天数（`| 14 Days`），以后可以拿来核对。
- **Epic 的时刻是推断的**，见上面「时刻」。
- **GOG 的开始时间不准**：新闻常只写「已开始」和结束时刻，开始日期用搜索结果的日期，可能差一天，时刻用默认 8:00 UTC。
- **GOG 同一场活动的名字每次运行可能不同**（实测一次抄成文章标题、一次抄成「Anniversary Sale」），所以按结束日期去重；
  代价是同一商店两场全站促销恰好同一天结束时会被合并成一条（少见）。
- **GOG 每次结果不完全一样**，也比固定来源贵：每次约十几次搜索 / 打开网页加多轮对话。
- **itch.io 只能看到正在进行或几天后开始的促销**；帖子没写时刻时用 10:00 PT，往年也出现过 11am PT，可能差一小时。
  开始日期缺失时用发布日期，促销实际可能早一两天开始。
