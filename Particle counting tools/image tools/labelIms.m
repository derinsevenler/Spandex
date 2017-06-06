function labeledIms = labelIms(images, particleList)
% LABELIMS label particles in images with circles
labelRadius = 12;

N = size(images,3);
imParticles = cell(N,2);
for n = 1:N
	nC = cell(length(particleList),1);
	nC(:) = {n};
	isHere = cellfun(@ismember, nC, particleList(:,1));
	myList = particleList(isHere,:);
	nC2 = cell(size(myList,1),1);
	nC2(:) = {n};
	myRC = cellfun(@(x,y,n) x(y==n,:), myList(:,2), myList(:,1), nC2, 'UniformOutput', false);
	myRC = cell2mat(myRC);
	myRC = fliplr(myRC);
	myColors = myList(:,3);
	myColors = cell2mat(myColors);
	imParticles(n,:) = {myRC, myColors};
end
% figure; hist(images(:),40);
% images = imrescale(images, min(images(:)), max(images(:)),1);
imCell = squeeze(num2cell(images, [1 2]));
rC = cell(N,1); rC(:) = {labelRadius};
labeledIms = cellfun(@drawCircles, imCell, imParticles(:,1), rC, imParticles(:,2), 'UniformOutput', false);


% % Rescale to 8 bit
% [zeroC, oneC, bitC] = deal(cell(size(labeledIms)));
% zeroC(:) = {0};
% oneC(:) = {1};
% bitC(:) = {2^8-1};
% labeledIms = cellfun(@imrescale, labeledIms, zeroC, oneC, bitC,'UniformOutput', false);
% labeledIms = cellfun(@uint8, labeledIms, 'UniformOutput', false);
