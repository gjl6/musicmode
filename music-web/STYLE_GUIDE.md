# Music Mode — 前端风格规范与性能优化指南

> **🚨 大模型回复规则（最高优先级）**
>
> **大模型在处理本项目的任何前端相关请求时，回复的第一句话必须是：**
>
> ```
> 啊龙，按照web指南给你回复。
> ```
>
> **如果模型回复的第一句话不是这句话，用户应重新输入"指南"二字，让模型重新阅读本文档并重新思考。**

---

## 目录

1. [组件规范](#1-组件规范)
2. [状态管理规范](#2-状态管理规范)
3. [多主题架构与样式规范](#3-多主题架构与样式规范)
4. [API 层规范](#4-api-层规范)
5. [路由与代码分割](#5-路由与代码分割)
6. [国际化规范](#6-国际化规范)
7. [性能优化](#7-性能优化)
8. [文件组织规范](#8-文件组织规范)
9. [代码风格](#9-代码风格)
10. [完整优化方案与执行计划](#10-完整优化方案与执行计划)

---

## 1. 组件规范

### 1.1 统一使用 `<script setup>`

所有 `.vue` 组件必须使用 `<script setup>` 语法，禁止使用 Options API 或 `h()` 渲染函数定义组件结构。

```vue
<!-- ✅ 正确 -->
<script setup>
import { ref } from 'vue'
const count = ref(0)
</script>

<!-- ❌ 错误 — Options API + h() render -->
<script>
export default {
  setup(props, { slots }) {
    return () => h('div', ...)
  }
}
</script>
```

> **现状**：`EditSection.vue` 使用 Options API + `h()`，需要重构为 `<script setup>` + `<template>`。

### 1.2 组件拆分粒度

- **页面组件**（`views/`）：只负责布局编排，不包含复杂业务逻辑
- **业务面板**（`components/`）：按功能域拆分，每个面板自包含状态和 API 调用
- **复用控件**：跨页面使用的 UI 片段抽为独立组件

**当前问题**：`Workbench.vue` 超过 1700 行，承载了工具条、表格、分页、播放器、14 个模态框。
**目标**：拆分为独立子组件，页面组件只做编排。

```vue
<!-- ✅ 正确：Workbench 只做编排 -->
<template>
  <div class="workbench">
    <Sidebar />
    <EditDrawer />
    <main class="content">
      <ToolBar @tool-click="handleToolClick" />
      <PathBar />
      <FileTable />
      <MiniPlayer />
    </main>
    <ToolModals :active-tool="activeTool" @done="onToolDone" />
  </div>
</template>
```

### 1.3 组件命名

| 类型 | 命名规则 | 示例 |
|------|----------|------|
| 页面组件 | `PascalCase`，以页面功能命名 | `Workbench.vue`, `PipelineDetail.vue` |
| 布局组件 | `PascalCase`，以布局功能命名 | `TopNavBar.vue`, `ToolPanelLayout.vue` |
| 业务面板 | `PascalCase`，以功能域名 + Panel 结尾 | `DedupPanel.vue`, `EnrichPanel.vue` |
| 通用控件 | `PascalCase`，以控件功能命名 | `EditSection.vue`, `ToolDialog.vue` |

### 1.4 Props 与 Emits 定义

使用 `defineProps` 和 `defineEmits`，必须声明类型和默认值。

```vue
<script setup>
// ✅ 正确 — 完整的 props 声明
const props = defineProps({
  mode: { type: String, default: 'tool' },
  targetPath: { type: String, default: '' },
  selectedFiles: { type: Array, default: () => [] },
  selectedFolders: { type: Array, default: () => [] },
})

const emit = defineEmits(['done', 'cancel'])

// ❌ 错误 — 缺少类型和默认值
const props = defineProps(['mode', 'targetPath'])
</script>
```

### 1.5 模板中的 v-model 可见性控制

工具面板/模态框必须支持 `v-model:visible` 双向绑定，父组件通过 v-model 控制显隐。

```vue
<!-- ✅ 正确 -->
<ToolDialog v-model:visible="toolDialogVisible" :tool-key="key" />

<!-- ❌ 错误 -->
<ToolDialog :visible="toolDialogVisible" @update:visible="v => toolDialogVisible = v" />
```

---

## 2. 状态管理规范

### 2.1 Store 使用 Composition API

所有 Pinia Store 必须使用 `defineStore` + Composition API（`ref`/`computed`/`function`），禁止使用 Options API。

```js
// ✅ 正确
export const useFooStore = defineStore('foo', () => {
  const bar = ref(0)
  const double = computed(() => bar.value * 2)
  function increment() { bar.value++ }
  return { bar, double, increment }
})

// ❌ 错误
export const useFooStore = defineStore('foo', {
  state: () => ({ bar: 0 }),
  getters: { double: (s) => s.bar * 2 },
  actions: { increment() { this.bar++ } },
})
```

> 现状：所有 Store 已使用 Composition API，继续保持。

### 2.2 Store 职责划分

| Store | 职责 | 不应做的事 |
|-------|------|-----------|
| `app` | 主题、语言、UI 开关状态 | 业务数据、API 调用 |
| `file` | 目录浏览、文件列表、选中状态 | 元数据编辑、播放控制 |
| `edit` | 编辑草稿、撤销/重做、字段修改 | UI 显隐控制、API 调用 |
| `player` | Audio 单例、播放状态、音量 | UI 布局、文件浏览 |

### 2.3 避免组件内直接操作 Store 复杂逻辑

组件只调用 Store 暴露的方法，不在组件内实现 Store 职责范围内的复杂逻辑。

```js
// ✅ 正确：Store 封装逻辑
// store/edit.js
function startEdit(file, meta) { /* 复杂的合并逻辑 */ }

// 组件中
editStore.startEdit(file, meta)

// ❌ 错误：组件内实现 Store 逻辑
const merged = emptyMeta()
Object.assign(merged.song, meta.song)
// ...50 行合并代码
editStore.currentMeta = merged
```

### 2.4 大型对象使用 shallowRef

对于大型只读数据（如文件列表、列定义），使用 `shallowRef` 减少响应式开销。

```js
// ✅ 正确：文件列表不需要深度响应式（每项是不可变替换）
const files = shallowRef([])

// 更新时整体替换
files.value = newFiles

// ❌ 错误：深度响应式追踪每个文件对象的每个属性
const files = ref([])
```

> 现状：`file.js` 中 `files`、`pageFiles`、`folders` 使用 `ref`，应评估改为 `shallowRef`。

---

## 3. 多主题架构与样式规范

### 3.1 核心原则：Theme-Agnostic Components

**组件只管布局/间距/动画，视觉效果 100% 交给 Token 变量。**

```
┌──────────────────────────────────────────────────┐
│              组件 .vue 文件                        │
│  只用 var(--token-name)，绝对不引用具体颜色值         │
│  禁止出现 #1a1a1a / rgba(0,0,0,0.5) / 3px solid   │
│  只管布局/间距/动画，视觉全部交给 Token              │
└──────────────┬───────────────────────────────────┘
               │ 引用
┌──────────────▼───────────────────────────────────┐
│          tokens.css（Token "接口" 定义）            │
│  所有主题共享的"变量名"——每个变量在所有主题中必须有值   │
│                                                   │
│  --bg-page, --bg-surface, --bg-sidebar            │
│  --text-primary, --text-secondary                 │
│  --border-default, --border-strong                │
│  --shadow-card, --shadow-button                   │
│  --gradient-page, --gradient-card                 │
│  --radius-sm/med/lg, --border-width               │
│  --effect-card-inner, --effect-card-outer         │
│  --transform-card-hover, --scale-button-active    │
│  ...                                              │
└──────────────┬───────────────────────────────────┘
               │ 赋值
┌──────────────▼───────────────────────────────────┐
│       主题文件（每个主题一个独立文件）                  │
│                                                   │
│  theme-default.css  — :root / :root.dark          │
│  theme-clay.css     — :root.clay-light / .dark    │
│  theme-glass.css    — 未来主题只需这一个文件          │
│  theme-neon.css     — 同样只定义 Token 值            │
│                                                   │
│  每个文件只需：                                     │
│  1. 给所有 Token 变量赋值（颜色/阴影/圆角/渐变/边框）   │
│  2. 覆盖 Naive UI --n-* 变量                       │
│  → 不需要知道任何组件 class 名！                     │
└──────────────────────────────────────────────────┘
```

### 3.2 当前样式架构的致命问题

```
当前 tokens-clay.css 共 620 行，其中约 400 行是组件 class 选择器覆盖：

  :root.clay-light .sidebar { ... }           ← 组件 class！
  :root.clay-light .tool-card { ... }         ← 组件 class！
  :root.clay-light .mini-player { ... }       ← 组件 class！
  :root.clay-light .cover-box { ... }         ← 组件 class！
  :root.clay-light .artist-tab { ... }        ← 组件 class！
  :root.clay-light .feature-card { ... }      ← 组件 class！
  :root.clay-light .top-nav { ... }           ← 组件 class！
  ...共约 30 个组件 class 选择器

问题：
  → 每增加一个主题，就要复制这 400 行组件选择器
  → 组件改名/重构时，所有主题文件都要同步修改
  → 主题文件与组件实现强耦合，违背关注点分离原则
```

### 3.3 三层 Token 架构设计

#### 第一层：全局 Design Token（`tokens.css`）

所有主题共享的"变量名接口"，每个变量在所有主题文件中都必须赋值。Token 覆盖面必须足够广，使得组件不需要任何主题选择器。

```css
/* ═══════════════════════════════════════════════════════════
   tokens.css — 完整的 Design Token "接口" 定义
   每个变量在 :root 中设定默认值（默认亮色主题）
   :root.dark 覆盖为暗色值
   粘土等额外主题在独立文件中覆盖
   ═══════════════════════════════════════════════════════════ */

:root {
  /* ── 颜色：品牌色 ── */
  --color-primary: #1E1B4B;
  --color-on-primary: #FFFFFF;
  --color-primary-rgb: 30 27 75;
  --color-secondary: #4338CA;
  --color-secondary-rgb: 67 56 202;
  --color-accent: #5b9bd5;
  --color-accent-rgb: 91 155 213;
  --color-success: #18a058;
  --color-success-rgb: 24 160 88;
  --color-warning: #f0a020;
  --color-warning-rgb: 240 160 32;
  --color-destructive: #d03050;
  --color-destructive-rgb: 208 48 80;

  /* ── 颜色：表面 ── */
  --color-bg: #f5f5f5;
  --color-bg-secondary: #fafafa;
  --color-surface: #ffffff;
  --color-surface-hover: #f8f8f8;
  --color-surface-elevated: #ffffff;
  --color-sidebar-bg: #fafafa;
  --color-sidebar-hover: rgba(0 0 0 / 0.04);

  /* ── 颜色：文字 ── */
  --color-text: #1a1a1a;
  --color-text-secondary: #555555;
  --color-text-tertiary: #999999;

  /* ── 颜色：边框 ── */
  --color-border: rgba(0 0 0 / 0.08);
  --color-border-light: rgba(0 0 0 / 0.04);

  /* ── 颜色：特殊 ── */
  --color-scrollbar: rgba(0 0 0 / 0.12);
  --color-tag-bg: rgba(0 0 0 / 0.06);
  --color-tag-text: #888888;
  --color-folder: #caa040;

  /* ── 字体 ── */
  --text-2xs: 10px;
  --text-xs: 10.5px;
  --text-sm: 11.5px;
  --text-base: 12.5px;
  --text-md: 14px;
  --text-lg: 16px;
  --text-xl: 18px;
  --text-2xl: 24px;
  --text-3xl: 32px;
  --text-hero: 48px;
  --leading-tight: 1.3;
  --leading-normal: 1.5;
  --leading-relaxed: 1.75;

  /* ── 间距 ── */
  --space-0: 0;
  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-10: 40px;
  --space-12: 48px;

  /* ── 圆角 ── */
  --radius-sm: 3px;
  --radius-md: 5px;
  --radius-lg: 7px;
  --radius-xl: 8px;
  --radius-full: 9999px;

  /* ── 阴影 ── */
  --shadow-sm: 0 2px 8px rgba(0 0 0 / 0.06);
  --shadow-md: 0 4px 16px rgba(0 0 0 / 0.08);
  --shadow-lg: 0 8px 32px rgba(0 0 0 / 0.12);
  --shadow-player: 0 -4px 20px rgba(0 0 0 / 0.1);

  /* ── z-index ── */
  --z-base: 0;
  --z-dropdown: 10;
  --z-sticky: 20;
  --z-drawer: 30;
  --z-modal: 40;
  --z-toast: 50;
  --z-tooltip: 60;

  /* ── 动画 ── */
  --transition-fast: 0.12s ease;
  --transition-base: 0.15s ease;
  --transition-slow: 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  --transition-player: 0.3s cubic-bezier(0.4, 0, 0.2, 1);

  /* ── 布局 ── */
  --sidebar-width: 260px;
  --topbar-height: 40px;

  /* ═══════════════════════════════════════════════════════
     以下为新增 Token —— 使组件完全无需主题 class 选择器
     ═══════════════════════════════════════════════════════ */

  /* ── 渐变背景（默认无色，粘土主题覆盖为渐变） ── */
  --gradient-page: none;
  --gradient-sidebar: none;
  --gradient-card: none;
  --gradient-card-hover: none;
  --gradient-button: none;
  --gradient-button-primary: none;
  --gradient-input: none;
  --gradient-table-row: none;
  --gradient-tool-card: none;
  --gradient-tool-card-hover: none;

  /* ── 边框宽度（统一控制粗细，粘土主题覆盖为 3px） ── */
  --border-width-default: 1px;
  --border-width-strong: 1px;

  /* ── 卡片/按钮特效（默认无，粘土主题激活） ── */
  --effect-card-inner: none;
  --effect-card-outer: none;
  --effect-press: none;
  --effect-selection-bar: none;
  --effect-selection-glow: none;
  --effect-button-inner: none;
  --effect-button-outer: none;
  --effect-input-inner: none;

  /* ── 交互形变（默认不变，粘土主题激活弹性效果） ── */
  --transform-card-hover: none;
  --transform-card-active: none;
  --scale-button-active: 1;
  --transition-button-active: var(--transition-base);

  /* ── 选中指示条（默认就是纯色条，粘土主题激活发光） ── */
  --selection-bar-width: 2.5px;
  --selection-bar-shadow: none;

  /* ── Naive UI 主题键（供 Naive UI ConfigProvider 使用） ── */
  --n-color-primary: var(--color-accent);
  --n-color-success: var(--color-success);
  --n-color-warning: var(--color-warning);
  --n-color-error: var(--color-destructive);
}

/* ── 暗色主题覆盖 ── */
:root.dark {
  --color-primary: #A5B4FC;
  --color-on-primary: #1E1B4B;
  --color-primary-rgb: 165 180 252;
  --color-secondary: #818CF8;
  --color-secondary-rgb: 129 140 248;
  --color-accent: #5b9bd5;
  --color-accent-rgb: 91 155 213;
  --color-success: #36ad6a;
  --color-success-rgb: 54 173 106;
  --color-warning: #f0a020;
  --color-warning-rgb: 240 160 32;
  --color-destructive: #e06070;
  --color-destructive-rgb: 224 96 112;
  --color-bg: #1e1e1e;
  --color-bg-secondary: #252525;
  --color-surface: #2a2a2a;
  --color-surface-hover: #303030;
  --color-surface-elevated: #333333;
  --color-sidebar-bg: #1a1a1a;
  --color-sidebar-hover: rgba(255 255 255 / 0.05);
  --color-text: #e8e8e8;
  --color-text-secondary: #999999;
  --color-text-tertiary: #666666;
  --color-border: rgba(255 255 255 / 0.08);
  --color-border-light: rgba(255 255 255 / 0.04);
  --color-scrollbar: rgba(255 255 255 / 0.1);
  --color-tag-bg: rgba(255 255 255 / 0.06);
  --color-tag-text: #777777;
  --color-folder: #caa040;
  --shadow-sm: 0 2px 8px rgba(0 0 0 / 0.2);
  --shadow-md: 0 4px 16px rgba(0 0 0 / 0.3);
  --shadow-lg: 0 8px 32px rgba(0 0 0 / 0.4);
  --shadow-player: 0 -4px 24px rgba(0 0 0 / 0.4);

  /* 暗色下渐变 Token 保持 none（和亮色一致，由主题文件覆盖） */
}

/* ── 向下兼容别名（逐步迁移后移除） ── */
:root {
  --sb-text: var(--color-text);
  --sb-text-2: var(--color-text-secondary);
  --sb-text-3: var(--color-text-tertiary);
  --sb-bg: var(--color-sidebar-bg);
  --sb-bg-hover: var(--color-sidebar-hover);
  --sb-border: var(--color-border);
  --sb-scrollbar: var(--color-scrollbar);
  --sb-format-bg: var(--color-tag-bg);
  --sb-format-text: var(--color-tag-text);
  --sb-folder: var(--color-folder);
  --sb-selection-rgb: var(--color-accent-rgb);

  --ct-bg: var(--color-bg);
  --ct-bg-secondary: var(--color-bg-secondary);
  --ct-text: var(--color-text);
  --ct-text-2: var(--color-text-secondary);
  --ct-text-3: var(--color-text-tertiary);
  --ct-border: var(--color-border);
  --ct-card-bg: var(--color-surface);
  --ct-card-hover: var(--color-surface-hover);
  --ct-accent: var(--color-accent);
  --ct-accent-rgb: var(--color-accent-rgb);
}
```

#### 第二层：主题文件（每个主题一个独立文件）

**关键约束：主题文件只能包含 `:root` 选择器 + CSS 变量赋值，禁止出现组件 class 名。**

```css
/* ═══════════════════════════════════════════════════════════
   theme-clay.css — 粘土主题
   整个文件只做一件事：给 Token 变量赋予粘土风格的值
   ═══════════════════════════════════════════════════════════ */

:root.clay-light {
  /* ── 品牌色（绿色系） ── */
  --color-primary: #16A34A;
  --color-on-primary: #FFFFFF;
  --color-primary-rgb: 22 163 74;
  --color-secondary: #22C55E;
  --color-secondary-rgb: 34 197 94;
  --color-accent: #16A34A;
  --color-accent-rgb: 22 163 74;
  --color-success: #10B981;
  --color-success-rgb: 16 185 129;
  --color-warning: #F59E0B;
  --color-warning-rgb: 245 158 11;
  --color-destructive: #EF4444;
  --color-destructive-rgb: 239 68 68;

  /* ── 表面（薄荷绿调） ── */
  --color-bg: #EDF4ED;
  --color-bg-secondary: #E4F0E4;
  --color-surface: #F2F8F2;
  --color-surface-hover: #E8F5E8;
  --color-surface-elevated: #F6FBF6;
  --color-sidebar-bg: #E8F3E8;
  --color-sidebar-hover: rgba(22 163 74 / 0.06);

  /* ── 文字 ── */
  --color-text: #1A2E1F;
  --color-text-secondary: #4A6B4F;
  --color-text-tertiary: #7A9B7F;

  /* ── 边框 ── */
  --color-border: rgba(22 163 74 / 0.18);
  --color-border-light: rgba(22 163 74 / 0.06);
  --color-scrollbar: rgba(22 163 74 / 0.15);
  --color-tag-bg: rgba(22 163 74 / 0.06);
  --color-tag-text: #4A6B4F;
  --color-folder: #CA8A40;

  /* ── 圆角（粘土用大圆角） ── */
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-xl: 24px;

  /* ── 边框宽度（粘土用粗边框） ── */
  --border-width-default: 2px;
  --border-width-strong: 3px;

  /* ── 渐变（粘土核心特征） ── */
  --gradient-page: linear-gradient(160deg, #EDF4ED 0%, #E0EDE0 50%, #E8F2E8 100%);
  --gradient-sidebar: linear-gradient(180deg, #E4F0E4 0%, #DCEEDC 100%);
  --gradient-card: linear-gradient(145deg, #F6FBF6 0%, #EDF5ED 50%, #F0F7F0 100%);
  --gradient-card-hover: linear-gradient(145deg, #FFFFFF 0%, #F2F9F2 50%, #F5FBF5 100%);
  --gradient-button: linear-gradient(180deg, #F8FDF8 0%, #E8F3E8 100%);
  --gradient-button-primary: linear-gradient(180deg, #22C55E 0%, #16A34A 100%);
  --gradient-input: linear-gradient(180deg, #F6FBF6 0%, #EEF6EE 100%);
  --gradient-table-row: linear-gradient(180deg, #F4FAF4 0%, #ECF4EC 100%);
  --gradient-tool-card: linear-gradient(145deg, #F0F8F0 0%, #E6F2E6 50%, #EDF5ED 100%);
  --gradient-tool-card-hover: linear-gradient(145deg, #F8FDF8 0%, #EEF7EE 50%, #F2F9F2 100%);

  /* ── 阴影（粘土双层阴影） ── */
  --shadow-sm: inset -2px -2px 8px rgba(0 0 0 / 0.04), inset 2px 2px 8px rgba(255 255 255 / 0.9), 2px 2px 6px rgba(22 163 74 / 0.06);
  --shadow-md: inset -2px -2px 8px rgba(0 0 0 / 0.04), inset 2px 2px 8px rgba(255 255 255 / 0.9), 4px 4px 16px rgba(22 163 74 / 0.1);
  --shadow-lg: inset -2px -2px 8px rgba(0 0 0 / 0.04), inset 2px 2px 8px rgba(255 255 255 / 0.9), 8px 8px 24px rgba(22 163 74 / 0.14);
  --shadow-player: inset 1px 1px 4px rgba(255 255 255 / 0.5), 0 -4px 16px rgba(22 163 74 / 0.1);

  /* ── 特效（粘土核心特征） ── */
  --effect-card-inner: inset -2px -2px 8px rgba(0 0 0 / 0.04), inset 2px 2px 8px rgba(255 255 255 / 0.9);
  --effect-card-outer: 4px 4px 14px rgba(22 163 74 / 0.1), -2px -2px 6px rgba(255 255 255 / 0.6);
  --effect-press: inset 2px 2px 8px rgba(0 0 0 / 0.06), inset -2px -2px 4px rgba(255 255 255 / 0.6);
  --effect-button-inner: inset 1px 1px 4px rgba(255 255 255 / 0.6), inset -1px -1px 2px rgba(0 0 0 / 0.04);
  --effect-button-outer: 2px 2px 6px rgba(22 163 74 / 0.08), -1px -1px 3px rgba(255 255 255 / 0.4);
  --effect-input-inner: inset 1px 1px 4px rgba(0 0 0 / 0.04), inset -1px -1px 2px rgba(255 255 255 / 0.8);
  --effect-selection-bar: 0 0 8px rgb(var(--color-accent-rgb) / 0.6), 0 0 16px rgb(var(--color-accent-rgb) / 0.25);
  --effect-selection-glow: 3px 3px 10px rgba(22 163 74 / 0.1);

  /* ── 选中指示条 ── */
  --selection-bar-width: 3px;
  --selection-bar-shadow: inset 1px 0 2px rgba(255 255 255 / 0.3);

  /* ── 交互形变（粘土弹性） ── */
  --transform-card-hover: translateY(-3px);
  --transform-card-active: scale(0.97) translateY(0);
  --scale-button-active: 0.95;
  --transition-button-active: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);

  /* ── Naive UI ── */
  --n-color-primary: var(--color-accent);
  --n-color-success: var(--color-success);
  --n-color-warning: var(--color-warning);
  --n-color-error: var(--color-destructive);
}

/* ── 暗色粘土 ── */
:root.dark.clay-dark {
  --color-primary: #4ADE80;
  --color-on-primary: #0A1F12;
  --color-primary-rgb: 74 222 128;
  --color-secondary: #34D399;
  --color-secondary-rgb: 52 211 153;
  --color-accent: #4ADE80;
  --color-accent-rgb: 74 222 128;
  --color-success: #34D399;
  --color-success-rgb: 52 211 153;
  --color-warning: #FBBF24;
  --color-warning-rgb: 251 191 36;
  --color-destructive: #F87171;
  --color-destructive-rgb: 248 113 113;
  --color-bg: #0F1713;
  --color-bg-secondary: #16201B;
  --color-surface: #1E2A24;
  --color-surface-hover: #24322C;
  --color-surface-elevated: #283832;
  --color-sidebar-bg: #0E1612;
  --color-sidebar-hover: rgba(74 222 128 / 0.06);
  --color-text: #E0EDE4;
  --color-text-secondary: #8BA895;
  --color-text-tertiary: #5A7062;
  --color-border: rgba(74 222 128 / 0.12);
  --color-border-light: rgba(74 222 128 / 0.05);
  --color-scrollbar: rgba(74 222 128 / 0.12);
  --color-tag-bg: rgba(74 222 128 / 0.08);
  --color-tag-text: #8BA895;
  --color-folder: #CA8A40;

  --gradient-page: linear-gradient(160deg, #0F1713 0%, #111D16 50%, #141F18 100%);
  --gradient-sidebar: linear-gradient(180deg, #0E1612 0%, #0A120E 100%);
  --gradient-card: linear-gradient(145deg, #223028 0%, #1C2822 50%, #1E2A24 100%);
  --gradient-card-hover: linear-gradient(145deg, #2A3A32 0%, #24342C 50%, #26362E 100%);
  --gradient-button: linear-gradient(180deg, #26362E 0%, #1C2822 100%);
  --gradient-button-primary: linear-gradient(180deg, #4ADE80 0%, #22C55E 100%);
  --gradient-input: linear-gradient(180deg, #1A2420 0%, #16201B 100%);
  --gradient-table-row: linear-gradient(180deg, #1C2822 0%, #18231D 100%);
  --gradient-tool-card: linear-gradient(145deg, #1E2A24 0%, #18231D 50%, #1C2822 100%);
  --gradient-tool-card-hover: linear-gradient(145deg, #283630 0%, #223028 50%, #24322C 100%);

  --shadow-sm: inset -1px -1px 4px rgba(255 255 255 / 0.02), inset 2px 2px 6px rgba(0 0 0 / 0.3), 2px 2px 8px rgba(0 0 0 / 0.3);
  --shadow-md: inset -1px -1px 4px rgba(255 255 255 / 0.02), inset 2px 2px 6px rgba(0 0 0 / 0.3), 4px 4px 18px rgba(0 0 0 / 0.4);
  --shadow-lg: inset -1px -1px 4px rgba(255 255 255 / 0.02), inset 2px 2px 6px rgba(0 0 0 / 0.3), 8px 8px 28px rgba(0 0 0 / 0.5);
  --shadow-player: inset 1px 1px 3px rgba(255 255 255 / 0.02), 0 -4px 20px rgba(0 0 0 / 0.5);

  --effect-card-inner: inset -1px -1px 4px rgba(255 255 255 / 0.02), inset 2px 2px 6px rgba(0 0 0 / 0.3);
  --effect-card-outer: 4px 4px 16px rgba(0 0 0 / 0.4), -2px -2px 6px rgba(255 255 255 / 0.02);
  --effect-press: inset 2px 2px 8px rgba(0 0 0 / 0.4), inset -1px -1px 3px rgba(255 255 255 / 0.03);
  --effect-button-inner: inset 1px 1px 3px rgba(255 255 255 / 0.04), inset -1px -1px 2px rgba(0 0 0 / 0.2);
  --effect-button-outer: 2px 2px 8px rgba(0 0 0 / 0.3);
  --effect-input-inner: inset 1px 1px 4px rgba(0 0 0 / 0.2), inset -1px -1px 2px rgba(255 255 255 / 0.02);
  --effect-selection-bar: 0 0 8px rgb(74 222 128 / 0.5), 0 0 16px rgb(74 222 128 / 0.2);
  --effect-selection-glow: 3px 3px 10px rgba(74 222 128 / 0.12);
  --selection-bar-width: 3px;
  --selection-bar-shadow: inset 1px 0 2px rgba(255 255 255 / 0.1);
  --transform-card-hover: translateY(-3px);
  --transform-card-active: scale(0.97) translateY(0);
  --scale-button-active: 0.95;
  --transition-button-active: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);

  --n-color-primary: var(--color-accent);
  --n-color-success: var(--color-success);
  --n-color-warning: var(--color-warning);
  --n-color-error: var(--color-destructive);
}
```

#### 第三层：Naive UI 全局覆盖（`naive-overrides.css`）

独立文件，与主题解耦。集中管理所有 Naive UI 组件的全局样式覆盖。

```css
/* ═══════════════════════════════════════════════════════════
   naive-overrides.css — Naive UI 全局覆盖
   使用 var(--gradient-*), var(--effect-*) 等 Token
   主题切换时自动生效，此文件无需改动
   ═══════════════════════════════════════════════════════════ */

/* ── 页面背景 ── */
body {
  background: var(--gradient-page, var(--color-bg));
}

/* ── 按钮 ── */
.n-button:not(.n-button--primary-type):not(.n-button--error-type):not(.n-button--warning-type):not(.n-button--info-type):not(.n-button--success-type) {
  border-radius: var(--radius-xl) !important;
  background: var(--gradient-button, var(--color-surface)) !important;
  box-shadow: var(--effect-button-inner), var(--effect-button-outer);
  transition: var(--transition-button-active) !important;
}
.n-button--primary-type {
  background: var(--gradient-button-primary, var(--color-accent)) !important;
  border-radius: var(--radius-xl) !important;
}
.n-button:not(.n-button--disabled):active {
  transform: scale(var(--scale-button-active));
  box-shadow: var(--effect-press) !important;
}

/* ── 卡片 ── */
.n-card {
  border-radius: var(--radius-xl) !important;
  background: var(--gradient-card, var(--color-surface)) !important;
  box-shadow: var(--shadow-md);
}
.n-card:hover {
  background: var(--gradient-card-hover, var(--color-surface-hover)) !important;
  box-shadow: var(--shadow-lg);
}

/* ── 输入框 ── */
.n-input,
.n-base-selection {
  border-radius: var(--radius-md) !important;
  background: var(--gradient-input, var(--color-surface)) !important;
  box-shadow: var(--effect-input-inner);
}

/* ── 表格 ── */
.n-data-table-tr td {
  background: var(--gradient-table-row, transparent) !important;
}
.n-data-table-tr:hover td {
  box-shadow: var(--shadow-sm) !important;
  background: var(--gradient-card, var(--color-surface-hover)) !important;
}
.n-data-table-tr.n-data-table-tr--checked td {
  box-shadow: var(--effect-selection-glow, none) !important;
  background: var(--gradient-card-hover, rgba(var(--color-accent-rgb) / 0.1)) !important;
}

/* ── Modal / Drawer ── */
.n-modal .n-card,
.n-drawer-content {
  border-radius: var(--radius-xl) !important;
  background: var(--gradient-card, var(--color-surface)) !important;
  box-shadow: var(--shadow-lg);
}

/* ── 进度条 ── */
.n-progress { border-radius: var(--radius-md); }

/* ── 开关 ── */
.n-switch__rail { box-shadow: var(--effect-input-inner); }
.n-switch__button { box-shadow: var(--shadow-sm); }

/* ── 标签 ── */
.n-tag {
  border-radius: var(--radius-sm) !important;
  box-shadow: var(--effect-button-inner);
}

/* ── Alert ── */
.n-alert {
  border-radius: var(--radius-md);
  background: var(--gradient-card, var(--color-surface)) !important;
}

/* ── Popover / Dropdown ── */
.n-popover { border-radius: var(--radius-xl) !important; box-shadow: var(--shadow-lg); }
.n-dropdown-menu { border-radius: var(--radius-lg); box-shadow: var(--shadow-lg); }
```

### 3.4 组件中如何使用 Token

```css
/* ═══════════════════════════════════════════════════════════
   ✅ 正确：组件只引用 Token，主题切换自动生效
   组件文件中绝对不出现主题选择器
   ═══════════════════════════════════════════════════════════ */

/* ── 侧边栏 ── */
.sidebar {
  background: var(--gradient-sidebar, var(--color-sidebar-bg));
  border-right: var(--border-width-strong) solid var(--color-border);
}
.tree-item.selected {
  background: rgb(var(--color-accent-rgb) / 0.12);
}
.tree-item.selected .select-bar {
  width: var(--selection-bar-width);
  background: rgb(var(--color-accent-rgb));
  box-shadow: var(--effect-selection-bar);
}

/* ── 工具卡片 ── */
.tool-card {
  border: var(--border-width-strong) solid var(--color-border);
  border-radius: var(--radius-xl);
  background: var(--gradient-tool-card, var(--color-surface));
  box-shadow: var(--effect-card-inner), var(--effect-card-outer);
}
.tool-card:hover {
  transform: var(--transform-card-hover);
  background: var(--gradient-tool-card-hover, var(--color-surface-hover));
}
.tool-card:active {
  transform: var(--transform-card-active);
  box-shadow: var(--effect-press);
}

/* ── 迷你播放器 ── */
.mini-player {
  border-top: var(--border-width-strong) solid var(--color-border);
  background: var(--gradient-card, var(--color-surface));
  box-shadow: var(--shadow-player);
}
```

### 3.5 新增主题只需三步

```
1. 新建 src/styles/theme-xxx.css
2. 在 :root.xxx-light 和 :root.dark.xxx-dark 中给所有 Token 赋值
3. 在 main.js 中 import './styles/theme-xxx.css'

完成！不需要改任何 .vue 文件，不需要写任何组件 class 选择器。
```

### 3.6 当前样式问题清单

| 问题 | 文件 | 影响 |
|------|------|------|
| 620行 tokens-clay.css 含 ~30 个组件 class 选择器 | `tokens-clay.css` | 新增主题=复制 400 行 |
| PipelineList/Detail 大量用 `--n-*` 变量而非 `--ct-*` | `PipelineList.vue`, `PipelineDetail.vue` | 主题切换时样式不一致 |
| Home.vue 用 `--n-*` 变量 | `Home.vue` | 同上 |
| Workbench.vue 混用 `--ct-*` 和 `--n-*` | `Workbench.vue` | 同上 |
| 硬编码颜色值 `#d03050`, `#18a058`, `#2080f0` | `PipelineDetail.vue` | 颜色不随主题变化 |
| `style.css` 中硬编码字体 | `style.css` | 应迁移到 Token |
| Sidebar 使用 `--sb-*` 别名（正确，但需确保别名链不断） | `Sidebar.vue` | 已验证正确 |

### 3.7 Scoped vs Global 样式使用规则

| 样式类型 | 使用场景 | 示例 |
|----------|----------|------|
| `<style scoped>` | 组件自身布局、间距、装饰 | `.tool-card`, `.path-bar` |
| `<style>` + `:deep()` | 覆盖 Naive UI 组件内部样式 | `.table-body :deep(.n-data-table-th)` |
| `<style>`（全局） | `naive-overrides.css` 中集中管理的 Naive UI 全局覆盖 | 按钮/卡片/输入框统一覆盖 |

**规则**：
- 组件内部样式必须 `scoped`
- Naive UI 覆盖用独立的非 scoped `<style>` 块，集中放在文件末尾
- 禁止在 scoped 样式中使用 `:deep()` 做全局性主题覆盖——应放到 `naive-overrides.css`

### 3.8 响应式布局

使用 CSS flexbox/grid，避免固定像素宽度。使用 token 中的 `--sidebar-width`。

```css
/* ✅ 正确 */
.sidebar {
  width: var(--sidebar-width);
  transition: width var(--transition-slow);
}

/* ❌ 错误 */
.sidebar {
  width: 260px;
}
```

### 3.9 滚动条样式

统一使用隐藏式滚动条（hover 时显示），复用以下模式：

```css
.scroll-area {
  overflow-y: auto;
}
.scroll-area::-webkit-scrollbar { width: 6px; }
.scroll-area::-webkit-scrollbar-track { background: transparent; }
.scroll-area::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 10px;
  transition: background 0.2s;
}
.scroll-area:hover::-webkit-scrollbar-thumb {
  background: var(--color-scrollbar);
}
```

---

## 4. API 层规范

### 4.1 API 函数命名

所有 API 函数使用动词开头，导出方式为命名导出：

```js
// ✅ 正确
export function fetchMetadata(rawPath) { ... }
export function saveMetadata(rawPath, meta) { ... }
export function pausePipeline(id) { ... }

// ❌ 错误
export default { getMeta, saveMeta }
```

### 4.2 统一错误处理

使用 axios 拦截器统一处理错误，组件中不再重复 `console.error` + `window.$message`：

```js
// client.js — 统一拦截
client.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err?.response?.data?.message || err.message
    // 仅日志，不弹提示（由调用方决定是否弹）
    console.error('[API]', err.config?.url, msg)
    return Promise.reject(err)
  },
)
```

**组件中处理**：

```js
// ✅ 正确：区分用户提示和静默失败
try {
  const data = await fetchData()
} catch (e) {
  // 关键操作：给用户提示
  message.error(t('common.error'))
}

// 非关键操作：静默处理
fetchMetadata(file.path).then(res => editStore.refreshMeta(file, res)).catch(() => {})
```

### 4.3 API 文件组织

按功能域划分，每个文件对应后端一个 Controller：

| 文件 | 对应后端 |
|------|----------|
| `browse.js` | BrowseController |
| `music.js` | MusicController |
| `pipeline.js` | PipelineController |
| `tools.js` | ToolController |
| `enrich.js` | EnrichController |

---

## 5. 路由与代码分割

### 5.1 路由懒加载

所有页面组件必须使用动态 import：

```js
// ✅ 正确 — 已全部使用
const routes = [
  { path: '/', component: () => import('@/views/Home.vue') },
  { path: '/workbench', component: () => import('@/views/Workbench.vue') },
]
```

### 5.2 工具面板懒加载

`Workbench.vue` 中的 14 个工具面板组件必须改为异步组件，减少首屏加载体积：

```js
// ✅ 正确
import { defineAsyncComponent } from 'vue'

const DedupPanel = defineAsyncComponent(() => import('@/components/tool/DedupPanel.vue'))
const SplitPanel = defineAsyncComponent(() => import('@/components/metadata/SplitPanel.vue'))
// ...其他面板

// ❌ 错误：全部静态导入（当前状态）
import DedupPanel from '@/components/tool/DedupPanel.vue'
import SplitPanel from '@/components/metadata/SplitPanel.vue'
// ...14 个面板
```

### 5.3 路由参数监听

页面组件监听路由参数变化时，必须处理参数切换的清理逻辑：

```js
// ✅ 正确：PipelineDetail.vue 的做法
watch(() => route.params.id, (newId) => {
  if (newId) {
    disconnect()     // 清理旧连接
    load()           // 重新加载
    connect(newId)   // 建立新连接
  }
})
```

---

## 6. 国际化规范

### 6.1 i18n Key 命名

使用点号分隔的层级结构：`域.组件.字段`

```js
// ✅ 正确
t('workbench.tools.split')
t('edit.fields.title')
t('pipeline.stateRunning')

// ❌ 错误 — 过于扁平
t('splitTool')
t('editTitle')
```

### 6.2 组件中使用方式

```vue
<template>
  <!-- ✅ 模板中：使用 $t() -->
  <span>{{ $t('common.save') }}</span>

  <!-- ❌ 不要在模板中使用 t()（需要额外引入） -->
  <span>{{ t('common.save') }}</span>
</template>

<script setup>
// ✅ 脚本中：使用 useI18n()
const { t } = useI18n()
message.success(t('common.success'))
</script>
```

### 6.3 禁止硬编码文本

所有面向用户的文本（包括 placeholder、tooltip、dialog content）必须走 i18n。

```vue
<!-- ❌ 错误 — 硬编码中文 -->
<n-button>开始处理</n-button>
<p class="placeholder-hint">在侧边栏选择一个目录开始浏览</p>
<n-modal title="去重检查">

<!-- ✅ 正确 -->
<n-button>{{ $t('tool.startProcessing') }}</n-button>
<p class="placeholder-hint">{{ $t('workbench.selectFileHint') }}</p>
<n-modal :title="$t('workbench.tools.dedup')">
```

> 现状：`TopNavBar.vue` 中有 `'切换到默认风格'`、`'切换到粘土风格'` 硬编码；`Workbench.vue` 中有 `'去重检查'` 等硬编码。需修复。

---

## 7. 性能优化

### 7.1 消除 Workbench.vue 中的重复代码

当前 `Workbench.vue` 有 10+ 个几乎完全相同的 computed 属性（`splitToolTarget`、`enrichToolTarget`、`dedupTarget` 等），每个约 10 行，共约 150 行重复代码。

**方案**：抽取通用工厂函数：

```js
// composables/useToolTarget.js
export function useToolTarget() {
  const fileStore = useFileStore()

  const toolTarget = computed(() => {
    const selected = fileStore.pageFiles.filter(f => fileStore.selectedIds.has(f.id))
    const files = selected.map(f => f.path)
    const base = fileStore.currentPath || ''
    const folderPaths = [...fileStore.selectedFolderNames].map(name =>
      base && base !== '/' ? `${base}/${name}` : `${base}${name}`,
    )
    return { path: base, files, folders: folderPaths }
  })

  return { toolTarget }
}
```

### 7.2 使用表格驱动的工具处理

当前 `handleToolClick` 是一个 60+ 行的 if-else 链：

```js
// ❌ 当前状态 — 12 个 if 分支，每个几乎相同
function handleToolClick(toolKey) {
  if (toolKey === 'split') { /* 8 行 */ return }
  if (toolKey === 'encodingRepair') { /* 8 行 */ return }
  // ... 10 more
}

// ✅ 改进：配置驱动
const TOOL_CONFIG = {
  split:          { visibleRef: 'splitToolVisible', targetRef: 'splitToolTarget', checkSelection: true },
  encodingRepair: { visibleRef: 'encodingRepairVisible', targetRef: 'encodingRepairTarget', checkSelection: true },
  // ... 每个工具 1 行
}

function handleToolClick(toolKey) {
  const config = TOOL_CONFIG[toolKey]
  if (!config) return
  if (config.checkSelection) {
    const t = toolTarget.value
    if (t.files.length === 0 && t.folders.length === 0) {
      message.warning('请先勾选文件或文件夹！')
      return
    }
  }
  visibilityState[config.visibleRef].value = true
}
```

### 7.3 虚化长列表

侧边栏文件列表和表格数据使用虚拟滚动。

**当前问题**：`n-data-table` 已自带虚拟滚动，但侧边栏的 `.tree-list` 渲染所有文件节点，大目录下性能差。

```vue
<!-- ✅ 使用 Naive UI 虚拟列表 -->
<n-virtual-list :items="fileStore.filteredFiles" :item-size="36">
  <template #default="{ item }">
    <li class="tree-item file">...</li>
  </template>
</n-virtual-list>
```

### 7.4 使用 v-memo 优化重复渲染

对于依赖大量数据的列表项，使用 `v-memo` 跳过不变项的重新渲染：

```vue
<!-- ✅ 表格行只依赖行数据 -->
<div v-for="item in items" :key="item.id" v-memo="[item.id, item.updatedAt]">
  <!-- 行内容 -->
</div>
```

### 7.5 静态数据使用 shallowRef

```js
// ✅ 列定义、工具列表等静态配置
const allColumns = shallowRef([...])  // 或直接用 computed
const allToolList = shallowRef([...])
```

### 7.6 避免在 computed 中创建新对象/数组

```js
// ❌ 错误：每次计算都新建数组
const filteredFiles = computed(() => {
  return files.value.filter(f => f.name.includes(kw))  // 每次都是新数组
})

// ✅ 对于大列表，使用缓存策略或 v-memo 减少重绘
```

### 7.7 轮询改为 WebSocket

`PipelineList.vue` 使用 `setInterval` 每 5 秒轮询，应改为 WebSocket 订阅（和 `PipelineDetail.vue` 一致）。

### 7.8 打包优化

```js
// vite.config.js — 添加分包策略
export default defineConfig({
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'naive-ui': ['naive-ui'],
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'icons': ['@vicons/ionicons5'],
        },
      },
    },
  },
})
```

---

## 8. 文件组织规范

### 8.1 目录结构（重构后目标）

```
src/
├── api/                # API 请求函数，按后端 Controller 划分
│   ├── client.js       # Axios 实例 + 拦截器
│   ├── browse.js
│   ├── music.js
│   ├── pipeline.js
│   ├── tools.js
│   └── enrich.js
├── components/         # 可复用组件，按功能域分目录
│   ├── file/           # 文件浏览相关（Sidebar）
│   ├── layout/         # 布局组件（TopNavBar）
│   ├── metadata/       # 元数据编辑（EditDrawer, EditSection, EnrichPanel 等）
│   └── tool/           # 工具面板（DedupPanel, OrganizePanel 等）
├── composables/        # 可复用逻辑（use*）
├── directive/          # 自定义指令
├── i18n/               # 国际化
│   ├── index.js
│   └── locales/
├── plugins/            # 插件安装（naive-ui 全局注册）
├── router/             # 路由定义
├── store/              # Pinia Store
├── styles/             # 全局样式 / Theme Token
│   ├── tokens.css          # Token "接口" 定义 + 默认主题值（唯一引用入口）
│   ├── theme-clay.css      # 粘土主题 Token 覆盖（纯变量赋值，无组件 class）
│   ├── theme-xxx.css       # 未来新主题
│   └── naive-overrides.css # Naive UI 全局覆盖（引用 Token，主题无关）
├── utils/              # 工具函数
├── views/              # 页面组件
├── App.vue
├── main.js
└── style.css           # 最小全局样式（仅 font-size, font-family, -webkit-font-smoothing）
```

### 8.2 新增 composable 的条件

满足以下任一条件，抽取为 composable：
1. 被 2 个以上组件使用
2. 包含独立的生命周期管理（如 WebSocket 连接）
3. 包含独立的 localStorage 持久化逻辑
4. 超过 40 行

### 8.3 Import 顺序

```js
// 1. Vue 核心
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'

// 2. 第三方库
import { useI18n } from 'vue-i18n'
import { NButton, NIcon, useMessage } from 'naive-ui'
import { MusicalNotesOutline } from '@vicons/ionicons5'

// 3. 项目内部 — API
import { fetchMetadata } from '@/api/music.js'

// 4. 项目内部 — Store
import { useEditStore } from '@/store/edit.js'

// 5. 项目内部 — Composables
import { useKeyboard } from '@/composables/useKeyboard.js'

// 6. 项目内部 — 组件
import EditDrawer from '@/components/metadata/EditDrawer.vue'

// 7. 项目内部 — Utils
import { songField } from '@/utils/musicMeta.js'
```

---

## 9. 代码风格

### 9.1 注释

- 文件头：用 JSDoc 风格描述文件职责（仅 stores 和核心 composables 需要）
- 区块分隔：用 `// ── 区块名 ──` 分隔不同功能区块
- 禁止无意义注释（如 `// 设置值` 对应 `value = newVal`）

```js
// ✅ 正确
// ── 分页 ──
function onPageChange(page) {
  fileStore.loadPage(page)
}

// ❌ 错误
// 处理分页变化
function onPageChange(page) {
  // 调用 fileStore 的 loadPage 方法
  fileStore.loadPage(page)
}
```

### 9.2 函数命名

| 类型 | 前缀 | 示例 |
|------|------|------|
| 事件处理 | `handle` / `on` | `handleSave`, `onPageChange` |
| 数据获取 | `fetch` / `load` | `fetchMetadata`, `loadDirectory` |
| 切换/设置 | `toggle` / `set` | `toggleTheme`, `setLocale` |
| 计算属性 | 名词/形容词 | `isDirty`, `filteredFiles` |

### 9.3 模板属性顺序

```vue
<template>
  <!-- 1. 静态属性 -->
  <!-- 2. v-bind 动态属性（:prop） -->
  <!-- 3. v-on 事件（@event） -->
  <!-- 4. v-if / v-for / v-show -->
  <n-button
    size="small"
    type="primary"
    :disabled="!editStore.isDirty"
    :loading="saving"
    @click="handleSave"
    v-if="editStore.editingFile"
  >
```

### 9.4 避免 template 中的复杂表达式

```vue
<!-- ❌ 错误 -->
<span>{{ player.currentFile?.meta?.songs?.[0]?.title || player.currentFile?.name || '—' }}</span>

<!-- ✅ 正确：提取为 computed -->
<span>{{ displayTrackName }}</span>

<script setup>
const displayTrackName = computed(() =>
  songField(player.currentFile?.meta, 'title') || player.currentFile?.name || '—'
)
</script>
```

---

## 10. 完整优化方案与执行计划

### 10.1 现状总览

| 维度 | 当前状态 | 目标状态 |
|------|----------|----------|
| 组件架构 | `Workbench.vue` 1700 行超级组件 | 拆分为 6 个独立子组件 |
| 主题架构 | 620 行 `tokens-clay.css` 含 ~30 个组件选择器 | 主题文件纯变量赋值，~150 行 |
| 新增主题成本 | 复制 620 行 + 改 ~30 个选择器 | 新建 150 行文件 + 给所有 Token 赋值 |
| 工具面板加载 | 14 个面板全部静态 import | `defineAsyncComponent` 按需加载 |
| 代码重复 | 10 个 identical computed, 12 分支 if-else | 1 个 composable + 配置驱动 |
| 视频样式一致性 | `EditSection.vue` 用 Options API | 全部 `<script setup>` |
| CSS 变量引用 | 混用 `--n-*`、`--ct-*`、`--color-*`、硬编码 | 统一用 Token 变量 |
| i18n 覆盖 | 多处硬编码中文 | 100% i18n 覆盖 |
| 轮询 vs WS | `PipelineList` 用 setInterval | 统一 WebSocket |
| 虚拟滚动 | 侧边栏全量渲染 | `n-virtual-list` |
| 打包优化 | 无分包策略 | `manualChunks` 三分包 |

### 10.2 分阶段执行计划

#### 阶段 1：主题架构重构（优先 — 解锁后续所有主题相关工作）

**目标**：建立三层 Token 架构，使组件与主题完全解耦。

| 步骤 | 文件 | 操作 |
|------|------|------|
| 1.1 | `styles/tokens.css` | 扩充 Token 变量：新增 `--gradient-*`、`--border-width-*`、`--effect-*`、`--transform-*`、`--selection-*` 系列 |
| 1.2 | `styles/tokens.css` | 重组：默认主题值保留在此文件，`--ct-*`/`--sb-*` 别名指向新 Token |
| 1.3 | `styles/theme-clay.css` | **重写**：删除所有组件 class 选择器，只保留 `:root.clay-light` 和 `:root.dark.clay-dark` 中的纯变量赋值 |
| 1.4 | `styles/naive-overrides.css` | **新建**：从 `tokens-clay.css` 中提取所有 Naive UI 全局覆盖，改用 Token 变量 |
| 1.5 | `main.js` | 更新 import 顺序：`tokens.css` → `theme-clay.css` → `naive-overrides.css` → `style.css` |
| 1.6 | 各组件 .vue | 逐步清理组件中的硬编码视觉属性，替换为 Token 引用 |

#### 阶段 2：组件架构重构（解决可维护性瓶颈）

**目标**：拆分 `Workbench.vue`，消除重复，异步加载面板。

| 步骤 | 文件 | 操作 |
|------|------|------|
| 2.1 | `views/Workbench.vue` | 提取 `<ToolBar>` 为独立组件 |
| 2.2 | `views/Workbench.vue` | 提取 `<PathBar>` 为独立组件 |
| 2.3 | `views/Workbench.vue` | 提取 `<FileTable>` 为独立组件（含列管理、分页） |
| 2.4 | `views/Workbench.vue` | 提取 `<MiniPlayer>` 为独立组件 |
| 2.5 | `views/Workbench.vue` | 提取 `<ToolModals>` 为独立组件（管理 14 个模态框） |
| 2.6 | `composables/useToolTarget.js` | **新建**：替换 10 个重复 computed |
| 2.7 | `views/Workbench.vue` | `handleToolClick` 改为配置驱动 |
| 2.8 | `views/Workbench.vue` | 14 个面板改为 `defineAsyncComponent` |

#### 阶段 3：样式一致性治理

**目标**：清理所有硬编码和不一致的 CSS 变量引用。

| 步骤 | 文件 | 操作 |
|------|------|------|
| 3.1 | `components/metadata/EditSection.vue` | 从 Options API + `h()` 改为 `<script setup>` + `<template>` |
| 3.2 | `views/PipelineList.vue` | `--n-*` 变量 → Token 变量 |
| 3.3 | `views/PipelineDetail.vue` | `--n-*` 变量 → Token 变量；硬编码颜色 → Token 变量 |
| 3.4 | `views/Home.vue` | `--n-*` 变量 → Token 变量 |
| 3.5 | `style.css` | 字体栈迁移到 Token 变量 |

#### 阶段 4：性能优化

**目标**：减少首屏体积、优化渲染性能。

| 步骤 | 文件 | 操作 |
|------|------|------|
| 4.1 | `components/file/Sidebar.vue` | 文件列表改用 `n-virtual-list` |
| 4.2 | `views/PipelineList.vue` | `setInterval` 轮询 → WebSocket |
| 4.3 | `vite.config.js` | 添加 `manualChunks` 分包配置 |
| 4.4 | `store/file.js` | `files`/`pageFiles`/`folders` 评估改为 `shallowRef` |
| 4.5 | 各组件 | 大列表项添加 `v-memo` |

#### 阶段 5：国际化补全 + 长期优化

**目标**：消除所有硬编码文本，建立长期质量基线。

| 步骤 | 文件 | 操作 |
|------|------|------|
| 5.1 | `components/layout/TopNavBar.vue` | 硬编码 title → i18n key |
| 5.2 | `views/Workbench.vue` | 硬编码 `'去重检查'` 等 → i18n key |
| 5.3 | `composables/useKeyboard.js` | 硬编码提示文本 → i18n |
| 5.4 | 全局 | TypeScript 迁移可行性评估 |

### 10.3 验收标准

每阶段完成后，对照以下标准验证：

- [ ] **阶段 1**：在 `theme-clay.css` 中搜索组件 class 名（如 `.sidebar`），结果为零
- [ ] **阶段 1**：新增一个测试主题文件（仅 150 行变量赋值），切换后所有页面视觉一致
- [ ] **阶段 2**：`Workbench.vue` 少于 400 行，且不再包含任何 `xxxTarget` 重复 computed
- [ ] **阶段 2**：DevTools Network 面板显示工具面板组件按需加载（非首屏一次性加载）
- [ ] **阶段 3**：搜索 `#1a1a1a`、`#e8e8e8`、`#d03050` 等硬编码颜色，结果为零（tokens.css 除外）
- [ ] **阶段 3**：`EditSection.vue` 使用 `<script setup>` + `<template>`
- [ ] **阶段 4**：Sidebar 渲染 10000 个文件项，滚动帧率 ≥ 50fps
- [ ] **阶段 4**：`PipelineList.vue` 不再使用 `setInterval`
- [ ] **阶段 5**：搜索硬编码中文字符串，结果为零（i18n locales 文件除外）
- [ ] **全部**：`npm run build` 无警告/错误

---

## 检查清单

新增组件/功能时，对照以下清单自查：

- [ ] 使用 `<script setup>` 语法
- [ ] Props 和 Emits 有完整类型声明
- [ ] 样式使用 `scoped`，引用 Token 变量而非硬编码颜色
- [ ] **不写任何主题 class 选择器**（如 `:root.clay-light .my-component`）
- [ ] 用户可见文本全部走 i18n
- [ ] 工具面板使用 `defineAsyncComponent` 异步加载
- [ ] 遵循 import 顺序约定
- [ ] 函数命名符合规范（handle/on/fetch/load/toggle/set）
- [ ] 没有在 template 中写复杂表达式
- [ ] 新增 API 函数命名导出，动词开头
- [ ] 超过 40 行的逻辑抽为 composable
