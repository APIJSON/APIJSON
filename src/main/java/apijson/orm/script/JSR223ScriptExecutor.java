package apijson.orm.script;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import apijson.Log;
import apijson.orm.AbstractFunctionParser;

/**
 * JSR223 script engine的统一实现抽象类
 */
public abstract class JSR223ScriptExecutor<T, M extends Map<String, Object>, L extends List<Object>> implements ScriptExecutor<T, M, L> {
	private static final String TAG = "JSR223ScriptExecutor";

	protected ScriptEngine scriptEngine;

	private final Map<String, CompiledScript> compiledScriptMap = new ConcurrentHashMap<>();
	
	@Override
	public ScriptExecutor<T, M, L> init() {
		scriptEngine = createScriptEngine();
		return this;
	}

	protected ScriptEngine createScriptEngine() {
		String name = scriptEngineName();
		if ("nashorn".equalsIgnoreCase(name) || "javascript".equalsIgnoreCase(name)
				|| "js".equalsIgnoreCase(name) || "ecmascript".equalsIgnoreCase(name)) {
			try {
				Class<?> factoryClass = Class.forName("jdk.nashorn.api.scripting.NashornScriptEngineFactory");
				Class<?> filterClass = Class.forName("jdk.nashorn.api.scripting.ClassFilter");
				Object filter = java.lang.reflect.Proxy.newProxyInstance(
						filterClass.getClassLoader(),
						new Class<?>[]{filterClass},
						(proxy, method, methodArgs) -> isClassExposureAllowed((String) methodArgs[0]));
				Object factory = factoryClass.getDeclaredConstructor().newInstance();
				return (ScriptEngine) factoryClass.getMethod("getScriptEngine", filterClass).invoke(factory, filter);
			} catch (Throwable e) {
				Log.e(TAG, "create sandboxed Nashorn engine failed, falling back: " + e);
			}
		}
		return new ScriptEngineManager().getEngineByName(name);
	}

	protected boolean isClassExposureAllowed(String className) {
		return false;
	}

	protected abstract String scriptEngineName();
	
	protected abstract Object extendParameter(AbstractFunctionParser<T, M, L> parser, Map<String, Object> currentObject, String methodName, Object[] args);

	protected abstract boolean isLockScript(String methodName);

	protected String convertScript(String script) {
		return script;
	}

	@Override
	public void load(String name, String script) {
		try {
			CompiledScript compiledScript = ((Compilable) scriptEngine).compile(convertScript(script));
			compiledScriptMap.put(name, compiledScript);
		} catch (Exception e) {
			Log.e(TAG, "compile script failed: " + name, e);
		}

	}

	@Override
	public Object execute(AbstractFunctionParser<T, M, L> parser, Map<String, Object> currentObject, String methodName, Object[] args) throws Exception {
		CompiledScript compiledScript = compiledScriptMap.get(methodName);
		Bindings bindings = new SimpleBindings();
		// 往脚本上下文里放入元数据
		// 把 RequestMethod method, String tag, int version, @NotNull JSONMap request,
		// HttpSession session 等参数作为全局参数传进去供脚本使用
		
		// 加载扩展属性
		Object extendParameter = this.extendParameter(parser, currentObject, methodName, args);
		if(extendParameter != null) {
			bindings.put("extParam", extendParameter);
		}
		
		Map<String, Object> metaMap = new HashMap<>();
		metaMap.put("version", parser == null ? 0 : parser.getVersion());
		metaMap.put("tag", parser == null ? null : parser.getTag());
		metaMap.put("args", args);
		bindings.put("_meta", metaMap);
		return compiledScript.eval(bindings);
	}

	@Override
	public void cleanCache() {
		compiledScriptMap.clear();
	}
}
