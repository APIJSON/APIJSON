/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm.exception;

import java.io.IOException;

/**
 * 给定的数据类型不被支持
 *
 * @author cnscoo
 */

public class UnsupportedDataTypeException extends IOException {
    private static final long serialVersionUID = 1L;

    public UnsupportedDataTypeException() {
        super();
    }

    public UnsupportedDataTypeException(String s) {
        super(s);
    }
}
