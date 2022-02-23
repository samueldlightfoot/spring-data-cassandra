/*
 * Copyright 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.cassandra.core.convert;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.data.cassandra.core.mapping.BasicMapId.*;
import static org.springframework.data.cassandra.test.util.RowMockUtil.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.annotation.Transient;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.domain.AllPossibleTypes;
import org.springframework.data.cassandra.domain.CompositeKey;
import org.springframework.data.cassandra.domain.TypeWithCompositeKey;
import org.springframework.data.cassandra.domain.TypeWithKeyClass;
import org.springframework.data.cassandra.domain.TypeWithMapId;
import org.springframework.data.cassandra.domain.User;
import org.springframework.data.cassandra.domain.UserToken;
import org.springframework.data.cassandra.test.util.RowMockUtil;
import org.springframework.data.projection.EntityProjection;
import org.springframework.data.projection.EntityProjectionIntrospector;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.core.data.DefaultTupleValue;
import com.datastax.oss.driver.internal.core.type.DefaultTupleType;

/**
 * Unit tests for {@link MappingCassandraConverter}.
 *
 * @author Mark Paluch
 * @author Christoph Strobl
 * @soundtrack Outlandich - Dont Leave Me Feat Cyt (Sun Kidz Electrocore Mix)
 */
public class MappingCassandraConverterUnitTests {

	private Row rowMock;

	private CassandraMappingContext mappingContext;
	private MappingCassandraConverter mappingCassandraConverter;

	@BeforeEach
	void setUp() {

		this.mappingContext = new CassandraMappingContext();

		this.mappingCassandraConverter = new MappingCassandraConverter(mappingContext);
		this.mappingCassandraConverter.afterPropertiesSet();
	}

	@Test // DATACASS-260
	void insertEnumShouldMapToString() {

		WithEnumColumns withEnumColumns = new WithEnumColumns();

		withEnumColumns.setCondition(Condition.MINT);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(withEnumColumns, insert);

		assertThat(getValues(insert)).contains("MINT");
	}

	@Test // DATACASS-260
	void shouldWriteEnumSet() {

		AllPossibleTypes entity = new AllPossibleTypes("1");
		entity.setSetOfEnum(Collections.singleton(CassandraTypeMappingIntegrationTests.Condition.MINT));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(entity, insert);

		assertThat(insert.get(CqlIdentifier.fromCql("setofenum"))).isInstanceOf(Set.class);
	}

	@Test // DATACASS-255
	void insertEnumMapsToOrdinal() {

		EnumToOrdinalMapping enumToOrdinalMapping = new EnumToOrdinalMapping();
		enumToOrdinalMapping.setAsOrdinal(Condition.USED);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(enumToOrdinalMapping, insert);

		assertThat(getValues(insert)).contains(Condition.USED.ordinal());
	}

	@Test // DATACASS-255, DATACASS-652
	void selectEnumMapsToOrdinal() {

		rowMock = RowMockUtil.newRowMock(column("asOrdinal", 1, DataTypes.INT));

		EnumToOrdinalMapping loaded = mappingCassandraConverter.read(EnumToOrdinalMapping.class, rowMock);

		assertThat(loaded.getAsOrdinal()).isEqualTo(Condition.USED);
	}

	@Test // DATACASS-260
	void insertEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(key, insert);

		assertThat(getValues(insert)).contains("MINT");
	}

	@Test // DATACASS-260
	void insertEnumInCompositePrimaryKeyShouldMapToString() {

		EnumCompositePrimaryKey key = new EnumCompositePrimaryKey();
		key.setCondition(Condition.MINT);

		CompositeKeyThing composite = new CompositeKeyThing();
		composite.setKey(key);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(composite, insert);

		assertThat(getValues(insert)).contains("MINT");
	}

	@Test // DATACASS-260
	void updateEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Where where = new Where();

		mappingCassandraConverter.write(key, where);

		assertThat(getWhereValues(where)).contains("MINT");
	}

	@Test // DATACASS-260
	void writeWhereEnumInCompositePrimaryKeyShouldMapToString() {

		EnumCompositePrimaryKey key = new EnumCompositePrimaryKey();
		key.setCondition(Condition.MINT);

		CompositeKeyThing composite = new CompositeKeyThing();
		composite.setKey(key);

		Where where = new Where();

		mappingCassandraConverter.write(composite, where);

		assertThat(getWhereValues(where)).contains("MINT");
	}

	@Test // DATACASS-260
	void writeWhereEnumAsPrimaryKeyShouldMapToString() {

		EnumPrimaryKey key = new EnumPrimaryKey();
		key.setCondition(Condition.MINT);

		Where where = new Where();

		mappingCassandraConverter.write(key, where);

		assertThat(getWhereValues(where)).contains("MINT");
	}

	@Test // DATACASS-280
	void shouldReadStringCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", "foo", DataTypes.TEXT));

		String result = mappingCassandraConverter.readRow(String.class, rowMock);

		assertThat(result).isEqualTo("foo");
	}

	@Test // DATACASS-280
	void shouldReadIntegerCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", 2, DataTypes.VARINT));

		Integer result = mappingCassandraConverter.readRow(Integer.class, rowMock);

		assertThat(result).isEqualTo(2);
	}

	@Test // DATACASS-280
	void shouldReadLongCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", 2, DataTypes.VARINT));

		Long result = mappingCassandraConverter.readRow(Long.class, rowMock);

		assertThat(result).isEqualTo(2L);
	}

	@Test // DATACASS-280
	void shouldReadDoubleCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", 2D, DataTypes.DOUBLE));

		Double result = mappingCassandraConverter.readRow(Double.class, rowMock);

		assertThat(result).isEqualTo(2D);
	}

	@Test // DATACASS-280
	void shouldReadFloatCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", 2F, DataTypes.DOUBLE));

		Float result = mappingCassandraConverter.readRow(Float.class, rowMock);

		assertThat(result).isEqualTo(2F);
	}

	@Test // DATACASS-280
	void shouldReadBigIntegerCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", BigInteger.valueOf(2), DataTypes.BIGINT));

		BigInteger result = mappingCassandraConverter.readRow(BigInteger.class, rowMock);

		assertThat(result).isEqualTo(BigInteger.valueOf(2));
	}

	@Test // DATACASS-280
	void shouldReadBigDecimalCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", BigDecimal.valueOf(2), DataTypes.DECIMAL));

		BigDecimal result = mappingCassandraConverter.readRow(BigDecimal.class, rowMock);

		assertThat(result).isEqualTo(BigDecimal.valueOf(2));
	}

	@Test // DATACASS-280
	void shouldReadUUIDCorrectly() {

		UUID uuid = UUID.randomUUID();

		rowMock = RowMockUtil.newRowMock(column("foo", uuid, DataTypes.UUID));

		UUID result = mappingCassandraConverter.readRow(UUID.class, rowMock);

		assertThat(result).isEqualTo(uuid);
	}

	@Test // DATACASS-280
	void shouldReadInetAddressCorrectly() throws UnknownHostException {

		InetAddress localHost = InetAddress.getLoopbackAddress();
		rowMock = RowMockUtil.newRowMock(column("foo", localHost, DataTypes.UUID));

		InetAddress result = mappingCassandraConverter.readRow(InetAddress.class, rowMock);

		assertThat(result).isEqualTo(localHost);
	}

	@Test // DATACASS-280, DATACASS-271
	void shouldReadTimestampCorrectly() {

		Instant instant = Instant.now();

		rowMock = RowMockUtil.newRowMock(column("foo", instant, DataTypes.TIMESTAMP));

		Date result = mappingCassandraConverter.readRow(Date.class, rowMock);

		assertThat(result).isEqualTo(Date.from(instant));
	}

	@Test // DATACASS-280, DATACASS-271
	void shouldReadInstantTimestampCorrectly() {

		Instant instant = Instant.now();

		rowMock = RowMockUtil.newRowMock(column("foo", instant, DataTypes.TIMESTAMP));

		Instant result = mappingCassandraConverter.readRow(Instant.class, rowMock);

		assertThat(result).isEqualTo(instant);
	}

	@Test // DATACASS-656
	void shouldReadAndWriteTimestampFromObject() {

		AllPossibleTypes entity = new AllPossibleTypes("1");
		entity.setInstant(Instant.now());
		entity.setTimestamp(new Date(1));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(entity, insert);

		assertThat(insert.get(CqlIdentifier.fromCql("instant"))).isInstanceOf(Instant.class);
		assertThat(insert.get(CqlIdentifier.fromCql("timestamp"))).isInstanceOf(Instant.class);
	}

	@Test // DATACASS-656, DATACASS-727
	void shouldReadAndWriteTimestampFromObjectWithConversion() {

		AllPossibleTypes entity = new AllPossibleTypes("1");
		entity.setInstant(Instant.now());
		entity.setTimestamp(new Date(1));
		entity.setJodaDateTime(new org.joda.time.DateTime(2010, 7, 4, 1, 2, 3));
		entity.setBpInstant(org.threeten.bp.Instant.now());

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(entity, insert);

		assertThat(insert.get(CqlIdentifier.fromCql("jodadatetime"))).isInstanceOf(Instant.class);
		assertThat(insert.get(CqlIdentifier.fromCql("bpinstant"))).isInstanceOf(Instant.class);
	}

	@Test // DATACASS-656
	void shouldReadAndWriteTimeFromObjectWithConversion() {

		AllPossibleTypes entity = new AllPossibleTypes("1");

		entity.setJodaLocalTime(org.joda.time.LocalTime.fromMillisOfDay(50000));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(entity, insert);

		assertThat(insert.get(CqlIdentifier.fromCql("jodalocaltime"))).isInstanceOf(LocalTime.class);
	}

	@Test // DATACASS-271
	void shouldReadDateCorrectly() {

		LocalDate date = LocalDate.ofEpochDay(1234);

		rowMock = RowMockUtil.newRowMock(column("foo", date, DataTypes.DATE));

		LocalDate result = mappingCassandraConverter.readRow(LocalDate.class, rowMock);

		assertThat(result).isEqualTo(date);
	}

	@Test // DATACASS-280
	void shouldReadBooleanCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("foo", true, DataTypes.BOOLEAN));

		Boolean result = mappingCassandraConverter.readRow(Boolean.class, rowMock);

		assertThat(result).isEqualTo(true);
	}

	@Test // DATACASS-296
	void shouldReadLocalDateCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localdate", Date.from(instant), DataTypes.TIMESTAMP));

		TypeWithLocalDate result = mappingCassandraConverter.readRow(TypeWithLocalDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(now.getYear());
		assertThat(result.localDate.getMonthValue()).isEqualTo(now.getMonthValue());
	}

	@Test // DATACASS-296
	void shouldCreateInsertWithLocalDateCorrectly() {

		java.time.LocalDate now = java.time.LocalDate.now();

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.localDate = now;

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
	}

	@Test // DATACASS-296
	void shouldCreateUpdateWithLocalDateCorrectly() {

		java.time.LocalDate now = java.time.LocalDate.now();

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.localDate = now;

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
	}

	@Test // DATACASS-296
	void shouldCreateInsertWithLocalDateListUsingCassandraDate() {

		java.time.LocalDate now = java.time.LocalDate.now();
		java.time.LocalDate localDate = java.time.LocalDate.of(2010, 7, 4);

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.list = Arrays.asList(now, localDate);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		List<LocalDate> dates = (List) insert.get(CqlIdentifier.fromCql("list"));

		assertThat(dates).contains(LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
		assertThat(dates).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	void shouldCreateInsertWithLocalDateSetUsingCassandraDate() {

		java.time.LocalDate now = java.time.LocalDate.now();
		java.time.LocalDate localDate = java.time.LocalDate.of(2010, 7, 4);

		TypeWithLocalDate typeWithLocalDate = new TypeWithLocalDate();
		typeWithLocalDate.set = new HashSet<>(Arrays.asList(now, localDate));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		Set<LocalDate> dates = (Set) insert.get(CqlIdentifier.fromInternal("set"));

		assertThat(dates).contains(LocalDate.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth()));
		assertThat(dates).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	void shouldReadLocalDateTimeUsingCassandraDateCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localDate", LocalDate.of(2010, 7, 4), DataTypes.DATE));

		TypeWithLocalDateMappedToDate result = mappingCassandraConverter.readRow(TypeWithLocalDateMappedToDate.class,
				rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthValue()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296, DATACASS-400
	void shouldCreateInsertWithLocalDateUsingCassandraDateCorrectly() {

		TypeWithLocalDateMappedToDate typeWithLocalDate = new TypeWithLocalDateMappedToDate(null,
				java.time.LocalDate.of(2010, 7, 4));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	void shouldCreateUpdateWithLocalDateUsingCassandraDateCorrectly() {

		TypeWithLocalDateMappedToDate typeWithLocalDate = new TypeWithLocalDateMappedToDate(null,
				java.time.LocalDate.of(2010, 7, 4));

		Map<CqlIdentifier, Object> update = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getValues(update)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	void shouldReadLocalDateTimeCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localDateTime", Date.from(instant), DataTypes.TIMESTAMP));

		TypeWithLocalDate result = mappingCassandraConverter.readRow(TypeWithLocalDate.class, rowMock);

		assertThat(result.localDateTime).isNotNull();
		assertThat(result.localDateTime.getYear()).isEqualTo(now.getYear());
		assertThat(result.localDateTime.getMinute()).isEqualTo(now.getMinute());
	}

	@Test // DATACASS-296
	void shouldReadInstantCorrectly() {

		LocalDateTime now = LocalDateTime.now();
		Instant instant = now.toInstant(ZoneOffset.UTC);

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("instant", Date.from(instant), DataTypes.TIMESTAMP));

		TypeWithInstant result = mappingCassandraConverter.readRow(TypeWithInstant.class, rowMock);

		assertThat(result.instant).isNotNull();
		assertThat(result.instant.getEpochSecond()).isEqualTo(instant.getEpochSecond());
	}

	@Test // DATACASS-296
	void shouldReadZoneIdCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("zoneId", "Europe/Paris", DataTypes.TEXT));

		TypeWithZoneId result = mappingCassandraConverter.readRow(TypeWithZoneId.class, rowMock);

		assertThat(result.zoneId).isNotNull();
		assertThat(result.zoneId.getId()).isEqualTo("Europe/Paris");
	}

	@Test // DATACASS-296
	void shouldReadJodaLocalDateTimeUsingCassandraDateCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localDate", LocalDate.of(2010, 7, 4), DataTypes.DATE));

		TypeWithJodaLocalDateMappedToDate result = mappingCassandraConverter
				.readRow(TypeWithJodaLocalDateMappedToDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthOfYear()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296
	void shouldCreateInsertWithJodaLocalDateUsingCassandraDateCorrectly() {

		TypeWithJodaLocalDateMappedToDate typeWithLocalDate = new TypeWithJodaLocalDateMappedToDate();
		typeWithLocalDate.localDate = new org.joda.time.LocalDate(2010, 7, 4);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	void shouldCreateUpdateWithJodaLocalDateUsingCassandraDateCorrectly() {

		TypeWithJodaLocalDateMappedToDate typeWithLocalDate = new TypeWithJodaLocalDateMappedToDate();
		typeWithLocalDate.localDate = new org.joda.time.LocalDate(2010, 7, 4);

		Map<CqlIdentifier, Object> update = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getValues(update)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	void shouldReadThreeTenBpLocalDateTimeUsingCassandraDateCorrectly() {

		rowMock = RowMockUtil.newRowMock(column("id", "my-id", DataTypes.ASCII),
				column("localDate", LocalDate.of(2010, 7, 4), DataTypes.DATE));

		TypeWithThreeTenBpLocalDateMappedToDate result = mappingCassandraConverter
				.readRow(TypeWithThreeTenBpLocalDateMappedToDate.class, rowMock);

		assertThat(result.localDate).isNotNull();
		assertThat(result.localDate.getYear()).isEqualTo(2010);
		assertThat(result.localDate.getMonthValue()).isEqualTo(7);
		assertThat(result.localDate.getDayOfMonth()).isEqualTo(4);
	}

	@Test // DATACASS-296
	void shouldCreateInsertWithThreeTenBpLocalDateUsingCassandraDateCorrectly() {

		TypeWithThreeTenBpLocalDateMappedToDate typeWithLocalDate = new TypeWithThreeTenBpLocalDateMappedToDate();
		typeWithLocalDate.localDate = org.threeten.bp.LocalDate.of(2010, 7, 4);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, insert);

		assertThat(getValues(insert)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-296
	void shouldCreateUpdateWithThreeTenBpLocalDateUsingCassandraDateCorrectly() {

		TypeWithThreeTenBpLocalDateMappedToDate typeWithLocalDate = new TypeWithThreeTenBpLocalDateMappedToDate();
		typeWithLocalDate.localDate = org.threeten.bp.LocalDate.of(2010, 7, 4);

		Map<CqlIdentifier, Object> update = new LinkedHashMap<>();

		mappingCassandraConverter.write(typeWithLocalDate, update);

		assertThat(getValues(update)).contains(LocalDate.of(2010, 7, 4));
	}

	@Test // DATACASS-206
	void updateShouldUseSpecifiedColumnNames() {

		UserToken userToken = new UserToken();
		userToken.setUserId(UUID.randomUUID());
		userToken.setToken(UUID.randomUUID());
		userToken.setAdminComment("admin comment");
		userToken.setUserComment("user comment");

		Map<CqlIdentifier, Object> update = new LinkedHashMap<>();
		Where where = new Where();

		mappingCassandraConverter.write(userToken, update);
		mappingCassandraConverter.write(userToken, where);

		assertThat(update).containsEntry(CqlIdentifier.fromCql("admincomment"), "admin comment");
		assertThat(update).containsEntry(CqlIdentifier.fromCql("user_comment"), "user comment");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("user_id"), userToken.getUserId());
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionUsingPlainId() {

		Where where = new Where();

		mappingCassandraConverter.write("42", where, mappingContext.getRequiredPersistentEntity(User.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("id"), "42");
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionUsingEntity() {

		Where where = new Where();

		User user = new User();
		user.setId("42");

		mappingCassandraConverter.write(user, where, mappingContext.getRequiredPersistentEntity(User.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("id"), "42");
	}

	@Test // DATACASS-308
	void shouldFailWriteWhereConditionUsingEntityWithNullId() {

		assertThatIllegalArgumentException().isThrownBy(() -> mappingCassandraConverter.write(new User(), new Where(),
				mappingContext.getRequiredPersistentEntity(User.class)));
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionUsingMapId() {

		Where where = new Where();

		mappingCassandraConverter.write(id("id", "42"), where, mappingContext.getRequiredPersistentEntity(User.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("id"), "42");
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionForCompositeKeyUsingEntity() {

		Where where = new Where();

		TypeWithCompositeKey entity = new TypeWithCompositeKey();
		entity.setFirstname("Walter");
		entity.setLastname("White");

		mappingCassandraConverter.write(entity, where,
				mappingContext.getRequiredPersistentEntity(TypeWithCompositeKey.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("firstname"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionForCompositeKeyUsingMapId() {

		Where where = new Where();

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), where,
				mappingContext.getRequiredPersistentEntity(TypeWithCompositeKey.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("firstname"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionForMapIdKeyUsingEntity() {

		Where where = new Where();

		TypeWithMapId entity = new TypeWithMapId();
		entity.setFirstname("Walter");
		entity.setLastname("White");

		mappingCassandraConverter.write(entity, where, mappingContext.getRequiredPersistentEntity(TypeWithMapId.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("firstname"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	void shouldWriteEnumWhereCondition() {

		Where where = new Where();

		mappingCassandraConverter.write(Condition.MINT, where,
				mappingContext.getRequiredPersistentEntity(EnumPrimaryKey.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("condition"), "MINT");
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionForMapIdKeyUsingMapId() {

		Where where = new Where();

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), where,
				mappingContext.getRequiredPersistentEntity(TypeWithMapId.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("firstname"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionForTypeWithPkClassKeyUsingEntity() {

		Where where = new Where();

		CompositeKey key = new CompositeKey();
		key.setFirstname("Walter");
		key.setLastname("White");

		TypeWithKeyClass entity = new TypeWithKeyClass();
		entity.setKey(key);

		mappingCassandraConverter.write(entity, where, mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	void shouldFailWritingWhereConditionForTypeWithPkClassKeyUsingEntityWithNullId() {

		assertThatIllegalArgumentException().isThrownBy(() -> mappingCassandraConverter.write(new TypeWithKeyClass(),
				new Where(), mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class)));
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionForTypeWithPkClassKeyUsingKey() {

		Where where = new Where();

		CompositeKey key = new CompositeKey();
		key.setFirstname("Walter");
		key.setLastname("White");

		mappingCassandraConverter.write(key, where, mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-463
	void shouldReadTypeWithCompositePrimaryKeyCorrectly() {

		// condition, localDate
		Row row = RowMockUtil.newRowMock(column("condition", "MINT", DataTypes.TEXT),
				column("localdate", LocalDate.of(2017, 1, 2), DataTypes.DATE));

		TypeWithEnumAndLocalDateKey result = mappingCassandraConverter.read(TypeWithEnumAndLocalDateKey.class, row);

		assertThat(result.id.condition).isEqualTo(Condition.MINT);
		assertThat(result.id.localDate).isEqualTo(java.time.LocalDate.of(2017, 1, 2));
	}

	@Test // DATACASS-672
	void shouldReadTypeCompositePrimaryKeyUsingEntityInstantiatorAndPropertyPopulationInKeyCorrectly() {

		// condition, localDate
		Row row = RowMockUtil.newRowMock(column("firstname", "Walter", DataTypes.TEXT),
				column("lastname", "White", DataTypes.TEXT));

		TableWithCompositeKeyViaConstructor result = mappingCassandraConverter
				.read(TableWithCompositeKeyViaConstructor.class, row);

		assertThat(result.key.firstname).isEqualTo("Walter");
		assertThat(result.key.lastname).isEqualTo("White");
	}

	@Test // DATACASS-308
	void shouldWriteWhereConditionForTypeWithPkClassKeyUsingMapId() {

		Where where = new Where();

		mappingCassandraConverter.write(id("firstname", "Walter").with("lastname", "White"), where,
				mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "Walter");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "White");
	}

	@Test // DATACASS-308
	void shouldFailWhereConditionForTypeWithPkClassKeyUsingMapIdHavingUnknownProperty() {

		assertThatIllegalArgumentException().isThrownBy(() -> mappingCassandraConverter.write(id("unknown", "Walter"),
				new Where(), mappingContext.getRequiredPersistentEntity(TypeWithMapId.class)));
	}

	@Test // DATACASS-362
	void shouldWriteWhereCompositeIdUsingCompositeKeyClass() {

		Where where = new Where();

		CompositeKey key = new CompositeKey();
		key.setFirstname("first");
		key.setLastname("last");

		mappingCassandraConverter.write(key, where, mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "first");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "last");
	}

	@Test // DATACASS-362
	void writeWhereCompositeIdUsingCompositeKeyClassViaMapId() {

		Where where = new Where();

		MapId mapId = BasicMapId.id("firstname", "first").with("lastname", "last");

		mappingCassandraConverter.write(mapId, where, mappingContext.getRequiredPersistentEntity(TypeWithKeyClass.class));

		assertThat(where).containsEntry(CqlIdentifier.fromCql("first_name"), "first");
		assertThat(where).containsEntry(CqlIdentifier.fromCql("lastname"), "last");
	}

	@Test // DATACASS-487
	void shouldReadConvertedMap() {

		LocalDate date1 = LocalDate.of(2018, 1, 1);
		LocalDate date2 = LocalDate.of(2019, 1, 1);

		Map<String, List<LocalDate>> times = Collections.singletonMap("Europe/Paris", Arrays.asList(date1, date2));

		rowMock = RowMockUtil.newRowMock(
				RowMockUtil.column("times", times, DataTypes.mapOf(DataTypes.TEXT, DataTypes.listOf(DataTypes.DATE))));

		TypeWithConvertedMap converted = this.mappingCassandraConverter.read(TypeWithConvertedMap.class, rowMock);

		assertThat(converted.times).containsKeys(ZoneId.of("Europe/Paris"));

		List<java.time.LocalDate> convertedTimes = converted.times.get(ZoneId.of("Europe/Paris"));

		assertThat(convertedTimes).hasSize(2).hasOnlyElementsOfType(java.time.LocalDate.class);
	}

	@Test // DATACASS-487
	void shouldWriteConvertedMap() {

		java.time.LocalDate date1 = java.time.LocalDate.of(2018, 1, 1);
		java.time.LocalDate date2 = java.time.LocalDate.of(2019, 1, 1);

		TypeWithConvertedMap typeWithConvertedMap = new TypeWithConvertedMap();

		typeWithConvertedMap.times = Collections.singletonMap(ZoneId.of("Europe/Paris"), Arrays.asList(date1, date2));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(typeWithConvertedMap, insert);

		List<Object> values = getValues(insert);

		assertThat(values).isNotEmpty();
		assertThat(values.get(1)).isInstanceOf(Map.class);

		Map<String, List<LocalDate>> map = (Map) values.get(1);

		assertThat(map).containsKey("Europe/Paris");
		assertThat(map.get("Europe/Paris")).hasOnlyElementsOfType(LocalDate.class);
	}

	@Test // DATACASS-189
	void writeShouldSkipTransientProperties() {

		WithTransient withTransient = new WithTransient();
		withTransient.firstname = "Foo";
		withTransient.lastname = "Bar";
		withTransient.displayName = "FooBar";

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(withTransient, insert);

		assertThat(insert).containsKey(CqlIdentifier.fromCql("firstname"))
				.doesNotContainKey(CqlIdentifier.fromCql("displayName"));
	}

	@Test // DATACASS-623
	void writeShouldSkipTransientReadProperties() {

		WithTransient withTransient = new WithTransient();
		withTransient.firstname = "Foo";
		withTransient.computedName = "FooBar";

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(withTransient, insert);

		assertThat(insert).containsKey(CqlIdentifier.fromCql("firstname"))
				.doesNotContainKey(CqlIdentifier.fromCql("computedName"));
	}

	@Test // DATACASS-741
	void shouldComputeValueInConstructor() {

		rowMock = RowMockUtil.newRowMock(RowMockUtil.column("id", "id", DataTypes.TEXT),
				RowMockUtil.column("fn", "fn", DataTypes.TEXT));

		WithValue result = this.mappingCassandraConverter.read(WithValue.class, rowMock);

		assertThat(result.id).isEqualTo("id");
		assertThat(result.firstname).isEqualTo("fn");
	}

	@Test // DATACASS-743
	void shouldConsiderCassandraTypeOnList() {

		TypeWithConvertedCollections value = new TypeWithConvertedCollections();
		value.conditionList = Arrays.asList(Condition.MINT, Condition.USED);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(value, insert);

		assertThat(insert).containsEntry(CqlIdentifier.fromCql("conditionlist"), Arrays.asList(0, 1));
	}

	@Test // DATACASS-743
	void shouldConsiderCassandraTypeOnSet() {

		TypeWithConvertedCollections value = new TypeWithConvertedCollections();
		value.conditionSet = new LinkedHashSet<>(Arrays.asList(Condition.MINT, Condition.USED));

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(value, insert);

		assertThat(insert).containsEntry(CqlIdentifier.fromCql("conditionset"), new LinkedHashSet<>(Arrays.asList(0, 1)));
	}

	@Test // DATACASS-743
	void shouldConsiderCassandraTypeOnMap() {

		TypeWithConvertedCollections value = new TypeWithConvertedCollections();
		value.conditionMap = Collections.singletonMap(Condition.MINT, Condition.USED);

		Map<CqlIdentifier, Object> insert = new LinkedHashMap<>();

		this.mappingCassandraConverter.write(value, insert);

		assertThat(insert).containsEntry(CqlIdentifier.fromCql("conditionmap"), Collections.singletonMap(0, 1));
	}

	@Test
	void shouldConsiderColumnAnnotationOnConstructor() {

		rowMock = RowMockUtil.newRowMock(RowMockUtil.column("fn", "Walter", DataTypes.ASCII),
				RowMockUtil.column("firstname", "Heisenberg", DataTypes.ASCII),
				RowMockUtil.column("lastname", "White", DataTypes.ASCII));

		WithColumnAnnotationInConstructor converted = this.mappingCassandraConverter
				.read(WithColumnAnnotationInConstructor.class, rowMock);

		assertThat(converted.firstname).isEqualTo("Walter");
		assertThat(converted.lastname).isEqualTo("White");
	}

	@Test
	void shouldConsiderElementAnnotationOnConstructor() {

		DefaultTupleValue value = new DefaultTupleValue(
				new DefaultTupleType(Arrays.asList(DataTypes.ASCII, DataTypes.ASCII, DataTypes.ASCII)));

		value.setString(0, "Zero");
		value.setString(1, "One");
		value.setString(2, "Two");

		rowMock = RowMockUtil.newRowMock(RowMockUtil.column("firstname", "Heisenberg", DataTypes.ASCII),
				RowMockUtil.column("tuple", value, value.getType()));

		WithMappedTuple converted = this.mappingCassandraConverter.read(WithMappedTuple.class, rowMock);

		assertThat(converted.firstname).isEqualTo("Heisenberg");
		assertThat(converted.tuple.firstname).isEqualTo("Two");
	}

	@Test // GH-1202
	void shouldConsiderNestedProjections() {

		DefaultTupleValue value = new DefaultTupleValue(
				new DefaultTupleType(Arrays.asList(DataTypes.ASCII, DataTypes.ASCII, DataTypes.ASCII)));

		value.setString(0, "Zero");
		value.setString(1, "One");
		value.setString(2, "Two");

		EntityProjectionIntrospector introspector = EntityProjectionIntrospector.create(
				new SpelAwareProxyProjectionFactory(), EntityProjectionIntrospector.ProjectionPredicate.typeHierarchy(),
				this.mappingContext);

		rowMock = RowMockUtil.newRowMock(RowMockUtil.column("firstname", "Heisenberg", DataTypes.ASCII),
				RowMockUtil.column("tuple", value, value.getType()));

		EntityProjection<WithMappedTupleDtoProjection, WithMappedTuple> projection = introspector
				.introspect(WithMappedTupleDtoProjection.class, WithMappedTuple.class);

		WithMappedTupleDtoProjection result = this.mappingCassandraConverter.project(projection, rowMock);

		assertThat(result.getFirstname()).isEqualTo("Heisenberg");
		assertThat(result.getTuple().getOne()).isEqualTo("One");
	}

	private static List<Object> getValues(Map<CqlIdentifier, Object> statement) {
		return new ArrayList<>(statement.values());
	}

	private static Collection<Object> getWhereValues(Where update) {
		return update.values();
	}

	@Table
	private static class EnumToOrdinalMapping {

		@PrimaryKey private String id;

		@CassandraType(type = CassandraType.Name.INT) private Condition asOrdinal;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		private Condition getAsOrdinal() {
			return asOrdinal;
		}

		private void setAsOrdinal(Condition asOrdinal) {
			this.asOrdinal = asOrdinal;
		}
	}

	@Table
	private static class WithEnumColumns {

		@PrimaryKey private String id;

		private Condition condition;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public Condition getCondition() {
			return condition;
		}

		private void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@PrimaryKeyClass
	public static class EnumCompositePrimaryKey implements Serializable {

		@PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.PARTITIONED) private Condition condition;

		private EnumCompositePrimaryKey() {}

		public EnumCompositePrimaryKey(Condition condition) {
			this.condition = condition;
		}

		public Condition getCondition() {
			return condition;
		}

		private void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@PrimaryKeyClass
	@RequiredArgsConstructor
	@lombok.Value
	public static class EnumAndDateCompositePrimaryKey implements Serializable {

		@PrimaryKeyColumn(ordinal = 1, type = PrimaryKeyType.PARTITIONED) private final Condition condition;

		@PrimaryKeyColumn(ordinal = 2, type = PrimaryKeyType.PARTITIONED) private final java.time.LocalDate localDate;
	}

	@RequiredArgsConstructor
	public static class TypeWithEnumAndLocalDateKey {

		@PrimaryKey private final EnumAndDateCompositePrimaryKey id;
	}

	@Table
	private static class EnumPrimaryKey {

		@PrimaryKey private Condition condition;

		public Condition getCondition() {
			return condition;
		}

		private void setCondition(Condition condition) {
			this.condition = condition;
		}
	}

	@Table
	private static class CompositeKeyThing {

		@PrimaryKey private EnumCompositePrimaryKey key;

		private CompositeKeyThing() {}

		public CompositeKeyThing(EnumCompositePrimaryKey key) {
			this.key = key;
		}

		public EnumCompositePrimaryKey getKey() {
			return key;
		}

		private void setKey(EnumCompositePrimaryKey key) {
			this.key = key;
		}
	}

	public enum Condition {
		MINT, USED
	}

	@PrimaryKeyClass
	private static class CompositeKeyWithPropertyAccessors {

		@PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED) private String firstname;
		@PrimaryKeyColumn private String lastname;
	}

	@Table
	@RequiredArgsConstructor
	public static class TableWithCompositeKeyViaConstructor {

		@PrimaryKey private final CompositeKeyWithPropertyAccessors key;
	}

	@Table
	private static class TypeWithLocalDate {

		@PrimaryKey private String id;

		private java.time.LocalDate localDate;
		private java.time.LocalDateTime localDateTime;

		private List<java.time.LocalDate> list;
		private Set<java.time.LocalDate> set;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to {@link LocalDate}
	 */
	@Table
	@AllArgsConstructor
	public static class TypeWithLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = CassandraType.Name.DATE) java.time.LocalDate localDate;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to Joda {@link LocalDate}
	 */
	@Table
	private static class TypeWithJodaLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = CassandraType.Name.DATE) private org.joda.time.LocalDate localDate;
	}

	/**
	 * Uses Cassandra's {@link Name#DATE} which maps by default to Joda {@link LocalDate}
	 */
	@Table
	private static class TypeWithThreeTenBpLocalDateMappedToDate {

		@PrimaryKey private String id;

		@CassandraType(type = CassandraType.Name.DATE) private org.threeten.bp.LocalDate localDate;
	}

	@Table
	private static class TypeWithInstant {

		@PrimaryKey private String id;

		private Instant instant;
	}

	@Table
	private static class TypeWithZoneId {

		@PrimaryKey private String id;

		private ZoneId zoneId;
	}

	@Table
	private static class TypeWithConvertedMap {

		@PrimaryKey private String id;

		private Map<ZoneId, List<java.time.LocalDate>> times;
	}

	private static class WithTransient {

		@Id String id;

		private String firstname;
		private String lastname;
		@Transient private String displayName;
		@ReadOnlyProperty private String computedName;
	}

	private static class TypeWithConvertedCollections {

		@CassandraType(type = CassandraType.Name.LIST,
				typeArguments = CassandraType.Name.INT) private List<Condition> conditionList;

		@CassandraType(type = CassandraType.Name.SET,
				typeArguments = CassandraType.Name.INT) private Set<Condition> conditionSet;

		@CassandraType(type = CassandraType.Name.MAP,
				typeArguments = { CassandraType.Name.INT,
						CassandraType.Name.INT }) private Map<Condition, Condition> conditionMap;

	}

	private static class WithValue {

		private final @Id String id;
		private final @Transient String firstname;

		private WithValue(String id, @Value("#root.getString(1)") String firstname) {
			this.id = id;
			this.firstname = firstname;
		}
	}

	private static class WithColumnAnnotationInConstructor {

		String firstname;
		final @Transient String lastname;

		public WithColumnAnnotationInConstructor(@Column("fn") String firstname, @Column("lastname") String lastname) {
			this.firstname = firstname;
			this.lastname = lastname;
		}
	}

	private static class WithMappedTuple {

		String firstname;
		TupleWithElementAnnotationInConstructor tuple;
	}

	@Data
	private static class WithMappedTupleDtoProjection {

		String firstname;
		TupleProjection tuple;
	}

	private static interface TupleProjection {

		String getZero();

		String getOne();
	}

	@Tuple
	private static class TupleWithElementAnnotationInConstructor {

		@Element(0) String zero;
		@Element(1) String one;
		@Element(2) String two;

		@Transient String firstname;

		public TupleWithElementAnnotationInConstructor(@Element(2) String firstname) {
			this.firstname = firstname;
		}
	}

	@ToString
	static class WithNullableEmbeddedType {

		String id;

		@Embedded.Nullable EmbeddedWithSimpleTypes nested;
	}

	@ToString
	static class WithPrefixedNullableEmbeddedType {

		String id;

		@Embedded.Nullable("prefix") EmbeddedWithSimpleTypes nested;
	}

	@ToString
	static class WithEmptyEmbeddedType {

		String id;

		@Embedded.Empty EmbeddedWithSimpleTypes nested;
	}

	@ToString
	@EqualsAndHashCode
	@NoArgsConstructor
	@AllArgsConstructor
	static class EmbeddedWithSimpleTypes {

		String firstname;
		Integer age;
		@Transient String displayName;
	}

	@Test // DATACASS-167
	void writeFlattensEmbeddedType() {

		WithNullableEmbeddedType entity = new WithNullableEmbeddedType();
		entity.id = "id-1";
		entity.nested = new EmbeddedWithSimpleTypes();
		entity.nested.firstname = "fn";
		entity.nested.age = 30;
		entity.nested.displayName = "dp-name";

		Map<CqlIdentifier, Object> sink = new LinkedHashMap<>();

		mappingCassandraConverter.write(entity, sink);

		assertThat(sink) //
				.containsEntry(CqlIdentifier.fromCql("id"), "id-1") //
				.containsEntry(CqlIdentifier.fromCql("age"), 30) //
				.containsEntry(CqlIdentifier.fromCql("firstname"), "fn") //
				.doesNotContainKey(CqlIdentifier.fromCql("displayName"));
	}

	@Test // DATACASS-167
	void writePrefixesEmbeddedType() {

		WithPrefixedNullableEmbeddedType entity = new WithPrefixedNullableEmbeddedType();
		entity.id = "id-1";
		entity.nested = new EmbeddedWithSimpleTypes();
		entity.nested.firstname = "fn";
		entity.nested.age = 30;
		entity.nested.displayName = "dp-name";

		Map<CqlIdentifier, Object> sink = new LinkedHashMap<>();

		mappingCassandraConverter.write(entity, sink);

		assertThat(sink) //
				.containsEntry(CqlIdentifier.fromCql("id"), "id-1") //
				.containsEntry(CqlIdentifier.fromCql("prefixage"), 30) //
				.containsEntry(CqlIdentifier.fromCql("prefixfirstname"), "fn") //
				.doesNotContainKey(CqlIdentifier.fromCql("displayName"));
	}

	@Test // DATACASS-167
	void writeNullEmbeddedType() {

		WithNullableEmbeddedType entity = new WithNullableEmbeddedType();
		entity.id = "id-1";
		entity.nested = null;

		Map<CqlIdentifier, Object> sink = new LinkedHashMap<>();

		mappingCassandraConverter.write(entity, sink);

		assertThat(sink) //
				.containsEntry(CqlIdentifier.fromCql("id"), "id-1") //
				.doesNotContainKey(CqlIdentifier.fromCql("age")) //
				.doesNotContainKey(CqlIdentifier.fromCql("firstname")) //
				.doesNotContainKey(CqlIdentifier.fromCql("displayName"));
	}

	@Test // DATACASS-167
	void readEmbeddedType() {

		Row source = RowMockUtil.newRowMock(column("id", "id-1", DataTypes.TEXT), column("age", 30, DataTypes.INT),
				column("firstname", "fn", DataTypes.TEXT));

		WithNullableEmbeddedType target = mappingCassandraConverter.read(WithNullableEmbeddedType.class, source);
		assertThat(target.nested).isEqualTo(new EmbeddedWithSimpleTypes("fn", 30, null));
	}

	@Test // DATACASS-167
	void readPrefixedEmbeddedType() {

		Row source = RowMockUtil.newRowMock(column("id", "id-1", DataTypes.TEXT), column("prefixage", 30, DataTypes.INT),
				column("prefixfirstname", "fn", DataTypes.TEXT));

		WithPrefixedNullableEmbeddedType target = mappingCassandraConverter.read(WithPrefixedNullableEmbeddedType.class, source);
		assertThat(target.nested).isEqualTo(new EmbeddedWithSimpleTypes("fn", 30, null));
	}

	@Test // DATACASS-167
	void readEmbeddedTypeWhenSourceDoesNotContainValues() {

		Row source = RowMockUtil.newRowMock(column("id", "id-1", DataTypes.TEXT));

		WithNullableEmbeddedType target = mappingCassandraConverter.read(WithNullableEmbeddedType.class, source);
		assertThat(target.nested).isNull();
	}

	@Test // DATACASS-1181
	void shouldApplyCustomConverterToMapLikeType() {

		CassandraCustomConversions conversions = new CassandraCustomConversions(
				Arrays.asList(JsonToStringConverter.INSTANCE, StringToJsonConverter.INSTANCE));

		this.mappingContext = new CassandraMappingContext();
		this.mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());

		this.mappingCassandraConverter = new MappingCassandraConverter(mappingContext);
		this.mappingCassandraConverter.setCustomConversions(conversions);
		this.mappingCassandraConverter.afterPropertiesSet();

		Row source = RowMockUtil.newRowMock(column("thejson", "{\"hello\":\"world\"}", DataTypes.TEXT));

		TypeWithJsonObject target = mappingCassandraConverter.read(TypeWithJsonObject.class, source);
		assertThat(target.theJson).isNotNull();
		assertThat(target.theJson.get("hello")).isEqualTo("world");
	}

	static class TypeWithJsonObject {

		JSONObject theJson;
	}

	enum StringToJsonConverter implements Converter<String, JSONObject> {
		INSTANCE;

		@Override
		public JSONObject convert(String source) {
			try {
				return (JSONObject) new JSONParser().parse(source);
			} catch (ParseException e) {
				throw new RuntimeException(e);
			}
		}

	}

	enum JsonToStringConverter implements Converter<JSONObject, String> {
		INSTANCE;

		@Override
		public String convert(JSONObject source) {
			return source.toJSONString();
		}

	}
}
