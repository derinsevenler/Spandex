function I2 = imrescale(I, minVal, maxVal,bitSize)
%IMRESCALE Rescale an image linearly between minVal and maxVal.
%   I2 = IMRESCALE(I, minVal, maxVal, bitSize)
%   Image values less than minVal are rescaled to 0 and values greater than
%   maxVal are rescaled to bitSize, with all intermediate values scaled
%   linearly between minVal and maxVal to 0 and bitSize.
% 
%   IMRESCALE does not change the data type.

I(I<minVal) = minVal;
I(I>maxVal) = maxVal;
I2 = bitSize * (I - minVal)/(maxVal-minVal);
