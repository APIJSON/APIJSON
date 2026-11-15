## English | [中文](/Document-Chinese.md)

#### A better online document is available at https://apijsondocs.readthedocs.io

### Examples:

#### Get a User
Request:
<pre><code class="language-json">{
  "User":{
  }
}
</code></pre>

[Click here to test](http://apijson.cn:8080/get/{"User":{}})

Response:
<pre><code class="language-json">{
  "User":{
    "id":38710,
    "sex":0,
    "name":"TommyLemon",
    "certified":true,
    "tag":"Android&Java",
    "phone":13000038710,
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

<br />

<p align="center" >
  <a >[GIF] APIJSON single objects: simple queries, statistics, groups, orders, aggregations, comparisons, filters, aliases, etc.</a>
</p> 
  
![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_single.gif)

#### Get an array of Users

Request:
<pre><code class="language-json">{
  "[]":{
    "count":3, //just get 3 results
    "User":{
      "@column":"id,name" //just get ids and names
    }
  }
}
</code></pre>

[Click here to test](http://apijson.cn:8080/get/{"[]":{"count":3,"User":{"@column":"id,name"}}})

Response:
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

<br />

<p align="center" >
  <a >[GIF] APIJSON single arrays: simple queries, statistics, groups, orders, aggregations, paginations, searches, regexps, combinations, etc.</a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_array.gif)


#### Get a Moment and its publisher
Request:
<pre><code class="language-json">{
  "Moment":{
  },
  "User":{
    "id@":"Moment/userId"  //User.id = Moment.userId
  }
}
</code></pre>

[Click here to test](http://apijson.cn:8080/get/{"Moment":{},"User":{"id@":"Moment%252FuserId"}})

Response:
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

#### Get a Moment list like Twitter tweets
Request:
<pre><code class="language-json">{
  "[]":{                             //get an array
    "page":0,                        //pagination
    "count":2,
    "Moment":{                       //get a Moment
      "content$":"%a%"               //filter condition: content contains 'a'
    },
    "User":{
      "id@":"/Moment/userId",        //User.id = Moment.userId, short reference path，starts from grandparents path
      "@column":"id,name,head"       //get specified keys with the written order 
    },
    "Comment[]":{                    //get a Comment array, and unwrap Comment object
      "count":2,
      "Comment":{
        "momentId@":"[]/Moment/id"   //Comment.momentId = Moment.id, full reference path
      }
    }
  }
}
</code></pre>

[Click here to test](http://apijson.cn:8080/get/{"[]":{"page":0,"count":2,"Moment":{"content$":"%2525a%2525"},"User":{"id@":"%252FMoment%252FuserId","@column":"id,name,head"},"Comment[]":{"count":2,"Comment":{"momentId@":"[]%252FMoment%252Fid"}}}})

Response:
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
  <a >[GIF] APIJSON query multi related tables: one to one, one to many, many to one, various conditions, etc.</a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_associate.gif)

<br />
  
<p align="center" >
  <a >[GIF] APIJSON joins: < LEFT JOIN, & INNER JOIN, etc.</a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_join.gif)
  
<br />
  
<p align="center" >
  <a >[GIF] APIJSON subqueries：@from@ FROM, key@ =, key>@ >, key{}@ IN, key}{@ EXISTS, etc.</a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_subquery.gif)
    
<br />
    
<p align="center" >
  <a >[GIF] APIJSON: a set of some features, simple to complex</a>
</p> 

![](https://raw.githubusercontent.com/TommyLemon/StaticResources/master/APIJSON/APIJSON_query_summary.gif)

<br />

[Test it online](http://apijson.cn/api) 

<br />
<br />

## API Design Rules

### 1. Methods and API endpoints

 Methods | URL | Request | Response
------------ | ------------ | ------------ | ------------
**GET**: <br /> A general way to get data.<br /> You can use dev tools to make edits in a web browser. | base_url/get/ | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; //Add contiditions here.<br /> &nbsp;&nbsp; }<br />} <br /> <br /> Eg. To get a Moment with `id = 235`：<br />{<br /> &nbsp;&nbsp; "Moment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id":235<br /> &nbsp;&nbsp; }<br />} | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; ...<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp; "msg":"success"<br />}<br />Eg.<br />{<br /> &nbsp;&nbsp; "Moment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "userId":38710,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "content":"APIJSON is the real-time coding-free, powerful and secure ORM"<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp; "msg":"success"<br /> }
**HEAD**: <br /> A general way to get counts.<br /> You can use dev tools to make edits in a web browser. | base_url/head/ | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; …<br /> &nbsp;&nbsp; }<br /> } <br /> {…} are conditions. <br /><br /> Eg. Get the number of Moments posted by the user with `id = 38710`：<br />{<br /> &nbsp;&nbsp; "Moment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "userId":38710<br /> &nbsp;&nbsp; }<br />} | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br /> &nbsp;&nbsp;&nbsp;&nbsp; "count":10<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp; "msg":"success"<br />} <br /> Eg.<br />{<br /> &nbsp;&nbsp; "Moment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br /> &nbsp;&nbsp;&nbsp;&nbsp; "count":10<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp;  "msg":"success"<br />}
**GETS**: <br /> Get data with high security and confidentiality.<br /> Eg. bank accounts, birth date. | base_url/gets/ | You need to add `"tag":tag` with the same level of `Moment:{}`. Others are the same as **GET**. | Same as **GET**.
**HEADS**: <br /> Get counts of confidential data(eg. bank account).| base_url/heads/ | You need to add  `"tag":tag` with the same level of `Moment:{}`. Others are the same as **HEAD**. | Same as **HEAD**.
**POST**: <br /> Add new data. | base_url/post/ | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; …<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "tag":tag<br />} <br /> The id in {...} is generated automatically when table is built and can’t be set by the user. <br /><br />Eg. A user with `id = 38710` posts a new Moment：<br />{<br /> &nbsp;&nbsp; "Moment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "userId":38710,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "content":"APIJSON is the real-time coding-free, powerful and secure ORM"<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "tag":"Moment"<br />} | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id":38710<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp; "msg":"success"<br />}<br />Eg.<br />{<br /> &nbsp;&nbsp; "Moment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id":120<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp; "msg":"success"<br />}
**PUT**: <br /> Make changes to a specific item.<br /> Only change the part sent to server. | base_url/put/ | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id":id,<br /> &nbsp;&nbsp;&nbsp;&nbsp; …<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "tag":tag<br />} <br />  You can also add multiple id as `id{}`.<br /><br />Eg. Make changes to Moment's content with id= 235:<br />{<br /> &nbsp;&nbsp; "Moment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id":235,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "content":"APIJSON is the real-time coding-free, powerful and secure ORM"<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "tag":"Moment"<br />} | Same as **POST**.
**DELETE**: <br /> Delete data. | base_url/delete/ | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id":id<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "tag":tag<br />} <br /> You can also add multiple id as `id{}`. <br /><br /> Or Delete contents with multiple id：<br />{<br /> &nbsp;&nbsp; "Comment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id{}":[100,110,120]<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "tag":"Comment[]"<br />} | {<br /> &nbsp;&nbsp; TableName:{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp;&nbsp;&nbsp; "msg":"success",<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id[]":[100,110,120]<br />&nbsp;&nbsp; &nbsp;&nbsp; "count":3<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "code":200,<br /> &nbsp;&nbsp; "msg":"success"<br />}<br />Eg.<br />{<br />&nbsp;&nbsp; "Comment":{<br />&nbsp;&nbsp; &nbsp;&nbsp; "code":200,<br />&nbsp;&nbsp; &nbsp;&nbsp; "msg":"success",<br />&nbsp;&nbsp; &nbsp;&nbsp; "id[]":[100,110,120],<br />&nbsp;&nbsp; &nbsp;&nbsp; "count":3<br />&nbsp;&nbsp; },<br />&nbsp;&nbsp; "code":200,<br />&nbsp;&nbsp; "msg":"success"<br />}

**Note**:<br />
1. TableName means the name of the table where you get data. It’ll respond with a JSON Object(the form is {....})with columns inside.
2. `"tag":tag` is needed when methods are not GET or HEAD. The tag after the colon is the key in JSON Object of making requests. Generally, it’s the name of the table you’re looking for.
3. GET, HEAD are methods for general data requests.They support versatile JSON Object structure. Other methods are used for requesting confidential data and the requesting JSON Object needs to be in the same form/order as that in the database. Otherwise, the request shall be denied.
4. GETS and GET, HEADS and HEAD return the same type of data. But the request form is a little different.
5. For HTTP, all API methods (get,gets,head,heads,post,put,delete) make requests with HTTP POST.
6. All JSON Objects here are with {...} form. You can put items or objects in it.
7. Each object in the database has a unique address.

<br />

### 2. Keywords in URL parameters
 
 Functions | Key-value pairs | Examples
------------ | ------------ | ------------
 Get data in arrays | `"key[]":{}`<br /> The part after the colon is a JSONObject. *key* is optional. When *key* is the same as the table name , the JSONObject will be in a simplified form. For example,  `{Table:{Content}}` will be written as `{Content}`.| [{"User[]":{"User":{}}}](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{}}}) <br />It is used for getting data from a user. Here, key and tablename are all "User", then <br />`{"User":{"id", ...}}` <br />will be written as <br /> `{"id", ...}`
 Get data that meets specific conditions | `"key{}":[]` <br />The part after the colon is a JSONArray with conditions inside.| ["id{}":[38710,82001,70793]](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"id{}":[38710,82001,70793]}}}) <br />In SQL, this would be `id IN(38710,82001,70793)`. <br />It means getting data with id equals 38710,82001,70793.
 Get data with comparison operation| `"key{}":"condition0,condition1..."`<br />Conditions can be any SQL comparison operation. Use''to include any non-number characters.| ["id{}":"<=80000,\>90000"](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"id{}":"<=80000,\>90000"}}}) <br />In SQL, it'd be <br /> `id<=80000 OR id>90000`, <br />which means get User array with id\<=80000 \| id>90000
 Get data that contains an element | `"key<>":Object`  =>  `"key<>":[Object]` <br /> *key* must be a JSONArray while *Object* cannot be JSON.|  ["contactIdList<\>":38710](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"contactIdList<\>":38710}}}) <br />In SQL, this would be <br />`json_contains(contactIdList,38710)`. <br />It means find data of the User whose contactList contains 38710.
 See if it exists |`"key}{@":{`<br /> &nbsp;&nbsp; `"from":"Table",`<br /> &nbsp;&nbsp; `"Table":{ ... }`<br />`}`<br /><br />}{ means EXISTS.<br /> *key* is the one you want to check. <br />Here is a *Subquery* in it, see specifications below for more information. | ["id}{@":{<br /> &nbsp;&nbsp; "from":"Comment",<br /> &nbsp;&nbsp; "Comment":{<br /> &nbsp;&nbsp;  &nbsp;&nbsp; "momentId":15 <br /> &nbsp;&nbsp; }<br />}](http://apijson.cn:8080/get/{"User":{"id}{@":{"from":"Comment","Comment":{"momentId":15}}}})<br /> WHERE EXISTS(SELECT * FROM Comment WHERE momentId=15)
 Include functions in parameters | `"key()":"function (key0,key1...)"`<br />This will trigger the back-end <br /> `function(JSONObject request, String key0, String key1...)` <br />to get or testify data.<br /> Use - and + to show the order of priority: analyze key-() > analyze the current object > analyze key() > analyze child object > analyze key+()| ["isPraised()":"isContain(praiseUserIdList,userId)"](http://apijson.cn:8080/get/{"Moment":{"id":301,"isPraised()":"isContain(praiseUserIdList,userId)"}}) <br />This will use function boolean isContain(JSONObject request, String array, String value). In this case, client will get "isPraised":true(In this case, client use function to testify if a user clicked ‘like’ button for a Moment.)
 Refer a value | `"key@":"key0/key1/.../refKey"`<br />Use / to show path. The part before the colon is the key that wants to refer. The path after the colon starts with the parent level of the key.| ["Moment":{<br /> &nbsp;&nbsp; "userId":38710<br />},<br />"User":{<br /> &nbsp;&nbsp; "id@":"/Moment/userId"<br />}](http://apijson.cn:8080/get/{"Moment":{"userId":38710},"User":{"id@":"%252FMoment%252FuserId"}})<br /> In this example, the value of id in User refer to the *userId* in *Moment*, which means <br />`User.id = Moment.userId`. <br />After the request is sent, <br />`"id@":"/Moment/userId"` will be `"id":38710`.
 Subquery | `"key@":{`<br /> &nbsp;&nbsp; `"range":"ALL",` <br /> &nbsp;&nbsp; `"from":"Table",`<br /> &nbsp;&nbsp; `"Table":{ ... }`<br />`}`<br />*range* can be ALL, ANY.<br />*from* means which table you want to query. <br />It’s very similar to how you query in SQL. <br />You can also use *count*, *join*, etc. | ["id@":{<br /> &nbsp;&nbsp; "from":"Comment",<br /> &nbsp;&nbsp; "Comment":{<br /> &nbsp;&nbsp;  &nbsp;&nbsp; "@column":"min(userId)" <br /> &nbsp;&nbsp; }<br />}](http://apijson.cn:8080/get/{"User":{"id@":{"from":"Comment","Comment":{"@column":"min(userId)"}}}})<br /> `WHERE id=(SELECT min(userId) FROM Comment)`.
 Fuzzy matching | `"key$":"SQL search expressions"`  =>  `"key$":["SQL search expressions"]`<br />Any SQL search expressions.Eg.%key%(include key), key%(start with key),%k%e%y%(include k, e, y). % means any characters. | ["name$":"%m%"](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"name$":"%2525m%2525"}}}) <br />In SQL, it's <br />`name LIKE '%m%'`, <br />meaning that get User with ‘m’ in name.
 Regular Expression| `"key~":"regular expression"`  =>  `"key~":["regular expression"]`<br />It can be any regular expressions.Eg. ^[0-9]+$ ，*~ not case sensitive, advanced search is applicable.| ["name~":"^[0-9]+$"](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"name~":"^[0-9]%252B$"}}}) <br />In SQL, it's <br />`name REGEXP '^[0-9]+$'`.
 Get data in a range| `"key%":"start,end"`  =>  `"key%":["start,end"]`<br />The data type of start and end can only be either Boolean, Number or String. Eg. "2017-01-01,2019-01-01" ，["1,90000", "82001,100000"]. It's used for getting data from a specific time range. | ["date%":"2017-10-01,2018-10-01"](http://apijson.cn:8080/get/{"User[]":{"count":3,"User":{"date%2525":"2017-10-01,2018-10-01"}}}) <br />In SQL, it's <br />`date BETWEEN '2017-10-01' AND '2018-10-01'`, <br />meaning to get User data that registered between 2017-10-01 and 2018-10-01.
 Make an alias | `"name:alias"`<br />this changes name to alias in returning results. It’s applicable to column, tableName, SQL Functions, etc. but only in GET, HEAD requests. | ["@column":"toId:parentId"](http://apijson.cn:8080/get/{"Comment":{"@column":"id,toId:parentId","id":51}}) <br />In SQL, it's <br />`toId AS parentId`. <br />It'll return `parentId` instead of `toId`.<br /><br />For @key format like "lc_wai6b3vk2:(lc_wai6b3vk)", it means renaming field lc_wai6b3vk2 to lc_wai6b3vk, commonly used for field renaming scenarios. Example:<br />{<br />&nbsp;&nbsp;"lc_sinan_ba074fbb": {<br />&nbsp;&nbsp;&nbsp;&nbsp;"lc_wai6b3vk": "11",<br />&nbsp;&nbsp;&nbsp;&nbsp;"lc_wai6b3vk2": "22",<br />&nbsp;&nbsp;&nbsp;&nbsp;"@combine": "lc_wai6b3vk \\| lc_wai6b3vk2",<br />&nbsp;&nbsp;&nbsp;&nbsp;"@key": "lc_wai6b3vk2:(lc_wai6b3vk)"<br />&nbsp;&nbsp;}<br />}<br />corresponds to SQL `(lc_wai6b3vk = '11' OR lc_wai6b3vk2 = '22')`, but the lc_wai6b3vk2 field will be renamed and displayed as lc_wai6b3vk in the returned result
 Add / expand an item | `"key+":Object` <br /> The type of Object is decided by *key*. Types can be Number, String, JSONArray. Forms are 82001,"apijson",["url0","url1"] respectively. It’s only applicable to PUT request.| "praiseUserIdList+":[82001]. In SQL, it's <br />`json_insert(praiseUserIdList,82001)`. <br />Add an *id* that praised the Moment.
 Delete / decrease an item | `"Key-":Object`<br /> It’s the contrary of "key+" | "balance-":100.00. In SQL, it's <br />`balance = balance - 100.00`, <br />meaning there's 100 less in balance.
 Operations | &, \|, ! <br /> They're used in logic operations. It’s the same as AND, OR, NOT in SQL respectively. <br />By default, for the same key, it’s ‘\|’ (OR)operation among conditions; for different keys, the default operation among conditions is ‘&’(AND). <br /> |  ① ["id&{}":">80000,<=90000"](http://apijson.cn:8080/head/{"User":{"id&{}":">80000,<=90000"}}) <br />In SQL, it's <br />`id>80000 AND id<=90000`, <br />meaning *id* needs to be id>80000 & id<=90000<br /><br /> ② ["id\|{}":">90000,<=80000"](http://apijson.cn:8080/head/{"User":{"id\|{}":">90000,<=80000"}}) <br />It's the same as "id{}":">90000,<=80000". <br />In SQL, it's <br />`id>80000 OR id<=90000`, <br />meaning that *id* needs to be id>90000 \| id<=80000<br /><br /> ③ ["id!{}":[82001,38710]](http://apijson.cn:8080/head/{"User":{"id!{}":[82001,38710]}}) <br />In SQL, it's <br />`id NOT IN(82001,38710)`, <br />meaning id needs to be ! (id=82001 \| id=38710).
 Keywords in an Array: It can be self-defined. | As for `"key":Object`, *key* is the keyword of *{}* in *"[]":{}*. The type of *Object* is up to *key*.<br /><br />① `"count":Integer` It's used to count the number. The default largest number is 100. <br /><br />② `"page":Integer` It’s used for getting data from which page, starting from 0. The default largest number is 100. It’s usually used with COUNT. <br /><br />③ `"query":Integer` Get the number of items that match conditions<br />When to get the object, the integer should be 0; when to get the total number, it’s 1; when both above, it’s 2.<br />You can get the total number with keyword total. It can be referred to other values. <br />Eg. <br />`"total@":"/[]/total"` <br />Put it as the same level of query.  <br />*Query* and *total* are used in GET requests just for convenience. Generally, HEAD request is for getting numbers like the total number.<br /><br />④ `"join":"&/Table0,</Table1"`<br />Join tables：<br /> "\<" - LEFT JOIN <br /> ">" - RIGHT JOIN <br /> "&" - INNER JOIN <br /> "\|" - FULL JOIN <br />  "!" - OUTER JOIN <br /> "@" - APP JOIN <br />Where @ APP JOIN is in application layer.It’ll get all the keys in tables that refKeys in result tables are referred to, like refKeys:[value0, value1….]. Then, as the results get data according to `key=$refKey` a number of times (COUNT), it uses key `IN($refKeys)` to put these counts together in just one SQL query, in order to improve the performance.<br /> Other JOIN functions are the same as those in SQL. <br />`"join":"</ViceTable",`<br />`"MainTable":{},`<br />`"ViceTable":{"key@":"/MainTable/refKey"}`<br />will return <br />`MainTable LEFT JOIN ViceTable` <br />`ON ViceTable.key=MainTable.refKey` <br /><br />⑤ `"otherKey":Object` Self-defined keyword other than those that already in the system. It also returns with self-defined keywords.| ① Get User arrays with maximum of 5：<br />["count":5](http://apijson.cn:8080/get/{"[]":{"count":5,"User":{}}})<br /><br /> ② Look into User arrays on page 3. Show 5 of them each page. <br />["count":5,<br />"page":3](http://apijson.cn:8080/get/{"[]":{"count":5,"page":3,"User":{}}})<br /><br /> ③ Get User Arrays and count the total number of Users：<br />["[]":{<br /> &nbsp;&nbsp; "query":2,<br /> &nbsp;&nbsp; "User":{}<br />},<br />"total@":"/[]/total"](http://apijson.cn:8080/get/{"[]":{"query":2,"count":5,"User":{}},"total@":"%252F[]%252Ftotal"})<br />Questions like total page numbers or if there's next page can be solved by total,count,page functions，<br />Total page number: <br/>`int totalPage = Math.ceil(total / count)`<br />If this is the last page: <br />`boolean hasNextPage = total > count*page`<br />If this is the first page: <br />`boolean isFirstPage = page <= 0`<br />If it's the last page: <br />`boolean isLastPage = total <= count*page`<br />... <br /><br /> ④ Moment INNER JOIN User LEFT JOIN Comment：<br />["[]":{<br /> &nbsp;&nbsp; "join": "&/User,\</Comment",<br /> &nbsp;&nbsp; "Moment":{},<br /> &nbsp;&nbsp; "User":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "name~":"t",<br /> &nbsp;&nbsp;&nbsp;&nbsp; "id@": "/Moment/userId"<br /> &nbsp;&nbsp; },<br /> &nbsp;&nbsp; "Comment":{<br /> &nbsp;&nbsp;&nbsp;&nbsp; "momentId@": "/Moment/id"<br /> &nbsp;&nbsp; }<br />}](http://apijson.cn:8080/get/{"[]":{"count":5,"join":"&%252FUser,\<%252FComment","Moment":{"@column":"id,userId,content"},"User":{"name~":"t","id@":"%252FMoment%252FuserId","@column":"id,name,head"},"Comment":{"momentId@":"%252FMoment%252Fid","@column":"id,momentId,content"}}})<br /><br /> ⑤ Add the current user to every level：<br />["User":{},<br />"[]":{<br /> &nbsp;&nbsp; "name@":"User/name", //self-defined keyword<br /> &nbsp;&nbsp; "Moment":{}<br />}](http://apijson.cn:8080/get/{"User":{},"[]":{"name@":"User%252Fname","Moment":{}}})
 Keywords in Objects: It can be self-defined. | `"@key":Object` @key is the keyword of {} in Table:{}. The type of Object is decided by @key<br /><br />① `"@combine":"&key0,&key1,\|key2,key3,`<br />`!key4,!key5,&key6,key7..."`<br />First, it’ll group data with same operators. Within one group, it operates from left to right. Then it’ll follow the order of & \| ! to do the operation. Different groups are connected with &. So the expression above will be : <br /> (key0 & key1 & key6 & other key) & (key2 \| key3 \| key7) & !(key4 \| key5) <br />\| is optional. <br /><br />② `"@column":"column;function(arg)..."` Return with specific columns.<br /><br />③ `"@order":"column0+,column1-..."` Decide the order of returning results:<br /><br />④ `"@group":"column0,column1..."` How to group data. If @column has declared Table id, this id need to be included in @group. In other situations, at least one of the following needs to be done:<br />1.Group id is declared in @column<br />2.Primary Key of the table is declared in @group.<br/><br/>⑤ `@having":"function0(...)?value0;function1(...)?value1;function2(...)?value2..."` Add conditions on return results with @having. Usually working with@group, it’s declared in @column.<br /><br />⑥ `"@schema":"sys"` Can be set as default setting.<br /><br />⑦ `"@database":"POSTGRESQL"` Get data from a different database.Can be set as default setting.<br /><br />⑧ `"@role":"OWNER"` Get information of the user, including <br />UNKNOWN,LOGIN,CONTACT,CIRCLE,OWNER,ADMIN，<br />Can be set as default setting. <br />You can self-define a new role or rewrite a role.  Use`Verifier.verify` etc. to self-define validation methods. <br /><br />⑨ `"@explain":true` Profiling. Can be set as default setting. <br /><br />⑩ `"@otherKey":Object` Self-define keyword | ① Search *Users* that *name* or *tag* contains the letter "a":<br />["name~":"a",<br />"tag~":"a",<br />"@combine":"name~,tag~"](http://apijson.cn:8080/get/{"User[]":{"count":10,"User":{"@column":"id,name,tag","name~":"a","tag~":"a","@combine":"name~,tag~"}}})<br /><br /> ② Only search column id,sex,name and return with the same order:<br />["@column":"id,sex,name"](http://apijson.cn:8080/get/{"User":{"@column":"id,sex,name","id":38710}})<br /><br /> ③ Search Users that have descending order of name and default order of id:<br />["@order":"name-,id"](http://apijson.cn:8080/get/{"[]":{"count":10,"User":{"@column":"name,id","@order":"name-,id"}}})<br /><br /> ④ Search Moment grouped with userId: <br />["@group":"userId,id"](http://apijson.cn:8080/get/{"[]":{"count":10,"Moment":%7B"@column":"userId,id","@group":"userId,id"}}})<br /><br /> ⑤ Search Moments that id equals or less than 100 and group with userId:<br />["@column":"userId;max(id)",<br />"@group":"userId",<br />"@having":"max(id)>=100"](http://apijson.cn:8080/get/{"[]":{"count":10,"Moment":{"@column":"userId%253Bmax(id)","@group":"userId","@having":"max(id)>=100"}}})<br />You can also define the name of the returned function:<br />["@column":"userId;max(id):maxId",<br />"@group":"userId",<br />"@having":"(maxId)>=100"](http://apijson.cn:8080/get/{"[]":{"count":10,"Moment":{"@column":"userId%253Bmax(id):maxId","@group":"userId","@having":"(maxId)>=100"}}})<br /><br /> ⑥ Check Users table in sys: <br />["@schema":"sys"](http://apijson.cn:8080/get/{"User":{"@schema":"sys"}})<br /><br /> ⑦ Check Users table in PostgreSQL:<br />["@database":"POSTGRESQL"](http://apijson.cn:8080/get/{"User":{"@database":"POSTGRESQL"}})<br /><br /> ⑧ Check the current user's activity:<br />["@role":"OWNER"](http://apijson.cn:8080/get/{"[]":{"Moment":{"@role":"OWNER"}}})<br /><br /> ⑨ Turn on profiling: <br />["@explain":true](http://apijson.cn:8080/get/{"[]":{"Moment":{"@explain":true}}})<br /><br /> ⑩ Get the No.0 picture from pictureList：<br />["@position":0, //self-defined keyword<br />"firstPicture()":"getFromArray(pictureList,@position)"](http://apijson.cn:8080/get/{"User":{"id":38710,"@position":0,"firstPicture()":"getFromArray(pictureList,@position)"}})
 global keyword. | It is a keyword inside the outermost object {}. Among them, @database, @schema, @datasource, @role, and @explain are basically the same as object keywords, see the above description, the difference is that the global keywords will be automatically inserted in each table object as the default value. <br /><br />① "tag": String, the following tag is the identifier of the JSON structure matching the request in non-GET or HEAD requests, generally it is the name of the Table to be queried or the array Table[] or Table:[] corresponding to the name, determined by the backend specified in the Request table. <br /><br /> ② "version": Integer, the interface version. If the version is not passed, null or <=0, the highest version will be used. If other valid values are passed, the lowest version closest to it will be used, which is specified in the backend Request table.<br /><br /> ③ "format": Boolean, formatted to return the key of the Response JSON, generally converting TableName to tableName, TableName[] to tableNameList, Table:alias to alias, TableName-key[] to tableNameKeyList and other camelcase formats.  | ①  Check private information:：<br />[{"tag":"Privacy","Privacy":{"id":82001}}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fgets&type=JSON&json={%22tag%22:%22Privacy%22,%22Privacy%22:{%22id%22:82001}})<br /><br /> ② Use the version 1 interface to check private information:：<br />[{"version":1,"tag":"Privacy","Privacy":{"id":82001}}](http://apijson.cn/api?url=http%3A%2F%2Fapijson.cn%3A8080%2Fgets&type=JSON&json={%22version%22:1,%22tag%22:%22Privacy%22,%22Privacy%22:{%22id%22:82001}}) <br /><br /> ③ Format Moments interface to return in JSON key：<br />[{<br > &nbsp;&nbsp; "format":true, <br > &nbsp;&nbsp; "[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp; "page":0, <br > &nbsp;&nbsp;&nbsp;&nbsp; "count":3, <br > &nbsp;&nbsp;&nbsp;&nbsp; "Moment":{}, <br > &nbsp;&nbsp;&nbsp;&nbsp; "User":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "id@":"/Moment/userId"<br > &nbsp;&nbsp;&nbsp;&nbsp; },<br > &nbsp;&nbsp;&nbsp;&nbsp; "Comment[]":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "count":3,<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "Comment":{<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; "momentId@":"[]/Moment/id"<br > &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp;&nbsp;&nbsp; }<br > &nbsp;&nbsp; }<br >}](http://apijson.cn:8080/get/{"format":true,"[]":{"page":0,"count":3,"Moment":{},"User":{"id@":"%252FMoment%252FuserId"},"Comment[]":{"count":3,"Comment":{"momentId@":"[]%252FMoment%252Fid"}}}})

<br />

