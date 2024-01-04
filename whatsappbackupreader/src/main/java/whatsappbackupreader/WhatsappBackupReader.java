package whatsappbackupreader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.protobuf.InvalidProtocolBufferException;

import whatsappbackupreader.protos.BackupPrefixOuterClass.BackupPrefix;

public class WhatsappBackupReader {
	
	private static Logger logger = LogManager.getLogger(WhatsappBackupReader.class);
	
	private Path outputPath;
	
	private byte[] keyFileData;
	
	private byte[] cryptFileData;
	int pos = 0;
	
	private byte[] iv;
	
	private final int LENGTH_CHECKSUM = 16;
	private final int LENGTH_AUTHENTICATION_TAG = 16;
	private final String ALGORITHM = "HmacSHA256";
	private final String MESSAGE_STRING = "backup encryption";
	private final String HEX_VALUES = "0123456789ABCDEF";
	
	
	public WhatsappBackupReader(Path cryptPath, Path keyPath, Path outputPath) throws WhatsappBackupReaderException {
		try {
			setup(cryptPath, Files.readAllBytes(keyPath), outputPath);
		} catch (IOException e) {
			throw new WhatsappBackupReaderException("Cannot read key or encrypted file", e);
		}
	}
	
	public WhatsappBackupReader(Path cryptPath, byte[] keyFileData, Path outputPath) throws WhatsappBackupReaderException {
		setup(cryptPath, keyFileData, outputPath);
	}
	
	private void setup(Path cryptPath, byte[] keyFileData, Path outputPath) throws WhatsappBackupReaderException {
		this.outputPath = outputPath;
		this.keyFileData = keyFileData;
		
		try {
			this.cryptFileData = Files.readAllBytes(cryptPath);
		} catch (IOException e) {
			throw new WhatsappBackupReaderException("Cannot read key or encrypted file", e);
		}
		
		pos = 0;
	}

	/**
	 * Parse proto files from header. Runs silently unless an error occurs, e.g. incorrect format or wrong version 
	 * @throws WhatsappBackupReaderException
	 */
	private void parseHeader() throws WhatsappBackupReaderException {
		byte buf = 0;
		
		buf = cryptFileData[pos]; pos++;
		
		int protobufSize = Byte.toUnsignedInt(buf);
		
		// A 0x01 as a second byte indicates the presence of the feature table in the protobuf.
		// It is optional and present only in msgstore database, although
        // Some old msgstore backups exist without it, so it is optional.
		buf = cryptFileData[pos]; pos++;
		int msgstoreFeaturesFlag = Byte.toUnsignedInt(buf);
        if(msgstoreFeaturesFlag != 1) {  
            msgstoreFeaturesFlag = 0;
        }
        
        if(msgstoreFeaturesFlag == 0) {   
        	System.out.println("No feature table found (not a msgstore DB or very old)");
        }

        byte[] protobufRaw = Arrays.copyOfRange(cryptFileData, pos, pos + protobufSize); pos += protobufSize;
        
        BackupPrefix header;
        try {
			header = BackupPrefix.parseFrom(protobufRaw);
		} catch (InvalidProtocolBufferException e) {
			throw new WhatsappBackupReaderException("Could not backup prefix protobuf", e);
		}
        
        logger.info("Whatsapp version: " + header.getInfo().getAppVersion());
        
        if(header.hasC15Iv()) {
        	int size = header.getC15Iv().getIV().size();
        	if(size != 16) {
        		throw new WhatsappBackupReaderException(
        				String.format("IV is not 16 bytes long but is %d bytes long", size)
        		);
        	}
        	
        	this.iv = header.getC15Iv().getIV().toByteArray();
        	
        } else if(header.hasC14Cipher()) {
        	throw new WhatsappBackupReaderException("C14 not implemented");
        } else {
        	throw new WhatsappBackupReaderException("Unknown encryption");
        }
	}
	
	// see https://raw.githubusercontent.com/ElDavoo/wa-crypt-tools/main/src/wa_crypt_tools/lib/key/key15.py for more information
	private byte[] calculateKey(byte[] key) throws NoSuchAlgorithmException, InvalidKeyException {
		byte[] privateseed = new byte[32];
		
		byte[] message = MESSAGE_STRING.getBytes();
		
		SecretKeySpec secretKeySpec = new SecretKeySpec(privateseed, ALGORITHM);
		Mac mac = Mac.getInstance(ALGORITHM);
		mac.init(secretKeySpec);
		mac.update(key);
		
		byte[] privatekey = mac.doFinal();
		
		SecretKeySpec hasherSpec = new SecretKeySpec(privatekey, ALGORITHM);
		Mac hasher = Mac.getInstance(ALGORITHM);
		hasher.init(hasherSpec);
		hasher.update(message);
		
		byte b = (byte)1;
		hasher.update(b);
		byte[] buf = hasher.doFinal();
		
		return buf;
	}
	
	public void decrypt() throws WhatsappBackupReaderException {
		parseHeader();
		
		// calculate the key
		String keyFileStr = new String(keyFileData);
		byte[] keyFileArr = hexStringToByteArray(keyFileStr);
		
		byte[] key;
		try {
			key = calculateKey(keyFileArr);
		} catch (InvalidKeyException | NoSuchAlgorithmException e) {
			throw new WhatsappBackupReaderException("Cannot initialize keys", e);
		}
		
		int checkSumStart = cryptFileData.length - LENGTH_CHECKSUM;
		byte[] checksumExpected = Arrays.copyOfRange(cryptFileData, checkSumStart, cryptFileData.length);
		
		// if check md5-checksum is correct
		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new WhatsappBackupReaderException("Cannot initiate md5 sum generator", e);
		}
		
		md5.update(cryptFileData, 0, checkSumStart);
		byte[] checksumActual = md5.digest();
		
		if(!Arrays.equals(checksumExpected, checksumActual)) {
			throw new WhatsappBackupReaderException("Checksums not equal");
		}
		
		// decrypt
		GCMParameterSpec parameterSpec = new GCMParameterSpec(LENGTH_AUTHENTICATION_TAG*8, iv);
		SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
		
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, parameterSpec);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException e) {
			throw new WhatsappBackupReaderException("Could not initialize cipher", e);
		}
		
		byte[] decrypted;
		try {
			decrypted = cipher.doFinal(cryptFileData, pos, checkSumStart - pos);
		} catch (IllegalBlockSizeException | BadPaddingException e) {
			throw new WhatsappBackupReaderException("Could not decrypt", e);
		}
		
		// unzip
		Inflater zlib = new Inflater(false);
		logger.info("Writing to: " + outputPath);
		try(FileOutputStream s = new FileOutputStream(outputPath.toFile())) {
			zlib.setInput(decrypted, 0, decrypted.length);
            byte[] buf = new byte[1024];
            while(!zlib.needsInput()) {
                int l = zlib.inflate(buf, 0, buf.length);
                if(l > 0) s.write(buf, 0, l);
            }
        } catch (IOException | DataFormatException e) {
        	throw new WhatsappBackupReaderException("Could not decompress", e);
		}
	}
	
	private byte hexCharToByte(char c) throws WhatsappBackupReaderException {
		c = Character.toUpperCase(c);
		int i = HEX_VALUES.indexOf(c);
		if(i==-1) {
			throw new WhatsappBackupReaderException("Bad hex character");
		}
		
		return (byte)i;
	}
	
	private byte[] hexStringToByteArray(String str) throws WhatsappBackupReaderException {
		str = str.replace(" ", "").replace("\n", "");
		
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
		return  byteArrayAsHex(arr, arr.length);
	}
	
	private String byteArrayAsHex(byte[] arr, int len) {
		String str = "";
		
		for(int i=0; i<len; i++) {
			str = str + String.format("%02x", arr[i]);
		}
		
		return str;
	}	
}
