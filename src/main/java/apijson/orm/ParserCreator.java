/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import apijson.NotNull;

import java.util.List;
import java.util.Map;

/**SQL相关创建器
 * @author Lemon
 */
public interface ParserCreator<T, M extends Map<String, Object>, L extends List<Object>> {
	
	@NotNull
	Parser<T, M, L> createParser();
	
	@NotNull
	FunctionParser<T, M, L> createFunctionParser();
}
