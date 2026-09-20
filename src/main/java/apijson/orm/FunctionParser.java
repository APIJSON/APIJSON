/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.util.List;
import java.util.Map;

import apijson.*;


/**远程函数解析器
 * @author Lemon
 */
public interface FunctionParser<T, M extends Map<String, Object>, L extends List<Object>> {

	Object invoke(@NotNull String function, @NotNull M currentObject) throws Exception;
	Object invoke(@NotNull String function, @NotNull M currentObject, boolean containRaw) throws Exception;

	Parser<T, M, L> getParser();

	FunctionParser<T, M, L> setParser(Parser<T, M, L> parser);

	RequestMethod getMethod();
	FunctionParser<T, M, L> setMethod(RequestMethod method);

	String getTag();
	FunctionParser<T, M, L> setTag(String tag);

	int getVersion();
	FunctionParser<T, M, L> setVersion(int version);

	@NotNull 
	M getRequest();
	FunctionParser<T, M, L> setRequest(@NotNull M request);


	String getKey();
	FunctionParser<T, M, L> setKey(String key);
	
	String getParentPath();
	FunctionParser<T, M, L> setParentPath(String parentPath);

	String getCurrentName();
	FunctionParser<T, M, L> setCurrentName(String currentName);


	@NotNull
	M getCurrentObject();
	FunctionParser<T, M, L> setCurrentObject(@NotNull M currentObject);

}
