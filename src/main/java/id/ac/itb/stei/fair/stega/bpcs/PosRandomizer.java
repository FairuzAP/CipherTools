/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.stega.bpcs;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

/**
 * Random unique (x,y,depth) generator. 
 * WARNING: May crash if 'most' triplet value already used.
 * 
 * USAGE:
 *  PosRandomizer rng = new PosRandomizer(seed, xmax, ymax, maxDepth);
 *  rng.next();
 *  int X = rng.nextX;
 *  int Y = rng.nextY;
 *  int depth = rng.nextDepth;
 */
public class PosRandomizer {
    
    private final Random rng;
    private final int maxDepth;
    private final int xmax;
    private final int ymax;
    
    /** Mapping x and y to a list of depth already generated */
    private final HashMap<Integer,HashMap<Integer,HashSet<Integer>>> history;
    
    /** The next random depth */
    public int nextDepth = 0;

    /** The next random X */
    public int nextX = 0;

    /** The next random Y */
    public int nextY = 0;
    
    /**
     * Generate a new random position generator with the given parameter
     * @param seed The pseudorandom function seed
     * @param xmax X coord max value 
     * @param ymax Y coord max value 
     * @param maxDepth Depth max value 
     */
    public PosRandomizer(long seed, int xmax, int ymax, int maxDepth) {
	rng= new SecureRandom();
	rng.setSeed(seed);
	
	this.maxDepth = maxDepth;
	this.xmax = xmax; 
	this.ymax = ymax; 
	
	history = new HashMap<>();
    }
    
    private boolean checkDupl(int x, int y, int depth) {
	boolean unique = true;
	if(!history.containsKey(x)) {
	    history.put(x, new HashMap<>());
	}
	if(!history.get(x).containsKey(y)) {
	    history.get(x).put(y, new HashSet<>());
	}
	if(!history.get(x).get(y).contains(depth)) {
	    history.get(x).get(y).add(depth);
	} else {
	    unique = false;
	}
	return unique;
    }
    
    /**
     * Generate the next unique (nextDepth, nextX, nextY)
     */
    public void next() {
	nextX = rng.nextInt(xmax);
	nextY = rng.nextInt(ymax);
	nextDepth = rng.nextInt(maxDepth);
	int iter = 1;
	while(!checkDupl(nextX, nextY, nextDepth)) {
	    nextX = rng.nextInt(xmax);
	    nextY = rng.nextInt(ymax);
	    nextDepth = rng.nextInt(maxDepth);
	    iter++;
	    assert iter <= nextX * nextY * nextDepth * 2;
	}
    }
    
}
