clear; close all;
warning('off','images:initSize:adjustingMag');

[imfile, imfolder] = uigetfile('*.*', 'Please open the preview image');
imgfile= [imfolder filesep imfile];

[XYfile, XYfolder] = uigetfile('*.*', 'Please select the XY particle position file');
XYfile= [XYfolder filesep XYfile];

[p, Cancelled] = getCPISEParams();
if Cancelled
	disp('Goodbye');
	return;
end

% -------------------------------------------------------

% Other acquisition-specific params
preview_bin = 5;

XY = csvread(XYfile);
img = imread(imgfile);

umPerPixel = p.camera_pixel_pitch_um/p.obj_magnification*preview_bin;
xyInImg = [(XY(:,1))/umPerPixel+1 , (-XY(:,2))/umPerPixel+1];


% First let the person zoom in on the top-left spot
f1 = figure; hold on;
imshow(img, median(double(img(:)))*[0.9, 1.1]);
title('Please click the center of the top-left spot');
set(f1, 'ToolBar','figure');
[startx, starty] = ginput(1);
close(f1);
% hold on; plot(xyInImg(:,1), xyInImg(:,2), '*');

selectFig = figure; hold on;
imshow(img, median(double(img(:)))*[0.9, 1.1]);
title('Please adjust spot regions');

cropHW = p.crop_diameter/umPerPixel/2;

nPos = p.number_of_rows*p.number_of_columns;

hrectangles = cell(p.number_of_columns, p.number_of_rows);
for xi = 1:p.number_of_columns
    for yi = 1:p.number_of_rows
        centroidx = startx+ (xi-1)*p.spot_pitch_um/umPerPixel;
        centroidy = starty+ (yi-1)*p.spot_pitch_um/umPerPixel;
        thisPos = [centroidx-cropHW centroidy-cropHW 2*cropHW 2*cropHW];
%         hrectangles{xi,yi} = imrect(gca, thisPos);
        hrectangles{xi,yi} = imellipse(gca, thisPos);
    end
    disp(num2str(xi/p.number_of_columns*100));
end
measureButtonH=uicontrol('Style','pushbutton','String','Measure Regions','Units','normalized','Position',[0.45 0 .1 .05],'Visible','on', 'Callback','uiresume(gcbf)');
set(selectFig, 'ToolBar','figure');
uiwait(gcf);

% Count Particles in these regions
dispFig = figure;
imshow(img, median(double(img(:)))*[0.8, 1.2]);
hold on;
for xi = 1:p.number_of_columns
	for yi = 1:p.number_of_rows 
		thisRegion = hrectangles{xi,yi}.getPosition;
% 		rectangle('Position', thisRegion, 'Curvature', [0,0], 'LineWidth',2, 'LineStyle','--', 'EdgeColor','r');
        rectangle('Position', thisRegion, 'Curvature', [1,1], 'LineWidth',2, 'LineStyle','--', 'EdgeColor','r');
        
        el_radius=[thisRegion(3)/2,thisRegion(4)/2];
        el_center=[thisRegion(1)+el_radius(1),thisRegion(2)+el_radius(2)];
        
        
        inEllipse=((xyInImg(:,1)-el_center(1)).^2)./(el_radius(1).^2)+((xyInImg(:,2)-el_center(2)).^2)./(el_radius(2).^2);
        
        % hrectangles{xi,yi}.delete;
        % 		minX = thisRegion(1); maxX = thisRegion(1)+thisRegion(3); minY = thisRegion(2); maxY = thisRegion(2)+thisRegion(4);
        % 		particlesInside = ( xyInImg(:,1) > minX & xyInImg(:,1) < maxX & xyInImg(:,2) > minY & xyInImg(:,2) < maxY );
        particlesInside=inEllipse<=1;


        plot(xyInImg(particlesInside,1), xyInImg(particlesInside,2), 'ob');
        counts(xi,yi) = sum( particlesInside );
        % 		areas(xi,yi) = thisRegion(3)*thisRegion(4)*umPerPixel^2;
        areas(xi,yi) = pi*el_radius(1)*el_radius(2)*umPerPixel^2;
	end
end
disp('Particle counts:');
disp(counts')
disp('Region Areas in um^2:');
disp(areas')
densities = counts'./areas';
disp('Particle Densities:');
disp(densities);

uiwait(gcf);
disp('Data is still in the workspace but has not been saved... Goodbye!');
