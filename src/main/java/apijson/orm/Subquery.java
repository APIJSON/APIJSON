/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.util.List;
import java.util.Map;

/**子查询 配置
 * @author Lemon
 */
public class Subquery<T, M extends Map<String, Object>, L extends List<Object>> {
	
	private String path; // []/0/User
	private String originKey; //id{}@
	private M originValue; // { "from": "Comment", "Comment": {...} }

	private String from; // Comment
	private String range; // ANY, ALL
	private String key; //id{}
	private SQLConfig<T, M, L> config;
	
	public String gainPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	
	public String gainOriginKey() {
		return originKey;
	}
	public void setOriginKey(String originKey) {
		this.originKey = originKey;
	}
	
	public M gainOriginValue() {
		return originValue;
	}
	public void setOriginValue(M originValue) {
		this.originValue = originValue;
	}
	
	public String gainFrom() {
		return from;
	}
	public void setFrom(String from) {
		this.from = from;
	}
	
	public String gainRange() {
		return range;
	}
	public void setRange(String range) {
		this.range = range;
	}
	
	public String gainKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	
	public SQLConfig<T, M, L> gainConfig() {
		return config;
	}
	public void setConfig(SQLConfig<T, M, L> config) {
		this.config = config;
	}

}
