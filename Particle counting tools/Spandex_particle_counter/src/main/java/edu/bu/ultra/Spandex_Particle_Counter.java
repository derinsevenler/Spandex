package edu.bu.ultra;

/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.measure.ResultsTable;
import ij.plugin.filter.LutApplier;
import ij.plugin.filter.ParticleAnalyzer;
import ij.plugin.frame.RoiManager;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Reconstruction;
import inra.ijpb.morphology.Strel;

/**
 * 
 * @author Derin Sevenler
 */
@Plugin(type = Command.class, headless = true, menuPath = "Help>Spandex Particle Counter")
public class Spandex_Particle_Counter implements Command {

	private String imagePath, xyFilePath;
	private ImagePlus originalImage;
	private float pixelSizeUm;
	private int arrayXSize, arrayYSize, spotRoiSizeMicrons;
	private ArrayList<Double> xPosUm, yPosUm;
	private ArrayList<Integer> spotCounts;
	private boolean canContinue, didCancel;
	private double topLeftX, topLeftY, botLeftX, botLeftY, topRightX, topRightY;
	private JFrame pointPickerFrame, gridFrame;
	private JLabel topLeftLabel, botLeftLabel, topRightLabel;
	private Overlay gridOverlay, spotOverlay;
	private Roi[] gridArr;
	private ParticleAnalyzer particleAnalyzer;
	private ResultsTable spotFindingResultsTable;
	private RoiManager regionRoiManager;
	private Polygon[] spotPolys;

	@Override
	public void run() {
		getScanResults();
		if (!getArrayProperties()){
			return;
		}
		if (!setGrid()){
			return;
		}
		if (!makeGrid()){
			return;
		}
		if (!detectSpots()){
			return;
		}
		countParticlesInside();
		displayResults();
		System.out.println("Complete!");
		// TODO: measure analog signal from each spot region
	}

	private void getScanResults(){
		imagePath = IJ.getFilePath("Choose Image preview file:");
		originalImage = IJ.openImage(imagePath);

		xyFilePath = IJ.getFilePath("Choose particle XY text file:");
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(xyFilePath));
			String line;
			xPosUm = new ArrayList<Double>();
			yPosUm = new ArrayList<Double>();
			while ((line = br.readLine()) != null) {
				String[] thisXY = line.split(",");
				xPosUm.add(Double.parseDouble(thisXY[0]));
				yPosUm.add(Double.parseDouble(thisXY[1]));
			}
		} catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
	}

	private boolean getArrayProperties(){
		// Get array properties using a simple dialog box
		
		// Show image 
		originalImage.show();
		// get array size
		GenericDialog dlg = new GenericDialog("Array Properties");
		dlg.addNumericField("How many spots across (X direction):", 8, 0);
		dlg.addNumericField("How many spots down (Y direction):", 12, 0);
		dlg.addNumericField("Size of each spot region in microns:", 200, 0);
		dlg.addNumericField("Camera pixel size in microns:", 3.45, 2);
		dlg.addNumericField("Objective Magnification:", 10, 0);
		dlg.showDialog();
		if (dlg.wasCanceled()){
			originalImage.close();
			return false;
		}

		arrayXSize = (int) dlg.getNextNumber();
		arrayYSize = (int) dlg.getNextNumber();
		spotRoiSizeMicrons = (int) dlg.getNextNumber();
		float cameraPixelSize = (float) dlg.getNextNumber();
		int mag = (int) dlg.getNextNumber();
		pixelSizeUm = cameraPixelSize/mag;
		
		return true;
	}

	private boolean setGrid(){
		// set grid positions using an interactive window
		
		// Create pointPanel, which is used to set the grid corners
		JPanel pointPanel = new JPanel();
		pointPanel.setLayout(new GridLayout(3,2,20,20));
		pointPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton setTopLeft = new JButton("Set top left");
		setTopLeft.setEnabled(true);
		setTopLeft.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				topLeftPressed();
			}
		});
		topLeftLabel = new JLabel("");
		pointPanel.add(setTopLeft);
		pointPanel.add(topLeftLabel);

		JButton setBotLeft = new JButton("Set bottom left");
		setBotLeft.setEnabled(true);
		setBotLeft.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				botLeftPressed();
			}
		});
		botLeftLabel = new JLabel("");
		pointPanel.add(setBotLeft);
		pointPanel.add(botLeftLabel);

		JButton setTopRight = new JButton("Set top right");
		setTopRight.setEnabled(true);
		setTopRight.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				topRightPressed();
			}
		});
		topRightLabel = new JLabel("");
		pointPanel.add(setTopRight);
		pointPanel.add(topRightLabel);

		// Create the buttonPanel, which has the "Cancel" and "OK" buttons
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				originalImage.close();
				closePickerDialogAndContinue();
			}
		});
		buttonPanel.add(cancelButton);
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				closePickerDialogAndContinue();
			}
		});
		buttonPanel.add(okButton);

		// Create and populate the JFrame
		pointPickerFrame = new JFrame("Set spot grid");
		pointPickerFrame.getContentPane().add(pointPanel, BorderLayout.NORTH);
		pointPickerFrame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		pointPickerFrame.pack();
		pointPickerFrame.setSize(350, 250);
		pointPickerFrame.setLocation(300,300);
		pointPickerFrame.setVisible(true);
		pointPickerFrame.setResizable(false);

		// Bring image to front and set tool
		originalImage.getWindow().toFront();
		IJ.setTool(Toolbar.POINT);
		
		// Wait for user to click either Cancel or OK button
		canContinue = false;
		didCancel = false;
		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		return !didCancel; // if user pressed cancel return false
	}

	private void closePickerDialogAndContinue(){
		canContinue = true;
		pointPickerFrame.setVisible(false);
		pointPickerFrame.dispose();
	}

	private void topLeftPressed(){
		Point thisPt = ((PointRoi) originalImage.getRoi()).getContainedPoints()[0];
		topLeftX = thisPt.getX();
		topLeftY = thisPt.getY();
		topLeftLabel.setText("" + topLeftX + ", " + topLeftY);
	}

	private void botLeftPressed(){
		Point thisPt = ((PointRoi) originalImage.getRoi()).getContainedPoints()[0];
		botLeftX = thisPt.getX();
		botLeftY = thisPt.getY();
		botLeftLabel.setText(" " + botLeftX + ", " + botLeftY);
	}

	private void topRightPressed(){
		Point thisPt = ((PointRoi) originalImage.getRoi()).getContainedPoints()[0];
		topRightX = thisPt.getX();
		topRightY = thisPt.getY();
		topRightLabel.setText(" " + topRightX + ", " + topRightY);
	}

	private boolean makeGrid(){
		// Interpolate to get spot regions

		double stepSizeDownXPix = (botLeftX - topLeftX)*1.0/(arrayYSize-1);
		double stepSizeDownYPix = (botLeftY - topLeftY)*1.0/(arrayYSize-1);

		double stepSizeAcrossXPix = (topRightX - topLeftX)*1.0/(arrayXSize-1);
		double stepSizeAcrossYPix = (topRightY - topLeftY)*1.0/(arrayXSize-1);

		double halfWidthPix = spotRoiSizeMicrons/pixelSizeUm/2.0;

		gridOverlay = new Overlay();
		gridOverlay.clear();
		gridOverlay.drawBackgrounds(true);
		gridOverlay.drawLabels(true);

		for (int idy = 0; idy<arrayYSize; idy++){
			double startX = topLeftX + (idy*stepSizeDownXPix);
			double startY = topLeftY + (idy*stepSizeDownYPix);
			
			for (int idx = 0; idx<arrayXSize; idx++){
				double thisX = startX + (idx*stepSizeAcrossXPix);
				double thisY = startY + (idx*stepSizeAcrossYPix);
				Rectangle thisRect = new Rectangle((int)Math.round(thisX-halfWidthPix), (int)Math.round(thisY-halfWidthPix), (int)Math.round(2*halfWidthPix), (int)Math.round(2*halfWidthPix));
				gridOverlay.add(new Roi(thisRect));
			}
		}
		originalImage.setOverlay(gridOverlay);

		// Let user adjust grid locations
		IJ.setTool(Toolbar.RECTANGLE);


		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(1,2,20,20));
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JButton cancelButton = new JButton("Cancel");
		cancelButton.setEnabled(true);
		cancelButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				didCancel = true;
				originalImage.close();
				closeSetDialogAndContinue();
			}
		});
		buttonPanel.add(cancelButton);
		JButton okButton = new JButton("OK");
		okButton.setEnabled(true);
		okButton.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				closeSetDialogAndContinue();
			}
		});
		buttonPanel.add(okButton);
		
		gridFrame = new JFrame("Spot grid");
		gridFrame.getContentPane().add(new JLabel("Adjust spot regions and select OK"), BorderLayout.NORTH);
		gridFrame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		gridFrame.pack();
		gridFrame.setSize(300, 200);
		gridFrame.setLocation(300,300);
		gridFrame.setVisible(true);
		gridFrame.setResizable(false);

		// Wait for user to click either Cancel or OK button
		canContinue = false;
		didCancel = false;
		while (!canContinue){
			try {
				Thread.sleep(200);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		gridArr = gridOverlay.toArray();
		return !didCancel;
	}
	
	private void closeSetDialogAndContinue(){
		canContinue = true;
		gridFrame.setVisible(false);
		gridFrame.dispose();
	}

	private boolean detectSpots(){
		gridOverlay.clear();
		originalImage.deleteRoi();
		
		
		spotPolys = new Polygon[gridArr.length];
		for(int idx = 0; idx< gridArr.length; idx++){
			regionRoiManager = new RoiManager(false);
			ParticleAnalyzer.setRoiManager(regionRoiManager);
			spotFindingResultsTable = ij.measure.ResultsTable.getResultsTable();
			
			originalImage.setRoi(gridArr[idx], false);
			ImagePlus spotRegion = originalImage.duplicate();
			spotRegion.close();
			Polygon thisPoly = detectSpot(spotRegion);
			Rectangle myRect = gridArr[idx].getBounds();
			thisPoly.translate(myRect.x, myRect.y);
			spotPolys[idx] = thisPoly;
			spotFindingResultsTable.reset();
			IJ.showProgress(idx/gridArr.length);
		}
		IJ.showProgress(1);
		
		// draw these rois on the main image
		spotOverlay = new Overlay();
		spotOverlay.clear();
		spotOverlay.drawBackgrounds(true);
		spotOverlay.drawLabels(true);
		for (int idx = 0; idx< spotPolys.length; idx++){
			spotOverlay.add(new PolygonRoi(spotPolys[idx], PolygonRoi.POLYGON));
		}
		originalImage.setRoi((Roi) null);
		originalImage.setOverlay(spotOverlay);
		
		return true;
	}

	private Polygon detectSpot(ImagePlus imp){
		// Smooth away particles with fast median filter
		int kernelSize = 12;
		IJ.run(imp, "Fast Filters", "link filter=median x=" + kernelSize + " y=" + kernelSize + " preprocessing=none");

		ImageStatistics myStats = imp.getStatistics(ij.measure.Measurements.MEAN + ij.measure.Measurements.MEDIAN + ij.measure.Measurements.STD_DEV); 
		double myMean = myStats.mean;
		double myStd = myStats.stdDev;
		imp.setDisplayRange(myMean-2*myStd, myMean+myStd);
		LutApplier lutAp = new LutApplier();
		lutAp.setup("", imp);
		lutAp.run(imp.getProcessor());

		// Use Otsu's method to binarize image
		imp.getProcessor().setAutoThreshold(AutoThresholder.Method.Otsu, false);
		IJ.run(imp, "Convert to Mask", "");

		
		// Perform morphological operations to adjust spot
		Strel disk4 = Strel.Shape.DISK.fromRadius(4);
		Strel disk6 = Strel.Shape.DISK.fromRadius(4);
		ImageProcessor closed = Morphology.closing(imp.getProcessor(), disk4);
		imp.setProcessor(closed);
 
		ImageProcessor ip2 = Reconstruction.fillHoles(imp.getProcessor());
		imp.setProcessor(ip2);

		ImageProcessor opened = Morphology.opening(imp.getProcessor(), disk6);
		imp.setProcessor(opened);
		
		particleAnalyzer = new ParticleAnalyzer((ParticleAnalyzer.SHOW_NONE), (ij.measure.Measurements.AREA + ij.measure.Measurements.CENTROID), spotFindingResultsTable, 0.0, 1e6);
		particleAnalyzer.analyze(imp);
		Roi[] rois = regionRoiManager.getRoisAsArray();
		double[] areas = spotFindingResultsTable.getColumnAsDoubles(spotFindingResultsTable.getColumnIndex("Area"));
		double biggestArea = 0;
		Polygon biggestPolygon = rois[0].getPolygon();
		for(int idx = 0; idx<rois.length; idx++){
			if(areas[idx] > biggestArea){
				biggestArea = areas[idx];
				biggestPolygon = rois[idx].getPolygon();
			}
		}
		return biggestPolygon;
	}
	
	private boolean countParticlesInside(){
		// Convert to pixels
		ArrayList<Double> xPosPix = new ArrayList<Double>();
		ArrayList<Double> yPosPix = new ArrayList<Double>();
		
		for (int particleIdx = 0; particleIdx< xPosUm.size(); particleIdx++){
			xPosPix.add(particleIdx, xPosUm.get(particleIdx)/pixelSizeUm);
			yPosPix.add(particleIdx, -1*yPosUm.get(particleIdx)/pixelSizeUm); // flip y axis!
		}
		// count particles contained within each spot polygon
		spotCounts = new ArrayList<Integer>();
		for (int spotIdx = 0; spotIdx< spotPolys.length; spotIdx++){
			int particleCounts = 0;
			for (int particleIdx = 0; particleIdx< xPosUm.size(); particleIdx++){
				if (spotPolys[spotIdx].contains(xPosPix.get(particleIdx), yPosPix.get(particleIdx))){
					particleCounts++;
				}
			}
			spotCounts.add(spotIdx, particleCounts);
		}
		return true;
	}
	
	private void displayResults(){
		ResultsTable finalTab = ij.measure.ResultsTable.getResultsTable();
		finalTab.reset();
		for (int idx = 0; idx< spotCounts.size(); idx++){
			finalTab.incrementCounter();
			finalTab.addValue("Counts", spotCounts.get(idx));
			System.out.println("" + spotCounts.get(idx));
		}
		finalTab.show("Results");
	}

	public static void main(final String... args) {
		// Launch ImageJ as usual.
		return;
//
//		// Launch the command right away.
//		ij.command().run(Spandex_Particle_Counter.class, true);
	}

}
