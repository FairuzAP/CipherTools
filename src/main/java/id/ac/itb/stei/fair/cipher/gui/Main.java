package id.ac.itb.stei.fair.cipher.gui;

import id.ac.itb.stei.fair.stega.bpcs.BpcsStega;
import java.util.Arrays;
import java.util.BitSet;

/**
 *
 * @author USER
 */
public class Main {
    
    public static void main(String [] args) {
        BpcsStega test = new BpcsStega();
        
	byte[] bytes = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes();
	BitSet[] res = BpcsStega.preprocessInput(bytes, 0);
	byte[] postprocessOutput = BpcsStega.postprocessOutput(res);
	System.out.println(new String(postprocessOutput));
    }

}

	