/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.cipher.classic;

/**
 * Vigenere cipher helper for [A-Z] character encoded in ASCII.
 * Both the key, plaintext, and ciphertext will be in [A-Z]
 * @author USER
 */
public class VigenereStandard extends VigenereCipher {

    /**
     * Create the Vigenere Cipher using the supplied key
     * @param key Symmetric encryption/decryption key, encoded in ASCII. [A-Z]
     */
    public VigenereStandard(String key) {
	super();
        start = 65;
	delta = 26;
	CreateSquare();
	SetKey(key.replaceAll("[^a-zA-Z]", "").toUpperCase());
    }

    @Override
    public String Encipher(String plaintext) {
	return super.Encipher(plaintext.replaceAll("[^a-zA-Z]", "").toUpperCase());
    }
    
    @Override
    public String Decipher(String ciphertext) {
	return super.Decipher(ciphertext.replaceAll("[^a-zA-Z]", "").toUpperCase());
    }
    
}
