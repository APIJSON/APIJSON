/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm.exception;

/**不存在，可接受，内部吃掉
 * @author Lemon
 */
public class NotExistException extends Exception {
	private static final long serialVersionUID = 1L;
	
	public NotExistException(String msg) {
		super(msg);
	}
	public NotExistException(Throwable t) {
		super(t);
	}
	public NotExistException(String msg, Throwable t) {
		super(msg, t);
	}

}

