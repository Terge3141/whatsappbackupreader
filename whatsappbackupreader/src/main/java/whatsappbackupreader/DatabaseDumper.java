package whatsappbackupreader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabaseDumper {
	private static Logger logger = LogManager.getLogger(DatabaseDumper.class);
	private Path outputPath;

	private WhatsappBackupReader wbr;
	private boolean createExtraSqlViews;
	
	private List<String> sqlViewCmds;

	public static DatabaseDumper of(Path keyPath, Path cryptPath, Path outputPath)
			throws WhatsappBackupReaderException {
		logger.info("Reading key file from '{}'", keyPath);
		logger.info("Reading crypt file from '{}'", cryptPath);

		return new DatabaseDumper(keyPath, cryptPath, outputPath);
	}

	private DatabaseDumper(Path keyPath, Path cryptPath, Path outputPath) throws WhatsappBackupReaderException {
		this.outputPath = outputPath;
		this.wbr = new WhatsappBackupReader(keyPath, cryptPath, outputPath);
		
		sqlViewCmds = new ArrayList<String>();
        try {
                sqlViewCmds.add(loadResourceToString("00-notorig_knowncontacts.sql.txt"));
                sqlViewCmds.add(loadResourceToString("01-v_allcontacts.sql.txt"));
                sqlViewCmds.add(loadResourceToString("02-v_chatnames.sql.txt"));
                sqlViewCmds.add(loadResourceToString("03-v_messages_with_ids.sql.txt"));
                sqlViewCmds.add(loadResourceToString("04-v_messages.sql.txt"));
        } catch (IOException e) {
                String msg = "Cannot load internal sql resource file";
                throw new WhatsappBackupReaderException(msg, e);
        }

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

	private void createExtraSqlViews() throws SQLException {
		String url = String.format("jdbc:sqlite:%s", outputPath);
		Connection connection = DriverManager.getConnection(url);
		connection.setAutoCommit(false);
		Statement stmt = connection.createStatement();
		for(String sql : this.sqlViewCmds) {
			stmt.addBatch(sql);
		}
		
		stmt.addBatch("INSERT INTO notorig_knowncontacts (\"user\", \"name\") VALUES (\"-1\", \"@@MYSELF@@\")");
		
		stmt.executeBatch();
		
		connection.commit();
		connection.close();
	}

	public boolean isCreateExtraSqlViews() {
		return createExtraSqlViews;
	}

	public void setCreateExtraSqlViews(boolean createExtraSqlViews) {
		this.createExtraSqlViews = createExtraSqlViews;
	}
	
	private String loadResourceToString(String resourceName) throws IOException, WhatsappBackupReaderException {
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
		if(inputStream==null) {
			throw new WhatsappBackupReaderException("Could not load resource: " + resourceName);
		}
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
	}

}
