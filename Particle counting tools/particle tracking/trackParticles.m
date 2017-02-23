function particleList = trackParticles(rc, matches)
% TRACKPARTICLES track matched particles.
% 
% particleList = trackParticles(rc, matches)
% particleRC is a cell array of [nx2] (r,c) coordinates for 
% 	particles in each image.
% matches is a cell array of [(n-1)x2] match arrays. 
% 
% particleList is a cell array of all the particles detected in 
% 	all the images. For particle n, particleList(n,1) is an array 
% 	of which images that particle was in (i.e., [1 2 3]). particleList(n,2)
% 	is an array of (r,c) particle coordinates in those images. 

% Initialize with particles in the first images
pList = ones(size(rc{1},1),1); % All 
particleList = [num2cell(pList) num2cell(rc{1},2)];

% The look-up table is like an updated list of matches
lut = repmat([1:size(rc{1},1)]',1,2); % dummy initialization lut

% matches gotta be sorted by the first column (previous indices)
oneC = cell(size(matches));
oneC(:) = {1};
matches = cellfun(@sortrows, matches,oneC,'UniformOutput', false);

for n = 2:length(rc)
	 % crazy indexing magic to update lut
	[~, lutIdx, ~] = intersect(lut(:,1), matches{n-1}(:,1),'stable');
	lut = [matches{n-1}(:,2) lut(lutIdx,2)];
    lastName = size(particleList,1); % The highest particle name
	unmatchedNames = lastName + (1:(size(rc{n},1)-size(matches{n-1},1))); % names to give to the unmatched particles
    
    if ~isempty(unmatchedNames) % we need to make particleList longer. If not, every particle is accounted for
        unmatched = setdiff(1:size(rc{n},1), lut(:,1)');
        lut = [lut; [unmatched', unmatchedNames']];
        lut = sortrows(lut, 1); % gotta sort it
        particleList = [particleList; cell(length(unmatchedNames),2)];
    end
    for x = 1:size(lut,1)
		particleList(lut(x,2),:) = {[particleList{lut(x,2),1} n], [particleList{lut(x,2),2}; rc{n}(lut(x,1),:)]};
    end
    size(particleList);
end