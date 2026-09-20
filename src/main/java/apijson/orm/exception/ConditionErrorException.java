/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm.exception;

/**条件错误
 * @author Lemon
 */
public class ConditionErrorException extends Exception {
	private static final long serialVersionUID = 1L;
	
	public ConditionErrorException(String msg) {
		super(msg);
	}
	public ConditionErrorException(Throwable t) {
		super(t);
	}
	public ConditionErrorException(String msg, Throwable t) {
		super(msg, t);
	}
	
}
