/* 
 * Single Particle Analysis and Detection EXperience
 * (SPANDEX)
 * for Single Particle IRIS (SP-IRIS)
 * 
 * @author Derin Sevenler <derin@bu.edu>
 * Created February 2017
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
	private ImageStack rawImgStack;

	private ShortProcessor maxVals;
	private ShortProcessor minVals;
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
			rawImgStack = rawImgPlus.getStack();
			imWidth = rawImgPlus.getWidth();
			imHeight = rawImgPlus.getHeight();
			zSize = rawImgPlus.getNSlices();
			
			// makeSIMask(rawImgStack.getProcessor(1));
			performMedianFiltering();
			findKeyPoints(rawImgStack);
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

	private void performMedianFiltering(){
		// Uses the 'Fast Filters' plugin
		int kernelSize = (int)(Math.round(10*sigma));
		IJ.run(rawImgPlus, "Fast Filters", "link filter=mean x=" + kernelSize + " y=" + kernelSize + " preprocessing=none subtract offset=32768 stack");
		if (showIntermediateImages){
			rawImgPlus.show();
		}
	}

	private void findKeyPoints(ImageStack imageStack){
		// Find the min and max values and indices


		maxVals = (ShortProcessor) imageStack.getProcessor(1).duplicate();
		short[] maxValPixs = (short[]) maxVals.getPixels();
		minVals = (ShortProcessor) imageStack.getProcessor(1).duplicate();
		short[] minValPixs = (short[]) minVals.getPixels();
		maxIdx = new FloatProcessor(imWidth, imHeight);
		float[] maxIdxPixs = (float[]) maxIdx.getPixels();
		minIdx = new FloatProcessor(imWidth, imHeight);
		float[] minIdxPixs = (float[]) minIdx.getPixels();
		for (int idz = 1; idz<zSize; idz++){
			ImageProcessor thisSlice = imageStack.getProcessor(idz+1);
			short[] thisSlicePixs = (short[]) thisSlice.getPixels();
			for (int idx = 0; idx<thisSlicePixs.length; idx++){
				if ((thisSlicePixs[idx]&0xffff) > (maxValPixs[idx]&0xffff)){
					maxValPixs[idx] = thisSlicePixs[idx];
					maxIdxPixs[idx] = idz;
				} else if ((thisSlicePixs[idx]&0xffff) < (minValPixs[idx]&0xffff)){
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
			nirPixs[idx] = ((float)(maxValPixs[idx]&0xffff) - (float)(minValPixs[idx]&0xffff));
			
			// IJ.showProgress(idx, maxValPixs.length);
		}
//		ImagePlus thingy1 = new ImagePlus("Normalized Intensity Range", maxVals);
//		thingy1.show();
//		ImagePlus thingy2 = new ImagePlus("Normalized Intensity Range", minVals);
//		thingy2.show();
		ImagePlus nirPlus = new ImagePlus("Normalized Intensity Range", nirs);

		// Perform Gaussian smoothing
		if (showIntermediateImages){
			ImagePlus preBlur = nirPlus.duplicate();
			preBlur.setTitle("Normalized Intensity Range");
			preBlur.show();
		}
		IJ.run(nirPlus,"Gaussian Blur...", "sigma=" + sigma);
		if (showIntermediateImages){
			ImagePlus postBlur = nirPlus.duplicate();
			postBlur.setTitle("NIR after blurring by sigma");
			postBlur.show();
		}
		// Perform thresholding and get keypoints
		IJ.setThreshold(nirPlus, (int) particleThreshold, 900000);
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
		gd.addNumericField("Sigma: decrease for small particles", 1.5, 1);
		gd.addNumericField("Particle threshold: decrease for dim particles", 850, 0);
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
