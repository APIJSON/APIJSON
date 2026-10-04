
<h1 align="center" style="text-align:center;">
  APIJSON 规划及路线图
</h1>


### 新增功能
部分功能描述可在 [APIAuto](https://github.com/TommyLemon/APIAuto) 上查看 <br />
账号 13000002020 密码 123456 <br />
http://apijson.cn/api <br />

##### 基本原则
1.一定要有相关的应用场景，不能是伪需求，最好举例说明 <br />
2.能用 远程函数 或者 @raw 等现有功能很好地实现的，都不新增，避免冗余 <br />
3.不影响现有功能的使用，不能让现有功能不可用或者使用更复杂 <br />

#### 新增支持假删除
20230106 更新：已支持，感谢 @cloudAndMonkey 的贡献 <br />
https://github.com/Tencent/APIJSON/pull/493

一般对于互联网项目，数据非常重要，基本不会物理删除， <br />
只是用 is_deleted 等字段来标记已删除，然后 CRUD 时默认过滤已标记的记录。 <br />
这个功能非常必要，可以通过重写 SQLConfig.isFakeDelete() 来标记， <br />
然后如果 true，则把 DELETE 改为 PUT 并且通过重写 <br />
SQLConfig.onFakeDelete(Map<String, Object> map) 来新增条件： <br />
GET: map.put("deleted", 0) <br />
PUT:  map.put("deleted", 0) <br />
DELETE:  map.put("deleted", 1) <br />
POST:  用不上，不处理 <br />
当然也可以再加一个删除时间 deletedTime 之类的。 <br />
 <br />
还可以在 apijson-framework 层支持 Access 表新增字段 deletedKey, deletedValue, notDeletedValue， <br />
然后读表自动配置是否为假删除 StringUtil.isNotEmpty(deletedKey, true) ，是假删除时 put 相应键值对。 <br />
 
#### 新增支持 @having& 
20220328 更新：5.0.0 已支持 <br />
https://github.com/Tencent/APIJSON/releases/tag/5.0.0

来实现内部条件 AND 连接，原来的 @having 由 AND 连接变为 OR 连接，保持 横或纵与 的统一规则。<br />
@having! 必须性不大，可通过反转内部条件来实现，但如果实现简单、且不影响原来的功能，则可以顺便加上。<br />

#### 新增支持 @column!
20210415 更新：已提供字段插件 [apijson-column](https://github.com/APIJSON/apijson-column)，支持 字段名映射 和 !key 反选字段。

这个只在 [apijson-framework](https://github.com/APIJSON/apijson-framework) 支持，需要配置每个接口版本、每张表所拥有的全部字段，然后排除掉 @column! 的。<br />
可新增一个 VersionedColumn 表记录来代替 HashMap 代码配置。<br />
需要注意的是，可能前端传参里既有 @column 又有 @column! ，碰到这种情况：<br />
如果没有重合字段就忽略 @column! ，只让 @column 生效；<br />
如果有有重合字段，则抛异常，转为错误码和错误信息返回。<br />

#### 新增支持 TSQL 的 @explain
20220809 更新：已支持 Oracle 的 EXPLAIN，感谢 @ifooling 的贡献 <br />
https://github.com/Tencent/APIJSON/pull/434

目前 APIJSON 支持 [Oracle](https://github.com/APIJSON/APIJSON-Demo/tree/master/Oracle), [SQL Server](https://github.com/APIJSON/APIJSON-Demo/tree/master/SQLServer), DB2 这 3 种 TSQL 数据库。<br />
但是 "@explain": true 使用的是 SET STATISTICS PROFILE ON(具体见 [AbstractSQLConfig](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractSQLConfig.java) 和 [AbstrctSQLExecutor](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstrctSQLExecutor.java))  <br />
执行后居然是 SELECT 查到的放在默认的 ResultSet，性能分析放在 moreResult，<br />
因为这个问题目前以上两个数据库的性能分析 @explain 实际并不可用，需要改用其它方式或解决现有方式的 bug。<br />

#### 新增支持 page 从 1 开始
目前只能从 0 开始，实际使用 1 更广泛，而且这方面用户习惯很强，支持它成本也不高。 <br />
[Parser](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/Parser.java) 新增 DEFAULT_QUERY_PAGE 和 getDefaultQueryPage， <br />
与 DEFAULT_QUERY_COUNT 和 getDefaultQueryCount 统一， <br />
方便前端直接用页码的值传参，以及 info.page 的值来渲染页码。 <br />
建议在 [AbstractParser](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractParser.java)，[AbstractSQLConfig](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractSQLConfig.java) 用到 page 的地方判断 getDefaultQueryPage，做兼容处理。 <br />

#### 新增支持分布式

 "@url": "http://apijson.cn:8080/get"
```js
{
    "User": {
        "@url": "http://apijson.cn:8080/get"  //转发给其它服务器执行
    },
    "[]": {
        "Comment": {
            "userId@": "User/id"
        }
    },
    "@explain": true
}
```
考虑到引用赋值的依赖关系要保持先后顺序，必须同步阻塞处理。<br />
或许可以加一个 ~ 前缀表示异步？例如 "@url": "\~http://apijson.cn:8080/get"，由调用方保证没有被下方对象依赖。<br />


#### 新增支持 Union

虽然可以通过 INNER JOIN + 条件 OR 连接来替代它的功能，但没法达到它能用索引的性能。<br />
支持 UNION 很有必要，但 UNION ALL 几乎没有需求，如果实现简单、且不影响原来的功能，则可以顺便加上。 <br />

"@union": Integer  // 0 - 不使用，1 - UNION，2 - UNION ALL
```js
{
    "[]": {
        "User": {
            "name~": "a",
            "tag~": "a",
            "@combine": "name~,tag~",    
            "@union": 1  //将 @combine 中的 N 个 OR 连接字段用 UNION 替换，原本一条 SQL 需要拆分成 N 条 SQL 来 UNION 
        }
    },
    "@explain": true
}
```
生成的 SQL
```sql
SELECT * FROM `sys`.`apijson_user` WHERE ( (`name` REGEXP BINARY 'a') OR (`tag` REGEXP BINARY 'a') ) LIMIT 10 OFFSET 0
```
需要变为
```sql
SELECT * FROM `sys`.`apijson_user` WHERE ( (`name` REGEXP BINARY 'a') )
UNION
SELECT * FROM `sys`.`apijson_user` WHERE ( (`tag` REGEXP BINARY 'a') )
LIMIT 10 OFFSET 0
```


#### 新增支持 With
20221126 更新：已支持，感谢 @cloudAndMonkey 的贡献 <br /> 
https://github.com/Tencent/APIJSON/pull/481

可以减少子查询执行次数，提高性能。
```js
{   //看看关注的人最近有什么动态（分享、评论）
    "sql@": {
        "with": true,  //生成 WITH(SELECT id ...) AS `sql`
        "from": "User",
        "User": {
            "@column": "id",
            "@role": "CONTACT"
        }
    },
    "Moment[]": {
        "Moment": {
            "userId{}@": "sql",
            "@order": "date-"
        }
    },
    "Comment[]": {
        "Comment": {
            "userId{}@": "sql",
            "@order": "date-"
        }
    },
    "@explain": true
}
```
生成的 SQL
```sql
SELECT * FROM `sys`.`Moment` WHERE ( (`userId` IN (SELECT `id` FROM `sys`.`User` WHERE `id` IN($contactIdList)) ) ) ORDER BY `date` DESC LIMIT 10 OFFSET 0
```
和
```sql
SELECT * FROM `sys`.`Moment` WHERE ( (`userId` IN (SELECT `id` FROM `sys`.`User` WHERE `id` IN($contactIdList)) ) ) ORDER BY `date` DESC LIMIT 10 OFFSET 0
```

需要变为
```sql
WITH (SELECT `id` FROM `sys`.`User` WHERE `id` IN($contactIdList)) AS `sql`
SELECT * FROM `sys`.`Moment` WHERE ( (`userId` IN `sql` ) ) ORDER BY `date` DESC LIMIT 10 OFFSET 0
```
和
```sql
WITH (SELECT `id` FROM `sys`.`User` WHERE `id` IN($contactIdList)) AS `sql`
SELECT * FROM `sys`.`Comment` WHERE ( (`userId` IN `sql` ) ) ORDER BY `date` DESC LIMIT 10 OFFSET 0
```


#### 新增支持 Case
【更新：不用实现了，直接按 SQL 的语法写 CASE WHEN，然后用 @raw】
实现远程函数也不方便的 SQL 内字段转换 CASE WHEN THEN ，<br />
暂时还没有想好如何设计。如果是 SQL 原来的写法，则有点繁琐。<br />


#### 新增支持 id 与其它字段同时作为增删改条件
AbstractVerifier.IS_UPDATE_MUST_HAVE_ID_CONDITION = false <br />
就同时支持 id、其它条件删除 <br />
https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractVerifier.java#L84-L86

但因为 Operation 没有 AT_LEAST_ONE/ANY_ONE 这样的操作， <br />
所以如果只配置一条规则，只能允许 MUST 配置传一种条件，不能单独传 id/其它字段。 <br />
 <br />
如果都传了，因为 id 强制作为 AND 条件，所以不能和其它条件 OR， <br />
可以配置两条不同规则，用不同的 tag 对应使用不同的条件。 <br />
 <br />
method: DELETE <br />

通过 id 删除 
```
tag: Comment-by-id // 当然写成 Comment:id 等其它任何不符合表名格式的名称都可
structure: ... "MUST":"id" ...
```
 <br />
 
通过 date 条件删除 
```
tag: Comment-by-date
structure: ... "MUST":"date" ...
```

 <br />
如果想只配置一条规则，则 Operation 加上 AT_LEAST_ONE/ANY_ONE ，然后配置 

```
tag: Comment
structure: ... "AT_LEAST_ONE":"id,date" ... // 至少传其中一个
```

或 

```
tag: Comment
structure: ... "ANY_ONE":"id,date" ... // 必须传其中一个，不能同时传 2 个以上
```
 <br />
 
AT_LEAST_ONE/ANY_ONE 其中一个也可以通过扩展 MUST 来实现（目前看这种方式更好） <br />
"MUST":"id | date,其它" 通过 | 或来表示其中任何一个，注意左右一定要各有一个空格，因为可能有 "name|$" "id|{}" 等包含 "|" 的 key <br />
https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/Operation.java

 <br />
还可以设置更复杂的表达方式

```
"MUST":"1:id | date,其它" // id, date 必须传其中一个，且不能多传 <br />
"MUST":">=2:id | momentId|{} | date>=,其它" // id, momentId|{}, date>= 必须至少其中 2 个
"MUST":"2+:id | momentId|{} | date>=,其它" // id, momentId|{}, date>= 必须至少其中 2 个，替代 >= 2，更方便解析，并且不用考虑 >1, != 2 等其它各种不等式
"MUST":"2-:id | momentId|{} | date>=,其它" // id, momentId|{}, date>= 最多传其中 2 个，替代 <= 2
```

 <br />
这样的话就不用加 Operation 了，不过 AbstractVerifier 仍然要处理下 REFUSE 和 MUST 的互斥关系 <br />
https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractVerifier.java#L1012-L1042
<img width="1117" alt="image" src="https://user-images.githubusercontent.com/5738175/211016885-9e752b6c-94e5-46c0-b87d-a7be68387a9f.png">

##### 需求来源及具体讨论
https://github.com/Tencent/APIJSON/pull/493#issuecomment-1373376359

#### ...  //欢迎补充



### 强化安全
APIJSON 提供了各种安全机制，可在目前的基础上新增或改进。

20220504 更新：新增插件 apijson-router，可控地对公网暴露类 RESTful 简单接口，内部转成 APIJSON 格式请求来执行。 <br />
https://github.com/APIJSON/apijson-router

#### 防越权操作

目前有 RBAC 自动化权限管理。<br />
APIJSONORM 提供 [@MethodAccess](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/MethodAccess.java) 注解来配置 <br />
[apijson-framework](https://github.com/APIJSON/apijson-framework) 则使用 [Access 表](https://github.com/APIJSON/APIJSON-Demo/blob/master/MySQL/single/sys_Access.sql) 记录来配置 <br />
在 [AbstractVerifier](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractVerifier.java) 中，假定真实、强制匹配。 <br />


#### 防 SQL 注入

目前有 预编译 + 白名单 校验机制。具体见 [AbstractSQLExecutor](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractSQLExecutor.java)  和 [AbstractSQLConfig](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractSQLConfig.java) 。 <br />

#### 防恶意请求

目前有限流机制，getMaxQueryCount, getMaxUpdateCount, getMaxObjectCount, getMaxSQLCount, getMaxQueryDepth 等。 <br />
https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/Parser.java <br />


#### ...  //欢迎补充


### 提高性能
20200205 更新：最近的及次大幅提升性能相关优化及 Release <br />
[新增支持 ClickHouse、窗口函数 OVER、反引号 `key`、单引号 'value'；大幅提升单表数组查询性能](https://github.com/Tencent/APIJSON/releases/tag/4.8.0) <br />
[4.6.0【性能】大幅提升数组内主表查询性能](https://github.com/Tencent/APIJSON/releases/tag/4.6.0) <br />
[4.4.5【性能】大幅提升增删改的性能](https://github.com/Tencent/APIJSON/releases/tag/4.4.5) <br />

#### 解析 JSON

优化 [AbstractParser](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractParser.java) 和 [AbstractObjectParser](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractObjectParser.java) 解析 JSON 性能。 <br />

#### 封装 JSON

优化 [AbstractSQLExecutor](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractSQLExecutor.java) 封装 JSON 性能。 <br />

#### 拼接 SQL

优化 [AbstractSQLConfig](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractSQLConfig.java) 拼接 SQL 性能。<br />
[完善功能](https://github.com/Tencent/APIJSON/blob/master/Roadmap.md#%E5%AE%8C%E5%96%84%E5%8A%9F%E8%83%BD) 中 Union 和 With 也是优化 SQL 性能的方式。 <br />

#### 读写缓存
20230105 更新：新增 Redis 缓存 Demo
https://github.com/APIJSON/APIJSON-Demo/commit/060a10e56818b31ab332770815467af9f052c261

在 [AbstractParser](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractParser.java) 使用了 HashMap<String, JSONObject> queryResultMap 存已解析的对象、总数等数据。<br />
在 [AbstractSQLExecutor](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM/src/main/java/apijson/orm/AbstractSQLExecutor.java) 使用了 HashMap<String, JSONObject> cacheMap 存已通过 SQL 查出的结果集。<br />

#### ...  //欢迎补充



### 增强稳定
APIJSON 代码经过商业分析软件 [源伞Pinpoint](https://www.sourcebrella.com/) 的质检，报告说明 APIJSON 非常可靠。<br />
https://github.com/Tencent/APIJSON/issues/48 <br />
但我们需要再接再厉，尽可能做到 99.999% 可靠度，降低使用风险，让用户放心和安心。 <br />

20200205 更新：已经解决了 [源伞科技](https://www.sourcebrella.com) 以上报告中的大部分问题 及 [奇安信代码卫士](https://github.com/QiAnXinCodeSafe) 发现的部分问题
https://github.com/Tencent/APIJSON/issues/created_by/QiAnXinCodeSafe

#### 减少 Bug

##### [APIAuto](https://github.com/TommyLemon/APIAuto) 上统计的 bug
账号 13000002000 密码 123456 <br />
http://apijson.cn/api <br />

##### 其它发现的 Bug
https://github.com/Tencent/APIJSON/issues?q=is%3Aissue+is%3Aopen+label%3Abug <br />

#### 完善测试

##### 在 APIAuto-机器学习自动化接口管理平台 上传更多、更全面、更细致的测试用例、动态参数等
http://apijson.cn/api <br />

##### 在 UnitAuto-机器学习自动化单元测试平台 上传更多、更全面、更细致的测试用例、动态参数等
http://apijson.cn/unit <br />


### 完善文档
20211112 更新：已在官网部署文档 http://apijson.cn/doc/zh
20200205 更新：最近完善及更新了通用文档、上手文档、图文入门文档等，还对首页引导文档加了导航目录
https://github.com/Tencent/APIJSON/blob/master/Navigation.md

#### 中文文档

##### 通用文档
https://github.com/Tencent/APIJSON/blob/master/Document.md <br />

##### 配置与部署
https://github.com/APIJSON/APIJSON-Demo/tree/master/APIJSON-Java-Server <br />

##### ...  //欢迎补充


#### English Document

Translation for Chinese document. <br />
https://github.com/Tencent/APIJSON/blob/master/README-English.md <br />
https://github.com/Tencent/APIJSON/blob/master/Document-English.md <br />
https://github.com/ruoranw/APIJSONdocs <br />



### 丰富周边
#### 新增或完善各种语言 ORM 库

Go, Node(js/ts), C#, PHP, Kotlin, Ruby 等。<br />
https://github.com/Tencent/APIJSON#%E7%94%9F%E6%80%81%E9%A1%B9%E7%9B%AE <br />

#### 新增或完善 Demo

JavaScript 前端，TypeScript 前端，微信等小程序，<br />
Android 客户端，iOS 客户端，C# 游戏客户端等。<br />
Java, C#, PHP, Node, Python 等后端 Demo 及数据。<br />
https://github.com/APIJSON/APIJSON-Demo <br />

#### 新增扩展
目前已有 apijson-column, apijson-router, apijson-milvus, apijson-mongodb, apijson-influxdb, apijson-cassandra, apijson-jackson, apijson-fastjson2, apijson-gson, apijson-query-spring-boot-starter, apijson-dynamic-datasource 等 10+ 插件

##### 1.基于或整合 [APIJSONORM](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM) 或 [apijson-framework](https://github.com/APIJSON/apijson-framework) 来实现的库/框架

##### 2.扩展 [APIJSONORM](https://github.com/Tencent/APIJSON/blob/master/APIJSONORM) 或 [apijson-framework](https://github.com/APIJSON/apijson-framework) 功能的插件
可以通过扩展对象关键词 @key，数组关键词 key，远程函数，重写部分方法等来实现。<br />

##### 3.前端/客户端 封装/解析 APIJSON 的库/框架
因为 APIJSON 基于 JSON，大部分情况下都可以直接用 fastjson 等 JSON 封装/解析库或其它工具等，<br />
只是 APIJSON 有部分功能需要在 key 里放 [], @ 等特殊符号，返回 [] 在某些情况下不方便解析，<br />
目前可使用 "format": true 让后端格式化后返回，但也会对服务器性能有一些损耗，<br />
如果 前端/客户端 有对应的格式化工具等（例如 [apijson-orm](https://github.com/APIJSON/apijson-orm) 可供 Android 使用 format），选择就会更多一些。<br />

##### ...  //欢迎补充

### 推广使用
#### 为 APIJSON 编写/发表 博客/文章/资讯 等

https://github.com/Tencent/APIJSON#%E7%9B%B8%E5%85%B3%E6%8E%A8%E8%8D%90

20200205 更新：最近首页相关推荐新增了 1 篇官方发的文章和 6 篇用户发的文章
https://github.com/Tencent/APIJSON/blob/master/README.md#%E7%9B%B8%E5%85%B3%E6%8E%A8%E8%8D%90

#### 登记正在使用 APIJSON 的公司或项目

https://github.com/Tencent/APIJSON/issues/73

#### 在社交技术群、论坛等聊天或评论时推荐 APIJSON

#### ...  //欢迎补充


