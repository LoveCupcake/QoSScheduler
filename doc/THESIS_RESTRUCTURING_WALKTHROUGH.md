# Thesis Restructuring — Walkthrough

## Overview

Restructured the QoS Scheduler graduation thesis from a 10-chapter layout (~41 pages) with massive duplication to a clean 7-chapter structure (~80+ pages) with zero content duplication.

## Structural Changes

### Chapter Reorganization

| Old Structure (10 chapters) | New Structure (7 chapters) |
|---|---|
| Ch1 Introduction (90 lines) | **Ch1** Introduction + Contributions (88 lines, ~6 pages) |
| Ch2 Theory (122 lines) | **Ch2** Literature Review & Theory (**263 lines**, ~14 pages) |
| Ch3 Architecture (84 lines) | **Ch3** Architecture & Design (**232 lines**, ~10 pages) |
| Ch4 Implementation (157 lines) | **Ch4** Implementation Details (**494 lines**, ~11 pages) |
| Ch5 Experimental (142 lines) | **Ch5** Testing & Evaluation = *Merged Ch5+Ch7* (**424 lines**, ~8 pages) |
| Ch6 Results (500 lines) | **Ch6** Results & Discussion = *Merged Ch6+Ch8+Ch9* (**308 lines**, ~7 pages) |
| Ch7 Validation (516 lines) | **Ch7** Conclusion = *From Ch10_new* (**145 lines**, ~6 pages) |
| Ch8 Exp Results (93 lines) | ❌ Removed (content merged into Ch6) |
| Ch9 Discussion (54 lines) | ❌ Removed (content merged into Ch6) |
| Ch10_new Conclusion (284 lines) | ❌ Removed (replaced by chapter7_new.tex) |

> [!NOTE]
> Line counts appear lower than expected because LaTeX paragraphs are single long lines. Byte count (184 KB body text) translates to ~63 body pages + ~17 pages frontmatter/appendices/references = **~80 pages total**.

## Files Modified

### Infrastructure
- [main.tex](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/main.tex) — Removed Ch8, Ch9, Ch10_new includes; replaced Ch7 with Ch7_new
- [references.bib](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/references.bib) — Added 20+ new BibTeX entries (48 total), all 32 cite keys verified

### Chapters
- [chapter1.tex](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/chapters/chapter1.tex) — Added "Summary of Contributions" (5 contributions), updated "Thesis Organization" from 6→7 chapters
- [chapter2.tex](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/chapters/chapter2.tex) — **Complete rewrite**: networking fundamentals (IPv4/IPv6/TCP/UDP/QUIC), queueing theory (Little's Law, M/M/1, Bufferbloat), AQM (RED/CoDel/FQ-CoDel comparison table), traffic shaping (Token Bucket math, Leaky Bucket, GPS→WFQ, DRR), Android stack (TUN, protect(), UID resolution, DPI), Related Work (7 categories + comparison table + research gap)
- [chapter3.tex](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/chapters/chapter3.tex) — **Complete rewrite**: 3-plane architecture, design philosophy/constraints, Data Plane pipeline, Control Plane (Token Bucket + WFQ + policing rationale), Transport Relay (routing loop, TCP proxy, UDP relay, PacketComposer), 5-tier caching, concurrency model, Telemetry Plane
- [chapter4.tex](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/chapters/chapter4.tex) — **Complete rewrite**: 12+ code listings from real source, tech stack table, VPN config, coroutine architecture, bitwise parsing, IPv6, UID resolution with negative caching, DPI classifier, TokenBucket, WFQ rebalance, TCP/UDP relay, PacketComposer, Web Admin, technical challenges (OEM fragmentation, GC mitigation, IPv6 dual-stack, race conditions)
- [chapter5.tex](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/chapters/chapter5.tex) — **Merged Ch5+Ch7**: testing pyramid, unit tests (Token Bucket, WFQ, Parser), integration tests, experimental setup table, 3 experimental scenarios, performance profiling, cross-device compatibility, security testing, coverage metrics table
- [chapter6.tex](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/chapters/chapter6.tex) — **Merged Ch6+Ch8+Ch9**: all results (throttling accuracy 99.4%, burst handling, statistical analysis, WFQ allocation, Jain's Fairness Index 0.9998, Bufferbloat mitigation 84.3%, CPU/memory/battery overhead, cross-device compatibility), discussion, comparison with related work, threats to validity
- [chapter7_new.tex](file:///c:/Users/hieum/AndroidStudioProjects/QoSScheduler/thesis/chapters/chapter7_new.tex) — **New file**: summary of contributions, answers to 3 RQs with evidence, limitations, 3-tier future work roadmap, lessons learned, broader impact, final remarks

## Verification

| Check | Status |
|---|---|
| All 32 citation keys resolve to references.bib entries | ✅ |
| Frontmatter filled (name, ID, supervisor, university) | ✅ (already done) |
| No content duplication across chapters | ✅ |
| Results appear exactly once (in Ch6 only) | ✅ |
| Thesis Organization matches actual structure | ✅ (7 chapters) |
| Total body text: ~184 KB (~63 body pages) | ✅ |
| Estimated total pages with frontmatter: **~80+** | ✅ |

## Not Done (Requires User Action)

- **LaTeX compilation**: No LaTeX compiler (XeLaTeX) installed on this system. User needs to compile locally with `xelatex main.tex` + `bibtex main` + `xelatex main.tex` × 2.
- **Figure placeholders**: 8 `example-image` placeholders remain for diagrams (architecture, pipeline, routing loop, etc.). User should replace with actual screenshots/diagrams.
- **Abstract review**: `frontmatter/abstract.tex` and `frontmatter/glossary.tex` were not modified — may need updates to match new structure.
