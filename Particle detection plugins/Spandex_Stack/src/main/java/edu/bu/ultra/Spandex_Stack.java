package edu.bu.ultra;


import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import edu.emory.mathcs.restoretools.Enums;
import edu.emory.mathcs.restoretools.iterative.IterativeEnums;
import edu.emory.mathcs.restoretools.iterative.mrnsd.MRNSDDoubleIterativeDeconvolver2D;
import edu.emory.mathcs.restoretools.iterative.mrnsd.MRNSDOptions;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Undo;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.measure.ResultsTable;

import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.plugin.ImageCalculator;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.filter.RankFilters;
import inra.ijpb.watershed.*;


public class Spandex_Stack implements PlugIn {
	protected ImagePlus image;
	// image property members
	private int radiusThreshold;
	private int contrastThreshold;
	private boolean showIntermediateImages;
	private int imWidth, imHeight, zSize;
	private int kernelSize;

	private ImagePlus rawImgPlus, nirImagePlus, psf, displayImage;

	private FloatProcessor maxVals;
	private FloatProcessor minVals;
	private FloatProcessor maxIdx;
	private FloatProcessor minIdx;
	private FloatProcessor nirs, pps;

	private double[] xPos;
	private double[] yPos;
	private double[] area, circularity, perimeter;
	private List<Double> xPosFiltered;
	private List<Double> yPosFiltered;
	private List<Double> particleNir;
	private List<Double> particlePps;
	private List<Double> particleAreas, circularities, perimeters;
	private boolean foundParticle=true;
	private ImagePlus[][] psfArr;
	private String psfPath;
	private ImagePlus nirPlusOrig;

	public void run(String arg) {
		if (showDialog()) {
			rawImgPlus = IJ.getImage();
			imWidth = rawImgPlus.getWidth();
			imHeight = rawImgPlus.getHeight();
			zSize = rawImgPlus.getNSlices();

			nirImagePlus = performPreProcessing();

			displayImage = findKeyPoints(nirImagePlus.getStack());
			filterKeyPoints();
			displayResults();
		}
	}

	private ImagePlus performPreProcessing(){
		// Perform filtering and smoothing on the stack to reduce shot noise and illumination gradients.
		// We are basically making the image into NI by subtracting and then dividing by E_ref
		
		// medianImage is essentially our estimate for E_ref
		ImagePlus medianImage = rawImgPlus.duplicate();
		medianImage.setTitle("Background Image");
		// Uses the 'Fast Filters' plugin

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
		IJ.run(niImg, "Gaussian Blur 3D...", "x=" + (radiusThreshold/10.0) + " y=" + (radiusThreshold/10.0) + " z=1");
		if (showIntermediateImages){
			medianImage.show();
			diffImage.show();
			niImg.show();
		}
		return niImg;
	}

	private ImagePlus findKeyPoints(ImageStack imageStack){
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
		float[] nirPixs = (float[]) nirs.getPixels();
		pps = new FloatProcessor(imWidth, imHeight);
		float[] ppsPixs = (float[]) pps.getPixels();
		for (int idx = 0; idx<maxValPixs.length; idx++){
			nirPixs[idx] = ((float)(maxValPixs[idx]) - (float)(minValPixs[idx]));
			ppsPixs[idx] = (float)(maxIdxPixs[idx] - minIdxPixs[idx]);
		}
		ImagePlus nirPlus = new ImagePlus("Normalized Intensity Range", nirs);
		nirPlusOrig = nirPlus.duplicate();
		if (showIntermediateImages){
			ImagePlus nirShow = nirPlus.duplicate();
			nirShow.show();
		}

		// Perform deconvolution
		IJ.run(nirPlus, "Gaussian Blur...", "radius=" + radiusThreshold/10.0);

		// apply brightness threshold threshold
		ImagePlus nirDisp = nirPlus.duplicate(); 
		IJ.setThreshold(nirPlus, contrastThreshold/100.0, 1);
		IJ.run(nirPlus,"Make Binary","");
		if (showIntermediateImages){
			ImagePlus binaryImp= nirPlus.duplicate();
			binaryImp.show();
		}
		
		//detect particles in threshold results
		int opts =  ParticleAnalyzer.SHOW_RESULTS | ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES | ParticleAnalyzer.CLEAR_WORKSHEET;
		int measurements = ParticleAnalyzer.AREA | ParticleAnalyzer.CENTROID | ParticleAnalyzer.SHAPE_DESCRIPTORS | ParticleAnalyzer.MEAN | ParticleAnalyzer.PERIMETER;
		ParticleAnalyzer analyzer = new ParticleAnalyzer(opts, measurements, ResultsTable.getResultsTable(), 0,10000,0.4,1.0);
		analyzer.analyze(nirPlus);
	
		ResultsTable resultsTable = ResultsTable.getResultsTable();
		

		int xCol = resultsTable.getColumnIndex("X");
		if(xCol==ResultsTable.COLUMN_NOT_FOUND){
			foundParticle=false;
			return nirDisp;
		}

		int yCol =  resultsTable.getColumnIndex("Y");

		xPos = resultsTable.getColumnAsDoubles(xCol);
		yPos = resultsTable.getColumnAsDoubles(yCol);
		area = resultsTable.getColumnAsDoubles(resultsTable.getColumnIndex("Area"));
		circularity = resultsTable.getColumnAsDoubles(resultsTable.getColumnIndex("Circ."));
		perimeter = resultsTable.getColumnAsDoubles(resultsTable.getColumnIndex("Perim."));
		
		if (!showIntermediateImages){
			IJ.selectWindow("Results");
			IJ.run("Close");
		}
		return nirDisp;
	}

	private void filterKeyPoints(){
		// TODO: filter particles based on blob properties (shape, size, brightness etc)

		xPosFiltered = new ArrayList<Double>();
		yPosFiltered = new ArrayList<Double>();
		particleNir = new ArrayList<Double>();
		particlePps = new ArrayList<Double>();
		particleAreas = new ArrayList<Double>();
		circularities = new ArrayList<Double>();
		perimeters = new ArrayList<Double>();
		
		if (foundParticle){
			for (int n = 0; n<xPos.length; n++){
				xPosFiltered.add(xPos[n]);
				yPosFiltered.add(yPos[n]);
				
				double myNir = nirPlusOrig.getProcessor().getInterpolatedPixel(xPos[n], yPos[n]);
				particleNir.add(myNir);
				
				double myPps = pps.getInterpolatedPixel(xPos[n], yPos[n]);
				particlePps.add(myPps);
				
				particleAreas.add(area[n]);
				circularities.add(circularity[n]);
				perimeters.add(perimeter[n]);
			}
		}
	}


	private void displayResults(){
		System.out.println("Displaying results!");
		int nParticles = xPosFiltered.size();
		// Create an overlay to show particles
		Overlay particleOverlay = new Overlay();
		for (int n = 0; n<nParticles; n++){
			Roi thisParticle = new OvalRoi(xPosFiltered.get(n)-8, yPosFiltered.get(n)-8, 16, 16);
			thisParticle.setStrokeColor(Color.red);
			particleOverlay.add(thisParticle);
		}
		displayImage.show();
		displayImage.setOverlay(particleOverlay);
		IJ.run(displayImage,"Enhance Contrast", "saturated=0.4");
	
		// create a resultsTable and put it in the resultsWindow
		ResultsTable resultsTable = new ResultsTable();
		for (int n = 0; n<nParticles; n++){
			resultsTable.setValue("x", n, xPosFiltered.get(n));
			resultsTable.setValue("y", n, yPosFiltered.get(n));
			resultsTable.setValue("NIR", n, particleNir.get(n));
			resultsTable.setValue("PPS", n, particlePps.get(n));
			resultsTable.setValue("Area", n, particleAreas.get(n));
			resultsTable.setValue("Perimeter", n, perimeters.get(n));
			resultsTable.setValue("Circularity", n, circularities.get(n));
		}
		resultsTable.show("Particle Results");
	}

	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("WELCOME TO SPANDEX");

		gd.addNumericField("Size threshold: decrease for small particles", 15, 0);
		gd.addNumericField("Brightness threshold: decrease for dim particles", 8, 0);
		gd.addCheckbox("Show intermediate images", false);
		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get user values
		radiusThreshold = (int) gd.getNextNumber();
		contrastThreshold = (int) gd.getNextNumber();
		showIntermediateImages = gd.getNextBoolean();

		kernelSize = (int)(Math.round(2*radiusThreshold));
		return true;
	}

	public void showAbout() {
		IJ.showMessage("SPANDEX: the Single Particle Analysis and Detection EXperience",
			" developed at Boston University for Single Particle Interferometric Reflectance Imaging Sensing (SP-IRIS)"
		);
	}
}
