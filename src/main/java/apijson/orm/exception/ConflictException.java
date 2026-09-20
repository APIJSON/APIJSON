/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm.exception;

/**冲突
 * @author Lemon
 */
public class ConflictException extends Exception {
	private static final long serialVersionUID = 1L;
	
	public ConflictException(String msg) {
		super(msg);
	}
	public ConflictException(Throwable t) {
		super(t);
	}
	public ConflictException(String msg, Throwable t) {
		super(msg, t);
	}

}
