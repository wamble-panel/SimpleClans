package net.sacredlabyrinth.phaed.simpleclans.storage;

import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * @author cc_madelg
 */
public class MySQLCore implements DBCore {

    private static final long VALIDATION_INTERVAL_MILLIS = 30_000;
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final Logger log;
    private long lastValidated;
    private Connection connection;
    private final String host;
    private final String username;
    private final String password;
    private final String database;
    private final int port;

    /**
     * @param host     The host
     * @param database The database
     * @param username The username
     * @param password The password
     */
    public MySQLCore(String host, String database, int port, String username, String password) {
        this.database = database;
        this.port = port;
        this.host = host;
        this.username = username;
        this.password = password;
        this.log = SimpleClans.getInstance().getLogger();
        initialize();
    }

    /**
     * @return true if a new connection was opened
     */
    private boolean connect() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&characterEncoding=utf-8&autoReconnect=true&useSSL=false", username, password);
            return true;
        } catch (ClassNotFoundException e) {
            log.severe("ClassNotFoundException! " + e.getMessage());
        } catch (SQLException e) {
            log.severe("SQLException! " + e.getMessage());
        }
        return false;
    }

    @Override
    public synchronized Connection getConnection() {
        try {
            long now = System.currentTimeMillis();
            if (connection == null || connection.isClosed()) {
                initialize();
            } else if (now - lastValidated > VALIDATION_INTERVAL_MILLIS) {
                // isValid() is a network round trip; doing it before every query doubled the
                // cost of each one. Check periodically, with a timeout (0 meant wait forever).
                if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                    lastValidated = now;
                } else {
                    initialize();
                }
            }
        } catch (SQLException e) {
            initialize();
        }
        return connection;
    }

    private void initialize() {
        // Only trust the connection for the validation interval if it actually opened;
        // otherwise the next call retries straight away.
        lastValidated = connect() ? System.currentTimeMillis() : 0;
    }

    @Override
    public synchronized void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            log.severe("Failed to close database connection! " + e.getMessage());
        }
    }

}
