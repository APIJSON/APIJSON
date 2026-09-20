/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm.exception;

/**超出范围
 * @author Lemon
 */
public class OutOfRangeException extends Exception {
	private static final long serialVersionUID = 1L;
	
	public OutOfRangeException(String msg) {
		super(msg);
	}
	public OutOfRangeException(Throwable t) {
		super(t);
	}
	public OutOfRangeException(String msg, Throwable t) {
		super(msg, t);
	}
	
}
