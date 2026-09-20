/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson;

import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**通用字符串(String)相关类,为null时返回""
 * @author Lemon
 * @use StringUtil.
 */
public class StringUtil {
	private static final String TAG = "StringUtil";

	public StringUtil() {
	}

	public static final String UTF_8 = "utf-8";

	public static final String EMPTY = "无";
	public static final String UNKNOWN = "未知";
	public static final String UNLIMITED = "不限";

	public static final String I = "我";
	public static final String YOU = "你";
	public static final String HE = "他";
	public static final String SHE = "她";
	public static final String IT = "它";

	public static final String MALE = "男";
	public static final String FEMALE = "女";

	public static final String TODO = "未完成";
	public static final String DONE = "已完成";

	public static final String FAIL = "失败";
	public static final String SUCCESS = "成功";

	public static final String SUNDAY = "日";
	public static final String MONDAY = "一";
	public static final String TUESDAY = "二";
	public static final String WEDNESDAY = "三";
	public static final String THURSDAY = "四";
	public static final String FRIDAY = "五";
	public static final String SATURDAY = "六";

	public static final String YUAN = "元";


	private static String current = "";
	/**获取刚传入处理后的 string
	 * @must 上个影响 current 的方法 和 这个方法都应该在同一线程中，否则返回值可能不对
	 * @return
	 */
	public static String cur() {
		return get(current);
	}

	/**FIXME 改用 cur
	 * @must 上个影响currentString的方法 和 这个方法都应该在同一线程中，否则返回值可能不对
	 * @return
	 */
	@Deprecated
	public static String getCurrentString() {
		return cur();
	}

	//获取string,为null时返回"" <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	/**获取string,为null则返回""
	 * @param obj
	 * @return
	 */
	public static String get(Object obj) {
		return obj == null ? "" : obj.toString();
	}
	/**获取string,为null则返回""
	 * @param s
	 * @return
	 */
	public static String get(String s) {
		return s == null ? "" : s;
	}
	/**获取string,为null则返回""
	 * ignoreEmptyItem = false;
	 * split = ","
	 * @param arr
	 * @return {@link #get(Object[], boolean)}
	 */
	public static String get(Object[] arr) {
		return get(arr, false);
	}
	/**获取string,为null则返回""
	 * split = ","
	 * @param arr
	 * @param ignoreEmptyItem
	 * @return {@link #get(Object[], boolean)}
	 */
	public static String get(Object[] arr, boolean ignoreEmptyItem) {
		return get(arr, null, ignoreEmptyItem);
	}
	/**获取string,为null则返回""
	 * ignoreEmptyItem = false;
	 * @param arr
	 * @param split
	 * @return {@link #get(Object[], String, boolean)}
	 */
	public static String get(Object[] arr, String split) {
		return get(arr, split, false);
	}
	//CS304 Issue link: https://github.com/APIJSON/APIJSON/issues/182
	/**获取string,为null则返回""
	 * @param arr -the str arr given
	 * @param split -the token used to split
	 * @param ignoreEmptyItem -whether to ignore empty item or not
	 * @return {@link #get(Object[], String, boolean)}
	 * <p>Here we replace the simple "+" way of concatenating with Stringbuilder 's append</p>
	 */
	public static String get(Object[] arr, String split, boolean ignoreEmptyItem) {
		StringBuilder s = new StringBuilder("");
		if (arr != null) {
			if (split == null) {
				split = ",";
			}
			for (int i = 0; i < arr.length; i++) {
				if (ignoreEmptyItem && isEmpty(arr[i], true)) {
					continue;
				}
				s.append(((i > 0 ? split : "") + arr[i]));
			}
		}
		return get(s.toString());
	}

	/**FIXME 用 get 替代
	 * @param object
	 * @return
	 */
	@Deprecated
	public static String getString(Object object) {
		return object == null ? "" : object.toString();
	}
	/**FIXME 用 get 替代
	 * @param cs
	 * @return
	 */
	@Deprecated
	public static String getString(CharSequence cs) {
		return cs == null ? "" : cs.toString();
	}
	/**FIXME 用 get 替代
	 * @param s
	 * @return
	 */
	@Deprecated
	public static String getString(String s) {
		return s == null ? "" : s;
	}
	/**FIXME 用 get 替代
	 * ignoreEmptyItem = false;
	 * split = ","
	 * @param array
	 * @return {@link #get(Object[], boolean)}
	 */
	@Deprecated
	public static String getString(Object[] array) {
		return get(array, false);
	}
	/**FIXME 用 get 替代
	 * split = ","
	 * @param array
	 * @param ignoreEmptyItem
	 * @return {@link #get(Object[], boolean)}
	 */
	@Deprecated
	public static String getString(Object[] array, boolean ignoreEmptyItem) {
		return get(array, null, ignoreEmptyItem);
	}
	/**FIXME 用 get 替代
	 * ignoreEmptyItem = false;
	 * @param array
	 * @param split
	 * @return {@link #get(Object[], String, boolean)}
	 */
	@Deprecated
	public static String getString(Object[] array, String split) {
		return get(array, split, false);
	}
	//CS304 Issue link: https://github.com/APIJSON/APIJSON/issues/182
	/**FIXME 用 get 替代
	 * @param array -the str array given
	 * @param split -the token used to split
	 * @param ignoreEmptyItem -whether to ignore empty item or not
	 * @return {@link #get(Object[], String, boolean)}
	 * <p>Here we replace the simple "+" way of concatenating with Stringbuilder 's append</p>
	 */
	@Deprecated
	public static String getString(Object[] array, String split, boolean ignoreEmptyItem) {
		return get(array, split, ignoreEmptyItem);
	}

	//获取string,为null时返回"" >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	//获取去掉前后空格后的string<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**获取去掉前后空格后的string,为null则返回""
	 * @param obj
	 * @return
	 */
	public static String trim(Object obj) {
		return trim(get(obj));
	}
	/**获取去掉前后空格后的string,为null则返回""
	 * @param cs
	 * @return
	 */
	public static String trim(CharSequence cs) {
		return trim(get(cs));
	}
	/**获取去掉前后空格后的string,为null则返回""
	 * @param s
	 * @return
	 */
	public static String trim(String s) {
		return get(s).trim();
	}


	/**FIXME 用 trim 替代
	 * @param object
	 * @return
	 */
	@Deprecated
	public static String getTrimedString(Object object) {
		return trim(object);
	}
	/**FIXME 用 trim 替代
	 * @param cs
	 * @return
	 */
	@Deprecated
	public static String getTrimedString(CharSequence cs) {
		return trim(cs);
	}
	/**FIXME 用 trim 替代
	 * @param s
	 * @return
	 */
	@Deprecated
	public static String getTrimedString(String s) {
		return trim(s);
	}

	//获取去掉前后空格后的string>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//获取去掉所有空格后的string <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**获取去掉所有空格后的string,为null则返回""
	 * @param obj
	 * @return
	 */
	public static String noBlank(Object obj) {
		return noBlank(get(obj));
	}
	/**获取去掉所有空格后的string,为null则返回""
	 * @param cs
	 * @return
	 */
	public static String noBlank(CharSequence cs) {
		return noBlank(get(cs));
	}
	/**获取去掉所有空格后的string,为null则返回""
	 * @param s
	 * @return
	 */
	public static String noBlank(String s) {
		return get(s).replaceAll("\\s", "");
	}

	/**FIXME 用 noBlank 替代
	 * @param object
	 * @return
	 */
	@Deprecated
	public static String getNoBlankString(Object object) {
		return noBlank(object);
	}
	/**FIXME 用 noBlank 替代
	 * @param cs
	 * @return
	 */
	@Deprecated
	public static String getNoBlankString(CharSequence cs) {
		return noBlank(cs);
	}
	/**FIXME 用 noBlank 替代
	 * @param s
	 * @return
	 */
	@Deprecated
	public static String getNoBlankString(String s) {
		return noBlank(s);
	}

	//获取去掉所有空格后的string >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//获取string的长度<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	/**获取string的长度,为null则返回0
	 * @param object
	 * @param trim
	 * @return
	 */
	public static int length(Object object, boolean trim) {
		return length(get(object), trim);
	}
	/**获取string的长度,为null则返回0
	 * @param cs
	 * @param trim
	 * @return
	 */
	public static int length(CharSequence cs, boolean trim) {
		return length(get(cs), trim);
	}
	/**获取string的长度,为null则返回0
	 * @param s
	 * @param trim
	 * @return
	 */
	public static int length(String s, boolean trim) {
		s = trim ? trim(s) : s;
		return get(s).length();
	}


	/**FIXME 用 length 替代
	 * @param object
	 * @param trim
	 * @return
	 */
	@Deprecated
	public static int getLength(Object object, boolean trim) {
		return length(object, trim);
	}
	/**FIXME 用 length 替代
	 * @param cs
	 * @param trim
	 * @return
	 */
	@Deprecated
	public static int getLength(CharSequence cs, boolean trim) {
		return length(cs, trim);
	}
	/**FIXME 用 length 替代
	 * @param s
	 * @param trim
	 * @return
	 */
	@Deprecated
	public static int getLength(String s, boolean trim) {
		return length(s, trim);
	}

	//获取string的长度>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//判断字符是否为空 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	/**判断字符是否为空 trim = true
	 * @param obj
	 * @return
	 */
	public static boolean isEmpty(Object obj) {
		return isEmpty(obj, true);
	}
	/**判断字符是否为空
	 * @param obj
	 * @param trim
	 * @return
	 */
	public static boolean isEmpty(Object obj, boolean trim) {
		return isEmpty(get(obj), trim);
	}
	/**判断字符是否为空 trim = true
	 * @param cs
	 * @return
	 */
	public static boolean isEmpty(CharSequence cs) {
		return isEmpty(cs, true);
	}
	/**判断字符是否为空
	 * @param cs
	 * @param trim
	 * @return
	 */
	public static boolean isEmpty(CharSequence cs, boolean trim) {
		return isEmpty(get(cs), trim);
	}
	/**判断字符是否为空 trim = true
	 * @param s
	 * @return
	 */
	public static boolean isEmpty(String s) {
		return isEmpty(s, true);
	}
	/**判断字符是否为空
	 * @param s
	 * @param trim
	 * @return
	 */
	public static boolean isEmpty(String s, boolean trim) {
		//		Log.i(TAG, "isEmpty   s = " + s);
		if (s == null) {
			return true;
		}
		if (trim) {
			s = s.trim();
		}
		if (s.isEmpty()) {
			return true;
		}

		current = s;

		return false;
	}

	//判断字符是否为空 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	//判断字符是否非空 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	/**判断字符是否非空 trim = true
	 * @param obj
	 * @return
	 */
	public static boolean isNotEmpty(Object obj) {
		return ! isEmpty(obj);
	}
	/**判断字符是否非空
	 * @param obj
	 * @param trim
	 * @return
	 */
	public static boolean isNotEmpty(Object obj, boolean trim) {
		return ! isEmpty(obj, trim);
	}
	/**判断字符是否非空 trim = true
	 * @param cs
	 * @return
	 */
	public static boolean isNotEmpty(CharSequence cs) {
		return ! isEmpty(cs);
	}
	/**判断字符是否非空
	 * @param cs
	 * @param trim
	 * @return
	 */
	public static boolean isNotEmpty(CharSequence cs, boolean trim) {
		return ! isEmpty(cs, trim);
	}
	/**判断字符是否非空  trim = true
	 * @param s
	 * @return
	 */
	public static boolean isNotEmpty(String s) {
		return ! isEmpty(s);
	}
	/**判断字符是否非空
	 * @param s
	 * @param trim
	 * @return
	 */
	public static boolean isNotEmpty(String s, boolean trim) {
		return ! isEmpty(s, trim);
	}

	//判断字符是否非空 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//判断字符类型 <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	public static final Pattern PATTERN_NUMBER;
	public static final Pattern PATTERN_PHONE;
	public static final Pattern PATTERN_EMAIL;
	public static final Pattern PATTERN_ID_CARD;
	public static final Pattern PATTERN_NUM_OR_ALPHA;
	public static final Pattern PATTERN_ALPHA;
	public static final Pattern PATTERN_PASSWORD; //TODO
	public static final Pattern PATTERN_NAME;
	public static final Pattern PATTERN_ALPHA_BIG;
	public static final Pattern PATTERN_ALPHA_SMALL;
	public static final Pattern PATTERN_BRANCH_URL;
	static {
		PATTERN_NUMBER = Pattern.compile("^[0-9]+$");
		PATTERN_NUM_OR_ALPHA = Pattern.compile("^[0-9a-zA-Z_.:]+$");
		PATTERN_ALPHA = Pattern.compile("^[a-zA-Z]+$");
		PATTERN_ALPHA_BIG = Pattern.compile("^[A-Z]+$");
		PATTERN_ALPHA_SMALL = Pattern.compile("^[a-z]+$");
		PATTERN_NAME = Pattern.compile("^[0-9a-zA-Z_.:]+$");//已用55个中英字符测试通过
		//newest phone regex expression reference https://github.com/VincentSit/ChinaMobilePhoneNumberRegex
		PATTERN_PHONE = Pattern.compile("^1(?:3\\d{3}|5[^4\\D]\\d{2}|8\\d{3}|7(?:[0-35-9]\\d{2}|4(?:0\\d|1[0-2]|9\\d))|9[0-35-9]\\d{2}|6[2567]\\d{2}|4(?:(?:10|4[01])\\d{3}|[68]\\d{4}|[579]\\d{2}))\\d{6}$");
		PATTERN_EMAIL = Pattern.compile("^([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\\]?)$");
		PATTERN_ID_CARD = Pattern.compile("(^[1-9]\\d{5}(18|19|([23]\\d))\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{3}[0-9Xx]$)|(^[1-9]\\d{5}\\d{2}((0[1-9])|(10|11|12))(([0-2][1-9])|10|20|30|31)\\d{2}$)");
		PATTERN_PASSWORD = Pattern.compile("^[0-9a-zA-Z]+$");
		PATTERN_BRANCH_URL = Pattern.compile("^[0-9a-zA-Z-_/]+$");
	}

	/**判断手机格式是否正确
	 * @param phone
	 * @return
	 */
	public static boolean isPhone(String phone) {
		if (isNotEmpty(phone, true) == false) {
			return false;
		}

		current = phone;
		return PATTERN_PHONE.matcher(phone).matches();
	}
	/**判断手机格式是否正确
	 * @param s
	 * @return
	 */
	public static boolean isPassword(String s) {
		return length(s, false) >= 6 && PATTERN_PASSWORD.matcher(s).matches();
	}
	/**判断是否全是数字密码
	 * @param s
	 * @return
	 */
	public static boolean isNumberPassword(String s) {
		return length(s, false) == 6 && isNumber(s);
	}
	/**判断email格式是否正确
	 * @param email
	 * @return
	 */
	public static boolean isEmail(String email) {
		if (isEmpty(email, true)) {
			return false;
		}

		current = email;
		return PATTERN_EMAIL.matcher(email).matches();
	}


	/**判断是否全是验证码
	 * @param s
	 * @return
	 */
	public static boolean isVerify(String s) {
		return length(s, false) >= 4 && isNumber(s);
	}
	/**判断是否全是数字
	 * @param s
	 * @return
	 */
	public static boolean isNumber(String s) {
		if (isEmpty(s, true)) {
			return false;
		}

		current = s;
		return PATTERN_NUMBER.matcher(s).matches();
	}
	/**判断是否全是字母
	 * @param s
	 * @return
	 */
	public static boolean isAlpha(String s) {
		if (isEmpty(s, true)) {
			return false;
		}

		current = s;
		return PATTERN_ALPHA.matcher(s).matches();
	}
	/**判断是否全是数字或字母
	 * @param s
	 * @return
	 */
	public static boolean isNumberOrAlpha(String s) {
		return isNumber(s) || isAlpha(s);
	}

	/**判断是否全是数字或字母
	 * @param s
	 * @return
	 */
	public static boolean isCombineOfNumOrAlpha(String s) {
		if (isEmpty(s, true)) {
			return false;
		}

		current = s;
		return PATTERN_NUM_OR_ALPHA.matcher(s).matches();
	}

	/**判断是否为代码名称，只能包含字母，数字或下划线
	 * @param s
	 * @return
	 */
	public static boolean isName(String s) {
		if (s == null || s.isEmpty()) {
			return false;
		}
		
		String first = s.substring(0, 1);
		if ("_".equals(first) == false && PATTERN_ALPHA.matcher(first).matches() == false) {
			return false;
		}
		
		return s.length() <= 1 ? true : PATTERN_NAME.matcher(s.substring(1)).matches();
	}
	/**判断是否为首字母大写的代码名称
	 * @param s
	 * @return
	 */
	public static boolean isBigName(String s) {
		if (s == null || s.isEmpty() || PATTERN_ALPHA_BIG.matcher(s.substring(0, 1)).matches() == false) {
			return false;
		}
		return s.length() <= 1 ? true : PATTERN_NAME.matcher(s.substring(1)).matches();
	}
	/**判断是否为首字母小写的代码名称
	 * @param s
	 * @return
	 */
	public static boolean isSmallName(String s) {
		if (s == null || s.isEmpty() || PATTERN_ALPHA_SMALL.matcher(s.substring(0, 1)).matches() == false) {
			return false;
		}
		return s.length() <= 1 ? true : PATTERN_NAME.matcher(s.substring(1)).matches();
	}


	/**判断字符类型是否是身份证号
	 * @param number
	 * @return
	 */
	public static boolean isIDCard(String number) {
		if (isCombineOfNumOrAlpha(number) == false) {
			return false;
		}
		number = get(number);
		if (number.length() == 15) {
			Log.i(TAG, "isIDCard number.length() == 15 old IDCard");
			current = number;
			return true;
		}
		if (number.length() == 18) {
			current = number;
			return true;
		}

		return false;
	}

	public static final String HTTP = "http";
	public static final String URL_PREFIX = "http://";
	public static final String URL_PREFIXs = "https://";
	/**判断字符类型是否是网址
	 * @param url
	 * @return
	 */
	public static boolean isUrl(String url) {
		if (isNotEmpty(url, true) == false) {
			return false;
		} 
		if (! url.startsWith(URL_PREFIX) && ! url.startsWith(URL_PREFIXs)) {
			return false;
		}

		current = url;
		return true;
	}

	public static boolean isBranchUrl(String branchUrl) {
		if (isEmpty(branchUrl, false)) {
			return false;
		}
		
		return PATTERN_BRANCH_URL.matcher(branchUrl).matches();
	}


	public static final String FILE_PATH_PREFIX = "file://";
	/**判断文件路径是否存在
	 * @param path
	 * @return
	 */
	public static boolean isFilePathExist(String path) {
		return StringUtil.isFilePath(path) && new File(path).exists();
	}

	public static final String SEPARATOR = "/";
	/**判断是否为路径
	 * @param path
	 * @return
	 */
	public static boolean isPath(String path) {
		return StringUtil.isNotEmpty(path, true) && path.contains(SEPARATOR)
				&& path.contains(SEPARATOR + SEPARATOR) == false && path.endsWith(SEPARATOR) == false;
	}

	/**判断字符类型是否是路径
	 * @param path
	 * @return
	 */
	public static boolean isFilePath(String path) {
		if (isNotEmpty(path, true) == false) {
			return false;
		}

		if (! path.contains(".") || path.endsWith(".")) {
			return false;
		}

		current = path;

		return true;
	}

	//判断字符类型 >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//提取特殊字符<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	/**去掉string内所有非数字类型字符
	 * @param object
	 * @return
	 */
	public static String getNumber(Object object) {
		return getNumber(get(object));
	}
	/**去掉string内所有非数字类型字符
	 * @param cs
	 * @return
	 */
	public static String getNumber(CharSequence cs) {
		return getNumber(get(cs));
	}
	/**去掉string内所有非数字类型字符
	 * @param s
	 * @return
	 */
	public static String getNumber(String s) {
		return getNumber(s, false);
	}

	//CS304 Issue link: https://github.com/APIJSON/APIJSON/issues/182
	/**去掉string内所有非数字类型字符
	 * @param s -string passed in
	 * @param onlyStart 中间有非数字时只获取前面的数字
	 * @return limit String
	 * <p>Here we replace the simple "+" way of concatenating with Stringbuilder 's append</p>
	 */
	public static String getNumber(String s, boolean onlyStart) {
		if (isEmpty(s, true)) {
			return "";
		}

		StringBuilder numberString = new StringBuilder("");
		String single;
		for (int i = 0; i < s.length(); i++) {
			single = s.substring(i, i + 1);
			if (isNumber(single)) {
				numberString.append(single);
			} else {
				if (onlyStart) {
					return numberString.toString();
				}
			}
		}
		return numberString.toString();
	}

	//提取特殊字符>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	//校正（自动补全等）字符串<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	/**获取网址，自动补全
	 * @param url
	 * @return
	 */
	public static String getCorrectUrl(String url) {
		Log.i(TAG, "getCorrectUrl : \n" + url);
		if (isNotEmpty(url, true) == false) {
			return "";
		}

		//		if (! url.endsWith("/") && ! url.endsWith(".html")) {
		//			url = url + "/";
		//		}

		if (isUrl(url) == false) {
			return URL_PREFIX + url;
		}
		return url;
	}

	/**获取去掉所有 空格 、"-" 、"+86" 后的phone
	 * @param phone
	 * @return
	 */
	public static String getCorrectPhone(String phone) {
		if (isNotEmpty(phone, true) == false) {
			return "";
		}

		phone = noBlank(phone);
		phone = phone.replaceAll("-", "");
		if (phone.startsWith("+86")) {
			phone = phone.substring(3);
		}
		return phone;
	}


	/**获取邮箱，自动补全
	 * @param email
	 * @return
	 */
	public static String getCorrectEmail(String email) {
		if (isNotEmpty(email, true) == false) {
			return "";
		}

		email = noBlank(email);
		if (isEmail(email) == false && ! email.endsWith(".com")) {
			email += ".com";
		}

		return email;
	}


	public static final int PRICE_FORMAT_DEFAULT = 0;
	public static final int PRICE_FORMAT_PREFIX = 1;
	public static final int PRICE_FORMAT_SUFFIX = 2;
	public static final int PRICE_FORMAT_PREFIX_WITH_BLANK = 3;
	public static final int PRICE_FORMAT_SUFFIX_WITH_BLANK = 4;
	public static final String[] PRICE_FORMATS = {
			"", "￥", "元", "￥ ", " 元"
	};

	/**获取价格，保留两位小数
	 * @param price
	 * @return
	 */
	public static String getPrice(String price) {
		return getPrice(price, PRICE_FORMAT_DEFAULT);
	}
	//CS304 Issue link: https://github.com/APIJSON/APIJSON/issues/182
	/**获取价格，保留两位小数
	 * @param price -price passed in
	 * @param formatType 添加单位（元）
	 * @return limit String
	 * <p>Here we replace the simple "+" way of concatenating with Stringbuilder 's append</p>
	 */
	public static String getPrice(String price, int formatType) {
		if (isNotEmpty(price, true) == false) {
			return getPrice(0, formatType);
		}

		//单独写到getCorrectPrice? <<<<<<<<<<<<<<<<<<<<<<
		String correctPrice;
		StringBuilder correctPriceBuilder = new StringBuilder("");
		String s;
		for (int i = 0; i < price.length(); i++) {
			s = price.substring(i, i + 1);
			if (".".equals(s) || isNumber(s)) {
				correctPriceBuilder.append(s);
			}
		}
		correctPrice = correctPriceBuilder.toString();
		//单独写到getCorrectPrice? >>>>>>>>>>>>>>>>>>>>>>

		Log.i(TAG, "getPrice  <<<<<<<<<<<<<<<<<< correctPrice =  " + correctPrice);
		if (correctPrice.contains(".")) {
			//			if (correctPrice.startsWith(".")) {
			//				correctPrice = 0 + correctPrice;
			//			}
			if (correctPrice.endsWith(".")) {
				correctPrice = correctPrice.replaceAll(".", "");
			}
		}

		Log.i(TAG, "getPrice correctPrice =  " + correctPrice + " >>>>>>>>>>>>>>>>");
		return isNotEmpty(correctPrice, true) ? getPrice(new BigDecimal(0 + correctPrice), formatType) : getPrice(0, formatType);
	}
	/**获取价格，保留两位小数
	 * @param price
	 * @return
	 */
	public static String getPrice(BigDecimal price) {
		return getPrice(price, PRICE_FORMAT_DEFAULT);
	}
	/**获取价格，保留两位小数
	 * @param price
	 * @return
	 */
	public static String getPrice(double price) {
		return getPrice(price, PRICE_FORMAT_DEFAULT);
	}
	/**获取价格，保留两位小数
	 * @param price
	 * @param formatType 添加单位（元）
	 * @return
	 */
	public static String getPrice(BigDecimal price, int formatType) {
		return getPrice(price == null ? 0 : price.doubleValue(), formatType);
	}
	/**获取价格，保留两位小数
	 * @param price
	 * @param formatType 添加单位（元）
	 * @return
	 */
	public static String getPrice(double price, int formatType) {
		String s = new DecimalFormat("#########0.00").format(price);
		switch (formatType) {
		case PRICE_FORMAT_PREFIX:
			return PRICE_FORMATS[PRICE_FORMAT_PREFIX] + s;
		case PRICE_FORMAT_SUFFIX:
			return s + PRICE_FORMATS[PRICE_FORMAT_SUFFIX];
		case PRICE_FORMAT_PREFIX_WITH_BLANK:
			return PRICE_FORMATS[PRICE_FORMAT_PREFIX_WITH_BLANK] + s;
		case PRICE_FORMAT_SUFFIX_WITH_BLANK:
			return s + PRICE_FORMATS[PRICE_FORMAT_SUFFIX_WITH_BLANK];
		default:
			return s;
		}
	}

	public static String join(String[] arr) {
		return join(arr);
	}
	/** 数组以指定分隔s拼接
	 * @param arr
	 * @param s
	 * @return
	 */
	public static String join(String[] arr, String s) {
		if (s == null) {
			s = ",";
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < arr.length; i++) {
			sb.append(arr[i]);
			if (i < arr.length-1) {
				sb.append(s);
			}
		}

		return sb.toString();
	}
	/**分割路径
	 * @param path
	 * @return
	 */
	public static String[] splitPath(String path) {
		if (StringUtil.isNotEmpty(path, true) == false) {
			return null;
		}
		return isPath(path) ? split(path, SEPARATOR) : new String[] {path};
	}
	/**将s分割成String[]
	 * @param s
	 * @return
	 */
	public static String[] split(String s) {
		return split(s, null);
	}
	/**将s用split分割成String[]
	 * trim = true;
	 * @param s
	 * @param split
	 * @return
	 */
	public static String[] split(String s, String split) {
		return split(s, split, true);
	}
	/**将s用split分割成String[]
	 * @param s
	 * @param trim 去掉前后两端的split
	 * @return
	 */
	public static String[] split(String s, boolean trim) {
		return split(s, null, trim);
	}
	/**将s用split分割成String[]
	 * @param s
	 * @param split
	 * @param trim 去掉前后两端的split
	 * @return
	 */
	public static String[] split(String s, String split, boolean trim) {
		s = get(s);
		if (s.isEmpty()) {
			return null;
		}
		if (isEmpty(split, false)) {
			split = ",";
		}
		if (trim) {
			while (s.startsWith(split)) {
				s = s.substring(split.length());
			}
			while (s.endsWith(split)) {
				s = s.substring(0, s.length() - split.length());
			}
		}
		return s.contains(split) ? s.split(split) : new String[]{s};
	}

	/**
	 * @param key
	 * @param suffix
	 * @return key + suffix，第一个字母小写
	 */
	public static String addSuffix(String key, String suffix) {
		key = noBlank(key);
		if (key.isEmpty()) {
			return firstCase(suffix);
		}
		return firstCase(key) + firstCase(suffix, true);
	}
	/**
	 * @param key
	 */
	public static String firstCase(String key) {
		return firstCase(key, false);
	}
	/**
	 * @param key
	 * @param upper
	 * @return
	 */
	public static String firstCase(String key, boolean upper) {
		key = get(key);
		if (key.isEmpty()) {
			return "";
		}

		String first = key.substring(0, 1);
		key = (upper ? first.toUpperCase() : first.toLowerCase()) + key.substring(1, key.length());

		return key;
	}

	/**全部大写
	 * @param s
	 * @return
	 */
	public static String toUpperCase(String s) {
		return toUpperCase(s, false);
	}
	/**全部大写
	 * @param s
	 * @param trim
	 * @return
	 */
	public static String toUpperCase(String s, boolean trim) {
		s = trim ? trim(s) : get(s);
		return s.toUpperCase();
	}
	/**全部小写
	 * @param s
	 * @return
	 */
	public static String toLowerCase(String s) {
		return toLowerCase(s, false);
	}
	/**全部小写
	 * @param s
	 * @return
	 */
	public static String toLowerCase(String s, boolean trim) {
		s = trim ? trim(s) : get(s);
		return s.toLowerCase();
	}

	public static String concat(String left, String right) {
		return concat(left, right, null);
	}
	public static String concat(String left, String right, String split) {
		return concat(left, right, split, true);
	}
	public static String concat(String left, String right, boolean trim) {
		return concat(left, right, null, trim);
	}
	public static String concat(String left, String right, String split, boolean trim) {
		if (isEmpty(left, trim)) {
			return right;
		}
		if (isEmpty(right, trim)) {
			return left;
		}

		if (split == null) {
			split = ",";
		}
		return left + split + right;
	}

	//校正（自动补全等）字符串>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	public static boolean equals(Object s1, Object s2) {
		return Objects.equals(s1, s2);
	}
	public static boolean equalsIgnoreCase(String s1, String s2) {
		if (s1 == s2) {
			return true;
		}
		if (s1 == null || s2 == null) {
			return false;
		}
		return s1.equalsIgnoreCase(s2);
	}

}
