/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.util.List;
import java.util.Map;

import apijson.NotNull;
import apijson.StringUtil;

/**连表 配置
 * @author Lemon
 */
public class Join<T, M extends Map<String, Object>, L extends List<Object>> {

	private String path;  // /User/id@

	private String joinType;  // "@" - APP, "<" - LEFT, ">" - RIGHT, "*" - CROSS, "&" - INNER, "|" - FULL, "!" - OUTER, "^" - SIDE, "(" - ANTI, ")" - FOREIGN, "~" ASOF
	private String table;  // User
	private String alias;  // owner
	private int count = 1;	// 当app join子表，需要返回子表的行数，默认1行；
	private List<On> onList;  // ON User.id = Moment.userId AND ...

	private M request;  // { "id@":"/Moment/userId" }
	private M on;  // "join": { "</User": { "@order":"id-", "@group":"id", "name~":"a", "tag$":"%a%", "@combine": "name~,tag$" } } 中的 </User 对应值

	private M outer; // "join": { "</User": { "key2": value2, "@column": "key2,key3","@group": "key2+" }}
	private SQLConfig<T, M, L> joinConfig;
	private SQLConfig<T, M, L> cacheConfig;
	private SQLConfig<T, M, L> onConfig;

	private SQLConfig<T, M, L> outerConfig;

	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}

	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}

	public String getJoinType() {
		return joinType;
	}
	public void setJoinType(String joinType) {
		this.joinType = joinType;
	}
	public String getTable() {
		return table;
	}
	public void setTable(String table) {
		this.table = table;
	}
	public String getAlias() {
		return alias;
	}
	public void setAlias(String alias) {
		this.alias = alias;
	}

	public List<On> getOnList() {
		return onList;
	}
	public void setOnList(List<On> onList) {
		this.onList = onList;
	}

	public M getRequest() {
		return request;
	}
	public void setRequest(M request) {
		this.request = request;
	}
	public M getOn() {
		return on;
	}
	public void setOn(M on) {
		this.on = on;
	}

	public void setOuter(M outer) {
		this.outer = outer;
	}

	public M getOuter() {
		return outer;
	}

	public SQLConfig<T, M, L> getOuterConfig() {
		return outerConfig;
	}

	public void setOuterConfig(SQLConfig<T, M, L> outerConfig) {
		this.outerConfig = outerConfig;
	}

	public SQLConfig<T, M, L> getJoinConfig() {
		return joinConfig;
	}
	public void setJoinConfig(SQLConfig<T, M, L> joinConfig) {
		this.joinConfig = joinConfig;
	}
	public SQLConfig<T, M, L> getCacheConfig() {
		return cacheConfig;
	}
	public void setCacheConfig(SQLConfig<T, M, L> cacheConfig) {
		this.cacheConfig = cacheConfig;
	}
	public SQLConfig<T, M, L> getOnConfig() {
		return onConfig;
	}
	public void setOnConfig(SQLConfig<T, M, L> onConfig) {
		this.onConfig = onConfig;
	}

	public boolean isOne2One() {
		return ! isOne2Many();
	}
	public boolean isOne2Many() {
		return count != 1 || (path != null && path.contains("[]"));  // TODO 必须保证一对一时不会传包含 [] 的 path
	}

	public boolean isAppJoin() {
		return "@".equals(getJoinType());
	}
	public boolean isLeftJoin() {
		return "<".equals(getJoinType());
	}
	public boolean isRightJoin() {
		return ">".equals(getJoinType());
	}
	public boolean isCrossJoin() {
		return "*".equals(getJoinType());
	}
	public boolean isInnerJoin() {
		return "&".equals(getJoinType());
	}
	public boolean isFullJoin() {
		String jt = getJoinType();
		return "".equals(jt) || "|".equals(jt);
	}
	public boolean isOuterJoin() {
		return "!".equals(getJoinType());
	}
	public boolean isSideJoin() {
		return "^".equals(getJoinType());
	}
	public boolean isAntiJoin() {
		return "(".equals(getJoinType());
	}
	public boolean isForeignJoin() {
		return ")".equals(getJoinType());
	}
	public boolean isAsofJoin() {
		return "~".equals(getJoinType());
	}

	public boolean isLeftOrRightJoin() {
		String jt = getJoinType();
		return "<".equals(jt) || ">".equals(jt);
	}

	public boolean canCacheViceTable() {
		String jt = getJoinType();
		return "@".equals(jt) || "<".equals(jt) || ">".equals(jt) || "&".equals(jt) || "*".equals(jt) || ")".equals(jt);
		// 副表是按常规条件查询，缓存会导致其它同表同条件对象查询结果集为空		return ! isFullJoin();  // ! - OUTER, ( - FOREIGN 都需要缓存空副表数据，避免多余的查询
	}

	public boolean isSQLJoin() {
		return ! isAppJoin();
	}

	public static boolean isSQLJoin(Join<?, ?, ?> j) {
		return j != null && j.isSQLJoin();
	}

	public static boolean isAppJoin(Join<?, ?, ?> j) {
		return j != null && j.isAppJoin();
	}

	public static boolean isLeftOrRightJoin(Join<?, ?, ?> j) {
		return j != null && j.isLeftOrRightJoin();
	}



	public static class On {

		private String originKey;
		private String originValue;

		private Logic logic; // & | !
		private String relateType;  // "" - 一对一, "{}" - 一对多, "<>" - 多对一, > , <= , !=
		private String key;  // id
		private String targetTableKey;  // Moment:main
		private String targetTable;  // Moment
		private String targetAlias;  // main
		private String targetKey;  // userId

		public String getOriginKey() {
			return originKey;
		}
		public void setOriginKey(String originKey) {
			this.originKey = originKey;
		}
		public String getOriginValue() {
			return originValue;
		}
		public void setOriginValue(String originValue) {
			this.originValue = originValue;
		}


		public Logic getLogic() {
			return logic;
		}
		public void setLogic(Logic logic) {
			this.logic = logic;
		}
		public String getRelateType() {
			return relateType;
		}
		public void setRelateType(String relateType) {
			this.relateType = relateType;
		}

		public String getKey() {
			return key;
		}
		public void setKey(String key) {
			this.key = key;
		}

		public void setTargetTableKey(String targetTableKey) {
			this.targetTableKey = targetTableKey;
		}
		public String getTargetTableKey() {
			return targetTableKey;
		}

		public void setTargetTable(String targetTable) {
			this.targetTable = targetTable;
		}
		public String getTargetTable() {
			return targetTable;
		}
		public void setTargetAlias(String targetAlias) {
			this.targetAlias = targetAlias;
		}
		public String getTargetAlias() {
			return targetAlias;
		}
		public String getTargetKey() {
			return targetKey;
		}
		public void setTargetKey(String targetKey) {
			this.targetKey = targetKey;
		}


		public void setKeyAndType(String joinType, String table, @NotNull String originKey) throws Exception { //id, id@, id{}@, contactIdList<>@ ...
			if (originKey.endsWith("@")) {
				originKey = originKey.substring(0, originKey.length() - 1);
			}
			else { //TODO 暂时只允许 User.id = Moment.userId 字段关联，不允许 User.id = 82001 这种
				throw new IllegalArgumentException(joinType + "/.../" + table + "/" + originKey + " 中字符 " + originKey + " 不合法！join:'.../refKey'" + " 中 refKey 必须以 @ 结尾！");
			}

			String k;

			if (originKey.endsWith("{}")) {
				setRelateType("{}");
				k = originKey.substring(0, originKey.length() - 2);
			}
			else if (originKey.endsWith("<>")) {
				setRelateType("<>");
				k = originKey.substring(0, originKey.length() - 2);
			}
			else if (originKey.endsWith("$")) {  // key%$:"a" -> key LIKE '%a%'; key?%$:"a" -> key LIKE 'a%'; key_?$:"a" -> key LIKE '_a'; key_%$:"a" -> key LIKE '_a%'
				k = originKey.substring(0, originKey.length() - 1);
				char c = k.isEmpty() ? 0 : k.charAt(k.length() - 1);

				String t = "$";
				if (c == '%' || c == '_' || c == '?') {
					t = c + t;
					k = k.substring(0, k.length() - 1);

					char c2 = k.isEmpty() ? 0 : k.charAt(k.length() - 1);
					if (c2 == '%' || c2 == '_' || c2 == '?') {
						if (c2 == c) {
							throw new IllegalArgumentException(originKey + ":value 中字符 " + k + " 不合法！key$:value 中不允许 key 中有连续相同的占位符！");
						}

						t = c2 + t;
						k = k.substring(0, k.length() - 1);
					}
					else if (c == '?') {
						throw new IllegalArgumentException(originKey + ":value 中字符 " + originKey + " 不合法！key$:value 中不允许只有单独的 '?'，必须和 '%', '_' 之一配合使用 ！");
					}
				}

				setRelateType(t);
			}
			else if (originKey.endsWith("~")) {
				boolean ignoreCase = originKey.endsWith("*~");
				setRelateType(ignoreCase ? "*~" : "~");
				k = originKey.substring(0, originKey.length() - (ignoreCase ? 2 : 1));
			}
			else if (originKey.endsWith(">=")) {
				setRelateType(">=");
				k = originKey.substring(0, originKey.length() - 2);
			}
			else if (originKey.endsWith("<=")) {
				setRelateType("<=");
				k = originKey.substring(0, originKey.length() - 2);
			}
			else if (originKey.endsWith(">")) {
				setRelateType(">");
				k = originKey.substring(0, originKey.length() - 1);
			}
			else if (originKey.endsWith("<")) {
				setRelateType("<");
				k = originKey.substring(0, originKey.length() - 1);
			}
			else {
				setRelateType("");
				k = originKey;
			}

			if (k != null && (k.contains("&") || k.contains("|"))) {
				throw new UnsupportedOperationException(joinType + "/.../" + table + "/" + originKey + " 中字符 " + k + " 不合法！与或非逻辑符仅支持 '!' 非逻辑符 ！");
			}

			//TODO if (c3 == '-') { // 表示 key 和 value 顺序反过来: value LIKE key

			Logic l = new Logic(k);
			setLogic(l);

			if (StringUtil.isName(l.getKey()) == false) {
				throw new IllegalArgumentException(joinType + "/.../" + table + "/" + originKey + " 中字符 " + l.getKey() + " 不合法！必须符合字段命名格式！");
			}

			setKey(l.getKey());
		}

  }

}
