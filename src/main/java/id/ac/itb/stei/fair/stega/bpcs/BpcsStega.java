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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;

/**
 *
 * @author USER
 */
public final class BpcsStega {

    private static final String bmp_format = "BMP";
    private static final String png_format = "PNG";

    private static final double EPSILON = 0.001;
    private static final int MAX_INPUT_BYTE = Integer.MAX_VALUE;

    private static final BitSet CHESS_BOARD;
    private static final double MAX_CXTY = 112;
    static {
        CHESS_BOARD = new BitSet(imgBPs.BIT_IN_BP);
        for(int i=1, n=0; i<imgBPs.BIT_IN_BP; i+=2) {
            CHESS_BOARD.set(i);
            if(++n%4==0) if(n%8==0) i++; else i--;
        }
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
    
    private static double countComplexity(BitSet in) {
        int res = 0;
        assert in.size() == imgBPs.BIT_IN_BP;
        boolean curr, right, down;

        for(int i=0; i<imgBPs.BP_BIT_SIDE_LEN; i++) {
            for(int j=0; j<imgBPs.BP_BIT_SIDE_LEN; j++) {
                curr = in.get((i*8)+j);
                right = (j+1)<imgBPs.BP_BIT_SIDE_LEN ? in.get((i*imgBPs.BP_BIT_SIDE_LEN)+(j+1)) : curr;
                down = (i+1)<imgBPs.BP_BIT_SIDE_LEN ? in.get(((i+1)*imgBPs.BP_BIT_SIDE_LEN)+j) : curr;
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
        int in_bp_len = (int)Math.ceil((double)in.length / (double)imgBPs.BYTE_IN_BP);
        assert in_bp_len * imgBPs.BYTE_IN_BP < MAX_INPUT_BYTE;
        int cm_bp_len = (int)Math.ceil((in_bp_len / (double)imgBPs.BIT_IN_BP));
        int bp_len = 1 + in_bp_len + cm_bp_len;

        BitSet res[] = new BitSet[bp_len];
        int idx = 0;
        res[idx++] = BitSet.valueOf(new long[]{in.length});

        // Skip conjugation mapping bitplane, process the input bitplane first
        idx += cm_bp_len;
        for(int i=0; i<in_bp_len; i++) {
            // Each iteration map the next 8 byte into the next BitSet
            byte next[] = new byte[imgBPs.BYTE_IN_BP];
            int id = i*imgBPs.BYTE_IN_BP;

            for(int j=0; j<imgBPs.BYTE_IN_BP; j++) {
                next[j] = (id+j)<in.length ? in[id+j] : 0;
            }
            res[idx++] = BitSet.valueOf(next);
            assert res[idx-1].size() == imgBPs.BIT_IN_BP;
        }

        // Processing the conjugation mapping bitplane
        idx = 1;
        for(int i=0; i<cm_bp_len; i++) {

            // Each iteration map the next 64 message BitSet into the next conjugate mapping bitset
            BitSet next = new BitSet(imgBPs.BIT_IN_BP);
            for(int j=0; j<imgBPs.BIT_IN_BP; j++) {
                if(i*imgBPs.BIT_IN_BP+j>=in_bp_len) break;

                // If a message BitSet need to be conjugated, set the respective conjugate map bit,
                // Also conjugate the message BitSet itself
                int next_in_idx = 1+cm_bp_len+(i*imgBPs.BIT_IN_BP+j);
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

        int in_bp_len = (int)Math.ceil((double)in_byte_len / (double)imgBPs.BYTE_IN_BP);
        assert in_bp_len * imgBPs.BYTE_IN_BP < MAX_INPUT_BYTE;
        int cm_bp_len = (int)Math.ceil((in_bp_len / (double)imgBPs.BIT_IN_BP));
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
            if(next.length != imgBPs.BYTE_IN_BP) {
                byte[] temp = new byte[imgBPs.BYTE_IN_BP];
                System.arraycopy(next, 0, temp, 0, next.length);
                next = temp;
            }
            assert next.length == imgBPs.BYTE_IN_BP;

            // For every byte in said "input" BitPlane, copy said byte to the output
            for(int j=0; j<imgBPs.BYTE_IN_BP; j++) {
                res[i*imgBPs.BYTE_IN_BP + j] = next[j];

                // Break out of loop if all the byte is processed
                in_byte_len--;
                if(in_byte_len == 0) break;
            }

            // Restore the conjugated BitPlane if needed
            if(in[cm_bp_idx].get(cm_bit_idx))
                in[in_bp_idx].xor(CHESS_BOARD);

            // Increment BP index
            cm_bit_idx++;
            if(cm_bit_idx==imgBPs.BIT_IN_BP) {
                cm_bp_idx++;
                cm_bit_idx = 0;
            }

            if(in_byte_len == 0) break;
            in_bp_idx++;
        }
        assert (cm_bp_idx-1)*imgBPs.BIT_IN_BP+cm_bit_idx == bp_len-(1+cm_bp_len);
        assert in_byte_len == 0;

        // Conjugate the ConjugateMapping
        for(int i=1; i<1+cm_bp_len; i++) {
            in[i].xor(CHESS_BOARD);
        }

        return res;
    }
    
    
    private String formatName = null;
    /**
     * contains original image, don't modify this.
     */
    private BufferedImage imgOriginal = null;
    /**
     * contains modified image, if you want to modify image, modify this
     */
    private BufferedImage imgModified = null;
    
    /**
     * BitPlane representation of the processed image.
     */
    private imgBPs imgBitPlanes = null;
    private final boolean useCGC;
    
    
    public boolean readImage(Path fileIn) {
        try (ImageInputStream input = ImageIO.createImageInputStream(fileIn.toFile())){
            ImageReader reader = ImageIO.getImageReaders(input).next();
            reader.setInput(input);

            assert "png".equalsIgnoreCase(reader.getFormatName()) ||
                "bmp".equalsIgnoreCase(reader.getFormatName());
            
            formatName = reader.getFormatName().toUpperCase();
            
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
            int type = BufferedImage.TYPE_INT_RGB;
            
            imgModified = new BufferedImage(temp.getWidth(), temp.getHeight(), type);
            imgModified.getGraphics().drawImage(temp, 0, 0, null);
            imgOriginal = new BufferedImage(temp.getWidth(), temp.getHeight(), type);
            imgOriginal.getGraphics().drawImage(temp, 0, 0, null);

            assert bufferedImagesEqual(imgOriginal, imgModified);
            reader.dispose();
            return true;

        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
            return false;

        }
    }
    public boolean writeImage(Path fileOut) {
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
     * Parse the imgModified BufferedImage into the imgBitPlanes.
     */
    private boolean parseImgToBitPlanes() {
        assert imgModified != null;
        imgBitPlanes = new imgBPs(imgModified.getWidth(), imgModified.getHeight());
        for(int i=0; i<imgModified.getWidth(); i++) {
            for(int j=0; j<imgModified.getHeight(); j++) {
                if(imgBitPlanes.inRange(i, j)) {
                    int color = imgModified.getRGB(i, j);
                    imgBitPlanes.setColor(i, j, color);
                    assert imgBitPlanes.getColor(i, j) == (color & 0x00ffffff);
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
        for(int i=0; i<imgBitPlanes.getBlockWidth()*imgBPs.BP_LENGTH; i++) {
            for(int j=0; j<imgBitPlanes.getBlockHeight()*imgBPs.BP_LENGTH; j++) {
                int color = imgBitPlanes.getColor(i, j);
                imgModified.setRGB(i, j, color);
                assert (imgModified.getRGB(i, j) & 0x00ffffff) == color;
            }
        }
        return true;
    }
    
    public void embedMessage(BitSet[] in, double threshold) {
        assert imgBitPlanes != null;
        if(useCGC) imgBitPlanes.toCGC();

        int indexBitSet = 0;

        for (int i=0; i<imgBitPlanes.getBlockWidth() && indexBitSet < in.length; i++) {
            for (int j=0; j<imgBitPlanes.getBlockHeight() && indexBitSet < in.length; j++) {
                for (int k=0; k<imgBPs.BP_DEPTH && indexBitSet < in.length; k++) {

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
        
        if(useCGC) imgBitPlanes.toPBC();
    }
    public BitSet[] extractMessage(double threshold) {
        assert imgBitPlanes != null;
        if(useCGC) imgBitPlanes.toCGC();

        BitSet byte_len = null;

        int first_i=0, first_j=0, first_k=0;

        for (int i=0; i<imgBitPlanes.getBlockWidth() && byte_len == null; i++) {
            for (int j=0; j<imgBitPlanes.getBlockHeight() && byte_len == null; j++) {
                for (int k=0; k<imgBPs.BP_DEPTH && byte_len == null; k++) {
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
        int in_bp_len = (int)Math.ceil((double)in_byte_len / (double)imgBPs.BYTE_IN_BP);
        assert in_bp_len * imgBPs.BYTE_IN_BP < MAX_INPUT_BYTE;
        int cm_bp_len = (int)Math.ceil((in_bp_len / (double)imgBPs.BIT_IN_BP));
        int bp_len = 1 + in_bp_len + cm_bp_len;
            
        assert bp_len > 0 && bp_len < imgBitPlanes.getBlockHeight() * imgBitPlanes.getBlockWidth() * imgBPs.BP_DEPTH :
                    "There is no message in image";  

        BitSet[] bs = new BitSet[bp_len];

        bs[0] = (BitSet) byte_len.clone();

        int indexBitSet = 1;

        for (int i=0; i<imgBitPlanes.getBlockWidth() && indexBitSet < bs.length; i++) {
            for (int j=0; j<imgBitPlanes.getBlockHeight() && indexBitSet < bs.length; j++) {
                for (int k=0; k<imgBPs.BP_DEPTH && indexBitSet < bs.length; k++) {

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

        if(useCGC) imgBitPlanes.toPBC();
        return bs;
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
    public void embedMessage(BitSet[] in, double threshold, long seed) {
        assert imgBitPlanes != null;
        if(useCGC) imgBitPlanes.toCGC();
               
        PosRandomizer rng = new PosRandomizer(seed, imgBitPlanes.getBlockWidth(), 
                                                    imgBitPlanes.getBlockHeight(), 
                                                    imgBPs.BP_DEPTH);
        
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
            assert i < imgBitPlanes.getBlockHeight() * imgBitPlanes.getBlockWidth() * imgBPs.BP_DEPTH * 2 :
                    "Image is too small for message";          
        }
 
        if(useCGC) imgBitPlanes.toPBC();
    }
    public BitSet[] extractMessage(double threshold, long seed) {
        assert imgBitPlanes != null;
        if(useCGC) imgBitPlanes.toCGC();

        BitSet byte_len = null;

        int firstX, firstY, firstDepth;
        
        
        PosRandomizer rng = new PosRandomizer(seed, imgBitPlanes.getBlockWidth(), 
                                                    imgBitPlanes.getBlockHeight(), 
                                                    imgBPs.BP_DEPTH);
        
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
            assert i < imgBitPlanes.getBlockHeight() * imgBitPlanes.getBlockWidth() * imgBPs.BP_DEPTH * 2 :
                    "There is no message in image";   
        }

        assert byte_len != null;
        byte_len.xor(CHESS_BOARD);
        int in_byte_len = (int) byte_len.toLongArray()[0];
        byte_len.xor(CHESS_BOARD);
        int in_bp_len = (int)Math.ceil((double)in_byte_len / (double)imgBPs.BYTE_IN_BP);
        assert in_bp_len * imgBPs.BYTE_IN_BP < MAX_INPUT_BYTE;
        int cm_bp_len = (int)Math.ceil((in_bp_len / (double)imgBPs.BIT_IN_BP));
        int bp_len = 1 + in_bp_len + cm_bp_len;

        assert bp_len > 0 && bp_len < imgBitPlanes.getBlockHeight() * imgBitPlanes.getBlockWidth() * imgBPs.BP_DEPTH :
                    "There is no message in image";  
        
        BitSet[] bs = new BitSet[bp_len];

        bs[0] = (BitSet) byte_len.clone();

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
                bs[indexBitSet] = (BitSet) bp.clone();
                indexBitSet++;
            }
            i++;
            assert i < imgBitPlanes.getBlockHeight() * imgBitPlanes.getBlockWidth() * imgBPs.BP_DEPTH * 2 :
                    "There is no message in image";   
        }

        if(useCGC) imgBitPlanes.toPBC();
        return bs;
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

        int maxWidth = imgOriginal.getWidth();
        int maxHeight = imgOriginal.getHeight();
        
        int channelSize = imgBPs.BP_DEPTH/imgBPs.BIT_IN_COLOR;
        double max = 0xffL;
        
        double rms;
        int diff = 0;
        int channelConst = 0x000000ff;
        
        for(int i=0; i<maxWidth; i++) {
            for(int j=0; j<maxHeight; j++) {
                int originalColor = imgOriginal.getRGB(i, j);
                int modifiedColor = imgModified.getRGB(i, j);

                // take each color (ARGB), get the difference, sum it all
                // For BMP format only consider the 24 LSB,
                for (int k=0;k<channelSize;k++) {
                    int currentOriColor = (originalColor >>> (imgBPs.BIT_IN_COLOR*k)) & channelConst;
                    int currentModColor = (modifiedColor >>> (imgBPs.BIT_IN_COLOR*k)) & channelConst;
                    diff += Math.pow(currentModColor - currentOriColor, 2);
                }
            }
        }

        rms = Math.sqrt((double)diff/(double)(maxWidth*maxHeight*channelSize));
        return 20 * Math.log10(max/rms);
    }
    
    
    /**
     * Empty Constructor for testing.
     */
    public BpcsStega() {
        
        useCGC = true;
        
        String msg = "To be fair, you have to have a very high IQ to understand Rick and Morty. The humour is extremely subtle, and without a solid grasp of theoretical physics most of the jokes will go over a typical viewer's head. There's also Rick's nihilistic outlook, which is deftly woven into his characterisation- his personal philosophy draws heavily from Narodnaya Volya literature, for instance. The fans understand this stuff; they have the intellectual capacity to truly appreciate the depths of these jokes, to realise that they're not just funny- they say something deep about LIFE. As a consequence people who dislike Rick & Morty truly ARE idiots- of course they wouldn't appreciate, for instance, the humour in Rick's existential catchphrase \"Wubba Lubba Dub Dub,\" which itself is a cryptic reference to Turgenev's Russian epic Fathers and Sons. I'm smirking right now just imagining one of those addlepated simpletons scratching their heads in confusion as Dan Harmon's genius wit unfolds itself on their television screens. What fools.. how I pity them. \nAnd yes, by the way, i DO have a Rick & Morty tattoo. And no, you cannot see it. It's for the ladies' eyes only- and even then they have to demonstrate that they're within 5 IQ points of my own (preferably lower) beforehand. Nothin personnel kid";
        byte[] message = msg.getBytes();
        byte[] output;
        String key = "OCEANOGRAPHY";
        double threshold = 0.3;
        Path in, out;
        
        in = Paths.get("D:\\imagePNG1.png");
        readImage(in);
        parseImgToBitPlanes();
        embedMessage(preprocessInput(message, threshold), threshold);
        output = postprocessOutput(extractMessage(threshold));
        parseBitPlanesToImg();
        System.out.println("PNG Embed/Extract Testing (Sequential)");
        System.out.println("Message extracted   : " + new String(output));
        System.out.println("PSNR                : " + calculatePSNR());
        System.out.println();
        out = Paths.get("D:\\imagePNG2.png");
        writeImage(out);

        in = Paths.get("D:\\imagePNG2.png");
        readImage(in);
        parseImgToBitPlanes();
        output = postprocessOutput(extractMessage(threshold)); 
        System.out.println("PNG Save/Load/Extract Testing (Sequential)");
        System.out.println("Message extracted   : " + new String(output));
        System.out.println();
        
        embedMessage(preprocessInput(message, threshold), threshold, generateSeed(key));
        output = postprocessOutput(extractMessage(threshold, generateSeed(key)));
        parseBitPlanesToImg();
        System.out.println("PNG Embed/Extract Testing (Pseudorandom)");
        System.out.println("Message extracted   : " + new String(output));
        System.out.println("PSNR                : " + calculatePSNR());
        System.out.println();
        out = Paths.get("D:\\imagePNG3.png");
        writeImage(out);
        
        in = Paths.get("D:\\imagePNG3.png");
        readImage(in);        
        parseImgToBitPlanes();
        output = postprocessOutput(extractMessage(threshold, generateSeed(key)));
        parseBitPlanesToImg();
        System.out.println("PNG Save/Load/Extract Testing (Pseudorandom)");
        System.out.println("Message extracted   : " + new String(output));
        System.out.println();
        
        
        in = Paths.get("D:\\image1.bmp");
        readImage(in);
        parseImgToBitPlanes();
        embedMessage(preprocessInput(message, threshold), threshold);
        output = postprocessOutput(extractMessage(threshold));
        parseBitPlanesToImg();
        System.out.println("BMP Embed/Extract Testing (Sequential)");
        System.out.println("Message extracted   : " + new String(output));
        System.out.println("PSNR                : " + calculatePSNR());
        System.out.println();
        out = Paths.get("D:\\image2.bmp");
        writeImage(out);

        in = Paths.get("D:\\image2.bmp");
        readImage(in);
        parseImgToBitPlanes();
        output = postprocessOutput(extractMessage(threshold)); 
        System.out.println("BMP Save/Load/Extract Testing (Sequential)");
        System.out.println("Message extracted   : " + new String(output));
        System.out.println();
        
        embedMessage(preprocessInput(message, threshold), threshold, generateSeed(key));
        output = postprocessOutput(extractMessage(threshold, generateSeed(key)));
        parseBitPlanesToImg();
        System.out.println("BMP Embed/Extract Testing (Pseudorandom)");
        System.out.println("Message extracted   : " + new String(output));
        System.out.println("PSNR                : " + calculatePSNR());
        System.out.println();
        out = Paths.get("D:\\image3.bmp");
        writeImage(out);
        
        in = Paths.get("D:\\image3.bmp");
        readImage(in);        
        parseImgToBitPlanes();
        output = postprocessOutput(extractMessage(threshold, generateSeed(key)));
        parseBitPlanesToImg();
        System.out.println("BMP Save/Load/Extract Testing (Pseudorandom)");
        System.out.println("Message extracted   : " + new String(output));
        System.out.println();
        
    }
    
    // TODO Add constructor for embedding and extracting message from given image
    
}
