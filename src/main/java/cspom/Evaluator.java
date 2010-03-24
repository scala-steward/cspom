package cspom;

import java.io.InputStreamReader;
import java.net.URL;
import java.util.logging.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public final class Evaluator {

	private static final Logger LOGGER = Logger.getLogger(Evaluator.class
			.getSimpleName());
	private static final ScriptEngine ENGINE;

	static {
		final ScriptEngine scriptEngine = new ScriptEngineManager()
				.getEngineByName("JavaScript");
		if (scriptEngine == null) {
			throw new IllegalStateException("Could not find JavaScript engine");
		}
		try {
			final URL url = Evaluator.class
					.getResource("predefinedFunctions.js");
			// if (url == null) {
			// url = new URL("file:predefinedFunctions.js");
			// }

			scriptEngine.eval(new InputStreamReader(url.openStream()));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		ENGINE = scriptEngine;

	}

	private Evaluator() {

	}

	public static boolean evaluate(final String expression)
			throws ScriptException {
		return (Boolean) ENGINE.eval(transform(expression));
	}

	private static String transform(final String expr) {
		return expr.replace("if(", "ite(");
	}
}
