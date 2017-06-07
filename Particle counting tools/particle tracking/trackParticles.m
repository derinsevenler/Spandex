function particleList = trackParticles(rc, matches)
% TRACKPARTICLES track matched particles.
% 
% particleList = trackParticles(rc, matches)
% particleRC is a cell array of [nx2] (r,c) coordinates for 
% 	particles in each image.
% matches is a cell array of [(n-1)x2] match arrays. 
% 
% particleList is a cell array of all the particles detected in 
% 	all the images. For particle n, particleList{n,1} is an array 
% 	of the indices of the images which that particle was in (i.e., 
%   [1 2 3] - particle was in images 1, 2 and 3). particleList{n,2}
% 	is an array of the particle's coordinates (r,c) in those images.

% Initialize the particleList with all the particles in the first image
pList = ones(size(rc{1},1),1);
particleList = [num2cell(pList) num2cell(rc{1},2)];

% The look-up table is like an updated list of matches. It has the
% same length as rc{n} 
% Column 1: the index of each particle in this particular frame
% Column 2: the index (or 'name') of this particle in the particleList
lut = repmat([1:size(rc{1},1)]',1,2); % dummy initialization lut

% matches must be sorted by the first column (previous indices)
oneC = cell(size(matches));
oneC(:) = {1};
matches = cellfun(@sortrows, matches,oneC,'UniformOutput', false);

for n = 2:length(rc)
	% crazy indexing magic to update lut:
    % get the index of particles in the previous frame which have matches
	[~, lutIdx, ~] = intersect(lut(:,1), matches{n-1}(:,1),'stable');
	% Update the lut:
    % 1. All the particles at lutIdx have matches here: change the first column to correspond
    lut = [matches{n-1}(:,2) lut(lutIdx,2)];
    lut = sortrows(lut, 1);

    % 2. Put (append) any new particles 
    lastName = size(particleList,1); % The highest particle 'name'
	unmatchedNames = lastName + (1:(size(rc{n},1)-size(matches{n-1},1))); % names to give to the unmatched particles
    
    if ~isempty(unmatchedNames) % we need to make particleList longer. If not, every particle in this frame is accounted for (it matches a previous one)
        unmatched = setdiff(1:size(rc{n},1), lut(:,1)');
        lut = [lut; [unmatched', unmatchedNames']];
        lut = sortrows(lut, 1); % gotta keep it sorted!
        particleList = [particleList; cell(length(unmatchedNames),2)];
    end

    for x = 1:size(lut,1)
		particleList(lut(x,2),:) = {[particleList{lut(x,2),1} n], [particleList{lut(x,2),2}; rc{n}(lut(x,1),:)]};
    end
end