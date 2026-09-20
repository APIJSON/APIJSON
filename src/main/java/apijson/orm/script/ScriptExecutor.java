package apijson.orm.script;

import java.util.List;
import java.util.Map;

import apijson.orm.AbstractFunctionParser;

public interface ScriptExecutor<T, M extends Map<String, Object>, L extends List<Object>> {

    ScriptExecutor<T, M, L> init();

    void load(String name, String script);

    Object execute(AbstractFunctionParser<T, M, L> parser, Map<String, Object> currentObject, String methodName, Object[] args) throws Exception;

    void cleanCache();
    
}
