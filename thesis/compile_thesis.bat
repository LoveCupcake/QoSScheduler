@echo off
REM ============================================================
REM Thesis Compilation Script for Windows
REM Compiles the thesis using XeLaTeX
REM ============================================================

echo ============================================================
echo COMPILING THESIS - Dynamic QoS Scheduler
echo ============================================================
echo.

cd /d "%~dp0"

echo [1/4] First XeLaTeX pass...
xelatex -interaction=nonstopmode main.tex
if errorlevel 1 (
    echo ERROR: First XeLaTeX pass failed!
    pause
    exit /b 1
)

echo.
echo [2/4] Running BibTeX...
bibtex main
if errorlevel 1 (
    echo WARNING: BibTeX encountered errors, continuing...
)

echo.
echo [3/4] Second XeLaTeX pass...
xelatex -interaction=nonstopmode main.tex
if errorlevel 1 (
    echo ERROR: Second XeLaTeX pass failed!
    pause
    exit /b 1
)

echo.
echo [4/4] Final XeLaTeX pass...
xelatex -interaction=nonstopmode main.tex
if errorlevel 1 (
    echo ERROR: Final XeLaTeX pass failed!
    pause
    exit /b 1
)

echo.
echo ============================================================
echo COMPILATION SUCCESSFUL!
echo ============================================================
echo.
echo Output: main.pdf
echo.

REM Clean up auxiliary files
echo Cleaning up auxiliary files...
del /Q *.aux *.log *.out *.toc *.lof *.lot *.bbl *.blg *.synctex.gz 2>nul

echo.
echo Opening PDF...
start main.pdf

pause
