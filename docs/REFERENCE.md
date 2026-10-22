# WRB reference and maintenance

Source: IUSS Working Group WRB. 2022. *World Reference Base for Soil Resources.
International soil classification system for naming soils and creating legends for soil maps*,
4th edition, corrected 18 December 2022 and 24 September 2024. IUSS, Vienna.

[Official PDF](https://wrb.isric.org/files/WRB_fourth_edition_2022-12-18_errata_correction_2024-09-24.pdf)
is linked by the [FAO soils portal](https://www.fao.org/soils-portal/data-hub/soil-classification/world-reference-base/en/).
The FAO `w8594e` HTML publication is the 1998 edition and is not used by this component.

The source PDF SHA-256 used for extraction is
`417a023329dca5d91e3e4ddc29ad8916a27d70da4d7ecc0b63c1259ab34a6e99`.
Printed and PDF page numbers coincide for the relevant pages.

The bundled TSV files contain factual vocabulary and rule metadata, without diagnostic prose:

- `groups.tsv`: Chapter 4, pp. 95-126. Each row contains the RSG code, singular name,
  ranked principal slots and supplementary slots. Semicolons separate slots; slashes retain
  alternative qualifier groups. The two supplementary columns on some pages are concatenated.
- `qualifiers.tsv`: Chapters 5 and 6, pp. 127-153. Each row contains the Chapter 6 code,
  name, replacement parent determined by indentation, depth rule (1-5), any explicitly limited
  depth-prefix set, explicit RSG-only restrictions, and a layer/diagnostic flag for burial specifiers.
- Naming, sorting, placement, depth-prefix construction, buried-soil syntax and codes follow
  Chapters 2.1-2.4 and 6, especially pp. 24-30 and 154. This is soil naming syntax; the separate
  map-legend scale notation on p. 155 is not interpreted as full soil-name syntax.

Extraction is reproducible with Python and `pdfplumber`:

```powershell
python scripts/extract_vocabulary.py path/to/official.pdf
```

Review the source checksum, assertions and changed TSVs before accepting any regeneration.
Run the Maven tests afterwards. Runtime resolution never downloads or extracts the PDF.
The official document credits IUSS Working Group WRB; its reproduction/dissemination notice permits
use with acknowledgement. This project preserves source attribution and does not reproduce the manual.

## Source inconsistencies

- Chapter 5 spells the Pelocrustic code `p`; Chapter 6 gives `pq`. The component uses `pq`.
- The alphabetical cross-reference for Hypergypsic prints `jy`; its definition and Chapter 6
  give `jg`. The component uses `jg`; `jy` remains Hyperhydragric.
- The p. 154 Ferralsol example places Bathypetroplinthic in the listed supplementary section.
  Petroplinthic is absent from the current Ferralsol table on p. 110. Applying the Chapter 4
  table and the non-listed-qualifier rule yields
  `FR-ha-loe-dy.ed.hu.po-ppd`, labelled
  `Haplic Ferralsol (Pantoloamic, Dystric, Endic, Humic, Posic, Bathypetroplinthic)`.
  The old printed compact code is rejected for incorrect section placement; its full name
  can be supplied and is normalized to the table-consistent representation.

## Validation boundary

The parser checks the known vocabulary, grammatical structure, qualifier placement/ranking,
constructed depth prefixes, explicit RSG-only restrictions, slash alternatives, duplicate and
selected redundant information, Haplic exclusivity, and the predominant-qualifier requirement
for rule-3 Dystric/Eutric variants. Qualifiers absent from an RSG list remain legal in the final
section, as explicitly required for polygenetic soils.

This does not implement the diagnostic key or solve all interactions among Chapter 5 definitions.
Whether a horizon meets a diagnostic threshold, whether a modifier's thickness/depth requirements
are satisfied, whether two specified layers can coexist, and whether every applicable qualifier
has been supplied require the soil profile. Supra/Thapto applicability additionally requires the
specified limiting or buried layer; the vocabulary flag is only an eligibility screen.
Names alone cannot establish those facts. Diagnostic certification and inference across all
defined qualifier families are outside this naming authority.

Destroyed-horizon additions to Relocatic have no official compact codes. Their canonical IDs
therefore retain a normalized full name. The implemented formative stems are listed in
`WrbVocabulary`; unrecognized stems are rejected rather than silently discarded.

The provider conservatively proves inclusion using exact qualifiers and optional constructed
depth variants under rules 1 and 2. It does not equate Proto-, Bathy-, Supra-, Thapto-, rule-3/4/5
variants or defined subqualifiers with their parent qualifier merely because their names resemble it.
Absence of proof returns UNKNOWN. Different RSGs from this same edition return INCOMPATIBLE.

All changes to validation/canonicalization semantics require a new cache/snapshot format revision
in `WRBAuthority`, alongside vocabulary/source provenance updates. A new WRB edition must be
explicitly configured and must not silently reinterpret persisted 2022/2024 codes.
