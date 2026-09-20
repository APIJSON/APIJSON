/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson;

import java.text.SimpleDateFormat;

/**测试用日志
 * @modifier Lemon
 */
public class Log {
	public static boolean DEBUG = false;
	public static final String VERSION = "10.0.0";

	public static final String LEVEL_VERBOSE = "VERBOSE";
	public static final String LEVEL_INFO = "INFO";
	public static final String LEVEL_DEBUG = "DEBUG";
	public static final String LEVEL_WARN = "WARN";
	public static final String LEVEL_ERROR = "ERROR";

	public static String LEVEL = LEVEL_WARN;

	public static final String KEY_SYSTEM_INFO_DIVIDER = "\n---|-----APIJSON SYSTEM INFO-----|---\n";

	public static final String OS_NAME;
	public static final String OS_VERSION;
	public static final String OS_ARCH;
	public static final String JAVA_VERSION;
	static {
		OS_NAME = System.getProperty("os.name");
		OS_VERSION = System.getProperty("os.version");
		OS_ARCH = System.getProperty("os.arch");
		JAVA_VERSION = System.getProperty("java.version");
	}


	//默认的时间格式
	public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");

	/**
	 * modify date format
	 * @param dateFormatString
	 */
	public static void setDateFormat(String dateFormatString) {
		DATE_FORMAT = new SimpleDateFormat(dateFormatString);
	}

	/**
	 * log info by level tag and msg
	 * @param TAG
	 * @param msg
	 * @param level
	 */
	public static void logInfo(String TAG, String msg, String level) {
		if (level == null || level.isEmpty()) {
			level = LEVEL;
		}

		if (level.equals(LEVEL_VERBOSE) || level.equals(LEVEL_INFO)) {
			System.out.println(DATE_FORMAT.format(System.currentTimeMillis()) + ": " + TAG + "." + level + ": " + msg);
		}
		else if (level.equals(LEVEL_DEBUG) || level.equals(LEVEL_ERROR) || level.equals(LEVEL_WARN)) {
			System.err.println(DATE_FORMAT.format(System.currentTimeMillis()) + ": " + TAG + "." + level + ": " + msg);
		}
	}

	/**
	 * @param TAG
	 * @param msg
	 */
	public static void d(String TAG, String msg) {
		if (DEBUG) {
			logInfo(TAG, msg, LEVEL_DEBUG);
		}
	}

	/**
	 * Forced debug
	 * @param TAG tag
	 * @param msg debug messages
	 */
	public static void fd(String TAG, String msg) {
		logInfo(TAG, msg, LEVEL_DEBUG);
	}

	/**
	 * Generate separation line
	 * @param pre prefix
	 * @param symbol used for generating separation line
	 * @param post postfix
	 */
	public static void sl(String pre, char symbol, String post) {
		System.err.println(pre + new String(new char[48]).replace('\u0000', symbol) + post);
	}

	/**
	 * @param TAG
	 * @param msg
	 */
	public static void v(String TAG, String msg) {
		if (DEBUG) {
			logInfo(TAG, msg, LEVEL_VERBOSE);
		}
	}

	/**
	 * @param TAG
	 * @param msg
	 */
	public static void i(String TAG, String msg) {
		if (DEBUG) {
			logInfo(TAG, msg, LEVEL_INFO);
		}
	}

	/**
	 * @param TAG
	 * @param msg
	 */
	public static void e(String TAG, String msg) {
		if (DEBUG) {
			logInfo(TAG, msg, LEVEL_ERROR);
		}
	}

	/**
	 * @param TAG
	 * @param msg
	 */
	public static void e(String TAG, String msg, Throwable e) {
		if (DEBUG) {
			if (e != null) {
				e.printStackTrace();
			}
			logInfo(TAG, msg, LEVEL_ERROR);
		}
	}

	/**
	 * @param TAG
	 * @param msg
	 */
	public static void w(String TAG, String msg) {
		if (DEBUG) {
			logInfo(TAG, msg, LEVEL_WARN);
		}
	}

}
