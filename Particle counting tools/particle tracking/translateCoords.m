function rcOut = translateCoords(particleRC, deltaRCT, imDim)
% TRANSLATECOORDS Perform rotation and translation to a set of 
% 	particle coordinates.
% 
% rcOut = translateCoords(particleRC, deltaRCT, imDim) is an n x 2 
% 	array of the new translated particle coordinates.
% 
% particleRC is an n x 2 array, where n is the number of particles. 
% 	particleRC(k,:) is the (r,c) coordinates of particle k.
% deltaRCT is the translation vector (deltaR, deltaC, deltaTheta). 
% 	Rotation is performed first, then translation.
% imDim is the image size (necessary for rotation).

% rotate the points
n = size(particleRC,1); % number of particles
pRCC = num2cell(particleRC,2);
thetaC = cell(n,1);
dimC = cell(n,1);
thetaC(:) = {deltaRCT(3)};
dimC(:) = {imDim};
rotatedPts = cellfun(@rotateCtrlPt, pRCC, thetaC, dimC,'UniformOutput', false);
rotatedPts = cell2mat(rotatedPts);
% translate the points
if size(rotatedPts,1) == 0
    rcOut = [1 1];
    rcOut(1,:) = [];
else
    rcOut = rotatedPts + repmat(deltaRCT(:,1:2), n,1);
end
end