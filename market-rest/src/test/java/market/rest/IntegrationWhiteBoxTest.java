package market.rest;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.evomaster.client.java.controller.db.DbCleaner;
import org.evomaster.client.java.controller.db.SqlScriptRunner;
import org.hamcrest.Matchers;
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

import static io.restassured.RestAssured.basic;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.preemptive;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class IntegrationWhiteBoxTest {

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
		try (InputStream in = IntegrationWhiteBoxTest.class.getResourceAsStream("/data.sql")) {
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
	public void getOrdersGetCartAddItemPayGetCartGetOrder() {
		RestAssured.authentication = preemptive().basic("ivan.petrov@yandex.ru", "petrov");

		given()
		.when()
			.get("/customer/orders")
		.then()
			.statusCode(200)
			.and()
			.body("", hasSize(1));
//			.and()
//			.body("cartItems[0].productId", equalTo(5));

		given()
		.when()
			.get("/customer/cart")
		.then()
			.statusCode(200)
			.and()
			.body("empty", equalTo(false))
			.and()
			.body("cartItems", hasSize(1));

		Response putCustomerCartResponse =
		given()
			.contentType("application/json")
			.body("{\n" +
				"    \"productId\": 2,\n" +
				"    \"quantity\": 5\n" +
				"}")
		.when()
			.put("/customer/cart");

		// Save totalCost of the cart
		Float totalCostCart = new JsonPath(putCustomerCartResponse.asString()).get("totalCost");

		// Assertions
		putCustomerCartResponse
		.then()
			.statusCode(200)
			.and()
			.body("cartItems", hasSize(2));

		// Pay cart and check that totalCost equals the cost previously extracted
		Response payResponse =
			given()
				.contentType("application/json")
				.body("{\n" +
					"  \"ccNumber\": \"4396785166007278\"\n" +
					"}")
				.when()
				.post("/customer/cart/pay");

		// Save totalCost of the cart
		Integer payId = new JsonPath(payResponse.asString()).get("id");

		// Assertions
		payResponse
			.then()
			.statusCode(201)
			.and()
			.body("totalCost", equalTo(totalCostCart));

		// Check that cart is now empty, after paying the order
		given()
		.when()
			.get("/customer/cart")
		.then()
			.statusCode(200)
			.and()
			.body("empty", equalTo(true))
			.and()
			.body("cartItems", hasSize(0));

		// Retrieve order just paid based on the ID extracted form previous response
		given()
			.pathParam("orderId", payId)
		.when()
			.get("/customer/orders/{orderId}")
		.then()
			.statusCode(200)
			.and()
			.body("totalCost", equalTo(totalCostCart));
	}

}
