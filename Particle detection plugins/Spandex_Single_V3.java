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
 */



import java.awt.Color; 
import java.util.ArrayList;
import java.util.List;

import ij.IJ;
import ij.ImageJ; 
import ij.ImagePlus;
import ij.ImageStack;
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

//NOTE: This version of Spandex Single is my test bench. I will save all milestone, functioning versions as Spand_Single_VX
public class Spandex_Single_Experimental implements PlugIn 
{
	protected ImagePlus image;
	// image property members
	private double sigma;
	private boolean showIntermediateImages;
	private double thresholdMax;
	private double thresholdMin;
	private double siThreshold;
	private String arg;
	private int imWidth;
	private int imHeight;
	private int zSize;

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
			imWidth = rawImgPlus.getWidth();
			imHeight = rawImgPlus.getHeight();

			//process using classes below
			nirImagePlus = performPreProcessing();
			findKeyPoints(nirImagePlus, negContrast);
			if(isParticle)
			{
				filterKeyPoints();
				displayResults();
			}
			
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
		IJ.run(niImg, "Gaussian Blur...", "x=" + sigma + " y=" + sigma);
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
			ImagePlus negImage = imagePro.duplicate();
			IJ.setThreshold(negImage, -1*thresholdMax, -1*thresholdMin);
			IJ.run(negImage,"Make Binary",""); //array of ones and zeros based on threshold setting
			IJ.run(negImage,"Analyze Particles...", "size=0-200 circularity=0.40-1.00 show=[Overlay Outlines] display exclude clear record add in_situ");
			if (showIntermediateImages)
			{
				negImage.setTitle("Dim Keypoints");
				negImage.show();
			}
			ResultsTable resultsTable = ResultsTable.getResultsTable();

			int xCol = resultsTable.getColumnIndex("XStart");
			if(xCol==resultsTable.COLUMN_NOT_FOUND)
				{
					noDark = true;
				}
			int yCol =  resultsTable.getColumnIndex("YStart");
			xPosdim = resultsTable.getColumnAsDoubles(xCol);
			yPosdim = resultsTable.getColumnAsDoubles(yCol);
			if (!showIntermediateImages)
			{
				IJ.selectWindow("Results");
				IJ.run("Close");
				IJ.selectWindow("ROI Manager");
				IJ.run("Close");
			}
		}
		else
		{
			noDark = true;
			xPosdim = new double[0];
			yPosdim = new double[0];
		}


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
			if(xCol==resultsTable2.COLUMN_NOT_FOUND && noDark)
			{
				isParticle=false;
				IJ.error("No particle is found");
				return;
			}
		int yCol =  resultsTable2.getColumnIndex("YStart");
		xPosbright = resultsTable2.getColumnAsDoubles(xCol);
		yPosbright = resultsTable2.getColumnAsDoubles(yCol);
		if (!showIntermediateImages)
		{
			IJ.selectWindow("Results");
			IJ.run("Close");
			IJ.selectWindow("ROI Manager");
			IJ.run("Close");
		}
		
	//combines arrays derived from results tables
	//double[] xnetResults = new double [xPosbright.length + xPosdim.length];
	// we need to pre-allocate the memory for the array to copy correctly (can't propegate a null object)
	xnetResults = new double [xPosbright.length + xPosdim.length];
	System.arraycopy(xPosbright, 0, xnetResults, 0, xPosbright.length);
	System.arraycopy(xPosdim, 0, xnetResults, xPosbright.length, xPosdim.length);

	//double[] ynetResults = new double [yPosbright.length + yPosdim.length];
	ynetResults = new double [yPosbright.length + yPosdim.length];
	System.arraycopy(yPosbright, 0, ynetResults, 0, yPosbright.length);
	System.arraycopy(yPosdim, 0, ynetResults, yPosbright.length, yPosdim.length);

	}

//this isn't implemented properly yet
	private void filterKeyPoints()
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

		// TODO: look at PSFs, brightness etc
	}

	private void displayResults()
	{
		int nParticles = xPosFiltered.size();
		
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

		// create a resultsTable and put it in the resultsWindow
		ResultsTable resultsTable = new ResultsTable();
		for (int n = 0; n<nParticles; n++)
		{
			resultsTable.setValue("x", n, xPosFiltered.get(n));
			resultsTable.setValue("y", n, yPosFiltered.get(n));
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
		gd.addCheckbox("Show intermediate images", false);
		gd.addCheckbox("Are their dark particles of interest? (Below background contrast)", true);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		// get entered values
		sigma = gd.getNextNumber();
		thresholdMin = gd.getNextNumber();
		thresholdMax = gd.getNextNumber();
		siThreshold = gd.getNextNumber();
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