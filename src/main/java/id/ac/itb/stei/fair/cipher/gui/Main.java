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
	BitSet[] res = BpcsStega.preprocessInput("ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes(), 1);
	System.out.println(Arrays.toString(res));
	BpcsStega test = new BpcsStega();
    }

}

	