package whatsappbackupreader;

public class WhatsappBackupReaderException extends Exception {
	private static final long serialVersionUID = 1L;

	public WhatsappBackupReaderException(String msg) {
		super(msg);
	}

	public WhatsappBackupReaderException(String message, Throwable cause) {
		super(message, cause);
	}

	public WhatsappBackupReaderException(Throwable cause) {
		super(cause);
	}
}
