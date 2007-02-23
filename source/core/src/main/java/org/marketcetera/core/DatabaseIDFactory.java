package org.marketcetera.core;


import java.sql.*;

@ClassVersion("$Id$")
public class DatabaseIDFactory extends DBBackedIDFactory {

    public static final String TABLE_NAME = "id_repository";
    public static final String COL_NAME = "nextAllowedID";

    private String dbURL;
    private String dbDriver;
    private String dbTable;
    private String dbColumn;
    private String dbLogin;
    private String dbPassword;
    private int mCacheQuantity;
    private Connection dbConnection;
    static final int NUM_IDS_GRABBED = 1000;

    protected DatabaseIDFactory(String dburl, String driver, String login, String password, String table,
                                String column, int quantity) {
        mCacheQuantity = quantity;
        dbColumn = column;
        dbDriver = driver;
        dbTable = table;
        dbURL = dburl;

        dbLogin = login;
        dbPassword = password;
    }

    public final void init() throws SQLException, ClassNotFoundException, NoMoreIDsException {
        Class.forName(dbDriver);

        dbConnection = DriverManager.getConnection(dbURL, dbLogin, dbPassword);
        dbConnection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        grabIDs();
    }


    /** Helper function intended to be overwritten by subclasses.
     * Thsi is where the real requiest for IDs happens
     * It is wrapped by a try/catch block higher up, so that we can
     * fall back onto an inMemory id factory if the request fails.
     */
    protected void performIDRequest() throws Exception {
        Statement stmt = dbConnection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
        ResultSet set = null;
        set = stmt.executeQuery("SELECT id, " + dbColumn + " FROM " + dbTable);
        if (!set.next()) {
            set.moveToInsertRow();
            set.insertRow();
            set.updateInt(dbColumn, NUM_IDS_GRABBED);
            set.moveToCurrentRow();
            set.next();
        }
        int nextID = set.getInt(dbColumn);
        int upTo = nextID + mCacheQuantity;
        set.updateInt(dbColumn, upTo);
        set.updateRow();
        stmt.close();
        setMaxAllowedID(upTo);
        setNextID(nextID);
    }

}
