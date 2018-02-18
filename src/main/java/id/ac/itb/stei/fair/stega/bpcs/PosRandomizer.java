/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.stega.bpcs;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Random;
import java.util.stream.Stream;

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
    
    /** ArrayList for x, y and depth */
    private final ArrayList<Integer> xList;
    private final ArrayList<Integer> yList;
    private final ArrayList<Integer> depthList;
    
    /** The next random depth */
    public int nextDepth = 0;

    /** The next random X */
    public int nextX = 0;

    /** The next random Y */
    public int nextY = 0;
    
    /**
     * Generate a new random position generator with the given parameter
     * @param seed The pseudorandom function seed 
     */
    public PosRandomizer(long seed) {
	rng = new SecureRandom();
	rng.setSeed(seed);
	
	xList = new ArrayList<>();
        yList = new ArrayList<>();
        depthList = new ArrayList<>();
    }
    
    /**
     * Generate the next unique (nextDepth, nextX, nextY)
     */
    public void next() {
        assert Stream.of(xList.size(), yList.size(), depthList.size()).distinct().count() == 1;
        int nextBP = rng.nextInt(xList.size());
        
        nextX = xList.get(nextBP);
        nextY = yList.get(nextBP);
        nextDepth = depthList.get(nextBP);
        
        xList.remove(nextBP);
        yList.remove(nextBP);
        depthList.remove(nextBP);
    }
    
    /**
     * Generate noisy bitplane list
     * @param x
     * @param y
     * @param depth
     */
    public void addNoisy (int x, int y, int depth) {
        xList.add(x);
        yList.add(y);
        depthList.add(depth);
        assert Stream.of(xList.size(), yList.size(), depthList.size()).distinct().count() == 1;
    }
    
    public int getSize () {
        assert Stream.of(xList.size(), yList.size(), depthList.size()).distinct().count() == 1;
        return xList.size();
    }
}
