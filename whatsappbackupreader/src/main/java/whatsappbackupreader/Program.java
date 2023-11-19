package whatsappbackupreader;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Program {

	public static void main(String[] args) throws WhatsappBackupReaderException {
		// TODO Auto-generated method stub
		System.out.println(args[1]);
		
		Path keyPath = Paths.get(args[0]);
		Path cryptPath = Paths.get(args[1]);
		
		WhatsappBackupReader wbr = new WhatsappBackupReader(keyPath, cryptPath);
		wbr.decrypt();
	}

}
