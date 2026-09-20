/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import apijson.*;
import apijson.orm.Join.On;
import apijson.orm.exception.NotExistException;
import apijson.orm.exception.UnsupportedDataTypeException;
import apijson.orm.model.*;

import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static apijson.JSON.getBoolean;
import static apijson.JSON.getString;
import static apijson.JSONMap.*;
import static apijson.RequestMethod.*;
import static apijson.SQL.*;

/**config sql for JSON Request
 * @author Lemon
 */
public abstract class AbstractSQLConfig<T, M extends Map<String, Object>, L extends List<Object>>
		implements SQLConfig<T, M, L> {
	private static final String TAG = "AbstractSQLConfig";

	/**
	 * 为 true 则兼容 5.0 之前 @having:"toId>0;avg(id)<100000" 默认 AND 连接，为 HAVING toId>0 AND avg(id)<100000；
	 * 否则按 5.0+ 新版默认 OR 连接，为 HAVING toId>0 OR avg(id)<100000，使用 @having& 或 @having:{ @combine: null } 时才用 AND 连接
	 */
	public static boolean IS_HAVING_DEFAULT_AND = false;
	/**
	 * 为 true 则兼容 5.0 之前 @having:"toId>0" 这种不包含 SQL 函数的表达式；
	 * 否则按 5.0+ 新版不允许，可以用 @having:"(toId)>0" 替代
	 */
	public static boolean IS_HAVING_ALLOW_NOT_FUNCTION = false;

	/**
	 * 开启 WITH AS 表达式(在支持这种语法的数据库及版本)来简化 SQL 和提升性能
	 */
	public static boolean ENABLE_WITH_AS = false;

	/**
	 * 对指定的方法，忽略空字符串，不作为 GET 条件，PUT 值等。可取值 new RequestMethod[]{ RequestMethod.GET, RequestMethod.POST ... }
	 */
	public static List<RequestMethod> IGNORE_EMPTY_STRING_METHOD_LIST = null;
	/**
	 * 对指定的方法，忽略空白字符串。即首尾 trim 去掉所有不可见字符后，仍然为空的，就忽略，不作为 GET 条件，PUT 值等。
	 * 可取值 new RequestMethod[]{ RequestMethod.GET, RequestMethod.POST ... }
	 */
	public static List<RequestMethod> IGNORE_BLANK_STRING_METHOD_LIST = null;

	public static String KEY_DELETED_KEY = "deletedKey";
	public static String KEY_DELETED_VALUE = "deletedValue";
	public static String KEY_NOT_DELETED_VALUE = "notDeletedValue";

	public static int MAX_HAVING_COUNT = 5;
	public static int MAX_WHERE_COUNT = 10;
	public static int MAX_COMBINE_DEPTH = 2;
	public static int MAX_COMBINE_COUNT = 5;
	public static int MAX_COMBINE_KEY_COUNT = 2;
	public static float MAX_COMBINE_RATIO = 1.0f;
	public static boolean ALLOW_MISSING_KEY_4_COMBINE = true;

	public static String DEFAULT_DATABASE = DATABASE_MYSQL;
	public static String DEFAULT_NAMESPACE = ""; // "root";
	public static String DEFAULT_CATALOG = ""; // PostgreSQL JDBC 必须在 URI 中传 ""postgres";
	public static String DEFAULT_SCHEMA = "sys";
	public static String PREFIX_DISTINCT = "DISTINCT ";

	public static Pattern PATTERN_SCHEMA;
	// * 和 / 不能同时出现，防止 /* */ 段注释！ # 和 -- 不能出现，防止行注释！ ; 不能出现，防止隔断SQL语句！空格不能出现，防止 CRUD,DROP,SHOW TABLES等语句！
	public static Pattern PATTERN_RANGE;
	public static Pattern PATTERN_FUNCTION;

	/**
	 * 表 SCHEMA 映射
	 */
	public static Map<String, String> TABLE_SCHEMA_MAP;

	/**
	 * 表名映射，隐藏真实表名，对安全要求很高的表可以这么做
	 */
	public static Map<String, String> TABLE_KEY_MAP;
	public static Map<String, Object> UPSERT_TABLE_MAP;
	/**
	 * 字段名映射，隐藏真实字段名，对安全要求很高的表可以这么做，另外可以配置 name_tag:(name,tag) 来实现多字段 IN，length_tag:length(tag) 来实现 SQL 函数复杂条件
	 */
	public static Map<String, String> COLUMN_KEY_MAP;
	/**
	 * 允许批量增删改部分记录失败的表
	 */
	public static Map<String, String> ALLOW_PARTIAL_UPDATE_FAIL_TABLE_MAP;
	public static List<String> CONFIG_TABLE_LIST;
	public static List<String> DATABASE_LIST;

	// 自定义原始 SQL 片段 Map<key, substring>：当 substring 为 null 时忽略；当 substring 为 "" 时整个 value 是 raw SQL；其它情况则只是 substring 这段为 raw SQL
	public static Map<String, String> RAW_MAP;
	// 允许调用的 SQL 函数：当 substring 为 null 时忽略；当 substring 为 "" 时整个 value 是 raw SQL；其它情况则只是 substring 这段为 raw SQL
	public static Map<String, String> SQL_AGGREGATE_FUNCTION_MAP;
	public static Map<String, String> SQL_FUNCTION_MAP;

	static {  // 凡是 SQL 边界符、分隔符、注释符 都不允许，例如 ' " ` ( ) ; # -- /**/ ，以免拼接 SQL 时被注入意外可执行指令
		PATTERN_SCHEMA = Pattern.compile("^[A-Za-z0-9_-]+$");
		PATTERN_RANGE = Pattern.compile("^[0-9%,!=\\<\\>/\\.\\+\\-\\*\\^]+$"); // ^[a-zA-Z0-9_*%!=<>(),"]+$ 导致 exists(select*from(Comment)) 通过！
		// TODO 改成更好的正则，校验前面为单词，中间为操作符，后面为值
		PATTERN_FUNCTION = Pattern.compile("^[A-Za-z0-9%,:_@&~`!=\\<\\>\\|\\[\\]\\{\\} /\\.\\+\\-\\*\\^\\?\\(\\)\\$]+$");

		TABLE_SCHEMA_MAP = new HashMap<>();
		TABLE_SCHEMA_MAP.put(Table.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(Column.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(PgClass.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(PgAttribute.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(SysTable.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(SysColumn.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(ExtendedProperty.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(AllTable.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(AllColumn.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(AllTableComment.class.getSimpleName(), DEFAULT_SCHEMA);
		TABLE_SCHEMA_MAP.put(AllColumnComment.class.getSimpleName(), DEFAULT_SCHEMA);

		TABLE_KEY_MAP = new HashMap<>();
		TABLE_KEY_MAP.put(Table.class.getSimpleName(), Table.TABLE_NAME);
		TABLE_KEY_MAP.put(Column.class.getSimpleName(), Column.TABLE_NAME);
		TABLE_KEY_MAP.put(PgClass.class.getSimpleName(), PgClass.TABLE_NAME);
		TABLE_KEY_MAP.put(PgAttribute.class.getSimpleName(), PgAttribute.TABLE_NAME);
		TABLE_KEY_MAP.put(SysTable.class.getSimpleName(), SysTable.TABLE_NAME);
		TABLE_KEY_MAP.put(SysColumn.class.getSimpleName(), SysColumn.TABLE_NAME);
		TABLE_KEY_MAP.put(ExtendedProperty.class.getSimpleName(), ExtendedProperty.TABLE_NAME);
		TABLE_KEY_MAP.put(AllTable.class.getSimpleName(), AllTable.TABLE_NAME);
		TABLE_KEY_MAP.put(AllColumn.class.getSimpleName(), AllColumn.TABLE_NAME);
		TABLE_KEY_MAP.put(AllTableComment.class.getSimpleName(), AllTableComment.TABLE_NAME);
		TABLE_KEY_MAP.put(AllColumnComment.class.getSimpleName(), AllColumnComment.TABLE_NAME);

		UPSERT_TABLE_MAP = new HashMap<>();

		ALLOW_PARTIAL_UPDATE_FAIL_TABLE_MAP = new HashMap<>();

		CONFIG_TABLE_LIST = new ArrayList<>();  // Table, Column 等是系统表 AbstractVerifier.SYSTEM_ACCESS_MAP.keySet());
		CONFIG_TABLE_LIST.add(Function.class.getSimpleName());
		CONFIG_TABLE_LIST.add(Request.class.getSimpleName());
		CONFIG_TABLE_LIST.add(Access.class.getSimpleName());
		CONFIG_TABLE_LIST.add(Document.class.getSimpleName());

		DATABASE_LIST = new ArrayList<>();
		DATABASE_LIST.add(DATABASE_MYSQL);
		DATABASE_LIST.add(DATABASE_POSTGRESQL);
		DATABASE_LIST.add(DATABASE_SQLSERVER);
		DATABASE_LIST.add(DATABASE_ORACLE);
		DATABASE_LIST.add(DATABASE_DB2);
		DATABASE_LIST.add(DATABASE_MARIADB);
		DATABASE_LIST.add(DATABASE_TIDB);
		DATABASE_LIST.add(DATABASE_COCKROACHDB);
		DATABASE_LIST.add(DATABASE_DAMENG);
		DATABASE_LIST.add(DATABASE_KINGBASE);
		DATABASE_LIST.add(DATABASE_KINGBASE_MYSQL);
		DATABASE_LIST.add(DATABASE_KINGBASE_ORACLE);
		DATABASE_LIST.add(DATABASE_KINGBASE_SQLSERVER);
		DATABASE_LIST.add(DATABASE_ELASTICSEARCH);
		DATABASE_LIST.add(DATABASE_MANTICORE);
		DATABASE_LIST.add(DATABASE_CLICKHOUSE);
		DATABASE_LIST.add(DATABASE_HIVE);
		DATABASE_LIST.add(DATABASE_PRESTO);
		DATABASE_LIST.add(DATABASE_TRINO);
		DATABASE_LIST.add(DATABASE_MILVUS);
		DATABASE_LIST.add(DATABASE_INFLUXDB);
		DATABASE_LIST.add(DATABASE_TDENGINE);
		DATABASE_LIST.add(DATABASE_TIMESCALEDB);
		DATABASE_LIST.add(DATABASE_QUESTDB);
		DATABASE_LIST.add(DATABASE_IOTDB);
		DATABASE_LIST.add(DATABASE_SNOWFLAKE);
		DATABASE_LIST.add(DATABASE_DATABEND);
		DATABASE_LIST.add(DATABASE_DATABRICKS);
		DATABASE_LIST.add(DATABASE_REDIS);
		DATABASE_LIST.add(DATABASE_MONGODB);
		DATABASE_LIST.add(DATABASE_CASSANDRA);
		DATABASE_LIST.add(DATABASE_KAFKA);
		DATABASE_LIST.add(DATABASE_MQ);
		DATABASE_LIST.add(DATABASE_DUCKDB);
		DATABASE_LIST.add(DATABASE_SURREALDB);
		DATABASE_LIST.add(DATABASE_OPENGAUSS);
		DATABASE_LIST.add(DATABASE_DORIS);
		DATABASE_LIST.add(DATABASE_STARROCKS);


		RAW_MAP = new LinkedHashMap<>();  // 保证顺序，避免配置冲突等意外情况

		RAW_MAP.put("+", "");
		RAW_MAP.put("-", "");
		RAW_MAP.put("*", "");
		RAW_MAP.put("/", "");
		RAW_MAP.put("=", "");
		RAW_MAP.put("!=", "");
		RAW_MAP.put(">", "");
		RAW_MAP.put(">=", "");
		RAW_MAP.put("<", "");
		RAW_MAP.put("<=", "");
		RAW_MAP.put("%", "");
		RAW_MAP.put("(", "");
		RAW_MAP.put(")", "");

		RAW_MAP.put("&", ""); // 位运算
		RAW_MAP.put("|", ""); // 位运算
		RAW_MAP.put("^", ""); // 位运算
		RAW_MAP.put("~", ""); // 位运算
		RAW_MAP.put("&=", ""); // 位运算
		RAW_MAP.put("|=", ""); // 位运算
		RAW_MAP.put("~=", ""); // 位运算
		RAW_MAP.put(">>", ""); // 位运算
		RAW_MAP.put("<<", ""); // 位运算

		// MySQL 关键字
		RAW_MAP.put("AS", "");
		RAW_MAP.put("IS NOT NULL", "");
		RAW_MAP.put("IS NULL", "");
		RAW_MAP.put("IS", "");
		RAW_MAP.put("NULL", "");
		RAW_MAP.put("AND", "");
		RAW_MAP.put("OR", "");
		RAW_MAP.put("NOT", "");
		RAW_MAP.put("VALUE", "");
		RAW_MAP.put("DISTINCT", "");
		RAW_MAP.put("CASE", "");
		RAW_MAP.put("WHEN", "");
		RAW_MAP.put("THEN", "");
		RAW_MAP.put("ELSE", "");
		RAW_MAP.put("END", "");

		//时间
		RAW_MAP.put("now()", "");
		RAW_MAP.put("DATE", "");
		RAW_MAP.put("TIME", "");
		RAW_MAP.put("DATETIME", "");
		RAW_MAP.put("TIMESTAMP", "");
		RAW_MAP.put("DateTime", "");
		RAW_MAP.put("SECOND", "");
		RAW_MAP.put("MINUTE", "");
		RAW_MAP.put("HOUR", "");
		RAW_MAP.put("DAY", "");
		RAW_MAP.put("WEEK", "");
		RAW_MAP.put("MONTH", "");
		RAW_MAP.put("QUARTER", "");
		RAW_MAP.put("YEAR", "");
		//		RAW_MAP.put("json", "");
		//		RAW_MAP.put("unit", "");

		//MYSQL 数据类型 BINARY，CHAR，DATETIME，TIME，DECIMAL，SIGNED，UNSIGNED
		RAW_MAP.put("BINARY", "");
		RAW_MAP.put("SIGNED", "");
		RAW_MAP.put("DECIMAL", "");
		RAW_MAP.put("DOUBLE", "");
		RAW_MAP.put("FLOAT", "");
		RAW_MAP.put("BOOLEAN", "");
		RAW_MAP.put("ENUM", "");
		RAW_MAP.put("SET", "");
		RAW_MAP.put("POINT", "");
		RAW_MAP.put("BLOB", "");
		RAW_MAP.put("LONGBLOB", "");
		RAW_MAP.put("UNSIGNED", "");
		RAW_MAP.put("BIT", "");
		RAW_MAP.put("TINYINT", "");
		RAW_MAP.put("SMALLINT", "");
		RAW_MAP.put("INT", "");
		RAW_MAP.put("BIGINT", "");
		RAW_MAP.put("CHAR", "");
		RAW_MAP.put("VARCHAR", "");
		RAW_MAP.put("TEXT", "");
		RAW_MAP.put("LONGTEXT", "");
		RAW_MAP.put("JSON", "");

		//窗口函数关键字
		RAW_MAP.put("OVER", "");
		RAW_MAP.put("INTERVAL", "");
		RAW_MAP.put("GROUP BY", ""); // 往前
		RAW_MAP.put("GROUP", ""); // 往前
		RAW_MAP.put("ORDER BY", ""); // 往前
		RAW_MAP.put("ORDER", "");
		RAW_MAP.put("PARTITION BY", ""); // 往前
		RAW_MAP.put("PARTITION", ""); // 往前
		RAW_MAP.put("BY", "");
		RAW_MAP.put("DESC", "");
		RAW_MAP.put("ASC", "");
		RAW_MAP.put("PRECEDING", ""); // 往前
		RAW_MAP.put("FOLLOWING", ""); // 往后
		RAW_MAP.put("BETWEEN", "");
		RAW_MAP.put("ROWS", "");

		RAW_MAP.put("AGAINST", "");
		RAW_MAP.put("IN NATURAL LANGUAGE MODE", "");
		RAW_MAP.put("IN BOOLEAN MODE", "");
		RAW_MAP.put("IN", "");
		RAW_MAP.put("NATURAL", "");
		RAW_MAP.put("LANGUAGE", "");
		RAW_MAP.put("MODE", "");


		SQL_AGGREGATE_FUNCTION_MAP = new LinkedHashMap<>();  // 保证顺序，避免配置冲突等意外情况
		SQL_AGGREGATE_FUNCTION_MAP.put("max", ""); // MAX(a, b, c ...) 最大值
		SQL_AGGREGATE_FUNCTION_MAP.put("min", ""); // MIN(a, b, c ...) 最小值
		SQL_AGGREGATE_FUNCTION_MAP.put("avg", ""); // AVG(a, b, c ...) 平均值
		SQL_AGGREGATE_FUNCTION_MAP.put("count", ""); // COUNT(a, b, c ...) 总数
		SQL_AGGREGATE_FUNCTION_MAP.put("sum", ""); // SUM(a, b, c ...) 总和

		SQL_FUNCTION_MAP = new LinkedHashMap<>(); // 保证顺序，避免配置冲突等意外情况

		// 窗口函数
		SQL_FUNCTION_MAP.put("rank", ""); // RANK(a, b, c ...) 得到数据项在分组中的排名，排名相等的时候会留下空位
		SQL_FUNCTION_MAP.put("dense_rank", ""); // DENSE_RANK(a, b, c ...) 得到数据项在分组中的排名，排名相等的时候不会留下空位
		SQL_FUNCTION_MAP.put("row_num", ""); // ROW_NUM() 按照分组中的顺序生成序列，不存在重复的序列
		SQL_FUNCTION_MAP.put("row_number", ""); // ROW_NUMBER() 按照分组中的顺序生成序列，不存在重复的序列
		SQL_FUNCTION_MAP.put("ntile", ""); // NTILE(a, b, c ...) 用于将分组数据按照顺序切分成N片，返回当前切片值，不支持ROWS_BETWEE
		SQL_FUNCTION_MAP.put("first_value", ""); // FIRST_VALUE() 取分组排序后，截止到当前行，分组内第一个值
		SQL_FUNCTION_MAP.put("last_value", ""); // LAST_VALUE() 取分组排序后，截止到当前行，分组内的最后一个值
		SQL_FUNCTION_MAP.put("lag", ""); // LAG() 统计窗口内往上第n行值。第一个参数为列名，第二个参数为往上第n行（可选，默认为1），第三个参数为默认值（当往上第n行为NULL时候，取默认值，如不指定，则为NULL）
		SQL_FUNCTION_MAP.put("lead", ""); // LEAD() 统计窗口内往下第n行值。第一个参数为列名，第二个参数为往下第n行（可选，默认为1），第三个参数为默认值（当往下第n行为NULL时候，取默认值，如不指定，则为NULL）
		SQL_FUNCTION_MAP.put("cume_dist", ""); // CUME_DIST() 返回（小于等于当前行值的行数）/（当前分组内的总行数）
		SQL_FUNCTION_MAP.put("percent_rank", ""); // PERCENT_RANK(a, b, c ...) 返回（组内当前行的rank值-1）/（分组内做总行数-1）

		// MySQL 字符串函数
		SQL_FUNCTION_MAP.put("ascii", ""); // ASCII(s)	返回字符串 s 的第一个字符的 ASCII 码。
		SQL_FUNCTION_MAP.put("char_length", ""); // CHAR_LENGTH(s)	返回字符串 s 的字符数
		SQL_FUNCTION_MAP.put("character_length", ""); // CHARACTER_LENGTH(s)	返回字符串 s 的字符数
		SQL_FUNCTION_MAP.put("concat", ""); // CONCAT(s1, s2...sn)	字符串 s1,s2 等多个字符串合并为一个字符串
		SQL_FUNCTION_MAP.put("concat_ws", ""); // CONCAT_WS(x, s1, s2...sn)	同 CONCAT(s1, s2 ...) 函数，但是每个字符串之间要加上 x，x 可以是分隔符
		SQL_FUNCTION_MAP.put("field", ""); // FIELD(s, s1, s2...)	返回第一个字符串 s 在字符串列表 (s1, s2...)中的位置
		SQL_FUNCTION_MAP.put("find_in_set", ""); // FIND_IN_SET(s1, s2)	返回在字符串s2中与s1匹配的字符串的位置
		SQL_FUNCTION_MAP.put("format", ""); // FORMAT(x, n)	函数可以将数字 x 进行格式化 "#,###.##", 将 x 保留到小数点后 n 位，最后一位四舍五入。
		SQL_FUNCTION_MAP.put("insert", ""); // INSERT(s1, x, len, s2)	字符串 s2 替换 s1 的 x 位置开始长度为 len 的字符串
		SQL_FUNCTION_MAP.put("locate", ""); // LOCATE(s1, s)	从字符串 s 中获取 s1 的开始位置
		SQL_FUNCTION_MAP.put("lcase", ""); // LCASE(s)	将字符串 s 的所有字母变成小写字母
		SQL_FUNCTION_MAP.put("left", ""); // LEFT(s, n)	返回字符串 s 的前 n 个字符
		SQL_FUNCTION_MAP.put("length", ""); // LENGTH(s)	返回字符串 s 的字符数
		SQL_FUNCTION_MAP.put("lower", ""); // LOWER(s)	将字符串 s 的所有字母变成小写字母
		SQL_FUNCTION_MAP.put("lpad", ""); // LPAD(s1, len, s2)	在字符串 s1 的开始处填充字符串 s2，使字符串长度达到 len
		SQL_FUNCTION_MAP.put("ltrim", ""); // LTRIM(s)	去掉字符串 s 开始处的空格
		SQL_FUNCTION_MAP.put("mid", ""); // MID(s, n, len)	从字符串 s 的 n 位置截取长度为 len 的子字符串，同 SUBSTRING(s, n, len)
		SQL_FUNCTION_MAP.put("position", ""); // POSITION(s, s1);	从字符串 s 中获取 s1 的开始位置
		SQL_FUNCTION_MAP.put("repeat", ""); // REPEAT(s, n)	将字符串 s 重复 n 次
		SQL_FUNCTION_MAP.put("replace", ""); // REPLACE(s, s1, s2)	将字符串 s2 替代字符串 s 中的字符串 s1
		SQL_FUNCTION_MAP.put("reverse", ""); // REVERSE(s); // )	将字符串s的顺序反过来
		SQL_FUNCTION_MAP.put("right", ""); // RIGHT(s, n)	返回字符串 s 的后 n 个字符
		SQL_FUNCTION_MAP.put("rpad", ""); // RPAD(s1, len, s2)	在字符串 s1 的结尾处添加字符串 s2，使字符串的长度达到 len
		SQL_FUNCTION_MAP.put("rtrim", ""); // RTRIM", ""); // )	去掉字符串 s 结尾处的空格
		SQL_FUNCTION_MAP.put("space", ""); // SPACE(n)	返回 n 个空格
		SQL_FUNCTION_MAP.put("strcmp", ""); // STRCMP(s1, s2)	比较字符串 s1 和 s2，如果 s1 与 s2 相等返回 0 ，如果 s1>s2 返回 1，如果 s1<s2 返回 -1
		SQL_FUNCTION_MAP.put("substr", ""); // SUBSTR(s, start, length)	从字符串 s 的 start 位置截取长度为 length 的子字符串
		SQL_FUNCTION_MAP.put("substring", ""); // STRING(s, start, length))	从字符串 s 的 start 位置截取长度为 length 的子字符串
		SQL_FUNCTION_MAP.put("substring_index", ""); // SUBSTRING_INDEX(s, delimiter, number)	返回从字符串 s 的第 number 个出现的分隔符 delimiter 之后的子串。
		SQL_FUNCTION_MAP.put("trim", ""); // TRIM(s)	去掉字符串 s 开始和结尾处的空格
		SQL_FUNCTION_MAP.put("ucase", ""); // UCASE(s)	将字符串转换为大写
		SQL_FUNCTION_MAP.put("upper", ""); // UPPER(s)	将字符串转换为大写

		// MySQL 数字函数
		SQL_FUNCTION_MAP.put("abs", ""); // ABS(x)	返回 x 的绝对值　　
		SQL_FUNCTION_MAP.put("acos", ""); // ACOS(x)	求 x 的反余弦值(参数是弧度)
		SQL_FUNCTION_MAP.put("asin", ""); // ASIN(x)	求反正弦值(参数是弧度)
		SQL_FUNCTION_MAP.put("atan", ""); // ATAN(x)	求反正切值(参数是弧度)
		SQL_FUNCTION_MAP.put("atan2", ""); // ATAN2(n, m)	求反正切值(参数是弧度)
		SQL_FUNCTION_MAP.put("avg", ""); // AVG(expression)	返回一个表达式的平均值，expression 是一个字段
		SQL_FUNCTION_MAP.put("ceil", ""); // CEIL(x)	返回大于或等于 x 的最小整数　
		SQL_FUNCTION_MAP.put("ceiling", ""); // CEILING(x)	返回大于或等于 x 的最小整数　
		SQL_FUNCTION_MAP.put("cos", ""); // COS(x)	求余弦值(参数是弧度)
		SQL_FUNCTION_MAP.put("cot", ""); // COT(x)	求余切值(参数是弧度)
		SQL_FUNCTION_MAP.put("count", ""); // COUNT(expression)	返回查询的记录总数，expression 参数是一个字段或者 * 号
		SQL_FUNCTION_MAP.put("degrees", ""); // DEGREES(x)	将弧度转换为角度　　
		SQL_FUNCTION_MAP.put("div", ""); // n DIV m	整除，n 为被除数，m 为除数
		SQL_FUNCTION_MAP.put("exp", ""); // EXP(x)	返回 e 的 x 次方　　
		SQL_FUNCTION_MAP.put("floor", ""); // FLOOR(x)	返回小于或等于 x 的最大整数　　
		SQL_FUNCTION_MAP.put("greatest", ""); // GREATEST(expr1, expr2, expr3, ...)	返回列表中的最大值
		SQL_FUNCTION_MAP.put("least", ""); // LEAST(expr1, expr2, expr3, ...)	返回列表中的最小值
		SQL_FUNCTION_MAP.put("ln", ""); // 2);  LN	返回数字的自然对数，以 e 为底。
		SQL_FUNCTION_MAP.put("log", ""); // LOG(x) 或 LOG(base, x)	返回自然对数(以 e 为底的对数)，如果带有 base 参数，则 base 为指定带底数。　　
		SQL_FUNCTION_MAP.put("log10", ""); // LOG10(x)	返回以 10 为底的对数　　
		SQL_FUNCTION_MAP.put("log2", ""); // LOG2(x)	返回以 2 为底的对数
		SQL_FUNCTION_MAP.put("max", ""); // MAX(expression)	返回字段 expression 中的最大值
		SQL_FUNCTION_MAP.put("min", ""); // MIN(expression)	返回字段 expression 中的最小值
		SQL_FUNCTION_MAP.put("mod", ""); // MOD(x,y)	返回 x 除以 y 以后的余数　
		SQL_FUNCTION_MAP.put("pi", ""); // PI()	返回圆周率(3.141593）　　
		SQL_FUNCTION_MAP.put("pow", ""); // POW(x,y)	返回 x 的 y 次方　
		SQL_FUNCTION_MAP.put("power", ""); // POWER(x,y)	返回 x 的 y 次方　
		SQL_FUNCTION_MAP.put("radians", ""); // RADIANS(x)	将角度转换为弧度　　
		SQL_FUNCTION_MAP.put("rand", ""); // RAND()	返回 0 到 1 的随机数　　
		SQL_FUNCTION_MAP.put("round", ""); // ROUND(x)	返回离 x 最近的整数
		SQL_FUNCTION_MAP.put("sign", ""); // SIGN(x)	返回 x 的符号，x 是负数、0、正数分别返回 -1、0 和 1　
		SQL_FUNCTION_MAP.put("sin", ""); // SIN(x)	求正弦值(参数是弧度)　　
		SQL_FUNCTION_MAP.put("sqrt", ""); // SQRT(x)	返回x的平方根　　
		SQL_FUNCTION_MAP.put("sum", ""); // SUM(expression)	返回指定字段的总和
		SQL_FUNCTION_MAP.put("tan", ""); // TAN(x)	求正切值(参数是弧度)
		SQL_FUNCTION_MAP.put("truncate", ""); // TRUNCATE(x,y)	返回数值 x 保留到小数点后 y 位的值（与 ROUND 最大的区别是不会进行四舍五入）

		// MySQL 时间与日期函数
		SQL_FUNCTION_MAP.put("adddate", ""); // ADDDATE(d,n)	计算起始日期 d 加上 n 天的日期
		SQL_FUNCTION_MAP.put("addtime", ""); // ADDTIME(t,n)	n 是一个时间表达式，时间 t 加上时间表达式 n
		SQL_FUNCTION_MAP.put("curdate", ""); // CURDATE()	返回当前日期
		SQL_FUNCTION_MAP.put("current_date", ""); // CURRENT_DATE()	返回当前日期
		SQL_FUNCTION_MAP.put("current_time", ""); // CURRENT_TIME	返回当前时间
		SQL_FUNCTION_MAP.put("current_timestamp", ""); // CURRENT_TIMESTAMP()	返回当前日期和时间
		SQL_FUNCTION_MAP.put("curtime", ""); // CURTIME()	返回当前时间
		SQL_FUNCTION_MAP.put("date", ""); // DATE()	从日期或日期时间表达式中提取日期值
		SQL_FUNCTION_MAP.put("datediff", ""); // DATEDIFF(d1,d2)	计算日期 d1->d2 之间相隔的天数
		SQL_FUNCTION_MAP.put("date_add", ""); // DATE_ADD(d，INTERVAL expr type)	计算起始日期 d 加上一个时间段后的日期
		SQL_FUNCTION_MAP.put("date_format", ""); // DATE_FORMAT(d,f)	按表达式 f的要求显示日期 d
		SQL_FUNCTION_MAP.put("date_sub", ""); // DATE_SUB(date,INTERVAL expr type)	函数从日期减去指定的时间间隔。
		SQL_FUNCTION_MAP.put("day", ""); // DAY(d)	返回日期值 d 的日期部分
		SQL_FUNCTION_MAP.put("dayname", ""); // DAYNAME(d)	返回日期 d 是星期几，如 Monday,Tuesday
		SQL_FUNCTION_MAP.put("dayofmonth", ""); // DAYOFMONTH(d)	计算日期 d 是本月的第几天
		SQL_FUNCTION_MAP.put("dayofweek", ""); // DAYOFWEEK(d)	日期 d 今天是星期几，1 星期日，2 星期一，以此类推
		SQL_FUNCTION_MAP.put("dayofyear", ""); // DAYOFYEAR(d)	计算日期 d 是本年的第几天
		SQL_FUNCTION_MAP.put("extract", ""); // EXTRACT(type FROM d)	从日期 d 中获取指定的值，type 指定返回的值。
		SQL_FUNCTION_MAP.put("from_days", ""); // FROM_DAYS(n)	计算从 0000 年 1 月 1 日开始 n 天后的日期
		SQL_FUNCTION_MAP.put("hour", ""); // 'HOUR(t)	返回 t 中的小时值
		SQL_FUNCTION_MAP.put("last_day", ""); // LAST_DAY(d)	返回给给定日期的那一月份的最后一天
		SQL_FUNCTION_MAP.put("localtime", ""); // LOCALTIME()	返回当前日期和时间
		SQL_FUNCTION_MAP.put("localtimestamp", ""); // LOCALTIMESTAMP()	返回当前日期和时间
		SQL_FUNCTION_MAP.put("makedate", ""); // MAKEDATE(year, day-of-year)	基于给定参数年份 year 和所在年中的天数序号 day-of-year 返回一个日期
		SQL_FUNCTION_MAP.put("maketime", ""); // MAKETIME(hour, minute, second)	组合时间，参数分别为小时、分钟、秒
		SQL_FUNCTION_MAP.put("microsecond", ""); // MICROSECOND(date)	返回日期参数所对应的微秒数
		SQL_FUNCTION_MAP.put("minute", ""); // MINUTE(t)	返回 t 中的分钟值
		SQL_FUNCTION_MAP.put("monthname", ""); // MONTHNAME(d)	返回日期当中的月份名称，如 November
		SQL_FUNCTION_MAP.put("month", ""); // MONTH(d)	返回日期d中的月份值，1 到 12
		SQL_FUNCTION_MAP.put("now", ""); // NOW()	返回当前日期和时间
		SQL_FUNCTION_MAP.put("period_add", ""); // PERIOD_ADD(period, number)	为 年-月 组合日期添加一个时段
		SQL_FUNCTION_MAP.put("period_diff", ""); // PERIOD_DIFF(period1, period2)	返回两个时段之间的月份差值
		SQL_FUNCTION_MAP.put("quarter", ""); // QUARTER(d)	返回日期d是第几季节，返回 1 到 4
		SQL_FUNCTION_MAP.put("second", ""); // SECOND(t)	返回 t 中的秒钟值
		SQL_FUNCTION_MAP.put("sec_to_time", ""); // SEC_TO_TIME(sec, format); // )	将以秒为单位的时间 s 转换为时分秒的格式
		SQL_FUNCTION_MAP.put("str_to_date", ""); // STR_TO_DATE(string, format)	将字符串转变为日期
		SQL_FUNCTION_MAP.put("subdate", ""); // SUBDATE(d,n)	日期 d 减去 n 天后的日期
		SQL_FUNCTION_MAP.put("subtime", ""); // SUBTIME(t,n)	时间 t 减去 n 秒的时间
		SQL_FUNCTION_MAP.put("sysdate", ""); // SYSDATE()	返回当前日期和时间
		SQL_FUNCTION_MAP.put("time", ""); // TIME(expression)	提取传入表达式的时间部分
		SQL_FUNCTION_MAP.put("time_format", ""); // TIME_FORMAT(t,f)	按表达式 f 的要求显示时间 t
		SQL_FUNCTION_MAP.put("time_to_sec", ""); // TIME_TO_SEC(t)	将时间 t 转换为秒
		SQL_FUNCTION_MAP.put("timediff", ""); // TIMEDIFF(time1, time2)	计算时间差值
		SQL_FUNCTION_MAP.put("timestamp", ""); // TIMESTAMP(expression, interval)	单个参数时，函数返回日期或日期时间表达式；有2个参数时，将参数加和
		SQL_FUNCTION_MAP.put("to_days", ""); // TO_DAYS(d)	计算日期 d 距离 0000 年 1 月 1 日的天数
		SQL_FUNCTION_MAP.put("week", ""); // WEEK(d)	计算日期 d 是本年的第几个星期，范围是 0 到 53
		SQL_FUNCTION_MAP.put("weekday", ""); // WEEKDAY(d)	日期 d 是星期几，0 表示星期一，1 表示星期二
		SQL_FUNCTION_MAP.put("weekofyear", ""); // WEEKOFYEAR(d)	计算日期 d 是本年的第几个星期，范围是 0 到 53
		SQL_FUNCTION_MAP.put("year", ""); // YEAR(d)	返回年份
		SQL_FUNCTION_MAP.put("yearweek", ""); // YEARWEEK(date, mode)	返回年份及第几周（0到53），mode 中 0 表示周天，1表示周一，以此类推
		SQL_FUNCTION_MAP.put("unix_timestamp", ""); // UNIX_TIMESTAMP(date)	获取UNIX时间戳函数，返回一个以 UNIX 时间戳为基础的无符号整数
		SQL_FUNCTION_MAP.put("from_unixtime", ""); // FROM_UNIXTIME(date)	将 UNIX 时间戳转换为时间格式，与UNIX_TIMESTAMP互为反函数

		// MYSQL JSON 函数
		SQL_FUNCTION_MAP.put("json_append", ""); // JSON_APPEND(json_doc, path, val[, path, val] ...)) 插入JSON数组
		SQL_FUNCTION_MAP.put("json_array", ""); // JSON_ARRAY(val1, val2...) 创建JSON数组
		SQL_FUNCTION_MAP.put("json_array_append", ""); // JSON_ARRAY_APPEND(json_doc, val) 将数据附加到JSON文档
		SQL_FUNCTION_MAP.put("json_array_insert", ""); // JSON_ARRAY_INSERT(json_doc, val) 插入JSON数组
		SQL_FUNCTION_MAP.put("json_array_get", ""); // JSON_ARRAY_GET(json_doc, position) 从JSON数组提取指定位置的元素
		SQL_FUNCTION_MAP.put("json_contains", ""); // JSON_CONTAINS(json_doc, val) JSON文档是否在路径中包含特定对象
		SQL_FUNCTION_MAP.put("json_array_contains", ""); // JSON_ARRAY_CONTAINS(json_doc, path) JSON文档是否在路径中包含特定对象
		SQL_FUNCTION_MAP.put("json_contains_path", ""); // JSON_CONTAINS_PATH(json_doc, path) JSON文档是否在路径中包含任何数据
		SQL_FUNCTION_MAP.put("json_depth", ""); // JSON_DEPTH(json_doc) JSON文档的最大深度
		SQL_FUNCTION_MAP.put("json_extract", ""); // JSON_EXTRACT(json_doc, path) 从JSON文档返回数据
		SQL_FUNCTION_MAP.put("json_extract_scalar", ""); // JSON_EXTRACT_SCALAR(json_doc, path) 从JSON文档返回基础类型数据，例如 Boolean, Number, String
		SQL_FUNCTION_MAP.put("json_insert", ""); // JSON_INSERT(json_doc, val) 将数据插入JSON文档
		SQL_FUNCTION_MAP.put("json_keys", ""); // JSON_KEYS(json_doc[, path]) JSON文档中的键数组
		SQL_FUNCTION_MAP.put("json_length", ""); // JSON_LENGTH(json_doc) JSON文档中的元素数
		SQL_FUNCTION_MAP.put("json_size", ""); // JSON_SIZE(json_doc) JSON文档中的元素数
		SQL_FUNCTION_MAP.put("json_array_length", ""); // JSON_ARRAY_LENGTH(json_doc) JSON文档中的元素数
		SQL_FUNCTION_MAP.put("json_format", ""); // JSON_FORMAT(json_doc) 格式化 JSON
		SQL_FUNCTION_MAP.put("json_parse", ""); // JSON_PARSE(val) 转换为 JSON
		SQL_FUNCTION_MAP.put("json_merge", ""); // JSON_MERGE(json_doc1, json_doc2) （已弃用） 合并JSON文档，保留重复的键。JSON_MERGE_PRESERVE（）的已弃用同义词
		SQL_FUNCTION_MAP.put("json_merge_patch", ""); // JSON_MERGE_PATCH(json_doc1, json_doc2) 合并JSON文档，替换重复键的值
		SQL_FUNCTION_MAP.put("json_merge_preserve", ""); // JSON_MERGE_PRESERVE(json_doc1, json_doc2) 合并JSON文档，保留重复的键
		SQL_FUNCTION_MAP.put("json_object", ""); // JSON_OBJECT(key1, val1, key2, val2...) 创建JSON对象
		SQL_FUNCTION_MAP.put("json_overlaps", ""); // JSON_OVERLAPS(json_doc1, json_doc2) （引入8.0.17） 比较两个JSON文档，如果它们具有相同的键值对或数组元素，则返回TRUE（1），否则返回FALSE（0）
		SQL_FUNCTION_MAP.put("json_pretty", ""); // JSON_PRETTY(json_doc) 以易于阅读的格式打印JSON文档
		SQL_FUNCTION_MAP.put("json_quote", ""); // JSON_QUOTE(json_doc1) 引用JSON文档
		SQL_FUNCTION_MAP.put("json_remove", ""); // JSON_REMOVE(json_doc1, path) 从JSON文档中删除数据
		SQL_FUNCTION_MAP.put("json_replace", ""); // JSON_REPLACE(json_doc1, val1, val2) 替换JSON文档中的值
		SQL_FUNCTION_MAP.put("json_schema_valid", ""); // JSON_SCHEMA_VALID(json_doc) （引入8.0.17） 根据JSON模式验证JSON文档；如果文档针对架构进行验证，则返回TRUE / 1；否则，则返回FALSE / 0
		SQL_FUNCTION_MAP.put("json_schema_validation_report", ""); // JSON_SCHEMA_VALIDATION_REPORT(json_doc, mode) （引入8.0.17） 根据JSON模式验证JSON文档；以JSON格式返回有关验证结果的报告，包括成功或失败以及失败原因
		SQL_FUNCTION_MAP.put("json_search", ""); // JSON_SEARCH(json_doc, val) JSON文档中值的路径
		SQL_FUNCTION_MAP.put("json_set", ""); // JSON_SET(json_doc, val) 将数据插入JSON文档
		//		SQL_FUNCTION_MAP.put("json_storage_free", ""); // JSON_STORAGE_FREE() 部分更新后，JSON列值的二进制表示形式中的可用空间
		//		SQL_FUNCTION_MAP.put("json_storage_size", ""); // JSON_STORAGE_SIZE() 用于存储JSON文档的二进制表示的空间
		SQL_FUNCTION_MAP.put("json_table", ""); // JSON_TABLE() 从JSON表达式返回数据作为关系表
		SQL_FUNCTION_MAP.put("json_type", ""); // JSON_TYPE(json_doc) JSON值类型
		SQL_FUNCTION_MAP.put("json_unquote", ""); // JSON_UNQUOTE(json_doc) 取消引用JSON值
		SQL_FUNCTION_MAP.put("json_valid", ""); // JSON_VALID(json_doc) JSON值是否有效
		SQL_FUNCTION_MAP.put("json_arrayagg", ""); // JSON_ARRAYAGG(key) 将每个表达式转换为 JSON 值，然后返回一个包含这些 JSON 值的 JSON 数组
		SQL_FUNCTION_MAP.put("json_objectagg", ""); // JSON_OBJECTAGG(key, val))  将每个表达式转换为 JSON 值，然后返回一个包含这些 JSON 值的 JSON 对象
		SQL_FUNCTION_MAP.put("is_json_scalar", ""); // IS_JSON_SCALAR(val))  是否为JSON基本类型，例如 Boolean, Number, String

		// MySQL 高级函数
		//		SQL_FUNCTION_MAP.put("bin", ""); // BIN(x)	返回 x 的二进制编码
		//		SQL_FUNCTION_MAP.put("binary", ""); // BINARY(s)	将字符串 s 转换为二进制字符串
		SQL_FUNCTION_MAP.put("case", ""); // CASE 表示函数开始，END 表示函数结束。如果 condition1 成立，则返回 result1, 如果 condition2 成立，则返回 result2，当全部不成立则返回 result，而当有一个成立之后，后面的就不执行了。
		SQL_FUNCTION_MAP.put("cast", ""); // CAST(x AS type)	转换数据类型
		SQL_FUNCTION_MAP.put("coalesce", ""); // COALESCE(expr1, expr2, ...., expr_n)	返回参数中的第一个非空表达式（从左向右）
		//		SQL_FUNCTION_MAP.put("conv", ""); // CONV(x,f1,f2)	返回 f1 进制数变成 f2 进制数
		//		SQL_FUNCTION_MAP.put("convert", ""); // CONVERT(s, cs)	函数将字符串 s 的字符集变成 cs
		SQL_FUNCTION_MAP.put("if", ""); // IF(expr,v1,v2)	如果表达式 expr 成立，返回结果 v1；否则，返回结果 v2。
		SQL_FUNCTION_MAP.put("ifnull", ""); // IFNULL(v1,v2)  如果 v1 的值不为 NULL，则返回 v1，否则返回 v2。
		SQL_FUNCTION_MAP.put("isnull", ""); // ISNULL(expression)	判断表达式是否为 NULL
		SQL_FUNCTION_MAP.put("nullif", ""); // NULLIF(expr1, expr2)  比较两个字符串，如果字符串 expr1 与 expr2 相等 返回 NULL，否则返回 expr1
		SQL_FUNCTION_MAP.put("group_concat", ""); // GROUP_CONCAT([DISTINCT], s1, s2...)  聚合拼接字符串
		SQL_FUNCTION_MAP.put("match", ""); // MATCH (name,tag) AGAINST ('a b' IN NATURAL LANGUAGE MODE)  全文检索
		SQL_FUNCTION_MAP.put("any_value", ""); // any_value(userId) 解决 ONLY_FULL_GROUP_BY 报错


		// ClickHouse 字符串函数  注释的函数表示返回的格式暂时不支持，如：返回数组 ，同时包含因版本不同 clickhosue不支持的函数，版本
		SQL_FUNCTION_MAP.put("empty", ""); // empty(s) 对于空字符串s返回1，对于非空字符串返回0
		SQL_FUNCTION_MAP.put("notEmpty", ""); // notEmpty(s) 对于空字符串返回0，对于非空字符串返回1。
		SQL_FUNCTION_MAP.put("lengthUTF8", ""); // 假定字符串以UTF-8编码组成的文本，返回此字符串的Unicode字符长度。如果传入的字符串不是UTF-8编码，则函数可能返回一个预期外的值
		SQL_FUNCTION_MAP.put("lcase", ""); // 将字符串中的ASCII转换为小写
		SQL_FUNCTION_MAP.put("ucase", ""); // 将字符串中的ASCII转换为大写。
		SQL_FUNCTION_MAP.put("lowerUTF8", ""); // 将字符串转换为小写，函数假设字符串是以UTF-8编码文本的字符集。
		SQL_FUNCTION_MAP.put("upperUTF8", ""); // 将字符串转换为大写，函数假设字符串是以UTF-8编码文本的字符集。
		SQL_FUNCTION_MAP.put("isValidUTF8", ""); // 检查字符串是否为有效的UTF-8编码，是则返回1，否则返回0。
		SQL_FUNCTION_MAP.put("toValidUTF8", ""); // 用（U+FFFD）字符替换无效的UTF-8字符。所有连续的无效字符都会被替换为一个替换字符。
		SQL_FUNCTION_MAP.put("reverseUTF8", ""); // 以Unicode字符为单位反转UTF-8编码的字符串。
		SQL_FUNCTION_MAP.put("concatAssumeInjective", ""); // concatAssumeInjective(s1, s2, …) 与concat相同，区别在于，你需要保证concat(s1, s2, s3) -> s4是单射的，它将用于GROUP BY的优化。
		SQL_FUNCTION_MAP.put("substringUTF8", ""); // substringUTF8(s,offset,length)¶ 与’substring’相同，但其操作单位为Unicode字符，函数假设字符串是以UTF-8进行编码的文本。如果不是则可能返回一个预期外的结果（不会抛出异常）。
		SQL_FUNCTION_MAP.put("appendTrailingCharIfAbsent", ""); // appendTrailingCharIfAbsent(s,c) 如果’s’字符串非空并且末尾不包含’c’字符，则将’c’字符附加到末尾
		SQL_FUNCTION_MAP.put("convertCharset", ""); // convertCharset(s,from,to) 返回从’from’中的编码转换为’to’中的编码的字符串’s’。
		SQL_FUNCTION_MAP.put("base64Encode", ""); // base64Encode(s) 将字符串’s’编码成base64
		SQL_FUNCTION_MAP.put("base64Decode", ""); // base64Decode(s)  使用base64将字符串解码成原始字符串。如果失败则抛出异常。
		SQL_FUNCTION_MAP.put("tryBase64Decode", ""); // tryBase64Decode(s) 使用base64将字符串解码成原始字符串。但如果出现错误，将返回空字符串。
		SQL_FUNCTION_MAP.put("endsWith", ""); // endsWith(s,后缀) 返回是否以指定的后缀结尾。如果字符串以指定的后缀结束，则返回1，否则返回0。
		SQL_FUNCTION_MAP.put("startsWith", ""); // startsWith（s，前缀) 返回是否以指定的前缀开头。如果字符串以指定的前缀开头，则返回1，否则返回0。
		SQL_FUNCTION_MAP.put("trimLeft", ""); // trimLeft(s)返回一个字符串，用于删除左侧的空白字符。
		SQL_FUNCTION_MAP.put("trimRight", ""); // trimRight(s) 返回一个字符串，用于删除右侧的空白字符。
		SQL_FUNCTION_MAP.put("trimBoth", ""); // trimBoth(s)，用于删除任一侧的空白字符
		SQL_FUNCTION_MAP.put("extractAllGroups", ""); // extractAllGroups(text, regexp) 从正则表达式匹配的非重叠子字符串中提取所有组
		// SQL_FUNCTION_MAP.put("leftPad", ""); // leftPad('string', 'length'[, 'pad_string']) 用空格或指定的字符串从左边填充当前字符串(如果需要，可以多次)，直到得到的字符串达到给定的长度
		// SQL_FUNCTION_MAP.put("leftPadUTF8", ""); // leftPadUTF8('string','length'[, 'pad_string']) 用空格或指定的字符串从左边填充当前字符串(如果需要，可以多次)，直到得到的字符串达到给定的长度
		// SQL_FUNCTION_MAP.put("rightPad", ""); // rightPad('string', 'length'[, 'pad_string']) 用空格或指定的字符串(如果需要，可以多次)从右边填充当前字符串，直到得到的字符串达到给定的长度
		// SQL_FUNCTION_MAP.put("rightPadUTF8", "");// rightPadUTF8('string','length'[, 'pad_string'])  用空格或指定的字符串(如果需要，可以多次)从右边填充当前字符串，直到得到的字符串达到给定的长度。
		SQL_FUNCTION_MAP.put("normalizeQuery", ""); // normalizeQuery(x)  用占位符替换文字、文字序列和复杂的别名。
		SQL_FUNCTION_MAP.put("normalizedQueryHash", ""); // normalizedQueryHash(x) 为类似查询返回相同的64位散列值，但不包含文字值。有助于对查询日志进行分析
		SQL_FUNCTION_MAP.put("positionUTF8", ""); // positionUTF8(s, needle[, start_pos]) 返回在字符串中找到的子字符串的位置(以Unicode点表示)，从1开始。
		SQL_FUNCTION_MAP.put("multiSearchFirstIndex", ""); // multiSearchFirstIndex(s, [needle1, needle2, …, needlen]) 返回字符串s中最左边的needlei的索引i(从1开始)，否则返回0
		SQL_FUNCTION_MAP.put("multiSearchAny", ""); // multiSearchAny(s, [needle1, needle2, …, needlen])如果至少有一个字符串needlei匹配字符串s，则返回1，否则返回0。
		SQL_FUNCTION_MAP.put("match", ""); // match(s, pattern) 检查字符串是否与模式正则表达式匹配。re2正则表达式。re2正则表达式的语法比Perl正则表达式的语法更有局限性。
		SQL_FUNCTION_MAP.put("multiMatchAny", ""); // multiMatchAny(s, [pattern1, pattern2, …, patternn]) 与match相同，但是如果没有匹配的正则表达式返回0，如果有匹配的模式返回1
		SQL_FUNCTION_MAP.put("multiMatchAnyIndex", ""); // multiMatchAnyIndex(s, [pattern1, pattern2, …, patternn]) 与multiMatchAny相同，但返回与干堆匹配的任何索引
		SQL_FUNCTION_MAP.put("extract", ""); // extract(s, pattern)  使用正则表达式提取字符串的片段
		SQL_FUNCTION_MAP.put("extractAll", ""); // extractAll(s, pattern) 使用正则表达式提取字符串的所有片段
		SQL_FUNCTION_MAP.put("like", ""); // like(s, pattern) 检查字符串是否与简单正则表达式匹配
		SQL_FUNCTION_MAP.put("notLike", ""); // 和‘like’是一样的，但是是否定的
		SQL_FUNCTION_MAP.put("countSubstrings", ""); // countSubstrings(s, needle[, start_pos])返回子字符串出现的次数
		SQL_FUNCTION_MAP.put("countMatches", ""); // 返回干s中的正则表达式匹配数。countMatches(s, pattern)
		SQL_FUNCTION_MAP.put("replaceOne", ""); // replaceOne(s, pattern, replacement)将' s '中的' pattern '子串的第一个出现替换为' replacement '子串。

		SQL_FUNCTION_MAP.put("replaceAll", ""); // replaceAll(s, pattern, replacement)/用' replacement '子串替换' s '中所有出现的' pattern '子串
		SQL_FUNCTION_MAP.put("replaceRegexpOne", ""); // replaceRegexpOne(s, pattern, replacement)使用' pattern '正则表达式进行替换
		SQL_FUNCTION_MAP.put("replaceRegexpAll", ""); // replaceRegexpAll(s, pattern, replacement)
		SQL_FUNCTION_MAP.put("regexpQuoteMeta", ""); // regexpQuoteMeta(s)该函数在字符串中某些预定义字符之前添加一个反斜杠

		// clickhouse日期函数
		SQL_FUNCTION_MAP.put("toYear", ""); // 将Date或DateTime转换为包含年份编号（AD）的UInt16类型的数字。
		SQL_FUNCTION_MAP.put("toQuarter", ""); // 将Date或DateTime转换为包含季度编号的UInt8类型的数字。
		SQL_FUNCTION_MAP.put("toMonth", ""); // Date或DateTime转换为包含月份编号（1-12）的UInt8类型的数字。
		SQL_FUNCTION_MAP.put("toDayOfYear", ""); // 将Date或DateTime转换为包含一年中的某一天的编号的UInt16（1-366）类型的数字。
		SQL_FUNCTION_MAP.put("toDayOfMonth", "");// 将Date或DateTime转换为包含一月中的某一天的编号的UInt8（1-31）类型的数字。
		SQL_FUNCTION_MAP.put("toDayOfWeek", ""); // 将Date或DateTime转换为包含一周中的某一天的编号的UInt8（周一是1, 周日是7）类型的数字。
		SQL_FUNCTION_MAP.put("toHour", ""); // 将DateTime转换为包含24小时制（0-23）小时数的UInt8数字。
		SQL_FUNCTION_MAP.put("toMinute", ""); // 将DateTime转换为包含一小时中分钟数（0-59）的UInt8数字。
		SQL_FUNCTION_MAP.put("toSecond", ""); // 将DateTime转换为包含一分钟中秒数（0-59）的UInt8数字。
		SQL_FUNCTION_MAP.put("toUnixTimestamp", ""); // 对于DateTime参数：将值转换为UInt32类型的数字-Unix时间戳
		SQL_FUNCTION_MAP.put("toStartOfYear", ""); // 将Date或DateTime向前取整到本年的第一天。
		SQL_FUNCTION_MAP.put("toStartOfISOYear", ""); // 将Date或DateTime向前取整到ISO本年的第一天。
		SQL_FUNCTION_MAP.put("toStartOfQuarter", "");// 将Date或DateTime向前取整到本季度的第一天。
		SQL_FUNCTION_MAP.put("toStartOfMonth", ""); // 将Date或DateTime向前取整到本月的第一天。
		SQL_FUNCTION_MAP.put("toMonday", "");  // 将Date或DateTime向前取整到本周的星期
		SQL_FUNCTION_MAP.put("toStartOfWeek", ""); // 按mode将Date或DateTime向前取整到最近的星期日或星期一。
		SQL_FUNCTION_MAP.put("toStartOfDay", ""); // 将DateTime向前取整到今天的开始。
		SQL_FUNCTION_MAP.put("toStartOfHour", ""); // 将DateTime向前取整到当前小时的开始。
		SQL_FUNCTION_MAP.put("toStartOfMinute", ""); // 将DateTime向前取整到当前分钟的开始。
		SQL_FUNCTION_MAP.put("toStartOfSecond", ""); // 将DateTime向前取整到当前秒数的开始。
		SQL_FUNCTION_MAP.put("toStartOfFiveMinute", "");// 将DateTime以五分钟为单位向前取整到最接近的时间点。
		SQL_FUNCTION_MAP.put("toStartOfTenMinutes", ""); // 将DateTime以十分钟为单位向前取整到最接近的时间点。
		SQL_FUNCTION_MAP.put("toStartOfFifteenMinutes", ""); // 将DateTime以十五分钟为单位向前取整到最接近的时间点。
		SQL_FUNCTION_MAP.put("toStartOfInterval", ""); //
		SQL_FUNCTION_MAP.put("toTime", ""); // 将DateTime中的日期转换为一个固定的日期，同时保留时间部分。
		SQL_FUNCTION_MAP.put("toISOYear", ""); // 将Date或DateTime转换为包含ISO年份的UInt16类型的编号。
		SQL_FUNCTION_MAP.put("toISOWeek", ""); //
		SQL_FUNCTION_MAP.put("toWeek", "");// 返回Date或DateTime的周数。
		SQL_FUNCTION_MAP.put("toYearWeek", ""); // 返回年和周的日期
		SQL_FUNCTION_MAP.put("date_trunc", ""); // 截断日期和时间数据到日期的指定部分
		SQL_FUNCTION_MAP.put("date_diff", ""); // 回两个日期或带有时间值的日期之间的差值。

		SQL_FUNCTION_MAP.put("yesterday", ""); // 不接受任何参数并在请求执行时的某一刻返回昨天的日期(Date)。
		SQL_FUNCTION_MAP.put("today", ""); // 不接受任何参数并在请求执行时的某一刻返回当前日期(Date)。
		SQL_FUNCTION_MAP.put("timeSlot", ""); // 将时间向前取整半小时。
		SQL_FUNCTION_MAP.put("toYYYYMM", ""); //
		SQL_FUNCTION_MAP.put("toYYYYMMDD", "");//
		SQL_FUNCTION_MAP.put("toYYYYMMDDhhmmss", ""); //
		SQL_FUNCTION_MAP.put("addYears", ""); // Function adds a Date/DateTime interval to a Date/DateTime and then return the Date/DateTime
		SQL_FUNCTION_MAP.put("addMonths", ""); // 同上
		SQL_FUNCTION_MAP.put("addWeeks", ""); // 同上
		SQL_FUNCTION_MAP.put("addDays", ""); // 同上
		SQL_FUNCTION_MAP.put("addHours", ""); // 同上
		SQL_FUNCTION_MAP.put("addMinutes", "");// 同上
		SQL_FUNCTION_MAP.put("addSeconds", ""); // 同上
		SQL_FUNCTION_MAP.put("addQuarters", ""); // 同上
		SQL_FUNCTION_MAP.put("subtractYears", ""); // Function subtract a Date/DateTime interval to a Date/DateTime and then return the Date/DateTime
		SQL_FUNCTION_MAP.put("subtractMonths", ""); // 同上
		SQL_FUNCTION_MAP.put("subtractWeeks", ""); // 同上
		SQL_FUNCTION_MAP.put("subtractDays", ""); // 同上
		SQL_FUNCTION_MAP.put("subtractours", "");// 同上
		SQL_FUNCTION_MAP.put("subtractMinutes", ""); // 同上
		SQL_FUNCTION_MAP.put("subtractSeconds", ""); // 同上
		SQL_FUNCTION_MAP.put("subtractQuarters", ""); // 同上
		SQL_FUNCTION_MAP.put("formatDateTime", ""); // 函数根据给定的格式字符串来格式化时间
		SQL_FUNCTION_MAP.put("timestamp_add", ""); // 使用提供的日期或日期时间值添加指定的时间值。
		SQL_FUNCTION_MAP.put("timestamp_sub", ""); // 从提供的日期或带时间的日期中减去时间间隔。

		// ClickHouse json函数
		SQL_FUNCTION_MAP.put("visitParamHas", ""); // visitParamHas(params, name)检查是否存在«name»名称的字段
		SQL_FUNCTION_MAP.put("visitParamExtractUInt", ""); // visitParamExtractUInt(params, name)将名为«name»的字段的值解析成UInt64。
		SQL_FUNCTION_MAP.put("visitParamExtractInt", ""); // 与visitParamExtractUInt相同，但返回Int64。
		SQL_FUNCTION_MAP.put("visitParamExtractFloat", ""); // 与visitParamExtractUInt相同，但返回Float64。
		SQL_FUNCTION_MAP.put("visitParamExtractBool", "");// 解析true/false值。其结果是UInt8类型的。
		SQL_FUNCTION_MAP.put("visitParamExtractRaw", ""); // 返回字段的值，包含空格符。
		SQL_FUNCTION_MAP.put("visitParamExtractString", ""); // 使用双引号解析字符串。这个值没有进行转义。如果转义失败，它将返回一个空白字符串。
		SQL_FUNCTION_MAP.put("JSONHas", ""); // 如果JSON中存在该值，则返回1。
		SQL_FUNCTION_MAP.put("JSONLength", ""); // 返回JSON数组或JSON对象的长度。
		SQL_FUNCTION_MAP.put("JSONType", ""); // 返回JSON值的类型。
		SQL_FUNCTION_MAP.put("JSONExtractUInt", ""); // 解析JSON并提取值。这些函数类似于visitParam*函数。
		SQL_FUNCTION_MAP.put("JSONExtractInt", ""); //
		SQL_FUNCTION_MAP.put("JSONExtractFloat", ""); //
		SQL_FUNCTION_MAP.put("JSONExtractBool", ""); //
		SQL_FUNCTION_MAP.put("JSONExtractString", ""); // 解析JSON并提取字符串。此函数类似于visitParamExtractString函数。
		SQL_FUNCTION_MAP.put("JSONExtract", ""); // 解析JSON并提取给定ClickHouse数据类型的值。
		SQL_FUNCTION_MAP.put("JSONExtractKeysAndValues", ""); // 从JSON中解析键值对，其中值是给定的ClickHouse数据类型
		SQL_FUNCTION_MAP.put("JSONExtractRaw", ""); // 返回JSON的部分。
		SQL_FUNCTION_MAP.put("toJSONString", ""); //

		// ClickHouse 类型转换函数
		SQL_FUNCTION_MAP.put("toInt8", ""); // toInt8(expr)  转换一个输入值为Int类型
		SQL_FUNCTION_MAP.put("toInt16", "");
		SQL_FUNCTION_MAP.put("toInt32", "");
		SQL_FUNCTION_MAP.put("toInt64", "");
		SQL_FUNCTION_MAP.put("toInt8OrZero", ""); // toInt(8|16|32|64)OrZero 尝试把字符串转为 Int
		SQL_FUNCTION_MAP.put("toInt16OrZero", "");
		SQL_FUNCTION_MAP.put("toInt32OrZero", "");
		SQL_FUNCTION_MAP.put("toInt64OrZero", "");
		SQL_FUNCTION_MAP.put("toInt8OrNull", "");// toInt(8|16|32|64)O 尝试把字符串转为 Int
		SQL_FUNCTION_MAP.put("toInt16OrNull", "");
		SQL_FUNCTION_MAP.put("toInt32OrNull", "");
		SQL_FUNCTION_MAP.put("toInt64OrNull", "");
		SQL_FUNCTION_MAP.put("toUInt8", ""); // toInt8(expr)  转换一个输入值为Int类型
		SQL_FUNCTION_MAP.put("toUInt16", "");
		SQL_FUNCTION_MAP.put("toUInt32", "");
		SQL_FUNCTION_MAP.put("toUInt64", "");
		SQL_FUNCTION_MAP.put("toUInt8OrZero", ""); // toInt(8|16|32|64)OrZero 尝试把字符串转为 Int
		SQL_FUNCTION_MAP.put("toUInt16OrZero", "");
		SQL_FUNCTION_MAP.put("toUInt32OrZero", "");
		SQL_FUNCTION_MAP.put("toUInt64OrZero", "");
		SQL_FUNCTION_MAP.put("toUInt8OrNull", ""); // toInt(8|16|32|64)OrNull 尝试把字符串转为 Int
		SQL_FUNCTION_MAP.put("toUInt16OrNull", "");
		SQL_FUNCTION_MAP.put("toUInt32OrNull", "");
		SQL_FUNCTION_MAP.put("toUInt64OrNull", "");

		SQL_FUNCTION_MAP.put("toFloat32", "");
		SQL_FUNCTION_MAP.put("toFloat64", "");
		SQL_FUNCTION_MAP.put("toFloat32OrZero", "");
		SQL_FUNCTION_MAP.put("toFloat64OrZero", "");
		SQL_FUNCTION_MAP.put("toFloat32OrNull", "");
		SQL_FUNCTION_MAP.put("toFloat64OrNull", "");

		SQL_FUNCTION_MAP.put("toDate", ""); //
		SQL_FUNCTION_MAP.put("toDateOrZero", ""); // toInt16(expr)
		SQL_FUNCTION_MAP.put("toDateOrNull", ""); // toInt32(expr)
		SQL_FUNCTION_MAP.put("toDateTimeOrZero", ""); // toInt64(expr) 尝试把字符串转为 DateTime
		SQL_FUNCTION_MAP.put("toDateTimeOrNull", ""); // toInt(8|16|32|64) 尝试把字符串转为 DateTime

		SQL_FUNCTION_MAP.put("toDecimal32", "");
		SQL_FUNCTION_MAP.put("toFixedString", ""); // 将String类型的参数转换为FixedString(N)类型的值
		SQL_FUNCTION_MAP.put("toStringCutToZero", ""); // 接受String或FixedString参数,返回String，其内容在找到的第一个零字节处被截断。
		SQL_FUNCTION_MAP.put("toDecimal256", "");
		SQL_FUNCTION_MAP.put("toDecimal32OrNull", "");
		SQL_FUNCTION_MAP.put("toDecimal64OrNull", "");
		SQL_FUNCTION_MAP.put("toDecimal128OrNull", "");
		SQL_FUNCTION_MAP.put("toDecimal256OrNull", "");
		SQL_FUNCTION_MAP.put("toDecimal32OrZero", "");
		SQL_FUNCTION_MAP.put("toDecimal64OrZero", "");
		SQL_FUNCTION_MAP.put("toDecimal128OrZero", "");
		SQL_FUNCTION_MAP.put("toDecimal256OrZero", "");

		SQL_FUNCTION_MAP.put("toIntervalSecond", ""); // 把一个数值类型的值转换为Interval类型的数据。
		SQL_FUNCTION_MAP.put("toIntervalMinute", "");
		SQL_FUNCTION_MAP.put("toIntervalHour", "");
		SQL_FUNCTION_MAP.put("toIntervalDay", "");
		SQL_FUNCTION_MAP.put("toIntervalWeek", "");
		SQL_FUNCTION_MAP.put("toIntervalMonth", "");
		SQL_FUNCTION_MAP.put("toIntervalQuarter", "");
		SQL_FUNCTION_MAP.put("toIntervalYear", "");
		SQL_FUNCTION_MAP.put("parseDateTimeBestEffort", ""); // 把String类型的时间日期转换为DateTime数据类型。
		SQL_FUNCTION_MAP.put("parseDateTimeBestEffortOrNull", "");
		SQL_FUNCTION_MAP.put("parseDateTimeBestEffortOrZero", "");
		SQL_FUNCTION_MAP.put("toLowCardinality", "");


		// ClickHouse hash 函数
		SQL_FUNCTION_MAP.put("halfMD5", ""); // 计算字符串的MD5。然后获取结果的前8个字节并将它们作为UInt64（大端）返回
		SQL_FUNCTION_MAP.put("MD5", ""); // 计算字符串的MD5并将结果放入FixedString(16)中返回

		// ClickHouse ip 地址函数
		SQL_FUNCTION_MAP.put("IPv4NumToString", ""); // 接受一个 UInt32（大端）表示的IPv4的地址，返回相应IPv4的字符串表现形式
		SQL_FUNCTION_MAP.put("IPv4StringToNum", ""); // 与IPv4NumToString函数相反。如果IPv4地址格式无效，则返回0。
		SQL_FUNCTION_MAP.put("IPv6NumToString", ""); // 接受FixedString(16)类型的二进制格式的IPv6地址。以文本格式返回此地址的字符串。
		SQL_FUNCTION_MAP.put("IPv6StringToNum", ""); // 与IPv6NumToString的相反。如果IPv6地址格式无效，则返回空字节字符串。
		SQL_FUNCTION_MAP.put("IPv4ToIPv6", ""); // 接受一个UInt32类型的IPv4地址，返回FixedString(16)类型的IPv6地址
		SQL_FUNCTION_MAP.put("cutIPv6", ""); // 接受一个FixedString(16)类型的IPv6地址，返回 String，包含删除指定位之后的地址的文本格
		SQL_FUNCTION_MAP.put("toIPv4", ""); // IPv4StringToNum()的别名，
		SQL_FUNCTION_MAP.put("toIPv6", ""); // IPv6StringToNum()的别名
		SQL_FUNCTION_MAP.put("isIPAddressInRange", ""); // 确定一个IP地址是否包含在以CIDR符号表示的网络中

		// ClickHouse Nullable 处理函数
		SQL_FUNCTION_MAP.put("isNull", ""); // 检查参数是否为NULL。
		SQL_FUNCTION_MAP.put("isNotNull", ""); // 检查参数是否不为 NULL.
		SQL_FUNCTION_MAP.put("ifNull", ""); // 如果第一个参数为«NULL»，则返回第二个参数的值。
		SQL_FUNCTION_MAP.put("assumeNotNull", ""); // 将可为空类型的值转换为非Nullable类型的值。
		SQL_FUNCTION_MAP.put("toNullable", ""); // 将参数的类型转换为Nullable。

		// ClickHouse UUID 函数
		SQL_FUNCTION_MAP.put("generateUUIDv4", ""); // 生成一个UUID
		SQL_FUNCTION_MAP.put("toUUID", ""); // toUUID(x) 将String类型的值转换为UUID类型的值。

		// ClickHouse 系统函数
		SQL_FUNCTION_MAP.put("hostName", ""); // hostName()回一个字符串，其中包含执行此函数的主机的名称。
		SQL_FUNCTION_MAP.put("getMacro", ""); // 从服务器配置的宏部分获取指定值。
		SQL_FUNCTION_MAP.put("FQDN", ""); // 返回完全限定的域名。
		SQL_FUNCTION_MAP.put("basename", ""); // 提取字符串最后一个斜杠或反斜杠之后的尾随部分
		SQL_FUNCTION_MAP.put("currentUser", ""); // 返回当前用户的登录。在分布式查询的情况下，将返回用户的登录，即发起的查询
		SQL_FUNCTION_MAP.put("version", ""); // 以字符串形式返回服务器版本。
		SQL_FUNCTION_MAP.put("uptime", ""); // 以秒为单位返回服务器的正常运行时间。

		// ClickHouse 数学函数
		SQL_FUNCTION_MAP.put("least", ""); // least(a, b) 返回a和b中最小的值。
		SQL_FUNCTION_MAP.put("greatest", ""); // greatest(a, b) 返回a和b的最大值。
		SQL_FUNCTION_MAP.put("plus", ""); // plus(a, b), a + b operator¶计算数值的总和。
		SQL_FUNCTION_MAP.put("minus", ""); // minus(a, b), a - b operator 计算数值之间的差，结果总是有符号的。
		SQL_FUNCTION_MAP.put("multiply", "");// multiply(a, b), a * b operator 计算数值的乘积
		SQL_FUNCTION_MAP.put("divide", ""); // divide(a, b), a / b operator 计算数值的商。结果类型始终是浮点类型
		SQL_FUNCTION_MAP.put("intDiv", ""); // intDiv(a,b)计算数值的商，向下舍入取整（按绝对值）。
		SQL_FUNCTION_MAP.put("intDivOrZero", ""); // intDivOrZero(a,b)与’intDiv’的不同之处在于它在除以零或将最小负数除以-1时返回零。
		SQL_FUNCTION_MAP.put("modulo", ""); // modulo(a, b), a % b operator 计算除法后的余数。
		SQL_FUNCTION_MAP.put("moduloOrZero", ""); // 和modulo不同之处在于，除以0时结果返回0
		SQL_FUNCTION_MAP.put("negate", ""); // 通过改变数值的符号位对数值取反，结果总是有符号
		SQL_FUNCTION_MAP.put("gcd", ""); // gcd(a,b) 返回数值的最大公约数。
		SQL_FUNCTION_MAP.put("lcm", ""); // lcm(a,b) 返回数值的最小公倍数
		SQL_FUNCTION_MAP.put("e", ""); // e() 返回一个接近数学常量e的Float64数字。
		SQL_FUNCTION_MAP.put("pi", ""); // pi() 返回一个接近数学常量π的Float64数字。
		SQL_FUNCTION_MAP.put("exp2", ""); // exp2(x)¶接受一个数值类型的参数并返回它的2的x次幂。
		SQL_FUNCTION_MAP.put("exp10", ""); // exp10(x)¶接受一个数值类型的参数并返回它的10的x次幂。
		SQL_FUNCTION_MAP.put("cbrt", ""); // cbrt(x) 接受一个数值类型的参数并返回它的立方根。
		SQL_FUNCTION_MAP.put("lgamma", ""); // lgamma(x) 返回x的绝对值的自然对数的伽玛函数。
		SQL_FUNCTION_MAP.put("tgamma", ""); // tgamma(x)¶返回x的伽玛函数。
		SQL_FUNCTION_MAP.put("intExp2", ""); // intExp2 接受一个数值类型的参数并返回它的2的x次幂（UInt64）
		SQL_FUNCTION_MAP.put("intExp10", ""); // intExp10 接受一个数值类型的参数并返回它的10的x次幂（UInt64）。
		SQL_FUNCTION_MAP.put("cosh", ""); // cosh(x)
		SQL_FUNCTION_MAP.put("sinh", ""); // sinh(x)
		SQL_FUNCTION_MAP.put("asinh", ""); // asinh(x)
		SQL_FUNCTION_MAP.put("atanh", ""); // atanh(x)
		SQL_FUNCTION_MAP.put("atan2", ""); // atan2(y, x)
		SQL_FUNCTION_MAP.put("hypot", ""); // hypot(x, y)
		SQL_FUNCTION_MAP.put("log1p", ""); // log1p(x)
		SQL_FUNCTION_MAP.put("trunc", ""); // 和 truncate 一样
		SQL_FUNCTION_MAP.put("roundToExp2", ""); // roundToExp2(num) 接受一个数字。如果数字小于1，它返回0。
		SQL_FUNCTION_MAP.put("roundDuration", ""); // roundDuration(num) 接受一个数字。如果数字小于1，它返回0。
		SQL_FUNCTION_MAP.put("roundAge", ""); // roundAge(age) 接受一个数字。如果数字小于18，它返回0。
		SQL_FUNCTION_MAP.put("roundDown", ""); // roundDown(num, arr) 接受一个数字并将其舍入到指定数组中的一个元素
		SQL_FUNCTION_MAP.put("bitAnd", ""); // bitAnd(a,b)
		SQL_FUNCTION_MAP.put("bitOr", ""); // bitOr(a,b)

		// PostgreSQL 表结构相关 SQL 函数
		SQL_FUNCTION_MAP.put("obj_description", "");
		SQL_FUNCTION_MAP.put("col_description", "");

		// SQLServer 相关 SQL 函数
		SQL_FUNCTION_MAP.put("len", "");
		SQL_FUNCTION_MAP.put("datalength", "");

		// Milvus 相关 SQL 函数
		SQL_FUNCTION_MAP.put("vMatch", "");
		SQL_FUNCTION_MAP.put("consistencyLevel", "");
		SQL_FUNCTION_MAP.put("partitionBy", "");
		SQL_FUNCTION_MAP.put("gracefulTime", "");
		SQL_FUNCTION_MAP.put("guaranteeTimestamp", "");
		SQL_FUNCTION_MAP.put("roundDecimal", "");
		SQL_FUNCTION_MAP.put("travelTimestamp", "");
		SQL_FUNCTION_MAP.put("nProbe", "");
		SQL_FUNCTION_MAP.put("ef", "");
		SQL_FUNCTION_MAP.put("searchK", "");

	}

	private Parser<T, M, L> parser;
	@Override
	public Parser<T, M, L> gainParser() {
		if (parser == null && objectParser != null) {
			parser = objectParser.getParser();
		}
		return parser;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setParser(Parser<T, M, L> parser) {
		this.parser = parser;
		return this;
	}
	public AbstractSQLConfig<T, M, L> putWarnIfNeed(String type, String warn) {
		if (Log.DEBUG && parser instanceof AbstractParser) {
			((AbstractParser<T, M, L>) parser).putWarnIfNeed(type, warn);
		}
		return this;
	}
	public AbstractSQLConfig<T, M, L> putWarn(String type, String warn) {
		if (Log.DEBUG && parser instanceof AbstractParser) {
			((AbstractParser<T, M, L>) parser).putWarn(type, warn);
		}
		return this;
	}

	private ObjectParser<T, M, L> objectParser;
	@Override
	public ObjectParser<T, M, L> gainObjectParser() {
		return objectParser;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setObjectParser(ObjectParser<T, M, L> objectParser) {
		this.objectParser = objectParser;
		return this;
	}

	private int version;
	@Override
	public int getVersion() {
		if (version <= 0 && parser != null) {
			version = parser.getVersion();
		}
		return version;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setVersion(int version) {
		this.version = version;
		return this;
	}

	private String tag;
	@Override
	public String getTag() {
		if (StringUtil.isEmpty(tag) && parser != null) {
			tag = parser.getTag();
		}
		return tag;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setTag(String tag) {
		this.tag = tag;
		return this;
	}

	// mysql8版本以上,子查询支持with as表达式
	private List<String> withAsExprSQLList = null;
	protected List<Object> withAsExprPreparedValueList = new ArrayList<>();
	private int[] dbVersionNums = null;
	@Override
	public int[] gainDBVersionNums() {
		if (dbVersionNums == null || dbVersionNums.length <= 0) {
			dbVersionNums = SQLConfig.super.gainDBVersionNums();
		}
		return dbVersionNums;
	}

	@Override
	public boolean limitSQLCount() {
		return AbstractVerifier.SYSTEM_ACCESS_MAP.containsKey(getTable()) == false;
	}
	@Override
	public boolean allowPartialUpdateFailed() {
		return allowPartialUpdateFailed(getTable());
	}
	public static boolean allowPartialUpdateFailed(String table) {
		return ALLOW_PARTIAL_UPDATE_FAIL_TABLE_MAP.containsKey(table);
	}

	@NotNull
	@Override
	public String getIdKey() {
		return KEY_ID;
	}
	@NotNull
	@Override
	public String getUserIdKey() {
		return KEY_USER_ID;
	}

	private RequestMethod method; //操作方法
	private boolean prepared = true; //预编译
	private boolean main = true;

	private Object id;  // Table 的 id
	private boolean idGeneratedByAPIJSON;
	private Object idIn;  // User Table 的 id IN
	private Object userId;  // Table 的 userId
	private Object userIdIn;  // Table 的 userId IN

	/**
	 * TODO 被关联的表通过就忽略关联的表？(这个不行 User:{"sex@":"/Comment/toId"})
	 */
	private String role; //发送请求的用户的角色
	private boolean distinct = false;
	private String database; //表所在的数据库类型
	private String namespace; //表所在的命名空间
	private String catalog; //表所在的目录
	private String schema; //表所在的数据库名
	private String datasource; //数据源
	private String table; //表名
	private String alias; //表别名
	private String group; //分组方式的字符串数组，','分隔
	private String havingCombine; //聚合函数的字符串数组，','分隔
	private Map<String, Object> having; //聚合函数的字符串数组，','分隔
	private String sample; //取样方式的字符串数组，','分隔
	private String latest; //最近方式的字符串数组，','分隔
	private String partition; //分区方式的字符串数组，','分隔
	private String fill; //填充方式的字符串数组，','分隔
	private String order; //排序方式的字符串数组，','分隔

	private Map<String, String> keyMap; //字段名映射，支持 name_tag:(name,tag) 多字段 IN，year:left(date,4) 截取日期年份等
	private List<String> raw; //需要保留原始 SQL 的字段，','分隔
	private List<String> json; //需要转为 JSON 的字段，','分隔
	private Subquery<T, M, L> from; //子查询临时表
	private List<String> column; //表内字段名(或函数名，仅查询操作可用)的字符串数组，','分隔
	private List<List<Object>> values; //对应表内字段的值的字符串数组，','分隔
	private List<String> nulls;
	private Map<String, String> cast;
	private Map<String, Object> content; //Request内容，key:value形式，column = content.keySet()，values = content.values()
	private Map<String, Object> where; //筛选条件，key:value形式
	private String combine; //条件组合， a | (b & c & !(d | !e))
	private Map<String, List<String>> combineMap; //条件组合，{ "&":[key], "|":[key], "!":[key] }

	//array item <<<<<<<<<<
	private int count; //Table数量
	private int page; //Table所在页码
	private int position; //Table在[]中的位置
	private int query; //apijson.JSONRequest.QUERY
	private Boolean compat; //apijson.JSONMap.compat  query total
	private int type; //ObjectParser.type
	private int cache;
	private boolean explain;

	private List<Join<T, M, L>> joinList; //连表 配置列表
	//array item >>>>>>>>>>
	private boolean test; //测试

	private String procedure;

	public AbstractSQLConfig<T, M, L> setProcedure(String procedure) {
		this.procedure = procedure;
		return this;
	}
	public String getProcedure() {
		return procedure;
	}

	public AbstractSQLConfig(RequestMethod method) {
		setMethod(method);
	}
	public AbstractSQLConfig(RequestMethod method, String table) {
		this(method);
		setTable(table);
	}
	public AbstractSQLConfig(RequestMethod method, int count, int page) {
		this(method);
		setCount(count);
		setPage(page);
	}

	@NotNull
	@Override
	public RequestMethod getMethod() {
		if (method == null) {
			method = GET;
		}
		return method;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setMethod(RequestMethod method) {
		this.method = method;
		return this;
	}
	@Override
	public boolean isPrepared() {
		return prepared && ! isMongoDB(); // MongoDB JDBC 还不支持预编译;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setPrepared(boolean prepared) {
		this.prepared = prepared;
		return this;
	}
	@Override
	public boolean isMain() {
		return main;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setMain(boolean main) {
		this.main = main;
		return this;
	}


	@Override
	public Object getId() {
		return id;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setId(Object id) {
		this.id = id;
		return this;
	}
	@Override
	public boolean isIdGeneratedByAPIJSON() {
		return idGeneratedByAPIJSON;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setIdGeneratedByAPIJSON(boolean generated) {
		this.idGeneratedByAPIJSON = generated;
		return this;
	}

	@Override
	public Object getIdIn() {
		return idIn;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setIdIn(Object idIn) {
		this.idIn = idIn;
		return this;
	}


	@Override
	public Object getUserId() {
		return userId;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setUserId(Object userId) {
		this.userId = userId;
		return this;
	}

	@Override
	public Object getUserIdIn() {
		return userIdIn;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setUserIdIn(Object userIdIn) {
		this.userIdIn = userIdIn;
		return this;
	}

	@Override
	public String getRole() {
		//不能 @NotNull , AbstractParser#getSQLObject 内当getRole() == null时填充默认值
		return role;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setRole(String role) {
		this.role = role;
		return this;
	}

	@Override
	public boolean isDistinct() {
		return distinct;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setDistinct(boolean distinct) {
		this.distinct = distinct;
		return this;
	}

	@Override
	public String getDatabase() {
		return database;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setDatabase(String database) {
		this.database = database;
		return this;
	}
	/**
	 * @return db == null ? DEFAULT_DATABASE : db
	 */
	@NotNull
	public String gainSQLDatabase() {
		String db = getDatabase();
		return db == null ? DEFAULT_DATABASE : db;  // "" 表示已设置，不需要用全局默认的 StringUtil.isEmpty(db, false)) {
	}

	@Override
	public boolean isTSQL() { // 兼容 TSQL 语法
		return isOracle() || isSQLServer() || isDb2() || isKingBaseOracle() || isKingBaseSQLServer();
	}
	@Override
	public boolean isMSQL() { // 兼容 MySQL 语法，但不一定可以使用它的 JDBC/ODBC
		return isMySQL() || isTiDB() || isMariaDB() || isSQLite() || isTDengine() || isKingBaseMySQL();
	}
	@Override
	public boolean isPSQL() { // 兼容 PostgreSQL 语法，但不一定可以使用它的 JDBC/ODBC
		return isPostgreSQL() || isCockroachDB() || isOpenGauss() || isInfluxDB() || isTimescaleDB() || isQuestDB() || isDuckDB();
	}

	@Override
	public boolean isMySQL() {
		return isMySQL(gainSQLDatabase());
	}
	public static boolean isMySQL(String db) {
		return DATABASE_MYSQL.equals(db);
	}

	@Override
	public boolean isPostgreSQL() {
		return isPostgreSQL(gainSQLDatabase());
	}
	public static boolean isPostgreSQL(String db) {
		return DATABASE_POSTGRESQL.equals(db);
	}

	@Override
	public boolean isSQLServer() {
		return isSQLServer(gainSQLDatabase());
	}
	public static boolean isSQLServer(String db) {
		return DATABASE_SQLSERVER.equals(db);
	}

	@Override
	public boolean isOracle() {
		return isOracle(gainSQLDatabase());
	}
	public static boolean isOracle(String db) {
		return DATABASE_ORACLE.equals(db);
	}

	@Override
	public boolean isDb2() {
		return isDb2(gainSQLDatabase());
	}
	public static boolean isDb2(String db) {
		return DATABASE_DB2.equals(db);
	}

	@Override
	public boolean isMariaDB() {
		return isMariaDB(gainSQLDatabase());
	}
	public static boolean isMariaDB(String db) {
		return DATABASE_MARIADB.equals(db);
	}

	@Override
	public boolean isTiDB() {
		return isTiDB(gainSQLDatabase());
	}
	public static boolean isTiDB(String db) {
		return DATABASE_TIDB.equals(db);
	}

	@Override
	public boolean isCockroachDB() {
		return isCockroachDB(gainSQLDatabase());
	}
	public static boolean isCockroachDB(String db) {
		return DATABASE_COCKROACHDB.equals(db);
	}

	@Override
	public boolean isDameng() {
		return isDameng(gainSQLDatabase());
	}
	public static boolean isDameng(String db) {
		return DATABASE_DAMENG.equals(db);
	}

	@Override
	public boolean isKingBase() {
		return isKingBase(gainSQLDatabase());
	}
	public static boolean isKingBase(String db) {
		return KingbaseSQLDialect.from(db).isKingbase();
	}

	@Override
	public boolean isKingBaseMySQL() {
		return isKingBaseMySQL(gainSQLDatabase());
	}
	public static boolean isKingBaseMySQL(String db) {
		return KingbaseSQLDialect.from(db).isMySQL();
	}

	@Override
	public boolean isKingBaseOracle() {
		return isKingBaseOracle(gainSQLDatabase());
	}
	public static boolean isKingBaseOracle(String db) {
		return KingbaseSQLDialect.from(db).isOracle();
	}

	@Override
	public boolean isKingBaseSQLServer() {
		return isKingBaseSQLServer(gainSQLDatabase());
	}
	public static boolean isKingBaseSQLServer(String db) {
		return KingbaseSQLDialect.from(db).isSQLServer();
	}

	@Override
	public boolean isElasticsearch() {
		return isElasticsearch(gainSQLDatabase());
	}
	public static boolean isElasticsearch(String db) {
		return DATABASE_ELASTICSEARCH.equals(db);
	}

	@Override
	public boolean isManticore() {
		return isManticore(gainSQLDatabase());
	}
	public static boolean isManticore(String db) {
		return DATABASE_MANTICORE.equals(db);
	}

	@Override
	public boolean isClickHouse() {
		return isClickHouse(gainSQLDatabase());
	}
	public static boolean isClickHouse(String db) {
		return DATABASE_CLICKHOUSE.equals(db);
	}

	@Override
	public boolean isHive() {
		return isHive(gainSQLDatabase());
	}
	public static boolean isHive(String db) {
		return DATABASE_HIVE.equals(db);
	}

	@Override
	public boolean isPresto() {
		return isPresto(gainSQLDatabase());
	}
	public static boolean isPresto(String db) {
		return DATABASE_PRESTO.equals(db);
	}

	@Override
	public boolean isTrino() {
		return isTrino(gainSQLDatabase());
	}
	public static boolean isTrino(String db) {
		return DATABASE_TRINO.equals(db);
	}

	@Override
	public boolean isSnowflake() {
		return isSnowflake(gainSQLDatabase());
	}
	public static boolean isSnowflake(String db) {
		return DATABASE_SNOWFLAKE.equals(db);
	}

	@Override
	public boolean isDatabend() {
		return isDatabend(gainSQLDatabase());
	}
	public static boolean isDatabend(String db) {
		return DATABASE_DATABEND.equals(db);
	}

	@Override
	public boolean isDatabricks() {
		return isDatabricks(gainSQLDatabase());
	}
	public static boolean isDatabricks(String db) {
		return DATABASE_DATABRICKS.equals(db);
	}

	@Override
	public boolean isCassandra() {
		return isCassandra(gainSQLDatabase());
	}
	public static boolean isCassandra(String db) {
		return DATABASE_CASSANDRA.equals(db);
	}

	@Override
	public boolean isMilvus() {
		return isMilvus(gainSQLDatabase());
	}
	public static boolean isMilvus(String db) {
		return DATABASE_MILVUS.equals(db);
	}

	@Override
	public boolean isInfluxDB() {
		return isInfluxDB(gainSQLDatabase());
	}
	public static boolean isInfluxDB(String db) {
		return DATABASE_INFLUXDB.equals(db);
	}

	@Override
	public boolean isTDengine() {
		return isTDengine(gainSQLDatabase());
	}
	public static boolean isTDengine(String db) {
		return DATABASE_TDENGINE.equals(db);
	}

	@Override
	public boolean isTimescaleDB() {
		return isTimescaleDB(gainSQLDatabase());
	}
	public static boolean isTimescaleDB(String db) {
		return DATABASE_TIMESCALEDB.equals(db);
	}

	@Override
	public boolean isQuestDB() {
		return isQuestDB(gainSQLDatabase());
	}
	public static boolean isQuestDB(String db) {
		return DATABASE_QUESTDB.equals(db);
	}


	public boolean isIoTDB() {
		return isIoTDB(getDatabase());
	}
	public static boolean isIoTDB(String db) {
		return DATABASE_IOTDB.equals(db);
	}


	@Override
	public boolean isRedis() {
		return isRedis(gainSQLDatabase());
	}
	public static boolean isRedis(String db) {
		return DATABASE_REDIS.equals(db);
	}

	@Override
	public boolean isMongoDB() {
		return isMongoDB(gainSQLDatabase());
	}
	public static boolean isMongoDB(String db) {
		return DATABASE_MONGODB.equals(db);
	}

	@Override
	public boolean isKafka() {
		return isKafka(gainSQLDatabase());
	}
	public static boolean isKafka(String db) {
		return DATABASE_KAFKA.equals(db);
	}

	@Override
	public boolean isMQ() {
		return isMQ(gainSQLDatabase());
	}
	public static boolean isMQ(String db) {
		return DATABASE_MQ.equals(db) || isKafka(db);
	}

	@Override
	public boolean isSQLite() {
		return isSQLite(gainSQLDatabase());
	}
	public static boolean isSQLite(String db) {
		return DATABASE_SQLITE.equals(db);
	}

	@Override
	public boolean isDuckDB() {
		return isDuckDB(gainSQLDatabase());
	}
	public static boolean isDuckDB(String db) {
		return DATABASE_DUCKDB.equals(db);
	}

	@Override
	public boolean isSurrealDB() {
		return isSurrealDB(gainSQLDatabase());
	}
	public static boolean isSurrealDB(String db) {
		return DATABASE_SURREALDB.equals(db);
	}

	@Override
	public boolean isOpenGauss() {
		return isOpenGauss(gainSQLDatabase());
	}
	public static boolean isOpenGauss(String db) {
		return DATABASE_OPENGAUSS.equals(db);
	}

	@Override
	public boolean isDoris() {
		return isDoris(gainSQLDatabase());
	}
	public static boolean isDoris(String db) {
		return DATABASE_DORIS.equals(db);
	}

	@Override
	public boolean isStarRocks() {
		return isStarRocks(gainSQLDatabase());
	}
	public static boolean isStarRocks(String db) {
		return DATABASE_STARROCKS.equals(db);
	}

	@Override
	public String getQuote() { // MongoDB  同时支持 `tbl` 反引号 和 "col" 双引号
		if(isElasticsearch() || isManticore() || isIoTDB() || isSurrealDB()) {
			return "";
		}
		KingbaseSQLDialect kingbaseDialect = KingbaseSQLDialect.from(gainSQLDatabase());
		if (kingbaseDialect.isKingbase()) {
			return kingbaseDialect.getIdentifierQuote();
		}
		return isMySQL() || isMariaDB() || isTiDB() || isClickHouse() || isTDengine() || isMilvus() || isDoris() || isStarRocks() ? "`" : "\"";
	}

	public String quote(String s) {
		String q = getQuote();
		return q + s + q;
	}

	@Override
	public String getSQLNamespace() {
		String sch = getNamespace(); // 前端传参 @namespace 优先
		return sch == null ? DEFAULT_NAMESPACE : sch; // 最后代码默认兜底配置
	}

	@Override
	public String getNamespace() {
		return namespace;
	}

	@Override
	public AbstractSQLConfig<T, M, L> setNamespace(String namespace) {
		this.namespace = namespace;
		return this;
	}


	@Override
	public String gainSQLCatalog() {
		String catalog = getCatalog(); // 前端传参 @catalog 优先
		return catalog == null ? DEFAULT_CATALOG : catalog; // 最后代码默认兜底配置
	}

	@Override
	public String getCatalog() {
		return catalog;
	}

	@Override
	public AbstractSQLConfig<T, M, L> setCatalog(String catalog) {
		this.catalog = catalog;
		return this;
	}

	@NotNull
	@Override
	public String gainSQLSchema() {
		String table = getTable();
		// FIXME 全部默认填充判断是 系统表 则不填充 // 强制，避免因为全局默认的 @schema 自动填充进来，导致这几个类的 schema 为 sys 等其它值
		if (Table.TAG.equals(table) || Column.TAG.equals(table)) {
			return SCHEMA_INFORMATION; //MySQL, PostgreSQL, SQL Server 都有的
		}
		if (PgClass.TAG.equals(table) || PgAttribute.TAG.equals(table)) {
			return ""; //PostgreSQL 的 pg_class 和 pg_attribute 表好像不属于任何 Schema
		}
		if (SysTable.TAG.equals(table) || SysColumn.TAG.equals(table) || ExtendedProperty.TAG.equals(table)) {
			return SCHEMA_SYS; //SQL Server 在 sys 中的属性比 information_schema 中的要全，能拿到注释
		}
		if (AllTable.TAG.equals(table) || AllColumn.TAG.equals(table)
				|| AllTableComment.TAG.equals(table) || AllColumnComment.TAG.equals(table)) {
			return ""; //Oracle, Dameng 的 all_tables, dba_tables 和 all_tab_columns, dba_columns 表好像不属于任何 Schema
		}

		String sch = getSchema(); // 前端传参 @schema 优先
		if (sch == null) {
			sch = TABLE_SCHEMA_MAP.get(table); // 其次 Access 表 alias 和 schema 配置
		}
		return sch == null ? DEFAULT_SCHEMA : sch; // 最后代码默认兜底配置
	}

	@Override
	public String getSchema() {
		return schema;
	}

	@Override
	public AbstractSQLConfig<T, M, L> setSchema(String schema) {
		if (schema != null) {
			AbstractFunctionParser.verifySchema(schema, getTable());
		}
		this.schema = schema;
		return this;
	}

	@Override
	public String getDatasource() {
		return datasource;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setDatasource(String datasource) {
		this.datasource = datasource;
		return this;
	}

	/**请求传进来的Table名
	 * @return
	 * @see {@link #gainSQLTable()}
	 */
	@Override
	public String getTable() {
		return table;
	}
	/**数据库里的真实Table名
	 * 通过 {@link #TABLE_KEY_MAP} 映射
	 * @return
	 */
	@Override
	public String gainSQLTable() {
		// 如果要强制小写，则可在子类重写这个方法再 toLowerCase
		// return DATABASE_POSTGRESQL.equals(getDatabase()) ? t.toLowerCase() : t;
		String ot = getTable();
		String nt = TABLE_KEY_MAP.get(ot);
		return StringUtil.isEmpty(nt) ? ot : nt;
	}


	@Override
	public String gainTablePath() {
		String q = getQuote();

		String ns = isSurrealDB() ? getSQLNamespace() : null;
		String cl = isPSQL() ? gainSQLCatalog() : null;
		String sch = gainSQLSchema();
		String sqlTable = gainSQLTable();

		return (StringUtil.isEmpty(ns, true) ? "" : q + ns + q + ".")
				+ (StringUtil.isEmpty(cl, true) ? "" : q + cl + q + ".")
				+ (StringUtil.isEmpty(sch, true) ? "" : q + sch + q + ".")
				+ q + sqlTable + q + (isKeyPrefix() ? gainAs() + q + gainSQLAlias() + q : "");
	}
	@Override
	public AbstractSQLConfig<T, M, L> setTable(String table) { //Table已经在Parser中校验，所以这里不用防SQL注入
		this.table = table;
		return this;
	}

	public String gainAs() {
		return isOracle() || isKingBaseOracle() || isManticore() ? " " : " AS ";
	}

	@Override
	public String getAlias() {
		return alias;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setAlias(String alias) {
		this.alias = alias;
		return this;
	}
	public String gainSQLAliasWithQuote() {
		String a = gainSQLAlias();
		String q = getQuote();
		// getTable 不能小写，因为Verifier用大小写敏感的名称判断权限
		// 如果要强制小写，则可在子类重写这个方法再 toLowerCase
		// return q + (DATABASE_POSTGRESQL.equals(getDatabase()) ? a.toLowerCase() : a) + q;
		return q + a + q;
	}

	@Override
	public String getGroup() {
		return group;
	}
	public AbstractSQLConfig<T, M, L> setGroup(String... keys) {
		return setGroup(StringUtil.get(keys));
	}
	@Override
	public AbstractSQLConfig<T, M, L> setGroup(String group) {
		this.group = group;
		return this;
	}

	public String gainGroupString(boolean hasPrefix) {
		//加上子表的 group
		String joinGroup = "";
		if (joinList != null) {
			boolean first = true;
			for (Join<T, M, L> join : joinList) {
				if (join.isAppJoin()) {
					continue;
				}

				SQLConfig<T, M, L> ocfg = join.getOnConfig();
				SQLConfig<T, M, L> cfg = (ocfg != null && ocfg.getGroup() != null) || join.isLeftOrRightJoin() ? ocfg : join.getJoinConfig();

				if (cfg != null) {
					cfg.setMain(false).setKeyPrefix(true);
					//if (StringUtil.isEmpty(cfg.getAlias(), true)) {
					//	cfg.setAlias(cfg.getTable());
					//}
					String c = ((AbstractSQLConfig<?, ?, ?>) cfg).gainGroupString(false);

					if (StringUtil.isNotEmpty(c, true)) {
						joinGroup += (first ? "" : ", ") + c;
						first = false;
					}
				}

				////先处理左/右关联，内关联忽略
				//SQLConfig<T, M, L> outerConfig = join.getOuterConfig();
				//SQLConfig<T, M, L> outerConfig2 = (outerConfig != null && outerConfig.getGroup() != null) || join.isLeftOrRightJoin() ? outerConfig : null;
				//
				//if (outerConfig2 != null) {
				//	outerConfig2.setMain(false).setKeyPrefix(true);
				//	//if (StringUtil.isEmpty(cfg.getAlias(), true)) {
				//	//	cfg.setAlias(cfg.getTable());
				//	//}
				//	String c = ((AbstractSQLConfig<?, ?, ?>) outerConfig2).gainGroupString(false);
				//
				//	if (StringUtil.isNotEmpty(c, true)) {
				//		joinGroup += (first ? "" : ", ") + c;
				//		first = false;
				//	}
				//}
			}
		}


		group = StringUtil.trim(group);
		String[] keys = StringUtil.split(group);
		if (keys == null || keys.length <= 0) {
			return StringUtil.isEmpty(joinGroup, true) ? "" : (hasPrefix ? " GROUP BY " : "") + joinGroup;
		}

		for (int i = 0; i < keys.length; i++) {
			if (isPrepared()) {
				// 不能通过 ? 来代替，因为SQLExecutor<T, M, L> statement.setString后 GROUP BY 'userId' 有单引号，只能返回一条数据，必须去掉单引号才行！
				if (StringUtil.isName(keys[i]) == false) {
					throw new IllegalArgumentException("@group:value 中 value里面用 , 分割的每一项都必须是1个单词！并且不要有空格！");
				}
			}

			keys[i] = gainKey(keys[i]);
		}

		return (hasPrefix ? " GROUP BY " : "") + StringUtil.concat(StringUtil.get(keys), joinGroup, ", ");
	}

	@Override
	public String getHavingCombine() {
		return havingCombine;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setHavingCombine(String havingCombine) {
		this.havingCombine = havingCombine;
		return this;
	}

	@Override
	public Map<String, Object> getHaving() {
		return having;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setHaving(Map<String, Object> having) {
		this.having = having;
		return this;
	}
	public AbstractSQLConfig<T, M, L> setHaving(String... conditions) {
		return setHaving(StringUtil.get(conditions));
	}

	/**TODO @having 改为默认 | 或连接，且支持 @having: { "key1>": 1, "key{}": "length(key2)>0", "@combine": "key1,key2" }
	 * @return HAVING conditoin0 AND condition1 OR condition2 ...
	 * @throws Exception
	 */
	public String gainHavingString(boolean hasPrefix) throws Exception {
		//加上子表的 having
		String joinHaving = "";
		if (joinList != null) {
			boolean first = true;
			for (Join<T, M, L> join : joinList) {
				if (join.isAppJoin()) {
					continue;
				}

				SQLConfig<T, M, L> ocfg = join.getOnConfig();
				SQLConfig<T, M, L> cfg = (ocfg != null && ocfg.getHaving() != null) || join.isLeftOrRightJoin() ? ocfg : join.getJoinConfig();

				if (cfg != null) {
					cfg.setMain(false).setKeyPrefix(true);
					//if (StringUtil.isEmpty(cfg.getAlias(), true)) {
					//	cfg.setAlias(cfg.getTable());
					//}
					String c = ((AbstractSQLConfig<?, ?, ?>) cfg).gainHavingString(false);

					if (StringUtil.isNotEmpty(c, true)) {
						joinHaving += (first ? "" : ", ") + c;
						first = false;
					}
				}
			}
		}

		Map<String, Object> map = getHaving();
		Set<Entry<String, Object>> set = map == null ? null : map.entrySet();
		if (set == null || set.isEmpty()) {
			return StringUtil.isEmpty(joinHaving, true) ? "" : (hasPrefix ? " HAVING " : "") + joinHaving;
		}

		List<String> raw = getRaw();
		//	提前把 @having& 转为 @having，或者干脆不允许 @raw:"@having&"	boolean containRaw = raw != null && (raw.contains(KEY_HAVING) || raw.contains(KEY_HAVING_AND));
		boolean containRaw = raw != null && raw.contains(KEY_HAVING);

		// 直接把 having 类型从 Map<String, String> 定改为 Map<String, Object>，避免额外拷贝
		//		Map<String, Object> newMap = new LinkedHashMap<>(map.size());
		//		for (Entry<String, String> entry : set) {
		//			newMap.put(entry.getKey(), entry.getValue());
		//		}

		//fun0(arg0,arg1,...);fun1(arg0,arg1,...)
		String havingString = parseCombineExpression(getMethod(), getQuote(), getTable()
				, getAlias(), map, getHavingCombine(), true, containRaw, true);

		return (hasPrefix ? " HAVING " : "") + StringUtil.concat(havingString, joinHaving, AND);
	}

	protected String gainHavingItem(String quote, String table, String alias
			, String key, String expression, boolean containRaw) throws Exception {
		//fun(arg0,arg1,...)
		if (containRaw) {
			String rawSQL = gainRawSQL(KEY_HAVING, expression);
			if (rawSQL != null) {
				return rawSQL;
			}
		}

		if (expression.length() > 100) {
			throw new UnsupportedOperationException("@having:value 的 value 中字符串 " + expression + " 不合法！"
					+ "不允许传超过 100 个字符的函数或表达式！请用 @raw 简化传参！");
		}

		int start = expression.indexOf("(");
		if (start < 0) {
			if (isPrepared() && PATTERN_FUNCTION.matcher(expression).matches() == false) {
				throw new UnsupportedOperationException("字符串 " + expression + " 不合法！"
						+ "预编译模式下 @having:\"column?value;function(arg0,arg1,...)?value...\""
						+ " 中 column?value 必须符合正则表达式 " + PATTERN_FUNCTION + " 且不包含连续减号 -- ！不允许空格！");
			}
			
			return parseSQLExpression(KEY_HAVING, expression, containRaw, false, null);
		}

		int end = expression.lastIndexOf(")");
		if (start >= end) {
			throw new IllegalArgumentException("字符 " + expression + " 不合法！"
					+ "@having:value 中 value 里的 SQL函数必须为 function(arg0,arg1,...) 这种格式！");
		}

		String method = expression.substring(0, start);
		if (method.isEmpty() == false) {
			if (SQL_FUNCTION_MAP == null || SQL_FUNCTION_MAP.isEmpty()) {
				if (StringUtil.isName(method) == false) {
					throw new IllegalArgumentException("字符 " + method + " 不合法！"
							+ "预编译模式下 @having:\"column?value;function(arg0,arg1,...)?value...\""
							+ " 中 function 必须符合小写英文单词的 SQL 函数名格式！");
				}
			}
			else if (SQL_FUNCTION_MAP.containsKey(method) == false) {
				throw new IllegalArgumentException("字符 " + method + " 不合法！"
						+ "预编译模式下 @column:\"column0,column1:alias;function0(arg0,arg1,...);function1(...):alias...\""
						+ " 中 function 必须符合小写英文单词的 SQL 函数名格式！且必须是后端允许调用的 SQL 函数!");
			}
		}

		return method + parseSQLExpression(KEY_HAVING, expression.substring(start), containRaw, false, null);
	}

	@Override
	public String getSample() {
		return sample;
	}
	public AbstractSQLConfig<T, M, L> setSample(String... conditions) {
		return setSample(StringUtil.get(conditions));
	}
	@Override
	public AbstractSQLConfig<T, M, L> setSample(String sample) {
		this.sample = sample;
		return this;
	}
	public String gainSampleString(boolean hasPrefix) {
		//加上子表的 sample
		String joinSample = "";
		if (joinList != null) {
			boolean first = true;
			for (Join<T, M, L> join : joinList) {
				if (join.isAppJoin()) {
					continue;
				}

				SQLConfig<T, M, L> ocfg = join.getOnConfig();
				SQLConfig<T, M, L> cfg = (ocfg != null && ocfg.getSample() != null) || join.isLeftOrRightJoin() ? ocfg : join.getJoinConfig();

				if (cfg != null) {
					cfg.setMain(false).setKeyPrefix(true);
					// if (StringUtil.isEmpty(cfg.getAlias(), true)) {
					//	 cfg.setAlias(cfg.getTable());
					// }
					String c = ((AbstractSQLConfig<?, ?, ?>) cfg).gainSampleString(false);

					if (StringUtil.isNotEmpty(c, true)) {
						joinSample += (first ? "" : ", ") + c;
						first = false;
					}
				}
			}
		}

		String sample = StringUtil.trim(getSample());

		String[] keys = StringUtil.split(sample);
		if (keys == null || keys.length <= 0) {
			return StringUtil.isEmpty(joinSample, true) ? "" : (hasPrefix ? " SAMPLE BY " : "") + joinSample;
		}

		for (int i = 0; i < keys.length; i++) {
			String item = keys[i];

			String origin = item;

			if (isPrepared()) { //不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
				//这里既不对origin trim，也不对 ASC/DESC ignoreCase，希望前端严格传没有任何空格的字符串过来，减少传输数据量，节约服务器性能
				if (StringUtil.isName(origin)) {}
				else if (StringUtil.isCombineOfNumOrAlpha(origin)) {
					continue;
				}
				else {
					throw new IllegalArgumentException("预编译模式下 @sample:value 中 " + item + " 不合法! value 里面用 , 分割的"
							+ "每一项必须是 column 且其中 column 必须是 数字或英语字母组合！并且不要有多余的空格！");
				}
			}

			keys[i] = gainKey(origin);
		}

		return (hasPrefix ? " SAMPLE BY " : "") + StringUtil.concat(StringUtil.get(keys), joinSample, ", ");
	}

	@Override
	public String getLatest() {
		return latest;
	}
	public AbstractSQLConfig<T, M, L> setLatest(String... conditions) {
		return setLatest(StringUtil.get(conditions));
	}
	@Override
	public AbstractSQLConfig<T, M, L> setLatest(String latest) {
		this.latest = latest;
		return this;
	}
	public String gainLatestString(boolean hasPrefix) {
		//加上子表的 latest
		String joinLatest = "";
		if (joinList != null) {
			boolean first = true;
			for (Join<T, M, L> join : joinList) {
				if (join.isAppJoin()) {
					continue;
				}

				SQLConfig<T, M, L> ocfg = join.getOnConfig();
				SQLConfig<T, M, L> cfg = (ocfg != null && ocfg.getLatest() != null) || join.isLeftOrRightJoin() ? ocfg : join.getJoinConfig();

				if (cfg != null) {
					cfg.setMain(false).setKeyPrefix(true);
					// if (StringUtil.isEmpty(cfg.getAlias(), true)) {
					//	 cfg.setAlias(cfg.getTable());
					// }
					String c = ((AbstractSQLConfig<?, ?, ?>) cfg).gainLatestString(false);

					if (StringUtil.isNotEmpty(c, true)) {
						joinLatest += (first ? "" : ", ") + c;
						first = false;
					}
				}
			}
		}

		String latest = StringUtil.trim(getLatest());

		String[] keys = StringUtil.split(latest);
		if (keys == null || keys.length <= 0) {
			return StringUtil.isEmpty(joinLatest, true) ? "" : (hasPrefix ? " LATEST ON " : "") + joinLatest;
		}

		for (int i = 0; i < keys.length; i++) {
			String item = keys[i];
			String origin = item;

			if (isPrepared()) { //不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
				//这里既不对origin trim，也不对 ASC/DESC ignoreCase，希望前端严格传没有任何空格的字符串过来，减少传输数据量，节约服务器性能
				if (StringUtil.isName(origin) == false) {
					throw new IllegalArgumentException("预编译模式下 @latest:value 中 " + item + " 不合法! value 里面用 , 分割的"
							+ "每一项必须是 column 且其中 column 必须是 英语单词！并且不要有多余的空格！");
				}
			}

			keys[i] = gainKey(origin);
		}

		return (hasPrefix ? " LATEST ON " : "") + StringUtil.concat(StringUtil.get(keys), joinLatest, ", ");
	}

	@Override
	public String getPartition() {
		return partition;
	}
	public AbstractSQLConfig<T, M, L> setPartition(String... conditions) {
		return setPartition(StringUtil.get(conditions));
	}
	@Override
	public AbstractSQLConfig<T, M, L> setPartition(String partition) {
		this.partition = partition;
		return this;
	}
	public String gainPartitionString(boolean hasPrefix) {
		//加上子表的 partition
		String joinPartition = "";
		if (joinList != null) {
			boolean first = true;
			for (Join<T, M, L> join : joinList) {
				if (join.isAppJoin()) {
					continue;
				}

				SQLConfig<T, M, L> ocfg = join.getOnConfig();
				SQLConfig<T, M, L> cfg = (ocfg != null && ocfg.getPartition() != null) || join.isLeftOrRightJoin() ? ocfg : join.getJoinConfig();

				if (cfg != null) {
					cfg.setMain(false).setKeyPrefix(true);
					// if (StringUtil.isEmpty(cfg.getAlias(), true)) {
					//	 cfg.setAlias(cfg.getTable());
					// }
					String c = ((AbstractSQLConfig<?, ?, ?>) cfg).gainPartitionString(false);

					if (StringUtil.isNotEmpty(c, true)) {
						joinPartition += (first ? "" : ", ") + c;
						first = false;
					}
				}
			}
		}

		String partition = StringUtil.trim(getPartition());

		String[] keys = StringUtil.split(partition);
		if (keys == null || keys.length <= 0) {
			return StringUtil.isEmpty(joinPartition, true) ? "" : (hasPrefix ? " PARTITION BY " : "") + joinPartition;
		}

		for (int i = 0; i < keys.length; i++) {
			String item = keys[i];
			String origin = item;

			if (isPrepared()) { //不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
				//这里既不对origin trim，也不对 ASC/DESC ignoreCase，希望前端严格传没有任何空格的字符串过来，减少传输数据量，节约服务器性能
				if (StringUtil.isName(origin) == false) {
					throw new IllegalArgumentException("预编译模式下 @partition:value 中 " + item + " 不合法! value 里面用 , 分割的"
							+ "每一项必须是 column 且其中 column 必须是 英语单词！并且不要有多余的空格！");
				}
			}

			keys[i] = gainKey(origin);
		}

		return (hasPrefix ? " PARTITION BY " : "") + StringUtil.concat(StringUtil.get(keys), joinPartition, ", ");
	}

	@Override
	public String getFill() {
		return fill;
	}
	public AbstractSQLConfig<T, M, L> setFill(String... conditions) {
		return setFill(StringUtil.get(conditions));
	}
	@Override
	public AbstractSQLConfig<T, M, L> setFill(String fill) {
		this.fill = fill;
		return this;
	}
	public String gainFillString(boolean hasPrefix) {
		//加上子表的 fill
		String joinFill = "";
		if (joinList != null) {
			boolean first = true;
			for (Join<T, M, L> join : joinList) {
				if (join.isAppJoin()) {
					continue;
				}

				SQLConfig<T, M, L> ocfg = join.getOnConfig();
				SQLConfig<T, M, L> cfg = (ocfg != null && ocfg.getFill() != null) || join.isLeftOrRightJoin() ? ocfg : join.getJoinConfig();

				if (cfg != null) {
					cfg.setMain(false).setKeyPrefix(true);
					// if (StringUtil.isEmpty(cfg.getAlias(), true)) {
					//	 cfg.setAlias(cfg.getTable());
					// }
					String c = ((AbstractSQLConfig<?, ?, ?>) cfg).gainFillString(false);

					if (StringUtil.isNotEmpty(c, true)) {
						joinFill += (first ? "" : ", ") + c;
						first = false;
					}
				}
			}
		}

		String fill = StringUtil.trim(getFill());

		String[] keys = StringUtil.split(fill);
		if (keys == null || keys.length <= 0) {
			return StringUtil.isEmpty(joinFill, true) ? "" : (hasPrefix ? " FILL(" : "") + joinFill + ")";
		}

		for (int i = 0; i < keys.length; i++) {
			String item = keys[i];
			if ("NULL".equals(item) || "LINEAR".equals(item) || "PREV".equals(item) || "PREVIOUS".equals(item)) {
				continue;
			}

			String origin = item;

			if (isPrepared()) { //不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
				//这里既不对origin trim，也不对 ASC/DESC ignoreCase，希望前端严格传没有任何空格的字符串过来，减少传输数据量，节约服务器性能
				if (StringUtil.isName(origin)) {}
				else if (StringUtil.isCombineOfNumOrAlpha(origin)) {
					continue;
				}
				else {
					throw new IllegalArgumentException("预编译模式下 @fill:value 中 " + item + " 不合法! value 里面用 , 分割的"
							+ "每一项必须是 column 且其中 column 必须是 数字或英语字母组合！并且不要有多余的空格！");
				}
			}

			keys[i] = gainKey(origin);
		}

		return (hasPrefix ? " FILL(" : "") + StringUtil.concat(StringUtil.get(keys), joinFill, ", ") + ")";
	}

	@Override
	public String getOrder() {
		return order;
	}
	public AbstractSQLConfig<T, M, L> setOrder(String... conditions) {
		return setOrder(StringUtil.get(conditions));
	}
	@Override
	public AbstractSQLConfig<T, M, L> setOrder(String order) {
		this.order = order;
		return this;
	}
	public String gainOrderString(boolean hasPrefix) {
		//加上子表的 order
		String joinOrder = "";
		String joinOuterOrder = "";
		if (joinList != null) {
			boolean first = true;
			for (Join<T, M, L> join : joinList) {
				if (join.isAppJoin()) {
					continue;
				}

				SQLConfig<T, M, L> ocfg = join.getOnConfig();
				SQLConfig<T, M, L> cfg = (ocfg != null && ocfg.getOrder() != null) || join.isLeftOrRightJoin() ? ocfg : join.getJoinConfig();

				if (cfg != null) {
					cfg.setMain(false).setKeyPrefix(true);
					//if (StringUtil.isEmpty(cfg.getAlias(), true)) {
					//	cfg.setAlias(cfg.getTable());
					//}
					String c = ((AbstractSQLConfig<?, ?, ?>) cfg).gainOrderString(false);

					if (StringUtil.isNotEmpty(c, true)) {
						joinOrder += (first ? "" : ", ") + c;
						first = false;
					}
				}
			}
		}


		String order = StringUtil.trim(getOrder());
		// SELECT * FROM sys.Moment ORDER BY userId ASC, rand();   前面的 userId ASC 和后面的 rand() 都有效
		//		if ("rand()".equals(order)) {
		//			return (hasPrefix ? " ORDER BY " : "") + StringUtil.concat(order, joinOrder, ", ");
		//		}

		if (getCount() > 0 && (isSQLServer() || isKingBaseSQLServer() || isDb2())) {
			// Oracle, SQL Server, DB2 的 OFFSET 必须加 ORDER BY.去掉Oracle，Oracle里面没有offset关键字

			//			String[] ss = StringUtil.split(order);
			if (StringUtil.isEmpty(order, true)) {  //SQL Server 子查询内必须指定 OFFSET 才能用 ORDER BY
				String idKey = getIdKey();
				if (StringUtil.isEmpty(idKey, true)) {
					idKey = "id";
					// ORDER BY NULL 不行，SQL Server 会报错，必须要有排序，才能使用 OFFSET FETCH，如果没有 idKey，请求中指定 @order 即可
				}
				order = idKey; //让数据库调控默认升序还是降序  + "+";
			}

			//不用这么全面，毕竟没有语法问题还浪费性能，如果有其它问题，让前端传的 JSON 直接加上 @order 来解决
			//			boolean contains = false;
			//			if (ss != null) {
			//				for (String s : ss) {
			//					if (s != null && s.startsWith(idKey)) {
			//						s = s.substring(idKey.length());
			//						if ("+".equals(s) || "-".equals(s)) {// || " ASC ".equals(s) || " DESC ".equals(s)) {
			//							contains = true;
			//							break;
			//						}
			//					}
			//				}
			//			}

			//			if (contains == false) {
			//				order = (ss == null || ss.length <= 0 ? "" : order + ",") + idKey + "+";
			//			}
		}


		String[] keys = StringUtil.split(order);
		if (keys == null || keys.length <= 0) {
			return StringUtil.isEmpty(joinOrder, true) ? "" : (hasPrefix ? " ORDER BY " : "") + joinOrder;
		}

		for (int i = 0; i < keys.length; i++) {
			String item = keys[i];
			if ("rand()".equals(item)) {
				continue;
			}

			int index = item.endsWith("+") ? item.length() - 1 : -1; //StringUtil.split返回数组中，子项不会有null
			String sort;
			if (index < 0) {
				index = item.endsWith("-") ? item.length() - 1 : -1;
				sort = index <= 0 ? "" : " DESC ";
			}
			else {
				sort = " ASC ";
			}

			String origin = index < 0 ? item : item.substring(0, index);

			if (isPrepared()) { //不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
				//这里既不对origin trim，也不对 ASC/DESC ignoreCase，希望前端严格传没有任何空格的字符串过来，减少传输数据量，节约服务器性能
				if (StringUtil.isName(origin) == false) {
					throw new IllegalArgumentException("预编译模式下 @order:value 中 " + item + " 不合法! value 里面用 , 分割的"
							+ "每一项必须是 随机函数 rand() 或 column+ / column- 且其中 column 必须是 1 个单词！并且不要有多余的空格！");
				}
			}

			keys[i] = gainKey(origin) + sort;
		}

		return (hasPrefix ? " ORDER BY " : "") + StringUtil.concat(StringUtil.get(keys), joinOrder, ", ");
	}

	@Override
	public Map<String, String> getKeyMap() {
		return keyMap;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setKeyMap(Map<String, String> keyMap) {
		this.keyMap = keyMap;
		return this;
	}

	@Override
	public List<String> getRaw() {
		return raw;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setRaw(List<String> raw) {
		this.raw = raw;
		return this;
	}

	/**获取原始 SQL 片段
	 * @param key
	 * @param value
	 * @return
	 * @throws Exception
	 */
	@Override
	public String gainRawSQL(String key, Object value) throws Exception {
		return gainRawSQL(key, value, ! ALLOW_MISSING_KEY_4_COMBINE);
	}
	/**获取原始 SQL 片段
	 * @param key
	 * @param value
	 * @param throwWhenMissing
	 * @return
	 * @throws Exception
	 */
	@Override
	public String gainRawSQL(String key, Object value, boolean throwWhenMissing) throws Exception {
		if (value == null) {
			return null;
		}

		List<String> rawList = getRaw();
		boolean containRaw = rawList != null && rawList.contains(key);
		if (containRaw && value instanceof String == false) {
			throw new UnsupportedOperationException("@raw:value 的 value 中 " + key + " 不合法！"
					+ "对应的 " + key + ":value 中 value 类型只能为 String！");
		}

		String rawSQL = containRaw ? RAW_MAP.get(value) : null;
		if (containRaw) {
			if (rawSQL == null) {
				if (throwWhenMissing) {
					throw new UnsupportedOperationException("@raw:value 的 value 中 " + key + " 不合法！"
							+ "对应的 " + key + ":value 中 value 值 " + value + " 未在后端 RAW_MAP 中配置 ！");
				}

				putWarnIfNeed(JSONMap.KEY_RAW, "@raw:value 的 value 中 "
							+ key + " 不合法！对应的 " + key + ":value 中 value 值 " + value + " 未在后端 RAW_MAP 中配置 ！");
			}
			else if (rawSQL.isEmpty()) {
				return (String) value;
			}
		}

		return rawSQL;
	}


	@Override
	public List<String> getJson() {
		return json;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setJson(List<String> json) {
		this.json = json;
		return this;
	}


	@Override
	public Subquery<T, M, L> getFrom() {
		return from;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setFrom(Subquery<T, M, L> from) {
		this.from = from;
		return this;
	}

	@Override
	public List<String> getColumn() {
		return column;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setColumn(List<String> column) {
		this.column = column;
		return this;
	}
	public String gainColumnString() throws Exception {
		return gainColumnString(false);
	}
	public String gainColumnString(boolean inSQLJoin) throws Exception {
		List<String> column = getColumn();
		String as = gainAs();
		String q = getQuote();

		switch (getMethod()) {
		case HEAD:
		case HEADS: //StringUtil.isEmpty(column, true) || column.contains(",") 时SQL.count(column)会return "*"
			if (isPrepared() && column != null) {
				List<String> raw = getRaw();
				boolean containRaw = raw != null && raw.contains(KEY_COLUMN);

				for (String c : column) {
					if (containRaw) {
						// 由于 HashMap 对 key 做了 hash 处理，所以 get 比 containsValue 更快
						if ("".equals(RAW_MAP.get(c)) || RAW_MAP.containsValue(c)) {  // newSQLConfig<T, M, L> 提前处理好的
							//排除@raw中的值，以避免使用date_format(date,'%Y-%m-%d %H:%i:%s') 时,冒号的解析出错
							//column.remove(c);
							continue;
						}
					}

					int index = c.lastIndexOf(":"); //StringUtil.split返回数组中，子项不会有null
					String origin = index < 0 ? c : c.substring(0, index);
					String alias = index < 0 ? null : c.substring(index + 1);

					if (alias != null && StringUtil.isName(alias) == false) {
						throw new IllegalArgumentException("HEAD请求: 字符 " + alias
								+ " 不合法！预编译模式下 @column:value 中 value里面用 , 分割的每一项"
								+ " column:alias 中 column 必须是1个单词！如果有alias，则alias也必须为1个单词！并且不要有多余的空格！");
					}

					if (StringUtil.isName(origin) == false) {
						int start = origin.indexOf("(");
						if (start < 0 || origin.lastIndexOf(")") <= start) {
							throw new IllegalArgumentException("HEAD请求: 字符" + origin
									+ " 不合法！预编译模式下 @column:value 中 value里面用 , 分割的每一项"
									+ " column:alias 中 column 必须是1个单词！"
									+ "如果有alias，则 alias 也必须为1个单词！并且不要有多余的空格！");
						}

						if (start > 0 && StringUtil.isName(origin.substring(0, start)) == false) {
							throw new IllegalArgumentException("HEAD请求: 字符 " + origin.substring(0, start)
									+ " 不合法！预编译模式下 @column:value 中 value里面用 , 分割的每一项"
									+ " column:alias 中 column 必须是1个单词！如果有alias，则alias也必须为1个单词！并且不要有多余的空格！");
						}
					}
				}
			}

			boolean onlyOne = column != null && column.size() == 1;
			String c0 = onlyOne ? column.get(0) : null;

			if (onlyOne) {
				int index = c0 == null ? -1 : c0.lastIndexOf(":");
				if (index > 0) {
					c0 = c0.substring(0, index);
				}

				int start = c0 == null ? -1 : c0.indexOf("(");
				int end = start <= 0 ? -1 : c0.lastIndexOf(")");
				if (start > 0 && end > start) {
					String fun = c0.substring(0, start);

					// Invalid use of group function  SELECT count(max(`id`))  AS count  FROM `sys`.`Comment`
					if (SQL_AGGREGATE_FUNCTION_MAP.containsKey(fun)) {
						String group = getGroup();  // TODO 唯一 100% 兼容的可能只有 SELECT count(*) FROM (原语句) AS table
						return StringUtil.isEmpty(group, true) ? "1" : "count(DISTINCT " + group + ")";
					}

					String[] args = start == end - 1 ? null : StringUtil.split(c0.substring(start + 1, end));
					if (args != null && args.length > 0) {
						List<String> raw = getRaw();
						boolean containRaw = raw != null && raw.contains(KEY_COLUMN);
						c0 = parseSQLExpression(KEY_COLUMN, c0, containRaw, false, null);
					}

					return "count(" + c0 + ")" + as + q + JSONResponse.KEY_COUNT + q;
				}
			}

			return "count(" + (onlyOne ? gainKey(c0) : "*") + ")" + as + q + JSONResponse.KEY_COUNT + q;
			//			return SQL.count(onlyOne && StringUtil.isName(column.get(0)) ? getKey(column.get(0)) : "*");
		case POST:
			if (column == null || column.isEmpty()) {
				throw new IllegalArgumentException("POST 请求必须在Table内设置要保存的 key:value ！");
			}

			String s = "";
			boolean pfirst = true;
			for (String c : column) {
				if (isPrepared() && StringUtil.isName(c) == false) {
					// 不能通过 ? 来代替，SELECT 'id','name' 返回的就是 id:"id", name:"name"，而不是数据库里的值！
					throw new IllegalArgumentException("POST请求: 每一个 key:value 中的key都必须是1个单词！");
				}
				s += ((pfirst ? "" : ",") + gainKey(c));

				pfirst = false;
			}

			return "(" + s + ")";
		case GET:
		case GETS:
			String joinColumn = "";
			if (joinList != null) {
				boolean first = true;
				for (Join<T, M, L> join : joinList) {
					if (join.isAppJoin()) {
						continue;
					}

					SQLConfig<T, M, L> ocfg = join.getOnConfig();
					boolean isEmpty = ocfg == null || ocfg.getColumn() == null;
					boolean isLeftOrRightJoin = join.isLeftOrRightJoin();

					if (isEmpty && isLeftOrRightJoin) {
						// 改为 SELECT  ViceTable.* 解决 SELECT sum(ViceTable.id)
						// LEFT/RIGHT JOIN (SELECT sum(id) FROM ViceTable...) AS ViceTable
						// 不仅导致 SQL 函数重复计算，还有时导致 SQL 报错或对应字段未返回
						joinColumn += (first ? "" : ", ") + q + SQLConfig.gainSQLAlias(join.getTable(), join.getAlias()) + q + ".*";
						first = false;
					} else {
						SQLConfig<T, M, L> cfg = isLeftOrRightJoin == false && isEmpty ? join.getJoinConfig() : ocfg;
						if (cfg != null) {
							cfg.setMain(false).setKeyPrefix(true);
							//if (StringUtil.isEmpty(cfg.getAlias(), true)) {
							//	cfg.setAlias(cfg.getTable());
							//}

							String c = ((AbstractSQLConfig<?, ?, ?>) cfg).gainColumnString(true);
							if (StringUtil.isNotEmpty(c, true)) {
								joinColumn += (first ? "" : ", ") + c;
								first = false;
							}
						}
					}

					inSQLJoin = true;
				}
			}

			String tableAlias = q + gainSQLAlias() + q;
			//	String c = StringUtil.getString(column); //id,name;json_length(contactIdList):contactCount;...

			String[] keys = column == null ? null : column.toArray(new String[]{}); //StringUtil.split(c, ";");
			if (keys == null || keys.length <= 0) {

				boolean noColumn = column != null && inSQLJoin;
				String mc = isKeyPrefix() ? (noColumn ? "" : tableAlias + ".*") : (noColumn ? "" : "*");

				return StringUtil.concat(mc, joinColumn, ", ", true);
			}

			List<String> raw = getRaw();
			boolean containRaw = raw != null && raw.contains(KEY_COLUMN);

			//...;fun0(arg0,arg1,...):fun0;fun1(arg0,arg1,...):fun1;...
			for (int i = 0; i < keys.length; i++) {
				String expression = keys[i];  //fun(arg0,arg1,...)

				if (containRaw) {  // 由于 HashMap 对 key 做了 hash 处理，所以 get 比 containsValue 更快
					if ("".equals(RAW_MAP.get(expression)) || RAW_MAP.containsValue(expression)) {  // newSQLConfig<T, M, L> 提前处理好的
						continue;
					}

					// 简单点， 后台配置就带上 AS
					int index = expression.lastIndexOf(":");
					String alias = expression.substring(index+1);
					boolean hasAlias = StringUtil.isName(alias);
					String pre = index > 0 && hasAlias ? expression.substring(0, index) : expression;
					if (RAW_MAP.containsValue(pre) || "".equals(RAW_MAP.get(pre))) {  // newSQLConfig<T, M, L> 提前处理好的
						keys[i] = pre + (hasAlias ? gainAs() + q + alias + q : "");
						continue;
					}
				}

				if (expression.length() > 100) {
					throw new UnsupportedOperationException("@column:value 的 value 中字符串 " + expression + " 不合法！"
							+ "不允许传超过 100 个字符的函数或表达式！请用 @raw 简化传参！");
				}
				keys[i] = parseSQLExpression(KEY_COLUMN, expression, containRaw, true
						, "@column:\"column0,column1:alias1;function0(arg0,arg1,...);function1(...):alias2...\"");
			}

			String c = StringUtil.get(keys);
			c = c + (StringUtil.isEmpty(joinColumn, true) ? "" : ", " + joinColumn);//不能在这里改，后续还要用到:
			return isMain() && isDistinct() ? PREFIX_DISTINCT + c : c;
		default:
			throw new UnsupportedOperationException(
					"服务器内部错误：getColumnString 不支持 " + RequestMethod.getName(getMethod())
					+ " 等 [GET,GETS,HEAD,HEADS,POST] 外的ReuqestMethod！"
					);
		}
	}

	/**解析@column 中以“;”分隔的表达式（"@column":"expression1;expression2;expression2;...."）中的expression
	 * @param key
	 * @param expression
	 * @param containRaw
	 * @param allowAlias
	 * @return
	 */
	public String parseSQLExpression(String key, String expression, boolean containRaw, boolean allowAlias) {
		return parseSQLExpression(key, expression, containRaw, allowAlias, null);
	}
	/**解析@column 中以“;”分隔的表达式（"@column":"expression1;expression2;expression2;...."）中的expression
	 * @param key
	 * @param expression
	 * @param containRaw
	 * @param allowAlias
	 * @param example
	 * @return
	 */
	public String parseSQLExpression(String key, String expression, boolean containRaw, boolean allowAlias, String example) {
		if (containRaw) {
			String s = RAW_MAP.get(expression);
			if ("".equals(s)) {
				return expression;
			}
			if (s != null) {
				return s;
			}
		}

		String quote = getQuote();
		int start = expression.indexOf('(');
		if (start < 0) {
			//没有函数 ,可能是字段,也可能是 DISTINCT xx
			String[] cks = parseArgsSplitWithComma(expression, true, containRaw, allowAlias);
			expression = StringUtil.get(cks);
		} else {  // FIXME 用括号断开？ 如果少的话，用关键词加括号断开，例如  )OVER( 和 )AGAINST(
			// 窗口函数 rank() OVER (PARTITION BY id ORDER BY userId ASC)
			// 全文索引 math(name,tag) AGAINST ('a b +c -d' IN NATURALE LANGUAGE MODE)  // IN BOOLEAN MODE

			if (StringUtil.isEmpty(example)) {
				if (KEY_COLUMN.equals(key)) {
					example = key + ":\"column0,column1:alias1;function0(arg0,arg1,...);function1(...):alias2...\"";
				}
				//	和 key{}:"" 一样		else if (KEY_HAVING.equals(key) || KEY_HAVING_AND.equals(key)) {
				//					exeptionExample = key + ":\"function0(arg0,arg1,...)>1;function1(...)%5<=3...\"";
				//				}
				else {
					example = key + ":\"column0!=0;column1+3*2<=10;function0(arg0,arg1,...)>1;function1(...)%5<=3...\"";
				}
			}

			//有函数,但不是窗口函数
			int overIndex = expression.indexOf(")OVER(");  // 传参不传空格，拼接带空格  ") OVER (");
			int againstIndex = expression.indexOf(")AGAINST(");  // 传参不传空格，拼接带空格  ") AGAINST (");
			boolean containOver = overIndex > 0 && overIndex < expression.length() - ")OVER(".length();
			boolean containAgainst = againstIndex > 0 && againstIndex < expression.length() - ")AGAINST(".length();

			if (containOver && containAgainst) {
				throw new IllegalArgumentException("字符 " + expression + " 不合法！预编译模式下 " + example
						+ " 中 function 必须符合小写英文单词的 SQL 函数名格式！不能同时存在窗口函数关键词 OVER 和全文索引关键词 AGAINST！");
			}

			if (containOver == false && containAgainst == false) {
				int end = expression.lastIndexOf(')');
				if (start >= end) {
					throw new IllegalArgumentException("字符 " + expression + " 不合法！"
							+ key + ":value 中 value 里的 SQL函数必须为 function(arg0,arg1,...) 这种格式！");
				}
				String fun = expression.substring(0, start);
				if (fun.isEmpty() == false) {
					if (SQL_FUNCTION_MAP == null || SQL_FUNCTION_MAP.isEmpty()) {
						if (StringUtil.isName(fun) == false) {
							throw new IllegalArgumentException("字符 " + fun + " 不合法！预编译模式下 " + example
									+ " 中 function 必须符合小写英文单词的 SQL 函数名格式！");
						}
					} else if (SQL_FUNCTION_MAP.containsKey(fun) == false) {
						throw new IllegalArgumentException("字符 " + fun + " 不合法！预编译模式下 " + example
								+ " 中 function 必须符合小写英文单词的 SQL 函数名格式！且必须是后端允许调用的 SQL 函数!");
					}
				}

				String s = expression.substring(start + 1, end);
				boolean distinct = s.startsWith(PREFIX_DISTINCT);
				if (distinct) {
					s = s.substring(PREFIX_DISTINCT.length());
				}

				// 解析函数内的参数
				String ckeys[] = parseArgsSplitWithComma(s, false, containRaw, allowAlias);

				String suffix = expression.substring(end + 1); //:contactCount
				String alias = null;
				if (allowAlias) {
					int index = suffix.lastIndexOf(":");
					alias = index < 0 ? "" : suffix.substring(index + 1); //contactCount
					suffix = index < 0 ? suffix : suffix.substring(0, index);
					if (alias.isEmpty() == false && StringUtil.isName(alias) == false) {
						throw new IllegalArgumentException("字符串 " + alias + " 不合法！预编译模式下 "
								+ key + ":value 中 value里面用 ; 分割的每一项"
								+ " function(arg0,arg1,...):alias 中 alias 必须是1个单词！并且不要有多余的空格！");
					}
				}

				if (suffix.isEmpty() == false && (suffix.contains("--") || suffix.contains("/*")
						|| PATTERN_RANGE.matcher(suffix).matches() == false)) {
					throw new UnsupportedOperationException("字符串 " + suffix + " 不合法！预编译模式下 " + key
							+ ":\"column?value;function(arg0,arg1,...)?value...\""
							+ " 中 ?value 必须符合正则表达式 " + PATTERN_RANGE + " 且不包含连续减号 -- 或注释符 /* ！不允许多余的空格！");
				}

				String origin = fun + "(" + (distinct ? PREFIX_DISTINCT : "") + StringUtil.get(ckeys) + ")" + suffix;
				expression = origin + (StringUtil.isEmpty(alias, true) ? "" : gainAs() + quote + alias + quote);
			}
			else {
				//是窗口函数   fun(arg0,agr1) OVER (agr0 agr1 ...)
				int keyIndex = containOver ? overIndex : againstIndex;
				String s1 = expression.substring(0, keyIndex + 1); // OVER 前半部分
				String s2 = expression.substring(keyIndex + 1); // OVER 后半部分

				int index1 = s1.indexOf("("); //  函数 "(" 的起始位置
				int end = s2.lastIndexOf(")"); // 后半部分 “)” 的位置

				if (index1 >= end + s1.length()) {
					throw new IllegalArgumentException("字符 " + expression + " 不合法！"
							+ key + ":value 中 value 里的 SQL 函数必须为 function(arg0,arg1,...) 这种格式！");
				}

				String fun = s1.substring(0, index1); // 函数名称
				if (fun.isEmpty() == false) {
					if (SQL_FUNCTION_MAP == null || SQL_FUNCTION_MAP.isEmpty()) {
						if (StringUtil.isName(fun) == false) {
							throw new IllegalArgumentException("字符 " + fun + " 不合法！预编译模式下 " + example
									+ " 中 function 必须符合小写英文单词的 SQL 函数名格式！");
						}
					}
					else if (SQL_FUNCTION_MAP.containsKey(fun) == false) {
						throw new IllegalArgumentException("字符 " + fun + " 不合法！预编译模式下 " + example
								+ " 中 function 必须符合小写英文单词的 SQL 函数名格式！且必须是后端允许调用的 SQL 函数!");
					}
				}

				// 获取前半部分函数的参数解析   fun(arg0,agr1)
				String[] args = parseArgsSplitWithComma(
						s1.substring(index1 + 1, s1.lastIndexOf(")")), false, containRaw, allowAlias
				);

				int index2 = s2.indexOf("("); // 后半部分 “(”的起始位置
				String argString2 = s2.substring(index2 + 1, end); // 后半部分的参数
				// 别名
				int aliasIndex = allowAlias ? s2.lastIndexOf(":") : -1;
				String alias = aliasIndex < 0 ? "" : s2.substring(aliasIndex + 1);
				if (alias.isEmpty() == false && StringUtil.isName(alias) == false) {
					throw new IllegalArgumentException("字符串 " + alias + " 不合法！预编译模式下 "
							+ key + ":value 中 value里面用 ; 分割的每一项"
							+ " function(arg0,arg1,...):alias 中 alias 必须是1个单词！并且不要有多余的空格！");
				}

				String suffix = s2.substring(end + 1, aliasIndex < 0 ? s2.length() : aliasIndex);
				if (suffix.isEmpty() == false && (suffix.contains("--") || suffix.contains("/*")
						|| PATTERN_RANGE.matcher(suffix).matches() == false)) {
					throw new UnsupportedOperationException("字符串 " + suffix + " 不合法！预编译模式下 " + key
							+ ":\"column?value;function(arg0,arg1,...)?value...\""
							+ " 中 ?value 必须符合正则表达式 " + PATTERN_RANGE + " 且不包含连续减号 -- 或注释符 /* ！不允许多余的空格！");
				}

				// 获取后半部分的参数解析 (agr0 agr1 ...)
				String[] args2 = parseArgsSplitWithComma(argString2, false, containRaw, allowAlias);
				expression = fun + "(" + StringUtil.get(args) + (containOver ? ") OVER (" : ") AGAINST (")
						+ StringUtil.get(args2) + ")" + suffix  // 传参不传空格，拼接带空格
						+ (StringUtil.isEmpty(alias, true) ? "" : gainAs() + quote + alias + quote);
			}
		}

		return expression;
	}


	/**解析函数参数或者字段,此函数对于解析字段 和 函数内参数通用
	 * @param param
	 * @param isColumn true:不是函数参数。false:是函数参数
	 * @param containRaw
	 * @param allowAlias
	 * @return
	 */
	private String[] parseArgsSplitWithComma(String param, boolean isColumn, boolean containRaw, boolean allowAlias) {
		// 以"," 分割参数
		String quote = getQuote();
		boolean isKeyPrefix = isKeyPrefix();
		String tableAlias = quote + gainSQLAlias() + quote;
		String[] ckeys = StringUtil.split(param); // 以","分割参数
		if (ckeys != null && ckeys.length > 0) {

			for (int i = 0; i < ckeys.length; i++) {
				String ck = ckeys[i];

				String origin;
				String alias;

				// 如果参数包含 "'" ,解析字符串
				if (ck.startsWith("`") && ck.endsWith("`")) {
					origin = ck.substring(1, ck.length() - 1);
					//sql 注入判断 判断
					if (origin.startsWith("_") || StringUtil.isName(origin) == false) {
						throw new IllegalArgumentException("字符 " + ck + " 不合法！"
								+ "预编译模式下 @column:\"`column0`,`column1`:alias;function0(arg0,arg1,...);function1(...):alias...\""
								+ " 中所有字符串 column 都必须必须为1个单词 ！");
					}

					origin = gainKey(origin);
				}
				else if (ck.startsWith("'") && ck.endsWith("'")) {
					origin = ck.substring(1, ck.length() - 1);
					if (origin.contains("'")) {
						throw new IllegalArgumentException("字符串 " + ck + " 不合法！"
								+ "预编译模式下 @column:\"column0,column1:alias;function0(arg0,arg1,...);function1(...):alias...\""
								+ " 中字符串参数不合法，必须以 ' 开头, ' 结尾,字符串中不能包含 ' ");
					}

					// 1.字符串不是字段也没有别名,所以不解析别名 2. 是字符串，进行预编译，使用getValue() ,对字符串进行截取
					origin = gainValue(origin).toString();
				}
				else {
					// 参数不包含",",即不是字符串
					// 解析参数:1. 字段 ,2. 是以空格分隔的参数 eg: cast(now() as date)
					if ("=null".equals(ck)) {
						origin = SQL.isNull();
					}
					else if ("!=null".equals(ck)) {
						origin = SQL.isNull(false);
					}
					else {
						origin = ck;
						alias = null;
						if (allowAlias) {
							int index = isColumn ? ck.lastIndexOf(":") : -1; //StringUtil.split返回数组中，子项不会有null
							origin = index < 0 ? ck : ck.substring(0, index); //获取 : 之前的
							alias = index < 0 ? null : ck.substring(index + 1);
							if (isPrepared()) {
								if (isColumn) {
									if (StringUtil.isName(origin) == false || (alias != null && StringUtil.isName(alias) == false)) {
										throw new IllegalArgumentException("字符 " + ck + " 不合法！"
												+ "预编译模式下 @column:value 中 value里面用 , 分割的每一项"
												+ " column:alias 中 column 必须是1个单词！如果有alias，则alias也必须为1个单词！"
												+ "关键字必须全大写，且以空格分隔的参数，空格必须只有 1 个！其它情况不允许空格！");
									}
								} else {
									if (origin.startsWith("_") || origin.contains("--")) {
										// || PATTERN_FUNCTION.matcher(origin).matches() == false) {
										throw new IllegalArgumentException("字符 " + ck + " 不合法！"
												+ "预编译模式下 @column:\"column0,column1:alias;function0(arg0,arg1,...);function1(...):alias...\""
												+ " 中所有 arg 都必须是1个不以 _ 开头的单词 或者符合正则表达式 "
												+ PATTERN_FUNCTION + " 且不包含连续减号 -- ！" +
												"DISTINCT 必须全大写，且后面必须有且只有 1 个空格！其它情况不允许空格！");
									}
								}
							}
						}

						// 以空格分割参数
						String[] mkes = containRaw ? StringUtil.split(ck, " ", true) : new String[]{ ck };

						//如果参数中含有空格(少数情况) 比如  fun(arg1, arg2,arg3,arg4) 中的 arg1 arg2 arg3，比如 DISTINCT id
						if (mkes != null && mkes.length >= 2) {
							origin = parseArgsSplitWithSpace(mkes);
						} else {
							String mk = RAW_MAP.get(origin);
							if (mk != null) {  // newSQLConfig<T, M, L> 提前处理好的
								if (mk.length() > 0) {
									origin = mk;
								}
							} else if (StringUtil.isNumber(origin)) {
								//do nothing
							} else {
								String[] keys = origin.split("[.]");
								StringBuilder sb = new StringBuilder();

								int len = keys == null ? 0 : keys.length;
								if (len > 0) {
									boolean first = true;
									for (String k : keys) {
										if (StringUtil.isName(k) == false) {
											sb = null;
											break;
										}

										sb.append(first ? "" : ".").append(quote).append(k).append(quote);
										first = false;
									}
								}

								String s = sb == null ? null : sb.toString();
								if (StringUtil.isNotEmpty(s, true)) {
									origin = (len == 1 && isKeyPrefix ? tableAlias + "." : "") + s;
								} else {
									origin = gainValue(origin).toString();
								}
							}

							if (isColumn && StringUtil.isNotEmpty(alias, true)) {
								origin += gainAs() + quote + alias + quote;
							}
						}
					}
				}

				ckeys[i] = origin;
			}
		}

		return ckeys;
	}


	/**
	 * 只解析以空格分隔的参数
	 *
	 * @param mkes
	 * @return
	 */
	private String parseArgsSplitWithSpace(String[] mkes) {
		String quote = getQuote();
		boolean isKeyPrefix = isKeyPrefix();
		String tableAlias = quote + gainSQLAlias() + quote;

		// 包含空格的参数  肯定不包含别名 不用处理别名
		if (mkes != null && mkes.length > 0) {
			for (int j = 0; j < mkes.length; j++) {
				// now()/AS/ DISTINCT/VALUE 等等放在RAW_MAP中
				String origin = mkes[j];

				String mk = RAW_MAP.get(origin);
				if (mk != null) {  // newSQLConfig<T, M, L> 提前处理好的
					if (mk.length() > 0) {
						mkes[j] = mk;
					}
					continue;
				}

				//这里为什么还要做一次判断 是因为解析窗口函数调用的时候会判断一次
				String ck = origin;
				// 如果参数包含 "`" 或 "'" ,解析字符串
				if (ck.startsWith("`") && ck.endsWith("`")) {
					origin = ck.substring(1, ck.length() - 1);
					if (origin.startsWith("_") || StringUtil.isName(origin) == false) {
						throw new IllegalArgumentException("字符 " + ck + " 不合法！"
								+ "预编译模式下 @column:\"`column0`,`column1`:alias;function0(arg0,arg1,...);function1(...):alias...\""
								+ " 中所有字符串 column 都必须必须为1个单词 ！");
					}

					mkes[j] = gainKey(origin);
					continue;
				}
				else if (ck.startsWith("'") && ck.endsWith("'")) {
					origin = ck.substring(1, ck.length() - 1);
					if (origin.contains("'")) {
						throw new IllegalArgumentException("字符串 " + ck + " 不合法！"
								+ "预编译模式下 @column:\"column0,column1:alias;function0(arg0,arg1,...);function1(...):alias...\""
								+ " 中字符串参数不合法，必须以 ' 开头, ' 结尾,字符串中不能包含 ' ");
					}

					// 1.字符串不是字段也没有别名,所以不解析别名 2. 是字符串，进行预编译，使用getValue() ,对字符串进行截取
					mkes[j] = gainValue(origin).toString();
					continue;
				}
				else if (ck.contains("`") || ck.contains("'") || origin.startsWith("_") || origin.contains("--")) {
					// || PATTERN_FUNCTION.matcher(origin).matches() == false) {
					throw new IllegalArgumentException("字符 " + origin + " 不合法！"
							+ "预编译模式下 @column:\"column0,column1:alias;function0(arg0,arg1,...);function1(...):alias...\""
							+ " 中所有 arg 都必须是1个不以 _ 开头的单词 或者符合正则表达式 " + PATTERN_FUNCTION
							+ " 且不包含连续减号 -- ！DISTINCT 必须全大写，且后面必须有且只有 1 个空格！其它情况不允许空格！");
				}

				if (StringUtil.isNumber(origin)) {
					//do nothing
				} else {
					String[] keys = origin.split("[.]");
					StringBuilder sb = new StringBuilder();

					int len = keys == null ? 0 : keys.length;
					if (len > 0) {
						boolean first = true;
						for (String k : keys) {
							if (StringUtil.isName(k) == false) {
								sb = null;
								break;
							}

							sb.append(first ? "" : ".").append(quote).append(k).append(quote);
							first = false;
						}
					}

					String s = sb == null ? null : sb.toString();
					if (StringUtil.isNotEmpty(s, true)) {
						origin = (len == 1 && isKeyPrefix ? tableAlias + "." : "") + s;
					} else {
						origin = gainValue(origin).toString();
					}
				}

				mkes[j] = origin;
			}
		}
		// 返回重新以" "拼接后的参数
		return StringUtil.join(mkes, " ");
	}


	@Override
	public List<List<Object>> getValues() {
		return values;
	}
	public String gainValuesString() {
		String s = "";
		if (values != null && values.size() > 0) {
			Object[] items = new Object[values.size()];
			List<Object> vs;
			for (int i = 0; i < values.size(); i++) {
				vs = values.get(i);
				if (vs == null) {
					continue;
				}

				items[i] = "(";
				for (int j = 0; j < vs.size(); j++) {
					items[i] += ((j <= 0 ? "" : ",") + gainValue(vs.get(j)));
				}
				items[i] += ")";
			}
			s = StringUtil.get(items);
		}
		return s;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setValues(List<List<Object>> valuess) {
		this.values = valuess;
		return this;
	}

	@Override
	public Map<String, Object> getContent() {
		return content;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setContent(Map<String, Object> content) {
		this.content = content;
		return this;
	}

	@Override
	public int getCount() {
		return count;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setCount(int count) {
		this.count = count;
		return this;
	}
	@Override
	public int getPage() {
		return page;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setPage(int page) {
		this.page = page;
		return this;
	}
	@Override
	public int getPosition() {
		return position;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setPosition(int position) {
		this.position = position;
		return this;
	}

	@Override
	public int getQuery() {
		return query;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setQuery(int query) {
		this.query = query;
		return this;
	}
	@Override
	public Boolean getCompat() {
		return compat;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setCompat(Boolean compat) {
		this.compat = compat;
		return this;
	}

	@Override
	public int getType() {
		return type;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setType(int type) {
		this.type = type;
		return this;
	}

	@Override
	public int getCache() {
		return cache;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setCache(int cache) {
		this.cache = cache;
		return this;
	}

	public AbstractSQLConfig<T, M, L> setCache(String cache) {
		return setCache(getCache(cache));
	}
	public static int getCache(String cache) {
		int cache2;
		if (cache == null) {
			cache2 = JSONMap.CACHE_ALL;
		}
		else {
			//			if (isSubquery) {
			//				throw new IllegalArgumentException("子查询内不支持传 " + apijson.JSONMap.KEY_CACHE + "!");
			//			}

			switch (cache) {
			case "0":
			case JSONMap.CACHE_ALL_STRING:
				cache2 = JSONMap.CACHE_ALL;
				break;
			case "1":
			case JSONMap.CACHE_ROM_STRING:
				cache2 = JSONMap.CACHE_ROM;
				break;
			case "2":
			case JSONMap.CACHE_RAM_STRING:
				cache2 = JSONMap.CACHE_RAM;
				break;
			default:
				throw new IllegalArgumentException(JSONMap.KEY_CACHE
						+ ":value 中 value 的值不合法！必须在 [0,1,2] 或 [ALL, ROM, RAM] 内 !");
			}
		}
		return cache2;
	}

	@Override
	public boolean isExplain() {
		return explain;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setExplain(boolean explain) {
		this.explain = explain;
		return this;
	}

	@Override
	public List<Join<T, M, L>> getJoinList() {
		return joinList;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setJoinList(List<Join<T, M, L>> joinList) {
		this.joinList = joinList;
		return this;
	}
	@Override
	public boolean hasJoin() {
		return joinList != null && joinList.isEmpty() == false;
	}


	@Override
	public boolean isTest() {
		return test;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setTest(boolean test) {
		this.test = test;
		return this;
	}

	/**获取初始位置offset
	 * @return
	 */
	public int getOffset() {
		return getOffset(getPage(), getCount());
	}
	/**获取初始位置offset
	 * @param page
	 * @param count
	 * @return
	 */
	public static int getOffset(int page, int count) {
		return page*count;
	}
	/**获取限制数量
	 * @return
	 */
	public String gainLimitString() {
		int count = getCount();
		int page = getPage();

		boolean isMilvus = isMilvus();
		if ((count <= 0 && ! (isMilvus && isMain())) || RequestMethod.isHeadMethod(getMethod(), true)) { // TODO HEAD 真的不需要 LIMIT ？
			return "";
		}

		boolean isSurrealDB = isSurrealDB();
		boolean isQuestDB = isQuestDB();
		if (isSurrealDB || isQuestDB || isMilvus) {
			if (count == 0) {
				Parser<T, M, L> parser = gainParser();
				count = parser == null ? AbstractParser.MAX_QUERY_COUNT : parser.getMaxQueryCount();
			}

			int offset = getOffset(page, count);
			if (isQuestDB()) {
				return " LIMIT " + offset + ", " + (offset + count);
			}
			else if (isSurrealDB()) {
				return " START " + offset + " LIMIT " + count;
			}
			else {
				return " LIMIT " + offset + ", " + count; // 目前 moql-transx 的限制
			}
		}

		KingbaseSQLDialect kingbaseDialect = KingbaseSQLDialect.from(gainSQLDatabase());
		String kingbaseLimit = kingbaseDialect.getSelectLimit(getOffset(page, count), count);
		if (kingbaseLimit != null) {
			return kingbaseLimit;
		}

		boolean isOracle = isOracle();
		return gainLimitString(page, count, isTSQL(), isOracle || isDameng() || isKingBaseOracle(), isPresto() || isTrino());
	}
	/**获取限制数量及偏移量
	* @param page
	* @param count
	* @param isTSQL
	* @param isOracle
	* @return
	*/
	public static String gainLimitString(int page, int count, boolean isTSQL, boolean isOracle) {
		return gainLimitString(page, count, isTSQL, isOracle, false);
	}
	/**获取限制数量及偏移量
	* @param page
	* @param count
	* @param isTSQL
	* @param isOracle
	* @param isPresto
	* @return
	*/
	public static String gainLimitString(int page, int count, boolean isTSQL, boolean isOracle, boolean isPresto) {
		int offset = getOffset(page, count);

		if (isOracle) {  // TODO 判断版本，高版本可以用 OFFSET FETCH
			return " WHERE ROWNUM BETWEEN " + offset + " AND " + (offset + count);
		}

		if (isTSQL) {  // OFFSET FECTH 中所有关键词都不可省略, 另外 Oracle 数据库使用子查询加 where 分页
			return " OFFSET " + offset + " ROWS FETCH FIRST " + count + " ROWS ONLY";
		}

		if (isPresto) {  // https://prestodb.io/docs/current/sql/select.html
			return (offset <= 0 ? "" : " OFFSET " + offset) + " LIMIT " + count;
		}

		return " LIMIT " + count + (offset <= 0 ? "" : " OFFSET " + offset);  // DELETE, UPDATE 不支持 OFFSET
	}

	@Override
	public List<String> getNull() {
		return nulls;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setNull(List<String> nulls) {
		this.nulls = nulls;
		return this;
	}

	@Override
	public Map<String, String> getCast() {
		return cast;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setCast(Map<String, String> cast) {
		this.cast = cast;
		return this;
	}

	//WHERE <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	protected int getMaxHavingCount() {
		return MAX_HAVING_COUNT;
	}
	protected int getMaxWhereCount() {
		return MAX_WHERE_COUNT;
	}
	protected int getMaxCombineDepth() {
		return MAX_COMBINE_DEPTH;
	}
	protected int getMaxCombineCount() {
		return MAX_COMBINE_COUNT;
	}
	protected int getMaxCombineKeyCount() {
		return MAX_COMBINE_KEY_COUNT;
	}
	protected float getMaxCombineRatio() {
		return MAX_COMBINE_RATIO;
	}


	@Override
	public String getCombine() {
		return combine;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setCombine(String combine) {
		this.combine = combine;
		return this;
	}

	@NotNull
	@Override
	public Map<String, List<String>> getCombineMap() {
		List<String> andList = combineMap == null ? null : combineMap.get("&");
		if (andList == null) {
			andList = where == null ? new ArrayList<String>() : new ArrayList<String>(where.keySet());
			if (combineMap == null) {
				combineMap = new HashMap<>();
			}
			combineMap.put("&", andList);
		}
		return combineMap;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setCombineMap(Map<String, List<String>> combineMap) {
		this.combineMap = combineMap;
		return this;
	}

	@Override
	public Map<String, Object> getWhere() {
		return where;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setWhere(Map<String, Object> where) {
		this.where = where;
		return this;
	}

	/**
	 * noFunctionChar = false
	 * @param key
	 * @return
	 */
	@Override
	public Object getWhere(String key) {
		return getWhere(key, false);
	}
	//CS304 Issue link: https://github.com/APIJSON/APIJSON/issues/48
	/**
	 * @param key - the key passed in
	 * @param exactMatch - whether it is exact match
	 * @return
	 * <p>use entrySet+getValue() to replace keySet+get() to enhance efficiency</p>
	 */
	@Override
	public Object getWhere(String key, boolean exactMatch) {
		if (exactMatch) {
			return where == null ? null : where.get(key);
		}

		if (key == null || where == null){
			return null;
		}

		int index;
		for (Entry<String,Object> entry : where.entrySet()) {
			String k = entry.getKey();
			index = k.indexOf(key);
			if (index >= 0 && StringUtil.isName(k.substring(index, index + 1)) == false) {
				return entry.getValue();
			}
		}

		return null;
	}
	@Override
	public AbstractSQLConfig<T, M, L> putWhere(String key, Object value, boolean prior) {
		if (key != null) {
			if (where == null) {
				where = new LinkedHashMap<>();
			}
			if (value == null) {
				where.remove(key);
			} else {
				where.put(key, value);
			}

			Map<String, List<String>> combineMap = getCombineMap();
			List<String> andList = combineMap.get("&");
			if (value == null) {
				if (andList != null) {
					andList.remove(key);
				}
			}
			else if (andList == null || andList.contains(key) == false) {
				int i = 0;
				if (andList == null) {
					andList = new ArrayList<>();
				}
				else if (prior && andList.isEmpty() == false) {

					String idKey = getIdKey();
					String idInKey = idKey + "{}";
					String userIdKey = getUserIdKey();
					String userIdInKey = userIdKey + "{}";

					int lastIndex;
					if (key.equals(idKey)) {
						setId(value);
						lastIndex = -1;
					}
					else if (key.equals(idInKey)) {
						setIdIn(value);
						lastIndex = andList.lastIndexOf(idKey);
					}
					else if (key.equals(userIdKey)) {
						setUserId(value);
						lastIndex = andList.lastIndexOf(idInKey);
						if (lastIndex < 0) {
							lastIndex = andList.lastIndexOf(idKey);
						}
					}
					else if (key.equals(userIdInKey)) {
						setUserIdIn(value);
						lastIndex = andList.lastIndexOf(userIdKey);
						if (lastIndex < 0) {
							lastIndex = andList.lastIndexOf(idInKey);
						}
						if (lastIndex < 0) {
							lastIndex = andList.lastIndexOf(idKey);
						}
					}
					else {
						lastIndex = andList.lastIndexOf(userIdInKey);
						if (lastIndex < 0) {
							lastIndex = andList.lastIndexOf(userIdKey);
						}
						if (lastIndex < 0) {
							lastIndex = andList.lastIndexOf(idInKey);
						}
						if (lastIndex < 0) {
							lastIndex = andList.lastIndexOf(idKey);
						}
					}

					i = lastIndex + 1;
				}

				if (prior) {
					andList.add(i, key); // userId 的优先级不能比 id 高  0, key);
				} else {
					// AbstractSQLExecutor.onPutColumn 里 getSQL，要保证缓存的 SQL 和查询的 SQL 里 where 的 key:value 顺序一致
					andList.add(key);
				}
			}
			combineMap.put("&", andList);
		}

		return this;
	}

	/**获取WHERE
	 * @return
	 * @throws Exception
	 */
	@Override
	public String gainWhereString(boolean hasPrefix) throws Exception {
		String combineExpr = getCombine();
		if (StringUtil.isEmpty(combineExpr, false)) {
			return getWhereString(hasPrefix, getMethod(), getWhere(), getCombineMap(), getJoinList(), ! isTest());
		}
		return getWhereString(hasPrefix, getMethod(), getWhere(), combineExpr, getJoinList(), ! isTest());
	}
	/**获取WHERE
	 * @param method
	 * @param where
	 * @return
	 * @throws Exception
	 */
	public String getWhereString(boolean hasPrefix, RequestMethod method, Map<String, Object> where
			, String combine, List<Join<T, M, L>> joinList, boolean verifyName) throws Exception {
		String whereString = parseCombineExpression(method, getQuote(), getTable(), getAlias()
				, where, combine, verifyName, false, false);
		whereString = concatJoinWhereString(whereString);
		String result = StringUtil.isEmpty(whereString, true) ? "" : (hasPrefix ? " WHERE " : "") + whereString;

		if (result.isEmpty() && RequestMethod.isQueryMethod(method) == false) {
			throw new UnsupportedOperationException("写操作请求必须带条件！！！");
		}

		return result;
	}

	/**解析 @combine 条件 key 组合的与或非+括号的逻辑运算表达式为具体的完整条件组合
	 * @param method
	 * @param quote
	 * @param table
	 * @param alias
	 * @param conditionMap  where 或 having 对应条件的 Map
	 * @param combine
	 * @param verifyName
	 * @param containRaw
	 * @param isHaving
	 * @return
	 * @throws Exception
	 */
	protected String parseCombineExpression(RequestMethod method, String quote, String table, String alias
			, Map<String, Object> conditionMap, String combine, boolean verifyName, boolean containRaw, boolean isHaving) throws Exception {

		String errPrefix = table + (isHaving ? ":{ @having:{ " : ":{ ") + "@combine:'" + combine + (isHaving ? "' } }" : "' }");
		String s = StringUtil.get(combine);
		if (s.startsWith(" ") || s.endsWith(" ") ) {
			throw new IllegalArgumentException(errPrefix + " 中字符 '" + s
					+ "' 不合法！不允许首尾有空格，也不允许连续空格！空格不能多也不能少！"
					+ "逻辑连接符 & | 左右必须各一个相邻空格！左括号 ( 右边和右括号 ) 左边都不允许有相邻空格！");
		}

		if (conditionMap == null) {
			conditionMap = new HashMap<>();
		}
		int size = conditionMap.size();

		int maxCount = isHaving ? getMaxHavingCount() : getMaxWhereCount();
		if (maxCount > 0 && size > maxCount) {
			throw new IllegalArgumentException(table + (isHaving ? ":{ @having:{ " : ":{ ") + "key0:value0, key1:value1... " + combine
					+ (isHaving ? " } }" : " }") + " 中条件 key:value 数量 " + size + " 已超过最大数量，必须在 0-" + maxCount + " 内！");
		}

		String result = "";

		List<Object> preparedValues = getPreparedValueList();
		if (preparedValues == null && isHaving == false) {
			preparedValues = new ArrayList<>();
		}

		Map<String, Integer> usedKeyCountMap = new HashMap<>(size);

		int n = s.length();
		if (n > 0) {
			if (isHaving == false) {  // 只收集表达式条件值
				setPreparedValueList(new ArrayList<>());  // 必须反过来，否则 JOIN ON 内部 @combine 拼接后顺序错误
			}

			int maxDepth = getMaxCombineDepth();
			int maxCombineCount = getMaxCombineCount();
			int maxCombineKeyCount = getMaxCombineKeyCount();
			float maxCombineRatio = getMaxCombineRatio();

			int depth = 0;
			int allCount = 0;

			int i = 0;

			char lastLogic = 0;
			char last = 0;
			boolean first = true;
			boolean isNot = false;

			String key = "";
			while (i <= n) {  // "date> | (contactIdList<> & (name*~ | tag&$))"
				boolean isOver = i >= n;
				char c = isOver ? 0 : s.charAt(i);
				boolean isBlankOrRightParenthesis = c == ' ' || c == ')';
				if (isOver || isBlankOrRightParenthesis) {
					boolean isEmpty = StringUtil.isEmpty(key, true);
					if (isEmpty && last != ')') {
						throw new IllegalArgumentException(errPrefix + " 中字符 '" + (isOver ? s : s.substring(i))
								+ "' 不合法！" + (c == ' ' ? "空格 ' ' " : "右括号 ')'") + " 左边缺少条件 key ！逻辑连接符 & | 左右必须各一个相邻空格！"
								+ "空格不能多也不能少！不允许首尾有空格，也不允许连续空格！左括号 ( 的右边 和 右括号 ) 的左边 都不允许有相邻空格！");
					}

					if (isEmpty == false) {
						if (first == false && lastLogic <= 0) {
							throw new IllegalArgumentException(errPrefix + " 中字符 "
									+ "'" + s.substring(i - key.length() - (isOver ? 1 : 0))
									+ "' 不合法！左边缺少 & | 其中一个逻辑连接符！");
						}

						allCount ++;
						if (allCount > maxCombineCount && maxCombineCount > 0) {
							throw new IllegalArgumentException(errPrefix + " 中字符 '" + s + "' 不合法！"
									+ "其中 key 数量 " + allCount + " 已超过最大值，必须在条件键值对数量 0-" + maxCombineCount + " 内！");
						}

						String column = key;
						int keyIndex = column.indexOf(":");
						column = keyIndex > 0 ? column.substring(0, keyIndex) : column;
						Object value = conditionMap.get(column);
						String wi = "";
						if (value == null && conditionMap.containsKey(column) == false) { // 兼容@null
							isNot = false; // 以占位表达式为准
							size++; // 兼容 key 数量判断
							wi = keyIndex > 0 ? key.substring(keyIndex + 1) : "";
							if (StringUtil.isEmpty(wi)) {
								throw new IllegalArgumentException(errPrefix + " 中字符 '"
										+ key + "' 对应的条件键值对 " + column + ":value 不存在！");
							}
						} else {
							wi = isHaving ? gainHavingItem(quote, table, alias, column, (String) value, containRaw)
									: gainWhereItem(column, value, method, verifyName);
						}

						if (1.0f*allCount/size > maxCombineRatio && maxCombineRatio > 0) {
							throw new IllegalArgumentException(errPrefix + " 中字符 '" + s + "' 不合法！"
									+ "其中 key 数量 " + allCount + " / 条件键值对数量 " + size + " = " + (1.0f*allCount/size)
									+ " 已超过 最大倍数，必须在条件键值对数量 0-" + maxCombineRatio + " 倍内！");
						}

						if (StringUtil.isEmpty(wi, true)) {  // 转成 1=1 ?
							throw new IllegalArgumentException(errPrefix + " 中字符 '" + key
									+ "' 对应的 " + column + ":value 不是有效条件键值对！");
						}

						Integer count = usedKeyCountMap.get(column);
						count = count == null ? 1 : count + 1;
						if (count > maxCombineKeyCount && maxCombineKeyCount > 0) {
							throw new IllegalArgumentException(errPrefix + " 中字符 '" + s + "' 不合法！"
									+ "其中 '" + column + "' 重复引用，次数 " + count
									+ " 已超过最大值，必须在 0-" + maxCombineKeyCount + " 内！");
						}
						usedKeyCountMap.put(column, count);

						result += "( " + gainCondition(isNot, wi) + " )";
						isNot = false;
						first = false;
					}

					key = "";
					lastLogic = 0;

					if (isOver) {
						break;
					}
				}

				if (c == ' ') {
				}
				else if (c == '&') {
					if (last == ' ') {
						if (i >= n - 1 || s.charAt(i + 1) != ' ') {
							throw new IllegalArgumentException(errPrefix + " 中字符 '" + (i >= n - 1 ? s : s.substring(0, i + 1))
									+ "' 不合法！逻辑连接符 & 右边缺少一个空格 ！逻辑连接符 & | 左右必须各一个相邻空格！空格不能多也不能少！"
									+ "不允许首尾有空格，也不允许连续空格！左括号 ( 的右边 和 右括号 ) 的左边 都不允许有相邻空格！");
						}

						result += SQL.AND;
						lastLogic = c;
						i ++;
					}
					else {
						key += c;
					}
				}
				else if (c == '|') {
					if (last == ' ') {
						if (i >= n - 1 || s.charAt(i + 1) != ' ') {
							throw new IllegalArgumentException(table + ":{ @combine: '" + combine
									+ "' } 中字符 '" + (i >= n - 1 ? s : s.substring(0, i + 1))
									+ "' 不合法！逻辑连接符 | 右边缺少一个空格 ！逻辑连接符 & | 左右必须各一个相邻空格！空格不能多也不能少！"
									+ "不允许首尾有空格，也不允许连续空格！左括号 ( 右边和右括号 ) 左边都不允许有相邻空格！");
						}

						result += SQL.OR;
						lastLogic = c;
						i ++;
					}
					else {
						key += c;
					}
				}
				else if (c == '!') {
					last = i <= 0 ? 0 : s.charAt(i - 1);  // & | 后面跳过了空格

					char next = i >= n - 1 ? 0 : s.charAt(i + 1);
					if (last == ' ' || last == '(') {
						if (next == ' ') {
							throw new IllegalArgumentException(errPrefix + " 中字符 '" + s.substring(0, i + 1)
							+ "' 不合法！非逻辑符 '!' 右边多了一个空格 ' ' ！非逻辑符 '!' " +
									"右边不允许任何相邻空格 ' '，也不允许 ')' '&' '|' 中任何一个！");
						}
						if (next == ')' || next == '&' || next == '!') {
							throw new IllegalArgumentException(errPrefix + " 中字符 '" + s.substring(0, i + 1)
							+ "' 不合法！非逻辑符 '!' 右边多了一个字符 '"
									+ next + "' ！非逻辑符 '!' 右边不允许任何相邻空格 ' '，也不允许 ')' '&' '|' 中任何一个！");
						}
						if (i > 0 && lastLogic <= 0 && last != '(') {
							throw new IllegalArgumentException(errPrefix + " 中字符 '" + s.substring(i)
							+ "' 不合法！左边缺少 & | 逻辑连接符！逻辑连接符 & | 左右必须各一个相邻空格！空格不能多也不能少！"
							+ "不允许首尾有空格，也不允许连续空格！左括号 ( 的右边 和 右括号 ) 的左边 都不允许有相邻空格！");
						}
					}

					if (next == '(') {
						result += SQL.NOT;
						lastLogic = c;
					}
					else if (last <= 0 || last == ' ' || last == '(') {
						isNot = true;
						//					lastLogic = c;
					}
					else {
						key += c;
					}
				}
				else if (c == '(') {
					if (key.isEmpty() == false || (i > 0 && lastLogic <= 0 && last != '(')) {
						throw new IllegalArgumentException(errPrefix + " 中字符 '" + s.substring(i)
						+ "' 不合法！左边缺少 & | 逻辑连接符！逻辑连接符 & | 左右必须各一个相邻空格！空格不能多也不能少！"
						+ "不允许首尾有空格，也不允许连续空格！左括号 ( 的右边 和 右括号 ) 的左边 都不允许有相邻空格！");
					}

					depth ++;
					if (depth > maxDepth && maxDepth > 0) {
						throw new IllegalArgumentException(errPrefix + " 中字符 '" + s.substring(0, i + 1)
						+ "' 不合法！括号 (()) 嵌套层级 " + depth + " 已超过最大值，必须在 0-" + maxDepth + " 内！");
					}

					result += c;
					lastLogic = 0;
					first = true;
				}
				else if (c == ')') {
					depth --;
					if (depth < 0) {
						throw new IllegalArgumentException(errPrefix + " 中字符 '" + s.substring(0, i + 1)
						+ "' 不合法！左括号 ( 比 右括号 ) 少！数量必须相等从而完整闭合 (...) ！");
					}

					result += c;
					lastLogic = 0;
				}
				else {
					key += c;
				}

				last = c;
				i ++;
			}

			if (depth != 0) {
				throw new IllegalArgumentException(errPrefix + " 中字符 '" + s
						+ "' 不合法！左括号 ( 比 右括号 ) 多！数量必须相等从而完整闭合 (...) ！");
			}
		}

		List<Object> exprPreparedValues = getPreparedValueList();
		if (isHaving == false) {  // 只收集 AND 条件值
			setPreparedValueList(new ArrayList<>());
		}

		Set<Entry<String, Object>> set = conditionMap.entrySet();

		String andCond = "";
		boolean isItemFirst = true;

		for (Entry<String, Object> entry : set) {
			String key = entry == null ? null : entry.getKey();
			if (key == null || usedKeyCountMap.containsKey(key)) {
				continue;
			}

			String wi = isHaving ? gainHavingItem(quote, table, alias, key, (String) entry.getValue(), containRaw)
					: gainWhereItem(key, entry.getValue(), method, verifyName);
			if (StringUtil.isEmpty(wi, true)) {//避免SQL条件连接错误
				continue;
			}

			andCond += (isItemFirst ? "" : AND) + "(" + wi + ")";
			isItemFirst = false;
		}

		if (isHaving == false) {  // 优先存放 AND 条件值
			preparedValues.addAll(getPreparedValueList());
		}

		if (StringUtil.isEmpty(result, true)) {
			result = andCond;
		}
		else if (StringUtil.isNotEmpty(andCond, true)) {  // andCond 必须放后面，否则 prepared 值顺序错误
			if (isHaving) {
				// HAVING 前 WHERE 已经有条件 ? 占位，不能反过来，想优化 AND 连接在最前，需要多遍历一次内部的 key，也可以 newSQLConfig<T, M, L> 时存到 andList
				result = "( " + result + " )" + AND + andCond;
			}
			else {
				result = andCond + AND + "( " + result + " )";  // 先暂存之前的 prepared 值，然后反向整合
			}
		}

		if (isHaving == false) {
			if (exprPreparedValues != null && exprPreparedValues.isEmpty() == false) {
				preparedValues.addAll(exprPreparedValues);  // 在 AND 条件值后存放表达式内的条件值
			}
			setPreparedValueList(preparedValues);
		}

		return result;
	}

	/**@combine:"a,b" 条件组合。虽然有了 @combine:"a | b" 这种新方式，但为了 Join 多个 On 能保证顺序正确，以及这个性能更好，还是保留这个方式
	 * @param hasPrefix
	 * @param method
	 * @param where
	 * @param combine
	 * @param joinList
	 * @param verifyName
	 * @return
	 * @throws Exception
	 */
	public String getWhereString(boolean hasPrefix, RequestMethod method, Map<String, Object> where
		, Map<String, List<String>> combine, List<Join<T, M, L>> joinList, boolean verifyName) throws Exception {
		Set<Entry<String, List<String>>> combineSet = combine == null ? null : combine.entrySet();
		if (combineSet == null || combineSet.isEmpty()) {
			Log.w(TAG, "getWhereString  combineSet == null || combineSet.isEmpty() >> return \"\";");
			return "";
		}

		List<String> keyList;

		String whereString = "";

		boolean isCombineFirst = true;
		int logic;

		boolean isItemFirst;
		String c;
		String cs;

		for (Entry<String, List<String>> ce : combineSet) {
			keyList = ce == null ? null : ce.getValue();
			if (keyList == null || keyList.isEmpty()) {
				continue;
			}

			if ("|".equals(ce.getKey())) {
				logic = Logic.TYPE_OR;
			}
			else if ("!".equals(ce.getKey())) {
				logic = Logic.TYPE_NOT;
			}
			else {
				logic = Logic.TYPE_AND;
			}

			isItemFirst = true;
			cs = "";
			for (String key : keyList) {
				c = gainWhereItem(key, where.get(key), method, verifyName);

				if (StringUtil.isEmpty(c, true)) {//避免SQL条件连接错误
					continue;
				}

				cs += (isItemFirst ? "" : (Logic.isAnd(logic) ? AND : OR)) + "(" + c + ")";
				isItemFirst = false;
			}

			if (StringUtil.isEmpty(cs, true)) {//避免SQL条件连接错误
				continue;
			}

			whereString += (isCombineFirst ? "" : AND) + (Logic.isNot(logic) ? NOT : "") + " (  " + cs + "  ) ";
			isCombineFirst = false;
		}

		whereString = concatJoinWhereString(whereString);

		String s = StringUtil.isEmpty(whereString, true) ? "" : (hasPrefix ? " WHERE " : "") + whereString;

		if (s.isEmpty() && RequestMethod.isQueryMethod(method) == false) {
			throw new UnsupportedOperationException("写操作请求必须带条件！！！");
		}

		return s;
	}


	protected String concatJoinWhereString(String whereString) throws Exception {
		List<Join<T, M, L>> joinList = getJoinList();
		if (joinList != null) {

			String newWs = "";
			String ws = whereString;

			List<Object> newPvl = new ArrayList<>();
			List<Object> pvl = new ArrayList<>(getPreparedValueList());

			SQLConfig<T, M, L> jc;
			SQLConfig<T, M, L> outerConfig;
			String js;
			boolean isWsEmpty = StringUtil.isEmpty(ws, true);
			boolean changed = false;
			// 各种 JOIN 没办法统一用 & | ！连接，只能按优先级，和 @combine 一样?
			for (Join<T, M, L> j : joinList) {
				String jt = j.getJoinType();

				switch (jt) {
				case "*": // CROSS JOIN
				case "@": // APP JOIN
				case "<": // LEFT JOIN
				case ">": // RIGHT JOIN
					outerConfig = j.getOuterConfig();
					if (outerConfig == null){
						break;
					}
					boolean isMain1 = outerConfig.isMain();
					outerConfig.setMain(false).setPrepared(isPrepared()).setPreparedValueList(new ArrayList<Object>());
					String outerWhere = outerConfig.gainWhereString(false);

					int logic1 = Logic.getType(jt);
					newWs += " ( "
							+ gainCondition(
							Logic.isNot(logic1),
							ws
									+ ( isWsEmpty ? "" : (Logic.isAnd(logic1) ? AND : OR) )
									+ " ( " + outerWhere + " ) "
					)
							+ " ) ";
					newPvl.addAll(pvl);
					newPvl.addAll(outerConfig.getPreparedValueList());

					changed = true;
					break;

				case "&": // INNER JOIN: A & B
				case "":  // FULL JOIN: A | B
				case "|": // FULL JOIN: A | B
				case "!": // OUTER JOIN: ! (A | B)
				case "^": // SIDE JOIN: ! (A & B)
				case "(": // ANTI JOIN: A & ! B
				case ")": // FOREIGN JOIN: B & ! A
				case "~": // ASOF JOIN: B ~= A
					jc = j.getJoinConfig();
					boolean isMain = jc.isMain();
					jc.setMain(false).setPrepared(isPrepared()).setPreparedValueList(new ArrayList<Object>());
					js = jc.gainWhereString(false);
					jc.setMain(isMain);

					boolean isOuterJoin = "!".equals(jt);
					boolean isSideJoin = "^".equals(jt);
					boolean isAntiJoin = "(".equals(jt);
					boolean isForeignJoin = ")".equals(jt);
					//boolean isWsEmpty = StringUtil.isEmpty(ws, true);

					if (isWsEmpty) {
						if (isOuterJoin) { // ! OUTER JOIN: ! (A | B)
							throw new NotExistException("no result for ! OUTER JOIN( ! (A | B) ) when A or B is empty!");
						}
						if (isForeignJoin) { // ) FOREIGN JOIN: B & ! A
							throw new NotExistException("no result for ) FOREIGN JOIN( B & ! A ) when A is empty!");
						}
					}

					if (StringUtil.isEmpty(js, true)) {
						if (isOuterJoin) { // ! OUTER JOIN: ! (A | B)
							throw new NotExistException("no result for ! OUTER JOIN( ! (A | B) ) when A or B is empty!");
						}
						if (isAntiJoin) { // ( ANTI JOIN: A & ! B
							throw new NotExistException("no result for ( ANTI JOIN( A & ! B ) when B is empty!");
						}

						if (isWsEmpty) {
							if (isSideJoin) {
								throw new NotExistException("no result for ^ SIDE JOIN( ! (A & B) ) when both A and B are empty!");
							}
						}
						else {
							if (isSideJoin || isForeignJoin) {
								newWs += " ( " + gainCondition(true, ws) + " ) ";

								newPvl.addAll(pvl);
								newPvl.addAll(jc.getPreparedValueList());
								changed = true;
							}
						}

						continue;
					}

					if (StringUtil.isNotEmpty(newWs, true)) {
						newWs += AND;
					}

					if (isAntiJoin) { // ( ANTI JOIN: A & ! B
						newWs += " ( " + ( isWsEmpty ? "" : ws + AND ) + NOT + " ( " + js + " ) " + " ) ";
					}
					else if (isForeignJoin) { // ) FOREIGN JOIN: (! A) & B  // preparedValueList.add 不好反过来  B & ! A
						newWs += " ( " + NOT + " ( " + ws + " ) ) " + AND + " ( " + js + " ) ";
					}
					else if (isSideJoin) { // ^ SIDE JOIN:  ! (A & B)
						//MySQL 因为 NULL 值处理问题，(A & ! B) | (B & ! A) 与 ! (A & B) 返回结果不一样，后者往往更多
						newWs += " ( " + gainCondition(
								true,
								( isWsEmpty ? "" : ws + AND ) + " ( " + js + " ) "
								) + " ) ";
					}
					else {  // & INNER JOIN: A & B; | FULL JOIN: A | B; OUTER JOIN: ! (A | B)
						int logic = Logic.getType(jt);
						newWs += " ( "
								+ gainCondition(
										Logic.isNot(logic),
										ws
										+ ( isWsEmpty ? "" : (Logic.isAnd(logic) ? AND : OR) )
										+ " ( " + js + " ) "
										)
								+ " ) ";
					}

					newPvl.addAll(pvl);
					newPvl.addAll(jc.getPreparedValueList());

					changed = true;
					break;
				default:
					throw new UnsupportedOperationException(
							"join:value 中 value 里的 " + jt + "/" + j.getPath()
							+ "错误！不支持 " + jt + " 等 [ @ APP, < LEFT, > RIGHT, * CROSS"
							+ ", & INNER, | FULL, ! OUTER, ^ SIDE, ( ANTI, ) FOREIGN, ~ ASOF ] 之外的 JOIN 类型 !"
							);
				}
			}

			if (changed) {
				whereString = newWs;
				setPreparedValueList(newPvl);
			}
		}

		return whereString;
	}


	/**
	 * @param key
	 * @param value
	 * @param method
	 * @param verifyName
	 * @return
	 * @throws Exception
	 */
	protected String gainWhereItem(String key, Object value, RequestMethod method, boolean verifyName) throws Exception {
		Log.d(TAG, "getWhereItem  key = " + key);
		// 避免筛选到全部	value = key == null ? null : where.get(key);
		if (key == null || key.endsWith("()") || key.startsWith("@")) { //关键字||方法, +或-直接报错
			Log.d(TAG, "getWhereItem  key == null || key.endsWith(()) || key.startsWith(@) >> continue;");
			return null;
		}
		if (key.endsWith("@")) { // 引用
			//	key = key.substring(0, key.lastIndexOf("@"));
			throw new IllegalArgumentException(TAG + ".getWhereItem: 字符 " + key + " 不合法！");
		}

		if (value == null) {
			return null;
		}

		int keyType;
		if (key.endsWith("$")) {
			keyType = 1;
		}
		else if (key.endsWith("~")) {
			keyType = key.charAt(key.length() - 2) == '*' ? -2 : 2;  //FIXME StringIndexOutOfBoundsException
		}
		else if (key.endsWith("%")) {
			keyType = 3;
		}
		else if (key.endsWith("{}")) {
			keyType = 4;
		}
		else if (key.endsWith("}{")) {
			keyType = 5;
		}
		else if (key.endsWith("<>")) {
			keyType = 6;
		}
		else if (key.endsWith(">=")) {
			keyType = 7;
		}
		else if (key.endsWith("<=")) {
			keyType = 8;
		}
		else if (key.endsWith(">")) {
			keyType = 9;
		}
		else if (key.endsWith("<")) {
			keyType = 10;
		} else {  // else绝对不能省，避免再次踩坑！ keyType = 0; 写在for循环外面都没注意！
			keyType = 0;
		}

		String column = gainRealKey(method, key, false, true, verifyName);

		// 原始 SQL 片段
		String rawSQL = gainRawSQL(key, value);

		switch (keyType) {
		case 1:
			return gainSearchString(key, column, value, rawSQL);
		case -2:
		case 2:
			return gainRegExpString(key, column, value, keyType < 0, rawSQL);
		case 3:
			return gainBetweenString(key, column, value, rawSQL);
		case 4:
			return gainRangeString(key, column, value, rawSQL);
		case 5:
			return gainExistsString(key, column, value, rawSQL);
		case 6:
			return gainContainString(key, column, value, rawSQL);
		case 7:
			return gainCompareString(key, column, value, ">=", rawSQL);
		case 8:
			return gainCompareString(key, column, value, "<=", rawSQL);
		case 9:
			return gainCompareString(key, column, value, ">", rawSQL);
		case 10:
			return gainCompareString(key, column, value, "<", rawSQL);
		default:  // TODO MySQL JSON类型的字段对比 key='[]' 会无结果！ key LIKE '[1, 2, 3]'  //TODO MySQL , 后面有空格！
			return gainEqualString(key, column, value, rawSQL);
		}
	}


	public String gainEqualString(String key, String column, Object value, String rawSQL) throws Exception {
		if (value != null && JSON.isBoolOrNumOrStr(value) == false && value instanceof Subquery == false) {
			throw new IllegalArgumentException(key + ":value 中value不合法！非PUT请求只支持 [Boolean, Number, String] 内的类型 ！");
		}

		boolean not = column.endsWith("!"); // & | 没有任何意义，写法多了不好控制
		if (not) {
			column = column.substring(0, column.length() - 1);
		}

		String rc = column.endsWith("[") || column.endsWith("{") ? column.substring(0, column.length() - 1) : column;
		if (StringUtil.isName(rc) == false) {
			throw new IllegalArgumentException(key + ":value 中key不合法！不支持 ! 以外的逻辑符 ！");
		}

		String logic = value == null && rawSQL == null ? (not ? SQL.IS_NOT : SQL.IS) : (not ? " != " : " = ");
		return gainKey(column) + logic + (value instanceof Subquery ? gainSubqueryString((Subquery<T, M, L>) value)
				: (rawSQL != null ? rawSQL : gainValue(key, column, value)));
	}

	public String gainCompareString(String key, String column, Object value, String type, String rawSQL) throws Exception {
		if (value != null && JSON.isBoolOrNumOrStr(value) == false && value instanceof Subquery == false) {
			throw new IllegalArgumentException(key + ":value 中 value 不合法！比较运算 [>, <, >=, <=] 只支持 [Boolean, Number, String] 内的类型 ！");
		}

		String rc = column.endsWith("[") || column.endsWith("{") ? column.substring(0, column.length() - 1) : column;
		if ( ! StringUtil.isName(rc)) {
			throw new IllegalArgumentException(key + ":value 中 key 不合法！比较运算 [>, <, >=, <=] 不支持 [&, !, |] 中任何逻辑运算符 ！");
		}

		return gainKey(column) + " " + type + " " + (value instanceof Subquery ? gainSubqueryString((Subquery<T, M, L>) value)
				: (rawSQL != null ? rawSQL : gainValue(key, column, value)));
	}

	public String gainKey(@NotNull String key) {
		String lenFun = "";
		if (key.endsWith("[")) {
			lenFun = isSQLServer() || isKingBaseSQLServer() ? "datalength" : "length";
			key = key.substring(0, key.length() - 1);
		}
		else if (key.endsWith("{")) {
			lenFun = "json_length";
			key = key.substring(0, key.length() - 1);
		}
		else if (isTest()) {
			if (key.contains("'")) {  // || key.contains("#") || key.contains("--")) {
				throw new IllegalArgumentException("参数 " + key + " 不合法！key 中不允许有单引号 ' ！");
			}
			return gainSQLValue(key).toString();
		}

		Map<String, String> keyMap = getKeyMap();
		String expression = keyMap == null ? null : keyMap.get(key);
		if (expression == null) {
			expression = COLUMN_KEY_MAP == null ? null : COLUMN_KEY_MAP.get(key);
		}

		String sqlKey;
		if (expression == null) {
			sqlKey = gainSQLKey(key);
		}
		else {
			// (name,tag) left(date,4) 等
			List<String> raw = getRaw();
			sqlKey = parseSQLExpression(KEY_KEY, expression, raw != null && raw.contains(KEY_KEY), false);
		}

		return lenFun.isEmpty() ? sqlKey : lenFun + "(" + sqlKey + ")";
	}
	public String gainSQLKey(String key) {
		String q = getQuote();
		return (isKeyPrefix() ? q + gainSQLAlias() + q + "." : "") + q + key + q;
	}

	/**
	 * 使用prepareStatement预编译，值为 ? ，后续动态set进去
	 */
	protected Object gainValue(@NotNull Object value) {
		return gainValue(null, null, value);
	}

	protected List<Object> preparedValueList = new ArrayList<>();
	protected Object gainValue(String key, String column, Object value) {
		if (isPrepared()) {
			if (value == null) {
				return null;
			}

			Map<String, String> castMap = getCast();
			String type = key == null || castMap == null ? null : castMap.get(key);

			//			if ("DATE".equalsIgnoreCase(type) && value instanceof Date == false) {
			//				value = value instanceof Number ? new Date(((Number) value).longValue()) : Date.valueOf((String) value);
			//			}
			//			else if ("TIME".equalsIgnoreCase(type) && value instanceof Time == false) {
			//				value = value instanceof Number ? new Time(((Number) value).longValue()) : Time.valueOf((String) value);
			//			}
			//			else if ("TIMESTAMP".equalsIgnoreCase(type) && value instanceof Timestamp == false) {
			//				value = value instanceof Number ? new Timestamp(((Number) value).longValue()) : Timestamp.valueOf((String) value);
			//			}
			//			else if ("ARRAY".equalsIgnoreCase(type) && value instanceof Array == false) {
			//				value = ((Collection<?>) value).toArray();
			//			}
			//			else if (StringUtil.isEmpty(type, true) == false) {
			//				preparedValueList.add(value);
			//				return "cast(?" + SQL.AS + type + ")";
			//			}

			preparedValueList.add(value);
			return StringUtil.isEmpty(type, true) ? "?" : "cast(?" + SQL.AS + type + ")";
		}

		return key == null ? gainSQLValue(value) : gainSQLValue(key, column, value);
	}

	public Object gainSQLValue(String key, String column, @NotNull Object value) {
		Map<String, String> castMap = getCast();
		String type = key == null || castMap == null ? null : castMap.get(key);
		Object val = gainSQLValue(value);
		return StringUtil.isEmpty(type, true) ? val : "cast(" + val + SQL.AS + type + ")";
	}
	public Object gainSQLValue(@NotNull Object value) {
		if (value == null) {
			return SQL.NULL;
		}
		//	return (value instanceof Number || value instanceof Boolean)
		//	&& DATABASE_POSTGRESQL.equals(getDatabase()) ? value :  "'" + value + "'";
		return (value instanceof Number || value instanceof Boolean)
				? value :  "'" + value.toString().replaceAll("\\'", "\\\\'") + "'"; // MySQL 隐式转换用不了索引
	}

	@Override
	public List<Object> getPreparedValueList() {
		return preparedValueList;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setPreparedValueList(List<Object> preparedValueList) {
		this.preparedValueList = preparedValueList;
		return this;
	}

	//$ search <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**search key match value
	* @param key
	* @param column
	* @param value
	* @param rawSQL
	* @return {@link #gainSearchString(String, String, Object[], int)}
	* @throws IllegalArgumentException
	*/
	public String gainSearchString(String key, String column, Object value, String rawSQL) throws IllegalArgumentException {
		if (rawSQL != null) {
			throw new UnsupportedOperationException("@raw:value 中 "
					+ key + " 不合法！@raw 不支持 key$ 这种功能符 ！只支持 key, key!, key<, key{} 等比较运算 和 @column, @having ！");
		}
		if (value == null) {
			return "";
		}

		Logic logic = new Logic(column);
		column = logic.getKey();
		Log.i(TAG, "getSearchString column = " + column);

		List<Object> arr = newJSONArray(value);
		if (arr.isEmpty()) {
			return "";
		}
		return gainSearchString(key, column, arr.toArray(), logic.getType());
	}
	/**search key match values
	* @param key
	* @param column
	* @param values
	* @param type
	* @return LOGIC [  key LIKE 'values[i]' ]
	* @throws IllegalArgumentException
	*/
	public String gainSearchString(String key, String column, Object[] values, int type) throws IllegalArgumentException {
		if (values == null || values.length <= 0) {
			return "";
		}

		String condition = "";
		for (int i = 0; i < values.length; i++) {
			Object v = values[i];
			if (v instanceof String == false) {
				throw new IllegalArgumentException(key + ":value 中 value 的类型只能为 String 或 String[]！");
			}
			if (((String) v).isEmpty()) {  // 允许查空格 StringUtil.isEmpty((String) v, true)
				throw new IllegalArgumentException(key + ":value 中 value 值 " + v + "是空字符串，没有意义，不允许这样传！");
			}
			//			if (((String) v).contains("%%")) {  // 需要通过 %\%% 来模糊搜索 %
			//				throw new IllegalArgumentException(key + "$:value 中 value 值 " + v + " 中包含 %% ！不允许有连续的 % ！");
			//			}

			condition += (i <= 0 ? "" : (Logic.isAnd(type) ? AND : OR)) + gainLikeString(key, column, (String) v);
		}

		return gainCondition(Logic.isNot(type), condition);
	}

	/**WHERE key LIKE 'value'
	 * @param key
	 * @param column
	 * @param value
	 * @return key LIKE 'value'
	 */
	public String gainLikeString(@NotNull String key, @NotNull String column, String value) {
		String k = key.substring(0, key.length() - 1);
		char r = k.charAt(k.length() - 1);

		char l;
		if (r == '%' || r == '_' || r == '?') {
			k = k.substring(0, k.length() - 1);

			l = k.charAt(k.length() - 1);
			if (l == '%' || l == '_' || l == '?') {
				if (l == r) {
					throw new IllegalArgumentException(key + ":value 中字符 "
							+ k + " 不合法！key$:value 中不允许 key 中有连续相同的占位符！");
				}

				k = k.substring(0, k.length() - 1);
			}
			else if (l > 0 && StringUtil.isName(String.valueOf(l))) {
				l = r;
			}

			if (l == '?') {
				l = 0;
			}
			if (r == '?') {
				r = 0;
			}
		}
		else {
			l = r = 0;
		}

		if (l > 0 || r > 0) {
			if (value == null) {
				throw new IllegalArgumentException(key + ":value 中 value 为 null！" +
						"key$:value 中 value 不能为 null，且类型必须是 String ！");
			}

			value = value.replaceAll("\\\\", "\\\\\\\\");
			value = value.replaceAll("\\%", "\\\\%");
			value = value.replaceAll("\\_", "\\\\_");
			if (l > 0) {
				value = l + value;
			}
			if (r > 0) {
				value = value + r;
			}
		}

		return gainKey(column) + " LIKE "  + gainValue(key, column, value);
	}

	//$ search >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	//~ regexp <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**search key match RegExp values
	 * @param key
	 * @param column
	 * @param value
	 * @param ignoreCase
	 * @return {@link #gainRegExpString(String, String, Object[], int, boolean)}
	 * @throws IllegalArgumentException
	 */
	public String gainRegExpString(String key, String column, Object value, boolean ignoreCase, String rawSQL)
			throws IllegalArgumentException {
		if (rawSQL != null) {
			throw new UnsupportedOperationException("@raw:value 中 " + key + " 不合法！@raw 不支持 key~ 这种功能符 ！" +
					"只支持 key, key!, key<, key{} 等比较运算 和 @column, @having ！");
		}
		if (value == null) {
			return "";
		}

		Logic logic = new Logic(column);
		column = logic.getKey();
		Log.i(TAG, "getRegExpString column = " + column);

		L arr = newJSONArray(value);
		if (arr.isEmpty()) {
			return "";
		}
		return gainRegExpString(key, column, arr.toArray(), logic.getType(), ignoreCase);
	}
	/**search key match RegExp values
	 * @param key
	 * @param values
	 * @param type
	 * @param ignoreCase
	 * @return LOGIC [  key REGEXP 'values[i]' ]
	 * @throws IllegalArgumentException
	 */
	public String gainRegExpString(String key, String column, Object[] values, int type, boolean ignoreCase)
			throws IllegalArgumentException {
		if (values == null || values.length <= 0) {
			return "";
		}

		String condition = "";
		for (int i = 0; i < values.length; i++) {
			if (values[i] instanceof String == false) {
				throw new IllegalArgumentException(key + ":value 中value的类型只能为String或String[]！");
			}
			condition += (i <= 0 ? "" : (Logic.isAnd(type) ? AND : OR))
					+ gainRegExpString(key, column, (String) values[i], ignoreCase);
		}

		return gainCondition(Logic.isNot(type), condition);
	}

	/**WHERE key REGEXP 'value'
	 * @param key
	 * @param value
	 * @param ignoreCase
	 * @return key REGEXP 'value'
	 */
	public String gainRegExpString(String key, String column, String value, boolean ignoreCase) {
		if (isPSQL() || isKingBaseSQLServer()) {
			return gainKey(column) + " ~" + (ignoreCase ? "* " : " ") + gainValue(key, column, value);
		}
		if (isOracle() || isDameng() || DATABASE_KINGBASE.equals(gainSQLDatabase())
				|| isKingBaseOracle() || isKingBaseMySQL() || (isMySQL() && gainDBVersionNums()[0] >= 8)) {
			return "regexp_like(" + gainKey(column) + ", " + gainValue(key, column, value) + (ignoreCase ? ", 'i'" : ", 'c'") + ")";
		}
		if (isPresto() || isTrino()) {
			return "regexp_like(" + (ignoreCase ? "lower(" : "") + gainKey(column) + (ignoreCase ? ")" : "")
		    + ", " + (ignoreCase ? "lower(" : "") + gainValue(key, column, value) + (ignoreCase ? ")" : "") + ")";
		}
		if (isClickHouse()) {
			return "match(" + (ignoreCase ? "lower(" : "") + gainKey(column) + (ignoreCase ? ")" : "")
					+ ", " + (ignoreCase ? "lower(" : "") + gainValue(key, column, value) + (ignoreCase ? ")" : "") + ")";
		}
		if (isElasticsearch()) {
			return gainKey(column) + " RLIKE " + gainValue(key, column, value);
		}
		if (isHive()) {
			return (ignoreCase ? "lower(" : "") + gainKey(column) + (ignoreCase ? ")" : "")
					+ " REGEXP " + (ignoreCase ? "lower(" : "") + gainValue(key, column, value) + (ignoreCase ? ")" : "");
		}
		return gainKey(column) + " REGEXP " + (ignoreCase ? "" : "BINARY ") + gainValue(key, column, value);
	}


	//~ regexp >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	//% between <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	/**WHERE key BETWEEN 'start' AND 'end'
	 * @param key
	 * @param value 'start,end'
	 * @return LOGIC [ key BETWEEN 'start' AND 'end' ]
	 * @throws IllegalArgumentException
	 */
	public String gainBetweenString(String key, String column, Object value, String rawSQL) throws IllegalArgumentException {
		if (rawSQL != null) {
			throw new UnsupportedOperationException("@raw:value 中 " + key + " 不合法！@raw 不支持 key% 这种功能符 ！" +
					"只支持 key, key!, key<, key{} 等比较运算 和 @column, @having ！");
		}
		if (value == null) {
			return "";
		}

		Logic logic = new Logic(column);
		column = logic.getKey();
		Log.i(TAG, "getBetweenString column = " + column);

		L arr = newJSONArray(value);
		if (arr.isEmpty()) {
			return "";
		}
		return gainBetweenString(key, column, arr.toArray(), logic.getType());
	}

	/**WHERE key BETWEEN 'start' AND 'end'
	 * @param key
	 * @param column
	 * @param values ['start,end'] TODO 在 '1,2' 和 ['1,2', '3,4'] 基础上新增支持 [1, 2] 和 [[1,2], [3,4]] ？
	 * @param type
	 * @return LOGIC [ key BETWEEN 'start' AND 'end' ]
	 * @throws IllegalArgumentException
	 */
	public String gainBetweenString(String key, String column, Object[] values, int type) throws IllegalArgumentException {
		if (values == null || values.length <= 0) {
			return "";
		}

		String condition = "";
		String[] vs;
		for (int i = 0; i < values.length; i++) {
			if (values[i] instanceof String == false) {
				throw new IllegalArgumentException(key + ":value 中 value 的类型只能为 String 或 String[] ！");
			}

			vs = StringUtil.split((String) values[i]);
			if (vs == null || vs.length != 2) {
				throw new IllegalArgumentException(key + ":value 中 value 不合法！类型为 String 时必须包括1个逗号 , " +
						"且左右两侧都有值！类型为 String[] 里面每个元素要符合前面类型为 String 的规则 ！");
			}

			condition += (i <= 0 ? "" : (Logic.isAnd(type) ? AND : OR))
					+ "(" + gainBetweenString(key, column, vs[0], (Object) vs[1]) + ")";
		}

		return gainCondition(Logic.isNot(type), condition);
	}

	/**WHERE key BETWEEN 'start' AND 'end'
	* @return key
	* @param column
	* @param start
	* @param end
	* @return LOGIC [ key BETWEEN 'start' AND 'end' ]
	* @throws IllegalArgumentException
	*/
	public String gainBetweenString(String key, String column, Object start, Object end) throws IllegalArgumentException {
		if (JSON.isBoolOrNumOrStr(start) == false || JSON.isBoolOrNumOrStr(end) == false) {
			throw new IllegalArgumentException(key + ":value 中 value 不合法！类型为 String 时必须包括1个逗号 , " +
					"且左右两侧都有值！类型为 String[] 里面每个元素要符合前面类型为 String 的规则 ！");
		}
		return gainKey(column) + " BETWEEN " + gainValue(key, column, start) + AND + gainValue(key, column, end);
	}


	//% between >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	//{} range <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


	/**WHERE key > 'key0' AND key <= 'key1' AND ...
	 * @param key
	 * @param range "condition0,condition1..."
	 * @return key condition0 AND key condition1 AND ...
	 * @throws Exception
	 */
	public String gainRangeString(String key, String column, Object range, String rawSQL) throws Exception {
		Log.i(TAG, "getRangeString column = " + column);
		if (range == null) {//依赖的对象都没有给出有效值，这个存在无意义。如果是客户端传的，那就能在客户端确定了。
			throw new NotExistException(TAG + "getRangeString(" + column + ", " + range + ") range == null");
		}

		Logic logic = new Logic(column);
		String k = logic.getKey();
		Log.i(TAG, "getRangeString k = " + k);

		if (range instanceof List) {
			if (rawSQL != null) {
				throw new UnsupportedOperationException("@raw:value 的 value 中 " + key + " 不合法！"
						+ "Raw SQL 不支持 key{}:[] 这种键值对！");
			}

			if (logic.isOr() || logic.isNot()) {
				List<?> l = (List<?>) range;
				if (logic.isNot() && l.isEmpty()) {
					return ""; // key!{}: [] 这个条件无效，加到 SQL 语句中 key IN() 会报错，getInString 里不好处理
				}
				return gainKey(k) + gainInString(k, column, l.toArray(), logic.isNot());
			}
			throw new IllegalArgumentException(key + ":[] 中 {} 前面的逻辑运算符错误！只能用'|','!'中的一种 ！");
		}
		else if (range instanceof String) {//非Number类型需要客户端拼接成 < 'value0', >= 'value1'这种
			String condition = "";
			String[] cs = rawSQL != null ? null : StringUtil.split((String) range, ";", false);

			if (rawSQL != null) {
				int index = rawSQL.indexOf("(");
				condition = (index >= 0 && index < rawSQL.lastIndexOf(")") ? "" : gainKey(k) + " ") + rawSQL;
			}

			if (cs != null) {
				List<String> raw = getRaw();
				boolean containRaw = raw == null ? false : raw.contains(key);
				String lk = logic.isAnd() ? AND : OR;

				for (int i = 0; i < cs.length; i++) {//对函数条件length(key)<=5这种不再在开头加key
					String expr = cs[i];

					if (expr.length() > 100) {
						throw new UnsupportedOperationException(key + ":value 的 value 中字符串 " + expr + " 不合法！"
								+ "不允许传超过 100 个字符的函数或表达式！请用 @raw 简化传参！");
					}

					int index = expr == null ? -1 : expr.indexOf("(");
					if (index >= 0) {
						expr = parseSQLExpression(key, expr, containRaw, false
								, key + ":\"!=null;+3*2<=10;function0(arg0,arg1,...)>1;function1(...)%5<=3...\"");
					}
					else {
						String fk = gainKey(k) + " ";
						String[] ccs = StringUtil.split(expr, false);
						expr = "";

						for (int j = 0; j < ccs.length; j++) {
							String c = ccs[j];
							if ("=null".equals(c)) {
								c = SQL.isNull();
							}
							else if ("!=null".equals(c)) {
								c = SQL.isNull(false);
							}
							else if (isPrepared() && (c.contains("--") || PATTERN_RANGE.matcher(c).matches() == false)) {
								throw new UnsupportedOperationException(key + ":value 的 value 中 " + c + " 不合法！"
										+ "预编译模式下 key{}:\"condition\" 中 condition 必须 为 =null 或 !=null " +
										"或 符合正则表达式 " + PATTERN_RANGE + " ！不允许连续减号 -- ！不允许空格！");
							}

							expr += (j <= 0 ? "" : lk) + fk + c;
						}
					}

					condition += ((i <= 0 ? "" : lk) + expr);
				}
			}

			if (condition.isEmpty()) {
				return "";
			}

			return gainCondition(logic.isNot(), condition);
		}
		else if (range instanceof Subquery) {
			// 如果在 Parser<T, M, L> 解析成 SQL 字符串再引用，没法保证安全性，毕竟可以再通过远程函数等方式来拼接再替代，最后引用的字符串就能注入
			return gainKey(k) + (logic.isNot() ? NOT : "") + " IN " + gainSubqueryString((Subquery) range);
		}

		throw new IllegalArgumentException(key + ":range 类型为" + range.getClass().getSimpleName()
				+ "！range 只能是 用','分隔条件的字符串 或者 可取选项JSONArray！");
	}
	/**WHERE key IN ('key0', 'key1', ... )
	 * @param in
	 * @return IN ('key0', 'key1', ... )
	 * @throws NotExistException
	 */
	public String gainInString(String key, String column, Object[] in, boolean not) throws NotExistException {
		String condition = "";
		if (in != null) {//返回 "" 会导致 id:[] 空值时效果和没有筛选id一样！
			for (int i = 0; i < in.length; i++) {
				condition += ((i > 0 ? "," : "") + gainValue(key, column, in[i]));
			}
		}
		if (condition.isEmpty()) {//条件如果存在必须执行，不能忽略。条件为空会导致出错，又很难保证条件不为空(@:条件)，所以还是这样好
			throw new NotExistException(TAG + ".getInString(" + key + "," + column + ", [], " + not + ") >> condition.isEmpty() >> IN()");
		}
		return (not ? NOT : "") + " IN (" + condition + ")";
	}
	//{} range >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//}{ exists <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**WHERE EXISTS subquery
	 * 如果合并到 getRangeString，一方面支持不了 [1,2,2] 和 ">1" (转成 EXISTS(SELECT IN ) 需要
	 * static newSQLConfig，但它不能传入子类实例，除非不是 static)，另一方面多了子查询临时表性能会比 IN 差
	 * @param key
	 * @param value
	 * @return EXISTS ALL(SELECT ...)
	 * @throws NotExistException
	 */
	public String gainExistsString(String key, String column, Object value, String rawSQL) throws Exception {
		if (rawSQL != null) {
			throw new UnsupportedOperationException("@raw:value 中 " + key + " 不合法！" +
					"@raw 不支持 key}{ 这种功能符 ！只支持 key, key!, key<, key{} 等比较运算 和 @column, @having ！");
		}
		if (value == null) {
			return "";
		}
		if (value instanceof Subquery == false) {
			throw new IllegalArgumentException(key + ":subquery 类型为" + value.getClass().getSimpleName()
					+ "！subquery 只能是 子查询JSONObejct！");
		}

		Logic logic = new Logic(column);
		column = logic.getKey();
		Log.i(TAG, "getExistsString column = " + column);

		return (logic.isNot() ? NOT : "") + " EXISTS " + gainSubqueryString((Subquery) value);
	}
	//}{ exists >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	//<> contain <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**WHERE key contains value
	 * @param key
	 * @param value
	 * @return	{@link #gainContainString(String, String, Object[], int)}
	 * @throws NotExistException
	 */
	public String gainContainString(String key, String column, Object value, String rawSQL) throws IllegalArgumentException {
		if (rawSQL != null) {
			throw new UnsupportedOperationException("@raw:value 中 " + key + " 不合法！@raw 不支持 key<> 这种功能符 ！" +
					"只支持 key, key!, key<, key{} 等比较运算 和 @column, @having ！");
		}

		Logic logic = new Logic(column);
		column = logic.getKey();
		Log.i(TAG, "getContainString column = " + column);

		return gainContainString(key, column, newJSONArray(value).toArray(), logic.getType());
	}
	/**WHERE key contains childs TODO 支持 key<>: { "path":"$[0].name", "value": 82001 }
	 *  或者  key<$[0].name>:82001 或者  key$[0].name<>:82001 ？ 还是前者好，key 一旦复杂了，
	 *  包含 , ; : / [] 等就容易和 @combine 其它功能等冲突
	 * @param key
	 * @param childs null ? "" : (empty ? no child : contains childs)
	 * @param type |, &, !
	 * @return LOGIC [  ( key LIKE '[" + childs[i] + "]'  OR  key LIKE '[" + childs[i] + ", %'
	 *   OR  key LIKE '%, " + childs[i] + ", %'  OR  key LIKE '%, " + childs[i] + "]' )  ]
	 * @throws IllegalArgumentException
	 */
	public String gainContainString(String key, String column, Object[] childs, int type) throws IllegalArgumentException {
		boolean not = Logic.isNot(type);
		String condition = "";
		if (childs != null) {
			for (int i = 0; i < childs.length; i++) {
				Object c = childs[i];
				if (c instanceof Collection) {
					throw new IllegalArgumentException(key + ":value 中 value 类型不能为 [JSONList, Collection] 中的任何一个 ！");
				}

				Object path = "";
				if (c instanceof Map) {
					path = ((Map<?, ?>) c).get("path");
					if (path != null && path instanceof String == false) {
						throw new IllegalArgumentException(key + ":{ path:path, value:value } 中 path 类型错误，" +
								"只能是 $, $.key1, $[0].key2 等符合 SQL 中 JSON 路径的 String ！");
					}

					c = ((Map<?, ?>) c).get("value");
					if (c instanceof Collection || c instanceof Map) {
						throw new IllegalArgumentException(key + ":{ path:path, value:value } 中 value 类型" +
								"不能为 [JSONMap, JSONList, Collection, Map] 中的任何一个 ！");
					}
				}

				condition += (i <= 0 ? "" : (Logic.isAnd(type) ? AND : OR));
				if (isPSQL()) {
					condition += (gainKey(column) + " @> " + gainValue(key, column, newJSONArray(c)));
					// operator does not exist: jsonb @> character varying  "[" + c + "]");
				}
				else if (isKingBase() && isKingBaseOracle() == false) {
					condition += (gainKey(column) + "::jsonb @> " + gainValue(key, column, newJSONArray(c)) + "::jsonb");
				}
				else if (isOracle() || isDameng() || isKingBaseOracle()) {
					condition += ("json_textcontains(" + gainKey(column) + ", " + (StringUtil.isEmpty(path, true)
							? "'$'" : gainValue(key, column, path)) + ", " + gainValue(key, column, c == null ? null : c.toString()) + ")");
				}
				else if (isPresto() || isTrino()) {
					condition += ("json_array_contains(cast(" + gainKey(column) + " AS VARCHAR), "
							+ gainValue(key, column, c) + (StringUtil.isEmpty(path, true)
							? "" : ", " + gainValue(key, column, path)) + ")");
				}
				else {
					String v = c == null ? "null" : (c instanceof Boolean || c instanceof Number ? c.toString() : "\"" + c + "\"");
					if (isClickHouse()) {
						condition += (condition + "has(JSONExtractArrayRaw(assumeNotNull(" + gainKey(column) + "))"
								+ ", " + gainValue(key, column, v) + (StringUtil.isEmpty(path, true)
								? "" : ", " + gainValue(key, column, path)) + ")");
					}
					else {
						condition += ("json_contains(" + gainKey(column) + ", " + gainValue(key, column, v)
								+ (StringUtil.isEmpty(path, true) ? "" : ", " + gainValue(key, column, path)) + ")");
					}
				}
			}

			if (condition.isEmpty()) {
				condition = gainKey(column) + SQL.isNull(true) + OR + gainLikeString(key, column, "[]"); // key = '[]' 无结果！
			}
			else {
				condition = gainKey(column) + SQL.isNull(false) + AND + "(" + condition + ")";
			}
		}

		if (condition.isEmpty()) {
			return "";
		}
		return gainCondition(not, condition);
	}
	//<> contain >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	//key@:{} Subquery <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	public List<String> getWithAsExprSQLList() {
		return withAsExprSQLList;
	}
	private void clearWithAsExprListIfNeed() {
		// mysql8版本以上,子查询支持with as表达式
		if((this.isMySQL() || this.isKingBaseMySQL()) && this.gainDBVersionNums()[0] >= 8) {
			this.withAsExprSQLList = new ArrayList<>();
		}
	}

	@Override
	public List<Object> getWithAsExprPreparedValueList() {
		return this.withAsExprPreparedValueList;
	}

	@Override
	public AbstractSQLConfig<T, M, L> setWithAsExprPreparedValueList(List<Object> list) {
		this.withAsExprPreparedValueList = list;
		return this;
	}

	/**
	 * 只要 method != RequestMethod.POST 就都支持 with-as表达式
	 * @param cfg
	 * @param subquery
	 * @return
	 * @throws Exception
	 */
	private String withAsExprSubqueryString(SQLConfig<T, M, L> cfg, Subquery<T, M, L> subquery) throws Exception {
		boolean isWithAsEnable = isWithAsEnable();
		List<String> list = isWithAsEnable ? getWithAsExprSQLList() : null;
		if (cfg.getMethod() != RequestMethod.POST && list == null) {
			clearWithAsExprListIfNeed();
		}

		String quote = getQuote();
		String as = gainAs();

		String withAsExpreSql;
		if (list != null) {
			String withQuoteName = quote + subquery.gainKey() + quote;
			list.add(" " + withQuoteName + as + "(" + cfg.gainSQL(isPrepared()) + ") ");
			withAsExpreSql = " SELECT * FROM " + withQuoteName;

			// 预编译参数 FIXME 这里重复添加了，导致子查询都报错参数超过 ? 数量 Parameter index out of range (5 > number of parameters, which is 4)
			List<Object> subPvl = cfg.getPreparedValueList();
			if (subPvl != null && subPvl.isEmpty() == false) {
				List<Object> valueList = getWithAsExprPreparedValueList();
				if (valueList == null) {
					valueList = new ArrayList<>();
				}
				valueList.addAll(subPvl);
				setWithAsExprPreparedValueList(valueList);

				cfg.setPreparedValueList(new ArrayList<>());
			}
		} else {
			withAsExpreSql = cfg.gainSQL(isPrepared());
			// mysql 才存在这个问题, 主表和子表是一张表
			if (isWithAsEnable && (isMySQL() || isKingBaseMySQL()) && StringUtil.equals(getTable(), subquery.gainFrom())) {
				withAsExpreSql = " SELECT * FROM (" + withAsExpreSql + ")" + as + quote + subquery.gainKey() + quote;
			}
		}

		return withAsExpreSql;
	}

	@Override
	public String gainSubqueryString(Subquery<T, M, L>  subquery) throws Exception {
		if (subquery == null) {
			return "";
		}

		String range = subquery.gainRange();
		SQLConfig<T, M, L> cfg = subquery.gainConfig();

		// 子查询  = 主语句 datasource
		if (StringUtil.equals(this.getTable(), subquery.gainFrom()) == false && cfg.hasJoin() == false) {
			cfg.setDatasource(this.getDatasource());
		}
		cfg.setPreparedValueList(new ArrayList<>());
		String withAsExprSql = withAsExprSubqueryString(cfg, subquery);
		String sql = (range  == null || range.isEmpty() ? "" : range) + "(" + withAsExprSql + ") ";

		//// SELECT .. FROM(SELECT ..) ..  WHERE .. 格式需要把子查询中的预编译值提前
		//// 如果外查询 SELECT concat(`name`,?)  这种 SELECT 里也有预编译值，那就不能这样简单反向
		//List<Object> subPvl = cfg.getPreparedValueList();
		//if (subPvl != null && subPvl.isEmpty() == false) {
		//  List<Object> pvl = getPreparedValueList();
		//
		//  if (pvl != null && pvl.isEmpty() == false) {
		//    subPvl.addAll(pvl);
		//  }
		//  setPreparedValueList(subPvl);
		//}

		List<Object> subPvl = cfg.getPreparedValueList();
		if (subPvl != null && subPvl.isEmpty() == false) {
			List<Object> pvl = getPreparedValueList();

			if (pvl == null || pvl.isEmpty()) {
				pvl = subPvl;
			}
			else {
				pvl.addAll(subPvl);
			}

			setPreparedValueList(pvl);
		}

		return sql;
	}

	//key@:{} Subquery >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	/**拼接条件
	 * @param not
	 * @param condition
	 * @return
	 */
	public static String gainCondition(boolean not, String condition) {
		return gainCondition(not, condition, false);
	}
	/**拼接条件
	 * @param not
	 * @param condition
	 * @param addOuterBracket
	 * @return
	 */
	public static String gainCondition(boolean not, String condition, boolean addOuterBracket) {
		String s = not ? NOT + "(" + condition + ")" : condition;
		return addOuterBracket ? "( " + s + " )" : s;
	}


	/**转为JSONArray
	 * @param obj
	 * @return
	 */
	@NotNull
	public static <L extends List<Object>> L newJSONArray(Object obj) {
		L array = JSON.createJSONArray();
		if (obj != null) {
			if (obj instanceof Collection) {
				array.addAll((Collection<?>) obj);
			} else {
				array.add(obj);
			}
		}
		return array;
	}

	//WHERE >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//SET <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**获取SET
	 * @return
	 * @throws Exception
	 */
	public String gainSetString() throws Exception {
		return gainSetString(getMethod(), getContent(), ! isTest());
	}
	//CS304 Issue link: https://github.com/APIJSON/APIJSON/issues/48
	/**获取SET
	 * @param method -the method used
	 * @param content -the content map
	 * @return
	 * @throws Exception
	 * <p>use entrySet+getValue() to replace keySet+get() to enhance efficiency</p>
	 */
	public String gainSetString(RequestMethod method, Map<String, Object> content, boolean verifyName) throws Exception {
		Set<String> set = content == null ? null : content.keySet();
		String setString = "";

		if (set != null && set.size() > 0) {
			boolean isFirst = true;
			int keyType;// 0 - =; 1 - +, 2 - -
			Object value;

			String idKey = getIdKey();
			for (Entry<String,Object> entry : content.entrySet()) {
				String key = entry.getKey();
				//避免筛选到全部	value = key == null ? null : content.get(key);
				if (key == null || idKey.equals(key)) {
					continue;
				}

				if (key.endsWith("+")) {
					keyType = 1;
				} else if (key.endsWith("-")) {
					keyType = 2;
				} else {
					keyType = 0; //注意重置类型，不然不该加减的字段会跟着加减
				}
				value = entry.getValue();
				String column = gainRealKey(method, key, false, true, verifyName);

				setString += (isFirst ? "" : ", ") + (gainKey(column) + " = "
						+ (keyType == 1 ? gainAddString(key, column, value) : (keyType == 2
						? gainRemoveString(key, column, value) : gainValue(key, column, value)) )
				);

				isFirst = false;
			}
		}

		if (setString.isEmpty()) {
			throw new IllegalArgumentException("PUT 请求必须在Table内设置要修改的 key:value ！");
		}
		return (isClickHouse() ? " " : " SET ") + setString;
	}

	/**SET key = concat(key, 'value')
	 * @param key
	 * @param value
	 * @return concat(key, 'value')
	 * @throws IllegalArgumentException
	 */
	public String gainAddString(String key, String column, Object value) throws IllegalArgumentException {
		if (value instanceof Number) {
			return gainKey(column) + " + " + value;
		}
		if (value instanceof String) {
			return SQL.concat(gainKey(column), (String) gainValue(key, column, value));
		}
		throw new IllegalArgumentException(key + ":value 中 value 类型错误，必须是 Number,String,Array 中的任何一种！");
	}
	/**SET key = replace(key, 'value', '')
	 * @param key
	 * @param value
	 * @return REPLACE (key, 'value', '')
	 * @throws IllegalArgumentException
	 */
	public String gainRemoveString(String key, String column, Object value) throws IllegalArgumentException {
		if (value instanceof Number) {
			return gainKey(column) + " - " + value;
		}
		if (value instanceof String) {
			return SQL.replace(gainKey(column), (String) gainValue(key, column, value), "''");
			// " replace(" + column + ", '" + value + "', '') ";
		}
		throw new IllegalArgumentException(key + ":value 中 value 类型错误，必须是 Number,String,Array 中的任何一种！");
	}
	//SET >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	@Override
	public boolean isFakeDelete() {
		return false;
	}

	@Override
	public Map<String, Object> onFakeDelete(Map<String, Object> map) {
		return map;
	}

	/**
	 * @return
	 * @throws Exception
	 */
	@Override
	public String gainSQL(boolean prepared) throws Exception {
		boolean isPrepared = isPrepared();
		if (isPrepared == prepared) {
			return gainSQL(this);
		}

		String sql = gainSQL(this.setPrepared(prepared));
		setPrepared(isPrepared);
		return sql;
	}
	/**
	 * @param config
	 * @return
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> String gainSQL(AbstractSQLConfig<T, M, L> config) throws Exception {
		if (config == null) {
			Log.i(TAG, "getSQL  config == null >> return null;");
			return null;
		}

		// TODO procedure 改为 List<Procedure>  procedureList; behind : true; function: callFunction(); String key; ...
		// for (...) { Call procedure1();\n SQL \n; Call procedure2(); ... }
		// 貌似不需要，因为 ObjectParser<T, M, L> 里就已经处理的顺序等，只是这里要解决下 Schema 问题。
		String table = config.getTable();
		String procedure = config.getProcedure();
		if (StringUtil.isNotEmpty(procedure, true)) {
			int ind = procedure.indexOf(".");
			boolean hasPrefix = ind >= 0 && ind < procedure.indexOf("(");
			String sch = hasPrefix ? AbstractFunctionParser.extractSchema(
					procedure.substring(0, ind), table
			) : config.gainSQLSchema();

			String q = config.getQuote();
			return "CALL " + q + sch + q + "." + (hasPrefix ? procedure.substring(ind + 1) : procedure);
		}

		String tablePath = config.gainTablePath();
		if (StringUtil.isEmpty(tablePath, true)) {
			Log.i(TAG, "getSQL  StringUtil.isEmpty(tablePath, true) >> return null;");
			return null;
		}

		// 解决重复添加导致报错：Parameter index out of range (6 > number of parameters, which is 5)
		config.setPreparedValueList(new ArrayList<>());
		RequestMethod method = config.getMethod();
		if (method == null) {
			method = GET;
		}

		String cSql;
		switch (method) {
			case POST:
				return config.gainUpsertSQL();
			case PUT:
				return config.gainUpdateSQL();
			case DELETE:
				return config.gainDeleteSQL();
			default:
				return config.gainSelectSQL();
		}
	}

	public static String gainInsertSQL(String tablePath, String columns, String values) {
		return "INSERT INTO " + tablePath + columns + " VALUES" + values;
	}

	public String gainUpsertSQL() throws Exception {
		String table = getTable();
		Object obj = UPSERT_TABLE_MAP.get(table);
		String tablePath = gainTablePath();
		if (obj == null) {
			return gainInsertSQL(tablePath, gainColumnString(), gainValuesString());
		}

		String database = gainSQLDatabase();
		String q = getQuote();

		String key = "";
		String setStr = "";
		if (obj instanceof String) { // MySQL/PostgreSQL 简单写 version = version + 1
			setStr = (String) obj;
		} else if (obj instanceof Map<?, ?>) {
			// PostgreSQL 写 { "": "excluded.version = Method.version" }
			// 或 自定义 CONFLICT key 写 { "package,method": "excluded.version = Method.version + 1" }
			// 或多数据库写 { "Oracle": { "method,tag": "excluded.version = Request.version + 1" }, "DB2": {...} } 等

			Map<String, Object> setObj = (Map<String, Object>) obj;
			Object dbSetObj = setObj.get(database);
			if (dbSetObj instanceof String) {
				setStr = (String) dbSetObj;
			} else {
				boolean isDBSet = dbSetObj instanceof Map<?, ?>;
				if (isDBSet) {
					setObj = (Map<String, Object>) dbSetObj;
				} else if (dbSetObj != null) {
					throw new IllegalArgumentException("UPSERT_TABLE_MAP 中 " + table + ": {" + database + ": value} 中 value 不合法，必须是 String 或 Map<String, Object> 类型！");
				}

				Set<Entry<String, Object>> set = setObj.entrySet();
				boolean first = true;
				for (Entry<String, Object> entry : set) {
					String k = entry == null ? null : entry.getKey();
					Object v = k == null ? null : entry.getValue();
					if (v == null || (isDBSet == false && DATABASE_LIST.contains(k))) {
						continue;
					}

					if (StringUtil.isEmpty(k)) {
						k = q + getIdKey() + q;
					} else if (!(k.contains(",") || (k.startsWith("`") && k.endsWith("`")) || (k.startsWith("\"") && k.endsWith("\"")))) {
						k = q + k + q;
					}

					key += (first ? "" : ", ") + k;
					setStr += (first ? "" : ", ") + v; // 自定义表达式不好自动处理，后端配置正确即可 gainValue(k, k, v);
					first = false;
				}
			}
		} else {
			throw new IllegalArgumentException("UPSERT_TABLE_MAP 中 " + table + ": value 中 value 不合法，必须是 String 或 Map<String, Object> 类型！");
		}

		if (StringUtil.isEmpty(setStr)) {
			return gainInsertSQL(tablePath, gainColumnString(), gainValuesString());
		}

		String columns = gainColumnString();

		if (isPostgreSQL(database) || isSQLite(database) || isCockroachDB(database) || isTimescaleDB(database)) {
			if (StringUtil.isEmpty(key)) {
				key = q + getIdKey() + q;
			}
			return gainInsertSQL(tablePath, columns, gainValuesString()) + " ON CONFLICT(" + key + ") DO UPDATE SET " + setStr;
		}

		boolean isDB2 = isDb2(database);
		boolean isOracle = isDB2 == false && isOracle(database);
		boolean isDameng = isDB2 == false && isOracle == false && isDameng(database);
		if (isDB2 || isOracle || isDameng || isSQLServer(database)) {
			String alias = gainSQLAlias();
			String idKey = getIdKey();

			String vs;
			String cvs = "";

			vs = "";
			List<String> cols = getColumn();
			List<List<Object>> valss = cols == null || cols.isEmpty() ? null : getValues();
			if (valss == null || valss.size() != 1) {
				throw new IllegalArgumentException("POST " + table + " 不合法，UPSERT 模式下 VALUES 必须是 1 条记录！");
			}

			List<Object> vals = valss.get(0);
			boolean first = true;
			for (int i = 0; i < cols.size(); i++) {
				String c = cols.get(i);
				if (c == null) { // FIXME 带上 id 是否有问题？ || idKey.equals(c)) {
					continue;
				}

				Object v = vals.get(i);
				String s = gainValue(c, c, v) + (isDB2 ? "" : " " + q + c + q);

				vs += (first ? "" : ", ") + s;
				cvs += (first ? "" : ", ") + "excluded." + q + c + q;
				first = false;
			}

			if (StringUtil.isEmpty(vs)) {
				throw new IllegalArgumentException("POST " + table + " 不合法，UPSERT 模式下 VALUES 必须是 1 条记录！");
			}

			vs = isDB2 ? "VALUES(" + vs + ")" : "SELECT " + vs + (isOracle || isDameng ? " FROM dual" : "");

			idKey = q + idKey + q;
			return "MERGE INTO " + tablePath + " " + alias + " USING (\n  " + vs + "\n) excluded"
					+ (isDB2 ? columns : "") + " ON " + alias + "." + idKey + " = excluded." + idKey + " \nWHEN MATCHED THEN \n  UPDATE SET "
					+ setStr + " \nWHEN NOT MATCHED THEN \n  INSERT" + columns + " VALUES(" + cvs + ")";
		}

		return gainInsertSQL(tablePath, columns, gainValuesString()) + " ON DUPLICATE KEY UPDATE " + setStr;
	}

	public String gainUpdateSQL() throws Exception {
		String tablePath = gainTablePath();
		if (isClickHouse()){
			return  "ALTER TABLE " + tablePath + " UPDATE" + gainSetString() + gainWhereString(true);
		}

		String cSql = "UPDATE " + tablePath + gainSetString() + gainWhereString(true)
				+ (isMySQL() || KingbaseSQLDialect.from(gainSQLDatabase()).supportsDmlLimit() ? gainLimitString() : "");
		cSql = buildWithAsExprSql(this, cSql);
		return cSql;
	}

	public String gainDeleteSQL() throws Exception {
		String tablePath = gainTablePath();
		if(isClickHouse()){
			return  "ALTER TABLE " +  tablePath + " DELETE" + gainWhereString(true);
		}

		String cSql =  "DELETE FROM " + tablePath + gainWhereString(true)
				+ (isMySQL() || KingbaseSQLDialect.from(gainSQLDatabase()).supportsDmlLimit() ? gainLimitString() : "");  // PostgreSQL 不允许 LIMIT
		cSql = buildWithAsExprSql(this, cSql);
		return cSql;
	}

	public String gainSelectSQL() throws Exception {
		KingbaseSQLDialect kingbaseDialect = KingbaseSQLDialect.from(gainSQLDatabase());
		String kingbaseExplain = kingbaseDialect.getExplainPrefix();
		String explain = isExplain() ? (kingbaseExplain != null ? kingbaseExplain
				: (isSQLServer() ? "SET STATISTICS PROFILE ON  "
				: (isOracle() || isDameng() ? "EXPLAIN PLAN FOR " : "EXPLAIN "))) : "";
		if (isTest() && RequestMethod.isGetMethod(getMethod(), true)) {  // FIXME 为啥是 code 而不是 count ？
			String q = getQuote();  // 生成 SELECT  (  (24 >=0 AND 24 <3)  )  AS `code` LIMIT 1 OFFSET 0
			return explain + "SELECT " + gainWhereString(false)
					+ gainAs() + q + JSONResponse.KEY_COUNT + q + gainLimitString();
		}

		setPreparedValueList(new ArrayList<>());

		String tablePath = gainTablePath();
		String column = gainColumnString();
		if (isOracle() || isDameng() || isKingBaseOracle()) {
			//When config's database is oracle,Using subquery since Oracle12 below does not support OFFSET FETCH paging syntax.
			//针对oracle分组后条数的统计
			if (StringUtil.isNotEmpty(getGroup(),true) && RequestMethod.isHeadMethod(getMethod(), true)){
				return explain + "SELECT count(*) FROM (SELECT " + (getCache() == JSONMap.CACHE_RAM
						? "SQL_NO_CACHE " : "") + column + " FROM " + gainConditionString(tablePath, this) + ") " + gainLimitString();
			}

			String sql = "SELECT " + (getCache() == JSONMap.CACHE_RAM
					? "SQL_NO_CACHE " : "") + column + " FROM " + gainConditionString(tablePath, this);
			return explain + gainOraclePageSQL(sql);
		}

		String cSql = "SELECT " + (getCache() == JSONMap.CACHE_RAM ? "SQL_NO_CACHE " : "")
				+ column + " FROM " + gainConditionString(tablePath, this) + gainLimitString();
		cSql = buildWithAsExprSql(this, cSql);
		if(isElasticsearch()) { // elasticSearch 不支持 explain
			return cSql;
		}

		return explain + cSql;
	}

	private static <T, M extends Map<String, Object>, L extends List<Object>> String buildWithAsExprSql(@NotNull AbstractSQLConfig<T, M, L> config, String cSql) throws Exception {
		if (config.isWithAsEnable() == false) {
			return cSql;
		}

		List<String> list = config.getWithAsExprSQLList();
		int size = list == null ? 0 : list.size();
		if (size > 0) {
			String withAsExpreSql = "WITH ";
			for (int i = 0; i < size; i++) {
				withAsExpreSql += (i <= 0 ? "" : ",") + list.get(i) + "\n";
			}
			cSql = withAsExpreSql + cSql;
			config.clearWithAsExprListIfNeed();
		}
		return cSql;
	}

	@Override
	public boolean isWithAsEnable() {
		return ENABLE_WITH_AS && ((isMySQL() || isKingBaseMySQL()) == false || gainDBVersionNums()[0] >= 8);
	}

	/**Oracle的分页获取
	 * @param sql
	 * @return
	 */
	protected String gainOraclePageSQL(String sql) {
		int count = getCount();
		if (count <= 0 || RequestMethod.isHeadMethod(getMethod(), true)) { // TODO HEAD 真的不需要 LIMIT ？
			return sql;
		}
		int offset = getOffset(getPage(), count);
		String quote = getQuote();
		String alias = quote + gainSQLAlias() + quote;
		return "SELECT * FROM (SELECT " + alias + ".*, ROWNUM "+ quote + "RN" + quote +" FROM (" + sql + ") " + alias
				+ "  WHERE ROWNUM <= " + (offset + count) + ") WHERE "+ quote + "RN" + quote +" > " + offset;
	}

	/**获取条件SQL字符串
	 * @param table
	 * @param config
	 * @return
	 * @throws Exception
	 */
	private static <T, M extends Map<String, Object>, L extends List<Object>> String gainConditionString(
			String table, AbstractSQLConfig<T, M, L> config) throws Exception {
		Subquery<T, M, L> from = config.getFrom();
		if (from != null) {
			table = config.gainSubqueryString(from) + config.gainAs() + config.gainSQLAliasWithQuote() + " ";
		}

		String join = config.gainJoinString();

		String where = config.gainWhereString(true);

		//根据方法不同，聚合语句不同。GROUP  BY 和 HAVING 可以加在 HEAD 上, HAVING 可以加在 PUT, DELETE 上，GET 全加，POST 全都不加
		RequestMethod method = config.getMethod();
		String aggregation;
		if (RequestMethod.isGetMethod(method, true)) {
			aggregation = config.gainGroupString(true) + config.gainHavingString(true)
					+ config.gainSampleString(true) + config.gainLatestString(true)
					+ config.gainPartitionString(true) + config.gainFillString(true)
					+ config.gainOrderString(true);
		}
		else if (RequestMethod.isHeadMethod(method, true)) {
			// TODO 加参数 isPagination 判断是 GET 内分页 query:2 查总数，不用加这些条件
			aggregation = config.gainGroupString(true) + config.gainHavingString(true)
					+ config.gainSampleString(true) + config.gainLatestString(true)
					+ config.gainPartitionString(true) + config.gainFillString(true);
		}
		else if (method == PUT || method == DELETE) {
			aggregation = config.gainHavingString(true) ;
		}
		else {
			aggregation = "";
		}

		String condition = table + join + where + aggregation;
		; //+ config.getLimitString();

		//no need to optimize
		//		if (config.getPage() <= 0 || ID.equals(column.trim())) {
		return condition;  // config.isOracle() ? condition : condition + config.getLimitString();
		//		}
		//
		//
		//		//order: id+ -> id >= idOfStartIndex; id- -> id <= idOfStartIndex <<<<<<<<<<<<<<<<<<<
		//		String order = StringUtil.getNoBlankString(config.getOrder());
		//		List<String> orderList = order.isEmpty() ? null : Arrays.asList(StringUtil.split(order));
		//
		//		int type = 0;
		//		if (BaseModel.isEmpty(orderList) || BaseModel.isContain(orderList, ID+"+")) {
		//			type = 1;
		//		}
		//		else if (BaseModel.isContain(orderList, ID+"-")) {
		//			type = 2;
		//		}
		//
		//		if (type > 0) {
		//			return condition.replace("WHERE",
		//					"WHERE id " + (type == 1 ? ">=" : "<=") + " (SELECT id FROM " + table
		//					+ where + " ORDER BY id " + (type == 1 ? "ASC" : "DESC") + " LIMIT " + config.getOffset() + ", 1) AND"
		//					)
		//					+ " LIMIT " + config.getCount(); //子查询起始id不一定准确，只能作为最小可能！ ;//
		//		}
		//		//order: id+ -> id >= idOfStartIndex; id- -> id <= idOfStartIndex >>>>>>>>>>>>>>>>>>
		//
		//
		//		//结果错误！SELECT * FROM User AS t0 INNER JOIN
		//      (SELECT id FROM User ORDER BY date ASC LIMIT 20, 10) AS t1 ON t0.id = t1.id
		//		//common case, inner join
		//		condition += config.getLimitString();
		//		return table + " AS t0 INNER JOIN (SELECT id FROM " + condition + ") AS t1 ON t0.id = t1.id";
	}


	private boolean keyPrefix;
	@Override
	public boolean isKeyPrefix() {
		return keyPrefix;
	}
	@Override
	public AbstractSQLConfig<T, M, L> setKeyPrefix(boolean keyPrefix) {
		this.keyPrefix = keyPrefix;
		return this;
	}


	public String gainJoinString() throws Exception {
		String joinOns = "";

		if (joinList != null) {
			String quote = getQuote();
			List<Object> pvl = getPreparedValueList();  // new ArrayList<>();
			//boolean changed = false;

			//  主表不用别名			String ta;
			for (Join j : joinList) {
				onGainJoinString(j);

				if (j.isAppJoin()) { // APP JOIN，只是作为一个标记，执行完主表的查询后自动执行副表的查询 User.id IN($commentIdList)
					continue;
				}
				String type = j.getJoinType();

				//LEFT JOIN sys.apijson_user AS User ON User.id = Moment.userId， 都是用 = ，通过relateType处理缓存
				// <"INNER JOIN User ON User.id = Moment.userId", UserConfig>  TODO  AS 放 getSQLTable 内
				SQLConfig<T, M, L> jc = j.getJoinConfig();
				jc.setPrepared(isPrepared());
				// 将关联表所属数据源配置为主表数据源
				jc.setDatasource(this.getDatasource());
				String jt = jc.gainSQLAlias();
				List<On> onList = j.getOnList();

				//如果要强制小写，则可在子类重写这个方法再 toLowerCase
				//				if (DATABASE_POSTGRESQL.equals(getDatabase())) {
				//					jt = jt.toLowerCase();
				//					tn = tn.toLowerCase();
				//				}

				String sql;

				switch (type) {
				//前面已跳过				case "@": // APP JOIN
				//					continue;

				case "*": // CROSS JOIN
					onGainCrossJoinString(j);
				case "<": // LEFT JOIN
				case ">": // RIGHT JOIN
					jc.setMain(true).setKeyPrefix(false);
					sql = ( "<".equals(type) ? " LEFT" : (">".equals(type) ? " RIGHT" : " CROSS") )
							+ " JOIN ( " + jc.gainSQL(isPrepared()) + " ) " + gainAs() + quote + jt + quote;
					sql = concatJoinOn(sql, quote, j, jt, onList);

					jc.setMain(false).setKeyPrefix(true);

					pvl.addAll(jc.getPreparedValueList());
					//changed = true;
					break;

				case "&": // INNER JOIN: A & B
				case "":  // FULL JOIN: A | B
				case "|": // FULL JOIN: A | B
				case "!": // OUTER JOIN: ! (A | B)
				case "^": // SIDE JOIN: ! (A & B)
				case "(": // ANTI JOIN: A & ! B
				case ")": // FOREIGN JOIN: B & ! A
					sql = " INNER JOIN " + jc.gainTablePath();
					sql = concatJoinOn(sql, quote, j, jt, onList);
					break;
				case "~": // ASOF JOIN: B ~= A
					sql = " ASOF JOIN " + jc.gainTablePath();
					sql = concatJoinOn(sql, quote, j, jt, onList);
					break;
				default:
					String k = jc.gainTableKey();
					throw new UnsupportedOperationException(
							"join:value 中 value 里的 " + k + "/" + j.getPath()
							+ "错误！不支持 " + k + " 等 [ @ APP, < LEFT, > RIGHT, * CROSS"
							+ ", & INNER, | FULL, ! OUTER, ^ SIDE, ( ANTI, ) FOREIGN, ~ ASOF ] 之外的 JOIN 类型 !"
							);
				}

				SQLConfig<T, M, L> oc = j.getOnConfig();
				String ow = null;
				if (oc != null) {
					oc.setPrepared(isPrepared());
					oc.setPreparedValueList(new ArrayList<>());
					oc.setMain(false).setKeyPrefix(true);
					ow = oc.gainWhereString(false);

					pvl.addAll(oc.getPreparedValueList());
					//changed = true;
				}

				joinOns += "  \n  " + sql + (StringUtil.isEmpty(ow, true) ? "" : " AND ( " + ow + " ) ");
			}


			//if (changed) {
			//  List<Object> opvl = getPreparedValueList();
			//  if (opvl != null && opvl.isEmpty() == false) {
			//    pvl.addAll(opvl);
			//  }
				setPreparedValueList(pvl);
			//}

		}

		return StringUtil.isEmpty(joinOns, true) ? "" : joinOns + "  \n";
	}

	protected String concatJoinOn(@NotNull String sql, @NotNull String quote, @NotNull Join<T, M, L> join, @NotNull String jt, List<On> onList) {
		if (onList != null) {
			SQLConfig<T, M, L> jc = join.getJoinConfig();
			Map<String, String> castMap = jc == null ? null : jc.getCast();

			boolean first = true;
			for (On on : onList) {
				Logic logic = on.getLogic();
				boolean isNot = logic == null ? false : logic.isNot();
				if (isNot) {
					onJoinNotRelation(sql, quote, join, jt, onList, on);
				}

				String lk = quote + jt + quote + "." + quote + on.getKey() + quote;
				Object ct = castMap == null ? null : castMap.get(on.getOriginKey());
				if (StringUtil.isNotEmpty(ct, false)) {
					lk = "cast(" + lk + " AS " + ct + ")"; // 解决 JOIN ON 不支持 @cast 问题，CAST(expression AS TYPE) 中 AS 不能省略
				}

				String rt = on.getRelateType();

				String rk = quote + SQLConfig.gainSQLAlias(on.getTargetTable(), on.getTargetAlias()) + quote + "." + quote + on.getTargetKey() + quote;

				if (StringUtil.isEmpty(rt, false)) {
					sql += (first ? ON : AND) + lk + (isNot ? " != " : " = ") + rk;
				}
				else {
					onJoinComplexRelation(sql, quote, join, jt, onList, on);

					if (">=".equals(rt) || "<=".equals(rt) || ">".equals(rt) || "<".equals(rt)) {
						if (isNot) {
							throw new IllegalArgumentException("join:value 中 value 里的 " + jt + "/" + join.getPath()
							+ " 中 JOIN ON 条件关联逻辑符 " + rt + " 不合法！ >, <, >=, <= 不支持与或非逻辑符 & | ! ！");
						}

						sql += (first ? ON : AND) + lk + " " + rt + " " + rk;
					}
					else if (rt.endsWith("$")) {
						String t = rt.substring(0, rt.length() - 1);
						char r = t.isEmpty() ? 0 : t.charAt(t.length() - 1);

						char l;
						if (r == '%' || r == '_' || r == '?') {
							t = t.substring(0, t.length() - 1);

							if (t.isEmpty()) {
								if (r == '?') {
									throw new IllegalArgumentException(on.getOriginKey() + ":value 中字符 " + on.getOriginKey()
											+ " 不合法！key$:value 中不允许只有单独的 '?'，必须和 '%', '_' 之一配合使用 ！");
								}

								l = r;
							}
							else {
								l = t.charAt(t.length() - 1);
								if (l == '%' || l == '_' || l == '?') {
									if (l == r) {
										throw new IllegalArgumentException(on.getOriginKey()
												+ ":value 中字符 " + t + " 不合法！key$:value 中不允许 key 中有连续相同的占位符！");
									}

									t = t.substring(0, t.length() - 1);
								}
								else if (l > 0 && StringUtil.isName(String.valueOf(l))) {
									l = r;
								}
							}

							if (l == '?') {
								l = 0;
							}
							if (r == '?') {
								r = 0;
							}
						}
						else {
							l = r = 0;
						}

						if (l <= 0 && r <= 0) {
							sql += (first ? ON : AND) + lk + (isNot ? NOT : "") + " LIKE " + rk;
						}
						else {
							sql += (first ? ON : AND) + lk + (isNot ? NOT : "")
									+ (l <= 0 ? " LIKE concat(" : " LIKE concat('" + l + "', ") + rk + (r <= 0 ? ")" : ", '" + r + "')");
						}
					}
					else if (rt.endsWith("~")) {
						boolean ignoreCase = "*~".equals(rt);
						if (isPSQL()) {
							sql += (first ? ON : AND) + lk + (isNot ? NOT : "") + " ~" + (ignoreCase ? "* " : " ") + rk;
						}
						else if (isKingBaseSQLServer()) {
							sql += (first ? ON : AND) + lk + (isNot ? NOT : "") + " ~" + (ignoreCase ? "* " : " ") + rk;
						}
						else if (isOracle() || isDameng() || DATABASE_KINGBASE.equals(gainSQLDatabase())
								|| isKingBaseOracle() || isKingBaseMySQL()) {
							sql += (first ? ON : AND) + "regexp_like(" +  lk + ", " + rk + (ignoreCase ? ", 'i'" : ", 'c'") + ")";
						}
						else if (isPresto() || isTrino()) {
							sql += (first ? ON : AND) + "regexp_like(" + (ignoreCase ? "lower(" : "") + lk + (ignoreCase ? ")" : "")
									+ ", " + (ignoreCase ? "lower(" : "") + rk + (ignoreCase ? ")" : "") + ")";
						}
						else if (isClickHouse()) {
							sql += (first ? ON : AND) + "match(" + (ignoreCase ? "lower(" : "") + lk + (ignoreCase ? ")" : "")
									+ ", " + (ignoreCase ? "lower(" : "") + rk + (ignoreCase ? ")" : "") + ")";
						}
						else if (isElasticsearch()) {
							sql += (first ? ON : AND) + lk + (isNot ? NOT : "") + " RLIKE " + rk;
						}
						else if (isHive()) {
							sql += (first ? ON : AND) + (ignoreCase ? "lower(" : "") + lk + (ignoreCase ? ")" : "")
									+ " REGEXP " + (ignoreCase ? "lower(" : "") + rk + (ignoreCase ? ")" : "");
						}
						else {
							sql += (first ? ON : AND) + lk + (isNot ? NOT : "") + " REGEXP " + (ignoreCase ? "" : "BINARY ") + rk;
						}
					}
					else if ("{}".equals(rt) || "<>".equals(rt)) {
						String tt = on.getTargetTable();
						String ta = on.getTargetAlias();

						Map<String, String> cast = null;
						if (tt.equals(getTable()) && Objects.equals(ta, getAlias())) {
							cast = getCast();
						}
						else {
							boolean find = false;
							for (Join<T, M, L> jn : joinList) {
								if (tt.equals(jn.getTable()) && Objects.equals(ta, jn.getAlias())) {
									cast = getCast();
									find = true;
									break;
								}
							}

							if (find == false) {
								throw new IllegalArgumentException("join:value 中 value 里的 " + jt + "/" + join.getPath()
								+ " 中 JOIN ON 条件中找不到对应的 " + rt + " 不合法！只支持 =, {}, <> 这几种！");
							}
						}

						boolean isBoolOrNum = SQL.isBooleanOrNumber(cast == null ? null : cast.get(on.getTargetKey()));

						boolean isIn = "{}".equals(rt);
						String arrKeyPath = isIn ? rk : lk;
						String itemKeyPath = isIn ? lk : rk;

						if (isPSQL()) {  //operator does not exist: jsonb @> character varying  "[" + c + "]");
							sql += (first ? ON : AND) + (isNot ? "( " : "") + gainCondition(isNot, arrKeyPath
									+ " IS NOT NULL AND " + arrKeyPath + " @> " + itemKeyPath) + (isNot ? ") " : "");
						}
						else if (isKingBase() && isKingBaseOracle() == false) {
							sql += (first ? ON : AND) + (isNot ? "( " : "") + gainCondition(isNot, arrKeyPath
									+ " IS NOT NULL AND " + arrKeyPath + "::jsonb @> " + itemKeyPath + "::jsonb") + (isNot ? ") " : "");
						}
						else if (isOracle() || isDameng() || isKingBaseOracle()) {
							sql += (first ? ON : AND) + (isNot ? "( " : "") + gainCondition(isNot, arrKeyPath
									+ " IS NOT NULL AND json_textcontains(" + arrKeyPath
									+ ", '$', " + itemKeyPath + ")") + (isNot ? ") " : "");
						}
						else if (isPresto() || isTrino()) {
							sql += (first ? ON : AND) + (isNot ? "( " : "") + gainCondition(isNot, arrKeyPath
									+ " IS NOT NULL AND json_array_contains(cast(" + arrKeyPath
									+ " AS VARCHAR), " + itemKeyPath + ")") + (isNot ? ") " : "");
						}
						else if (isClickHouse()) {
							sql += (first ? ON : AND) + (isNot ? "( " : "") + gainCondition(isNot, arrKeyPath
									+ " IS NOT NULL AND has(JSONExtractArrayRaw(assumeNotNull(" + arrKeyPath + "))"
									+ ", " + itemKeyPath + ")") + (isNot ? ") " : "");
						}
						else {
							sql += (first ? ON : AND) + (isNot ? "( " : "") + gainCondition(isNot, arrKeyPath
									+ " IS NOT NULL AND json_contains(" + arrKeyPath
									+ (isBoolOrNum ? ", cast(" + itemKeyPath + " AS CHAR), '$')"
											: ", concat('\"', " + itemKeyPath + ", '\"'), '$')"
									)
							) + (isNot ? ") " : "");
						}
					}
					else {
						throw new IllegalArgumentException("join:value 中 value 里的 " + jt + "/" + join.getPath()
						+ " 中 JOIN ON 条件关联类型 " + rt + " 不合法！只支持 =, >, <, >=, <=, !=, $, ~, {}, <> 这几种！");
					}
				}

				first = false;
			}
		}

		return sql;
	}

	protected void onJoinNotRelation(String sql, String quote, Join<T, M, L> join, String table, List<On> onList, On on) {
		throw new UnsupportedOperationException("JOIN 已禁用 '!' 非逻辑连接符 ！性能很差、需求极少，如要取消禁用可在后端重写相关方法！");
	}
	protected void onJoinComplexRelation(String sql, String quote, Join<T, M, L> join, String table, List<On> onList, On on) {
		throw new UnsupportedOperationException("JOIN 已禁用 $, ~, {}, <>, >, <, >=, <= 等复杂关联 ！" +
				"性能很差、需求极少，默认只允许 = 等价关联，如要取消禁用可在后端重写相关方法！");
	}

	protected void onGainJoinString(Join<T, M, L> join) throws UnsupportedOperationException {
	}
	protected void onGainCrossJoinString(Join<T, M, L> join) throws UnsupportedOperationException {
		throw new UnsupportedOperationException("已禁用 * CROSS JOIN ！性能很差、需求极少，如要取消禁用可在后端重写相关方法！");
	}

	/**新建SQL配置
	 * @param table
	 * @param request
	 * @param joinList
	 * @param isProcedure
	 * @param callback
	 * @return
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> SQLConfig<T, M, L> newSQLConfig(
			RequestMethod method, String table, String alias
			, M request, List<Join<T, M, L>> joinList, boolean isProcedure, Callback<T, M, L> callback) throws Exception {
		if (request == null) { // User:{} 这种空内容在查询时也有效
			throw new NullPointerException(TAG + ": newSQLConfig<T, M, L>  request == null!");
		}

		Boolean explain = getBoolean(request, KEY_EXPLAIN);
		if (explain != null && explain && Log.DEBUG == false) { // 不在 config.setExplain 抛异常，一方面处理更早性能更好，另一方面为了内部调用可以绕过这个限制
			throw new UnsupportedOperationException("非DEBUG模式, 不允许传 " + KEY_EXPLAIN + " ！");
		}

		String database = getString(request, KEY_DATABASE);
		if (StringUtil.isNotEmpty(database, false) && DATABASE_LIST.contains(database) == false) {
			throw new UnsupportedDataTypeException("@database:value 中 value 错误，只能是 ["
					+ StringUtil.get(DATABASE_LIST.toArray()) + "] 中的一种！");
		}

		String datasource = getString(request, KEY_DATASOURCE);
		String namespace = getString(request, KEY_NAMESPACE);
		String catalog = getString(request, KEY_CATALOG);
		String schema = getString(request, KEY_SCHEMA);

		SQLConfig<T, M, L> config = (SQLConfig<T, M, L>) callback.getSQLConfig(method, database, datasource, namespace, catalog, schema, table);
		config.setAlias(alias);

		config.setDatabase(database); // 不删，后面表对象还要用的，必须放在 parseJoin 前
		config.setDatasource(datasource); // 不删，后面表对象还要用的
		config.setNamespace(namespace); // 不删，后面表对象还要用的
		config.setCatalog(catalog); // 不删，后面表对象还要用的
		config.setSchema(schema); // 不删，后面表对象还要用的

		if (isProcedure) {
			return config;
		}

		config = parseJoin(method, config, joinList, callback); // 放后面会导致主表是空对象时 joinList 未解析

		if (request.isEmpty()) { // User:{} 这种空内容在查询时也有效
			return config; // request.remove(key); 前都可以直接return，之后必须保证 put 回去
		}

		// 对 id, id{}, userId, userId{} 处理，这些只要不为 null 就一定会作为 AND 条件 <<<<<<<<<<<<<<<<<<<<<<<<<

		String idKey = callback.getIdKey(database, datasource, namespace, catalog, schema, table);
		String idInKey = idKey + "{}";
		String userIdKey = callback.getUserIdKey(database, datasource, namespace, catalog, schema, table);
		String userIdInKey = userIdKey + "{}";

		Object idIn = request.get(idInKey); // 可能是 id{}:">0"
		if (idIn instanceof Collection) { // 排除掉 0, 负数, 空字符串 等无效 id 值
			Collection<?> ids = (Collection<?>) idIn;
			List<Object> newIdIn = new ArrayList<>();
			for (Object d : ids) { // 不用 idIn.contains(id) 因为 idIn 里存到很可能是 Integer，id 又是 Long！
				if ((d instanceof Number && ((Number) d).longValue() > 0) || (d instanceof String && StringUtil.isNotEmpty(d, true))) {
					if (newIdIn.contains(d) == false) {
						newIdIn.add(d);
					}
				}
			}
			if (newIdIn.isEmpty()) {
				throw new NotExistException(TAG + ": newSQLConfig<T, M, L> idIn instanceof List >> 去掉无效 id 后 newIdIn.isEmpty()");
			}
			idIn = newIdIn;

			if (method == DELETE || method == PUT) {
				config.setCount(newIdIn.size());
			}
		}

		Object id = request.get(idKey);
		boolean idGeneratedByAPIJSON = false;
		if (id == null && method == POST) {
			id = callback.newId(method, database, datasource, namespace, catalog, schema, table); // null 表示数据库自增 id
			idGeneratedByAPIJSON = id != null;
		}

		if (id != null) { // null 无效
			if (id instanceof Number) {
				if (((Number) id).longValue() <= 0) { // 一定没有值
					throw new NotExistException(TAG + ": newSQLConfig<T, M, L> " + table + ".id <= 0");
				}
			}
			else if (id instanceof String) {
				if (StringUtil.isEmpty(id, true)) { // 一定没有值
					throw new NotExistException(TAG + ": newSQLConfig<T, M, L> StringUtil.isEmpty(" + table + ".id, true)");
				}
			}
			else if (id instanceof Subquery) {}
			else {
				throw new IllegalArgumentException(idKey + ":value 中 value 的类型只能是 Long , String 或 Subquery ！");
			}

			if (idIn instanceof Collection) { // 共用idIn场景少性能差
				boolean contains = false;
				Collection<?> idList = ((Collection<?>) idIn);
				for (Object d : idList) { // 不用 idIn.contains(id) 因为 idIn 里存到很可能是 Integer，id 又是 Long！
					if (d != null && id.toString().equals(d.toString())) {
						contains = true;
						break;
					}
				}
				if (contains == false) { // empty有效  BaseModel.isEmpty(idIn) == false) {
					throw new NotExistException(TAG + ": newSQLConfig<T, M, L>  idIn != null && (((List<?>) idIn).contains(id) == false");
				}
			}

			if (method == DELETE || method == PUT) {
				config.setCount(1);
			}
		}

		// 对 id, id{}, userId, userId{} 处理，这些只要不为 null 就一定会作为 AND 条件 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
		Object userIdIn = userIdInKey.equals(idInKey) ? null : request.get(userIdInKey); // 可能是 userId{}:">0"
		if (userIdIn instanceof Collection) { // 排除掉 0, 负数, 空字符串 等无效 userId 值
			Collection<?> userIds = (Collection<?>) userIdIn;
			List<Object> newUserIdIn = new ArrayList<>();
			for (Object d : userIds) { // 不用 userIdIn.contains(userId) 因为 userIdIn 里存到很可能是 Integer，userId 又是 Long！
				if ((d instanceof Number && ((Number) d).longValue() > 0) || (d instanceof String && StringUtil.isNotEmpty(d, true))) {
					if (newUserIdIn.contains(d) == false) {
						newUserIdIn.add(d);
					}
				}
			}
			if (newUserIdIn.isEmpty()) {
				throw new NotExistException(TAG + ": newSQLConfig<T, M, L> userIdIn instanceof List >> 去掉无效 userId 后 newIdIn.isEmpty()");
			}
			userIdIn = newUserIdIn;
		}

		Object userId = userIdKey.equals(idKey) ? null : request.get(userIdKey);
		if (userId != null) { // null 无效
			if (userId instanceof Number) {
				if (((Number) userId).longValue() <= 0) { // 一定没有值
					throw new NotExistException(TAG + ": newSQLConfig<T, M, L> " + table + ".userId <= 0");
				}
			}
			else if (userId instanceof String) {
				if (StringUtil.isEmpty(userId, true)) { // 一定没有值
					throw new NotExistException(TAG + ": newSQLConfig<T, M, L> StringUtil.isEmpty(" + table + ".userId, true)");
				}
			}
			else if (userId instanceof Subquery) {}
			else {
				throw new IllegalArgumentException(userIdKey + ":value 中 value 的类型只能是 Long , String 或 Subquery ！");
			}

			if (userIdIn instanceof Collection) { // 共用 userIdIn 场景少性能差
				boolean contains = false;
				Collection<?> userIds = (Collection<?>) userIdIn;
				for (Object d : userIds) { // 不用 userIdIn.contains(userId) 因为 userIdIn 里存到很可能是 Integer，userId 又是 Long！
					if (d != null && userId.toString().equals(d.toString())) {
						contains = true;
						break;
					}
				}
				if (contains == false) { // empty有效  BaseModel.isEmpty(userIdIn) == false) {
					throw new NotExistException(TAG + ": newSQLConfig<T, M, L>  userIdIn != null && (((List<?>) userIdIn).contains(userId) == false");
				}
			}
		}
		// 对 id, id{}, userId, userId{} 处理，这些只要不为 null 就一定会作为 AND 条件 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


		String role = getString(request, KEY_ROLE);
		String cache = getString(request, KEY_CACHE);
		Subquery<T, M, L> from = (Subquery<T, M, L>) request.get(KEY_FROM);
		String column = getString(request, KEY_COLUMN);
		String nulls = getString(request, KEY_NULL);
		String cast = getString(request, KEY_CAST);
		String combine = getString(request, KEY_COMBINE);
		String group = getString(request, KEY_GROUP);
		Object having = request.get(KEY_HAVING);
		String havingAnd = getString(request, KEY_HAVING_AND);
		String sample = getString(request, KEY_SAMPLE);
		String latest = getString(request, KEY_LATEST);
		String partition = getString(request, KEY_PARTITION);
		String fill = getString(request, KEY_FILL);
		String order = getString(request, KEY_ORDER);
		Object keyMap = request.get(KEY_KEY);
		String raw = getString(request, KEY_RAW);
		String json = getString(request, KEY_JSON);
		String mthd = getString(request, KEY_METHOD);

		try {
			// 强制作为条件且放在最前面优化性能
			request.remove(idKey);
			request.remove(idInKey);
			request.remove(userIdKey);
			request.remove(userIdInKey);
			// 关键词
			request.remove(KEY_ROLE);
			request.remove(KEY_EXPLAIN);
			request.remove(KEY_CACHE);
			request.remove(KEY_DATABASE);
			request.remove(KEY_DATASOURCE);
			request.remove(KEY_NAMESPACE);
			request.remove(KEY_CATALOG);
			request.remove(KEY_SCHEMA);
			request.remove(KEY_FROM);
			request.remove(KEY_COLUMN);
			request.remove(KEY_NULL);
			request.remove(KEY_CAST);
			request.remove(KEY_COMBINE);
			request.remove(KEY_GROUP);
			request.remove(KEY_HAVING);
			request.remove(KEY_HAVING_AND);
			request.remove(KEY_SAMPLE);
			request.remove(KEY_LATEST);
			request.remove(KEY_PARTITION);
			request.remove(KEY_FILL);
			request.remove(KEY_ORDER);
			request.remove(KEY_KEY);
			request.remove(KEY_RAW);
			request.remove(KEY_JSON);
			request.remove(KEY_METHOD);


			// @null <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
			String[] nullKeys = StringUtil.split(nulls);
			if (nullKeys != null && nullKeys.length > 0) {
				for (String nk : nullKeys) {
					if (StringUtil.isEmpty(nk, true)) {
						throw new IllegalArgumentException(table + ":{ @null: value } 中的字符 '" + nk + "' 不合法！不允许为空！");
					}
					if (request.get(nk) != null) {
						throw new IllegalArgumentException(table + ":{ @null: value } 中的字符 '"
								+ nk + "' 已在当前对象有非 null 值！不允许对同一个 JSON key 设置不同值！");
					}

					request.put(nk, null);
				}
			}
			// @null >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

			// @cast <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
			String[] casts = StringUtil.split(cast);
			Map<String, String> castMap = null;
			if (casts != null && casts.length > 0) {
				castMap = new HashMap<>(casts.length);
				for (String c : casts) {
					apijson.orm.Entry<String, String> p = Pair.parseEntry(c);

					if (StringUtil.isEmpty(p.getKey(), true)) {
						throw new IllegalArgumentException(table + ":{} 里的 @cast: 'key0:type0,key1:type1..' 中 '"
								+ c +  "' 对应的 key 的字符 '" + p.getKey() + "' 不合法！不允许为空！");
					}
					if (StringUtil.isName(p.getValue()) == false) {
						throw new IllegalArgumentException(table + ":{} 里的 @cast: 'key0:type0,key1:type1..' 中 '"
								+ c +  "' 对应的 type 的字符 '" + p.getValue() + "' 不合法！必须符合类型名称格式！");
					}
					if (castMap.get(p.getKey()) != null) {
						throw new IllegalArgumentException(table + ":{} 里的 @cast: 'key0:type0,key1:type1..' 中 '"
								+ c +  "' 对应的 key 的字符 '" + p.getKey() + "' 已存在！不允许重复设置类型！");
					}

					castMap.put(p.getKey(), p.getValue());
				}
			}
			// @cast >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


			String[] rawArr = StringUtil.split(raw);
			config.setRaw(rawArr == null || rawArr.length <= 0 ? null : new ArrayList<>(Arrays.asList(rawArr)));

			Map<String, Object> tableWhere = new LinkedHashMap<String, Object>(); // 保证顺序好优化 WHERE id > 1 AND name LIKE...

			boolean ignoreBlankStr = IGNORE_BLANK_STRING_METHOD_LIST != null && IGNORE_BLANK_STRING_METHOD_LIST.contains(method);
			boolean ignoreEmptyStr = ignoreBlankStr || (IGNORE_EMPTY_STRING_METHOD_LIST != null && IGNORE_EMPTY_STRING_METHOD_LIST.contains(method));
			boolean ignoreEmptyOrBlankStr = ignoreEmptyStr || ignoreBlankStr;

			boolean enableFakeDelete = config.isFakeDelete();

			// 已经 remove了 id 和 id{}，以及 @key
			Set<String> set = request.keySet(); // 前面已经判断 request 是否为空
			if (method == POST) { // POST操作
				if (idIn != null) {
					throw new IllegalArgumentException(table + ":{" + idInKey + ": value} 里的 key 不合法！POST 请求中不允许传 " + idInKey
							+ " 这种非字段命名 key ！必须为 英文字母 开头且只包含 英文字母、数字、下划线的 字段命名！");				}
				if (userIdIn != null) {
					throw new IllegalArgumentException(table + ":{" + userIdInKey + ": value} 里的 key 不合法！POST 请求中不允许传 " + userIdInKey
							+ " 这种非字段命名 key ！必须为 英文字母 开头且只包含 英文字母、数字、下划线的 字段命名！");				}

				if (set != null && set.isEmpty() == false) { // 不能直接return，要走完下面的流程
					for (String k : set) {
						if (StringUtil.isName(k) == false) {
							throw new IllegalArgumentException(table + ":{" + k + ": value} 里的 key 不合法！POST 请求中不允许传 " + k
									+ " 这种非字段命名 key ！必须为 英文字母 开头且只包含 英文字母、数字、下划线的 字段命名！");
						}
					}

					String[] columns = set.toArray(new String[]{});

					Collection<Object> valueCollection = request.values();
					Object[] values = valueCollection == null ? null : valueCollection.toArray();

					if (values == null || values.length != columns.length) {
						throw new Exception("服务器内部错误:\n" + TAG
								+ " newSQLConfig<T, M, L>  values == null || values.length != columns.length !");
					}

					column = (id == null ? "" : idKey + ",") + (userId == null ? "" : userIdKey + ",")
							+ StringUtil.get(columns); //set已经判断过不为空

					int idCount = id == null ? (userId == null ? 0 : 1) : (userId == null ? 1 : 2);
					int size = idCount + columns.length; // 以 key 数量为准

					List<Object> items = new ArrayList<>(size);  // VALUES(item0, item1, ...)
					if (id != null) {
						items.add(id); // idList.get(i)); // 第 0 个就是 id
					}
					if (userId != null) {
						items.add(userId); // idList.get(i)); // 第 1 个就是 userId
					}

					for (int j = 0; j < values.length; j++) {
						items.add(values[j]); // 从第 1 个开始，允许 "null"
					}

					List<List<Object>> valuess = new ArrayList<>(1);
					valuess.add(items);
					config.setValues(valuess);
				}
			}
			else { // 非 POST 操作
				final boolean isWhere = method != PUT; // 除了POST,PUT，其它全是条件！！！

				// 条件<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
				String[] ws = StringUtil.split(combine);
				String combineExpr = ws == null || ws.length != 1 ? null : ws[0];

				Map<String, List<String>> combineMap = new LinkedHashMap<>();
				List<String> andList = new ArrayList<>();
				List<String> orList = new ArrayList<>();
				List<String> notList = new ArrayList<>();

				List<String> whereList = new ArrayList<>();

				// 强制作为条件且放在最前面优化性能
				if (id != null) {
					tableWhere.put(idKey, id);
					andList.add(idKey);
					whereList.add(idKey);
				}
				if (idIn != null) {
					tableWhere.put(idInKey, idIn);
					andList.add(idInKey);
					whereList.add(idInKey);
				}
				if (userId != null) {
					tableWhere.put(userIdKey, userId);
					andList.add(userIdKey);
					whereList.add(userIdKey);
				}
				if (userIdIn != null) {
					tableWhere.put(userIdInKey, userIdIn);
					andList.add(userIdInKey);
					whereList.add(userIdInKey);
				}

				if (enableFakeDelete) {
					// 查询 Access 假删除
					Map<String, Object> accessFakeDeleteMap = method == DELETE
							? null : AbstractVerifier.ACCESS_FAKE_DELETE_MAP.get(config.getTable());
					Object deletedKey = accessFakeDeleteMap == null ? null : accessFakeDeleteMap.get(KEY_DELETED_KEY);
					boolean hasKey = deletedKey instanceof String && StringUtil.isNotEmpty(deletedKey, true);
					Object deletedValue = hasKey ? accessFakeDeleteMap.get(KEY_DELETED_VALUE) : null;
					boolean containNotDeletedValue = hasKey && accessFakeDeleteMap.containsKey(KEY_NOT_DELETED_VALUE);
					Object notDeletedValue = containNotDeletedValue ? accessFakeDeleteMap.get(KEY_NOT_DELETED_VALUE) : null;

					if (deletedValue != null || containNotDeletedValue) {
						boolean isFakeDelete = true;
						if (from != null) {
							// 兼容 JOIN 外层 SELECT 重复生成 deletedKey
							SQLConfig<?, ?, ?> cfg = from.gainConfig();
							if (cfg != null && StringUtil.equals(table, cfg.getTable())) {
								isFakeDelete = false;
							}

							List<? extends Join<?, ?, ?>> jl = isFakeDelete && cfg != null ? cfg.getJoinList() : null;
							if (jl != null) {
								for (Join<?, ?, ?> join : jl) {
									if (join != null && StringUtil.equals(table, join.getTable())) {
										isFakeDelete = false;
										break;
									}
								}
							}
						}

						if (isFakeDelete) { // 支持 deleted != 1 / deleted is null 等表达式
							if (deletedValue != null) { // deletedKey != deletedValue
								String key = deletedKey + "!";
								tableWhere.put(key, deletedValue);
								andList.add(key);
								whereList.add(key);
							}

							if (containNotDeletedValue) { // deletedKey = notDeletedValue
								String key = deletedKey.toString();
								tableWhere.put(key, notDeletedValue);
								andList.add(key);
								whereList.add(key);
							}
						}
					}
				}

				if (StringUtil.isNotEmpty(combineExpr, true)) {
					List<String> banKeyList = Arrays.asList(idKey, idInKey, userIdKey, userIdInKey);
					for (String key : banKeyList) {
						if (isKeyInCombineExpr(combineExpr, key)) {
							throw new UnsupportedOperationException(table + ":{} 里的 @combine:value 中的 value 里 " + key + " 不合法！"
									+ "不允许传 [" + idKey + ", " + idInKey + ", " + userIdKey + ", " + userIdInKey + "] 其中任何一个！");
						}
					}
				}
				else if (ws != null) {
					for (int i = 0; i < ws.length; i++) { // 去除 &,|,! 前缀
						String w = ws[i];
						if (w != null) {
							if (w.startsWith("&")) {
								w = w.substring(1);
								andList.add(w);
							}
							else if (w.startsWith("|")) {
								if (method == PUT) {
									throw new IllegalArgumentException(table + ":{} 里的 @combine:value 中的value里条件 " + ws[i] + " 不合法！"
											+ "PUT请求的 @combine:\"key0,key1,...\" 不允许传 |key 或 !key !");
								}
								w = w.substring(1);
								orList.add(w);
							}
							else if (w.startsWith("!")) {
								if (method == PUT) {
									throw new IllegalArgumentException(table + ":{} 里的 @combine:value 中的value里条件 " + ws[i] + " 不合法！"
											+ "PUT请求的 @combine:\"key0,key1,...\" 不允许传 |key 或 !key !");
								}
								w = w.substring(1);
								notList.add(w);
							}
							else {
								orList.add(w);
							}

							if (w.isEmpty()) {
								throw new IllegalArgumentException(table + ":{} 里的 @combine:value 中的value里条件 " + ws[i] + " 不合法！不允许为空值！");
							}
							else {
								if (idKey.equals(w) || idInKey.equals(w) || userIdKey.equals(w) || userIdInKey.equals(w)) {
									throw new UnsupportedOperationException(table + ":{} 里的 @combine:value 中的 value 里 " + ws[i] + " 不合法！"
											+ "不允许传 [" + idKey + ", " + idInKey + ", " + userIdKey + ", " + userIdInKey + "] 其中任何一个！");
								}
							}

							whereList.add(w);
						}

						// 可重写回调方法自定义处理 // 动态设置的场景似乎很少，而且去掉后不方便用户排错！
						// 去掉判断，有时候不在没关系，如果是对增删改等非开放请求强制要求传对应的条件，可以用 Operation.NECESSARY
						if (request.containsKey(w) == false) {  // 和 request.get(w) == null 没区别，前面 Parser<T, M, L> 已经过滤了 null
							//	throw new IllegalArgumentException(table + ":{} 里的 @combine:value 中的value里 " + ws[i] + " 对应的 " + w + " 不在它里面！");
							callback.onMissingKey4Combine(table, request, combine, ws[i], w);
							if (config instanceof AbstractSQLConfig) {
								((AbstractSQLConfig<T, M, L>) config).putWarnIfNeed(KEY_COMBINE, table + ":{} 里的 @combine:value 中的 value 里 "
										+ ws[i] + " 对应的条件 " + w + ":value 中 value 必须存在且不能为 null！");
							}
						}
					}
				}

				// 条件 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


				Map<String, Object> tableContent = new LinkedHashMap<String, Object>();
				for (String key : set) {
					Object value = request.get(key);
					if (ignoreEmptyOrBlankStr && value instanceof String && StringUtil.isEmpty(value, ignoreBlankStr)) {
						continue;
					}

					if (key.endsWith("<>") == false && value instanceof Map) { // 只允许常规 Object
						throw new IllegalArgumentException(table + ":{ " + key + ":value } 中 value 类型错误！除了 key<>:{} 外，不允许 "
								+ key + " 等其它任何 key 对应 value 的类型为 JSONMap {} !");
					}

					// 兼容 PUT @combine
					// 解决AccessVerifier新增userId没有作为条件，而是作为内容，导致PUT，DELETE出错
					if ((isWhere || (StringUtil.isName(key.replaceFirst("[+-]$", "")) == false))
							|| (isWhere == false && StringUtil.isNotEmpty(combineExpr, true) && isKeyInCombineExpr(combineExpr, key))) {
						tableWhere.put(key, value);
						if (whereList.contains(key) == false) {
							andList.add(key);
						}
					} else if (whereList.contains(key)) {
						tableWhere.put(key, value);
					} else {
						tableContent.put(key, value); // 一样 instanceof List<?> ? JSON.toJSONString(value) : value);
					}
				}

				if (combineMap != null) {
					combineMap.put("&", andList);
					combineMap.put("|", orList);
					combineMap.put("!", notList);
				}
				config.setCombineMap(combineMap);
				config.setCombine(combineExpr);

				config.setContent(tableContent);
			}

			if (enableFakeDelete && method == DELETE) {
				// 查询 Access 假删除
				Map<String, Map<String, Object>> fakeDeleteConfigMap = AbstractVerifier.ACCESS_FAKE_DELETE_MAP;
				Map<String, Object> accessFakeDeleteMap = fakeDeleteConfigMap == null
						? null : fakeDeleteConfigMap.get(config.getTable());

				Object deletedKey = accessFakeDeleteMap == null ? null : accessFakeDeleteMap.get(KEY_DELETED_KEY);
				if ((deletedKey instanceof String) == false || StringUtil.isEmpty(deletedKey, true)) {
					throw new IllegalArgumentException(config.getTable() + " 对应的假删除配置错误！"
							+ KEY_DELETED_KEY + ":value 中 value 必须为非空 String！当前值为 " + deletedKey);
				}

				// 假删除需要更新的其他字段，比如：删除时间 deletedTime 之类的
				Map<String, Object> fakeDeleteMap = new HashMap<>();
				fakeDeleteMap.put(deletedKey.toString(), accessFakeDeleteMap.get(KEY_DELETED_VALUE));
				fakeDeleteMap = config.onFakeDelete(fakeDeleteMap);

				Map<String, Object> content = config.getContent();
				if (content == null || content.isEmpty()) {
					content = fakeDeleteMap;
				} else {
					content.putAll(fakeDeleteMap);
				}

				config.setMethod(PUT);
				config.setContent(content);
			}

			List<String> cs = new ArrayList<>();

			List<String> rawList = config.getRaw();
			boolean containColumnHavingAnd = rawList != null && rawList.contains(KEY_HAVING_AND);

			if (containColumnHavingAnd) {
				throw new IllegalArgumentException(table + ":{ @raw:value } 的 value 里字符 @having& 不合法！"
						+ "@raw 不支持 @having&，请用 @having 替代！");
			}

			// TODO 这段是否必要？如果 @column 只支持分段后的 SQL 片段，也没问题
			boolean containColumnRaw = rawList != null && rawList.contains(KEY_COLUMN);
			String rawColumnSQL = null;
			if (containColumnRaw) {
				rawColumnSQL = config.gainRawSQL(KEY_COLUMN, column);
				if (rawColumnSQL != null) {
					cs.add(rawColumnSQL);
				}
			}

			boolean distinct = rawColumnSQL == null && column != null && column.startsWith(PREFIX_DISTINCT);
			if (rawColumnSQL == null) {
				// key0,key1;fun0(key0,...);fun1(key0,...);key3;fun2(key0,...)
				String[] fks = StringUtil.split(distinct ? column.substring(PREFIX_DISTINCT.length()) : column, ";");
				if (fks != null) {
					for (String fk : fks) {
						if (containColumnRaw) {
							String rawSQL = config.gainRawSQL(KEY_COLUMN, fk);
							if (rawSQL != null) {
								cs.add(rawSQL);
								continue;
							}
						}

						if (fk.contains("(")) {  // fun0(key0,...)
							cs.add(fk);
						}
						else { // key0,key1...
							String[] ks = StringUtil.split(fk);
							if (ks != null && ks.length > 0) {
								cs.addAll(Arrays.asList(ks));
							}
						}
					}
				}
			}


			// @having, @haivng& <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
			Object newHaving = having;
			boolean isHavingAnd = false;

			Map<String, Object> havingMap = new LinkedHashMap<>();
			if (havingAnd != null) {
				if (having != null) {
					throw new IllegalArgumentException(table + ":{ @having: value1, @having&: value2 } "
							+ "中 value1 与 value2 不合法！不允许同时传 @having 和 @having& ，两者最多传一个！");
				}

				newHaving = havingAnd;
				isHavingAnd = true;
			}

			String havingKey = (isHavingAnd ? KEY_HAVING_AND : KEY_HAVING);
			String havingCombine = "";

			if (newHaving instanceof String) {
				String[] havingss = StringUtil.split((String) newHaving, ";");
				if (havingss != null) {
					int ind = -1;
					for (int i = 0; i < havingss.length; i++) {

						String havingsStr = havingss[i];
						int start = havingsStr == null ? -1 : havingsStr.indexOf("(");
						int end = havingsStr == null ? -1 : havingsStr.lastIndexOf(")");
						if (IS_HAVING_ALLOW_NOT_FUNCTION == false && (start < 0 || start >= end)) {
							throw new IllegalArgumentException(table + ":{ " + havingKey +  ":value } 里的 value 中的第 " + i +
									" 个字符 '" + havingsStr + "' 不合法！里面没有包含 SQL 函数！必须为 fun(col1,col2..)?val 格式！");
						}

						String[] havings = start >= 0 && end > start ? new String[]{havingsStr} : StringUtil.split(havingsStr);
						if (havings != null) {
							for (int j = 0; j < havings.length; j++) {
								ind ++;
								String h = havings[j];
								if (StringUtil.isEmpty(h, true)) {
									throw new IllegalArgumentException(table + ":{ " + havingKey +  ":value } 里的"
											+ " value 中的第 " + ind + " 个字符 '" + h + "' 不合法！不允许为空！");
								}

								havingMap.put("having" + ind, h);

								if (isHavingAnd == false && IS_HAVING_DEFAULT_AND == false) {
									havingCombine += (ind <= 0 ? "" : " | ") + "having" + ind;
								}
							}
						}
					}
				}
			}
			else if (newHaving instanceof Map<?, ?>) {
				if (isHavingAnd) {
					throw new IllegalArgumentException(table + ":{ " + havingKey +  ":value } 里的 value 类型不合法！"
							+ "@having&:value 中 value 只能是 String，@having:value 中 value 只能是 String 或 JSONMap ！");
				}

				M havingObj = JSON.createJSONObject((Map<? extends String, ?>) newHaving);
				Set<Entry<String, Object>> havingSet = havingObj.entrySet();
				for (Entry<String, Object> entry : havingSet) {
					String k = entry == null ? null : entry.getKey();
					Object v = k == null ? null : entry.getValue();
					if (v == null) {
						continue;
					}
					if (v instanceof String == false) {
						throw new IllegalArgumentException(table + ":{ " + havingKey +  ":{ " + k + ":value } } 里的"
								+ " value 不合法！类型只能是 String，且不允许为空！");
					}
					if (ignoreEmptyOrBlankStr && StringUtil.isEmpty(v, ignoreBlankStr)) {
						continue;
					}

					if (KEY_COMBINE.equals(k)) {
						havingCombine = (String) v;
					}
					else if (StringUtil.isName(k) == false) {
						throw new IllegalArgumentException(table + ":{ " + havingKey +  ":{ " + k + ":value } } 里的"
								+ " key 对应字符 " + k + " 不合法！必须为 英文字母 开头，且只包含 英文字母、下划线、数字 的合法变量名！");
					}
					else {
						havingMap.put(k, (String) v);
					}
				}
			}
			else if (newHaving != null) {
				throw new IllegalArgumentException(table + ":{ " + havingKey +  ":value } 里的 value 类型不合法！"
						+ "@having:value 中 value 只能是 String 或 JSONMap，@having&:value 中 value 只能是 String ！");
			}
			// @having, @haivng& >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

			if (keyMap instanceof Map) {
				config.setKeyMap((Map<String, String>) keyMap);
			}
			else if (keyMap instanceof String) {
				String[] ks = StringUtil.split((String) keyMap, ";");
				if (ks.length > 0) {
					Map<String, String> nkm = new LinkedHashMap<>();
					for (int i = 0; i < ks.length; i++) {
						Entry<String, String> ety = Pair.parseEntry(ks[i]);
						if (ety == null) {
							continue;
						}
						nkm.put(ety.getKey(), ety.getValue());
					}
					config.setKeyMap(nkm);
				}
			}
			else if (keyMap != null) {
				throw new UnsupportedDataTypeException("@key:value 中 value 错误，只能是 String, JSONMap 中的一种！");
			}


			config.setExplain(explain != null && explain);
			config.setCache(getCache(cache));
			config.setDistinct(distinct);
			config.setColumn(column == null ? null : cs); //解决总是 config.column != null，总是不能得到 *
			config.setFrom(from);

			config.setRole(role);
			config.setId(id);
			config.setIdGeneratedByAPIJSON(idGeneratedByAPIJSON);
			config.setIdIn(idIn);
			config.setUserId(userId);
			config.setUserIdIn(userIdIn);

			config.setNull(nullKeys == null || nullKeys.length <= 0 ? null : new ArrayList<>(Arrays.asList(nullKeys)));
			config.setCast(castMap);
			config.setWhere(tableWhere);
			config.setGroup(group);
			config.setHaving(havingMap);
			config.setHavingCombine(havingCombine);
			config.setSample(sample);
			config.setLatest(latest);
			config.setPartition(partition);
			config.setFill(fill);
			config.setOrder(order);

			String[] jsons = StringUtil.split(json);
			config.setJson(jsons == null || jsons.length <= 0 ? null : new ArrayList<>(Arrays.asList(jsons)));

		}
		finally {  // 后面还可能用到，要还原
			// id, id{}, userId, userIdIn 条件
			if (id != null) {
				request.put(idKey, id);
			}
			if (idIn != null) {
				request.put(idInKey, idIn);
			}
			if (userId != null) {
				request.put(userIdKey, userId);
			}
			if (userIdIn != null) {
				request.put(userIdInKey, userIdIn);
			}

			// 关键词
			if (role != null) {
				request.put(KEY_ROLE, role);
			}
			if (explain != null) {
				request.put(KEY_EXPLAIN, explain);
			}
			if (cache != null) {
				request.put(KEY_CACHE, cache);
			}
			if (database != null) {
				request.put(KEY_DATABASE, database);
			}
			if (datasource != null) {
				request.put(KEY_DATASOURCE, datasource);
			}
			if (schema != null) {
				request.put(KEY_SCHEMA, schema);
			}
			if (from != null) {
				request.put(KEY_FROM, from);
			}
			if (column != null) {
				request.put(KEY_COLUMN, column);
			}
			if (nulls != null) {
				request.put(KEY_NULL, nulls);
			}
			if (cast != null) {
				request.put(KEY_CAST, cast);
			}
			if (combine != null) {
				request.put(KEY_COMBINE, combine);
			}
			if (group != null) {
				request.put(KEY_GROUP, group);
			}
			if (having != null) {
				request.put(KEY_HAVING, having);
			}
			if (havingAnd != null) {
				request.put(KEY_HAVING_AND, havingAnd);
			}
			if (sample != null) {
				request.put(KEY_SAMPLE, sample);
			}
			if (latest != null) {
				request.put(KEY_LATEST, latest);
			}
			if (partition != null) {
				request.put(KEY_PARTITION, partition);
			}
			if (fill != null) {
				request.put(KEY_FILL, fill);
			}
			if (order != null) {
				request.put(KEY_ORDER, order);
			}
			if (keyMap != null) {
				request.put(KEY_KEY, keyMap);
			}
			if (raw != null) {
				request.put(KEY_RAW, raw);
			}
			if (json != null) {
				request.put(KEY_JSON, json);
			}
			if (mthd != null) {
				request.put(KEY_METHOD, mthd);
			}
		}

		return config;
	}



	/**
	 * @param method
	 * @param config
	 * @param joinList
	 * @param callback
	 * @return
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> SQLConfig<T, M, L> parseJoin(
			RequestMethod method, SQLConfig<T, M, L> config, List<Join<T, M, L>> joinList, Callback<T, M, L> callback) throws Exception {
		boolean isQuery = RequestMethod.isQueryMethod(method);
		config.setKeyPrefix(isQuery && config.isMain() == false);

		//TODO 解析出 SQLConfig<T, M, L> 再合并 column, order, group 等
		if (joinList == null || joinList.isEmpty() || RequestMethod.isQueryMethod(method) == false) {
			return config;
		}


		String table;
		String alias;
		for (Join<T, M, L> join : joinList) {
			table = join.getTable();
			alias = join.getAlias();
			//JOIN子查询不能设置LIMIT，因为ON关系是在子查询后处理的，会导致结果会错误
			SQLConfig<T, M, L> joinConfig = newSQLConfig(method, table, alias, join.getRequest(), null, false, callback);
			SQLConfig<T, M, L> cacheConfig = join.canCacheViceTable() == false ? null : newSQLConfig(method, table, alias
					, join.getRequest(), null, false, callback).setCount(join.getCount());

			if (join.isAppJoin() == false) { //除了 @ APP JOIN，其它都是 SQL JOIN，则副表要这样配置
				if (joinConfig.getDatabase() == null) {
					joinConfig.setDatabase(config.getDatabase()); //解决主表 JOIN 副表，引号不一致
				}
				else if (joinConfig.getDatabase().equals(config.getDatabase()) == false) {
					throw new IllegalArgumentException("主表 " + config.getTable() + " 的 @database:" + config.getDatabase()
							+ " 和它 SQL JOIN 的副表 " + table + " 的 @database:" + joinConfig.getDatabase() + " 不一致！");
				}
				if (joinConfig.getSchema() == null) {
					joinConfig.setSchema(config.getSchema()); //主表 JOIN 副表，默认 schema 一致
				}

				if (cacheConfig != null) {
					cacheConfig.setDatabase(joinConfig.getDatabase()).setSchema(joinConfig.getSchema()); //解决主表 JOIN 副表，引号不一致
				}

				if (isQuery) {
					config.setKeyPrefix(true);
				}

				joinConfig.setMain(false).setKeyPrefix(true);

				if (join.getOn() != null) {
					SQLConfig<T, M, L> onConfig = newSQLConfig(method, table, alias, join.getOn(), null, false, callback);
					onConfig.setMain(false)
							.setKeyPrefix(true)
							.setDatabase(joinConfig.getDatabase())
							.setSchema(joinConfig.getSchema()); //解决主表 JOIN 副表，引号不一致

					join.setOnConfig(onConfig);
				}

				if (join.getOuter() != null) {
					SQLConfig<T, M, L> outerConfig = newSQLConfig(method, table, alias, join.getOuter(), null, false, callback);
					outerConfig.setMain(false)
							.setKeyPrefix(true)
							.setDatabase(joinConfig.getDatabase())
							.setSchema(joinConfig.getSchema()); //解决主表 JOIN 副表，引号不一致
					join.setOuterConfig(outerConfig);
				}
			}

			//解决 query: 1/2 查数量时报错
			/* SELECT  count(*)  AS count  FROM sys.Moment AS Moment
			   LEFT JOIN ( SELECT count(*)  AS count FROM sys.Comment ) AS Comment ON Comment.momentId = Moment.id LIMIT 1 OFFSET 0 */
			if (RequestMethod.isHeadMethod(method, true)) {
				List<On> onList = join.getOnList();
				List<String> column = onList == null ? null : new ArrayList<>(onList.size());
				//解决 pg  如果只查询关联键，会报找不到column的错误
				///* SELECT  count(*)  AS count  FROM sys.Moment AS Moment
				//			   LEFT JOIN ( SELECT *  FROM sys.Comment ) AS Comment ON Comment.momentId = Moment.id LIMIT 1 OFFSET 0 */
				if (column != null && joinConfig.isMSQL()) { // 暂时这样兼容 PostgreSQL 等不支持 SELECT 中不包含对应 key 的隐式 ON 关联字段的数据库
					for (On on : onList) {
						column.add(on.getKey()); // TODO PostgreSQL 等需要找到具体的 targetTable 对应 targetKey 来加到 SELECT，比直接 SELECT * 性能更好
					}
				}

				joinConfig.setMethod(GET);  // 子查询不能为 SELECT count(*) ，而应该是 SELECT momentId
				joinConfig.setColumn(column);  // 优化性能，不取非必要的字段

				if (cacheConfig != null) {
					cacheConfig.setMethod(GET);  // 子查询不能为 SELECT count(*) ，而应该是 SELECT momentId
					cacheConfig.setColumn(column);  // 优化性能，不取非必要的字段
				}
			}

			join.setJoinConfig(joinConfig);
			join.setCacheConfig(cacheConfig);
		}

		config.setJoinList(joinList);

		return config;
	}



	/**获取客户端实际需要的key
	 * verifyName = true
	 * @param method
	 * @param originKey
	 * @param isTableKey
	 * @param saveLogic 保留逻辑运算符 & | !
	 * @return
	 */
	public static String gainRealKey(RequestMethod method, String originKey
			, boolean isTableKey, boolean saveLogic) throws Exception {
		return gainRealKey(method, originKey, isTableKey, saveLogic, true);
	}
	/**获取客户端实际需要的key
	 * @param method
	 * @param originKey
	 * @param isTableKey
	 * @param saveLogic 保留逻辑运算符 & | !
	 * @param verifyName 验证key名是否符合代码变量/常量名
	 * @return
	 */
	public static String gainRealKey(RequestMethod method, String originKey
			, boolean isTableKey, boolean saveLogic, boolean verifyName) throws Exception {
		Log.i(TAG, "getRealKey  saveLogic = " + saveLogic + "; originKey = " + originKey);
		if (originKey == null || JSONMap.isArrayKey(originKey)) {
			Log.w(TAG, "getRealKey  originKey == null || apijson.JSONMap.isArrayKey(originKey) >>  return originKey;");
			return originKey;
		}

		String key = originKey;
		if (key.endsWith("$")) {//搜索 LIKE，查询时处理
			String k = key.substring(0, key.length() - 1);
			// key%$:"a" -> key LIKE '%a%'; key?%$:"a" -> key LIKE 'a%'; key_?$:"a" -> key LIKE '_a'; key_%$:"a" -> key LIKE '_a%'
			char c = k.isEmpty() ? 0 : k.charAt(k.length() - 1);

			if (c == '%' || c == '_' || c == '?') {
				k = k.substring(0, k.length() - 1);

				char c2 = k.isEmpty() ? 0 : k.charAt(k.length() - 1);
				if (c2 == '%' || c2 == '_' || c2 == '?') {
					if (c2 == c) {
						throw new IllegalArgumentException(originKey + ":value 中字符 "
								+ k + " 不合法！key$:value 中不允许 key 中有连续相同的占位符！");
					}

					k = k.substring(0, k.length() - 1);
				}
				else if (c == '?') {
					throw new IllegalArgumentException(originKey + ":value 中字符 " + originKey
							+ " 不合法！key$:value 中不允许只有单独的 '?'，必须和 '%', '_' 之一配合使用 ！");
				}
			}

			key = k;
		}
		else if (key.endsWith("~")) {//匹配正则表达式 REGEXP，查询时处理
			key = key.substring(0, key.length() - 1);
			if (key.endsWith("*")) {//忽略大小写
				key = key.substring(0, key.length() - 1);
			}
		}
		else if (key.endsWith("%")) {//数字、文本、日期范围 BETWEEN AND
			key = key.substring(0, key.length() - 1);
		}
		else if (key.endsWith("{}")) {//被包含 IN，或者说key对应值处于value的范围内。查询时处理
			key = key.substring(0, key.length() - 2);
		}
		else if (key.endsWith("}{")) {//被包含 EXISTS，或者说key对应值处于value的范围内。查询时处理
			key = key.substring(0, key.length() - 2);
		}
		else if (key.endsWith("<>")) {//包含 json_contains，或者说value处于key对应值的范围内。查询时处理
			key = key.substring(0, key.length() - 2);
		}
		else if (key.endsWith("()")) {//方法，查询完后处理，先用一个Map<key,function>保存
			key = key.substring(0, key.length() - 2);
		}
		else if (key.endsWith("@")) {//引用，引用对象查询完后处理。fillTarget中暂时不用处理，因为非GET请求都是由给定的id确定，不需要引用
			key = key.substring(0, key.length() - 1);
		}
		else if (key.endsWith(">=")) {//比较。查询时处理
			key = key.substring(0, key.length() - 2);
		}
		else if (key.endsWith("<=")) {//比较。查询时处理
			key = key.substring(0, key.length() - 2);
		}
		else if (key.endsWith(">")) {//比较。查询时处理
			key = key.substring(0, key.length() - 1);
		}
		else if (key.endsWith("<")) {//比较。查询时处理
			key = key.substring(0, key.length() - 1);
		}
		else if (key.endsWith("+")) {//延长，PUT查询时处理
			if (method == PUT) {//不为PUT就抛异常
				key = key.substring(0, key.length() - 1);
			}
		}
		else if (key.endsWith("-")) {//缩减，PUT查询时处理
			if (method == PUT) {//不为PUT就抛异常
				key = key.substring(0, key.length() - 1);
			}
		}

		// TODO if (key.endsWith("-")) { // 表示 key 和 value 顺序反过来: value LIKE key ?

		// 不用Logic优化代码，否则 key 可能变为 key| 导致 key=value 变成 key|=value 而出错
		String last = key.isEmpty() ? "" : key.substring(key.length() - 1);
		if ("&".equals(last) || "|".equals(last) || "!".equals(last)) {
			key = key.substring(0, key.length() - 1);
		} else {
			last = null; // 避免key + StringUtil.getString(last) 错误延长
		}

		String len = "";
		if (key.endsWith("[") || key.endsWith("{")) {
			len = key.substring(key.length() - 1);
			key = key.substring(0, key.length() - 1);
		}

		// "User:toUser":User转换"toUser":User, User为查询同名Table得到的JSONObject。交给客户端处理更好
		if (isTableKey) { // 不允许在column key中使用Type:key形式
			key = Pair.parseEntry(key, true).getKey(); // table以左边为准
		} else {
			key = Pair.parseEntry(key).getValue();// column 以右边为准
		}

		if (verifyName && StringUtil.isName(key.startsWith("@") ? key.substring(1) : key) == false) {
			throw new IllegalArgumentException(method + "请求，字符 " + originKey + " 不合法！"
					+ " key:value 中的 key 只能关键词 '@key' 或 'key[长度符][逻辑符][条件符]' 或 PUT 请求下的 'key+' / 'key-' ！"
					+ "长度符 只能为 [ - length 和 { - json_length，逻辑符 只能是 & - 与、| - 或、! - 非 ！");
		}

		key += len;

		if (saveLogic && last != null) {
			key = key + last;
		}
		Log.i(TAG, "getRealKey  return key = " + key);
		return key;
	}


	public static interface IdCallback<T> {
		/**为 post 请求新建 id， 只能是 Long 或 String
		 * @param method
		 * @param database
		 * @param datasource
		 * @param namespace
		 * @param catalog
		 * @param schema
		 * @param table
		 * @return
		 */
		T newId(RequestMethod method, String database, String datasource, String namespace, String catalog, String schema, String table);


		/**获取主键名
		 * @param database
		 * @param datasource
		 * @param namespace
		 * @param catalog
		 * @param schema
		 * @param table
		 * @return
		 */
		String getIdKey(String database, String datasource, String namespace, String catalog, String schema, String table);

		/**获取 User 的主键名
		 * @param database
		 * @param datasource
		 * @param namespace
		 * @param catalog
		 * @param schema
		 * @param table
		 * @return
		 */
		String getUserIdKey(String database, String datasource, String namespace, String catalog, String schema, String table);
	}

	public static interface Callback<T, M extends Map<String, Object>, L extends List<Object>> extends IdCallback<T> {
		/**获取 SQLConfig<T, M, L> 的实例
		 * @param method
		 * @param database
		 * @param datasource
		 * @param namespace
		 * @param catalog
		 * @param schema
		 * @param table
		 * @return
		 */
		SQLConfig<T, M, L> getSQLConfig(RequestMethod method, String database, String datasource, String namespace, String catalog, String schema, String table);

		/**combine 里的 key 在 request 中 value 为 null 或不存在，即 request 中缺少用来作为 combine 条件的 key: value
		 * @param name
		 * @param request
		 * @param combine
		 * @param item
		 * @param key
		 * @throws Exception
		 */
		void onMissingKey4Combine(String name, M request, String combine, String item, String key) throws Exception;
	}

	public static Long LAST_ID;
	static {
		LAST_ID = System.currentTimeMillis();
	}

	public static abstract class SimpleCallback<T, M extends Map<String, Object>, L extends List<Object>> implements Callback<T, M, L> {

		@SuppressWarnings("unchecked")
		@Override
		public T newId(RequestMethod method, String database, String datasource, String namespace, String catalog, String schema, String table) {
			Long id = System.currentTimeMillis();
			if (id <= LAST_ID) {
				id = LAST_ID + 1; // 解决高并发下 id 冲突导致新增记录失败
			}
			LAST_ID = id;

			return (T) id;
		}

		@Override
		public String getIdKey(String database, String datasource, String namespace, String catalog, String schema, String table) {
			return KEY_ID;
		}

		@Override
		public String getUserIdKey(String database, String datasource, String namespace, String catalog, String schema, String table) {
			return KEY_USER_ID;
		}

		@Override
		public void onMissingKey4Combine(String name, M request, String combine, String item, String key) throws Exception {
			if (ALLOW_MISSING_KEY_4_COMBINE) {
				return;
			}
			throw new IllegalArgumentException(name + ":{} 里的 @combine:value 中的value里 "
					+ item + " 对应的条件 " + key + ":value 中 value 必须存在且不能为 null！");
		}

	}

	private static boolean isKeyInCombineExpr(String combineExpr, String key) {
		while (combineExpr.isEmpty() == false) {
			int index = combineExpr.indexOf(key);
			if (index < 0) {
				return false;
			}

			char left = index <= 0 ? ' ' : combineExpr.charAt(index - 1);
			char right = index >= combineExpr.length() - key.length() ? ' ' : combineExpr.charAt(index + key.length());
			if ((left == ' ' || left == '(' || left == '&' || left == '|' || left == '!') && (right == ' ' || right == ')' || right == ':')) {
				return true;
			}
			int newIndex = index + key.length() + 1;
			if (combineExpr.length() <= newIndex) {
				break;
			}
			combineExpr = combineExpr.substring(newIndex);
		}

		return false;
	}


}
