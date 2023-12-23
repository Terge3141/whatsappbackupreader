package whatsappbackupreader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

public class Program {
	
	public static void main(String[] args) throws WhatsappBackupReaderException, SQLException {
		Path cryptPath = Paths.get(args[0]);
		Path keyPath = Paths.get(args[1]);
		Path outputPath = Paths.get(args[2]);
		
		DatabaseDumper dumper = DatabaseDumper.of(cryptPath, keyPath, outputPath);
		dumper.setCreateExtraSqlViews(true);
		
		dumper.run();
	}

}
