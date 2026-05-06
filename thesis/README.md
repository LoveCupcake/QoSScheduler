# Dynamic QoS Scheduler for Mobile Hotspots - Thesis

LaTeX source for the master's thesis on Dynamic QoS Scheduler for Mobile Hotspots.

## Structure

```
thesis/
├── main.tex                 # Main document
├── references.bib           # Bibliography
├── chapters/
│   ├── 00-frontmatter.tex  # Title, abstract, TOC
│   ├── 01-introduction.tex # Introduction
│   ├── 02-background.tex   # Background and theory
│   ├── 03-related-work.tex # Literature review
│   ├── 04-methodology.tex  # Research methodology
│   ├── 05-system-design.tex # System architecture
│   ├── 06-implementation.tex # Implementation details
│   ├── 07-validation.tex   # Experimental setup
│   ├── 08-results.tex      # Experimental results
│   ├── 09-discussion.tex   # Discussion and analysis
│   ├── 10-conclusion.tex   # Conclusion and future work
│   ├── appendix-a.tex      # SRS
│   ├── appendix-b.tex      # UML diagrams
│   └── appendix-c.tex      # Source code excerpts
└── figures/                 # Figures and diagrams
```

## Compilation

### Prerequisites

- TeX Live 2023+ or MiKTeX
- biber (for bibliography)
- pdflatex

### Compile

```bash
# Full compilation
pdflatex main.tex
biber main
pdflatex main.tex
pdflatex main.tex

# Or use latexmk (recommended)
latexmk -pdf main.tex

# Clean auxiliary files
latexmk -c
```

### Using Makefile

```bash
make          # Compile thesis
make clean    # Remove auxiliary files
make distclean # Remove all generated files including PDF
```

## Requirements

### LaTeX Packages

All required packages are included in standard TeX distributions:
- geometry, graphicx, amsmath, algorithm, listings
- tikz, pgfplots, booktabs, hyperref
- biblatex (with biber backend)

### Figures

Place figures in `figures/` directory:
- `usth-logo.png` - University logo for title page
- Additional figures as referenced in chapters

## Customization

### Author Information

Edit `chapters/00-frontmatter.tex`:
- Student name and ID
- Supervisor name and title
- Academic year

### Bibliography

Add references to `references.bib` in BibTeX format.

### Figures

Generate figures using:
- TikZ/PGFPlots (embedded in LaTeX)
- External tools (export as PDF/PNG)

## Output

Compilation produces `main.pdf` - the complete thesis document.

## License

Academic work - University of Science & Technology of Hanoi, 2024-2025

## Author

Phạm Hiếu Minh - Student ID: 23BI14295
