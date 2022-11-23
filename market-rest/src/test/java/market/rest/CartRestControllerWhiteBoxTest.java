package market.rest;

import io.restassured.RestAssured;
import org.evomaster.client.java.controller.db.DbCleaner;
import org.evomaster.client.java.controller.db.SqlScriptRunner;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class CartRestControllerWhiteBoxTest {

	private static ConfigurableApplicationContext ctx;
	private static Connection connection;
	private static List<String> sqlCommands;

	@BeforeClass
	public static void setUp() {
		// Custom settings for REST Assured
		RestAssured.baseURI = "http://localhost:8081";

		// Start SUT
		ctx = SpringApplication.run(market.RestApplication.class, new String[]{
			"--server.port=8081",
			"--spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;",
			"--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		});

		// Get DB connection to reset it before each test
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}
		JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);

		try {
			connection = jdbc.getDataSource().getConnection();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		// Get SQL commands that will be run before each test
		try (InputStream in = CartRestControllerWhiteBoxTest.class.getResourceAsStream("/data.sql")) {
			sqlCommands = (new SqlScriptRunner()).readCommands(new InputStreamReader(in));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Before
	public void resetDB() {
		DbCleaner.clearDatabase_H2(connection);
		SqlScriptRunner.runCommands(connection, sqlCommands);
	}

	@Test
	public void getCartTest() {
		given()
			.auth().preemptive().basic("ivan.petrov@yandex.ru", "petrov")
		.when()
			.get("/customer/cart")
		.then()
			.statusCode(200)
			.and()
			.body("totalItems", equalTo(1))
			.and()
			.body("cartItems[0].productId", equalTo(5));
	}

	@Test
	public void addItemTest() {
		given()
			.auth().preemptive().basic("ivan.petrov@yandex.ru", "petrov")
			.contentType("application/json")
			.body("{\n" +
				"    \"productId\": 2,\n" +
				"    \"quantity\": 5\n" +
				"}")
		.when()
			.put("/customer/cart")
		.then()
			.statusCode(200)
			.and()
			.body("totalItems", equalTo(2))
			.and()
			.body("cartItems[0].productId", equalTo(5))
			.and()
			.body("cartItems[1].productId", equalTo(2));
	}
}
