package whatsappbackupreader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseDumper {
	private static Logger logger = LogManager.getLogger(DatabaseDumper.class);
	private Path outputPath;

	private WhatsappBackupReader wbr;
	private boolean createExtraSqlViews;
	
	private List<String> sqlViewCmds;
	
	private Hashtable<String, String> contacts;
	
	private static final Map<Integer, String> messageTypes = Stream.of(new Object[][] { 
	    {0, "TEXT"},
		{1, "PICTURE"},
		{2, "AUDIO"},
		{3, "VIDEO"},
		{4, "CONTACT"},
		{5, "STATIC_LOCATION"},
		{7, "END-TO-END_ENCRYPTION"},
		{9, "DOCUMENT"},
		{10, "MISSED_VIDEO_CALL"},
		{13, "ANIMATION"},
		{15, "DELETED_MESSAGE"},
		{16, "LIVE_LOCATION"},
		{20, "STICKER"},
		{24, "INVITATION_TO_WHATSAPP_GROUP"}
	 }).collect(Collectors.toMap(data -> (Integer) data[0], data -> (String) data[1]));

	public static DatabaseDumper of(Path cryptPath, Path keyPath, Path outputPath)
			throws WhatsappBackupReaderException {
		logger.info("Reading key file from '{}'", keyPath);
		logger.info("Reading crypt file from '{}'", cryptPath);

		try {
			return new DatabaseDumper(cryptPath, Files.readAllBytes(keyPath), outputPath);
		} catch (IOException e) {
			throw new WhatsappBackupReaderException("Cannot open key file", e);
		}
	}
	
	public static DatabaseDumper of(Path cryptPath, byte[] key, Path outputPath)
			throws WhatsappBackupReaderException {
		logger.info("Reading crypt file from '{}'", cryptPath);

		return new DatabaseDumper(cryptPath, key, outputPath);
	}

	private DatabaseDumper(Path cryptPath, byte[] key, Path outputPath) throws WhatsappBackupReaderException {
		this.outputPath = outputPath;
		this.wbr = new WhatsappBackupReader(cryptPath, key, outputPath);
		
		sqlViewCmds = new ArrayList<String>();
        try {
                sqlViewCmds.add(loadResourceToString("00-notorig_knowncontacts.sql.txt"));
                sqlViewCmds.add(loadResourceToString("01-notorig_messagetypes.sql.txt"));
                sqlViewCmds.add(loadResourceToString("02-v_allcontacts.sql.txt"));
                sqlViewCmds.add(loadResourceToString("03-v_chatnames.sql.txt"));
                sqlViewCmds.add(loadResourceToString("04-v_messages_with_ids.sql.txt"));
                sqlViewCmds.add(loadResourceToString("05-v_messages.sql.txt"));
        } catch (IOException e) {
                String msg = "Cannot load internal sql resource file";
                throw new WhatsappBackupReaderException(msg, e);
        }

        resetContacts();
	}

	public void run() throws WhatsappBackupReaderException, SQLException {
		logger.info("Start dump");
		wbr.decrypt();

		if (isCreateExtraSqlViews()) {
			logger.info("Creating extra views");
			createExtraSqlViews();
			logger.info("Done");
		}
	}
	
	private void resetContacts() {
		this.contacts = new Hashtable<String, String>();
		this.contacts.put("-1", "@@MYSELF@@");
	}

	private void createExtraSqlViews() throws SQLException {
		String url = String.format("jdbc:sqlite:%s", outputPath);
		Connection connection = DriverManager.getConnection(url);
		connection.setAutoCommit(false);
		Statement stmt = connection.createStatement();
		for(String sql : this.sqlViewCmds) {
			stmt.addBatch(sql);
		}
		
		stmt.executeBatch();
		
		createContactsTable(connection);
		createMessageTypesTable(connection);
		
		connection.commit();
		connection.close();
	}

	private void createContactsTable(Connection connection) throws SQLException {
		for(String phone : contacts.keySet()) {
			PreparedStatement pstmt = connection.prepareStatement("INSERT INTO notorig_knowncontacts (user, name) VALUES(?, ?)");
			pstmt.setString(1, phone);
			pstmt.setString(2, contacts.get(phone));
			
			pstmt.executeUpdate();
		}
	}
	
	private void createMessageTypesTable(Connection con) throws SQLException {
		for(int mtype : messageTypes.keySet()) {
			String typeDescription = messageTypes.get(mtype);
			
			PreparedStatement pstmt = con.prepareStatement("INSERT INTO notorig_messagetypes(message_type, message_description) VALUES(?, ?)");
			pstmt.setInt(1, mtype);
			pstmt.setString(2, typeDescription);
			
			pstmt.executeUpdate();
		}
	}

	public boolean isCreateExtraSqlViews() {
		return createExtraSqlViews;
	}

	public void setCreateExtraSqlViews(boolean createExtraSqlViews) {
		this.createExtraSqlViews = createExtraSqlViews;
	}
	
	public void readContacts(Path contactPath) throws IOException {
		List<String> lines = Files.readAllLines(contactPath);
		int linenr = 1;
		for(String line : lines) {
			StringTokenizer st = new StringTokenizer(line, ";");
			
			if(st.countTokens() != 2) {
				logger.warn("Incorrect number of entries in line " + linenr + ". Skipping");
			} else {
				String phone = st.nextToken().trim();
				String name = st.nextToken().trim();
				
				contacts.put(phone, name);
			}
			
			linenr++;
		}
	}
	
	private String loadResourceToString(String resourceName) throws IOException, WhatsappBackupReaderException {
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
		if(inputStream==null) {
			throw new WhatsappBackupReaderException("Could not load resource: " + resourceName);
		}
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
	}

}
