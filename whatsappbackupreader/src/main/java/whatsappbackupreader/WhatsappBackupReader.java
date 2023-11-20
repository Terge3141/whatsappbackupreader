package whatsappbackupreader;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import whatsappbackupreader.protos.BackupPrefixOuterClass.BackupPrefix;

public class WhatsappBackupReader {
	private Path keyPath;
	private byte[] encrypted;
	
	private byte[] iv;
	
	private final int LENGTH_CHECKSUM = 16;
	private final int LENGTH_AUTHENTICATION_TAG = 16;
	
	public WhatsappBackupReader(Path keyPath, Path cryptPath) throws WhatsappBackupReaderException {
		this.keyPath = keyPath;
		try {
			this.encrypted = Files.readAllBytes(cryptPath);
		} catch (IOException e) {
			throw new WhatsappBackupReaderException("Cannot read encrypted file", e);
		}
		
		byte buf = 0;
		int pos = 0;
		
		buf = encrypted[pos]; pos++;
		
		/*System.out.println(byteArrayAsHex(file_hash.digest()));
		if(true) {
			return;
		}*/
		
		int protobufSize = Byte.toUnsignedInt(buf);
		System.out.println(protobufSize);
		
		// TODO rename
		// file_hash
		// msgstore_features_flag
		
		
		// A 0x01 as a second byte indicates the presence of the feature table in the protobuf.
		// It is optional and present only in msgstore database, although
        // Some old msgstore backups exist without it, so it is optional.
		buf = encrypted[pos]; pos++;
		int msgstore_features_flag = Byte.toUnsignedInt(buf);
        if(msgstore_features_flag != 1) {  
            msgstore_features_flag = 0;
        }
        
        if(msgstore_features_flag == 0) {   
        	System.out.println("No feature table found (not a msgstore DB or very old)");
        }

        byte[] protobuf_raw = Arrays.copyOfRange(encrypted, pos, pos + protobufSize); pos += protobufSize;
        
        BackupPrefix header;
        try {
			header = BackupPrefix.parseFrom(protobuf_raw);
		} catch (InvalidProtocolBufferException e) {
			throw new WhatsappBackupReaderException("Could not backup prefix protobuf", e);
		}
        
        System.out.println("Whatsapp version: " + header.getInfo().getAppVersion());
        
        if(header.hasC15Iv()) {
        	int size = header.getC15Iv().getIV().size();
        	if(size != 16) {
        		throw new WhatsappBackupReaderException(
        				String.format("IV is not 16 bytes long but is %d bytes long", size)
        		);
        	}
        	
        	this.iv = header.getC15Iv().getIV().toByteArray();
        	System.out.println("iv: " + byteArrayAsHex(this.iv));
        	
        } else if(header.hasC14Cipher()) {
        	throw new WhatsappBackupReaderException("C14 not implemented");
        } else {
        	throw new WhatsappBackupReaderException("Unknown encryption");
        }
	}
	
	public void decrypt() throws WhatsappBackupReaderException {
		byte[] key = null;
		try {
			key = Files.readAllBytes(keyPath);
		} catch (IOException e) {
			throw new WhatsappBackupReaderException("Cannot read key file", e);
		}
		
		String keystr = new String(key, StandardCharsets.UTF_8);
		
		System.out.println(keystr);
		byte[] key2 = hexStringToByteArray(keystr);
		System.out.println(byteArrayAsHex(key2));
		
		int checkSumStart = encrypted.length - LENGTH_CHECKSUM;
		byte[] checksumExpected = Arrays.copyOfRange(encrypted, checkSumStart, encrypted.length);
		
		int authenticationTagStart = encrypted.length - LENGTH_CHECKSUM - LENGTH_AUTHENTICATION_TAG;
		byte[] authenticationTag = Arrays.copyOfRange(encrypted, authenticationTagStart, checkSumStart);
		
		byte[] encryptedData = Arrays.copyOfRange(encrypted, 0, authenticationTagStart);
		
		// if check md5-checksum is correct
		System.out.println("Checksum expected: " + byteArrayAsHex(checksumExpected));
		
		/*try {
			file_hash = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new WhatsappBackupReaderException("Cannot initiate md5 sum generator", e);
		}*/
		
		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new WhatsappBackupReaderException("Cannot initiate md5 sum generator", e);
		} 
		md5.update(encryptedData);
		md5.update(authenticationTag);
		
		byte[] checksumActual = md5.digest();
		System.out.println("Checksum actual: " + byteArrayAsHex(checksumActual));
		
		if(!Arrays.equals(checksumExpected, checksumActual)) {
			throw new WhatsappBackupReaderException("Checksums not equal");
		}
	}
	
	private byte hexCharToByte(char c) throws WhatsappBackupReaderException {
		c = Character.toUpperCase(c);
		switch(c) {
		case '0': return 0;
		case '1': return 1;
		case '2': return 2;
		case '3': return 3;
		case '4': return 4;
		case '5': return 5;
		case '6': return 6;
		case '7': return 7;
		case '8': return 8;
		case '9': return 9;
		case 'A': return 10;
		case 'B': return 11;
		case 'C': return 12;
		case 'D': return 13;
		case 'E': return 14;
		case 'F': return 15;
 		}
		
		throw new WhatsappBackupReaderException("Bad hex character");
	}
	
	private byte[] hexStringToByteArray(String str) throws WhatsappBackupReaderException {
		// TODO proper trimming, see trimPassphrase in SignalBackupReader
		str = str.trim();
		
		if(str.length() % 2 != 0) {
			throw new WhatsappBackupReaderException("Key string length must be divisble by two");
		}
		
		byte buf[] = new byte[str.length() / 2];
		
		for(int i=0; i<buf.length; i++) {
			char c1 = str.charAt(2*i);
			char c2 = str.charAt(2*i+1);
			
			buf[i] = (byte) (hexCharToByte(c1)*16 + hexCharToByte(c2));
		}
		
		return buf;
	}
	
	private String byteArrayAsHex(byte[] arr) {
		String str = "";
		
		for(int i=0; i<arr.length; i++) {
			str = str + String.format("%02x", arr[i]);
		}
		
		return str;
	}
	
	/*private byte[] getBytes(Path path) throws IOException {
		FileInputStream fip = new FileInputStream(path.toFile());
		byte[] data = fip.readAllBytes();
		fip.close();
		
		return data;
	}*/
}
