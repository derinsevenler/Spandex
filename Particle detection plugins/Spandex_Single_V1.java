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


public class Spandex_Single_V1 implements PlugIn 
{
	protected ImagePlus image;
	// image property members
	private double sigma;
	private boolean showIntermediateImages;
	private double particleThreshold;
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

	private double[] xPos;
	private double[] yPos;
	private List<Double> xPosFiltered;
	private List<Double> yPosFiltered;
	private boolean isParticle=true;

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
			findKeyPoints(nirImagePlus);
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
		IJ.run(niImg, "Gaussian Blur 3D...", "x=" + sigma + " y=" + sigma + " z=1");
		if (showIntermediateImages)
		{
			medianImage.show();
			diffImage.show();
			niImg.show();
		}
		return niImg;
	}

	private void findKeyPoints(ImagePlus imagePro)
	{

		// Perform thresholding and get keypoints
		IJ.setThreshold(imagePro, particleThreshold, 1);
		IJ.run(imagePro,"Make Binary",""); //array of ones and zeros based on threshold setting
		IJ.run(imagePro,"Analyze Particles...", "size=0-200 circularity=0.40-1.00 show=[Overlay Outlines] display exclude clear record add in_situ");
		if (showIntermediateImages)
		{
			imagePro.setTitle("Keypoints");
			imagePro.show();
		}
		ResultsTable resultsTable = ResultsTable.getResultsTable();

		int xCol = resultsTable.getColumnIndex("XStart");
		if(xCol==resultsTable.COLUMN_NOT_FOUND)
			{
				isParticle=false;
				IJ.error("No particle is found");
				return;
			}
		int yCol =  resultsTable.getColumnIndex("YStart");
		xPos = resultsTable.getColumnAsDoubles(xCol);
		yPos = resultsTable.getColumnAsDoubles(yCol);
		if (!showIntermediateImages)
		{
			IJ.selectWindow("Results");
			IJ.run("Close");
			IJ.selectWindow("ROI Manager");
			IJ.run("Close");
		}
		
	}

//this isn't implemented properly yet
	private void filterKeyPoints()
	{
		// delete any keypoints within the bare Si region
		xPosFiltered = new ArrayList<Double>();
		yPosFiltered = new ArrayList<Double>();
		for (int n = 0; n<xPos.length; n++)
		{
			int thisXpx = (int)Math.round(xPos[n]);
			int thisYpx = (int)Math.round(yPos[n]);
				xPosFiltered.add(xPos[n]);
				yPosFiltered.add(yPos[n]);
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
		gd.addNumericField("Particle threshold: decrease for dim particles", .05, 3);
		gd.addNumericField("Bare silicon region threshold: increase for dirty chips (?)", 1.3, 1);
		gd.addCheckbox("Show intermediate images", false);

		gd.showDialog();
		if (gd.wasCanceled()) return false;

		// get entered values
		sigma = gd.getNextNumber();
		particleThreshold = gd.getNextNumber();
		siThreshold = gd.getNextNumber();
		showIntermediateImages = gd.getNextBoolean();
		return true;
	}

	public void showAbout() 
	{
		IJ.showMessage("SPANDEX: the Single Particle Analysis and Detection EXperience",
			" developed at Boston University for Single Particle Interferometric Reflectance Imaging Sensing (SP-IRIS)"
		);
	}

}
