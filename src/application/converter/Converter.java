package application.converter;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;


import application.encoder.GifEncoder;
import application.utility.GifUtility;
import application.utility.Utility;

public class Converter {
	
	public enum CONV_TYPE {TXT, IMG_OTHER, IMG_GIF};
	
	private final float scaleFactor = 1.5F;
	
	private static final String asciiChars = "-+^=>?$&@#";
	
	private final CONV_TYPE convType;
	
	private static int gifDelayTime;
	
	private final int blockSize;
	
	private int blockCountForWidth;
	
	private int blockCountForHeight;
	
	private String outFile;
	
	private File inputFile;
	
	private Color background;
	
	private Color foreground;
	
	public void setOutFile(String outFile) {
		this.outFile = outFile;
	}
	
	public Converter(File input, 
			int b,
			Converter.CONV_TYPE convType, 
			Color back, 
			Color fore){
		this.inputFile = input;
		this.blockSize = b;
		this.convType = convType;
		this.background = back;
		this.foreground = fore;
	}
	public void convert(File output){
		String format = Utility.inferExtension(this.inputFile.getName());
		File modifiedOutPath = new File(output.getAbsolutePath()+"\\"+outFile);
		GifEncoder ge = null;
			try{
				int charSize = 20;
				ImageReader reader = ImageIO.getImageReadersByFormatName(format).next();
				ImageInputStream stream = ImageIO.createImageInputStream(this.inputFile);
				ImageOutputStream outStream = new FileImageOutputStream(modifiedOutPath);
				
				reader.setInput(stream);
				
				int frameCount = reader.getNumImages(true);
				for(int i = 0;i<frameCount;i++){
					
					BufferedImage frame = reader.read(i);
					frame = scaleImage(frame);
					if(frame != null){
							blockCountForWidth = (frame.getWidth()/blockSize);
							blockCountForHeight = (frame.getHeight()/blockSize);
							
							char charMatrix[][] = new char[blockCountForHeight][blockCountForWidth];
							int colorMatrix[][] = new int[blockCountForHeight][blockCountForWidth];
							System.out.println(blockCountForWidth+" "+blockCountForHeight);
							//get pixels of this block
							//processing each block and computing grayscale
							for(int x = 1;x<=blockCountForWidth;x++){
								for(int y = 1;y<=blockCountForHeight;y++){		
									int endPointX = x*blockSize;
									int endPointY = y*blockSize;
									int startingPointX = endPointX - blockSize;
									int startingPointY = endPointY - blockSize;
									
									
									long avgColorForBlock;
									if(this.foreground == null){
										avgColorForBlock = getAvgColor(startingPointX, startingPointY, endPointX, endPointY, frame);
										colorMatrix[y-1][x-1] = (int)avgColorForBlock;
									}
									long avgGreyScaleForBlock = getAvgGS(startingPointX, startingPointY, endPointX, endPointY, frame);
									
									charMatrix[y-1][x-1] = getCorrespondingChar((int)avgGreyScaleForBlock);
								}
							}
							
							if(this.convType == Converter.CONV_TYPE.TXT){
									FileWriter fw = new FileWriter(modifiedOutPath);
									BufferedWriter bw = new BufferedWriter(fw); 
									for(int y = 0;y<blockCountForHeight;y++){
										for(int x = 0;x<blockCountForWidth;x++){
											bw.write(charMatrix[y][x]);
										}
										bw.write("\r\n");
									}
									bw.close();
							}
							
							else{

								int yOffset = charSize;
								
								int newImageWidth = blockCountForWidth*((charSize));
								int newImageHeight = blockCountForHeight*(charSize);
								
								BufferedImage outImage = new BufferedImage(newImageWidth, 
										newImageHeight, 
										BufferedImage.TYPE_INT_RGB);
								Graphics g = outImage.getGraphics();
								g.setColor(this.background); 
								g.fillRect(0, 0, newImageWidth, newImageHeight);
								Font f = new Font("Monospaced",Font.PLAIN, charSize);
								g.setFont(f);
								int currYOffset = 0;
								
								String lastLine = null;
							
									
									for(int y = 0;y<blockCountForHeight;y++){
										StringBuilder line = new StringBuilder();
										for(int x = 0;x<blockCountForWidth;x++){
											 line.append(charMatrix[y][x]);
										}
										if(this.foreground == null){
											int xOffset = 0;
											for(int z = 0;z<line.length();z++){
												
												g.setColor(new Color(colorMatrix[y][z]));
												g.drawString(String.valueOf(line.charAt(z)),xOffset,currYOffset);
												xOffset+=12;
											}
										}
										else{
											g.setColor(this.foreground);
											
											g.drawString(line.toString(), 0, currYOffset);
												
										}
										currYOffset+=yOffset;
										lastLine = line.toString();
									}
										
								
								
								
								outImage = outImage.getSubimage(0, 0, g.getFontMetrics().stringWidth(lastLine), newImageHeight);
							
								if(this.convType==Converter.CONV_TYPE.IMG_GIF){
									if(ge == null){
										gifDelayTime = GifUtility.getGifDelay(reader);
										ge = new GifEncoder(outStream,outImage.getType(), gifDelayTime);
										ge.encode();
									}
									ge.write(outImage);
											
								}
								else{
									ImageIO.write(outImage,Utility.inferExtension(modifiedOutPath.getAbsolutePath()),modifiedOutPath);
								}
							}
					}
				}	
			}
			catch(IOException e){
				
			}
			finally{
				try {
					if(ge!=null){
						ge.dispose();	
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		
	}
	private static char getCorrespondingChar(int i){
		int reqIndex = i % asciiChars.length();
		return asciiChars.charAt(reqIndex);
	}
	

	private static int[] getPixelRGBValues(BufferedImage i, int x, int y){
		int[] values = new int[4];
		int pixel = i.getRGB(x, y);
		
		values[0] = pixel >> 24 & 0xff;
	    values[1] = pixel >> 16 & 0xff;
		values[2] = pixel >> 8 & 0xff;
		values[3] = pixel & 0xff;
		return values;
	}
	//for separate block
	private long getAvgColor(int x, int y, int w, int h, BufferedImage img){
		int dominantColor;
		//fillout pixelmap
		int[][] pixelMap = new int[blockSize][blockSize];
		for(int i_x = 0;x<w;x++,i_x++){
			for(int i_y = 0;y<h;y++,i_y++){
				int pixel = img.getRGB(x,y);
				pixelMap[i_x][i_y] = pixel;
			}
		}
		dominantColor = findDominant(pixelMap);
		pixelMap = null;
		return dominantColor;
	}
	
	private int findDominant(int[][] map){
		Map<Integer, Integer> occursCounter = new HashMap<>();
		Integer occurredCount = null;
		for(int x = 0;x<blockSize;x++){
			for(int y = 0;y<blockSize;y++){
				if(map[x][y]!=0){
					occurredCount = occursCounter.get(map[x][y]);
					if(occurredCount!=null){
						occursCounter.put(map[x][y], occurredCount+1);	
					}
					else{
						occursCounter.put(map[x][y], 1);
						occurredCount = occursCounter.get(map[x][y]);
					}	
				}
				
				
			}
		}
		Integer maxValue = occurredCount;
		Integer currValue = null;
		Integer currValueKey= null;
		for(Map.Entry<Integer, Integer> entry:occursCounter.entrySet()){
			currValue = entry.getValue();
			if(currValue > maxValue){
				maxValue = currValue;
				currValueKey = entry.getKey();
			}
		}
		if(currValueKey !=null){
			return currValueKey.intValue();		
		}
		return 0;
	}
	
	private static long getAvgGS(int x, int y, int w, int h, BufferedImage img) throws IOException{
		
		long pixelsCount = (w-x) * (h-y);
		long scaleAccumulator = 0;
		long avg = 0;
		
		for(;x<w;x++){
			for(;y<h;y++){
				int[] argb = getPixelRGBValues(img,x,y);
				long greyPixel = (argb[1]+argb[2]+argb[3])/3;
				scaleAccumulator+=greyPixel;
			}
		}
		avg = scaleAccumulator / pixelsCount;
		return avg;
	}
	
	private BufferedImage scaleImage(BufferedImage rawImage){
		BufferedImage scaled = null;
		if(rawImage != null){
			int scaledWidth = (int) (rawImage.getWidth()*scaleFactor);
			scaled = new BufferedImage(scaledWidth, rawImage.getHeight(), rawImage.getType());
			Graphics2D g = scaled.createGraphics();
			g.drawImage(rawImage, 0, 0, scaledWidth, rawImage.getHeight(), null);
			g.dispose();
		}
		return scaled;
	}
}
