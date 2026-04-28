# DexKit KMP 包装层评估

## 背景

本文用于评估当前 `dexkit/` KMP 包装层与 vendored 上游 `dexkit/vendor/DexKit` 之间的关系。

目标不是要求当前包装层去做 1:1 镜像，而是把边界说清楚：

- 哪些部分已经形成了稳定包装
- 哪些部分看起来是有意裁剪
- 哪些部分可能值得后续补齐，否则容易出现能力表面不平整

本次评估范围：

- `dexkit/src/commonMain/kotlin/io/github/dexclub/dexkit/query`
- `dexkit/src/commonMain/kotlin/io/github/dexclub/dexkit/result`
- `dexkit/vendor/DexKit/dexkit/src/main/java/org/luckypray/dexkit/query`
- `dexkit/vendor/DexKit/dexkit/src/main/java/org/luckypray/dexkit/result`

当前工作区对应的上游修订为：`19277f5`

## 总结

当前包装层并没有在类型结构与能力层面完全对齐上游。

这本身不一定是问题，因为本仓库现在做的明显是“包装层”，不是“镜像层”。

更准确地说：

- `query/` 侧的主能力形状基本跟上了，但 API 形态已经是你们自己的
- `result/` 侧明显只是上游的一部分能力子集，也是当前差距最明显的区域

可以概括为：

- query 侧：大体已包
- result 侧：明显精简
- bridge 侧：已经暴露出一部分 richer 能力，但结果模型没有完全承接

## 1. 已形成稳定包装的部分

这些区域已经具备明确的包装设计，不需要为了“追上游”去改成结构镜像。

### 1.1 主查询入口类型

当前 KMP 包装层已经有：

- `FindClass`
- `FindMethod`
- `FindField`
- `BatchFindClassUsingStrings`
- `BatchFindMethodUsingStrings`

这些都能对应上游的主查询入口，已经构成了比较完整的 KMP 查询契约。

### 1.2 核心 matcher 体系

当前包装层已经有主要 matcher 家族：

- `ClassMatcher`
- `MethodMatcher`
- `FieldMatcher`
- `ParametersMatcher`
- `AnnotationsMatcher`
- `StringMatcher`
- `UsingFieldMatcher`
- 以及 `MatchType`、`OpCodeMatchType`、`RetentionPolicyType`、`UsingType` 等配套枚举/类型

从这个角度看，查询建模方向并没有偏离上游。

### 1.3 轻量可序列化 DTO 模型

当前结果模型是明确偏轻量、可序列化的：

- `ClassData`
- `MethodData`
- `FieldData`
- `ClassDataList`
- `MethodDataList`
- `FieldDataList`

它和上游 richer object graph 不同，但和“做包装层而不是做原样搬运”这一设计方向是自洽的。

### 1.4 查询形态已经是包装层自有设计

KMP 侧查询模型已经不是上游原样。

例如：

- KMP 的 `FindClass` 是 `@Serializable data class`
- 上游的 `FindClass` 是带 DSL/builder 行为的对象

这不是问题，反而说明包装层已经做出了自己的设计取舍。

## 2. 更像是有意裁剪的部分

这些差异更像是“主动简化”，而不是“明显漏了”。

### 2.1 不保留上游 builder / fluent API

上游有很多 fluent 方法，例如：

- `searchPackages(...)`
- `excludePackages(...)`
- `matcher(...)`
- `searchIn(...)`

当前包装层没有保留这些方法，而是使用纯数据对象承载查询。

如果包装层方向继续保持“数据模型优先”，这是完全可以接受的。

### 2.2 不保留上游 richer result object graph

上游结果对象里有很多关系型、派生型字段。

例如：

- `ClassData.superClass`
- `ClassData.interfaces`
- `ClassData.methods`
- `ClassData.fields`
- `ClassData.annotations`
- `MethodData.callers`
- `MethodData.invokes`
- `MethodData.usingStrings`
- `MethodData.usingFields`
- `MethodData.opCodes`
- `FieldData.annotations`
- `FieldData.readers`
- `FieldData.writers`

当前 KMP DTO 没有保留整套图结构。

如果目标就是稳定、轻量、可跨平台序列化，这种裁剪是说得通的。

### 2.3 不保留结果对象上的行为方法

上游结果对象本身还带一些“继续发起查询”的行为，例如：

- `ClassData.findMethod(...)`
- `ClassData.findField(...)`

包装层没有保留这一类 API。

这也符合“服务入口集中、结果对象尽量纯”的思路。

### 2.4 不追 utility holder 文件的镜像

上游有一些辅助文件，例如 `MatcherCollections.kt`。

当前包装层没有对应物。

只要这些文件没有影响到你们明确想暴露的能力，这种缺失不是问题。

## 3. 值得关注的缺口

这些部分不是简单“不同”，而是已经开始出现能力表面不平整的迹象。

### 3.1 query 侧已有 annotation 建模，但 result 侧没有 annotation 结果模型

当前 query 里已经有：

- `AnnotationMatcher`
- `AnnotationsMatcher`
- `AnnotationElementMatcher`
- `AnnotationEncodeValueMatcher`
- 以及相关枚举

但 result 侧没有对应 annotation 结果类型。

而上游有：

- `AnnotationData`
- `AnnotationElementData`
- `AnnotationEncodeArrayData`
- `AnnotationEncodeValue`

这会形成一个不对称：

- 查询已经能表达注解条件
- 结果却没有 wrapper-native 的注解结果模型

这是当前最值得关注的缺口之一。

### 3.2 `UsingFieldData` 已补齐为最小结果模型

这一点原先是缺口，但当前工作区已经补上了最小承接模型：

- `UsingFieldData`
- `FieldUsingType`

这意味着：

- query 侧的 `UsingFieldMatcher` 不再只是“能过滤”
- bridge 侧也可以稳定返回“某个方法使用了哪些字段”

但需要注意，这里目前仍然只是“最小结果模型补齐”，还没有继续扩到 CLI 命令面或更完整的 detail DTO。

### 3.3 bridge 能力已经比 DTO 模型更丰富

当前 wrapper bridge 已经暴露了：

- `getFieldReaders`
- `getFieldWriters`
- `getMethodCallers`
- `getMethodInvokes`

但 `FieldData` 和 `MethodData` 本身没有把这些关系建模为一等结果字段或相关结果类型。

这不一定是错，但它说明：

- bridge 表面已经在往 richer capability 走
- DTO 层没有同步长出来

如果这种情况继续增加，包装层会越来越不平整。

### 3.4 包装层命名与上游命名相近，但并不完全一致

例如：

- 包装层：`searchInClasses`
- 上游：`searchClasses` / `searchIn(...)`

这不影响正确性，但后面如果继续扩包装能力，最好有统一命名规则，不然会逐渐漂。

### 3.5 `DataCollections` 语义并没有和上游完全同构

上游有 `DataCollections.kt`。

当前包装层改成了：

- `ClassDataList`
- `MethodDataList`
- `FieldDataList`

这当然可以，但意味着集合语义已经归包装层自己所有。后面继续扩结果能力时，要意识到这里已经不是上游原样。

## 4. 建议的分类方式

下面是当前比较适合的归类。

### 4.1 建议继续保持为稳定包装

- 主查询入口类型
- 主要 matcher 树
- 可序列化 query DTO 形态
- 轻量基础结果 DTO
- 独立的 `*List` 集合包装

### 4.2 建议明确视为“有意裁剪”

- 上游 builder / fluent API
- 上游 richer object graph 结果字段
- 上游结果对象上的便利行为方法
- 对能力暴露没有实质影响的 utility holder 文件

### 4.3 建议继续跟踪、后续决定是否补齐

- annotation 结果 DTO
- bridge 能力与结果模型表达能力之间的一致性
- 包装层 query 命名规范

## 5. 建议结论

不建议去机械镜像上游。

更合理的做法是：

1. 继续把当前层定义为包装层。
2. 明确承认 `result/` 现在是一个精简子集。
3. 决定 annotation 与 field-usage 这两类 richer result 是否进入正式包装契约。
4. 避免 bridge 能力继续扩张，而 DTO 层一直不承接。

其中 `field-usage` 这一项，在当前工作区里已经迈出了第一步：

- 已补齐最小结果模型
- 已补齐 bridge 读取能力
- 尚未扩展到 `core/cli` 的命令与输出契约

如果你们的预期是：

- query 负责表达搜索意图
- result 保持轻量、跨平台、可传输

那当前方向是合理的。

如果你们的预期是：

- KMP 包装层最终也要暴露接近上游的语义丰富度

那当前 `result/` 明显还没有跟上。

从现状看，这套代码更接近前者，而不是后者。

## 6. 建议先做的决策

在继续新增包装类型之前，最好先明确一个策略：

- 策略 A：`result/` 继续保持轻量，不追上游 richer result graph
- 策略 B：`result/` 选择性扩充，至少补 annotation 与 field-usage 相关模型

如果没有这个决策，后面比较容易演变成一种混合状态：

- 一部分上游概念被 richly wrapped
- 一部分仍然是扁平 DTO
- 还有一部分只能通过 bridge helper 间接访问

真正的风险不是“没有镜像”，而是这种没有明确策略的混合态。
