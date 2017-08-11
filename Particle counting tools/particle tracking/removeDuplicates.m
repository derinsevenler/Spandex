function dedupedXY = removeDuplicates(xy, minDist)

% Fast nearest neighbor distance search:

% Create a triangulation
dt = delaunayTriangulation(xy);
% Measure edge lengths
allEdges = edges(dt);
edgeLengths = sqrt( ( xy(allEdges(:,1),1) - xy(allEdges(:,2),1) ).^2 + ( xy(allEdges(:,1),2) - xy(allEdges(:,2),2) ).^2 );

% Identify any edge lengths with a distance less than minDist and knock one point out
edgesWithDups = allEdges(edgeLengths<minDist,:);
duplicatePoints = edgesWithDups(:,2);
dedupedXY = xy;
dedupedXY(duplicatePoints,:) = [];

% [~,~,clustMembsCell] = MeanShiftCluster(xy,dist);
% dups = cellfun(@length, clustMembsCell);
% rCell = clustMembsCell(dups~=1);
% redundCell= cellfun(@(x) x(2:end), rCell, 'UniformOutput', false);
% redundants = cell2mat(redundCell');