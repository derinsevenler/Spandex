/* 
 * Single Particle Analysis and Detection EXperience
 * (SPANDEX)
 * for Single Particle IRIS (SP-IRIS)
 * 
 * @author Derin Sevenler <derin@bu.edu>
 * Created February 2017
 */
// testing Github

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


public class Spandex_Stack implements PlugIn {
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

	// private ImageProcessor siMask;
	private float[] particleXY;

	@Override
	public void run(String arg) {
		if (showDialog()) {
			rawImgPlus = IJ.getImage();
			imWidth = rawImgPlus.getWidth();
			imHeight = rawImgPlus.getHeight();
			zSize = rawImgPlus.getNSlices();
			
			// makeSIMask(rawImgStack.getProcessor(1));
			nirImagePlus = performPreProcessing();
			findKeyPoints(nirImagePlus.getStack());
			filterKeyPoints();
			displayResults();
			
		}

	}

	// private void makeSIMask(ImageProcessor ip){
	// 	ImageProcessor smallImg = ip.resize(ip.getWidth()/5);
	// 	ImagePlus smallImgPl = new ImagePlus("smallImage", smallImg);
	// 	IJ.run(smallImgPl, "Bandpass Filter...", "filter_large=40 filter_small=5 suppress=None tolerance=5 autoscale saturate process");
	// 	ImageStatistics smallStats = smallImgPl.getProcessor().getStatistics();
	// 	smallImg.threshold((int)Math.round(smallStats.mean*siThreshold));
		
	// 	ByteProcessor siByteMask = smallImg.convertToByteProcessor();
	// 	for (int idx = 0; idx<12; idx++){
	// 		siByteMask.erode();
	// 	}
	// 	siMask = siByteMask.resize(ip.getWidth(), ip.getHeight());
	// 	if (showIntermediateImages){
	// 		ImagePlus siMaskPl = new ImagePlus("Bare Silicon Regions",siMask);
	// 		siMaskPl.show();
	// 	}
	// }

	private ImagePlus performPreProcessing(){
		// Perform filtering and smoothing on the stack to reduce shot noise and illumination gradients.
		// We are basically making the image into NI by subtracting and then dividing by E_ref
		
		// medianImage is essentially our estimate for E_ref
		ImagePlus medianImage = rawImgPlus.duplicate();
		medianImage.setTitle("Background Image");
		// Uses the 'Fast Filters' plugin
		int kernelSize = (int)(Math.round(20*sigma));
		// int imgMean = (int)(Math.round(rawImgPlus.getProcessor().getStatistics().mean));
		IJ.run(medianImage, "Fast Filters", "link filter=median x=" + kernelSize + " y=" + kernelSize + " preprocessing=none stack");
		
		// Subtract medianImage from original
		ImageCalculator ic = new ImageCalculator();
		ImagePlus diffImage = ic.run("Subtract create 32-bit stack", rawImgPlus, medianImage);
		diffImage.setTitle("Difference image");
		
		// Divide by the medianImage to get niImg
		// Convert from 16-bit unsigned int to float
		// medianImage.setProcessor(medianImage.getProcessor().convertToFloat());
		ImagePlus niImg = ic.run("Divide create 32-bit stack", diffImage, medianImage);
		niImg.setTitle("Normalized intensity image");

		// Perform smoothing.
		// Convolution with the correct kernel does not effect peak amplitude but reduces noise
		IJ.run(niImg, "Gaussian Blur 3D...", "x=" + sigma + " y=" + sigma + " z=1");
		if (showIntermediateImages){
			medianImage.show();
			diffImage.show();
			niImg.show();
		}
		return niImg;
	}

	private void findKeyPoints(ImageStack imageStack){
		// Find the min and max values and indices


		maxVals = (FloatProcessor) imageStack.getProcessor(1).duplicate();
		float[] maxValPixs = (float[]) maxVals.getPixels();
		minVals = (FloatProcessor) imageStack.getProcessor(1).duplicate();
		float[] minValPixs = (float[]) minVals.getPixels();
		maxIdx = new FloatProcessor(imWidth, imHeight);
		float[] maxIdxPixs = (float[]) maxIdx.getPixels();
		minIdx = new FloatProcessor(imWidth, imHeight);
		float[] minIdxPixs = (float[]) minIdx.getPixels();
		for (int idz = 1; idz<zSize; idz++){
			ImageProcessor thisSlice = imageStack.getProcessor(idz+1);
			float[] thisSlicePixs = (float[]) thisSlice.getPixels();
			for (int idx = 0; idx<thisSlicePixs.length; idx++){
				if ((thisSlicePixs[idx]) > (maxValPixs[idx])){
					maxValPixs[idx] = thisSlicePixs[idx];
					maxIdxPixs[idx] = idz;
				} else if ((thisSlicePixs[idx]) < (minValPixs[idx])){
					minValPixs[idx] = thisSlicePixs[idx];
					minIdxPixs[idx] = idz;
				}
			}
			IJ.showProgress((idz+1),zSize);
		}
		
		
		// Calculate the NIR and PPS
		nirs = new FloatProcessor(imWidth, imHeight);
		pps = new FloatProcessor(imWidth, imHeight);
		float[] nirPixs = (float[]) nirs.getPixels();
		for (int idx = 0; idx<maxValPixs.length; idx++){
			nirPixs[idx] = ((float)(maxValPixs[idx]) - (float)(minValPixs[idx]));
		}
		ImagePlus nirPlus = new ImagePlus("Normalized Intensity Range", nirs);

		if (showIntermediateImages){
			ImagePlus nirShow = nirPlus.duplicate();
			nirShow.show();
		}
		// Perform thresholding and get keypoints
		IJ.setThreshold(nirPlus, particleThreshold, 1);
		IJ.run(nirPlus,"Make Binary","");
		IJ.run(nirPlus,"Analyze Particles...", "size=0-200 circularity=0.40-1.00 show=[Overlay Outlines] display exclude clear record add in_situ");
		if (showIntermediateImages){
			nirPlus.setTitle("Keypoints");
			nirPlus.show();
		}
		ResultsTable resultsTable = ResultsTable.getResultsTable();

		int xCol = resultsTable.getColumnIndex("XStart");
		int yCol =  resultsTable.getColumnIndex("YStart");
		xPos = resultsTable.getColumnAsDoubles(xCol);
		yPos = resultsTable.getColumnAsDoubles(yCol);
		if (!showIntermediateImages){
			IJ.selectWindow("Results");
			IJ.run("Close");
			IJ.selectWindow("ROI Manager");
			IJ.run("Close");
		}
		
	}

	private void filterKeyPoints(){
		// delete any keypoints within the bare Si region
		xPosFiltered = new ArrayList<Double>();
		yPosFiltered = new ArrayList<Double>();
		for (int n = 0; n<xPos.length; n++){
			int thisXpx = (int)Math.round(xPos[n]);
			int thisYpx = (int)Math.round(yPos[n]);
			// if ( siMask.getPixel(thisXpx, thisYpx) < 10){
				// this particle is outside, it's ok
				xPosFiltered.add(xPos[n]);
				yPosFiltered.add(yPos[n]);
			// }
		}

		// TODO: look at PSFs, brightness etc
	}

	private void displayResults(){
		int nParticles = xPosFiltered.size();
		
		// Create an overlay to show particles
		Overlay particleOverlay = new Overlay();
		for (int n = 0; n<nParticles; n++){
			Roi thisParticle = new OvalRoi(xPosFiltered.get(n)-4, yPosFiltered.get(n)-4, 16, 16);
			thisParticle.setStrokeColor(Color.red);
			particleOverlay.add(thisParticle);
		}
		rawImgPlus.setOverlay(particleOverlay);
		IJ.run(rawImgPlus,"Enhance Contrast", "saturated=0.4");

		// create a resultsTable and put it in the resultsWindow
		ResultsTable resultsTable = new ResultsTable();
		for (int n = 0; n<nParticles; n++){
			resultsTable.setValue("x", n, xPosFiltered.get(n));
			resultsTable.setValue("y", n, yPosFiltered.get(n));
		}
		resultsTable.show("Particle Results");
		// Create a dialog summary
		// GenericDialog gd = new GenericDialog("SPANDEX RESULTS");
		// gd.addMessage("Total particles in this image: " + nParticles);
		// gd.showDialog();
	}

	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("WELCOME TO SPANDEX");

		// default value is 0.00, 2 digits right of the decimal point
		gd.addNumericField("Sigma: decrease for small particles", 2, 1);
		gd.addNumericField("Particle threshold: decrease for dim particles", .05, 3);
		gd.addNumericField("Bare silicon region threshold: increase for dirty chips (?)", 1.3, 1);
		gd.addCheckbox("Show intermediate images", false);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		sigma = gd.getNextNumber();
		particleThreshold = gd.getNextNumber();
		siThreshold = gd.getNextNumber();
		showIntermediateImages = gd.getNextBoolean();
		return true;
	}

	public void showAbout() {
		IJ.showMessage("SPANDEX: the Single Particle Analysis and Detection EXperience",
			" developed at Boston University for Single Particle Interferometric Reflectance Imaging Sensing (SP-IRIS)"
		);
	}

}
