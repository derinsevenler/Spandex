function matches = matchParticles(particles,clusterBandwidth)
% matchParticles match particles in a series of aligned images
% 
% matches = matchParticles(particles, clusterBandwidth)
% 
% 'particles' is a cell array of matrices. The matrix at particles{k} 
% 	has dimensions m x 2, where m is the number of particles in 
% 	image k. Each row corresponds to the (r,c) coordinates
%	of one detected particle.
% 
% clusterBandwidth is the spacing for clustering - default = 4
% 
% 'matches' is a cell array with a length equal to (length(particles)-1). 
%	matches{n} is an a x 2 array, where 'a' matches were found between 
% 	particles{n} and particles{n+1}. Each match is represented by the 
% 	indices of the coordinates in particles{n} (at matchesand particles{n+1}.
% 
% This function uses uniqueNN (unique nearest neighbor) and 
% 	MeanShiftCluster (clustering).

matches = cell(size(particles)-1);
for n = 1:(length(particles)-1)
	p1 = particles{n};
	p2 = particles{n+1};
    if isempty(p1) || isempty(p2) % There can be no matches if one of the sets is empty
        m = [1 1];
        m(1,:) = [];
        matches(n) = {m};
    else
        % find nearest neighbors. Query points in image n, field points in image n+1
        p2origL = size(p2,1);
        p2padded = [p2; -1e4+rand((size(p1,1)- size(p2,1) + 2), 2)]; % p2 must have at least 2 more points than p1

        pairs = uniqueNN(p2padded, p1);

        pairs(pairs(:,1)>p2origL,:) = []; % eliminate any pairs made to pad points

        % find neighbors which correspond to matches.

        vecs = p2(pairs(:,1),:)- p1(pairs(:,2),:);
        % plot(vecs(:,1), vecs(:,2), '*b');
        [clustCent,~,clustMembsCell] = MeanShiftCluster(vecs',clusterBandwidth); % vecs input must be mdim x npoints
        [~,matchCluster] = min(clustCent(1,:).^2 + clustCent(2,:).^2); % The match cluster is the cluster closest to (0,0) displacement vector which is the largest also

        m = pairs(clustMembsCell{matchCluster},:);
        matches(n) = {fliplr(m)};
    end
end