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
package org.neo4j.driver.jdbc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Wrapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.neo4j.driver.jdbc.internal.bolt.BoltConnection;
import org.neo4j.driver.jdbc.internal.bolt.response.PullResponse;
import org.neo4j.driver.jdbc.internal.bolt.response.RunResponse;
import org.neo4j.driver.jdbc.values.Record;
import org.neo4j.driver.jdbc.values.Type;
import org.neo4j.driver.jdbc.values.Value;
import org.neo4j.driver.jdbc.values.Values;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ResultSetImplTests {

	private static final int INDEX = 1;

	private static final String LABEL = "label";

	private ResultSet resultSet;

	@ParameterizedTest
	@MethodSource("getStringArgs")
	void shouldProcessValueOnGetString(Value value, VerificationLogic<String> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getString(INDEX) : this.resultSet.getString(LABEL));
	}

	private static Stream<Arguments> getStringArgs() {
		return Stream.of(
				// string handling
				Arguments.of(Values.value("0"),
						Named.<VerificationLogic<String>>of("verify returns '0'",
								supplier -> assertThat(supplier.get()).isEqualTo("0"))),
				Arguments.of(Values.value(""),
						Named.<VerificationLogic<String>>of("verify returns ''",
								supplier -> assertThat(supplier.get()).isEqualTo(""))),
				Arguments.of(Values.value("testing"),
						Named.<VerificationLogic<String>>of("verify returns 'testing'",
								supplier -> assertThat(supplier.get()).isEqualTo("testing"))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Boolean>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(0),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(true),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getBooleanArgs")
	void shouldProcessValueOnGetBoolean(Value value, VerificationLogic<Boolean> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getBoolean(INDEX) : this.resultSet.getBoolean(LABEL));
	}

	private static Stream<Arguments> getBooleanArgs() {
		return Stream.of(
				// string handling
				Arguments.of(Values.value("0"),
						Named.<VerificationLogic<Boolean>>of("verify returns false",
								supplier -> assertThat(supplier.get()).isFalse())),
				Arguments.of(Values.value("1"),
						Named.<VerificationLogic<Boolean>>of("verify returns true",
								supplier -> assertThat(supplier.get()).isTrue())),
				Arguments.of(Values.value("-1"),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value("5"),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(""),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value("testing"),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				// char handling
				Arguments.of(Values.value('0'),
						Named.<VerificationLogic<Boolean>>of("verify returns false",
								supplier -> assertThat(supplier.get()).isFalse())),
				Arguments.of(Values.value('1'),
						Named.<VerificationLogic<Boolean>>of("verify returns true",
								supplier -> assertThat(supplier.get()).isTrue())),
				Arguments.of(Values.value('5'),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(' '),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value('t'),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				// number handling
				Arguments.of(Values.value(0),
						Named.<VerificationLogic<Boolean>>of("verify returns false",
								supplier -> assertThat(supplier.get()).isFalse())),
				Arguments.of(Values.value(1),
						Named.<VerificationLogic<Boolean>>of("verify returns true",
								supplier -> assertThat(supplier.get()).isTrue())),
				Arguments.of(Values.value(-1),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(5),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Boolean>>of("verify returns false",
								supplier -> assertThat(supplier.get()).isFalse())),
				// boolean handling
				Arguments.of(Values.value(false),
						Named.<VerificationLogic<Boolean>>of("verify returns false",
								supplier -> assertThat(supplier.get()).isFalse())),
				Arguments.of(Values.value(true),
						Named.<VerificationLogic<Boolean>>of("verify returns true",
								supplier -> assertThat(supplier.get()).isTrue())),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getByteArgs")
	void shouldProcessValueOnGetByte(Value value, VerificationLogic<Byte> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getByte(INDEX) : this.resultSet.getByte(LABEL));
	}

	private static Stream<Arguments> getByteArgs() {
		return Stream.of(
				// number handling
				Arguments.of(Values.value(0),
						Named.<VerificationLogic<Byte>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo((byte) 0))),
				Arguments.of(Values.value(Byte.MIN_VALUE),
						Named.<VerificationLogic<Byte>>of("verify returns Byte.MIN_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Byte.MIN_VALUE))),
				Arguments.of(Values.value(Byte.MAX_VALUE),
						Named.<VerificationLogic<Byte>>of("verify returns Byte.MAX_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Byte.MAX_VALUE))),
				Arguments.of(Values.value(Byte.MAX_VALUE + 1),
						Named.<VerificationLogic<Byte>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Byte.MIN_VALUE - 1),
						Named.<VerificationLogic<Byte>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Byte>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo((byte) 0))),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getShortArgs")
	void shouldProcessValueOnGetShort(Value value, VerificationLogic<Short> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getShort(INDEX) : this.resultSet.getShort(LABEL));
	}

	private static Stream<Arguments> getShortArgs() {
		return Stream.of(
				// number handling
				Arguments.of(Values.value(0),
						Named.<VerificationLogic<Short>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo((short) 0))),
				Arguments.of(Values.value(Short.MIN_VALUE),
						Named.<VerificationLogic<Short>>of("verify returns Short.MIN_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Short.MIN_VALUE))),
				Arguments.of(Values.value(Short.MAX_VALUE),
						Named.<VerificationLogic<Short>>of("verify returns Short.MAX_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Short.MAX_VALUE))),
				Arguments.of(Values.value(Short.MAX_VALUE + 1),
						Named.<VerificationLogic<Short>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Short.MIN_VALUE - 1),
						Named.<VerificationLogic<Short>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Short>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo((short) 0))),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getIntArgs")
	void shouldProcessValueOnGetInt(Value value, VerificationLogic<Integer> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getInt(INDEX) : this.resultSet.getInt(LABEL));
	}

	private static Stream<Arguments> getIntArgs() {
		return Stream.of(
				// number handling
				Arguments.of(Values.value(0),
						Named.<VerificationLogic<Integer>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo(0))),
				Arguments.of(Values.value(Integer.MIN_VALUE),
						Named.<VerificationLogic<Integer>>of("verify returns Integer.MIN_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Integer.MIN_VALUE))),
				Arguments.of(Values.value(Integer.MAX_VALUE),
						Named.<VerificationLogic<Integer>>of("verify returns Integer.MAX_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Integer.MAX_VALUE))),
				Arguments.of(Values.value(Integer.MAX_VALUE + 1L),
						Named.<VerificationLogic<Integer>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Integer.MIN_VALUE - 1L),
						Named.<VerificationLogic<Integer>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Integer>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo(0))),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getLongArgs")
	void shouldProcessValueOnGetLong(Value value, VerificationLogic<Long> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getLong(INDEX) : this.resultSet.getLong(LABEL));
	}

	private static Stream<Arguments> getLongArgs() {
		return Stream.of(
				// number handling
				Arguments.of(Values.value(0),
						Named.<VerificationLogic<Long>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo(0))),
				Arguments.of(Values.value(Long.MIN_VALUE),
						Named.<VerificationLogic<Long>>of("verify returns Long.MIN_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Long.MIN_VALUE))),
				Arguments.of(Values.value(Long.MAX_VALUE),
						Named.<VerificationLogic<Long>>of("verify returns Long.MAX_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Long.MAX_VALUE))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Long>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo(0))),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getFloatArgs")
	void shouldProcessValueOnGetFloat(Value value, VerificationLogic<Float> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getFloat(INDEX) : this.resultSet.getFloat(LABEL));
	}

	private static Stream<Arguments> getFloatArgs() {
		return Stream.of(
				// number handling
				Arguments.of(Values.value(0.0f),
						Named.<VerificationLogic<Float>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo(0.0f))),
				Arguments.of(Values.value(Float.MIN_VALUE),
						Named.<VerificationLogic<Float>>of("verify returns Float.MIN_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Float.MIN_VALUE))),
				Arguments.of(Values.value(Float.MAX_VALUE),
						Named.<VerificationLogic<Float>>of("verify returns Float.MAX_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Float.MAX_VALUE))),
				Arguments.of(Values.value(Double.MAX_VALUE),
						Named.<VerificationLogic<Float>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Double.MIN_VALUE),
						Named.<VerificationLogic<Float>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Float>>of("verify returns 0.0",
								supplier -> assertThat(supplier.get()).isEqualTo(0.0f))),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getDoubleArgs")
	void shouldProcessValueOnGetDouble(Value value, VerificationLogic<Double> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getDouble(INDEX) : this.resultSet.getDouble(LABEL));
	}

	private static Stream<Arguments> getDoubleArgs() {
		return Stream.of(
				// number handling
				Arguments.of(Values.value(0.0f),
						Named.<VerificationLogic<Double>>of("verify returns 0",
								supplier -> assertThat(supplier.get()).isEqualTo(0.0))),
				Arguments.of(Values.value(Double.MIN_VALUE),
						Named.<VerificationLogic<Double>>of("verify returns Double.MIN_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Double.MIN_VALUE))),
				Arguments.of(Values.value(Double.MAX_VALUE),
						Named.<VerificationLogic<Double>>of("verify returns Double.MAX_VALUE",
								supplier -> assertThat(supplier.get()).isEqualTo(Double.MAX_VALUE))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Double>>of("verify returns 0.0",
								supplier -> assertThat(supplier.get()).isEqualTo(0.0))),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getBytesArgs")
	void shouldProcessValueOnGetBytes(Value value, VerificationLogic<byte[]> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getBytes(INDEX) : this.resultSet.getBytes(LABEL));
	}

	private static Stream<Arguments> getBytesArgs() {
		return Stream.of(
				// byte array handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<byte[]>>of("verify returns empty byte array",
								supplier -> assertThat(supplier.get()).isEqualTo(new byte[] {}))),
				Arguments.of(Values.value(new byte[] { 1, 2, 3, 4, 5 }),
						Named.<VerificationLogic<byte[]>>of("verify returns non-empty byte array",
								supplier -> assertThat(supplier.get()).isEqualTo(new byte[] { 1, 2, 3, 4, 5 }))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Double>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(false),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Boolean>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getDateArgs")
	void shouldProcessValueOnGetDate(Value value, VerificationLogic<Date> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getDate(INDEX) : this.resultSet.getDate(LABEL));
	}

	private static Stream<Arguments> getDateArgs() {
		return Stream.of(
				// date handling
				Arguments
					.of(Values.value(LocalDate.of(2023, 1, 1)), Named.<VerificationLogic<Date>>of("verify returns Date",
							supplier -> assertThat(supplier.get()).isEqualTo(Date.valueOf(LocalDate.of(2023, 1, 1))))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Date>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(Duration.ZERO),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Period.ZERO),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDateTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(ZonedDateTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getDateWithCalendarArgs")
	void shouldProcessValueOnGetDateWithCalendar(Value value, VerificationLogic<Date> verificationLogic,
			boolean indexAccess) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getDate(INDEX, Calendar.getInstance())
				: this.resultSet.getDate(LABEL, Calendar.getInstance()));
	}

	private static Stream<Arguments> getDateWithCalendarArgs() {
		var zonedDateTime = ZonedDateTime.of(2023, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault());
		return Stream.of(
				// date handling
				Arguments.of(Values.value(zonedDateTime), Named.<VerificationLogic<Date>>of("verify returns Date",
						supplier -> assertThat(supplier.get()).isEqualTo(Date.valueOf(zonedDateTime.toLocalDate())))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Date>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(Duration.ZERO),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Period.ZERO),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDate.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDateTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getTimeArgs")
	void shouldProcessValueOnGetTime(Value value, VerificationLogic<Time> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getTime(INDEX) : this.resultSet.getTime(LABEL));
	}

	private static Stream<Arguments> getTimeArgs() {
		return Stream.of(
				// date handling
				Arguments.of(Values.value(LocalTime.of(1, 1)),
						Named.<VerificationLogic<Time>>of("verify returns Time",
								supplier -> assertThat(supplier.get()).isEqualTo(Time.valueOf(LocalTime.of(1, 1))))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Time>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(Duration.ZERO),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Period.ZERO),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDate.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDateTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(ZonedDateTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getTimeWithCalendarArgs")
	void shouldProcessValueOnGetTimeWithCalendar(Value value, VerificationLogic<Time> verificationLogic,
			boolean indexAccess) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getTime(INDEX, Calendar.getInstance())
				: this.resultSet.getTime(LABEL, Calendar.getInstance()));
	}

	private static Stream<Arguments> getTimeWithCalendarArgs() {
		var zonedDateTime = ZonedDateTime.of(2023, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault());
		return Stream.of(
				// date handling
				Arguments.of(Values.value(zonedDateTime), Named.<VerificationLogic<Time>>of("verify returns Date",
						supplier -> assertThat(supplier.get()).isEqualTo(Time.valueOf(zonedDateTime.toLocalTime())))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Time>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(Duration.ZERO),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Period.ZERO),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDate.now()),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalTime.now()),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDateTime.now()),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Time>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getTimestampArgs")
	void shouldProcessValueOnGetTimestamp(Value value, VerificationLogic<Timestamp> verificationLogic,
			boolean indexAccess) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic
			.run(() -> indexAccess ? this.resultSet.getTimestamp(INDEX) : this.resultSet.getTimestamp(LABEL));
	}

	private static Stream<Arguments> getTimestampArgs() {
		return Stream.of(
				// date handling
				Arguments.of(Values.value(LocalDateTime.of(2023, 1, 1, 1, 1)),
						Named.<VerificationLogic<Timestamp>>of("verify returns Date",
								supplier -> assertThat(supplier.get())
									.isEqualTo(Timestamp.valueOf(LocalDateTime.of(2023, 1, 1, 1, 1))))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Timestamp>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(Duration.ZERO),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Period.ZERO),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDate.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(ZonedDateTime.now()),
						Named.<VerificationLogic<Date>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getTimestampWithCalendarArgs")
	void shouldProcessValueOnGetTimestampWithCalendar(Value value, VerificationLogic<Timestamp> verificationLogic,
			boolean indexAccess) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getTimestamp(INDEX, Calendar.getInstance())
				: this.resultSet.getTimestamp(LABEL, Calendar.getInstance()));
	}

	private static Stream<Arguments> getTimestampWithCalendarArgs() {
		var zonedDateTime = ZonedDateTime.of(2023, 1, 1, 1, 1, 1, 1, ZoneId.systemDefault());
		return Stream.of(
				// date handling
				Arguments.of(Values.value(zonedDateTime),
						Named.<VerificationLogic<Timestamp>>of("verify returns Date",
								supplier -> assertThat(supplier.get())
									.isEqualTo(Timestamp.valueOf(zonedDateTime.toLocalDateTime())))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Timestamp>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(Duration.ZERO),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(Period.ZERO),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDate.now()),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalTime.now()),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(LocalDateTime.now()),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Timestamp>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getCharacterStreamArgs")
	void shouldProcessValueOnGetCharacterStream(Value value, VerificationLogic<Reader> verificationLogic,
			boolean indexAccess) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getCharacterStream(INDEX)
				: this.resultSet.getCharacterStream(LABEL));
	}

	private static Stream<Arguments> getCharacterStreamArgs() {
		return Stream.of(
				// string handling
				Arguments.of(Values.value("testing"),
						Named.<VerificationLogic<Reader>>of("verify returns Reader", supplier -> {
							var value = new BufferedReader(supplier.get()).lines().collect(Collectors.joining());
							assertThat(value).isEqualTo("testing");
						})),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<Reader>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<Reader>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<Reader>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getBigDecimalArgs")
	void shouldProcessValueOnGetBigDecimal(Value value, VerificationLogic<BigDecimal> verificationLogic,
			boolean indexAccess) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic
			.run(() -> indexAccess ? this.resultSet.getBigDecimal(INDEX) : this.resultSet.getBigDecimal(LABEL));
	}

	private static Stream<Arguments> getBigDecimalArgs() {
		return Stream.of(
				// integer handling
				Arguments.of(Values.value(1),
						Named.<VerificationLogic<BigDecimal>>of("verify returns BigDecimal",
								supplier -> assertThat(supplier.get()).isEqualTo(BigDecimal.valueOf(1)))),
				// float handling
				Arguments.of(Values.value(1.5),
						Named.<VerificationLogic<BigDecimal>>of("verify returns BigDecimal",
								supplier -> assertThat(supplier.get()).isEqualTo(BigDecimal.valueOf(1.5)))),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<BigDecimal>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<BigDecimal>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<BigDecimal>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getAsciiStreamArgs")
	void shouldProcessValueOnGetAsciiStream(Value value, VerificationLogic<InputStream> verificationLogic,
			boolean indexAccess) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic
			.run(() -> indexAccess ? this.resultSet.getAsciiStream(INDEX) : this.resultSet.getAsciiStream(LABEL));
	}

	private static Stream<Arguments> getAsciiStreamArgs() {
		return Stream.of(
				// string handling
				Arguments.of(Values.value("testing"),
						Named.<VerificationLogic<InputStream>>of("verify returns InputStream", supplier -> {
							String value;
							try {
								value = new String((new BufferedInputStream(supplier.get()).readAllBytes()),
										StandardCharsets.US_ASCII);
							}
							catch (IOException ex) {
								throw new RuntimeException(ex);
							}
							assertThat(value).isEqualTo("testing");
						})),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<InputStream>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<InputStream>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<InputStream>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getBinaryStreamArgs")
	void shouldProcessValueOnGetBinaryStream(Value value, VerificationLogic<InputStream> verificationLogic,
			boolean indexAccess) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic
			.run(() -> indexAccess ? this.resultSet.getAsciiStream(INDEX) : this.resultSet.getAsciiStream(LABEL));
	}

	private static Stream<Arguments> getBinaryStreamArgs() {
		return Stream.of(
				// string handling
				Arguments.of(Values.value("testing"),
						Named.<VerificationLogic<InputStream>>of("verify returns InputStream", supplier -> {
							byte[] value;
							try {
								value = new BufferedInputStream(supplier.get()).readAllBytes();
							}
							catch (IOException ex) {
								throw new RuntimeException(ex);
							}
							assertThat(Arrays.equals("testing".getBytes(), value)).isTrue();
						})),
				// null handling
				Arguments.of(Values.NULL,
						Named.<VerificationLogic<InputStream>>of("verify returns null",
								supplier -> assertThat(supplier.get()).isNull())),
				// other types handling
				Arguments.of(Values.value(new byte[] {}),
						Named.<VerificationLogic<InputStream>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))),
				Arguments.of(Values.value(List.of("value")),
						Named.<VerificationLogic<InputStream>>of("verify throws exception",
								supplier -> assertThatThrownBy(supplier::get).isInstanceOf(SQLException.class))))
			// map each set of arguments to both index and label access methods
			.flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@ParameterizedTest
	@MethodSource("getObjectArgs")
	void shouldProcessValueOnGetObject(Value value, VerificationLogic<Object> verificationLogic, boolean indexAccess)
			throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();

		// when & then
		verificationLogic.run(() -> indexAccess ? this.resultSet.getObject(INDEX) : this.resultSet.getObject(LABEL));
	}

	private static Stream<Arguments> getObjectArgs() {
		return Stream.of(Type.values()).flatMap(type -> {
			var booleanStream = Stream.of(Boolean.TRUE, Boolean.FALSE)
				.map(value -> Arguments.of(Values.value(value), Named.<VerificationLogic<Object>>of(
						"verify returns boolean", supplier -> assertThat(supplier.get()).isEqualTo(value))));

			var bytesStream = Stream.of(Arguments.of(Values.value(new byte[] { 1, 2, 3, 4, 5 }),
					Named.<VerificationLogic<Object>>of("verify returns byte array",
							supplier -> assertThat(supplier.get()).isEqualTo(new byte[] { 1, 2, 3, 4, 5 }))));

			var stringStream = Stream
				.of(Arguments.of(Values.value("0"), Named.<VerificationLogic<Object>>of("verify returns string '0'",
						supplier -> assertThat(supplier.get()).isEqualTo("0"))));

			var integerStream = Stream.<Number>of(Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE)
				.map(value -> Arguments.of(Values.value(value), Named.<VerificationLogic<Object>>of(
						"verify returns long", supplier -> assertThat(supplier.get()).isEqualTo(value.longValue()))));

			var floatStream = Stream.<Number>of(Float.MIN_VALUE, Float.MAX_VALUE, Double.MIN_VALUE, Double.MAX_VALUE)
				.map(value -> Arguments.of(Values.value(value),
						Named.<VerificationLogic<Object>>of("verify returns double",
								supplier -> assertThat(supplier.get()).isEqualTo(value.doubleValue()))));

			var nullStream = Stream
				.of(Arguments.of(Values.NULL, Named.<VerificationLogic<Object>>of("verify returns null",
						supplier -> assertThat(supplier.get()).isNull())));

			var listStream = Stream.of(List.of(1L, 2L, 3L, 4L, 5L))
				.flatMap(list -> Stream
					.of(Arguments.of(Values.value(list), Named.<VerificationLogic<Object>>of("verify returns List",
							supplier -> assertThat(supplier.get()).isEqualTo(list)))));

			var mapStream = Stream.of(Map.of("k0", "v0", "k1", "k2"))
				.flatMap(map -> Stream
					.of(Arguments.of(Values.value(map), Named.<VerificationLogic<Object>>of("verify returns Map",
							supplier -> assertThat(supplier.get()).isEqualTo(map)))));

			var pointStream = Stream.of(Values.point(0, 1, 2))
				.flatMap(point -> Stream
					.of(Arguments.of(Values.value(point), Named.<VerificationLogic<Object>>of("verify returns Point",
							supplier -> assertThat(supplier.get()).isEqualTo(point.asPoint())))));

			var dateStream = Stream.of(LocalDate.now())
				.flatMap(date -> Stream
					.of(Arguments.of(Values.value(date), Named.<VerificationLogic<Object>>of("verify returns LocalDate",
							supplier -> assertThat(supplier.get()).isEqualTo(date)))));

			var timeStream = Stream.of(OffsetTime.now())
				.flatMap(time -> Stream.of(Arguments.of(Values.value(time), Named.<VerificationLogic<Object>>of(
						"verify returns OffsetTime", supplier -> assertThat(supplier.get()).isEqualTo(time)))));

			var localTimeStream = Stream.of(LocalTime.now())
				.flatMap(time -> Stream
					.of(Arguments.of(Values.value(time), Named.<VerificationLogic<Object>>of("verify returns LocalTime",
							supplier -> assertThat(supplier.get()).isEqualTo(time)))));

			var localDateTimeStream = Stream.of(LocalDateTime.now())
				.flatMap(time -> Stream.of(Arguments.of(Values.value(time), Named.<VerificationLogic<Object>>of(
						"verify returns LocalDateTime", supplier -> assertThat(supplier.get()).isEqualTo(time)))));

			var dateTimeStream = Stream.of(ZonedDateTime.now())
				.flatMap(time -> Stream.of(Arguments.of(Values.value(time), Named.<VerificationLogic<Object>>of(
						"verify returns ZonedDateTime", supplier -> assertThat(supplier.get()).isEqualTo(time)))));

			var durationStream = Stream.of(Values.isoDuration(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 0))
				.flatMap(durationValue -> Stream
					.of(Arguments.of(durationValue, Named.<VerificationLogic<Object>>of("verify returns IsoDuration",
							supplier -> assertThat(supplier.get()).isEqualTo(durationValue.asIsoDuration())))));

			return switch (type) {
				case ANY, NODE, RELATIONSHIP, PATH, NUMBER -> Stream.of();
				case BOOLEAN -> booleanStream;
				case BYTES -> bytesStream;
				case STRING -> stringStream;
				case INTEGER -> integerStream;
				case FLOAT -> floatStream;
				case NULL -> nullStream;
				case LIST -> listStream;
				case MAP -> mapStream;
				case POINT -> pointStream;
				case DATE -> dateStream;
				case TIME -> timeStream;
				case LOCAL_TIME -> localTimeStream;
				case LOCAL_DATE_TIME -> localDateTimeStream;
				case DATE_TIME -> dateTimeStream;
				case DURATION -> durationStream;
			};
		}).flatMap(ResultSetImplTests::mapArgumentToBothIndexAndLabelAccess);
	}

	@Test
	void shouldThrowOnWasNullWhenClosed() throws SQLException {
		// given
		this.resultSet = setupWithValue(Values.value((Object) null));
		this.resultSet.close();

		// when & then
		assertThatThrownBy(() -> this.resultSet.wasNull()).isInstanceOf(SQLException.class);
	}

	@Test
	void shouldThrowOnWasNullWhenNotReadFirst() throws SQLException {
		// given
		this.resultSet = setupWithValue(Values.value((Object) null));

		// when & then
		assertThatThrownBy(() -> this.resultSet.wasNull()).isInstanceOf(SQLException.class);
	}

	@ParameterizedTest
	@MethodSource("getWasNullArgs")
	void shouldHandleWasNull(Value value) throws SQLException {
		// given
		this.resultSet = setupWithValue(value);
		this.resultSet.next();
		this.resultSet.getObject(1);

		// when & then
		assertThat(this.resultSet.wasNull()).isEqualTo(Type.NULL.isTypeOf(value));
	}

	private static Stream<Value> getWasNullArgs() {
		return Stream.of(Values.value((Object) null), Values.value(1), Values.value(false), Values.value("value"),
				Values.value(0.5));
	}

	@ParameterizedTest
	@MethodSource("getUnwrapArgs")
	void shouldUnwrap(Class<?> cls, boolean shouldUnwrap) throws SQLException {
		// given
		this.resultSet = setupWithValue(Values.value(1));

		// when & then
		if (shouldUnwrap) {
			var unwrapped = this.resultSet.unwrap(cls);
			assertThat(unwrapped).isInstanceOf(cls);
		}
		else {
			assertThatThrownBy(() -> this.resultSet.unwrap(cls)).isInstanceOf(SQLException.class);
		}
	}

	@ParameterizedTest
	@MethodSource("getUnwrapArgs")
	void shouldSomethinhg(Class<?> cls, boolean shouldUnwrap) throws SQLException {
		// given
		this.resultSet = setupWithValue(Values.value(1));

		// when
		var wrapperFor = this.resultSet.isWrapperFor(cls);

		// then
		assertThat(wrapperFor).isEqualTo(shouldUnwrap);
	}

	private static Stream<Arguments> getUnwrapArgs() {
		return Stream.of(Arguments.of(ResultSetImpl.class, true), Arguments.of(ResultSet.class, true),
				Arguments.of(Wrapper.class, true), Arguments.of(AutoCloseable.class, true),
				Arguments.of(Object.class, true), Arguments.of(Statement.class, false));
	}

	@ParameterizedTest
	@ValueSource(strings = { LABEL, "nonexistent" })
	void shouldFindColumn(String label) throws SQLException {
		// given
		this.resultSet = setupWithValue(Values.value(1));
		var exists = LABEL.equals(label);

		// when & then
		if (exists) {
			var index = this.resultSet.findColumn(label);
			assertThat(index).isEqualTo(1);
		}
		else {
			assertThatThrownBy(() -> this.resultSet.findColumn(label)).isInstanceOf(SQLException.class);
		}
	}

	@Test
	void shouldReturnNullOnGetWarnings() throws SQLException {
		// given
		this.resultSet = setupWithValue(Values.value(1));

		// when
		var warnings = this.resultSet.getWarnings();

		// then
		assertThat((Object) warnings).isNull();
	}

	@Test
	void shouldReturnSuccessfullyOnClearWarnings() throws SQLException {
		// given
		this.resultSet = setupWithValue(Values.value(1));

		// when & then
		this.resultSet.clearWarnings();
	}

	private static Stream<Arguments> mapArgumentToBothIndexAndLabelAccess(Arguments arguments) {
		return Stream.of(Arguments.of(Stream.concat(Arrays.stream(arguments.get()), Stream.of(true)).toArray()),
				Arguments.of(Stream.concat(Arrays.stream(arguments.get()), Stream.of(false)).toArray()));
	}

	private ResultSet setupWithValue(Value expectedValue) throws SQLException {
		var boltConnection = mock(BoltConnection.class);
		var statement = mock(StatementImpl.class);
		given(statement.getBoltConnection()).willReturn(boltConnection);
		var runResponse = mock(RunResponse.class);

		var boltRecord = mock(Record.class);
		given(boltRecord.size()).willReturn(1);
		given(boltRecord.get(INDEX - 1)).willReturn(expectedValue);
		given(boltRecord.containsKey(LABEL)).willReturn(true);
		given(boltRecord.get(LABEL)).willReturn(expectedValue);
		given(boltRecord.keys()).willReturn(List.of(LABEL));

		var pullResponse = mock(PullResponse.class);
		given(pullResponse.records()).willReturn(List.of(boltRecord));

		return new ResultSetImpl(statement, runResponse, pullResponse, 1000, 0);
	}

	@FunctionalInterface
	private interface VerificationLogic<T> {

		void run(ValueSupplier<T> valueSupplier) throws SQLException;

	}

	@FunctionalInterface
	private interface ValueSupplier<T> {

		T get() throws SQLException;

	}

}
