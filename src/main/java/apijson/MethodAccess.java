/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static apijson.orm.AbstractVerifier.ADMIN;
import static apijson.orm.AbstractVerifier.CIRCLE;
import static apijson.orm.AbstractVerifier.CONTACT;
import static apijson.orm.AbstractVerifier.LOGIN;
import static apijson.orm.AbstractVerifier.OWNER;
import static apijson.orm.AbstractVerifier.UNKNOWN;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**请求方法权限，只允许某些角色通过对应方法访问
 * @author Lemon
 */
@Documented
@Retention(RUNTIME)
@Target(TYPE)
@Inherited
public @interface MethodAccess {

	/**@see {@link RequestMethod#GET}
	 * @return 该请求方法允许的角色 default {UNKNOWN, LOGIN, CONTACT, CIRCLE, OWNER, ADMIN};
	 */
	String[] GET() default {UNKNOWN, LOGIN, CONTACT, CIRCLE, OWNER, ADMIN};

	/**@see {@link RequestMethod#HEAD}
	 * @return 该请求方法允许的角色 default {UNKNOWN, LOGIN, CONTACT, CIRCLE, OWNER, ADMIN};
	 */
	String[] HEAD() default {UNKNOWN, LOGIN, CONTACT, CIRCLE, OWNER, ADMIN};

	/**@see {@link RequestMethod#GETS}
	 * @return 该请求方法允许的角色 default {LOGIN, CONTACT, CIRCLE, OWNER, ADMIN};
	 */
	String[] GETS() default {LOGIN, CONTACT, CIRCLE, OWNER, ADMIN};

	/**@see {@link RequestMethod#HEADS}
	 * @return 该请求方法允许的角色 default {LOGIN, CONTACT, CIRCLE, OWNER, ADMIN};
	 */
	String[] HEADS() default {LOGIN, CONTACT, CIRCLE, OWNER, ADMIN};

	/**@see {@link RequestMethod#POST}
	 * @return 该请求方法允许的角色  default {LOGIN, ADMIN};
	 */
	String[] POST() default {OWNER, ADMIN};

	/**@see {@link RequestMethod#PUT}
	 * @return 该请求方法允许的角色 default {OWNER, ADMIN};
	 */
	String[] PUT() default {OWNER, ADMIN};

	/**@see {@link RequestMethod#DELETE}
	 * @return 该请求方法允许的角色 default {OWNER, ADMIN};
	 */
	String[] DELETE() default {OWNER, ADMIN};

}
