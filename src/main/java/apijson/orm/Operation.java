/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

/**对请求 JSON 的操作
 * @author Lemon
 */
public enum Operation {
	/**
	 * 必须传的字段，结构是
	 * "key0,key1,key2..."
	 */
	MUST,

	/**
	 * 不允许传的字段，结构是
	 * "key0,key1,key2..."
	 */
	REFUSE,
	
	/**TODO 是否应该把数组类型写成 BOOLEANS, NUMBERS 等复数单词，以便抽取 enum ？扩展用 VERIFY 或 INSERT/UPDATE 远程函数等
	 * 验证是否符合预设的类型:
	 * BOOLEAN, NUMBER, DECIMAL, STRING, URL, DATE, TIME, DATETIME, OBJECT, ARRAY 
	 * 或它们的数组
	 * BOOLEAN[], NUMBER[], DECIMAL[], STRING[], URL[], DATE[], TIME[], DATETIME[], OBJECT[], ARRAY[]
	 * 结构是
	 * {
	 *   key0: value0,
	 *   key1: value1,
	 *   key2: value2
	 *   ...
	 * }
	 * 例如
	 * {
	 *   "id": "NUMBER", //id 类型必须为 NUMBER
	 *   "pictureList": "URL[]", //pictureList 类型必须为 URL[]
	 * }
	 * @see {@link AbstractVerifier#verifyType(String, String, Object, boolean)}
	 */
	TYPE,

	/**
	 * 验证是否符合预设的条件，结构是
	 * {
	 *   key0: value0,
	 *   key1: value1,
	 *   key2: value2
	 *   ...
	 * }
	 * 例如
	 * {
	 *   "phone~": "PHONE",  //phone 必须满足 PHONE 的格式，配置见 {@link AbstractVerifier#COMPILE_MAP}
	 *   "status{}": [1,2,3],  //status 必须在给出的范围内
	 *   "content[{}": ">0",  //content的长度 必须在给出的范围内
	 *   "pictureList{&{}": ">0,<=10",  //pictureList 的 JSON 长度必须在给出的范围内
	 *   "balance&{}":">0,<=10000"  //必须满足 balance>0 & balance<=10000
	 * }
	 */
	VERIFY,
	
	/**
	 * 验证是否存在，结构是
	 * "key0,key1,key2..."
	 * 多个字段用逗号隔开，联合校验
	 */
	EXIST,
	
	/**
	 * 验证是否不存在，除了本身的记录，结构是
	 * "key0,key1,key2..."
	 * 多个字段用逗号隔开，联合校验
	 */
	UNIQUE,
	
	
	/**
	 * 添加，当要被添加的对象不存在时，结构是
	 * {
	 *   key0: value0,
	 *   key1: value1,
	 *   key2: value2
	 *   ...
	 * }
	 */
	INSERT,
	
	/**
	 * 强行放入，不存在时就添加，存在时就修改，结构是
	 * {
	 *   key0: value0,
	 *   key1: value1,
	 *   key2: value2
	 *   ...
	 * }
	 */
	UPDATE,
	
	/**
	 * 替换，当要被替换的对象存在时，结构是
	 * {
	 *   key0: value0,
	 *   key1: value1,
	 *   key2: value2
	 *   ...
	 * }
	 */
	REPLACE,
	
	/**
	 * 移除，当要被移除的对象存在时，结构是
	 * "key0,key1,key2..."
	 */
	REMOVE,

	/**
	 * 监听事件，用于同步到其它表，结构是
	 * "key0": {}
	 * 例如 "name": { "UPDATE": { "Comment": { "userName@": "/name" } } }
	 * 当 User.name 被修改时，同步修改 Comment.userName
	 *
	 * 例如 "sex != 0 && sex != 1": "throw new Error('sex 必须在 [0, 1] 内！')"
	 * 自定义代码，当满足条件是执行后面的代码
	 *
	 * 还可以指定语言，例如 "python:sex not in(0, 1)": "throw new Error('sex 必须在 [0, 1] 内！')"
	 *
	 * 还有
	 * "ELSE": ""
	 * 自定义代码，不处理，和不传一样
	 *
	 * 需要
	 * 1.AbstractFunctionParser.ENABLE_SCRIPT_FUNCTION = true 启用
	 * 2.JDK 8~13 可用自带 Nashorn 这个 js 引擎，注意配置 ClassFilter 防脚本注入攻击；
	 * 其它语言及 JDK 14+ 都必须依赖外部脚本引擎，注意按对应引擎说明方式防脚本注入攻击，最好是沙箱环境。
	 */
	IF,

//	/** 直接用 IF 替代
//	 * 自定义代码，结构是 "code"，例如
//	 * "var a = 1;
//	 *  var b = a + 2;
//	 *  if (b % 2 == 0) {
//	 *      throw new Error('b % 2 == 0 !');
//	 *  }
//	 * "
//	 *
//	 * 或 { "code": "JS", "code2": "LUA" }
//	 */
//	CODE,

	/**
	 * 允许批量增删改部分失败，结构是
	 * "Table[],key[],key:alias[]"
	 * 自动 ALLOW_PARTIAL_UPDATE_FAILED_TABLE_MAP.put，结构是 Boolean，例如 true
	 */
	ALLOW_PARTIAL_UPDATE_FAIL,

	/**
	 * 强制要求必须有 id/id{}/id{}@ 条件，结构是 Boolean，例如 true
	 */
	IS_ID_CONDITION_MUST;

}
