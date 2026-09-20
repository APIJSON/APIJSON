/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm.model;

import apijson.MethodAccess;

import static apijson.orm.AbstractVerifier.ADMIN;
import static apijson.orm.AbstractVerifier.LOGIN;

/**PostgreSQL 字段属性
 * @author Lemon
 */
@MethodAccess(GET = {LOGIN, ADMIN}, HEAD = {LOGIN, ADMIN}, POST = {}, PUT = {}, DELETE = {})
public class PgAttribute {
	public static final String TAG = "PgAttribute";
	public static final String TABLE_NAME = "pg_attribute";
	
}