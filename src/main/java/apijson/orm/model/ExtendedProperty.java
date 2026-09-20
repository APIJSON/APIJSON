/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm.model;

import apijson.MethodAccess;

import static apijson.orm.AbstractVerifier.ADMIN;
import static apijson.orm.AbstractVerifier.LOGIN;

/**扩展属性，SQL Server 转用
 * @author Lemon
 */
@MethodAccess(GET = {LOGIN, ADMIN}, HEAD = {LOGIN, ADMIN}, POST = {}, PUT = {}, DELETE = {})
public class ExtendedProperty {
	public static final String TAG = "ExtendedProperty";
	public static final String TABLE_NAME = "extended_properties";

}
