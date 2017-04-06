import java.awt.Color; 
import java.util.ArrayList;
import java.util.List;
import java.lang.Integer;


import ij.IJ;
import ij.ImageJ; 
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.io.Opener;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public class Image_Data implements PlugIn 
{
	protected ImagePlus image;

	private int[] characteristics;
	//private String[] strI;
	private String wid= "width: ";
	private String hei = "height: " ;
	private String nChan = "nChannels: " ;
	private String zSli = "zSlices: " ;
	private String nFra = "nFrames: " ;
	private String newLine = "\n";

	@Override
	public void run(String arg)
	{
		image = IJ.getImage();
		characteristics = image.getDimensions();
		// for(int i = 0; i < characteristics.length; i++)
		// {
		// 	strI = new String [characteristics.length];
		// 	strI[i] = Integer.toString(characteristics[i]);
		// }
		//IJ.log(Integer.toString(characteristics[0]));
		IJ.log(wid+Integer.toString(characteristics[0])+newLine+hei+Integer.toString(characteristics[1])+newLine+nChan+Integer.toString(characteristics[2])+newLine+zSli+Integer.toString(characteristics[3])+newLine+nFra+Integer.toString(characteristics[4]));
	}
}
