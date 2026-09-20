package apijson.orm.script;

import java.util.List;
import java.util.Map;

import apijson.orm.AbstractFunctionParser;

/**
 * JavaScript脚本语言的执行器实现
 */
public class JavaScriptExecutor<T, M extends Map<String, Object>, L extends List<Object>> extends JSR223ScriptExecutor<T, M, L> {

    @Override
    protected String scriptEngineName() {
        return "javascript";
    }

	@Override
	protected Object extendParameter(AbstractFunctionParser<T, M, L> parser, Map<String, Object> currentObject, String methodName, Object[] args) {
		return null;
	}

	@Override
	protected boolean isLockScript(String methodName) {
		return false;
	}

}
