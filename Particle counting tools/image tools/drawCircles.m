function labeledIm = drawCircles(I, XY, r, color)
% DRAWCIRCLES draw circles in images
% 
% labeledIm = DRAWCIRCLES(I, XY, r, color)
% 
% I must be an RGB image matrix: It has type uint8 or 
% 	uint16, and has dimensions (r,c,3).
% XY are the (x,y) coordinates of the particles (reversed
% 	of (r,c) image indexing convention)
% r is the desired radius of the circles (i.e., r=6).
% color is the desired line color, i.e., 'red', 'green', 
% 	'blue', or an 1x3 rgb array with values between 0 and 1 
% 	(i.e., [1 0 0]), or a cell array of [1x3] rgb, one for each circle.

rArray = r*ones(size(XY,1),1);
labeledIm= insertShape(I, 'circle', [XY rArray], 'color', color);