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
import java.util.logging.Level;
import java.util.logging.Logger;
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

        for(BitSet bp : res) {
            assert countComplexity(bp) >= threshold;
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
        in[0].xor(CHESS_BOARD);

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

            // Restore the conjugated BitPlane if needed
            if(in[cm_bp_idx].get(cm_bit_idx))
                in[in_bp_idx].xor(CHESS_BOARD);

            // Increment BP index
            cm_bit_idx++;
            if(cm_bit_idx==BIT_IN_BP) {
                cm_bp_idx++;
                cm_bit_idx = 0;
            }

            if(in_byte_len == 0) break;
            in_bp_idx++;
        }
        assert (cm_bp_idx-1)*BIT_IN_BP+cm_bit_idx == bp_len-(1+cm_bp_len);
        assert in_byte_len == 0;

        // Conjugate the ConjugateMapping
        for(int i=1; i<1+cm_bp_len; i++) {
            in[i].xor(CHESS_BOARD);
        }

        return res;
    }

    
    private void embedMessage(BitSet[] in, double threshold) {
        assert imgBitPlanes != null;
        //imgBitPlanes.toCGC();

        int indexBitSet = 0;

        for (int i=0; i<imgBitPlanes.getBlockWidth() && indexBitSet < in.length; i++) {
            for (int j=0; j<imgBitPlanes.getBlockHeight() && indexBitSet < in.length; j++) {
                for (int k=0; k<BP_DEPTH && indexBitSet < in.length; k++) {

                    BitSet bp = imgBitPlanes.getBitPlane(i, j, k);

                    double cs = countComplexity(bp);
                    if (cs >= threshold) {
                        imgBitPlanes.setBitPlane(in[indexBitSet], i, j, k);
                        indexBitSet++;
                    }

                }
            }
        }

        assert indexBitSet == in.length : "image is too small for message. If it is intended, delete this assertion";
        
        //imgBitPlanes.toPBC();
    }

    private long generateSeed(String key) {
        long seed = 0;
        
        key = key.toUpperCase();
        for (int i=0; i<key.length(); i++) {
            int intChar = (int) key.charAt(i) - (int) 'A';
            assert intChar >= 0 && intChar < 26;
            seed += intChar;
        }
        
        return seed;
    }
    
    private void embedMessage(BitSet[] in, double threshold, long seed) {
        assert imgBitPlanes != null;
               
        PosRandomizer rng = new PosRandomizer(seed, imgBitPlanes.getBlockWidth(), 
                                                    imgBitPlanes.getBlockHeight(), 
                                                    BP_DEPTH);
        
        int indexBitSet = 0;
        
        int i=0;
        while (indexBitSet < in.length) {
            rng.next();
            int currentX = rng.nextX;
            int currentY = rng.nextY;
            int currentDepth = rng.nextDepth;
            
            BitSet bp = imgBitPlanes.getBitPlane(currentX, currentY, currentDepth);

            double cs = countComplexity(bp);
            if (cs >= threshold) {
                imgBitPlanes.setBitPlane(in[indexBitSet], currentX, currentY, currentDepth);
                indexBitSet++;
            }    
            i++;
            assert i < imgBitPlanes.getBlockHeight() * imgBitPlanes.getBlockWidth() * BP_DEPTH * 2 :
                    "Image is too small for message";          
        }
 
    }
    
    private BitSet[] extractMessage(double threshold) {
        assert imgBitPlanes != null;
        //imgBitPlanes.toCGC();

        BitSet byte_len = null;

        int first_i=0, first_j=0, first_k=0;

        for (int i=0; i<imgBitPlanes.getBlockWidth() && byte_len == null; i++) {
            for (int j=0; j<imgBitPlanes.getBlockHeight() && byte_len == null; j++) {
                for (int k=0; k<BP_DEPTH && byte_len == null; k++) {
                    BitSet bp = imgBitPlanes.getBitPlane(i, j, k);

                    if (countComplexity(bp) >= threshold) {
                        byte_len = bp;
                        first_i = i;
                        first_j = j;
                        first_k = k;
                    }

                }
            }
        }

        assert byte_len != null;
        byte_len.xor(CHESS_BOARD);
        int in_byte_len = (int) byte_len.toLongArray()[0];
        byte_len.xor(CHESS_BOARD);
        int in_bp_len = (int)Math.ceil((double)in_byte_len / (double)BYTE_IN_BP);
        assert in_bp_len * BYTE_IN_BP < MAX_INPUT_BYTE;
        int cm_bp_len = (int)Math.ceil((in_bp_len / (double)BIT_IN_BP));
        int bp_len = 1 + in_bp_len + cm_bp_len;

        BitSet[] bs = new BitSet[bp_len];

        bs[0] = (BitSet) byte_len.clone();

        int indexBitSet = 1;

        for (int i=0; i<imgBitPlanes.getBlockWidth() && indexBitSet < bs.length; i++) {
            for (int j=0; j<imgBitPlanes.getBlockHeight() && indexBitSet < bs.length; j++) {
                for (int k=0; k<BP_DEPTH && indexBitSet < bs.length; k++) {

                    if (i != first_i || j != first_j || k != first_k) {
                        BitSet bp = imgBitPlanes.getBitPlane(i, j, k);

                        double cs = countComplexity(bp);
                        if (cs >= threshold) {
                            bs[indexBitSet] = (BitSet) bp.clone();
                            indexBitSet++;
                        }
                    }

                }
            }
        }


        assert indexBitSet == bs.length : "There is no message in image";

        //imgBitPlanes.toPBC();
        return bs;
    }

    private BitSet[] extractMessage(double threshold, long seed) {
        assert imgBitPlanes != null;

        BitSet byte_len = null;

        int firstX, firstY, firstDepth;
        
        
        PosRandomizer rng = new PosRandomizer(seed, imgBitPlanes.getBlockWidth(), 
                                                    imgBitPlanes.getBlockHeight(), 
                                                    BP_DEPTH);
        
        int i=0;
        while (byte_len == null) {
            rng.next();
            firstX = rng.nextX;
            firstY = rng.nextY;
            firstDepth = rng.nextDepth;
            
            BitSet bp = imgBitPlanes.getBitPlane(firstX, firstY, firstDepth);

            if (countComplexity(bp) >= threshold) {
                byte_len = bp;
            }
            i++;
            assert i < imgBitPlanes.getBlockHeight() * imgBitPlanes.getBlockWidth() * BP_DEPTH * 2 :
                    "There is no message in image";   
        }

        assert byte_len != null;
        byte_len.xor(CHESS_BOARD);
        int in_byte_len = (int) byte_len.toLongArray()[0];
        byte_len.xor(CHESS_BOARD);
        int in_bp_len = (int)Math.ceil((double)in_byte_len / (double)BYTE_IN_BP);
        assert in_bp_len * BYTE_IN_BP < MAX_INPUT_BYTE;
        int cm_bp_len = (int)Math.ceil((in_bp_len / (double)BIT_IN_BP));
        int bp_len = 1 + in_bp_len + cm_bp_len;

        BitSet[] bs = new BitSet[bp_len];

        bs[0] = byte_len;

        int indexBitSet = 1;

        i=0;
        while (indexBitSet < bs.length) {
        
            rng.next();
            int currentX = rng.nextX;
            int currentY = rng.nextY;
            int currentDepth = rng.nextDepth;
            
            BitSet bp = imgBitPlanes.getBitPlane(currentX, currentY, currentDepth);

            double cs = countComplexity(bp);
            if (cs >= threshold) {
                bs[indexBitSet] = bp;
                indexBitSet++;
            }
            i++;
            assert i < imgBitPlanes.getBlockHeight() * imgBitPlanes.getBlockWidth() * BP_DEPTH * 2 :
                    "There is no message in image";   
        }

        return bs;
    }
    
    /**
     * contains original image, don't modify this.
     */
    private BufferedImage imgOriginal = null;
    /**
     * contains modified image, if you want to modify image, modify this
     */
    private BufferedImage imgModified = null;

    private String formatName = null;
    
    private boolean readImage(Path fileIn) {
        try (ImageInputStream input = ImageIO.createImageInputStream(fileIn.toFile())){
            ImageReader reader = ImageIO.getImageReaders(input).next();
            reader.setInput(input);

            assert "png".equalsIgnoreCase(reader.getFormatName()) ||
                "bmp".equalsIgnoreCase(reader.getFormatName());
            
            formatName = reader.getFormatName().toLowerCase();
            
            // Check that image is encoded and loaded in RGB/RGBA/GRAYSCALE format
            ImageTypeSpecifier rgbType = null;
            Iterator<ImageTypeSpecifier> imageTypes = reader.getImageTypes(0);
            while(imageTypes.hasNext()) {
                ImageTypeSpecifier next = imageTypes.next();
                int type = next.getColorModel().getColorSpace().getType();
                if (type == ColorSpace.TYPE_RGB || type == ColorSpace.TYPE_GRAY) {
                    rgbType = next;
                    break;
                }
            }

            assert rgbType != null;
            ImageReadParam readParam = reader.getDefaultReadParam();
            readParam.setDestinationType(rgbType);

            BufferedImage temp = reader.read(0, readParam);
            imgModified = new BufferedImage(temp.getWidth(), temp.getHeight(), BufferedImage.TYPE_INT_ARGB);
            imgModified.getGraphics().drawImage(temp, 0, 0, null);
            imgOriginal = new BufferedImage(temp.getWidth(), temp.getHeight(), BufferedImage.TYPE_INT_ARGB);
            imgOriginal.getGraphics().drawImage(temp, 0, 0, null);

            assert bufferedImagesEqual(imgOriginal, imgModified);
            reader.dispose();
            return true;

        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
            return false;

        }
    }

    private boolean writeImage(Path fileOut) {
        assert imgModified != null;
        try {
            ImageIO.write(imgModified, formatName, fileOut.toFile());
            return true;
        } catch (IOException ex) {
            Logger.getLogger(BpcsStega.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }


    /**
     * 2D MATRIX INDEXING (The number are printed 2d array)
     *         0---j---4    0---Y---4
     *      0 [1 0 0 0 0]    0 [1 0 0 0 0]
     *      | [0 1 0 0 0]    | [0 1 0 0 0]
     *      i [0 0 1 0 0]    X [0 0 1 0 0]
     *      | [0 0 0 1 0]    | [0 0 0 1 0]
     *      5 [0 0 0 0 1]    5 [0 0 0 0 1]
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

            public void setBitPlane(BitSet bs, int depth) {
                assert depth>=0 && depth<BP_DEPTH;
                block[depth] = bs;
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

        public void setBitPlane(BitSet bs, int blockX, int blockY, int depth) {
            assert blockX>=0 && blockX<getBlockWidth();
            assert blockY>=0 && blockY<getBlockHeight();
            assert depth>=0 && depth<BP_DEPTH;
            data.get(blockX).get(blockY).setBitPlane(bs, depth);
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
                            BitSet block_cpy = cur_block;
                            // Start of iteration
                            if (init) {
                                prev = block_cpy.get(i);
                                init = false;
                                continue;
                            }
                            cur_block.set(i, prev ^ block_cpy.get(i));
                            prev = block_cpy.get(i);
                        }
                    }
//                    for (int i=BP_DEPTH-1; i>0; i--) {
//                        BitSet cur_block = p_block.block[i];
//                        BitSet prev = p_block.block[i-1];
//                        for (int j=0; j < BIT_IN_BP; j++) {
//                            cur_block.set(j, prev.get(j) ^ cur_block.get(j));
//                        }
//                    }
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
                                continue;
                            }
                            cur_block.set(i, cur_block.get(i) ^ prev);
                            prev = cur_block.get(i);
                        }
                    }
//                    for (int i=1; i<BP_DEPTH; i++) {
//                        BitSet cur_block = p_block.block[i];
//                        BitSet prev = p_block.block[i-1];
//                        for (int j=0; j<BIT_IN_BP; j++) {
//                            cur_block.set(j, prev.get(j) ^ cur_block.get(j));
//                        }
//                    }
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder res = new StringBuilder();
            for(int i=0; i<getBlockWidth(); i++) {
                for(int j=0; j<getBlockHeight(); j++) {
                    for (int k=0; k<BP_DEPTH; k++) {
                        res.append(String.format("%d, %d, %d: ", i, j, k));
                        res.append(getBitPlane(i, j, k).toString());
                        res.append("\n");
                    }
                }
            }
            return res.toString();
        }
    }

    /**
     * BitPlane representation of the processed image.
     */
    private imageBitPlanes imgBitPlanes = null;

    /**
     * Parse the imgModified BufferedImage into the imgBitPlanes.
     */
    private boolean parseImgToBitPlanes() {
        assert imgModified != null;
        imgBitPlanes = new imageBitPlanes(imgModified.getWidth(), imgModified.getHeight());
        for(int i=0; i<imgModified.getWidth(); i++) {
            for(int j=0; j<imgModified.getHeight(); j++) {
                if(imgBitPlanes.inRange(i, j)) {
                    int color = imgModified.getRGB(i, j);
                    imgBitPlanes.setColor(i, j, color);
                    assert imgBitPlanes.getColor(i, j) == color;
                }
            }
        }
        return true;
    }

    /**
     * Parse the imgBitPlanes into the imgModified BufferedImage.
     * The BufferedImage must not be null and already contain an image,
     * This function will map the changes in the BitPlane to the image.
     */
    private boolean parseBitPlanesToImg() {
        assert imgBitPlanes != null && imgModified != null;
        for(int i=0; i<imgBitPlanes.getBlockWidth()*BP_LENGTH; i++) {
            for(int j=0; j<imgBitPlanes.getBlockHeight()*BP_LENGTH; j++) {
                int color = imgBitPlanes.getColor(i, j);
                imgModified.setRGB(i, j, color);
                assert imgModified.getRGB(i, j) == color;
            }
        }
        return true;
    }

    
    /**
     * return PSNR value between original image and modified image
     * if size isn't the same, the rest of smaller images will be
     * treated as is padded by (0,0,0).
     *
     * Be sure to load an image first!
     * @return PSNR between original and modified image
     */
    private double calculatePSNR() {
        assert imgOriginal != null && imgModified != null;

        int maxWidth = Math.max(imgOriginal.getWidth(),imgModified.getWidth());
        int maxHeight = Math.max(imgOriginal.getHeight(),imgModified.getHeight());

        double rms;
        int diff = 0;

        for(int i=0; i<maxWidth; i++) {
            for(int j=0; j<maxHeight; j++) {
                int originalColor = 0;
                int modifiedColor = 0;

                if ((i < imgOriginal.getWidth()) && (j < imgOriginal.getHeight())) {
                    originalColor = imgOriginal.getRGB(i, j);
                }

                if ((i < imgModified.getWidth()) && (j < imgModified.getHeight())) {
                    modifiedColor = imgModified.getRGB(i, j);
                }

                // take each color (ARGB), get the difference, sum it all
                for (int k=0;k<4;k++) {
                    int currentOriColor = (originalColor >> (8*k)) & 0xff;
                    int currentModColor = (modifiedColor >> (8*k)) & 0xff;

                    diff += Math.pow(currentModColor - currentOriColor, 2);
                }
            }
        }

        rms = Math.sqrt((double)diff/(double)(maxWidth*maxHeight));

        return 20 * Math.log10(255/rms);
    }

    
    /**
     * Empty Constructor for testing.
     */
    public BpcsStega() {
        byte[] message = "ABCDEFGHIJKLMNOPQRSTUVWXYZABC".getBytes();
        byte[] message2 = "another message entirely".getBytes();
        String key = "OCEANOGRAPHY";
        double threshold = 0.3;

        Path in = Paths.get("D:\\imagePNG1.png");
        readImage(in);
        parseImgToBitPlanes();

        embedMessage(preprocessInput(message, threshold), threshold);
        message = postprocessOutput(extractMessage(threshold));
        System.out.println("Message extracted 1 : " + new String(message));
        parseBitPlanesToImg();
        System.out.println("PSNR after embedding : " + calculatePSNR());

        parseImgToBitPlanes();
        message = postprocessOutput(extractMessage(threshold));
        System.out.println("Message extracted 2 : " + new String(message));
        parseBitPlanesToImg();
        System.out.println("PSNR after extracting before save  : " + calculatePSNR());

        Path out = Paths.get("D:\\imagePNG2.png");
        writeImage(out);


        in = Paths.get("D:\\imagePNG2.png");
        readImage(in);
        parseImgToBitPlanes();
        message = postprocessOutput(extractMessage(threshold)); // Error here, all bitset changed from beforce save-loading
        System.out.println("Message extracted 3 : " + new String(message));
        System.out.println("PSNR after extracting immediately : " + calculatePSNR());
        
        embedMessage(preprocessInput(message2, threshold), threshold, generateSeed(key));
        message = postprocessOutput(extractMessage(threshold, generateSeed(key)));
        System.out.println("Message extracted 4 : " + new String(message));
        parseBitPlanesToImg();
        System.out.println("PSNR after embedding with key : " + calculatePSNR());
        
        parseImgToBitPlanes();
        message = postprocessOutput(extractMessage(threshold, generateSeed(key)));
        System.out.println("Message extracted 5 : " + new String(message));
        parseBitPlanesToImg();
        System.out.println("PSNR after extracting before save  : " + calculatePSNR());
        
        out = Paths.get("D:\\imagePNG3.png");
        writeImage(out);
        
        in = Paths.get("D:\\image1.bmp");
        readImage(in);
        parseImgToBitPlanes();
        
        embedMessage(preprocessInput(message2, threshold), threshold, generateSeed(key));
        message = postprocessOutput(extractMessage(threshold, generateSeed(key)));
        System.out.println("Message extracted 6 : " + new String(message));
        parseBitPlanesToImg();
        System.out.println("PSNR after embedding with key : " + calculatePSNR());
        
        parseImgToBitPlanes();
        message = postprocessOutput(extractMessage(threshold, generateSeed(key)));
        System.out.println("Message extracted 7 : " + new String(message));
        parseBitPlanesToImg();
        System.out.println("PSNR after extracting before save  : " + calculatePSNR());
        
        out = Paths.get("D:\\image2.bmp");
        writeImage(out);
    }

    
    private static boolean bufferedImagesEqual(BufferedImage img1, BufferedImage img2) {
        if (img1.getWidth() == img2.getWidth() && img1.getHeight() == img2.getHeight()) {
            for (int x = 0; x < img1.getWidth(); x++) {
                for (int y = 0; y < img1.getHeight(); y++) {
                    int rgb1 = img1.getRGB(x, y);
                    int rgb2 = img2.getRGB(x, y);
                    if (rgb1 != rgb2)
                        return false;
                }
            }
        } else {
            return false;
        }
        return true;
    }
}
