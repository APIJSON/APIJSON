## 中文 | [English](/Document.md)

# APIJSON 通用文档 
本文是通用文档，只和 APIJSON 协议有关，和 C#, Go, Java, JavaScript, PHP, Python, TypeScript 等开发语言无关。 <br />
具体开发语言相关的 配置、运行、部署 等文档见各个相关项目的文档，可以在[首页](/README.md)点击对应语言的入口来查看。<br />
![](https://github.com/user-attachments/assets/cc085b42-8678-4fa0-8ccc-b59f8322f28c)

后端开发者可以先看 [图文入门教程1](http://apijson.cn/doc/zh/) 或 [图文入门教程2](https://hanxu2018.github.io/APIJSON-DOC/) （和本文档有出入的点以本文档为准。例如正则匹配 key? 已废弃，用 key~ 替代；例如 "@column":"store_id,sum(amt):totAmt" 中逗号 , 有误，应该用分号 ; 隔开 SQL 函数，改为 "@column":"store_id;sum(amt):totAmt"）

* ### [1.示例](#1)
* ### [2.对比传统方式](#2)
* [2.1 开发流程](#2.1)
* [2.2 前端请求](#2.2)
* [2.3 后端操作](#2.3)
* [2.4 前端解析](#2.4)
* [2.5 对应不同需求的请求](#2.5)
* [2.6 对应不同请求的结果](#2.6)
* ### [3.设计规范](#3)
* [3.1 操作方法](#3.1)
* [3.2 功能符](#3.2)


## <h2 id="1">1.示例<h2/>

#### 获取用户
请求：
<pre><code class="language-json">{
  "User":{
    "id":38710
  }
}
</code></pre>

[点击这里测试](http://apijson.cn:8080/get/{"User":{"id":38710}})

返回：
<pre><code class="language-json">{
  "User":{
    "id":38710,
    "sex":0,
    "name":"TommyLemon",
    "tag":"Android&Java",
    "head":"http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
    "date":1485948110000,
    "pictureList":[
      "http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
      "http://common.cnblogs.com/images/icon_weibo_24.png"
    ]
  },
  "code":200,
  "msg":"success"
}
</code></pre>

<p align="center" >
  <a >[GIF] APIJSON 各种单表对象查询：简单查询、统计、分组、排序、聚合、比较、筛选字段、字段别名 等</a>
</p> 
  
![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_single.gif)
  
<br />

#### 获取用户列表
请求：
<pre><code class="language-json">{
  "[]":{
    "count":3, //只要3个
    "User":{
      "@column":"id,name" //只要id,name这两个字段
    }
  }
}
</code></pre>

[点击这里测试](http://apijson.cn:8080/get/{"[]":{"count":3,"User":{"@column":"id,name"}}})

返回：
<pre><code class="language-json">{
  "[]":[
    {
      "User":{
        "id":38710,
        "name":"TommyLemon"
      }
    },
    {
      "User":{
        "id":70793,
        "name":"Strong"
      }
    },
    {
      "User":{
        "id":82001,
        "name":"Android"
      }
    }
  ],
  "code":200,
  "msg":"success"
}
</code></pre>

<p align="center" >
  <a >[GIF] APIJSON 各种单表数组查询：简单查询、统计、分组、排序、聚合、分页、比较、搜索、正则、条件组合 等</a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_array.gif)

<br />

#### 获取动态及发布者用户
请求：
<pre><code class="language-json">{
  "Moment":{
  },
  "User":{
    "id@":"Moment/userId"  //User.id = Moment.userId
  }
}
</code></pre>

[点击这里测试](http://apijson.cn:8080/get/{"Moment":{},"User":{"id@":"Moment%252FuserId"}})

返回：
<pre><code class="language-json">{
  "Moment":{
    "id":12,
    "userId":70793,
    "date":"2017-02-08 16:06:11.0",
    "content":"1111534034"
  },
  "User":{
    "id":70793,
    "sex":0,
    "name":"Strong",
    "tag":"djdj",
    "head":"http://static.oschina.net/uploads/user/585/1170143_50.jpg?t=1390226446000",
    "contactIdList":[
      38710,
      82002
    ],
    "date":"2017-02-01 19:21:50.0"
  },
  "code":200,
  "msg":"success"
}
</code></pre>
  
<br />

#### 获取类似微信朋友圈的动态列表
请求：
<pre><code class="language-json">{
  "[]":{                             //请求一个数组
    "page":0,                        //数组条件
    "count":2,
    "Moment":{                       //请求一个名为Moment的对象
      "content$":"%a%"               //对象条件，搜索content中包含a的动态
    },
    "User":{
      "id@":"/Moment/userId",  //User.id = Moment.userId  缺省引用赋值路径，从所处容器的父容器路径开始
      "@column":"id,name,head"       //指定返回字段
    },
    "Comment[]":{                    //请求一个名为Comment的数组，并去除Comment包装
      "count":2,
      "Comment":{
        "momentId@":"[]/Moment/id"   //Comment.momentId = Moment.id  完整引用赋值路径
      }
    }
  }
}
</code></pre>

[点击这里测试](http://apijson.cn:8080/get/{"[]":{"page":0,"count":2,"Moment":{"content$":"%2525a%2525"},"User":{"id@":"%252FMoment%252FuserId","@column":"id,name,head"},"Comment[]":{"count":2,"Comment":{"momentId@":"[]%252FMoment%252Fid"}}}})

返回：
<pre><code class="language-json">{
  "[]":[
    {
      "Moment":{
        "id":15,
        "userId":70793,
        "date":1486541171000,
        "content":"APIJSON is a JSON Transmission Protocol…",
        "praiseUserIdList":[
          82055,
          82002,
          82001
        ],
        "pictureList":[
          "http://static.oschina.net/uploads/user/1218/2437072_100.jpg?t=1461076033000",
          "http://common.cnblogs.com/images/icon_weibo_24.png"
        ]
      },
      "User":{
        "id":70793,
        "name":"Strong",
        "head":"http://static.oschina.net/uploads/user/585/1170143_50.jpg?t=1390226446000"
      },
      "Comment[]":[
        {
          "id":176,
          "toId":166,
          "userId":38710,
          "momentId":15,
          "date":1490444883000,
          "content":"thank you"
        },
        {
          "id":1490863469638,
          "toId":0,
          "userId":82002,
          "momentId":15,
          "date":1490863469000,
          "content":"Just do it"
        }
      ]
    },
    {
      "Moment":{
        "id":58,
        "userId":90814,
        "date":1485947671000,
        "content":"This is a Content...-435",
        "praiseUserIdList":[
          38710,
          82003,
          82005,
          93793,
          82006,
          82044,
          82001
        ],
        "pictureList":[
          "http://static.oschina.net/uploads/img/201604/22172507_aMmH.jpg"
        ]
      },
      "User":{
        "id":90814,
        "name":7,
        "head":"http://static.oschina.net/uploads/user/51/102723_50.jpg?t=1449212504000"
      },
      "Comment[]":[
        {
          "id":13,
          "toId":0,
          "userId":82005,
          "momentId":58,
          "date":1485948050000,
          "content":"This is a Content...-13"
        },
        {
          "id":77,
          "toId":13,
          "userId":93793,
          "momentId":58,
          "date":1485948050000,
          "content":"This is a Content...-77"
        }
      ]
    }
  ],
  "code":200,
  "msg":"success"
}
</code></pre>

<p align="center" >
  <a >[GIF] APIJSON 各种多表关联查询：一对一、一对多、多对一、各种条件 等</a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_associate.gif)

<br />
  
<p align="center" >
  <a >[GIF] APIJSON 各种 JOIN：< LEFT JOIN, & INNER JOIN 等</a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_join.gif)
  
<br />
  
<p align="center" >
  <a >[GIF] APIJSON 各种子查询：@from@ FROM, key@ =, key>@ >, key{}@ IN, key}{@ EXISTS 等 </a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_subquery.gif)
    
<br />
    
<p align="center" >
  <a >[GIF] APIJSON 部分功能演示集合，由浅入深、由简单到复杂 </a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_summary.gif)

<br />

[在线测试](http://apijson.cn/api)

<br />
<br />
 
## <h2 id="2">2.对比传统RESTful方式<h2/>

### <h3 id="2.1">2.1 开发流程<h3/>
 开发流程 | 传统方式 | APIJSON
-------- | ------------ | ------------
 接口传输 | 等后端编辑接口，然后更新文档，前端再按照文档编辑请求和解析代码 | 前端按照自己的需求编辑请求和解析代码。<br />没有接口，更不需要文档！前端再也不用和后端沟通接口或文档问题了！
 兼容旧版 | 后端增加新接口，用v2表示第2版接口，然后更新文档 | 什么都不用做！
 
 <br />
 
### <h3 id="2.2">2.2 前端请求<h3/>
 前端请求 | 传统方式 | APIJSON
-------- | ------------ | ------------
 要求 | 前端按照文档在对应URL后面拼接键值对 | 前端按照自己的需求在固定URL后拼接JSON
 URL | 不同的请求对应不同的URL，基本上有多少个不同的请求就得有多少个接口URL | 相同的操作方法(增删改查)都用同一个URL，<br />大部分请求都用7个通用接口URL的其中一个
 键值对 | key=value | key:value
 结构 | 同一个URL内table_name只能有一个 <br /><br /> base_url/get/table_name?<br />key0=value0&key1=value1... | 同一个URL后TableName可传任意数量个 <br /><br /> base_url/get/<br />{<br > &nbsp;&nbsp; TableName0:{<br > &nbsp;&nbsp;&nbsp;&nbsp; key0:value0,<br > &nbsp;&nbsp;&nbsp;&nbsp; key1:value1,<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; TableName1:{<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; }<br > &nbsp;&nbsp; ...<br > }
 
 <br />
 
### <h3 id="2.3">2.3 后端操作<h3/>
 后端操作 | 传统方式 | APIJSON
-------- | ------------ | ------------
 解析和返回 | 取出键值对，把键值对作为条件用预设的的方式去查询数据库，最后封装JSON并返回给前端 | 把Parser#parse方法的返回值返回给前端就行
 返回JSON结构的设定方式 | 由后端设定，前端不能修改 | 由前端设定，后端不能修改
 
 <br />
 
### <h3 id="2.4">2.4 前端解析<h3/>
 前端解析 | 传统方式 | APIJSON
-------- | ------------ | ------------
 查看方式 | 查文档或问后端，或等请求成功后看日志 | 看请求就行，所求即所得，不用查、不用问、不用等。也可以等请求成功后看日志
 解析方法 | 用JSON解析器来解析JSONObject | 可以用JSONResponse解析JSONObject，或使用传统方式
 
 <br />
 
### <h3 id="2.5">2.5 前端对应不同需求的请求<h3/>
 前端的请求 | 传统方式 | APIJSON
-------- | ------------ | ------------
 User | base_url/get/user?id=38710 | [base_url/get/<br >{<br > &nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710<br > &nbsp;&nbsp; }<br >}](http://apijson.cn:8080/get/{"User":{"id":38710}})
 Moment和对应的User | 分两次请求<br />Moment: <br /> base_url/get/moment?userId=38710<br /><br />User: <br /> base_url/get/user?id=38710 | [base_url/get/<br >{<br > &nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "userId":38710<br > &nbsp;&nbsp; }, <br > &nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710<br > &nbsp;&nbsp; }<br >}](http://apijson.cn:8080/get/{"Moment":{"userId":38710},"User":{"id":38710}})
 User列表 | base_url/get/user/list?<br />page=0&count=3&sex=0 | [base_url/get/<br >{<br > &nbsp;&nbsp; "User[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "page":0,<br > &nbsp;&nbsp;&nbsp;&nbsp;  "count":3, <br > &nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "sex":0<br > &nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp; }<br >}](http://apijson.cn:8080/get/{"User[]":{"page":0,"count":3,"User":{"sex":0}}})
 Moment列表，<br />每个Moment包括<br />1.发布者User<br />2.前3条Comment | Moment里必须有<br />1.User对象<br >2.Comment数组<br /><br /> base_url/get/moment/list?<br />page=0&count=3&commentCount=3 | [base_url/get/<br >{<br > &nbsp;&nbsp; "[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "page":0, <br > &nbsp;&nbsp;&nbsp;&nbsp; "count":3, <br > &nbsp;&nbsp;&nbsp;&nbsp; "Moment":{}, <br > &nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id@":"/Moment/userId"<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "count":3,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "momentId@":"[]/Moment/id"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp; }<br >}](http://apijson.cn:8080/get/{"[]":{"page":0,"count":3,"Moment":{},"User":{"id@":"%252FMoment%252FuserId"},"Comment[]":{"count":3,"Comment":{"momentId@":"[]%252FMoment%252Fid"}}}})
 User发布的Moment列表，<br /> 每个Moment包括<br /> 1.发布者User<br /> 2.前3条Comment | 1.Moment里必须有User对象和Comment数组<br > 2.字段名必须查接口文档，例如评论数量字段名可能是<br /> commentCount,comment_count或者简写cmt_count等各种奇葩写法... <br /><br /> base_url/get/moment/list?<br />page=0&count=3<br />&commentCount=3&userId=38710 | 有以下几种方式:<br /><br /> ① 把以上请求里的<br >"Moment":{}, "User":{"id@":"/Moment/userId"}<br >改为<br >["Moment":{"userId":38710}, "User":{"id":38710}](http://apijson.cn:8080/get/{"[]":{"page":0,"count":3,"Moment":{"userId":38710},"User":{"id":38710},"Comment[]":{"count":3,"Comment":{"momentId@":"[]%252FMoment%252Fid"}}}}) <br /><br /> ② 或把User放在上面的最外层省去重复的User<br />[base_url/get/<br >{<br > &nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "page":0,<br > &nbsp;&nbsp;&nbsp;&nbsp; "count":3, <br > &nbsp;&nbsp;&nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "userId":38710<br > &nbsp;&nbsp;&nbsp;&nbsp; }, <br > &nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "count":3,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "momentId@":"[]/Moment/id"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp; }<br >}](http://apijson.cn:8080/get/{"User":{"id":38710},"[]":{"page":0,"count":3,"Moment":{"userId":38710},"Comment[]":{"count":3,"Comment":{"momentId@":"[]%252FMoment%252Fid"}}}})<br /><br /> ③ 如果User之前已经获取到了，还可以不传User来节省请求和返回数据的流量并提升速度<br />[base_url/get/<br >{<br > &nbsp;&nbsp; "[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "page":0,<br > &nbsp;&nbsp;&nbsp;&nbsp; "count":3, <br > &nbsp;&nbsp;&nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "userId":38710<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "count":3,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "momentId@":"[]/Moment/id"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp; }<br >}](http://apijson.cn:8080/get/{"[]":{"page":0,"count":3,"Moment":{"userId":38710},"Comment[]":{"count":3,"Comment":{"momentId@":"[]%252FMoment%252Fid"}}}})
 
 <br />
 
### <h3 id="2.6">2.6 后端对应不同请求的返回结果<h3/>
 后端的返回结果 | 传统方式 | APIJSON
-------- | ------------ | ------------
 User | {<br > &nbsp;&nbsp; "data":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp; "name":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >} | {<br > &nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp; "name":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}
 Moment和对应的User | 分别返回两次请求的结果，获取到Moment后取出userId作为User的id条件去查询User <br /><br /> Moment: <br > {<br > &nbsp;&nbsp; "data":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >} <br /><br /> User: <br > {<br > &nbsp;&nbsp; "data":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp; "name":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >} | 一次性返回，没有传统方式导致的 长时间等待结果、两次结果间关联、线程多次切换 等问题 <br /><br /> {<br > &nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp; "name":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}
 User列表 | {<br > &nbsp;&nbsp; "data":[<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "name":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":82001,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >} | {<br > &nbsp;&nbsp; "User[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "name":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":82001,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}
 Moment列表，每个Moment包括发布者User和前3条Comment | Moment里必须有<br />1.User对象<br />2.Comment数组 <br /><br /> {<br > &nbsp;&nbsp; "data":[<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":301,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >} | 1.高灵活，可任意组合<br />2.低耦合，逻辑很清晰<br /><br />{<br > &nbsp;&nbsp; "[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":301,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}
 User发布的Moment列表，每个Moment包括发布者User和前3条Comment | 1.大量重复User，浪费流量和服务器性能<br />2.优化很繁琐，需要后端扩展接口、写好文档，前端/前端再配合优化<br /><br />{<br > &nbsp;&nbsp; "data":[<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "name":"Tommy"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":470,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "name":"Tommy"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":511,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "name":"Tommy"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":595,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "name":"Tommy"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >} | 以上不同请求方式的结果:<br /><br /> ① 常规请求 <br > {<br > &nbsp;&nbsp; "[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "name":"Tommy"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}<br /><br /> ② 省去重复的User <br > {<br > &nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp; "name":"Tommy",<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}<br /><br /> ③ 不查询已获取到的User <br > {<br > &nbsp;&nbsp; "[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp; {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "content":"xxx",<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":[<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; ]<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}


1.base_url指基地址，一般是顶级域名，其它分支url都是在base_url后扩展。如base_url:http://apijson.cn:8080/ ，对应的GET分支url:http://apijson.cn:8080/get/ 。下同。<br >
2.请求中的key或value任意一个为null值时，这个 key:value键值对 被视为无效。下同。<br >
3.请求中的 / 需要转义。JSONRequest.java已经用URLEncoder.encode转义，不需要再写；但如果是浏览器或Postman等直接输入url/request，需要把request中的所有 / 都改成 %252F 。下同。<br >
4.code，指返回结果中的状态码，200表示成功，其它都是错误码，值全部都是HTTP标准状态码。下同。<br >
5.msg，指返回结果中的状态信息，对成功结果或错误原因的详细说明。下同。<br >
6.code和msg总是在返回结果的同一层级成对出现。对所有请求的返回结果都会在最外层有一对总结式code和msg。下同。<br >
7.id等字段对应的值仅供说明，不一定是数据库里存在的，请求里用的是真实存在的值。下同。

<br />
<br />

## <h2 id="3">3.设计规范<h2/>

### <h3 id="3.1">3.1 操作方法<h3/>

 方法及说明 | URL | Request | Response
------------ | ------------ | ------------ | ------------
GET: <br > 普通获取数据，<br > 可用浏览器调试 | base_url/get/ | {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; … <br > &nbsp;&nbsp; }<br >} <br > {…}内为限制条件<br ><br > 例如获取一个 id = 235 的 Moment：<br > [{<br > &nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":235<br > &nbsp;&nbsp; }<br >}](http://apijson.cn/api/?url=http%3A%2F%2Fapijson.cn%3A8080%2Fget&type=JSON&json={"Moment"%3A{"id"%3A235}}) <br > 后端校验通过后自动解析为 SQL 并执行：<br >`SELECT * FROM Moment WHERE id=235 LIMIT 1` | {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; ...<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}<br >例如<br >{<br > &nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp; "userId":38710,<br > &nbsp;&nbsp;&nbsp;&nbsp; "content":"APIJSON is the real-time coding-free, powerful and secure ORM"<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br > }
HEAD: <br > 普通获取数量，<br > 可用浏览器调试 | base_url/head/ | {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; …<br > &nbsp;&nbsp; }<br > } <br > {…}内为限制条件 <br ><br > 例如获取一个 id = 38710 的 User 所发布的 Moment 总数：<br > [{<br > &nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "userId":38710<br > &nbsp;&nbsp; }<br >}](http://apijson.cn/api/?url=http%3A%2F%2Fapijson.cn%3A8080%2Fhead&type=JSON&json={"Moment"%3A{"userId"%3A38710}}) <br > 后端校验通过后自动解析为 SQL 并执行：<br >`SELECT count(*) FROM Moment WHERE userId=38710 LIMIT 1`  | {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br > &nbsp;&nbsp;&nbsp;&nbsp; "count":10<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >} <br > 例如<br >{<br > &nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br > &nbsp;&nbsp;&nbsp;&nbsp; "count":10<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp;  "msg":"success"<br >}
GETS: <br > 安全/私密获取数据，<br > 用于获取钱包等<br >对安全性要求高的数据 | base_url/gets/ | 最外层加一个 "tag":tag，例如 ["tag":"Privacy"](http://apijson.cn/api/?url=http%3A%2F%2Fapijson.cn%3A8080%2Fgets&type=JSON&json={"tag"%3A"Privacy","Privacy"%3A{"id"%3A82001}})，其它同GET | 同GET
HEADS: <br > 安全/私密获取数量，<br > 用于获取银行卡数量等<br >对安全性要求高的数据总数 | base_url/heads/ | 最外层加一个 "tag":tag，例如 ["tag":"Verify"](http://apijson.cn/api/?url=http%3A%2F%2Fapijson.cn%3A8080%2Fheads&type=JSON&json={"tag"%3A"Verify","Verify"%3A{"phone"%3A13000082001}})，其它同HEAD | 同HEAD
POST: <br > 新增数据 | base_url/post/ | 单个： <br > {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; …<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "tag":tag<br >} <br > {…}中id由后端生成，不能传 <br ><br >例如当前登录用户 38710 发布一个新 Comment：<br >[{<br > &nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "momentId":12,<br > &nbsp;&nbsp;&nbsp;&nbsp; "content":"APIJSON is the real-time coding-free, powerful and secure ORM" <br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "tag":"Comment"<br >}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fpost&type=JSON&json={"Comment":{"momentId":12,"content":"APIJSON%20is%20the%20Real-Time%20coding-free,%20powerful%20and%20secure%20ORM."},"tag":"Comment"}) <br > 后端校验通过后自动解析为 SQL 并执行：<br >`INSERT INTO Comment(userId,momentId,content) VALUES(38710,12,'APIJSON is the real-time coding-free, powerful and secure ORM')` <br >  <br > 批量： <br > {<br > &nbsp;&nbsp; TableName\[]:\[{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; …<br > &nbsp;&nbsp;&nbsp;&nbsp; }, {<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; …<br > &nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp;&nbsp;&nbsp; …<br > &nbsp;&nbsp; ],<br > &nbsp;&nbsp; "tag":tag<br >} <br > {…}中id由后端生成，不能传 <br ><br >例如当前登录用户 82001 发布 2 个 Comment：<br >[{<br > &nbsp;&nbsp; "Comment[]":[{<br > &nbsp;&nbsp;&nbsp;&nbsp; "momentId":12,<br > &nbsp;&nbsp;&nbsp;&nbsp; "content":"APIJSON is the real-time coding-free, powerful and secure ORM"<br > &nbsp;&nbsp;&nbsp;&nbsp; }, {<br > &nbsp;&nbsp;&nbsp;&nbsp; "momentId":15,<br > &nbsp;&nbsp;&nbsp;&nbsp; "content":"APIJSON is a JSON transmision protocol."<br > &nbsp;&nbsp; }],<br > &nbsp;&nbsp; "tag":"Comment:[]"<br >}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fpost&type=JSON&json={"Comment[]":[{"momentId":12,"content":"APIJSON%20is%20the%20Real-Time%20coding-free,%20powerful%20and%20secure%20ORM."},{"momentId":15,"content":"APIJSON%20is%20a%20JSON%20transmision%20protocol."}],"tag":"Comment:[]"})<br >  后端校验通过后自动解析为 SQL 并执行：<br >`INSERT INTO Comment(userId,momentId,content) VALUES(82001,12,'APIJSON is the real-time coding-free, powerful and secure ORM');`<br ><br >`INSERT INTO Comment(userId,momentId,content) VALUES(82001,15,'APIJSON is a JSON transmision protocol.');`   | 单个： <br > {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":38710<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}<br >例如<br >{<br > &nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":120<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >} <br > <br > 批量： <br > {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br > &nbsp;&nbsp;&nbsp;&nbsp; "count":5,<br > &nbsp;&nbsp;&nbsp;&nbsp; "id[]":[1, 2, 3, 4, 5]<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}<br >例如<br >{<br > &nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br > &nbsp;&nbsp;&nbsp;&nbsp; "count":2,<br > &nbsp;&nbsp;&nbsp;&nbsp; "id[]":\[1, 2]<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}
PUT: <br > 修改数据，<br > 只修改所传的字段 | base_url/put/ | {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":id,<br > &nbsp;&nbsp;&nbsp;&nbsp; …<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "tag":tag<br >} <br > {…} 中 id 或 id{} 至少传一个 <br ><br >例如当前登录用户 82001 修改 id = 235 的 Moment 的 content：<br >[{<br > &nbsp;&nbsp; "Moment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br > &nbsp;&nbsp;&nbsp;&nbsp; "content":"APIJSON is the real-time coding-free, powerful and secure ORM"<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "tag":"Moment"<br >}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fput&type=JSON&json={"Moment":{"id":235,"content":"APIJSON%20is%20the%20Real-Time%20coding-free,%20powerful%20and%20secure%20ORM."},"tag":"Moment"}) <br > 后端校验通过后自动解析为 SQL 并执行：<br >`UPDATE Moment SET content='APIJSON is the real-time coding-free, powerful and secure ORM' WHERE id=235 AND userId=82001 LIMIT 1` <br ><br > 批量除了 id{}:\[] 也可类似批量 POST，只是每个 {...} 里面都必须有 id。<br >"tag":"Comment[]" 对应对象 "Comment":{"id{}":[1,2,3]}，表示指定记录全部统一设置；<br >"tag":"Comment:[]" 多了冒号，对应数组 "Comment[]":[{"id":1},{"id":2},{"id":3}]，表示每项单独设置 | 同POST
DELETE: <br > 删除数据 | base_url/delete/ | {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id":id<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "tag":tag<br >} <br > {…} 中 id 或 id{} 至少传一个，一般只传 id 或 id{} <br ><br >例如当前登录用户 82001 批量删除 id = 100,110,120 的 Comment：<br >[{<br > &nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id{}":[100,110,120]<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "tag":"Comment[]"<br >}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fdelete&type=JSON&json={"Comment":{"id{}":[100,110,120]},"tag":"Comment[]"}) <br > 后端校验通过后自动解析为 SQL 并执行：<br >`DELETE FROM Comment WHERE id IN(100,110,120) AND userId=82001 LIMIT 3` | {<br > &nbsp;&nbsp; TableName:{<br > &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br > &nbsp;&nbsp;&nbsp;&nbsp; "id[]":[100,110,120]<br >&nbsp;&nbsp; &nbsp;&nbsp; "count":3<br > &nbsp;&nbsp; },<br > &nbsp;&nbsp; "code":200,<br > &nbsp;&nbsp; "msg":"success"<br >}<br >例如<br >{<br >&nbsp;&nbsp; "Comment":{<br >&nbsp;&nbsp; &nbsp;&nbsp; "code":200,<br >&nbsp;&nbsp; &nbsp;&nbsp; "msg":"success",<br >&nbsp;&nbsp; &nbsp;&nbsp; "id[]":[100,110,120],<br >&nbsp;&nbsp; &nbsp;&nbsp; "count":3<br >&nbsp;&nbsp; },<br >&nbsp;&nbsp; "code":200,<br >&nbsp;&nbsp; "msg":"success"<br >}
以上接口的简单形式: <br > base_url/{method}/{tag} |  GET: 普通获取数据 <br > base_url/get/{tag} <br ><br > HEAD: 普通获取数量 <br > base_url/head/{tag} <br ><br > GETS: 安全/私密获取数据 <br >  base_url/gets/{tag} <br ><br > HEADS: 安全/私密获取数量 <br > base_url/heads/{tag} <br ><br > POST: 新增数据 <br >  base_url/post/{tag} <br ><br > PUT: 修改数据  base_url/put/{tag} <br ><br > DELETE: 删除数据 <br > base_url/delete/{tag} | 例如安全/私密获取一个 id = 82001 的 Privacy：<br > [base_url/gets/Privacy/ <br > {"id":82001}](http://apijson.cn/api/?url=http%3A%2F%2Fapijson.cn%3A8080%2Fgets%2FPrivacy&type=JSON&json={"id"%3A82001}) <br > 相当于 <br > [base_url/gets/ <br >{"tag":"Privacy", "Privacy":{"id":82001}}](http://apijson.cn/api/?url=http%3A%2F%2Fapijson.cn%3A8080%2Fgets&type=JSON&json={"tag"%3A"Privacy","Privacy"%3A{"id"%3A82001}}) <br > <br > 例如批量修改 id = 114, 124 的 Comment 的 content：<br >[base_url/put/Comemnt[]/ <br > {<br > &nbsp;&nbsp; "id{}":[114,124],<br > &nbsp;&nbsp; "content":"test multi put"<br >}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fput%2FComment[]&type=JSON&json={"id{}"%3A[114,124],"content"%3A"test%20multi%20put"}) <br > 相当于 <br > [base_url/put/ <br > {<br > &nbsp;&nbsp; "tag":"Comment[]", <br > &nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "id{}":[114,124],<br > &nbsp;&nbsp;&nbsp;&nbsp; "content":"test multi put"<br > &nbsp;&nbsp; }<br >}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fput&type=JSON&json={"tag"%3A"Comment[]","Comment"%3A{"id{}"%3A[114,124],"content"%3A"test%20multi%20put"}})  | 同以上对应的方法

1.TableName指要查询的数据库表Table的名称字符串。第一个字符为大写字母，剩下的字符要符合英语字母、数字、下划线中的任何一种。对应的值的类型为JSONObject，结构是 {...}，里面放的是Table的字段(列名)。下同。<br >
2."tag":tag 后面的tag是非GET、HEAD请求中匹配请求的JSON结构的标识，一般是要查询的Table的名称，由后端Request表中指定。下同。<br >
3.GET、HEAD请求是开放请求，可任意组合任意嵌套。其它请求为受限制的安全/私密请求，对应的 方法（method）, 标识（tag）, 版本（version）, 结构（structure） 都必须和 后端Request表中所指定的 一一对应，否则请求将不被通过。version 不传、为 null 或 <=0 都会使用最高版本，传了其它有效值则会使用最接近它的最低版本。下同。<br >
4.GETS与GET、HEADS与HEAD分别为同一类型的操作方法，请求稍有不同但返回结果相同。下同。<br >
5.在HTTP通信中，自动化接口(get,gets,head,heads,post,put,delete) 全用HTTP POST请求。下同。<br >
6.所有JSONObject都视为容器(或者文件夹)，结构为 {...} ，里面可以放普通对象或子容器。下同。<br >
7.每个对象都有一个唯一的路径(或者叫地址)，假设对象名为refKey，则用 key0/key1/.../refKey 表示。下同。

<br >

### <h3 id="3.2">3.2 功能符<h3/>

#### KingbaseES 接入与迁移

- 使用 MySQL、Oracle 或 SQL Server 兼容模式时，将 `@database` 或后端 `DEFAULT_DATABASE` 配置为 `KINGBASE_AUTO`，并由应用提供运行时 Kingbase JDBC 驱动。APIJSON 使用 `SHOW database_mode` 识别模式，并缓存数据源的识别结果。
- 从 `KINGBASE_MYSQL`、`KINGBASE_ORACLE` 或 `KINGBASE_SQLSERVER` 直接配置迁移时，请改为 `KINGBASE_AUTO`。这三个标识仍可用于后端 `gainKingbaseExpectedDatabase()` 的期望模式校验，但不再作为直接配置值。原有 `KINGBASE` 入口保留旧行为，不自动探测模式。
- 自管数据源可覆盖 `openKingbaseModeConnection()`，返回可由探测器关闭的连接。探测连接和缓存键不能依赖已解析的 SQL 方言；如果同一数据源切换目标实例，请使用 `KINGBASE_MODE_RESOLVER.invalidate(config)` 清除旧的模式缓存。

默认 Maven 测试不需要 Kingbase 驱动。要单独验证 JDBC 驱动合约，可运行 `mvn -Pkingbase-driver-tests -Dtest=KingbaseTypeMappingTest test`，按需要通过 `-Dkingbase.jdbc.version` 指定可从 Maven 仓库获取的驱动版本。这不等同于实库测试。
 
 功能 | 键值对格式 | 使用示例
------------ | ------------ | ------------
 查询数组 | "key[]":{}，后面是 JSONObject，key 可省略。当 key 和里面的 Table 名相同时，Table 会被提取出来，即 {Table:{Content}} 会被转化为 {Content} | [{"User[]":{"User":{}}}](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{}}})，查询一个 User 数组。这里 key 和 Table 名都是 User，User 会被提取出来，即 {"User":{"id", ...}} 会被转化为 {"id", ...}，如果要进一步提取 User 中的 id，可以把 User[] 改为 User-id[]，其中 - 用来分隔路径中涉及的 key
 匹配选项范围 | "key{}":[]，后面是 JSONArray，作为 key 可取的值的选项 | ["id{}":[38710,82001,70793]](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"id{}":[38710,82001,70793]}}})，对应 SQL 是`id IN(38710,82001,70793)`，查询 id 符合 38710,82001,70793 中任意一个的一个 User 数组
 匹配条件范围 | "key{}":"条件0,条件1..."，条件为 SQL 表达式字符串，可进行数字比较运算等 | ["id{}":"<=80000,\>90000"](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"id{}":"<=80000,\>90000"}}})，对应 SQL 是`id<=80000 OR id>90000`，查询 id 符合 id\<=80000 \| id>90000 的一个 User 数组
 包含选项范围 | "key<\>":value  =>  "key<\>":[value]，key 对应值的类型必须为 JSONArray，value 值类型只能为 Boolean, Number, String 中的一种 |  ["contactIdList<\>":38710](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"contactIdList<\>":38710}}})，对应SQL是`json_contains(contactIdList,38710)`，查询 contactIdList 包含 38710 的一个 User 数组
 判断是否存在 | "key}{@":{<br /> &nbsp;&nbsp; "from":"Table",<br /> &nbsp;&nbsp; "Table":{ ... }<br />}<br />其中：<br />}{ 表示 EXISTS；<br /> key 用来标识是哪个判断；<br /> @ 后面是 子查询 对象，具体见下方 子查询 的说明。 | ["id}{@":{<br /> &nbsp;&nbsp; "from":"Comment",<br /> &nbsp;&nbsp; "Comment":{<br /> &nbsp;&nbsp;  &nbsp;&nbsp; "momentId":15 <br /> &nbsp;&nbsp; }<br />}](http://apijson.cn:8080/get/{"User":{"id}{@":{"from":"Comment","Comment":{"momentId":15}}}})<br /> WHERE EXISTS(SELECT * FROM Comment WHERE momentId=15)
 远程调用函数 | "key()":"函数表达式"，函数表达式为 function(key0,key1...)，会调用后端对应的函数 function(JSONObject request, String key0, String key1...)，实现 参数校验、数值计算、数据同步、消息推送、字段拼接、结构变换 等特定的业务逻辑处理，<br>可使用 - 和 + 表示优先级，解析 key-() > 解析当前对象 > 解析 key() > 解析子对象 > 解析 key+()  | ["isPraised()":"isContain(praiseUserIdList,userId)"](http://apijson.cn:8080/get/{"Moment":{"id":301,"isPraised()":"isContain(praiseUserIdList,userId)"}})，会调用远程函数 [boolean isContain(JSONObject request, String array, String value)](https://github.com/APIJSON/apijson-framework/blob/master/src/main/java/apijson/framework/APIJSONFunctionParser.java#L361-L374) ，然后变为 "isPraised":true 这种（假设点赞用户id列表包含了userId，即这个User点了赞）
 存储过程 | "@key()":"SQL函数表达式"，函数表达式为 <br />  function(key0,key1...) <br /> 会调用后端数据库对应的存储过程 SQL函数 <br /> function(String key0, String key1...) <br /> 除了参数会提前赋值，其它和 远程函数 一致 | ["@limit":10, <br /> "@offset":0, <br /> "@procedure()":"getCommentByUserId(id,@limit,@offset)"](http://apijson.cn:8080/get/{"User":{"@limit":10,"@offset":0,"@procedure()":"getCommentByUserId(id,@limit,@offset)"}}) <br /> 会转为 <br /> `getCommentByUserId(38710,10,0)` <br /> 来调用存储过程 SQL 函数 <br /> `getCommentByUserId(IN id bigint, IN limit int, IN offset int)` <br /> 然后变为 <br />"procedure":{<br /> &nbsp;&nbsp; "count":-1, <br /> &nbsp;&nbsp; "update":false, <br /> &nbsp;&nbsp; "list":[] <br /> } <br /> 其中 count 是指写操作影响记录行数，-1 表示不是写操作；update 是指是否为写操作（增删改）；list 为返回结果集
 引用赋值 | "key@":"key0/key1/.../refKey"，引用路径为用/分隔的字符串。以/开头的是缺省引用路径，从声明key所处容器的父容器路径开始；其它是完整引用路径，从最外层开始。<br /> 被引用的refKey必须在声明key的上面。如果对refKey的容器指定了返回字段，则被引用的refKey必须写在@column对应的值内，例如 "@column":"refKey,key1,..." | ["Moment":{<br /> &nbsp;&nbsp; "userId":38710<br />},<br />"User":{<br /> &nbsp;&nbsp; "id@":"/Moment/userId"<br />}](http://apijson.cn:8080/get/{"Moment":{"userId":38710},"User":{"id@":"%252FMoment%252FuserId"}})<br /> User内的id引用了与User同级的Moment内的userId，<br />即User.id = Moment.userId，请求完成后<br > "id@":"/Moment/userId" 会变成 "id":38710
 子查询 | "key@":{<br /> &nbsp;&nbsp; "range":"ALL", <br /> &nbsp;&nbsp; "from":"Table",<br /> &nbsp;&nbsp; "Table":{ ... }<br />}<br />其中：<br />range 可为 ALL,ANY；<br />from 为目标表 Table 的名称；<br />@ 后面的对象类似数组对象，可使用 count 和 join 等功能。 | ["id@":{<br /> &nbsp;&nbsp; "from":"Comment",<br /> &nbsp;&nbsp; "Comment":{<br /> &nbsp;&nbsp;  &nbsp;&nbsp; "@column":"min(userId)" <br /> &nbsp;&nbsp; }<br />}](http://apijson.cn:8080/get/{"User":{"id@":{"from":"Comment","Comment":{"@column":"min(userId)"}}}})<br /> WHERE id=(SELECT min(userId) FROM Comment)
 模糊搜索 | `"key$":"SQL搜索表达式"`  =>  `"key$":["SQL搜索表达式"]`，任意SQL搜索表达式字符串，如 %key%(包含key), key%(以key开始), %k%e%y%(包含字母k,e,y) 等，%表示任意字符 | ["name$":"%m%"](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"name$":"%2525m%2525"}}})，对应SQL是`name LIKE '%m%'`，查询name包含"m"的一个User数组
 正则匹配 | "key~":"正则表达式"  =>  "key~":["正则表达式"]，任意正则表达式字符串，如 ^[0-9]+$ ，*~ 忽略大小写，可用于高级搜索 | ["name~":"^[0-9]+$"](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"name~":"^[0-9]%252B$"}}})，对应SQL是`name REGEXP '^[0-9]+$'`，查询name中字符全为数字的一个User数组
 连续范围 | "key%":"start,end"  =>  "key%":["start,end"]，其中 start 和 end 都只能为 Boolean, Number, String 中的一种，如 "2017-01-01,2019-01-01" ，["1,90000", "82001,100000"] ，可用于连续范围内的筛选 | ["date%":"2017-10-01,2018-10-01"](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"date%2525":"2017-10-01,2018-10-01"}}})，对应SQL是`date BETWEEN '2017-10-01' AND '2018-10-01'`，查询在2017-10-01和2018-10-01期间注册的用户的一个User数组
 新建别名 | ① "name:alias"，name 映射为 alias，用 alias 替代 name，可用于 column,Table,SQL 函数 等，只用于 GET 类型、HEAD 类型的请求 <br /><br />② 函数调用映射<br />"@key": "fun:avg(id);keyA:(keyB)",<br />"fun>": 1,<br />"keyA": 1<br />其中 fun:fun(arg) 把 SQL 函数调用 fun(arg) 作为左侧表达式替代 fun，即 fun(arg) > 1；<br />keyA:(keyB) 表示将字段 keyA 重命名为 keyB，即实际 SQL 中为 keyB = 1，常用于重命名冲突的多条件同名字段。 | ① ["@column":"toId:parentId"](http://apijson.cn:8080/get/{"Comment":{"@column":"id,toId:parentId","id":51}})，对应 SQL 是 `toId AS parentId`，将查询的字段 toId 变为 parentId 返回<br /><br />② ["@key": "len:length(content);mid:(momentId)",<br />"len<=": 10,<br />"mid": 12,<br />"momentId": 15,<br />"@combine": "(len<= \\| mid) & momentId"](http://apijson.cn/api?type=JSON&json={%22Comment%22:{%22@key%22:%22len%3Alength(content)%3Bmid%3A(momentId)%22,%22len%3C=%22:10,%22mid%22:12,%22momentId%22:15,%22@combine%22:%22(len%3C%3D%20%7C%20mid)%20%26%20momentId%22}})<br />对应 SQL 是 `(length(content) <= 10 OR momentId = 12) AND momentId = 15`
 增加 或 扩展 | "key+":Object，Object的类型由key指定，且类型为Number,String,JSONArray中的一种。如 82001,"apijson",["url0","url1"] 等。只用于PUT请求 | "praiseUserIdList+":[82001]，对应SQL是`json_insert(praiseUserIdList,82001)`，添加一个点赞用户id，即这个用户点了赞
 减少 或 去除 | "key-":Object，与"key+"相反 | "balance-":100.00，对应SQL是`balance = balance - 100.00`，余额减少100.00，即花费了100元
 比较运算 | >, <, >=, <= 比较运算符，用于 <br />① 提供 "id{}":"<=90000" 这种条件范围的简化写法 <br /><br />② 实现子查询相关比较运算<br /><br />不支持 "key=":Object 和 "key!=":Object 这两种写法，直接用更简单的 "key":Object 和 "key!":Object 替代。 |  ① ["id<=":90000](http://apijson.cn:8080/get/{"[]":{"User":{"id<=":90000}}})，对应 SQL 是`id<=90000`，查询符合id<=90000的一个User数组<br /><br /> ② ["id>@":{<br /> &nbsp;&nbsp; "from":"Comment",<br /> &nbsp;&nbsp; "Comment":{<br /> &nbsp;&nbsp;  &nbsp;&nbsp; "@column":"min(userId)" <br /> &nbsp;&nbsp; }<br />}](http://apijson.cn:8080/get/{"User":{"id>@":{"from":"Comment","Comment":{"@column":"min(userId)"}}}})<br /> WHERE id>(SELECT min(userId) FROM Comment)
 逻辑运算 | &, \|, ! 逻辑运算符，对应数据库 SQL 中的 AND, OR, NOT。 <br />横或纵与：同一键值对的值内条件默认 \| 或连接，可以在 key 后加逻辑运算符来具体指定；不同键值对的条件默认 & 与连接，可以用下面说明的对象关键词 @combine 来具体指定。 <br /><br />① & 可用于 "key&{}":"条件"等<br /><br />② \| 可用于 "key\|{}":"条件", "key\|{}":[]等，一般可省略<br /><br />③ ! 可单独使用，如 "key!":Object，也可像 &,\| 一样配合其他功能符使用 <br /> "key!":null 无效，null 值会导致整个键值对被忽略解析，可以用 "key{}":"!=null" 替代，<br />"key":null 同理，用 "key{}":"=null" 替代。 |  ① ["id&{}":">80000,<=90000"](http://apijson.cn:8080/head/{"User":{"id&{}":">80000,<=90000"}})，对应SQL是`id>80000 AND id<=90000`，即id满足id>80000 & id<=90000<br /><br /> ② ["id\|{}":">90000,<=80000"](http://apijson.cn:8080/head/{"User":{"id\|{}":">90000,<=80000"}})，同 "id{}":">90000,<=80000"，对应 SQL 是`id>90000 OR id<=80000`，即 id 满足 id>90000 \| id<=80000<br /><br /> ③ ["id!{}":[82001,38710]](http://apijson.cn:8080/head/{"User":{"id!{}":[82001,38710]}})，对应 SQL 是`id NOT IN(82001,38710)`，即 id 满足 ! (id=82001 \| id=38710)，可过滤黑名单的消息
 数组关键词，可自定义 | "key":Object，key为 "[]":{} 中 {} 内的关键词，Object 的类型由 key 指定<br /><br />① "count":5，查询数量，0 表示最大值，默认值为 10，默认最大值为 100 <br /><br />② "page":1，查询页码，从 0 开始，默认值为 0，默认最大值为 100，一般和 count 一起用<br /><br />③ "query":2，查询内容<br />0-对象，1-总数和分页详情，2-数据、总数和分页详情<br />总数关键词为 total，分页详情关键词为 info，<br /> 它们都和 query 同级，通过引用赋值得到自定义 key:value 键值对，不传则返回默认键值对，例如  <br />"total@":"/[]/total", "info@":"/[]/info" <br />这里query及total仅为GET类型的请求提供方便，<br /> 一般可直接用HEAD类型的请求获取总数<br /><br />④ "compat": true <br />处理最外层查询字段有特殊处理的情况下 "query":2 返回查询总数不准确的问题：如DISTINCT去重、Aggregate 函数等<br /><br />⑤ "join":"&/Table0,\</Table1/key1@" 或 <br />"join":{<br /> &nbsp;&nbsp; "&/Table0":{},  // 支持 ON 多个字段关联,<br /> &nbsp;&nbsp; "\</Table1/key1@":{  // ON 只允许指定的 key1 关联<br /> &nbsp;&nbsp;&nbsp;&nbsp; "key0":value0, // 其它ON条件 <br /> &nbsp;&nbsp;&nbsp;&nbsp; "key2":value2,<br /> &nbsp;&nbsp;&nbsp;&nbsp; ... <br /> &nbsp;&nbsp;&nbsp;&nbsp; "@combine":"...", // 其它ON条件的组合方式 <br /> &nbsp;&nbsp;&nbsp;&nbsp; "@column":"...", // 外层 SELECT <br /> &nbsp;&nbsp;&nbsp;&nbsp; "@group":"...", // 外层 GROUP BY <br /> &nbsp;&nbsp;&nbsp;&nbsp; "@having":"..." // 外层 HAVING <br /> &nbsp;&nbsp; }<br />}<br />多表连接方式：<br />  "@" - APP JOIN <br /> "\<" - LEFT JOIN <br /> ">" - RIGHT JOIN <br /> "&" - INNER JOIN <br /> "\|" - FULL JOIN <br />  "!" - OUTER JOIN <br />  "*" - CROSS JOIN <br />  "^" - SIDE JOIN <br />  "(" - ANTI JOIN <br />  ")" - FOREIGN JOIN <br />其中 @ APP JOIN 为应用层连表，会从已查出的主表里取得所有副表 key@ 关联的主表内的 refKey 作为一个数组 refKeys: [value0, value1...]，然后把原来副表 count 次查询 key=$refKey 的 SQL 用 key IN($refKeys) 的方式合并为一条 SQL 来优化性能；<br /> 其它 JOIN 都是 SQL JOIN，具体功能和 MySQL,PostgreSQL 等数据库的 JOIN 一一对应 <br />`"join":"</ViceTable/key@",`<br />`"MainTable":{},`<br />`"ViceTable":{"key@":"/MainTable/refKey"}`<br />会对应生成 <br />`MainTable LEFT JOIN ViceTable` <br />`ON ViceTable.key=MainTable.refKey` AND 其它ON条件 <br /> 除了 = 等价关联，也支持 ! 不等关联、\> \< \>= \<= 等比较关联和 $ ~ {} <> 等其它复杂关联方式 <br /><br />⑥ "otherKey":Object，自定义关键词，名称和以上系统关键词不一样，且原样返回上传的值  | ① 查询User数组，最多5个：<br />["count":5](http://apijson.cn:8080/get/{"[]":{"count":5,"User":{}}})<br />对应 SQL 是`LIMIT 5` <br /><br /> ② 查询第3页的User数组，每页5个：<br />["count":5,<br />"page":3](http://apijson.cn:8080/get/{"[]":{"count":5,"page":3,"User":{}}})<br />对应 SQL 是`LIMIT 5 OFFSET 15` <br /><br /> ③ 查询User数组和对应的User总数：<br />["[]":{<br /> &nbsp;&nbsp; "query":2,<br /> &nbsp;&nbsp; "User":{}<br />},<br />"total@":"/[]/total", // 可省略<br />"info@":"/[]/info" // 可省略](http://apijson.cn:8080/get/{"[]":{"query":2,"count":5,"User":{}},"total@":"%252F[]%252Ftotal","info@":"%252F[]%252Finfo"})<br /> 返回的数据中，总数及分页详情结构为： <br />  "total":139, // 总数 <br /> "info":{ // 分页详情 <br /> &nbsp;&nbsp; "total":139, // 总数 <br /> &nbsp;&nbsp; "count":5, // 每页数量 <br /> &nbsp;&nbsp; "page":0, // 当前页码 <br /> &nbsp;&nbsp; "max":27, // 最大页码 <br /> &nbsp;&nbsp; "more":true, // 是否还有更多 <br /> &nbsp;&nbsp; "first":true, // 是否为首页 <br /> &nbsp;&nbsp; "last":false // 是否为尾页 <br />} <br /><br /> ④查询User数组ID唯一情况下的User总数：<br />["[]":{<br /> &nbsp;&nbsp; "query":2,<br /> &nbsp;&nbsp; "compat":"true",<br /> &nbsp;&nbsp; "User":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "@column":"DISTINCT id" <br /> &nbsp;&nbsp; }<br />}](http://apijson.cn:8080/get/{"[]":{"query":2,"count":5,"User":{}}})<br /> 返回的数据和结构同上 <br /><br /> ⑤ Moment INNER JOIN User LEFT JOIN Comment：<br />["[]":{<br /> &nbsp;&nbsp; "join":"&/User/id@,\</Comment",<br /> &nbsp;&nbsp; "Moment":{<br />&nbsp;&nbsp;&nbsp;&nbsp; "@group":"id" // 主副表不是一对一，要去除重复数据<br />&nbsp;&nbsp; },<br /> &nbsp;&nbsp; "User":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "name~":"t",<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id@":"/Moment/userId"<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "Comment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "momentId@":"/Moment/id"<br /> &nbsp;&nbsp; }<br />}](http://apijson.cn/api/?type=JSON&url=http://apijson.cn:8080/get&json=%7B%22%5B%5D%22:%7B%22count%22:5,%22join%22:%22%26%2FUser%2Fid@,%3C%2FComment%22,%22Moment%22:%7B%22@column%22:%22id,userId,content%22,%22@group%22:%22id%22%7D,%22User%22:%7B%22name~%22:%22t%22,%22id@%22:%22%2FMoment%2FuserId%22,%22@column%22:%22id,name,head%22%7D,%22Comment%22:%7B%22momentId@%22:%22%2FMoment%2Fid%22,%22@column%22:%22id,momentId,content%22%7D%7D%7D)<br /><br /> ⑥ 每一层都加当前用户名：<br />["User":{},<br />"[]":{<br /> &nbsp;&nbsp; "name@":"User/name", // 自定义关键词<br /> &nbsp;&nbsp; "Moment":{}<br />}](http://apijson.cn:8080/get/{"User":{},"[]":{"name@":"User%252Fname","Moment":{}}})
 对象关键词，可自定义 | "@key":Object，@key 为 Table:{} 中 {} 内的关键词，Object 的类型由 @key 指定<br /><br />① "@combine":"key0 \| (key1 & (key2 \| !key3))..."，条件组合方式，最终按 <br /> (其它key条件 AND 连接) AND (key0条件 OR (key1条件 AND (key2条件 OR (NOT key3条件)))) <br />这种方式连接，其中 "其它key" 是指与 @combine 在同一对象，且未被它声明的条件 key，默认都是 & 连接。注意不要缺少或多余任何一个空格。 <br /><br />② "@column":"column;function(arg)..."，返回字段<br /><br />③ "@order":"column0+,column1-..."，排序方式<br /><br />④ "@group":"column0,column1..."，分组方式。如果 @column 里声明了 Table 的 id，则 id 也必须在 @group 中声明；其它情况下必须满足至少一个条件:<br />1.分组的 key 在 @column 里声明<br />2.Table 主键在 @group 中声明 <br /><br />⑤ "@having":"function0(...)?value0;function1(...)?value1;function2(...)?value2..." // OR 连接，或 <br />"@having&":"function0(...)?value0;function1(...)?value1;function2(...)?value2..." // AND 连接，或 <br />"@having":{<br />&nbsp;&nbsp; "h0":"function0(...)?value0",<br />&nbsp;&nbsp; "h1":function1(...)?value1",<br />&nbsp;&nbsp; "h2":function2(...)?value2...",<br />&nbsp;&nbsp; "@combine":"h0 & (h1 \| !h2)"  // 任意组合，非必传<br />}<br />SQL 函数条件，一般和 @group 一起用，函数一般在 @column 里声明<br /><br />⑥ "@schema":"sys"，集合空间(数据库名/模式)，非默认的值可通过它来指定，可以在最外层作为全局默认配置<br /><br />⑦ "@database":"POSTGRESQL"，数据库类型，非默认的值可通过它来指定，可以在最外层作为全局默认配置<br /><br />⑧ "@datasource":"DRUID"，跨数据源，非默认的值可通过它来指定，可以在最外层作为全局默认配置<br /><br />⑨ "@json":"key0,key1..."，转为 JSON 格式返回，符合 JSONObject 则转为 {...}，符合 JSONArray 则转为 \[...] <br /><br />⑩ "@role":"OWNER"，来访角色，包括<br />UNKNOWN,LOGIN,CONTACT,CIRCLE,OWNER,ADMIN，<br />可以在最外层作为全局默认配置，<br />可自定义其它角色并重写 Verifier.verify 等相关方法来自定义校验 <br /><br />⑪ "@explain":true，性能分析，可以在最外层作为全局默认配置 <br /><br />⑫ "@raw":"key0,key1..."，其中 key0, key1 都对应有键值对<br /> "key0":"SQL片段或SQL片段的别名", <br /> "key1":"SQL片段或SQL片段的别名" <br /> 自定义原始SQL片段，可扩展嵌套SQL函数等复杂语句，必须是后端已配置的，只有其它功能符都做不到才考虑，谨慎使用，注意防 SQL 注入<br /><br />⑬ "@null":"key1,key2..."，空值键值对，自动插入 key1:null, key2:null ... 并作为有效键值对执行，作为条件时对应 SQL 是 `WHERE tag IS NULL`，作为值时对应 SQL 是 `SET tag = NULL`<br /><br />⑭ "@otherKey":Object，自定义关键词，名称和以上系统关键词不一样，且原样返回上传的值 | ① 搜索 name 或 tag 任何一个字段包含字符 a 的 User 列表：<br /> ["name~":"a",<br />"tag~":"a",<br />"@combine":"name~ \| tag~"](http://apijson.cn:8080/get/{"User[]":{"count":10,"User":{"@column":"id,name,tag","name~":"a","tag~":"a","@combine":"name~%20%7C%20tag~"}}}) <br />对应SQL是`name REGEXP 'a' OR tag REGEXP 'a'` <br /><br /> ② 只查询 id,sex,name 这几列并且请求结果也按照这个顺序：<br />["@column":"id,sex,name"](http://apijson.cn:8080/get/{"User":{"@column":"id,sex,name","id":38710}})<br />对应 SQL 是`SELECT id,sex,name` <br /><br /> ③ 查询按 name 降序、id 默认顺序 排序的 User 数组：<br />["@order":"name-,id"](http://apijson.cn:8080/get/{"[]":{"count":10,"User":{"@column":"name,id","@order":"name-,id"}}})<br />对应 SQL 是`ORDER BY name DESC,id` <br /><br /> ④ 查询按 userId 分组的 Moment 数组：<br />["@group":"userId,id"](http://apijson.cn:8080/get/{"[]":{"count":10,"Moment":%7B"@column":"userId,id","@group":"userId,id"}}})<br />对应 SQL 是`GROUP BY userId,id` <br /><br /> ⑤ 查询 按 userId 分组、id 最大值>=100 的 Moment 数组：<br />["@column":"userId;max(id)",<br />"@group":"userId",<br />"@having":"max(id)>=100"](http://apijson.cn:8080/get/{"[]":{"count":10,"Moment":{"@column":"userId%253Bmax(id)","@group":"userId","@having":"max(id)>=100"}}})<br />对应 SQL 是`SELECT userId,max(id) ... GROUP BY userId HAVING max(id)>=100` <br />还可以指定函数返回名：<br />["@column":"userId;max(id):maxId",<br />"@group":"userId",<br />"@having":"(maxId)>=100"](http://apijson.cn:8080/get/{"[]":{"count":10,"Moment":{"@column":"userId%253Bmax(id):maxId","@group":"userId","@having":"(maxId)>=100"}}})<br />对应 SQL 是`SELECT userId,max(id) AS maxId ... GROUP BY userId HAVING (maxId)>=100` <br /><br /> ⑥ 查询 sys 内的 User 表：<br />["@schema":"sys"](http://apijson.cn:8080/get/{"User":{"@schema":"sys"}})<br />对应 SQL 是`FROM sys.User` <br /><br /> ⑦ 查询 PostgreSQL 数据库的 User 表：<br />["@database":"POSTGRESQL"](http://apijson.cn:8080/get/{"User":{"@database":"POSTGRESQL","@explain":true}})<br /><br /> ⑧ 使用 Druid 连接池查询 User 表：<br />["@datasource":"DRUID"](http://apijson.cn:8080/get/{"User":{"@datasource":"DRUID"}})<br /><br /> ⑨ 将 VARCHAR 字符串字段 get 转为 JSONArray 返回：<br />["@json":"get"](http://apijson.cn:8080/get/{"Access":{"@json":"get"}})<br /><br /> ⑩ 查询当前用户的动态：<br />["@role":"OWNER"](http://apijson.cn:8080/get/{"[]":{"Moment":{"@role":"OWNER"}}})<br /><br /> ⑪ 开启性能分析：<br />["@explain":true](http://apijson.cn:8080/get/{"[]":{"Moment":{"@explain":true}}})<br />对应 SQL 是`EXPLAIN` <br /><br /> ⑫ 统计最近一周偶数 userId 的数量<br />["@column":"date;left(date,10):day;sum(if(userId%2=0,1,0))",<br />"@group":"day",<br />"@having":"to_days(now())-to_days(\`date\`)<=7",<br />"@raw":"@column,@having"](http://apijson.cn:8080/get/{"[]":{"Moment":{"@column":"date%3bleft(date,10):day%3bsum(if(userId%252=0,1,0))","@group":"day","@having":"to_days(now())-to_days(\`date\`)<=7","@raw":"@column,@having"}}})<br />对应 SQL 是``SELECT date, left(date,10) AS day, sum(if(userId%2=0,1,0)) ... GROUP BY day HAVING to_days(now())-to_days(`date`)<=7`` <br /><br /> ⑬ 把用户的标签设置为空 <br />["@null":"tag"](http://apijson.cn/api/?type=JSON&url=http://apijson.cn:8080/put/User&json={%22id%22:82001,%22@null%22:%22tag%22,%22@explain%22:true}) <br /><br /> ⑭  从pictureList 获取第 0 张图片：<br />["@position":0, // 自定义关键词<br />"firstPicture()":"getFromArray(pictureList,@position)"](http://apijson.cn:8080/get/{"User":{"id":38710,"@position":0,"firstPicture()":"getFromArray(pictureList,@position)"}})
 全局关键词 | 为最外层对象 {} 内的关键词。其中 @database，@schema, @datasource, @role, @explain 基本同对象关键词，见上方说明，区别是全局关键词会每个表对象中没有时自动放入，作为默认值。 <br /><br />① "tag":"Table"，后面的 tag 是非 GET、HEAD 请求中匹配请求的 JSON 结构的标识，一般是要查询的 Table 的名称或该名称对应的数组 Table[] 或 Table:[]，由后端 Request 表中指定。 <br /><br />② "version":1，接口版本，version 不传、为 null 或 <=0 都会使用最高版本，传了其它有效值则会使用最接近它的最低版本，由后端 Request 表中指定。<br /><br />③ "format":true，格式化返回 Response JSON 的 key，一般是将 TableName 转为 tableName, TableName[] 转为 tableNameList, Table:alias 转为 alias, TableName-key[] 转为 tableNameKeyList 等小驼峰格式。  | ①  查隐私信息：<br />[{"tag":"Privacy","Privacy":{"id":82001}}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fgets&type=JSON&json={%22tag%22:%22Privacy%22,%22Privacy%22:{%22id%22:82001}})<br /><br /> ② 使用第 1 版接口查隐私信息：<br />[{"version":1,"tag":"Privacy","Privacy":{"id":82001}}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fgets&type=JSON&json={%22version%22:1,%22tag%22:%22Privacy%22,%22Privacy%22:{%22id%22:82001}}) <br /><br /> ③ 格式化朋友圈接口返回 JSON 中的 key：<br />[{<br > &nbsp;&nbsp; "format":true, <br > &nbsp;&nbsp; "[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "page":0, <br > &nbsp;&nbsp;&nbsp;&nbsp; "count":3, <br > &nbsp;&nbsp;&nbsp;&nbsp; "Moment":{}, <br > &nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id@":"/Moment/userId"<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "count":3,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "momentId@":"[]/Moment/id"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp; }<br >}](http://apijson.cn:8080/get/{"format":true,"[]":{"page":0,"count":3,"Moment":{},"User":{"id@":"%252FMoment%252FuserId"},"Comment[]":{"count":3,"Comment":{"momentId@":"[]%252FMoment%252Fid"}}}})
<br />
