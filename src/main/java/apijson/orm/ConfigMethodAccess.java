/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


//package apijson.orm;
//
//import apijson.MethodAccess;
//
//import java.lang.annotation.Documented;
//import java.lang.annotation.Inherited;
//import java.lang.annotation.Retention;
//import java.lang.annotation.Target;
//
//import static apijson.orm.AbstractVerifier.*;
//import static java.lang.annotation.ElementType.TYPE;
//import static java.lang.annotation.RetentionPolicy.RUNTIME;
//
///**配置表的请求方法权限，只允许某些角色通过对应方法访问
// 不能直接查到 MethodAccess，需要往上递归查找
// * @author Lemon
// */
//@Documented
//@Retention(RUNTIME)
//@Target(TYPE)
//@Inherited
//@MethodAccess(GET = {LOGIN, ADMIN}, HEAD = {LOGIN, ADMIN}, POST = {}, PUT = {}, DELETE = {})
//public @interface ConfigMethodAccess {
//}
