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
 * This version of SPANDEX runs spandex single on each image of a stack. Use when you are counting particles hybridization/ dehyb over time. 
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
//IJ.log('s')
//IJ.setLocation(x,y) w/ screenWidth, screenHeight
public class Spandex_Time implements PlugIn 
{
	protected ImagePlus image;

	// image property members
	private double sigma;
	private boolean showIntermediateImages;
	private double thresholdMax;
	private double thresholdMin;
	private double siThreshold;
	private double tDelay;
	private double time;
	private int counter = 0;
	private String arg;
	private int imWidth;
	private int imHeight;
	private int zSize;
	private int nFrames;

	private ImagePlus rawImgPlus;
	private ImagePlus nirImagePlus;

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
	private List<Double> xPosFiltered;
	private List<Double> yPosFiltered;
	private boolean isParticle=true;
	private boolean negContrast;
	private boolean noDark = false;

	private float[] particleXY;

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
			totalParticles = new double [zSize];
			timeStamps = new double [zSize];


			//process using classes below
			nirImagePlus = performPreProcessing();

			//grab the stack and calibration so we can turn each slice into an imagePlus object
			ImageStack stack = nirImagePlus.getStack();
			Calibration cal = nirImagePlus.getCalibration();

			//runs SpandexSingle on each image in the stack. 
			for (int idz = 1; idz<=zSize; idz++)
			{
				ImagePlus thisSlice = new ImagePlus(stack.getSliceLabel(idz), stack.getProcessor(idz));
				thisSlice.setCalibration(cal);
				findKeyPoints(thisSlice, negContrast);
				if(isParticle)
				{
					showKeyPoints();
				} 
				counter++;
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
		// Convolution with the correct kernel does not effect peak amplitude but reduces noise
		IJ.run(niImg, "Gaussian Blur 3D...", "x=" + sigma + " y=" + sigma + " z=1");
		if (showIntermediateImages)
		{
			medianImage.show();
			diffImage.show();
			niImg.show();
		}
		return niImg;
	}

	private void findKeyPoints(ImagePlus imagePro, boolean negCon)
	{
		//there are dim particles of interest as well as bright
		if(negCon)
		{
			//duplicates image since we need to do 2 thresholdings (above and below background)
			ImagePlus negImage = imagePro.duplicate();
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
		IJ.setThreshold(imagePro, thresholdMin, thresholdMax);
		IJ.run(imagePro,"Make Binary",""); //array of ones and zeros based on threshold setting
		IJ.run(imagePro,"Analyze Particles...", "size=0-200 circularity=0.40-1.00 show=[Overlay Outlines] display exclude clear record add in_situ");
		if (showIntermediateImages)
		{
			imagePro.setTitle("Bright Keypoints");
			imagePro.show();
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
		for (int n = 0; n<zSize; n++)
		{
			resultsTable.setValue("x", n, timeStamps[n]);
			resultsTable.setValue("y", n, totalParticles[n]);
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
		showIntermediateImages = gd.getNextBoolean();
		negContrast = gd.getNextBoolean();
		return true;
	}

	public void showAbout() 
	{
		IJ.showMessage("SPANDEX: the Single Particle Analysis and Detection EXperience",
			" developed at Boston University for Single Particle Interferometric Reflectance Imaging Sensing (SP-IRIS)"
		);
	}

}