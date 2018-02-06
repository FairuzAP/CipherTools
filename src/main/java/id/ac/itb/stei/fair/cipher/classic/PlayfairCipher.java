/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.cipher.classic;

import java.util.ArrayList;

/**
 *
 * @author USER
 */
public class PlayfairCipher {
    
    private final char excluded = 'J';
    private final char excludedReplacement = 'I';
    private final char duplicateReplacement = 'Z';
    
    private final ArrayList<ArrayList<Character>> key;

    
    public PlayfairCipher(char[][] newKey) {
	key = new ArrayList<>();
	for(int i=0; i<5; i++) {
	    key.add(new ArrayList());
	    for(int j=0; j<5; j++) {
		key.get(i).add(Character.toUpperCase(newKey[i][j]));
	    }
	}
	System.out.println(key);
    }
    
    public String Encipher(String plaintext) {
	plaintext = PreparePlaintext(plaintext);
	StringBuilder ciphertext = new StringBuilder();
	
	for(int i=0; i<plaintext.length(); i+=2) {
	    ciphertext.append(MapCharPair(plaintext.charAt(i), plaintext.charAt(i+1), true));
	}
	
	return ciphertext.toString();
    }

    public String Decipher(String ciphertext) {
	ciphertext = ciphertext.replaceAll("[^a-zA-Z]", "").toUpperCase();
	StringBuilder plaintext = new StringBuilder();
	
	for(int i=0; i<ciphertext.length(); i+=2) {
	    plaintext.append(MapCharPair(ciphertext.charAt(i), ciphertext.charAt(i+1), false));
	}
	
	return plaintext.toString();
    }
    
    private String PreparePlaintext(String plaintext) {
	StringBuilder res = new StringBuilder();
	boolean isEven = false;
	char[] buf = new char[2];
	
	plaintext = plaintext.replaceAll("[^a-zA-Z]", "").toUpperCase();
	
	for(int i=0; i<plaintext.length(); i++) {
	    char curr = plaintext.charAt(i);
	    
	    if(curr == excluded) {
		curr = excludedReplacement;
	    }
	    
	    if(!isEven) {
		buf[0] = curr;
		isEven = true;
		
	    } else {
		
		if(buf[0]==curr) {
		    buf[1] = duplicateReplacement;
		    res.append(buf);
		    buf[0] = curr;
		    
		} else {
		    buf[1] = curr;
		    res.append(buf);
		    isEven = false;
		}
	    }
	    
	}
	if(isEven) {
	    buf[1] = duplicateReplacement;
	    res.append(buf);
	}
	
	return res.toString();
    }
    
    private boolean IsEqualKey(char c, int i, int j) {
	return key.get(i).get(j) == c;
    }
    
    private char[] MapCharPair(char a, char b, boolean isEncrypt) {
	int ai = 0, aj = 0, bi = 0, bj = 0;
	for(int i=0; i<5; i++) {
	    for(int j=0; j<5; j++) {
		
		if(IsEqualKey(a, i, j)) {
		    ai = i;
		    aj = j;
		}
		if(IsEqualKey(b, i, j)) {
		    bi = i;
		    bj = j;
		}
		
	    }
	}
	
	int step = 1;
	if(!isEncrypt) {
	    step = 4;
	}
	
	if(ai == bi) {
	    aj = (aj+step)%5;
	    bj = (bj+step)%5;
	} else if(aj == bj) {
	    ai = (ai+step)%5;
	    bi = (bi+step)%5;
	} else {
	    int temp = aj;
	    aj = bj;
	    bj = temp;
	}
	
	a = key.get(ai).get(aj);
	b = key.get(bi).get(bj);
	return new char[]{a,b};
    }
    
}
