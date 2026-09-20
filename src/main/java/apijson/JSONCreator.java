/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**JSON相关创建器
 * @author Lemon
 */
public interface JSONCreator<M extends Map<String, Object>, L extends List<Object>> {
	
	@NotNull
    M createJSONObject();

    @NotNull
    default M createJSONObject(String key, Object value) {
        M obj = createJSONObject();
        obj.put(key, value);
        return obj;
    }

    @NotNull
    default M createJSONObject(Map<? extends String, ?> map) {
        M obj = createJSONObject();
        if (map != null && ! map.isEmpty()) {
            obj.putAll(map);
        }
        return obj;
    }

	@NotNull
    L createJSONArray();

    @NotNull
    default L createJSONArray(Object obj){
        L arr = createJSONArray();
        arr.add(obj);
        return arr;
    }

    @NotNull
    default L createJSONArray(Collection<?> list){
        L arr = createJSONArray();
        if (list != null && ! list.isEmpty()) {
            arr.addAll(list);
        }
        return arr;
    }
}
