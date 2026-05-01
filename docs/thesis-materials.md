# SmartCampusNavigation 第二版论文材料

本文件提供可直接整理进论文的结构图、流程图、ER 图和测试用例表。图中名称以第二版实际实现为准：AI 地图交互、NUIST POI 数据、高德地图兜底、反馈修正闭环。

## 系统功能结构图

```mermaid
flowchart TB
  A["SmartCampusNavigation 第二版"] --> U["用户端"]
  A --> M["管理员端"]
  A --> S["后端服务"]
  A --> D["数据与外部服务"]

  U --> U1["登录 / 注册"]
  U --> U2["AI 地图工作台"]
  U --> U3["地点经验发现"]
  U --> U4["个人反馈记录"]

  U2 --> U21["自然语言提问"]
  U2 --> U22["POI 高亮与详情"]
  U2 --> U23["普通路线兜底"]
  U2 --> U24["POI 绑定反馈"]

  M --> M1["数据看板"]
  M --> M2["POI 管理"]
  M --> M3["反馈审核"]
  M --> M4["发现内容管理"]
  M --> M5["AI 记录查看"]

  S --> S1["认证与角色控制"]
  S --> S2["AI Chat API"]
  S --> S3["POI API"]
  S --> S4["Feedback API"]
  S --> S5["Admin Dashboard API"]

  D --> D1["MySQL 自建 POI 数据"]
  D --> D2["Mock AI 兜底"]
  D --> D3["DeepSeek 结构化意图识别"]
  D --> D4["高德地图 JS API 2.0"]
```

## 业务流程图

```mermaid
flowchart LR
  Start["用户登录"] --> Ask["输入地图问题"]
  Ask --> AI["后端识别意图并返回结构化 JSON"]
  AI --> Map["前端执行地图动作"]
  Map --> Detail["查看 POI 详情"]
  Detail --> Feedback["提交 POI 绑定反馈"]
  Feedback --> Review["管理员审核反馈"]
  Review --> Decision{"审核通过?"}
  Decision -->|是| Sync["同步 POI 状态或备注"]
  Decision -->|否| Reject["记录驳回原因"]
  Sync --> Visible["用户端可见 POI 变化"]
  Reject --> End["闭环结束"]
  Visible --> End
```

## AI 地图动作流程图

```mermaid
flowchart TB
  Q["POST /api/ai/chat"] --> Provider{"AI_PROVIDER=deepseek 且已配置 Key?"}
  Provider -->|否| Mock["MockAiService 生成稳定响应"]
  Provider -->|是| Rule["先生成 Mock 规则响应作为兜底"]
  Rule --> DeepSeek["DeepSeek 返回 JSON 文本"]
  DeepSeek --> Parse{"JSON 可解析且字段有效?"}
  Parse -->|否| Mock
  Parse --> Check{"intent 与规则场景兼容且 mapActions 非空?"}
  Check -->|否| Mock
  Check -->|是| Real["使用 DeepSeek 结构化响应"]
  Mock --> Save["保存 AI 交互日志"]
  Real --> Save
  Save --> FE["前端执行 mapActions"]
  FE --> Highlight["highlight_pois: 聚焦并高亮 POI"]
  FE --> Detail["open_poi_detail: 打开详情"]
  FE --> Route["draw_route: 起终点 + 高德步行路线或直线兜底"]
```

## ER 图

```mermaid
erDiagram
  APP_USER ||--o{ FEEDBACK : submits
  APP_USER ||--o{ AI_MESSAGE : asks
  POI ||--o{ FEEDBACK : receives
  POI ||--o{ DISCOVER_POST : binds

  APP_USER {
    bigint id PK
    varchar username
    varchar password_hash
    varchar display_name
    varchar role
    varchar status
    datetime created_at
  }

  POI {
    bigint id PK
    varchar name
    varchar category
    decimal longitude
    decimal latitude
    varchar location_text
    varchar open_status
    varchar tags
    tinyint sheltered
    varchar remark
    tinyint enabled
    int map_rank
    varchar source_url
    datetime updated_at
  }

  FEEDBACK {
    bigint id PK
    bigint user_id FK
    bigint poi_id FK
    varchar type
    varchar content
    varchar status
    varchar review_note
    datetime created_at
    datetime reviewed_at
  }

  DISCOVER_POST {
    bigint id PK
    varchar title
    varchar summary
    bigint poi_id FK
    varchar category
    varchar cover_url
    varchar status
    datetime created_at
  }

  AI_MESSAGE {
    bigint id PK
    bigint user_id FK
    varchar question
    varchar intent
    varchar reply
    text tool_calls_json
    text map_actions_json
    datetime created_at
  }
```

## 测试用例表

| 编号 | 模块 | 测试场景 | 输入或操作 | 预期结果 |
| --- | --- | --- | --- | --- |
| TC-01 | AI 地图 | 中文条件推荐 | `找一个安静有插座的自习点` | 返回 `recommend_place`，地图高亮自习类 POI |
| TC-02 | AI 地图 | 英文条件推荐 | `Find a quiet study place with outlets` | 返回英文回复，结构化地图动作正常执行 |
| TC-03 | AI 地图 | 精确地点查找 | `Find Campus Print Shop` | 返回 `find_poi`，结果聚焦打印相关 POI |
| TC-04 | AI 地图 | 路线帮助 | `Go from Xiyuan Dormitory Area to NUIST Library Study Area` | 返回 `draw_route`，显示起点、终点和普通路线兜底 |
| TC-05 | AI 容错 | 未配置 DeepSeek Key | 不设置 `DEEPSEEK_API_KEY` | 自动使用 Mock AI，页面不报错 |
| TC-06 | AI 容错 | DeepSeek 非 JSON | 模拟返回非 JSON 文本 | 回退 Mock AI |
| TC-07 | AI 容错 | DeepSeek 缺少 `mapActions` | 模拟响应只有 `poiIds` | 回退 Mock AI |
| TC-08 | AI 容错 | DeepSeek 返回非法 POI ID | 模拟 `poiIds=[999]` | 丢弃非法动作并回退 Mock AI |
| TC-09 | 地图容错 | 未配置高德 Key | 清空 `VITE_AMAP_KEY` | 显示模拟地图和兜底提示 |
| TC-10 | 地图容错 | 高德加载失败 | 阻断高德脚本加载 | 页面不崩溃，继续显示模拟地图 |
| TC-11 | 反馈闭环 | 提交 POI 反馈 | 从 POI 详情填写反馈 | 反馈绑定当前 POI 并进入待审核 |
| TC-12 | 反馈闭环 | 游离反馈提交 | 请求体缺少 `poiId` | 后端拒绝提交 |
| TC-13 | 反馈闭环 | 反馈绑定不存在 POI | 请求体 `poiId=404` | 后端拒绝提交，不写入反馈表 |
| TC-14 | 管理端 | 审核通过并同步备注 | 管理员通过反馈并填写 POI 备注 | 用户端 POI 详情显示新备注 |
| TC-15 | UI | 1024/1440 宽度检查 | 切换中英文查看核心页面 | 按钮、表格、快捷问题文本不溢出 |

## 数据来源说明

POI 数据采用自建表维护，高德地图仅作为底图、Marker、坐标预览和普通路线兜底。第二版种子数据包含 100 条启用 POI，其中 `map_rank` 为 1-20 的地点作为默认地图热门层。地点覆盖参考以下官方页面，具体坐标和演示标签为系统演示数据：

- [NUIST 校园地图服务](https://nic.nuist.edu.cn/2473/listm.htm)
- [Campus and Facilities](https://en.nuist.edu.cn/4204/list.psp)
- [Library](https://en.nuist.edu.cn/4061/list.psp)
- [Sports](https://en.nuist.edu.cn/4062/list.psp)
- [Medical Service](https://en.nuist.edu.cn/_s104/4217/list.psp)
- [Services](https://en.nuist.edu.cn/4071/listm.psp)
