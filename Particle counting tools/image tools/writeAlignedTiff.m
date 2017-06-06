function writeAlignedTiff(images, deltaRCT, dirName)
% WRITEALIGNEDTIFF write a multipage tiff of images.
% 
% WRITEALIGNEDTIFF(images, deltaRCT, tifName)
% images is a cell array of (r,c,3) color images). It should have 
% 	the data type of the desired image bit depth - for example, 
% 	uint8 or uint16.
% deltaRCT is an n x 3 array, where n is size(images,3) and the 
% 	n-th column has the displacements (r, c, theta) of the (n+1)th 
% 	image. The rotation is done first, then displacement.
% tifName can point to a directory, relative to the current 
% 	working directory or in absolute terms.

mkdir(pwd, dirName);

% Write the rest of the images
for n = 1:size(images,1)
	im = images{n};
    imr = imrotate(im,deltaRCT(n,3),'crop');
	imr(imr == 0) = median(imr(:));
	imrAligned = imtranslate(im, [deltaRCT(n,1:2) 0]);
	imwrite(imrAligned, [pwd filesep dirName filesep 'image' num2str(n) '.tif'],'TIFF');
end