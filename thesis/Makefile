# Makefile for LaTeX thesis compilation

# Main document
MAIN = main
CHAPTERS = chapters/*.tex
BIBFILE = references.bib

# LaTeX compiler
LATEX = pdflatex
BIBTEX = biber
LATEXMK = latexmk

# Compilation flags
LATEXFLAGS = -interaction=nonstopmode -halt-on-error

.PHONY: all clean distclean view

# Default target
all: $(MAIN).pdf

# Compile thesis using latexmk (recommended)
$(MAIN).pdf: $(MAIN).tex $(CHAPTERS) $(BIBFILE)
	$(LATEXMK) -pdf -pdflatex="$(LATEX) $(LATEXFLAGS)" $(MAIN).tex

# Manual compilation (alternative)
manual: $(MAIN).tex $(CHAPTERS) $(BIBFILE)
	$(LATEX) $(LATEXFLAGS) $(MAIN).tex
	$(BIBTEX) $(MAIN)
	$(LATEX) $(LATEXFLAGS) $(MAIN).tex
	$(LATEX) $(LATEXFLAGS) $(MAIN).tex

# Clean auxiliary files
clean:
	$(LATEXMK) -c
	rm -f *.aux *.log *.out *.toc *.lof *.lot *.bbl *.blg *.bcf *.run.xml *.synctex.gz *.fdb_latexmk *.fls *.loa

# Clean all generated files including PDF
distclean: clean
	$(LATEXMK) -C
	rm -f $(MAIN).pdf

# View PDF (Linux/macOS)
view: $(MAIN).pdf
	@if command -v xdg-open > /dev/null; then \
		xdg-open $(MAIN).pdf; \
	elif command -v open > /dev/null; then \
		open $(MAIN).pdf; \
	else \
		echo "No PDF viewer found"; \
	fi

# Continuous compilation (watches for changes)
watch:
	$(LATEXMK) -pdf -pvc -pdflatex="$(LATEX) $(LATEXFLAGS)" $(MAIN).tex

# Word count (approximate)
wordcount:
	@texcount -inc -incbib -sum $(MAIN).tex

# Help
help:
	@echo "Available targets:"
	@echo "  all        - Compile thesis (default)"
	@echo "  manual     - Manual compilation (pdflatex + biber)"
	@echo "  clean      - Remove auxiliary files"
	@echo "  distclean  - Remove all generated files"
	@echo "  view       - Open PDF in viewer"
	@echo "  watch      - Continuous compilation"
	@echo "  wordcount  - Approximate word count"
	@echo "  help       - Show this help message"
