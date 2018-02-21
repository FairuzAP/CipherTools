/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.cipher.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author USER
 */
public class Utils {
    
    public static String ReadTextFile(String fileName)  {
	try {
	    return new String(Files.readAllBytes(Paths.get(fileName)));
	} catch (IOException ex) {
	    Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
	    return "";
	}
    }
    
    public static byte[] ReadBinaryFile(String fileName)  {
	try {
	    return Files.readAllBytes(Paths.get(fileName));
	} catch (IOException ex) {
	    Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
	    return null;
	}
    }
    
    public static boolean SaveTextFile(String fileName, String content)  {
	try {
	    Files.write(Paths.get(fileName), content.getBytes());
	    return true;
	} catch (IOException ex) {
	    Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
	    return false;
	}
    }
    
    public static boolean SaveBinaryFile(String fileName, byte[] content)  {
	try {
	    Files.write(Paths.get(fileName), content);
	    return true;
	} catch (IOException ex) {
	    Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
	    return false;
	}
    }
    
    public static String FormatCopy(String text, String format) {
	StringBuilder res = new StringBuilder();
	int j=0;
	
	OUTER:
	for (int i = 0; i<format.length(); i++) {
	    switch (format.charAt(i)) {
	    	case ' ':
		    res.append(' ');
		    break;
	    	case '\n':
		    res.append('\n');
		    break;
	    	default:
		    if (j >= text.length()) {
			break OUTER;
		    }
		    res.append(text.charAt(j));
		    j++;
		    break;
	    }
	}
	
	return res.toString();
    }
    
    public static String FormatSplitBy(String text, int split) {
	StringBuilder res = new StringBuilder();
	
	for(int i=0; i<text.length();) {
	    res.append(text.charAt(i));
	    i++;
	    if(i%split == 0) {
		res.append(' ');
	    }
	}
	
	return res.toString();
    }
    
}
