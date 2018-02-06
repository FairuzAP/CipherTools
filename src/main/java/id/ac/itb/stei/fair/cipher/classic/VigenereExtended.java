/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.cipher.classic;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Vigenere cipher helper for all 256 character encoded in ASCII.
 * @author USER
 */
public class VigenereExtended extends VigenereCipher{

    /**
     * Create the Vigenere Cipher using the supplied key
     * @param key Symmetric encryption/decryption key.
     */
    public VigenereExtended(String key) {
	super();
        start = 0;
	delta = 256;
	CreateSquare();
	SetKey(key);
    }
    
    public boolean EnchiperFile(Path fileIn, Path fileOut) {
	try (	
	    InputStream in= Files.newInputStream(fileIn);
	    OutputStream out = Files.newOutputStream(fileOut);
	) {
	    int next, i = 0;
	    byte enc[] = new byte[1];
	    while ((next = in.read()) != -1) {
		int idx = i % encryptKey.length;
		enc[0] = (byte) MapVigenereSquare(next, encryptKey[idx]);
		out.write(enc);
		i++;
	    }
	    return true;
	
	} catch (IOException x) {
	    System.err.format("IOException: %s%n", x);
	    return false;
	}
    }

    public boolean DecipherFile(Path fileIn, Path fileOut) {
	try (	
	    InputStream in= Files.newInputStream(fileIn);
	    OutputStream out = Files.newOutputStream(fileOut);
	) {
	    
	    int next, i = 0;
	    byte dec[] = new byte[1];
	    while ((next = in.read()) != -1) {
		int idx = i % decryptKey.length;
		dec[0] = (byte) MapVigenereSquare(next, decryptKey[idx]);
		out.write(dec);
		i++;
	    }
	    return true;
	
	} catch (IOException x) {
	    System.err.format("IOException: %s%n", x);
	    return false;
	}
    }
    
}
