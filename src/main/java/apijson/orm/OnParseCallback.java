/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.util.List;
import java.util.Map;

/**
 * @author Lemon
 */
public abstract class OnParseCallback<T, M extends Map<String, Object>, L extends List<Object>> {


	/**
	 * @param key
	 * @param to
	 * @param ro
	 * @return false ? continue
	 * @throws Exception
	 */
	protected boolean onParse(String key, Object to, Object ro) throws Exception {
		return true;
	}

	/**
	 * @param key
	 * @param to
	 * @param ro
	 * @return
	 * @throws Exception
	 */
	protected Object onParseObject(String key, Object to, Object ro) throws Exception {
		return ro;
	}

	/**
	 * @param key
	 * @param tobj
	 * @param robj
	 * @return
	 * @throws Exception
	 */
	protected M onParseJSONObject(String key, M tobj, M robj) throws Exception {
		return robj;
	}

	/**
	 * @param key
	 * @param tarray
	 * @param rarray
	 * @return
	 * @throws Exception
	 */
	protected L onParseJSONArray(String key, L tarray, L rarray) throws Exception {
		return rarray;
	}

}
