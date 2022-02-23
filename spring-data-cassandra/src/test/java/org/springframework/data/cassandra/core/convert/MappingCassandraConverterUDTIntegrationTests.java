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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.core.mapping.SimpleUserTypeResolver;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;
import org.springframework.data.cassandra.core.mapping.UserTypeResolver;
import org.springframework.data.cassandra.domain.AllPossibleTypes;
import org.springframework.data.cassandra.repository.support.AbstractSpringDataEmbeddedCassandraIntegrationTest;
import org.springframework.data.cassandra.repository.support.IntegrationTestConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.data.UdtValue;

/**
 * Integration tests for UDT types through {@link MappingCassandraConverter}.
 *
 * @author Mark Paluch
 */
@SpringJUnitConfig
public class MappingCassandraConverterUDTIntegrationTests extends AbstractSpringDataEmbeddedCassandraIntegrationTest {

	private static AtomicBoolean initialized = new AtomicBoolean();

	@Configuration
	public static class Config extends IntegrationTestConfig {

		@Override
		public SchemaAction getSchemaAction() {
			return SchemaAction.NONE;
		}

		@Override
		public String[] getEntityBasePackages() {
			return new String[] { AllPossibleTypes.class.getPackage().getName() };
		}

		@Override
		public CassandraCustomConversions customConversions() {
			return new CassandraCustomConversions(Arrays.asList(new UDTToCurrencyConverter(),
					new CurrencyToUDTConverter(new SimpleUserTypeResolver(getRequiredSession()))));
		}
	}

	@Autowired CqlSession session;
	@Autowired MappingCassandraConverter converter;

	@BeforeEach
	void setUp() {

		if (initialized.compareAndSet(false, true)) {

			session.execute("DROP TABLE IF EXISTS addressbook");
			session.execute("CREATE TYPE IF NOT EXISTS address (zip text, city text, streetlines list<text>)");
			session.execute("CREATE TABLE addressbook (id text PRIMARY KEY, currentaddress FROZEN<address>, "
					+ "alternate FROZEN<address>, previousaddresses FROZEN<list<address>>)");

			session.execute("DROP TABLE IF EXISTS bank");
			session.execute("CREATE TYPE IF NOT EXISTS currency (currency text)");
			session.execute(
					"CREATE TABLE bank (id text PRIMARY KEY, currency FROZEN<currency>, othercurrencies FROZEN<list<currency>>)");

			session.execute("DROP TABLE IF EXISTS money");
			session.execute("CREATE TYPE IF NOT EXISTS currency (currency text)");
			session.execute("CREATE TABLE money (currency FROZEN<currency> PRIMARY KEY)");

			session.execute("DROP TABLE IF EXISTS car");
			session.execute("CREATE TYPE IF NOT EXISTS manufacturer (name text)");
			session.execute("CREATE TYPE IF NOT EXISTS engine (manufacturer FROZEN<manufacturer>)");
			session.execute("CREATE TABLE car (id text PRIMARY KEY, engine FROZEN<engine>)");

			session.execute("DROP TABLE IF EXISTS supplier");
			session.execute(
					"CREATE TABLE supplier (id text PRIMARY KEY, acceptedCurrencies frozen<map<manufacturer, list<currency>>>)");

		} else {

			session.execute("TRUNCATE addressbook");
			session.execute("TRUNCATE bank");
			session.execute("TRUNCATE money");
			session.execute("TRUNCATE car");
			session.execute("TRUNCATE supplier");
		}
	}

	@Test // DATACASS-172
	void shouldReadMappedUdt() {

		session.execute("INSERT INTO addressbook (id, currentaddress) " + "VALUES ('1', "
				+ "{zip:'69469', city: 'Weinheim', streetlines: ['Heckenpfad', '14']})");

		ResultSet resultSet = session.execute("SELECT * from addressbook");
		AddressBook addressBook = converter.read(AddressBook.class, resultSet.one());

		assertThat(addressBook.getCurrentaddress()).isNotNull();

		AddressUserType address = addressBook.getCurrentaddress();
		assertThat(address.getCity()).isEqualTo("Weinheim");
		assertThat(address.getZip()).isEqualTo("69469");
		assertThat(address.getStreetLines()).contains("Heckenpfad", "14");
	}

	@Test // DATACASS-172
	void shouldReadMappedUdtCollection() {

		session.execute("INSERT INTO addressbook (id,  previousaddresses) " + "VALUES ('1', "
				+ " [{zip:'53773', city: 'Bonn'}, {zip:'12345', city: 'Bonn'}])");

		ResultSet resultSet = session.execute("SELECT * from addressbook");
		AddressBook addressBook = converter.read(AddressBook.class, resultSet.one());

		assertThat(addressBook.getPreviousaddresses()).hasSize(2);

		AddressUserType address = addressBook.getPreviousaddresses().get(0);

		assertThat(address.getCity()).isEqualTo("Bonn");
		assertThat(address.getZip()).isEqualTo("53773");
		assertThat(address.getStreetLines()).isEmpty();
	}

	@Test // DATACASS-172
	void shouldReadUdt() {

		session.execute("INSERT INTO addressbook (id, alternate) " + "VALUES ('1', "
				+ "{zip:'69469', city: 'Weinheim', streetlines: ['Heckenpfad', '14']})");

		ResultSet resultSet = session.execute("SELECT * from addressbook");
		AddressBook addressBook = converter.read(AddressBook.class, resultSet.one());

		assertThat(addressBook.getAlternate()).isNotNull();
		assertThat(addressBook.getAlternate().getString("city")).isEqualTo("Weinheim");
		assertThat(addressBook.getAlternate().getString("zip")).isEqualTo("69469");
	}

	@Test // DATACASS-172
	void shouldReadUdtWithCustomConversion() {

		session.execute("INSERT INTO bank (id, currency) " + "VALUES ('1', {currency:'EUR'})");

		ResultSet resultSet = session.execute("SELECT * from bank");
		Bank addressBook = converter.read(Bank.class, resultSet.one());

		assertThat(addressBook.getCurrency()).isNotNull();
		assertThat(addressBook.getCurrency().getCurrencyCode()).isEqualTo("EUR");
	}

	@Test // DATACASS-172
	void shouldReadUdtListWithCustomConversion() {

		session.execute("INSERT INTO bank (id, othercurrencies) " + "VALUES ('1', [{currency:'EUR'}])");

		ResultSet resultSet = session.execute("SELECT * from bank");
		Bank addressBook = converter.read(Bank.class, resultSet.one());

		assertThat(addressBook.getOtherCurrencies()).hasSize(1).contains(Currency.getInstance("EUR"));
	}

	@Test // DATACASS-172
	void shouldReadNestedUdt() {

		session.execute("INSERT INTO car (id, engine)  VALUES ('1',  {manufacturer: {name:'a good one'}})");

		ResultSet resultSet = session.execute("SELECT * from car");
		Car car = converter.read(Car.class, resultSet.one());

		assertThat(car.getEngine()).isNotNull();
		assertThat(car.getEngine().getManufacturer()).isNotNull();
		assertThat(car.getEngine().getManufacturer().getName()).isEqualTo("a good one");
	}

	@Test // DATACASS-487
	void shouldReadUdtInMap() {

		this.session.execute("INSERT INTO supplier (id, acceptedCurrencies)"
				+ " VALUES ('1', {{name:'a good one'}:[{currency:'EUR'},{currency:'USD'}]})");

		ResultSet resultSet = this.session.execute("SELECT * FROM supplier");
		Supplier supplier = this.converter.read(Supplier.class, resultSet.one());

		assertThat(supplier.getAcceptedCurrencies()).isNotEmpty();

		List<Currency> currencies = supplier.getAcceptedCurrencies().get(new Manufacturer("a good one"));

		assertThat(currencies).contains(Currency.getInstance("EUR"), Currency.getInstance("USD"));
	}

	@Table
	@Getter
	@AllArgsConstructor
	private static class Bank {

		@Id String id;
		Currency currency;
		List<Currency> otherCurrencies;
	}

	@Data
	@Table
	public static class Money {
		@Id private Currency currency;
	}

	@Table
	@AllArgsConstructor
	@Getter
	public static class MoneyTransfer {

		@Id String id;

		private Currency currency;
	}

	@Table
	@Getter
	@AllArgsConstructor
	private static class Car {

		@Id String id;
		Engine engine;
	}

	@UserDefinedType
	@Getter
	@AllArgsConstructor
	private static class Engine {
		Manufacturer manufacturer;
	}

	@UserDefinedType
	@Data
	@AllArgsConstructor
	private static class Manufacturer {
		String name;
	}

	@Table
	@Data
	@AllArgsConstructor
	private static class Supplier {

		@Id String id;
		Map<Manufacturer, List<Currency>> acceptedCurrencies;
	}

	@Data
	@Table
	public static class AddressBook {

		@Id private String id;

		private AddressUserType currentaddress;
		private List<AddressUserType> previousaddresses;
		private UdtValue alternate;
	}

	@Data
	@Table
	public static class WithUdtId {
		@Id private UdtValue id;
	}

	@Data
	@Table
	public static class WithMappedUdtId {
		@Id private AddressUserType id;
	}

	@UserDefinedType("address")
	@Data
	public static class AddressUserType {

		String zip;
		String city;

		List<String> streetLines;
	}

	private static class UDTToCurrencyConverter implements Converter<UdtValue, Currency> {

		@Override
		public Currency convert(UdtValue source) {
			return Currency.getInstance(source.getString("currency"));
		}
	}

	private static class CurrencyToUDTConverter implements Converter<Currency, UdtValue> {

		private final UserTypeResolver userTypeResolver;

		CurrencyToUDTConverter(UserTypeResolver userTypeResolver) {
			this.userTypeResolver = userTypeResolver;
		}

		@Override
		public UdtValue convert(Currency source) {
			com.datastax.oss.driver.api.core.type.UserDefinedType userType = userTypeResolver
					.resolveType(CqlIdentifier.fromCql("currency"));
			UdtValue udtValue = userType.newValue();
			udtValue.setString("currency", source.getCurrencyCode());
			return udtValue;
		}
	}
}
