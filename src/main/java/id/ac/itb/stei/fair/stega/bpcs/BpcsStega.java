/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.stega.bpcs;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Vector;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 *
 * @author USER
 */
public class BpcsStega {
    
    private static final double EPSILON = 0.001;
    
    private static final int MAX_INPUT_BYTE = Integer.MAX_VALUE; 
    private static final int BIT_IN_BYTE = 8;
    private static final int BYTE_IN_BP = 8;
    private static final int BIT_IN_BP = BIT_IN_BYTE * BYTE_IN_BP;
    private static final int BP_BIT_LEN = 8;
    
    private static final BitSet CHESS_BOARD;  
    private static final double MAX_CXTY = 112;
    static {
        CHESS_BOARD = new BitSet(BIT_IN_BP);
	for(int i=1, n=0; i<BIT_IN_BP; i+=2) {
	    CHESS_BOARD.set(i);
	    if(++n%4==0) if(n%8==0) i++; else i--;
	}
    }
    
    private static double countComplexity(BitSet in) {
	int res = 0;
	assert in.size() == BIT_IN_BP;
	boolean curr, right, down;
	
	for(int i=0; i<BP_BIT_LEN; i++) {
	    for(int j=0; j<BP_BIT_LEN; j++) {
		curr = in.get((i*8)+j);
		right = (j+1)<BP_BIT_LEN ? in.get((i*BP_BIT_LEN)+(j+1)) : curr;
		down = (i+1)<BP_BIT_LEN ? in.get(((i+1)*BP_BIT_LEN)+j) : curr;
		if(curr != right) res++;
		if(curr != down) res++;
	    }
	}
	
	return (double)res / MAX_CXTY;
    }
    
    /**
     * @param in The data to be inserted to the vessel 
     * @param threshold 
     * @return Array of Bitset, each represent an 8x8 bitplane.
     * Each bitset contains 1 long / 8 byte / 64 bit of data
     * First bitset contains the message byte number in long
     * The next 'cm_bp_len' bitset contains conjugation mapping in bit array
     * The last 'in_bp_len' bitset contains the message in byte array
     */
    public static BitSet[] preprocessInput(byte[] in, double threshold) {
	int in_bp_len = (int)Math.ceil((double)in.length / (double)BYTE_IN_BP);
	assert in_bp_len * BYTE_IN_BP < MAX_INPUT_BYTE;
	int cm_bp_len = (int)Math.ceil((in_bp_len / (double)BIT_IN_BP));
	int bp_len = 1 + in_bp_len + cm_bp_len;
	
	BitSet res[] = new BitSet[bp_len];
	int idx = 0;
	res[idx++] = BitSet.valueOf(new long[]{in.length});
	
	// Skip conjugation mapping bitplane, process the input bitplane first
	idx += cm_bp_len;
	for(int i=0; i<in_bp_len; i++) {
	    // Each iteration map the next 8 byte into the next BitSet
	    byte next[] = new byte[BYTE_IN_BP];
	    int id = i*BYTE_IN_BP;
	    
	    for(int j=0; j<BYTE_IN_BP; j++) {
		next[j] = (id+j)<in.length ? in[id+j] : 0;
	    }
	    res[idx++] = BitSet.valueOf(next);
	    assert res[idx-1].size() == BIT_IN_BP;
	}
	
	// Processing the conjugation mapping bitplane
	idx = 1;
	for(int i=0; i<cm_bp_len; i++) {
	    
	    // Each iteration map the next 64 message BitSet into the next conjugate mapping bitset
	    BitSet next = new BitSet(BIT_IN_BP);
	    for(int j=0; j<BIT_IN_BP; j++) {		
		if(j>=in_bp_len) break;
		
		// If a message BitSet need to be conjugated, set the respective conjugate map bit,
		// Also conjugate the message BitSet itself
		double curr_cxty = countComplexity(res[1+cm_bp_len+i]);
		if(curr_cxty < threshold) {
		    next.set(j);
		    res[1+cm_bp_len+i].xor(CHESS_BOARD);
		    assert Math.abs((1 - curr_cxty)-countComplexity(res[1+cm_bp_len+i])) < EPSILON;
		}
		
	    }
	    res[idx++] = next;
	}
	
	return res;
    }
    
    
    private BufferedImage img = null;
    private ImageWriter writer = null;
    
    private boolean readImage(Path fileIn) {
	try (ImageInputStream input = ImageIO.createImageInputStream(fileIn.toFile())){
	    ImageReader reader = ImageIO.getImageReaders(input).next();
	    reader.setInput(input);
	    
	    assert "png".equalsIgnoreCase(reader.getFormatName()) || 
		    "bmp".equalsIgnoreCase(reader.getFormatName());
	    
	    // Check that image is encoded and loaded in RGB/RGBA format
	    ImageTypeSpecifier rgbType = null;
	    Iterator<ImageTypeSpecifier> imageTypes = reader.getImageTypes(0);
	    while(imageTypes.hasNext()) {
		ImageTypeSpecifier next = imageTypes.next();
		if (next.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_RGB) {
		    rgbType = next;
		    break;
		}
	    }
	    
	    assert rgbType != null;
	    ImageReadParam readParam = reader.getDefaultReadParam();
	    readParam.setDestinationType(rgbType);
	    img = reader.read(0, readParam);
	    
	    writer = ImageIO.getImageWriter(reader);
	    reader.dispose();
	    return true;
	    
	} catch (IOException x) {
	    System.err.format("IOException: %s%n", x);
	    return false;
	    
	}
    }
    
    private boolean writeImage(Path fileOut) {
	assert img != null && writer != null;
	try (ImageOutputStream output = ImageIO.createImageOutputStream(fileOut.toFile())) {
	    writer.setOutput(output);
            writer.write(img);
	    return true;
	    
	} catch (IOException x) {
	    System.err.format("IOException: %s%n", x);
	    return false;
	
	} finally {
	    writer.dispose();
	    img = null; writer = null;
	}
    }
    
    /**
     * An 8x8x8 bit block represented in an array of 8 BitSet. 
     * Each BitSet is a representation of an 8x8 BitPlane. 
     */
    private class bitPlaneBlock {
	public BitSet[] block;
	
	public bitPlaneBlock() {
	    this.block = new BitSet[BIT_IN_BYTE];
	    for(int i=0; i<block.length; i++) {
		block[i] = new BitSet(BIT_IN_BP);
	    }
	}
    }
    
    /**
     * A two dimensional matrix of bitPlaneBlock. 
     * Representing an image channel entire BitPlane.
     */
    private class channelBitPlane {
	public Vector<Vector<bitPlaneBlock>> data;
	
	public channelBitPlane(int xmax, int ymax) {
	    // TODO: Implement helper struct constructor
	}
    }
    
    /**
     * An array of channelBitPlane. 
     * Representing an image with multiple color channel (RGBA)
     * 0 = R, 1 = G, 2 = B, 3 = A
     */
    private channelBitPlane[] imgBitPlanes = new channelBitPlane[4];
    
    /**
     * Parse the img BufferedImage into the imgBitPlanes.
     * If the img size (width or length) is not divisible by eight, only parse
     * the top-left subimage which size (width and length) is divisible by eight
     * 
     * For example, if an image has a size of 9x19, only the top-left 8x16 byte
     * will be converted into BitPlanes. The rest is ignored and won't have it
     * values changed.
     */
    private boolean parseImgToBitPlanes() {
	assert img != null;
	Color c = new Color(img.getRGB(0, 0), true);
	c.getRed();
	
	// TODO: Tuturu~
	
	return true;
    }
    
    /**
     * Empty Constructor for testing.
     */
    public BpcsStega() {
	Path in = Paths.get("D:\\Apocyanletter.png");
	readImage(in);
	Path out = Paths.get("D:\\Apocyanletter2.png");
	writeImage(out);
	in = Paths.get("D:\\Apocyanletter2.png");
	readImage(in);
	out = Paths.get("D:\\Apocyanletter3.png");
	writeImage(out);
    }
    
}
