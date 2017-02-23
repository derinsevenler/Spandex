function pairs = uniqueNN(fieldPoints, queryPoints)
% Find one unique nearest neighbor of each queryPoints in the set of fieldPoints. fieldPoints must have at least 2 more 
% elements than queryPoints, and both must be m-querypoints x 2 dimensions. pairs is a m x 2 array, the first column is 
% the index of fieldPoints and the second column is the index of queryPoints.
% Derin Sevenler, February 2014. Derin@bu.edu

numX = size(fieldPoints,1);
numQP = size(queryPoints,1);
xid = 1:numX;
qpid = 1:numQP;

% Initialize some loop variables
pairs = [];
loopCount = 0;
qp = 1;
x = 1;

while ~isempty(qpid) && ~isempty(xid)
	qp = queryPoints(qpid,:);
	x = fieldPoints(xid,:);
	dt = delaunayTriangulation(x);
	[xNNid, D] = nearestNeighbor(dt,qp);
	xNNqpD = [xid(xNNid)' qpid' D];
	sortedNN = sortrows(xNNqpD,3);
	uxid = unique(xid(xNNid));

	for i = 1:length(uxid)
	    idx = find(sortedNN(:,1)==uxid(i), 1); % get the first query point that matches this field point
	    pairs = [pairs; sortedNN(idx,1), sortedNN(idx,2)]; % save the match: column1 = field points, column2 = query points
	    sortedNN(idx,:) = []; % delete the match row
	end
	loopCount = loopCount + 1;
	pairs;
	% get remaining field points and query points
	xid = setdiff(1:numX,pairs(:,1));
	qpid = setdiff(1:numQP,pairs(:,2));
end