@echo off
set /p input="Enter the path to the input file: "
set /p output="Enter the path to the output file: "
set /p mappings="Enter the version of Yarn mappings: "

set decompileFlag=false
set /p decompile="Do you want to decompile? (yes/no): "
if /i "%decompile%"=="yes" set decompileFlag=true

java -jar fabricmod-remapper-1.0.0.jar --input %input% --output %output% --mappings %mappings% --decompile %decompileFlag%
pause
