# backend

steaL 的「促销活动日历」后端：定期抓各商店官方公布的全站促销日期，用 DeepSeek 抄出日期，校验后存成 JSON，
再由一个只读接口 `GET /v1/sale-events` 合并返回。目前只在本地运行，云厂商还没定；代码尽量不依赖任何一家云。

## 运行

需要 [uv](https://docs.astral.sh/uv/) 和 Python 3.12。命令都在 `backend/` 里执行。

```sh
cp .env.example .env                                   # 填 DEEPSEEK_API_KEY
uv run python scripts/refresh_local.py --text-only     # 只看预处理后的正文，不调 AI
uv run python scripts/refresh_local.py                 # 刷新全部商店，写进 .data/sale-events/
uv run python scripts/refresh_local.py --store epic    # 只刷新一个商店（steam / epic）
uv run python scripts/serve_local.py                   # http://127.0.0.1:8000/v1/sale-events
uv run pytest                                          # 测试不联网、不调 AI
```

## 目录

```
src/sale_events/        刷新逻辑（一个包）
  steam.py              Steam：抓页面、预处理、prompt、补时刻
  epic.py               Epic：找季度指南、预处理、prompt、补时刻
  extraction.py         通用的 DeepSeek 调用（prompt 和输出结构由商店模块传入）
  validation.py         校验规则
  storage.py            存储接口 + 本地文件实现
  refresh.py            每个商店一次刷新的编排
  models.py / normalize.py   数据模型、时区换算与 id
functions/sale_events_api/func.py   读接口：只用标准库，与云无关
scripts/                本地入口（不打包上云）
tests/                  pytest；fixtures/ 是存下来的真实页面
```

## 一次刷新做什么

1. 抓页面 → 2. 预处理成纯文本 → 3. DeepSeek 抄出名字和日期 → 4. 代码补上时刻、换成 UTC、生成 id →
5. 校验 → 6. 整份替换这个商店的文件。

AI 只负责抄日期；时区、id、来源网址都由代码处理。任何一步校验不通过，这次结果整份作废，旧数据保留，
AI 的输出存进 `.data/sale-events/rejected/`，方便排查。

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
- 时刻都是 UTC，`end` 是结束的那一刻（不含）。
- 还没有任何数据或读不到存储时返回 503，数据损坏返回 500，错误体是 RFC 9457 `application/problem+json`。
- 接口还在原型阶段，字段可能会变，等稳定后再写 OpenAPI 文件。

## 已知限制

- **Epic 指南的标题换了格式就认不出来**（比如改成 `Q1 2027 Promotional Guide`）：新帖子会被过滤掉，代码继续用旧的两篇。
  旧指南里还有没结束的活动时，校验照常通过，新季度的活动就悄悄缺了；等旧活动全部结束才会报「no upcoming events left」。
  不会写坏数据，但会有一段时间缺数据。可以补的检查：选中的指南里必须有当前或下一季度的那篇，否则直接报错。
- **刷新失败还没有通知**：失败只记日志、存 rejected，要上云时再接告警。
- **Epic 只能看到 1 到 1.5 个月后**，Steam 大约能看到半年后。
- **校验只核对名字，不核对日期**：名字必须原样出现在正文里，防止 AI 编造活动；日期抄错但看起来合理（结束晚于开始、不超过 31 天）时拦不住。
  Epic 指南写了天数（`| 14 Days`），以后可以拿来核对。
- **Epic 的时刻是推断的**，见上面「时刻」。
