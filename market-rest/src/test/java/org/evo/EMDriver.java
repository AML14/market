package org.evo;

import org.evomaster.client.java.controller.AuthUtils;
import org.evomaster.client.java.controller.EmbeddedSutController;
import org.evomaster.client.java.controller.InstrumentedSutStarter;
import org.evomaster.client.java.controller.api.dto.database.schema.DatabaseType;
import org.evomaster.client.java.controller.db.DbCleaner;
import org.evomaster.client.java.controller.db.SqlScriptRunner;
import org.evomaster.client.java.controller.internal.db.DbSpecification;
import org.evomaster.client.java.controller.problem.ProblemInfo;
import org.evomaster.client.java.controller.problem.RestProblem;
import org.evomaster.client.java.controller.api.dto.AuthenticationDto;
import org.evomaster.client.java.controller.api.dto.SutInfoDto;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Class used to start/stop the SUT. This will be controller by the EvoMaster process
 */
public class EMDriver extends EmbeddedSutController {

	public static void main(String[] args) {

		int port = 40100;
		if (args.length > 0) {
			port = Integer.parseInt(args[0]);
		}

		EMDriver controller = new EMDriver(port);
		InstrumentedSutStarter starter = new InstrumentedSutStarter(controller);

		starter.start();
	}


	private ConfigurableApplicationContext ctx;
	private Connection connection;
	private final List<String> sqlCommands;
	private List<DbSpecification> dbSpecification;


	public EMDriver() {
		this(40100);
	}

	public EMDriver(int port) {
		setControllerPort(port);

		try (InputStream in = getClass().getResourceAsStream("/data.sql")) {
			sqlCommands = (new SqlScriptRunner()).readCommands(new InputStreamReader(in));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String startSut() {

		ctx = SpringApplication.run(market.RestApplication.class, new String[]{
			"--server.port=8081",
			"--spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;",
			"--spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		});

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

		dbSpecification = Arrays.asList(new DbSpecification(DatabaseType.H2,connection)
			.withDisabledSmartClean());

		return "http://localhost:" + getSutPort();
	}

	protected int getSutPort() {
		return (Integer) ((Map) ctx.getEnvironment()
			.getPropertySources().get("server.ports").getSource())
			.get("local.server.port");
	}


	@Override
	public boolean isSutRunning() {
		return ctx != null && ctx.isRunning();
	}

	@Override
	public void stopSut() {
		ctx.stop();
	}

	@Override
	public String getPackagePrefixesToCover() {
		return "market.";
	}

	@Override
	public void resetStateOfSUT() {
		DbCleaner.clearDatabase_H2(connection);
		SqlScriptRunner.runCommands(connection, sqlCommands);
	}

	@Override
	public ProblemInfo getProblemInfo() {
		return new RestProblem(
			"http://localhost:8081/v2/api-docs",
			null
		);
	}

	@Override
	public SutInfoDto.OutputFormat getPreferredOutputFormat() {
		return SutInfoDto.OutputFormat.JAVA_JUNIT_4;
	}

	@Override
	public List<AuthenticationDto> getInfoForAuthentication() {
		return Arrays.asList(
			AuthUtils.getForAuthorizationHeader("admin", "Basic YWRtaW46cGFzc3dvcmQ="),
			AuthUtils.getForAuthorizationHeader("petrov", "Basic aXZhbi5wZXRyb3ZAeWFuZGV4LnJ1OnBldHJvdg==")
		);
	}

	@Override
	public List<DbSpecification> getDbSpecifications() {
		return dbSpecification;
	}

}
