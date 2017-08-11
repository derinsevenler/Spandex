function B = cropPow2(A)
% cropPow2(A) crops the input matrix to the largest power of 2 along the
% two r,c axes. This is useful for performing faster fft2 or ifft2, when
% the edges do not contain useful information.
% Derin Sevenler, February 2014

cent = floor(size(A)/2);
rr = 2^(nextpow2(size(A,1))-1);
cc = 2^(nextpow2(size(A,2))-1);
r = (cent(1) -rr/2 + 1):(cent(1) + rr/2);
c = (cent(2) -cc/2 + 1):(cent(2) + cc/2);
B = A(r,c);