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

public class CartRestControllerBlackBoxTest {

	@BeforeClass
	public static void setUp() {
		// Custom settings for REST Assured
		RestAssured.baseURI = "http://localhost:8081";
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
