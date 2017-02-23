function [imagesHere, sitesXY] = findSites(pList, bandWidth)
% FINDSITES find particle sites. 
% sites is a list of all unique particle locations found in an image. 
% Every particle has a site, and particles can share the same 
% site, but not at the same time. 
% Bandwidth is how far a particle has to be from 
% 	an existing site to be considered a new site.
bwsq = bandWidth^2;
averageLocs = cellfun(@(x) sum(x,1)/size(x,1), pList(:,2), 'UniformOutput',false);

% sites = {1}; % 
imagesHere = pList(1,1);
sitesXY = pList(1,2); % lists of site locations in each image
siteLocs= averageLocs{1}; % Site locations when first found

progressbar('Identifying sites')
for n = 2:length(pList)
	% Get all the sites which are available in all of my images
	myIms = pList(n,1);
	myImsC = repmat(myIms, size(imagesHere));
	overLaps = cellfun(@ismember, myImsC, imagesHere, 'UniformOutput', false);
	openSites = find(~cellfun(@nnz, overLaps));
	if isempty(openSites)
		% make a new site
		% sites = [sites {n}];
		imagesHere = [imagesHere myIms];
		sitesXY = [sitesXY; pList{n,2}];
		siteLocs = [siteLocs; averageLocs{n}];
	else
		% Find the closest site
		openLocs = siteLocs(openSites,:);
		myCoords = averageLocs{n};
		k = dsearchn(openLocs, myCoords);
		d = sum((openLocs(k,:)-myCoords).^2); % square of distance to closest site
		if d<bwsq
			% Append to site openSites(k)
			imagesHere(openSites(k)) = {[imagesHere{openSites(k)} myIms{:}]};
			sitesXY(openSites(k)) = {[sitesXY{openSites(k)}; pList{n,2}]};
			% disp(['appended to ' num2str(openSites(k)) '!']);
		else
			% make a new site
			% sites = [sites {n}];
			imagesHere = [imagesHere myIms];
			sitesXY = [sitesXY; pList{n,2}];
			siteLocs = [siteLocs; averageLocs{n}];
		end
	end
	progressbar(n/length(pList));
end