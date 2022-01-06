package io.openems.edge.application;

import java.net.InetSocketAddress;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.google.common.base.Strings;

import info.faljse.SDNotify.SDNotify;
import io.openems.common.OpenemsConstants;

@Component(immediate = true)
public class EdgeApp {

	private final Logger log = LoggerFactory.getLogger(EdgeApp.class);

	@Activate
	void activate() {
		String message = "OpenEMS version [" + OpenemsConstants.VERSION + "] started";
		String line = Strings.repeat("=", message.length());
		this.log.info(line);
		this.log.info(message);
		this.log.info(line);

		// Announce Operating System that OpenEMS Edge started
		String socketName = System.getenv().get("NOTIFY_SOCKET");
		if (socketName != null && socketName.length() != 0) {
			if (SDNotify.isAvailable()) {
				SDNotify.sendNotify();
			}
		}

		CassandraSample cassandraSample = new CassandraSample();
		cassandraSample.doCRUDOperations();

		System.out.println("Server - started");
	}

	@Deactivate
	void deactivate() {
		this.log.debug("Deactivate EdgeApp");
	}

}

class CassandraSample {
	public CassandraSample() {
	}

	public void doCRUDOperations() {
		
		// TO DO: Fill in your own host, port, and data center
		try (CqlSession session = CqlSession.builder().addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
				.withKeyspace("demo").withLocalDatacenter("datacenter1").build()) {

			setUser(session, "Jones", 35, "Austin", "bob@example.com", "Bob");
			getUser(session, "Jones");
			updateUser(session, 36, "Jones");
			getUser(session, "Jones");
			deleteUser(session, "Jones");
		}
	}

	private static void setUser(CqlSession session, String lastname, int age, String city, String email,
			String firstname) {

		// TO DO: execute SimpleStatement that inserts one user into the table
		session.execute(
				SimpleStatement.builder("INSERT INTO users (lastname, age, city, email, firstname) VALUES (?,?,?,?,?)")
						.addPositionalValues(lastname, age, city, email, firstname).build());
	}

	private static void getUser(CqlSession session, String lastname) {

		// TO DO: execute SimpleStatement that retrieves one user from the table
		// TO DO: print firstname and age of user
		ResultSet rs = session.execute(
				SimpleStatement.builder("SELECT * FROM users WHERE lastname=?").addPositionalValue(lastname).build());

		Row row = rs.one();
		System.out.format("%s %d\n", row.getString("firstname"), row.getInt("age"));
	}

	private static void updateUser(CqlSession session, int age, String lastname) {

		// TO DO: execute SimpleStatement that updates the age of one user
		session.execute(SimpleStatement.builder("UPDATE users SET age =?  WHERE lastname =? ")
				.addPositionalValues(age, lastname).build());
	}

	private static void deleteUser(CqlSession session, String lastname) {

		// TO DO: execute SimpleStatement that deletes one user from the table
		session.execute(
				SimpleStatement.builder("DELETE FROM users WHERE lastname=?").addPositionalValue(lastname).build());

	}
}