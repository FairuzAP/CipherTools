/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package id.ac.itb.stei.fair.stega.bpcs;

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
		int next_in_idx = 1+cm_bp_len+(i*BIT_IN_BP+j);
		double curr_cxty = countComplexity(res[next_in_idx]);
		if(curr_cxty < threshold) {
		    next.set(j);
		    res[next_in_idx].xor(CHESS_BOARD);
		    assert Math.abs((1 - curr_cxty)-countComplexity(res[next_in_idx])) < EPSILON;
		}
		
	    }
	    res[idx++] = next;
	}
	
	// Conjugate the message header
	for(int i=0; i<1+cm_bp_len; i++) {
	    res[i].xor(CHESS_BOARD);
	}
	
	return res;
    }
    
    /**
     * @param in Array of Bitset, generated from a byte array by preprocessInput
     * @return The data contained in the given BitSet array
     */
    public static byte[] postprocessOutput(BitSet[] in) {
	
	// Conjugate the message size header
	in[0].xor(CHESS_BOARD);
	
	int in_byte_len = (int) in[0].toLongArray()[0];
	int in_bp_len = (int)Math.ceil((double)in_byte_len / (double)BYTE_IN_BP);
	assert in_bp_len * BYTE_IN_BP < MAX_INPUT_BYTE;
	int cm_bp_len = (int)Math.ceil((in_bp_len / (double)BIT_IN_BP));
	int bp_len = 1 + in_bp_len + cm_bp_len;
	assert bp_len == in.length;
	
	// Conjugate the ConjugateMapping
	for(int i=1; i<1+cm_bp_len; i++) {
	    in[i].xor(CHESS_BOARD);
	}
	
	byte[] res = new byte[in_byte_len];
	int in_bp_idx = 1 + cm_bp_len;
	int cm_bp_idx = 1;
	int cm_bit_idx = 0;
	
	// For every "input" BitPlane,
	for(int i=0; i<bp_len-(1+cm_bp_len); i++) {
	    
	    // Conjugate the next BitPlane if needed
	    if(in[cm_bp_idx].get(cm_bit_idx)) 
		in[in_bp_idx].xor(CHESS_BOARD);
	    
	    cm_bit_idx++;
	    if(cm_bit_idx==BIT_IN_BP) {
		cm_bp_idx++;
		cm_bit_idx = 0;
	    }
	    
	    // Get the current BitPlane byte array, pad with zero if needed
	    byte[] next = in[in_bp_idx].toByteArray();
	    if(next.length != BYTE_IN_BP) {
		byte[] temp = new byte[BYTE_IN_BP];
		System.arraycopy(next, 0, temp, 0, next.length);
		next = temp;
	    }
	    assert next.length == BYTE_IN_BP;
	    
	    // For every byte in said "input" BitPlane, copy said byte to the output
	    for(int j=0; j<BYTE_IN_BP; j++) {
		res[i*BYTE_IN_BP + j] = next[j];
		
		// Break out of loop if all the byte is processed
		in_byte_len--;
		if(in_byte_len == 0) break;
	    }
	    if(in_byte_len == 0) break;	    
	    in_bp_idx++;
	}
	assert (cm_bp_idx-1)*BIT_IN_BP+cm_bit_idx == bp_len-(1+cm_bp_len);
	assert in_byte_len == 0;
	
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
     * 2D MATRIX INDEXING (The number are printed 2d array)
     *	       0---j---4	0---Y---4
     *	    0 [1 0 0 0 0]    0 [1 0 0 0 0]
     *	    | [0 1 0 0 0]    | [0 1 0 0 0]
     *	    i [0 0 1 0 0]    X [0 0 1 0 0]
     *	    | [0 0 0 1 0]    | [0 0 0 1 0]
     *	    5 [0 0 0 0 1]    5 [0 0 0 0 1]
     * Accessing a 2D array = arr[x][y] / arr[i][j]
     * i ~ X ~ width 
     * j ~ Y ~ height
     */
    private static final int BP_DEPTH = 32;
    private static final int BP_LENGTH = 8;
	
    /**
     * Representation of an ARGB image in a collection of BitPlaneBlock. 
     * If the img size (width or length) is not divisible by eight, only parse
     * the top-left subimage which size (width and length) is divisible by eight
     * 
     * For example, if an image has a size of 9x19, only the top-left 8x16 byte
     * will be converted into BitPlanes. The rest is ignored and won't have it
     * values changed.
     */
    private class imageBitPlanes {
	
	/**
	 * An 8x8x32 bit block represented in an array of 8 BitSet. 
	 * Each BitSet is a representation of an 8x8 BitPlane.
	 * The 32 BitSet layer correspond to every BitPlane in an ARGB image
	 * Layer 0-7 = B, 8-15 = G, 16-23 = R, 24-31 = A
	 */
	private class bitPlaneBlock {
	    private final BitSet[] block;

	    public bitPlaneBlock() {
		this.block = new BitSet[BP_DEPTH];
		for(int i=0; i<block.length; i++) {
		    block[i] = new BitSet(BIT_IN_BP);
		}
	    }

	    public void setColor(int x, int y, int color) {
		assert x<BP_LENGTH && x>=0 && y<BP_LENGTH && y>=0;
		for(int i=0; i<BP_DEPTH; i++) {
		    if((color & 1) == 1) {
			block[i].set(x*BP_LENGTH + y);
		    }
		    color >>>= 1;
		}
		assert color == 0;
	    }
	    public int getColor(int x, int y) {
		int color = 0;
		for(int i=BP_DEPTH-1; i>=0; i--) {
		    color <<= 1;
		    if(block[i].get(x*BP_LENGTH + y)) {
			color |= 1;
		    }
		}
		return color;
	    }

	    public BitSet getBitPlane(int depth) {
		assert depth>=0 && depth<BP_DEPTH;
		return block[depth];
	    }
	}
	
	private final Vector<Vector<bitPlaneBlock>> data;
	private final int xmax, ymax;
	
	public imageBitPlanes(int xmax, int ymax) {
	    data = new Vector<>();
	    assert xmax > BP_LENGTH && ymax > BP_LENGTH;
	    int i, j = 0;
	    for(i=BP_LENGTH; i<xmax; i+=BP_LENGTH) {
		data.add(new Vector<>());
		for(j=BP_LENGTH; j<ymax; j+=BP_LENGTH) {
		    data.lastElement().add(new bitPlaneBlock());
		}
	    }
	    this.xmax = i-BP_LENGTH;
	    this.ymax = j-BP_LENGTH;
	}
	
	public boolean inRange(int x, int y) {
	    return x>=0 && x<xmax && y>=0 && y<ymax;
	}
	
	public int getBlockWidth() {
	    return xmax / (int)BP_LENGTH;
	}
	public int getBlockHeight() {
	    return ymax / (int)BP_LENGTH;
	}
	
	public void setColor(int absX, int absY, int color) {
	    assert absX<xmax && absX>=0 && absY<ymax && absY>=0;
	    int blockX = absX / (int)BP_LENGTH;
	    int blockY = absY / (int)BP_LENGTH;
	    assert blockX<getBlockWidth() && blockY<getBlockHeight();
	    int relX = absX % BP_LENGTH;
	    int relY = absY % BP_LENGTH;
	    data.get(blockX).get(blockY).setColor(relX, relY, color);
	}
        
	public int getColor(int absX, int absY) {
	    assert absX<xmax && absX>=0 && absY<ymax && absY>=0;
	    int blockX = absX / (int)BP_LENGTH;
	    int blockY = absY / (int)BP_LENGTH;
	    assert blockX<getBlockWidth() && blockY<getBlockHeight();
	    int relX = absX % BP_LENGTH;
	    int relY = absY % BP_LENGTH;
	    return data.get(blockX).get(blockY).getColor(relX, relY);
	}
	
	public BitSet getBitPlane(int blockX, int blockY, int depth) {
	    assert blockX>=0 && blockX<getBlockWidth();
	    assert blockY>=0 && blockY<getBlockHeight();
	    assert depth>=0 && depth<BP_DEPTH;
	    return data.get(blockX).get(blockY).getBitPlane(depth);
	}
        
        /**
         * Convert bit encoding in imageBitPlanes into Canonical Gray Code. 
         */
        private void toCGC() {
            boolean prev = false;
            boolean init = true; 
            for (Vector<bitPlaneBlock> vec : data) {
                for (bitPlaneBlock p_block : vec) {
                    for (int i = 0; i < BIT_IN_BP; i++) {
                        for(int j = BP_DEPTH-1; j>=0; j--) {
                            BitSet cur_block = p_block.block[j];
                            // Start of iteration
                            if (init) {
                                prev = cur_block.get(i);
                                init = false;
                            }
                            cur_block.set(i, prev ^ cur_block.get(i));
                            prev = cur_block.get(i);
                        }
                    }
                }
            }
        }
        
        /**
         * Convert bit encoding in imageBitPlanes into Pure Byte Code. 
         */
        private void toPBC() {
            boolean prev = false;
            boolean init = true; 
            for (Vector<bitPlaneBlock> vec : data) {
                for (bitPlaneBlock p_block : vec) {
                    for (int i = 0; i < BIT_IN_BP; i++) {
                        for(int j = BP_DEPTH-1; j>=0; j--) {
                            BitSet cur_block = p_block.block[j];
                            // Start of iteration
                            if (init) {
                                prev = cur_block.get(i);
                                init = false;
                            }
                            cur_block.set(i, cur_block.get(i) ^ prev);
                            prev = cur_block.get(i);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * BitPlane representation of the processed image.
     */
    private imageBitPlanes imgBitPlanes = null;
    
    /**
     * Parse the img BufferedImage into the imgBitPlanes.
     */
    private boolean parseImgToBitPlanes() {
	assert img != null;
	imgBitPlanes = new imageBitPlanes(img.getWidth(), img.getHeight());
	for(int i=0; i<img.getWidth(); i++) {
	    for(int j=0; j<img.getHeight(); j++) {
		if(imgBitPlanes.inRange(i, j))
		    imgBitPlanes.setColor(i, j, img.getRGB(i, j));
	    }
	}
	return true;
    }
    
    /**
     * Parse the imgBitPlanes into the img BufferedImage. 
     * The BufferedImage must not be null and already contain an image,
     * This function will map the changes in the BitPlane to the image.
     */
    private boolean parseBitPlanesToImg() {
	assert imgBitPlanes != null && img != null;
	for(int i=0; i<imgBitPlanes.getBlockWidth()*BP_LENGTH; i++) {
	    for(int j=0; j<imgBitPlanes.getBlockHeight()*BP_LENGTH; j++) {
		img.setRGB(i, j, imgBitPlanes.getColor(i, j));
	    }
	}
	return true;
    }    
    
    /**
     * Empty Constructor for testing.
     */
    public BpcsStega() {
	Path in = Paths.get("D:\\Apocyanletter.png");
	readImage(in);
	parseImgToBitPlanes();	
	parseBitPlanesToImg();
	Path out = Paths.get("D:\\Apocyanletter2.png");
	writeImage(out);
	
	in = Paths.get("D:\\Apocyanletter2.png");
	readImage(in);
	parseImgToBitPlanes();
	parseBitPlanesToImg();
	out = Paths.get("D:\\Apocyanletter3.png");
	writeImage(out);
    }
    
}
