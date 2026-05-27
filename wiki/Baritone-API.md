# Baritone API 指令参考

> `baritone` / `br` 全局对象上的所有函数。Baritone 为可选前置，未安装时调用任何函数会提示并终止脚本。

## 寻路与移动

| 函数 | 参数 | 说明 |
|------|------|------|
| `goto(x, y, z)` | 坐标 | 寻路走到指定坐标 |
| `come()` | — | 走到玩家摄像机所在位置 |
| `axis()` | — | 前往最近的坐标轴（X=0 或 Z=0） |
| `thisWay()` | — | 沿当前视角方向持续前进 |
| `surface()` | — | 回到地表（露出天空的位置） |
| `getToBlock(blockId)` | 方块 ID，如 `"minecraft:chest"` | 走到最近的指定方块 |

### 示例

```js
// 走到坐标 (100, 64, 200)
br.goto(100, 64, 200);

// 走到最近的箱子
br.getToBlock("minecraft:chest");

// 回到地表
br.surface();
```

## 挖掘与隧道

| 函数 | 参数 | 说明 |
|------|------|------|
| `mine(blockId, count?)` | 方块 ID，可选数量 | 自动挖掘指定方块 |
| `tunnel()` | — | 沿当前方向挖掘一条 1×2 的隧道 |

### 示例

```js
// 挖 64 个钻石矿
br.mine("minecraft:diamond_ore", 64);

// 挖隧道
br.tunnel();
```

## 农场与探索

| 函数 | 参数 | 说明 |
|------|------|------|
| `farm(range?)` | 范围（默认 100） | 自动收割并补种附近农作物 |
| `explore()` | — | 随机探索未知区块 |

### 示例

```js
// 农场模式，范围 50
br.farm(50);
```

## 跟随与拾取

| 函数 | 参数 | 说明 |
|------|------|------|
| `follow(entityType?)` | 实体 ID，如 `"minecraft:cow"`；省略则跟随附近玩家 | 跟踪并跟随指定实体 |
| `pickup()` | — | 自动捡起附近掉落物 |

### 示例

```js
// 跟随牛
br.follow("minecraft:cow");

// 跟随最近玩家
br.follow();
```

## 建筑

| 函数 | 参数 | 说明 |
|------|------|------|
| `build(schematic, x?, y?, z?)` | 蓝图名称, 可选起点坐标 | 加载 `.minecraft/schematics/` 下的 `.schematic` 文件并建造 |

### 示例

```js
// 在当前位置建造 "house" 蓝图
br.build("house");
```

## 交互

| 函数 | 参数 | 说明 |
|------|------|------|
| `click()` | — | 模拟点击（须先 `lookAt` 对准目标方块/实体） |

### 示例

```js
mc.lookAt(100, 64, 200);
mc.waitTick(1);
br.click();
```

## 控制

| 函数 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `stop()` | — | — | 取消所有 Baritone 行为 |
| `pause()` | — | — | 暂停当前任务 |
| `resume()` | — | — | 恢复暂停的任务 |
| `isActive()` | — | `boolean` | 是否正在工作（寻路中） |
| `isPaused()` | — | `boolean` | 是否处于暂停状态 |
| `command(cmd)` | Baritone 原始命令字符串 | — | 执行任意 Baritone 命令 |

### 示例

```js
// 暂停
br.pause();

// 查询状态
if (br.isActive()) {
    mc.log("Baritone is working...");
}

// 恢复
br.resume();

// 停止
br.stop();
```

## 设置

| 函数 | 参数 | 说明 |
|------|------|------|
| `setting(key, value)` | 设置项名, 值 | 修改 Baritone 设置 |

### 示例

```js
// 允许破坏方块
br.setting("allowBreak", true);

// 启用聊天控制
br.setting("chatControl", true);
```

## 选区

| 函数 | 参数 | 说明 |
|------|------|------|
| `selPos1(x?, y?, z?)` | 可选坐标（省略 = 当前位置） | 设置选区点 1 |
| `selPos2(x?, y?, z?)` | 可选坐标（省略 = 当前位置） | 设置选区点 2 |
| `select(x1, y1, z1, x2, y2, z2)` | 两个角的坐标 | 直接设置选区 |
| `clearSelection()` | — | 清除所有选区 |

### 示例

```js
// 手动设选区
br.selPos1(100, 64, 100);
br.selPos2(120, 80, 120);

// 直接指定
br.select(0, 64, 0, 50, 100, 50);

// 清除
br.clearSelection();
```

## 信息 & 工具

| 函数 | 参数 | 说明 |
|------|------|------|
| `find(blockId)` | 方块 ID | 搜索世界中的方块位置 |
| `blacklist()` | — | 将准星对准的方块加入寻路黑名单 |
| `proc()` | — | 显示当前进程状态 |
| `eta()` | — | 显示预计到达时间 |

### 示例

```js
// 搜索钻石
br.find("minecraft:diamond_ore");

// 查看状态
br.proc();
```

## 路径点 & 家

| 函数 | 参数 | 说明 |
|------|------|------|
| `waypointSave(name)` | 名称 | 保存当前位置为路径点 |
| `waypointList()` | — | 列出所有路径点 |
| `waypointDelete(name)` | 名称 | 删除指定路径点 |
| `sethome()` | — | 设置当前位置为家 |
| `home()` | — | 回家 |

### 示例

```js
// 保存路径点
br.waypointSave("基地");

// 列出所有路径点
br.waypointList();

// 回家
br.sethome();
// ... 之后 ...
br.home();
```

## 完整脚本示例

```js
// 采矿 + 回家的完整流程
br.setting("allowBreak", true);

// 搜索钻石
br.find("minecraft:diamond_ore");

// 挖 32 个钻石
br.mine("minecraft:diamond_ore", 32);

// 检查状态
while (br.isActive()) {
    mc.waitTick(5);
}

mc.log("Mining complete! Going home...");
br.home();
mc.log("Welcome home!");
```
