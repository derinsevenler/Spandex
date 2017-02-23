
function rc_out = rotateCtrlPt(rc,theta,imDim)
% ROTATECTRLPT perform a rotation transformation to a single point.
% 
% rc_out = rotateCtrlPt(rc, theta, imDim) is the (r,c) coordinates 
% 	of a point after rotation by angle 'theta' (degrees) about an 
% 	image with size 'imDim'
% 
% rc is the inital coordinates of the point in matrix (or image) 
% 	coordinates, i.e., image(r,c). 
% Theta is the rotation angle in degrees.
% imDim is the [r,c] size of the image. 

t = theta*pi/180;
A = [cos(t), -sin(t)     % Rotation matrix
    sin(t), cos(t)];
centroid = (imDim+1)/2;
x = rc' - centroid';	% coordinates wrt centroid of image
b = A*x;                % rotated coordinates
rc_out = b' + centroid;