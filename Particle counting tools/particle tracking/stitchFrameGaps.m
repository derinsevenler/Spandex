function particleList = stitchFrameGaps(particleList, maxGapFrames, maxXYDist)

idx = 1;
% just for visualisation histogram
bestDistList = [];
while idx<size(particleList,1)
	idx;
	myStartFrame = particleList{idx,1}(1);
	myStartXY = particleList{idx,2}(1,:);

	% Find all particles whose last frame is within maxGapFrames before my first frame
	possibleMatches = find(cellfun( @(x)(x(end)<myStartFrame & x(end)>=(myStartFrame - maxGapFrames)) , particleList(:,1) ));

	% Find which of these possible matches is closest (Euclidian distance)
	candidateDists = cellfun(@(x)( sqrt((x(end,1)-myStartXY(1)).^2 + (x(end,2)-myStartXY(2)).^2) ), particleList(possibleMatches,2));
	
	[bestDist, bestIdx] = min(candidateDists);
	
	bestDistList = [bestDistList; bestDist];
	
	% See if any of them are close enough
	% disp(['Best distance is ' num2str(bestDist)]);
	if bestDist < maxXYDist
		% disp(['Match found with a distance of ' num2str(bestDist)]);

		% Add my info to the previous particle and remove myself from the list
		myFrames = [particleList{possibleMatches(bestIdx),1} particleList{idx,1}];
		particleList{possibleMatches(bestIdx),1} = myFrames;
		myXYInfo = [particleList{possibleMatches(bestIdx),2}; particleList{idx,2}];
		particleList{possibleMatches(bestIdx),2} = myXYInfo;
		particleList(idx,:) = [];
		
		% We don't need to re-check any earlier particles, because they all landed sooner than me anyway,
		% but we mustn't increase the index so we don't skip the next particle in the list.
	else
		idx = idx+1;
	end
	progressbar(idx/size(particleList,1));
end

figure; histogram(bestDistList,0:.5:100);