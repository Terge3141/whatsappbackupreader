package whatsappbackupreader;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

public class Program {
	
	public static void main(String[] args) throws WhatsappBackupReaderException, SQLException, IOException {
		if(args.length<3) {
			System.err.println("usage: Program <cryptpath> <keypath> <outputpath> [<contactspath>]");
			System.exit(1);
		}
		Path cryptPath = Paths.get(args[0]);
		Path keyPath = Paths.get(args[1]);
		Path outputPath = Paths.get(args[2]);
		Path contactsPath = args.length > 3 ? Paths.get(args[3]) : null;
		
		DatabaseDumper dumper = DatabaseDumper.of(cryptPath, keyPath, outputPath);
		dumper.setCreateExtraSqlViews(true);
		
		if(contactsPath != null) {
			dumper.readContacts(contactsPath);
		}
		
		dumper.run();
	}

}
