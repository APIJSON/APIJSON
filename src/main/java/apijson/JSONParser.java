/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson;

import java.util.List;
import java.util.Map;

/**JSON 相关解析器
 * @author Lemon
 */
public interface JSONParser<M extends Map<String, Object>, L extends List<Object>> extends JSONCreator<M, L> {

    Object parse(Object json);

    M parseObject(Object json);

    <T> T parseObject(Object json, Class<T> clazz);

    L parseArray(Object json);

    <T> List<T> parseArray(Object json, Class<T> clazz);

    default String format(Object obj) {
        return toJSONString(obj, true);
    }
    default String toJSONString(Object obj) {
        return toJSONString(obj, false);
    }
    String toJSONString(Object obj, boolean format);
}
