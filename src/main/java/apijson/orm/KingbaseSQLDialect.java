/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/

package apijson.orm;

/** Centralized SQL capabilities for KingbaseES compatibility modes. */
public enum KingbaseSQLDialect {
	NONE(null, null, false, false),
	LEGACY(SQLConfig.DATABASE_KINGBASE, "\"", false, false),
	MYSQL(SQLConfig.DATABASE_KINGBASE_MYSQL, "`", true, true),
	ORACLE(SQLConfig.DATABASE_KINGBASE_ORACLE, "\"", false, false),
	SQLSERVER(SQLConfig.DATABASE_KINGBASE_SQLSERVER, "\"", false, false);

	private final String database;
	private final String identifierQuote;
	private final boolean mysqlSyntax;
	private final boolean dmlLimit;

	KingbaseSQLDialect(String database, String identifierQuote, boolean mysqlSyntax, boolean dmlLimit) {
		this.database = database;
		this.identifierQuote = identifierQuote;
		this.mysqlSyntax = mysqlSyntax;
		this.dmlLimit = dmlLimit;
	}

	public static KingbaseSQLDialect from(String database) {
		if (database != null) {
			for (KingbaseSQLDialect dialect : values()) {
				if (database.equals(dialect.database)) {
					return dialect;
				}
			}
		}
		return NONE;
	}

	public boolean isKingbase() {
		return this != NONE;
	}

	public boolean isMySQL() {
		return mysqlSyntax;
	}

	public boolean isOracle() {
		return this == ORACLE;
	}

	public boolean isSQLServer() {
		return this == SQLSERVER;
	}

	public boolean supportsDmlLimit() {
		return dmlLimit;
	}

	/**
	 * SQL Server compatibility mode follows the SQL Server spelling of the
	 * OFFSET/FETCH clause. Other T-SQL-family databases in APIJSON retain their
	 * existing FETCH FIRST spelling.
	 */
	public String getSelectLimit(int offset, int count) {
		if (isSQLServer()) {
			return " OFFSET " + offset + " ROWS FETCH NEXT " + count + " ROWS ONLY";
		}
		return null;
	}

	/**
	 * KingbaseES exposes an explain statement returning the plan result set.
	 * SQL Server's session-level STATISTICS PROFILE switch is therefore neither
	 * required nor suitable for APIJSON's one-shot explain request. Oracle mode
	 * retains its compatible EXPLAIN PLAN FOR spelling.
	 */
	public String getExplainPrefix() {
		if (isOracle()) {
			return "EXPLAIN PLAN FOR ";
		}
		return isKingbase() ? "EXPLAIN " : null;
	}

	public String getIdentifierQuote() {
		return identifierQuote;
	}
}
