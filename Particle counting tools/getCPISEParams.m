function [Answer, Cancelled] = getParams()
% Get parameters for lookup table generation, using 'inputsdlg'

Formats = {};
Prompt = {};
DefAns = {};
Options.Resize = 'on';
Options.Interpreter = 'tex';
Options.CancelButton = 'on';
Options.ApplyButton = 'off';
Options.Buttonnames = {'Ok', 'Quit'};

Title = 'Spot Finding Parameters';

% Number of Rows
Prompt(1,:) = {'Number of Rows:', 'number_of_rows', []};
Formats(1,1).type = 'edit';
Formats(1,1).style = 'edit';
Formats(1,1).format = 'float';
Formats(1,1).size = [25 20];
DefAns.number_of_rows = 5;

% Number of Columns
Prompt(2,:) = {'Number of Columns:', 'number_of_columns', []};
Formats(1,2).type = 'edit';
Formats(1,2).style = 'edit';
Formats(1,2).format = 'float';
Formats(1,2).size = [25 20];
DefAns.number_of_columns = 4;

% Spot pitch, microns
Prompt(3,:) = {'Spot pitch, microns:', 'spot_pitch_um', []};
Formats(2,1).type = 'edit';
Formats(2,1).style = 'edit';
Formats(2,1).format = 'float';
Formats(2,1).size = [40 20];
DefAns.spot_pitch_um = 250;

% Spot crop diameter, microns
Prompt(4,:) = {'Spot crop diameter, microns:', 'crop_diameter', []};
Formats(2,2).type = 'edit';
Formats(2,2).style = 'edit';
Formats(2,2).format = 'float';
Formats(2,2).size = [40 20];
DefAns.crop_diameter = 150;

% Objective Magnification
Prompt(5,:) = {'Objective Magnification:', 'obj_magnification', []};
Formats(3,1).type = 'edit';
Formats(3,1).style = 'edit';
Formats(3,1).format = 'float';
Formats(3,1).size = [25 20];
DefAns.obj_magnification = 40;

% Spot crop diameter, microns
Prompt(6,:) = {'Camera Pixel Pitch, microns:', 'camera_pixel_pitch_um', []};
Formats(3,2).type = 'edit';
Formats(3,2).style = 'edit';
Formats(3,2).format = 'float';
Formats(3,2).size = [40 20];
DefAns.camera_pixel_pitch_um = 3.45;

% set(0, 'DefaultUIControlFontSize', 14); % increase font size for readability
[Answer,Cancelled] = inputsdlg(Prompt,Title,Formats,DefAns,Options);

end