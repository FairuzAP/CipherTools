/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.cipher.classic;

import java.util.Map;
import java.util.TreeMap;

/**
 *
 * @author USER
 */
public abstract class VigenereCipher {
    protected int start;
    protected int delta;
    
    protected Map<Integer,Map<Integer,Integer>> vigenereSquare;
    
    /** The Vigenere Cypher symmetric encryption key */
    protected int[] encryptKey;
    
    /** The reverse of the Vigenere key, used during decryption */
    protected int[] decryptKey;
    
    
    /**
     * Set the numeric encryption key, also calculate the reverse of said key 
     * that will be used for decryption
     * @param key The symmetric encryption key
     */
    protected final void SetKey(String key) {
	encryptKey = new int[key.length()];
	decryptKey = new int[key.length()];
	for(int i=0; i<key.length(); i++) {
	    encryptKey[i] = (int)key.charAt(i);
	    decryptKey[i] = (start + ((delta - encryptKey[i] + start) % delta));
	}
    }
    
    /**
     * Initialize the numeric Vigenere Square according to the start and delta
     */
    protected final void CreateSquare() {
	vigenereSquare = new TreeMap<>();
	for(int i=0; i<delta; i++) {
	    vigenereSquare.put(start+i, new TreeMap<>());
	    
	    for(int j=0; j<delta; j++) {
		int idx = (i+j) % delta;
		vigenereSquare.get(start+i).put(start+j,start+idx);
	    }
	}
    }
    
    /**
     * Encrypt the given plaintext into the ciphertext
     * @param plaintext The text to be encrypted, encoded in ASCII. [A-Z]
     * @return The ciphertext, encoded in ASCII. [A-Z]
     */
    public String Encipher(String plaintext) {
	char[] cipherChars = new char[plaintext.length()];
	for(int i=0; i<plaintext.length(); i++) {
	    int idx = i % encryptKey.length;
	    if(plaintext.charAt(i) > 255 || plaintext.charAt(i) <0) {
		cipherChars[i] = ' ';
	    } else {
		int temp = MapVigenereSquare(plaintext.charAt(i), encryptKey[idx]);
	        cipherChars[i] = (char) temp;
	    }
	}
	return new String(cipherChars);
    }
    
    /**
     * Decrypt the given ciphertext into the plaintext
     * @param ciphertext The text to be decrypted, encoded in ASCII. [A-Z]
     * @return The plaintext, encoded in ASCII. [A-Z]
     */
    public String Decipher(String ciphertext) {
	char[] plainChars = new char[ciphertext.length()];
	for(int i=0; i<ciphertext.length(); i++) {
	    int idx = i % decryptKey.length;
	    int temp = MapVigenereSquare(ciphertext.charAt(i), decryptKey[idx]);
	    plainChars[i] = (char) temp;
	}
	return new String(plainChars);
    }
    
    protected int MapVigenereSquare(int plain, int key) {
	return vigenereSquare.get(plain).get(key);
    }
    
}
