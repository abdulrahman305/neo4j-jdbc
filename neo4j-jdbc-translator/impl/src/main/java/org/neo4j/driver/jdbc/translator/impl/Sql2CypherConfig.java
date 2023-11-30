/*
 * Copyright (c) 2023 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.driver.jdbc.translator.impl;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jooq.SQLDialect;
import org.jooq.conf.ParseNameCase;
import org.jooq.conf.RenderNameCase;

/**
 * Configuration for the {@link Sql2Cypher}, use this to configure parsing and rendering
 * settings as well as table to node mappings.
 *
 * @author Michael Hunger
 * @author Michael J. Simons
 */
public final class Sql2CypherConfig {

	private static final Sql2CypherConfig DEFAULT_CONFIG = Sql2CypherConfig.builder().build();

	/**
	 * Derives a configuration for {@code Sql2Cypher} based from the properties given.
	 * @param config will be searched for values under keys prefixed with {@code s2c}.
	 * @return a new configuration object or the default config if there are no matching
	 * properties.
	 */
	public static Sql2CypherConfig of(Map<String, String> config) {

		if (config == null || config.isEmpty()) {
			return defaultConfig();
		}

		var prefix = Pattern.compile("s2c\\.(.+)");
		var dashWord = Pattern.compile("-(\\w)");

		var builder = builder();
		var relevantProperties = config.keySet().stream().map(prefix::matcher).filter(Matcher::matches).toList();
		if (relevantProperties.isEmpty()) {
			return defaultConfig();
		}
		boolean customConfig = false;
		for (Matcher m : relevantProperties) {
			var v = config.get(m.group());
			var k = dashWord.matcher(m.group(1)).replaceAll(mr -> mr.group(1).toUpperCase(Locale.ROOT));
			customConfig = null != switch (k) {
				case "parseNameCase" -> builder.withParseNameCase(ParseNameCase.fromValue(v));
				case "renderNameCase" -> builder.withRenderNameCase(RenderNameCase.fromValue(v));
				case "jooqDiagnosticLogging" -> builder.withJooqDiagnosticLogging(Boolean.parseBoolean(v));
				case "tableToLabelMappings" -> builder.withTableToLabelMappings(buildMap(v));
				case "joinColumnsToTypeMappings" -> builder.withJoinColumnsToTypeMappings(buildMap(v));
				case "sqlDialect" -> builder.withSqlDialect(SQLDialect.valueOf(v));
				case "prettyPrint" -> builder.withPrettyPrint(Boolean.parseBoolean(v));
				case "alwaysEscapeNames" -> builder.withAlwaysEscapeNames(Boolean.parseBoolean(v));
				case "parseNamedParamPrefix" -> builder.withParseNamedParamPrefix(v);
				default -> {
					Sql2Cypher.LOGGER.log(Level.WARNING, "Unknown config option {0}", m.group());
					yield null;
				}
			};
		}

		return customConfig ? builder.build() : defaultConfig();
	}

	/**
	 * Builds a map from a string. String must be in {@code k1:v1;k2:v2} format.
	 * @param source the source of the map
	 * @return a new, unmodifiable map
	 */
	static Map<String, String> buildMap(String source) {

		return Arrays.stream(source.split(";"))
			.map(String::trim)
			.map((s) -> s.split(":"))
			.collect(Collectors.toUnmodifiableMap((a) -> a[0], (a) -> a[1]));
	}

	/**
	 * A builder for creating new {@link Sql2CypherConfig configuration objects}.
	 * @return a new builder for creating a new configuration from scratch.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Provides access to the default configuration.
	 * @return the default configuration ready to use.
	 */
	public static Sql2CypherConfig defaultConfig() {

		return DEFAULT_CONFIG;
	}

	private final ParseNameCase parseNameCase;

	private final RenderNameCase renderNameCase;

	private final boolean jooqDiagnosticLogging;

	private final Map<String, String> tableToLabelMappings;

	private final Map<String, String> joinColumnsToTypeMappings;

	private final SQLDialect sqlDialect;

	private final boolean prettyPrint;

	private final Boolean alwaysEscapeNames;

	private final String parseNamedParamPrefix;

	private Sql2CypherConfig(Builder builder) {

		this.parseNameCase = builder.parseNameCase;
		this.renderNameCase = builder.renderNameCase;
		this.jooqDiagnosticLogging = builder.jooqDiagnosticLogging;
		this.tableToLabelMappings = builder.tableToLabelMappings;
		this.joinColumnsToTypeMappings = builder.joinColumnsToTypeMappings;
		this.sqlDialect = builder.sqlDialect;
		this.prettyPrint = builder.prettyPrint;
		this.alwaysEscapeNames = builder.alwaysEscapeNames();
		this.parseNamedParamPrefix = builder.parseNamedParamPrefix;
	}

	/**
	 * Allows modifying this configuration.
	 * @return builder with all settings from this instance
	 */
	public Builder modify() {
		return new Builder(this);
	}

	public ParseNameCase getParseNameCase() {
		return this.parseNameCase;
	}

	public RenderNameCase getRenderNameCase() {
		return this.renderNameCase;
	}

	public boolean isJooqDiagnosticLogging() {
		return this.jooqDiagnosticLogging;
	}

	public Map<String, String> getTableToLabelMappings() {
		return this.tableToLabelMappings;
	}

	public Map<String, String> getJoinColumnsToTypeMappings() {
		return this.joinColumnsToTypeMappings;
	}

	public SQLDialect getSqlDialect() {
		return this.sqlDialect;
	}

	public boolean isPrettyPrint() {
		return this.prettyPrint;
	}

	public boolean isAlwaysEscapeNames() {
		return this.alwaysEscapeNames;
	}

	public String getParseNamedParamPrefix() {
		return this.parseNamedParamPrefix;
	}

	/**
	 * A builder to create new instances of {@link Sql2CypherConfig configurations}.
	 */
	public static final class Builder {

		private ParseNameCase parseNameCase;

		private RenderNameCase renderNameCase;

		private boolean jooqDiagnosticLogging;

		private Map<String, String> tableToLabelMappings;

		private Map<String, String> joinColumnsToTypeMappings;

		private SQLDialect sqlDialect;

		private boolean prettyPrint;

		private String parseNamedParamPrefix;

		private Boolean alwaysEscapeNames;

		private Builder() {
			this(ParseNameCase.AS_IS, RenderNameCase.AS_IS, false, Map.of(), Map.of(), SQLDialect.DEFAULT, true, null,
					null);
		}

		private Builder(Sql2CypherConfig config) {
			this(config.parseNameCase, config.renderNameCase, config.jooqDiagnosticLogging, config.tableToLabelMappings,
					config.joinColumnsToTypeMappings, config.sqlDialect, config.prettyPrint, config.alwaysEscapeNames,
					config.parseNamedParamPrefix);
		}

		private Builder(ParseNameCase parseNameCase, RenderNameCase renderNameCase, boolean jooqDiagnosticLogging,
				Map<String, String> tableToLabelMappings, Map<String, String> joinColumnsToTypeMappings,
				SQLDialect sqlDialect, boolean prettyPrint, Boolean alwaysEscapeNames, String parseNamedParamPrefix) {
			this.parseNameCase = parseNameCase;
			this.renderNameCase = renderNameCase;
			this.jooqDiagnosticLogging = jooqDiagnosticLogging;
			this.tableToLabelMappings = tableToLabelMappings;
			this.joinColumnsToTypeMappings = joinColumnsToTypeMappings;
			this.sqlDialect = sqlDialect;
			this.prettyPrint = prettyPrint;
			this.alwaysEscapeNames = alwaysEscapeNames;
			this.parseNamedParamPrefix = parseNamedParamPrefix;
		}

		/**
		 * Configures how names should be parsed.
		 * @param newParseNameCase the new configuration
		 * @return this builder
		 */
		public Builder withParseNameCase(ParseNameCase newParseNameCase) {
			this.parseNameCase = Objects.requireNonNull(newParseNameCase);
			return this;
		}

		/**
		 * Configures how SQL names should be parsed.
		 * @param newRenderNameCase the new configuration
		 * @return this builder
		 */
		public Builder withRenderNameCase(RenderNameCase newRenderNameCase) {
			this.renderNameCase = Objects.requireNonNull(newRenderNameCase);
			return this;
		}

		/**
		 * Enables diagnostic logging for jOOQ.
		 * @param enabled set to {@literal true} to enable diagnostic logging on the jOOQ
		 * side of things
		 * @return this builder
		 */
		public Builder withJooqDiagnosticLogging(boolean enabled) {
			this.jooqDiagnosticLogging = enabled;
			return this;
		}

		/**
		 * Applies new table mappings.
		 * @param newTableToLabelMappings the new mappings
		 * @return this builder
		 */
		public Builder withTableToLabelMappings(Map<String, String> newTableToLabelMappings) {
			this.tableToLabelMappings = Map.copyOf(Objects.requireNonNull(newTableToLabelMappings));
			return this;
		}

		/**
		 * Applies new join column mappings.
		 * @param newJoinColumnsToTypeMappings the new mappings
		 * @return this builder
		 */
		public Builder withJoinColumnsToTypeMappings(Map<String, String> newJoinColumnsToTypeMappings) {
			this.joinColumnsToTypeMappings = Map.copyOf(Objects.requireNonNull(newJoinColumnsToTypeMappings));
			return this;
		}

		/**
		 * Applies a new {@link SQLDialect} for both parsing and optionally rendering SQL.
		 * @param newSqlDialect the new sql dialect
		 * @return this builder
		 */
		public Builder withSqlDialect(SQLDialect newSqlDialect) {
			this.sqlDialect = Objects.requireNonNull(newSqlDialect);
			return this;
		}

		/**
		 * Enables or disables pretty printing of the generated Cypher queries.
		 * @param prettyPrint set to {@literal false} to disable pretty printing
		 * @return this builder
		 */
		public Builder withPrettyPrint(boolean prettyPrint) {
			this.prettyPrint = prettyPrint;
			return this;
		}

		/**
		 * Changes the prefix used for parsing named parameters. If set to
		 * {@literal null}, the jOOQ default ({@literal :}) is used.
		 * @param parseNamedParamPrefix the new prefix for parsing named parameters
		 * @return this builder
		 */
		public Builder withParseNamedParamPrefix(String parseNamedParamPrefix) {
			this.parseNamedParamPrefix = parseNamedParamPrefix;
			return this;
		}

		/**
		 * Configure whether names should be always escaped.
		 * @param alwaysEscapeNames use {@literal true} to always escape names
		 * @return this builder
		 */
		public Builder withAlwaysEscapeNames(boolean alwaysEscapeNames) {
			this.alwaysEscapeNames = alwaysEscapeNames;
			return this;
		}

		/**
		 * Finishes building a new configuration. The builder is safe to reuse afterward.
		 * @return a new immutable configuration
		 */
		public Sql2CypherConfig build() {
			return new Sql2CypherConfig(this);
		}

		private boolean alwaysEscapeNames() {
			return (this.alwaysEscapeNames != null) ? this.alwaysEscapeNames : !this.prettyPrint;
		}

	}

}
