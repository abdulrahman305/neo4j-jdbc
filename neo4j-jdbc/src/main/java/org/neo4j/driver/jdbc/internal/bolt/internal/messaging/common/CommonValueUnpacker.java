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
package org.neo4j.driver.jdbc.internal.bolt.internal.messaging.common;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.neo4j.driver.jdbc.internal.bolt.Value;
import org.neo4j.driver.jdbc.internal.bolt.Values;
import org.neo4j.driver.jdbc.internal.bolt.exception.BoltException;
import org.neo4j.driver.jdbc.internal.bolt.internal.InternalNode;
import org.neo4j.driver.jdbc.internal.bolt.internal.InternalPath;
import org.neo4j.driver.jdbc.internal.bolt.internal.InternalRelationship;
import org.neo4j.driver.jdbc.internal.bolt.internal.messaging.ValueUnpacker;
import org.neo4j.driver.jdbc.internal.bolt.internal.packstream.PackInput;
import org.neo4j.driver.jdbc.internal.bolt.internal.packstream.PackStream;
import org.neo4j.driver.jdbc.internal.bolt.internal.types.TypeConstructor;
import org.neo4j.driver.jdbc.internal.bolt.internal.util.Iterables;
import org.neo4j.driver.jdbc.internal.bolt.internal.value.ListValue;
import org.neo4j.driver.jdbc.internal.bolt.internal.value.MapValue;
import org.neo4j.driver.jdbc.internal.bolt.internal.value.NodeValue;
import org.neo4j.driver.jdbc.internal.bolt.internal.value.PathValue;
import org.neo4j.driver.jdbc.internal.bolt.internal.value.RelationshipValue;
import org.neo4j.driver.jdbc.internal.bolt.internal.value.UnsupportedDateTimeValue;
import org.neo4j.driver.jdbc.internal.bolt.types.Node;
import org.neo4j.driver.jdbc.internal.bolt.types.Path;
import org.neo4j.driver.jdbc.internal.bolt.types.Relationship;

public class CommonValueUnpacker implements ValueUnpacker {

	public static final byte DATE = 'D';

	public static final int DATE_STRUCT_SIZE = 1;

	public static final byte TIME = 'T';

	public static final int TIME_STRUCT_SIZE = 2;

	public static final byte LOCAL_TIME = 't';

	public static final int LOCAL_TIME_STRUCT_SIZE = 1;

	public static final byte LOCAL_DATE_TIME = 'd';

	public static final int LOCAL_DATE_TIME_STRUCT_SIZE = 2;

	public static final byte DATE_TIME_WITH_ZONE_OFFSET = 'F';

	public static final byte DATE_TIME_WITH_ZONE_OFFSET_UTC = 'I';

	public static final byte DATE_TIME_WITH_ZONE_ID = 'f';

	public static final byte DATE_TIME_WITH_ZONE_ID_UTC = 'i';

	public static final int DATE_TIME_STRUCT_SIZE = 3;

	public static final byte DURATION = 'E';

	public static final int DURATION_TIME_STRUCT_SIZE = 4;

	public static final byte POINT_2D_STRUCT_TYPE = 'X';

	public static final int POINT_2D_STRUCT_SIZE = 3;

	public static final byte POINT_3D_STRUCT_TYPE = 'Y';

	public static final int POINT_3D_STRUCT_SIZE = 4;

	public static final byte NODE = 'N';

	public static final byte RELATIONSHIP = 'R';

	public static final byte UNBOUND_RELATIONSHIP = 'r';

	public static final byte PATH = 'P';

	private static final int NODE_FIELDS = 3;

	private static final int RELATIONSHIP_FIELDS = 5;

	private final boolean dateTimeUtcEnabled;

	protected final PackStream.Unpacker unpacker;

	public CommonValueUnpacker(PackInput input, boolean dateTimeUtcEnabled) {
		this.dateTimeUtcEnabled = dateTimeUtcEnabled;
		this.unpacker = new PackStream.Unpacker(input);
	}

	@Override
	public long unpackStructHeader() throws IOException {
		return this.unpacker.unpackStructHeader();
	}

	@Override
	public int unpackStructSignature() throws IOException {
		return this.unpacker.unpackStructSignature();
	}

	@Override
	public Map<String, Value> unpackMap() throws IOException {
		var size = (int) this.unpacker.unpackMapHeader();
		if (size == 0) {
			return Collections.emptyMap();
		}
		Map<String, Value> map = Iterables.newHashMapWithSize(size);
		for (var i = 0; i < size; i++) {
			var key = this.unpacker.unpackString();
			map.put(key, unpack());
		}
		return map;
	}

	@Override
	public Value[] unpackArray() throws IOException {
		var size = (int) this.unpacker.unpackListHeader();
		var values = new Value[size];
		for (var i = 0; i < size; i++) {
			values[i] = unpack();
		}
		return values;
	}

	protected Value unpack() throws IOException {
		var type = this.unpacker.peekNextType();
		switch (type) {
			case NULL -> {
				return Values.value(this.unpacker.unpackNull());
			}
			case BOOLEAN -> {
				return Values.value(this.unpacker.unpackBoolean());
			}
			case INTEGER -> {
				return Values.value(this.unpacker.unpackLong());
			}
			case FLOAT -> {
				return Values.value(this.unpacker.unpackDouble());
			}
			case BYTES -> {
				return Values.value(this.unpacker.unpackBytes());
			}
			case STRING -> {
				return Values.value(this.unpacker.unpackString());
			}
			case MAP -> {
				return new MapValue(unpackMap());
			}
			case LIST -> {
				var size = (int) this.unpacker.unpackListHeader();
				var vals = new Value[size];
				for (var j = 0; j < size; j++) {
					vals[j] = unpack();
				}
				return new ListValue(vals);
			}
			case STRUCT -> {
				var size = this.unpacker.unpackStructHeader();
				var structType = this.unpacker.unpackStructSignature();
				return unpackStruct(size, structType);
			}
		}
		throw new IOException("Unknown value type: " + type);
	}

	private Value unpackStruct(long size, byte type) throws IOException {
		switch (type) {
			case DATE -> {
				ensureCorrectStructSize(TypeConstructor.DATE, DATE_STRUCT_SIZE, size);
				return unpackDate();
			}
			case TIME -> {
				ensureCorrectStructSize(TypeConstructor.TIME, TIME_STRUCT_SIZE, size);
				return unpackTime();
			}
			case LOCAL_TIME -> {
				ensureCorrectStructSize(TypeConstructor.LOCAL_TIME, LOCAL_TIME_STRUCT_SIZE, size);
				return unpackLocalTime();
			}
			case LOCAL_DATE_TIME -> {
				ensureCorrectStructSize(TypeConstructor.LOCAL_DATE_TIME, LOCAL_DATE_TIME_STRUCT_SIZE, size);
				return unpackLocalDateTime();
			}
			case DATE_TIME_WITH_ZONE_OFFSET -> {
				if (!this.dateTimeUtcEnabled) {
					ensureCorrectStructSize(TypeConstructor.DATE_TIME, DATE_TIME_STRUCT_SIZE, size);
					return unpackDateTime(ZoneMode.OFFSET, BaselineMode.LEGACY);
				}
				else {
					throw instantiateExceptionForUnknownType(type);
				}
			}
			case DATE_TIME_WITH_ZONE_OFFSET_UTC -> {
				if (this.dateTimeUtcEnabled) {
					ensureCorrectStructSize(TypeConstructor.DATE_TIME, DATE_TIME_STRUCT_SIZE, size);
					return unpackDateTime(ZoneMode.OFFSET, BaselineMode.UTC);
				}
				else {
					throw instantiateExceptionForUnknownType(type);
				}
			}
			case DATE_TIME_WITH_ZONE_ID -> {
				if (!this.dateTimeUtcEnabled) {
					ensureCorrectStructSize(TypeConstructor.DATE_TIME, DATE_TIME_STRUCT_SIZE, size);
					return unpackDateTime(ZoneMode.ZONE_ID, BaselineMode.LEGACY);
				}
				else {
					throw instantiateExceptionForUnknownType(type);
				}
			}
			case DATE_TIME_WITH_ZONE_ID_UTC -> {
				if (this.dateTimeUtcEnabled) {
					ensureCorrectStructSize(TypeConstructor.DATE_TIME, DATE_TIME_STRUCT_SIZE, size);
					return unpackDateTime(ZoneMode.ZONE_ID, BaselineMode.UTC);
				}
				else {
					throw instantiateExceptionForUnknownType(type);
				}
			}
			case DURATION -> {
				ensureCorrectStructSize(TypeConstructor.DURATION, DURATION_TIME_STRUCT_SIZE, size);
				return unpackDuration();
			}
			case POINT_2D_STRUCT_TYPE -> {
				ensureCorrectStructSize(TypeConstructor.POINT, POINT_2D_STRUCT_SIZE, size);
				return unpackPoint2D();
			}
			case POINT_3D_STRUCT_TYPE -> {
				ensureCorrectStructSize(TypeConstructor.POINT, POINT_3D_STRUCT_SIZE, size);
				return unpackPoint3D();
			}
			case NODE -> {
				ensureCorrectStructSize(TypeConstructor.NODE, getNodeFields(), size);
				var adapted = unpackNode();
				return new NodeValue(adapted);
			}
			case RELATIONSHIP -> {
				ensureCorrectStructSize(TypeConstructor.RELATIONSHIP, getRelationshipFields(), size);
				return unpackRelationship();
			}
			case PATH -> {
				ensureCorrectStructSize(TypeConstructor.PATH, 3, size);
				return unpackPath();
			}
			default -> throw instantiateExceptionForUnknownType(type);
		}
	}

	protected Value unpackRelationship() throws IOException {
		var urn = this.unpacker.unpackLong();
		var startUrn = this.unpacker.unpackLong();
		var endUrn = this.unpacker.unpackLong();
		var relType = this.unpacker.unpackString();
		var props = unpackMap();

		var adapted = new InternalRelationship(urn, String.valueOf(urn), startUrn, String.valueOf(startUrn), endUrn,
				String.valueOf(endUrn), relType, props);
		return new RelationshipValue(adapted);
	}

	protected InternalNode unpackNode() throws IOException {
		var urn = this.unpacker.unpackLong();

		var numLabels = (int) this.unpacker.unpackListHeader();
		List<String> labels = new ArrayList<>(numLabels);
		for (var i = 0; i < numLabels; i++) {
			labels.add(this.unpacker.unpackString());
		}
		var numProps = (int) this.unpacker.unpackMapHeader();
		Map<String, Value> props = Iterables.newHashMapWithSize(numProps);
		for (var j = 0; j < numProps; j++) {
			var key = this.unpacker.unpackString();
			props.put(key, unpack());
		}

		return new InternalNode(urn, String.valueOf(urn), labels, props);
	}

	@SuppressWarnings({ "deprecation", "DuplicatedCode" })
	protected Value unpackPath() throws IOException {
		// List of unique nodes
		var uniqNodes = new Node[(int) this.unpacker.unpackListHeader()];
		for (var i = 0; i < uniqNodes.length; i++) {
			ensureCorrectStructSize(TypeConstructor.NODE, getNodeFields(), this.unpacker.unpackStructHeader());
			ensureCorrectStructSignature("NODE", NODE, this.unpacker.unpackStructSignature());
			uniqNodes[i] = unpackNode();
		}

		// List of unique relationships, without start/end information
		var uniqRels = new InternalRelationship[(int) this.unpacker.unpackListHeader()];
		for (var i = 0; i < uniqRels.length; i++) {
			ensureCorrectStructSize(TypeConstructor.RELATIONSHIP, 3, this.unpacker.unpackStructHeader());
			ensureCorrectStructSignature("UNBOUND_RELATIONSHIP", UNBOUND_RELATIONSHIP,
					this.unpacker.unpackStructSignature());
			var id = this.unpacker.unpackLong();
			var relType = this.unpacker.unpackString();
			var props = unpackMap();
			uniqRels[i] = new InternalRelationship(id, String.valueOf(id), -1, String.valueOf(-1), -1,
					String.valueOf(-1), relType, props);
		}

		// Path sequence
		var length = (int) this.unpacker.unpackListHeader();

		// Knowing the sequence length, we can create the arrays that will represent the
		// nodes, rels and segments in
		// their "path order"
		var segments = new Path.Segment[length / 2];
		var nodes = new Node[segments.length + 1];
		var rels = new Relationship[segments.length];

		var prevNode = uniqNodes[0];
		Node nextNode; // Start node is always 0, and isn't encoded in the sequence

		nodes[0] = prevNode;
		InternalRelationship rel;
		for (var i = 0; i < segments.length; i++) {
			var relIdx = (int) this.unpacker.unpackLong();
			nextNode = uniqNodes[(int) this.unpacker.unpackLong()];
			// Negative rel index means this rel was traversed "inversed" from its
			// direction
			if (relIdx < 0) {
				rel = uniqRels[(-relIdx) - 1]; // -1 because rel idx are 1-indexed
				rel.setStartAndEnd(nextNode.id(), String.valueOf(nextNode.id()), prevNode.id(),
						String.valueOf(prevNode.id()));
			}
			else {
				rel = uniqRels[relIdx - 1];
				rel.setStartAndEnd(prevNode.id(), String.valueOf(prevNode.id()), nextNode.id(),
						String.valueOf(nextNode.id()));
			}

			nodes[i + 1] = nextNode;
			rels[i] = rel;
			segments[i] = new InternalPath.SelfContainedSegment(prevNode, rel, nextNode);
			prevNode = nextNode;
		}
		return new PathValue(new InternalPath(Arrays.asList(segments), Arrays.asList(nodes), Arrays.asList(rels)));
	}

	protected final void ensureCorrectStructSize(TypeConstructor typeConstructor, int expected, long actual) {
		if (expected != actual) {
			var structName = typeConstructor.toString();
			throw new BoltException(
					String.format(
							"Invalid message received, serialized %s structures should have %d fields, "
									+ "received %s structure has %d fields.",
							structName, expected, structName, actual));
		}
	}

	protected void ensureCorrectStructSignature(String structName, byte expected, byte actual) {
		if (expected != actual) {
			throw new BoltException(String.format(
					"Invalid message received, expected a `%s`, signature 0x%s. Received signature was 0x%s.",
					structName, Integer.toHexString(expected), Integer.toHexString(actual)));
		}
	}

	private Value unpackDate() throws IOException {
		var epochDay = this.unpacker.unpackLong();
		return Values.value(LocalDate.ofEpochDay(epochDay));
	}

	private Value unpackTime() throws IOException {
		var nanoOfDayLocal = this.unpacker.unpackLong();
		var offsetSeconds = Math.toIntExact(this.unpacker.unpackLong());

		var localTime = LocalTime.ofNanoOfDay(nanoOfDayLocal);
		var offset = ZoneOffset.ofTotalSeconds(offsetSeconds);
		return Values.value(OffsetTime.of(localTime, offset));
	}

	private Value unpackLocalTime() throws IOException {
		var nanoOfDayLocal = this.unpacker.unpackLong();
		return Values.value(LocalTime.ofNanoOfDay(nanoOfDayLocal));
	}

	private Value unpackLocalDateTime() throws IOException {
		var epochSecondUtc = this.unpacker.unpackLong();
		var nano = Math.toIntExact(this.unpacker.unpackLong());
		return Values.value(LocalDateTime.ofEpochSecond(epochSecondUtc, nano, ZoneOffset.UTC));
	}

	private Value unpackDateTime(ZoneMode unpackOffset, BaselineMode useUtcBaseline) throws IOException {
		var epochSecondLocal = this.unpacker.unpackLong();
		var nano = Math.toIntExact(this.unpacker.unpackLong());
		Supplier<ZoneId> zoneIdSupplier;
		if (unpackOffset == ZoneMode.OFFSET) {
			var offsetSeconds = Math.toIntExact(this.unpacker.unpackLong());
			zoneIdSupplier = () -> ZoneOffset.ofTotalSeconds(offsetSeconds);
		}
		else {
			var zoneIdString = this.unpacker.unpackString();
			zoneIdSupplier = () -> ZoneId.of(zoneIdString);
		}
		ZoneId zoneId;
		try {
			zoneId = zoneIdSupplier.get();
		}
		catch (DateTimeException ex) {
			return new UnsupportedDateTimeValue(ex);
		}
		return (useUtcBaseline == BaselineMode.UTC)
				? Values.value(newZonedDateTimeUsingUtcBaseline(epochSecondLocal, nano, zoneId))
				: Values.value(newZonedDateTime(epochSecondLocal, nano, zoneId));
	}

	private Value unpackDuration() throws IOException {
		var months = this.unpacker.unpackLong();
		var days = this.unpacker.unpackLong();
		var seconds = this.unpacker.unpackLong();
		var nanoseconds = Math.toIntExact(this.unpacker.unpackLong());
		return Values.isoDuration(months, days, seconds, nanoseconds);
	}

	private Value unpackPoint2D() throws IOException {
		var srid = Math.toIntExact(this.unpacker.unpackLong());
		var x = this.unpacker.unpackDouble();
		var y = this.unpacker.unpackDouble();
		return Values.point(srid, x, y);
	}

	private Value unpackPoint3D() throws IOException {
		var srid = Math.toIntExact(this.unpacker.unpackLong());
		var x = this.unpacker.unpackDouble();
		var y = this.unpacker.unpackDouble();
		var z = this.unpacker.unpackDouble();
		return Values.point(srid, x, y, z);
	}

	private static ZonedDateTime newZonedDateTime(long epochSecondLocal, long nano, ZoneId zoneId) {
		var instant = Instant.ofEpochSecond(epochSecondLocal, nano);
		var localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
		return ZonedDateTime.of(localDateTime, zoneId);
	}

	private ZonedDateTime newZonedDateTimeUsingUtcBaseline(long epochSecondLocal, int nano, ZoneId zoneId) {
		var instant = Instant.ofEpochSecond(epochSecondLocal, nano);
		var localDateTime = LocalDateTime.ofInstant(instant, zoneId);
		return ZonedDateTime.of(localDateTime, zoneId);
	}

	private RuntimeException instantiateExceptionForUnknownType(byte type) {
		return new RuntimeException("Unknown struct type: " + type);
	}

	protected int getNodeFields() {
		return NODE_FIELDS;
	}

	protected int getRelationshipFields() {
		return RELATIONSHIP_FIELDS;
	}

	private enum ZoneMode {

		OFFSET, ZONE_ID

	}

	private enum BaselineMode {

		UTC, LEGACY

	}

}