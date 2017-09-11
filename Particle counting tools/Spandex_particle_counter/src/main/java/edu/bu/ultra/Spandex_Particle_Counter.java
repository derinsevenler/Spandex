/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

import net.imagej.ImageJ;

import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * 
 * @author Derin Sevenler
 */
@Plugin(type = Command.class, headless = true, menuPath = "Help>Spandex Particle Counter")
public class Spandex_Particle_Counter implements Command {

	private String imagePath, xyFilePath;
	private ImagePlus originalImage;
	private ArrayList<Double> xPosUm, yPosUm;

	@Override
	public void run() {
		getScanResults(); // get image and particle XY data
		// getArrayProperties(); // TODO: get array properties using a simple dialog box
		// TODO: user clicks on spots in corners to create grid
		// TODO: Make grid of boxes
		// TODO: perform spot detection within each grid box
		// TODO: Show all spot regions and allow user adjustment
		// TODO: Count the number of particles within each spot region
		// TODO: measure analog signal from each spot region
	}

	private void getScanResults(){
		imagePath = IJ.getFilePath("Choose Image preview file:");
		originalImage = IJ.openImage(imagePath);

		xyFilePath = IJ.getFilePath("Choose particle XY text file:");
		try (BufferedReader br = new BufferedReader(new FileReader(xyFilePath))){
			String line;
			while ((line = br.readLine()) != null) {
				String[] thisXY = line.split(", ");
				xPosUm.
			}
		}

	}

	// private void getArrayProperties(){

	// }

	public static void main(final String... args) {
		// Launch ImageJ as usual.
		final ImageJ ij = net.imagej.Main.launch(args);

		// Launch the command right away.
		ij.command().run(Spandex_Particle_Counter.class, true);
	}

}
