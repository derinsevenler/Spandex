/*
 * Single Particle Analysis and Detection EXperience
 * (SPANDEX)
 * for the Single Particle IRIS (SP-IRIS)
 * 
 * @author Joseph Greene <joeg18@bu.edu>
 * Created March 2017
 *
 * Adapted from Spandex_Stack
 * by Derin Sevenler <derin@bu.edu>
 * This version of SPANDEX is designed for multispot acquisition for real time experiments. It will take in an image, and based on the center of the first spot, find each spot, crop it, and analyze it. Then take the data and organize it into trials and conditions
 */

//This version is not complete. It does not handle images with no particles yet. I.E it stops processing if a zero appears for any image in the stack. 
import java.awt.Color; 
import java.util.ArrayList;
import java.util.List;
import java.lang.Integer;

import ij.IJ;
import ij.ImageJ; 
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.io.Opener;
import ij.measure.ResultsTable;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;
import ij.plugin.ImageCalculator;

public class Spandex_Crop_V2 implements PlugIn 
{
	protected ImagePlus image;

	// image property members
	private double sigma;
	private double thresholdMax;
	private double thresholdMin;
	private double siThreshold;
	private double tDelay;
	private double time;
	private double xPixel;
	private double yPixel;
	private double deltaP;
	private double spotDiameter;
	private double deltaD;
	private double rows;
	private double cols;
	private double mag;

	private int counter = 0;
	private int imWidth;
	private int imHeight;
	private int zSize;
	private int nFrames;

	private String arg;

	private ImagePlus rawImgPlus;
	private ImagePlus nirImagePlus;
	private ImagePlus thisSlice;

	private FloatProcessor maxVals;
	private FloatProcessor minVals;
	private FloatProcessor maxIdx;
	private FloatProcessor minIdx;
	private FloatProcessor nirs;
	private FloatProcessor pps;

	private double[] xPosdim;
	private double[] yPosdim;
	private double[] xPosbright;
	private double[] yPosbright;
	private double[] xnetResults;
	private double[] ynetResults;
	private double[] timeStamps;
	private double[] totalParticles;

	private int [] characteristics;

	private float[] particleXY;

	private List<Double> xPosFiltered;
	private List<Double> yPosFiltered;

	private boolean isParticle=true;
	private boolean negContrast;
	private boolean noDark = false;
	private boolean showIntermediateImages;

	@Override
	public void run(String arg) 
	{
		if (showDialog()) 
		{
			//acquire relevant image data
			rawImgPlus = IJ.getImage();
			//get dimensions returns an array of width, height, channel #, zSlice # and Frame #
			characteristics = rawImgPlus.getDimensions();
			imWidth = characteristics[0];
			imHeight = characteristics[1];
			zSize = characteristics[3];
			nFrames = characteristics[4];
			//we had a problem where our data would save frames instead of zSlices, so the code now checks both and assumes the larger number indicates how the images are saved.
			if(nFrames > zSize) zSize = nFrames;
			//since we're cropping the image into several pieces, our array must be the stack z depth times the number of slices
			totalParticles = new double [zSize*(int)(rows*cols)];
			timeStamps = new double [zSize*(int)(rows*cols)];


			//process using classes below
			nirImagePlus = performPreProcessing();
			
			//convert distance between spots (in micrometers) to pixels between spots, based on the specifics for our camera.
			deltaP = deltaD*(mag/3.45);
			double spotPixelDiameter = spotDiameter*(mag/3.45);
			double resetY = yPixel;
			Calibration cal = nirImagePlus.getCalibration();
			//this will crop all trials and then conditions
			for(int x=0;x<cols;x++)
			{
				xPixel = xPixel + deltaP*x;
				//ensures x axis doesn't place ROI off the image
				if((int)(xPixel+spotPixelDiameter/2) > nirImagePlus.getWidth())
					xPixel = (double)nirImagePlus.getWidth() - spotPixelDiameter/2;
				//x changes each loop, y needs to reset whenever x changes so the loop starts at the top of the image
				yPixel = resetY;

				for(int y=0;y<rows;y++)
				{
					ImageStack is = nirImagePlus.getStack();
					yPixel = yPixel + deltaP*y;
					if((int)(yPixel+spotPixelDiameter/2) > nirImagePlus.getHeight())
						yPixel = (double)nirImagePlus.getHeight() - spotPixelDiameter/2;	
					ImageStack cropped = crop(is, (int)(xPixel-spotPixelDiameter/2), (int)(yPixel-spotPixelDiameter/2), 0, (int)(spotPixelDiameter), (int)(spotPixelDiameter), zSize);
					//new ImagePlus("", cropped).show();

					//processing each cropped image
					for (int idz = 1; idz<=zSize; idz++)
					{
						thisSlice = new ImagePlus(cropped.getSliceLabel(idz), cropped.getProcessor(idz));
						//thisSlice.setCalibration(cal);
						thisSlice.show();
						findKeyPoints();
						if(isParticle)
						{
							showKeyPoints();
						} 
						counter++;
						//IJ.log(String.valueOf(counter));
					}
				}
			}
			displayResults();
		}
	}

	private ImagePlus performPreProcessing()
	{
		// Perform filtering and smoothing on the stack to reduce shot noise and illumination gradients.
		// We are basically making the image into NI by subtracting and then dividing by E_ref
		
		// medianImage is essentially our estimate for E_ref
		ImagePlus medianImage = rawImgPlus.duplicate();
		medianImage.setTitle("Background Image");

		// Uses the 'Fast Filters' plugin
		//Fast Filters is a built in plugin w/ documentation here: http://imagejdocu.tudor.lu/doku.php?id=plugin:filter:fast_filters:start
		int kernelSize = (int)(Math.round(20*sigma));

		// int imgMean = (int)(Math.round(rawImgPlus.getProcessor().getStatistics().mean));
		IJ.run(medianImage, "Fast Filters", "link filter=median x=" + kernelSize + " y=" + kernelSize + " preprocessing=none stack");
		
		// Subtract medianImage from original
		ImageCalculator ic = new ImageCalculator();
		ImagePlus diffImage = ic.run("Subtract create 32-bit stack", rawImgPlus, medianImage); //calculates the difference between the two
		diffImage.setTitle("Difference image");
		
		// Divide by the medianImage to get niImg
		// Convert from 16-bit unsigned int to float
		// medianImage.setProcessor(medianImage.getProcessor().convertToFloat());
		ImagePlus niImg = ic.run("Divide create 32-bit stack", diffImage, medianImage);
		niImg.setTitle("Normalized intensity image");

		// Perform smoothing.
		// Convolution with the correct kernel does not effect peak amplitude but reduces noise. Since we don't average the stack, we must run a 2D image on each slice, instead of a 3d filter
		// This step is correspondingly tricky. 
		ImageStack stack = niImg.getStack();
		Calibration cal = niImg.getCalibration();
		//ImageStack niStack = new ImageStack(imWidth, imHeight, zSize);
		for (int idz = 1; idz<=zSize; idz++)
		{

			ImagePlus thisSlice = new ImagePlus(stack.getSliceLabel(idz), stack.getProcessor(idz));
			thisSlice.setCalibration(cal);
			IJ.run(thisSlice, "Gaussian Blur...", "x=" + sigma + " y=" + sigma + " z=1");
			stack.addSlice(stack.getSliceLabel(idz), thisSlice.getProcessor(), idz);	
			stack.deleteSlice(idz);
		}
		niImg.setStack(stack);
		
		if (showIntermediateImages)
		{
			medianImage.show();
			diffImage.show();
			niImg.show();
		}
		return niImg;
	}

	private void findKeyPoints()
	{
		//there are dim particles of interest as well as bright
		if(negContrast)
		{
			//duplicates image since we need to do 2 thresholdings (above and below background)
			ImagePlus negImage = thisSlice.duplicate();
			IJ.setThreshold(negImage, -1*thresholdMax, -1*thresholdMin);
			IJ.run(negImage,"Make Binary",""); //array of ones and zeros based on threshold setting
			IJ.run(negImage,"Analyze Particles...", "size=0-200 circularity=0.40-1.00 show=[Overlay Outlines] display exclude clear record add in_situ");
			if (showIntermediateImages)
			{
				negImage.setTitle("Dim Keypoints");
				negImage.show();
			}
			//generating a results table is the easiest way of collecting and organizing all the results. 
			ResultsTable resultsTable = ResultsTable.getResultsTable();

			int xCol = resultsTable.getColumnIndex("XStart");
			if(xCol==resultsTable.COLUMN_NOT_FOUND)
			{
				noDark = true;
			}
			//since we're not ending the code if just one condition reads zero, the else loop is just a safty
			else
			{
				//these lines take the info from the results table and saves each array
				int yCol =  resultsTable.getColumnIndex("YStart");
				xPosdim = resultsTable.getColumnAsDoubles(xCol);
				yPosdim = resultsTable.getColumnAsDoubles(yCol);
			}

		}
		//these conditions allow the code to execute properly if you decide you're not looking for any dark particles
		else
		{
			noDark = true;
			xPosdim = new double[0];
			yPosdim = new double[0];
		}
		//end of dark particle analysis

		// Perform thresholding and get keypoints above background noise
		IJ.setThreshold(thisSlice, thresholdMin, thresholdMax);
		IJ.run(thisSlice,"Make Binary",""); //array of ones and zeros based on threshold setting
		IJ.run(thisSlice,"Analyze Particles...", "size=0-200 circularity=0.40-1.00 show=[Overlay Outlines] display exclude clear record add in_situ");
		if (showIntermediateImages)
		{
			thisSlice.setTitle("Bright Keypoints");
			thisSlice.show();
		}
		ResultsTable resultsTable2 = ResultsTable.getResultsTable();
		int xCol = resultsTable2.getColumnIndex("XStart");
		//if both dim and bright particles are not found, ends the code
		if(xCol==resultsTable2.COLUMN_NOT_FOUND && noDark)
		{
			isParticle=false;
			IJ.error("No particle detected");
			return;
		}
		//foreseeably, we could analyze an image with just dark particles- this will prevent it from breaking if no bright particles found
		if(xCol==resultsTable2.COLUMN_NOT_FOUND)
		{
			xPosbright = new double[0];
			yPosbright = new double[0];
		}
		//normal condition
		else
		{
			int yCol =  resultsTable2.getColumnIndex("YStart");
			xPosbright = resultsTable2.getColumnAsDoubles(xCol);
			yPosbright = resultsTable2.getColumnAsDoubles(yCol);
		}

		if (!showIntermediateImages && isParticle == true)
		{
			IJ.selectWindow("Results");
			IJ.run("Close");
			IJ.selectWindow("ROI Manager");
			IJ.run("Close");
		}
		
		//combines arrays derived from results tables
		// we need to pre-allocate the memory for the array to copy correctly (can't propegate a null object)
		xnetResults = new double [xPosbright.length + xPosdim.length];
		System.arraycopy(xPosbright, 0, xnetResults, 0, xPosbright.length);
		System.arraycopy(xPosdim, 0, xnetResults, xPosbright.length, xPosdim.length);

		ynetResults = new double [yPosbright.length + yPosdim.length];
		System.arraycopy(yPosbright, 0, ynetResults, 0, yPosbright.length);
		System.arraycopy(yPosdim, 0, ynetResults, yPosbright.length, yPosdim.length);

		//now grabs the particle total, and what time this occured
		totalParticles[counter] = xnetResults.length;
		timeStamps[counter] = tDelay*(counter);

	}

//this isn't implemented properly yet
	private void showKeyPoints()
	{
		// delete any keypoints within the bare Si region
		xPosFiltered = new ArrayList<Double>();
		yPosFiltered = new ArrayList<Double>();
		for (int n = 0; n<xnetResults.length; n++)
		{
			int thisXpx = (int)Math.round(xnetResults[n]);
			int thisYpx = (int)Math.round(ynetResults[n]);
				xPosFiltered.add(xnetResults[n]);
				yPosFiltered.add(ynetResults[n]);
			// }
		}

		//I moved the overlay option here so it runs for every image. This is only temporary.
		int nParticles = xPosFiltered.size();

		//since we are dealing with a stack, we will only show the overlay if the user wants to see each overlay.
		if(showIntermediateImages)
		{
			// Create an overlay to show particles
			Overlay particleOverlay = new Overlay();
			for (int n = 0; n<nParticles; n++)
			{
				Roi thisParticle = new OvalRoi(xPosFiltered.get(n)-4, yPosFiltered.get(n)-4, 16, 16);
				thisParticle.setStrokeColor(Color.red);
				particleOverlay.add(thisParticle);
			}
			rawImgPlus.setOverlay(particleOverlay);
			IJ.run(rawImgPlus,"Enhance Contrast", "saturated=0.4");
			IJ.wait(1000);
		}

		// TODO: look at PSFs, brightness etc
	}

	private void displayResults()
	{

		// create a resultsTable and put it in the resultsWindow
		ResultsTable resultsTable = new ResultsTable();
		int trial =0;
		int condition = 0;
		boolean heading = false;
		//counter must be >0 for the resultsTable to generate columns. Who Guessed it??? I didn't :/
		resultsTable.incrementCounter();
		for (int n = 0; n<counter; n++)
		{
			//cycled through a trial
			if(n%zSize == 0 && n%(zSize*rows) != 0)
			{
				//resultsTable.setHeading(1+trial+condition*(int)rows,'x'+String.valueOf(condition+1)+'-'+String.valueOf(trial+1));
				trial++;
				heading = false;
			}

			//cycled through a condition
			if(n%(zSize*rows) == 0 && n != 0)
			{
				//resultsTable.setHeading(1+trial+condition*(int)rows,'x'+String.valueOf(condition+1)+'-'+String.valueOf(trial+1));
				condition++;
				trial = 0;
				heading = false;
			}
			//sets the heading of a new row
			if(!heading)
			{
				resultsTable.setHeading(trial+condition*(int)rows,'x'+String.valueOf(condition+1)+'-'+String.valueOf(trial+1));
				heading = true;
			}

			resultsTable.addValue((trial+condition*(int)rows), totalParticles[n]);
		// resultsTable.setValue("x", n, timeStamps[n]);
		// resultsTable.setValue("y", n, totalParticles[n]);
		}

		
		resultsTable.show("Particle Results");
		// Create a dialog summary
		// GenericDialog gd = new GenericDialog("SPANDEX RESULTS");
		// gd.addMessage("Total particles in this image: " + nParticles);
		// gd.showDialog();
	}

	private boolean showDialog() 
	{
		GenericDialog gd = new GenericDialog("WELCOME TO SPANDEX");

		// default value is 0.00, 2 digits right of the decimal point
		gd.addNumericField("Sigma: decrease for small particles", 2, 1);
		gd.addNumericField("Image Threshold minimum: decrease for dim particles", .01, 3);
		gd.addNumericField("Particle threshold maximum: decrease if prominent noise, or bare si", 0.06, 3);
		gd.addNumericField("Bare silicon region threshold: increase for dirty chips (?)", 1.3, 1);
		gd.addNumericField("Time delay between images, in minutes", 5.0, 3);
		gd.addNumericField("x Location (in pixels) of first spot", 0.0, 3);
		gd.addNumericField("y Location (in pixels) of first spot", 0.0, 3);
		gd.addNumericField("number of trials (rows)", 4, 0);
		gd.addNumericField("number of conditions(columns)",4, 0);
		gd.addNumericField("Distance between spots (microns)", 225, 3);
		gd.addNumericField("Microscope Magnification", 20, 2);
		gd.addNumericField("Spot Diameter (microns)", 150, 3);
		gd.addCheckbox("Show intermediate images", false);
		gd.addCheckbox("Are their dark particles of interest? (Below background contrast)", true);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		// get entered values
		sigma = gd.getNextNumber();
		thresholdMin = gd.getNextNumber();
		thresholdMax = gd.getNextNumber();
		siThreshold = gd.getNextNumber();
		tDelay = gd.getNextNumber();
		xPixel = gd.getNextNumber();
		yPixel = gd.getNextNumber();
		rows = gd.getNextNumber();
		cols = gd.getNextNumber();
		deltaD = gd.getNextNumber();
		mag = gd.getNextNumber();
		spotDiameter = gd.getNextNumber();
		showIntermediateImages = gd.getNextBoolean();
		negContrast = gd.getNextBoolean();
		return true;
	}

	//Micromanager uses imageJ 1.46r, which does not host this command so this is my rendition of it from the API/ documentation
	public ImageStack crop(ImageStack is, int x, int y, int z, int width, int height, int depth) 
	{
		if (x<0||y<0||x+width>is.getWidth()||y>is.getHeight())
	 		throw new IllegalArgumentException("x,y dimensions out of range, please check input parameters");
		ImageStack newstack = new ImageStack(width, height, is.getColorModel());
		for (int i=z; i<z+depth; i++) 
		{
			ImageProcessor ip = is.getProcessor(i+1);
			ip.setRoi(x, y, width, height);
			ip = ip.crop();
			// IJ.log("width "+String.valueOf(x));
			// IJ.log("height "+String.valueOf(y));
			newstack.addSlice(is.getSliceLabel(i+1), ip);			
		}
		return newstack;
	}

	public void showAbout() 
	{
		IJ.showMessage("SPANDEX: the Single Particle Analysis and Detection EXperience",
			" developed at Boston University for Single Particle Interferometric Reflectance Imaging Sensing (SP-IRIS)"
		);
	}

}