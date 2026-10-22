# k.LAB WRB soil classification authority

`klab.authority.wrb` resolves, validates and normalizes World Reference Base soil classification
expressions. It bundles the 32 Reference Soil Groups (RSGs), 286 qualifiers/defined subqualifiers,
their codes and the RSG qualifier lists from **WRB 2022, fourth edition, with corrections through
24 September 2024**. It works offline and has no external vocabulary service dependency.

The normative reference is the [IUSS WRB 2022/2024 PDF](https://wrb.isric.org/files/WRB_fourth_edition_2022-12-18_errata_correction_2024-09-24.pdf),
linked from the [FAO soils portal](https://www.fao.org/soils-portal/data-hub/soil-classification/world-reference-base/en/).
The older FAO `w8594e` HTML manual describes WRB 1998. It is not used here.
Source attribution, extraction procedure and precise validation limits are in [reference notes](docs/REFERENCE.md).

## Bind the authority to a worldview

The component supplies provider URN `klab.authority.wrb.authority`. A worldview chooses its own
uppercase local name, here `WRB`, and declares an identity anchor:

```kwv
identity SoilType requires authority WRB {
    urn: "klab.authority.wrb.authority",
    edition: "2022-2024",
    codelists: {
        groups: "soil.groups"
    }
};
```

Place this declaration in your soil ontology. The ontology namespace, anchor name and codelist
namespace are your choices; they do not require a particular worldview. The Reasoner retains
the `codelists` map and passes the remaining parameters to the provider.

| Parameter | Meaning |
| --- | --- |
| `urn` | Required provider reference, `klab.authority.wrb.authority` (optionally pinned with `@1.0.0-SNAPSHOT`) |
| `edition` | Optional, defaults to `2022-2024`; other editions are rejected |
| `codelists` | Optional host-owned namespace mapping; `groups` is the supplied list |
| `semanticBoundaries` | Only an empty mapping is supported; the complete RSG hierarchy is inexpensive |

Other parameters are rejected. Each configuration has an independent local name, anchor and
lifecycle. No sub-authorities are advertised. There is no unconfigured/global resolution route.

The `groups` codelist contains singular official names as aliases for generic RSG codes:
`soil.groups:Luvisol` denotes the same identity as `WRB:LV`. Its 32 seed entries reject new
proposals. Qualified expressions are resolved directly; no combinatorial codelist is generated.

## Names, codes and normalization

The first level is the unqualified RSG. The second level adds principal and supplementary
qualifiers; it is not a fixed tree of enumerated subclasses. The authority accepts English official
names, official codes, singular/plural RSG names, and mixed name/code elements, without case sensitivity.
It does not accept translated names, misspellings or codes from other WRB editions.

| Input expression | Canonical provider code |
| --- | --- |
| `Luvisol`, `Luvisols`, `lv` | `LV` |
| `Albic Luvisol` | `LV-ab` |
| `Albic Stagnic Luvisol (Cutanic)` | `LV-st.ab--ct` |
| `st ab luvisols (ct)` | `LV-st.ab--ct` |
| `Albic Endostagnic Luvisol (Anoloamic, Endoclayic, Cutanic, Differentic, Endic, Ochric)` | `LV-stn.ab-loa.cen-ct.df.ed.oh` |
| `Eutric Arenosol (Bathylixic)` | `AR-eu---lxd` |
| `Isolatic Ekranic Technosol (Supraarenic, Supracalcaric)` | `TC-ek.il-ars-cas` |

Full names put principal qualifiers before the singular RSG name, without commas, and
supplementary qualifiers after it in parentheses, separated by commas. Principal qualifiers
are sorted in reverse rank order in the display name: the highest-ranked qualifier is closest
to the RSG. Texture qualifiers come first in the parenthesized list, ordered by their depth
specifiers; other listed qualifiers follow alphabetically by the underlying qualifier, and
non-listed qualifiers come last. Input order is normalized within these sections.

Canonical compact codes follow Chapter 6:

```text
RSG-PQ1.PQ2-TQ1.TQ2-SQ1.SQ2-NQ1.NQ2
```

The four sections contain principal qualifiers in ranked order, texture qualifiers from top
to bottom, other listed supplementary qualifiers, and qualifiers not listed for that RSG.
Empty intervening sections retain their hyphens; trailing empty sections are omitted.
Wrong section placement is rejected. Specifier suffixes follow the official codes, e.g.
`stn` = Endostagnic, `loa` = Anoloamic, `cen` = Endoclayic. Invented specifier combinations
such as Epihumic are rejected.

The parser also supports buried-soil expressions separated by `over`, Novic combinations such
as `Aeoli-Siltinovic`, and recognized destroyed-horizon additions such as `Spodi-Epirelocatic`.
The latter have no official compact codes, so their canonical identity remains the normalized
full name. The separate map-legend scale/elective-qualifier syntax is not accepted as soil-name syntax.

In k.LAB semantic expressions use the configured local authority name and bracket complex codes:

```text
WRB:LV
WRB:[LV-st.ab--ct]
WRB:[Albic Stagnic Luvisol (Cutanic)]
soil.groups:Luvisol
```

Both qualified forms resolve to the same canonical identity. Provider results include its
canonical code, full display name, stable concept name, local locator, parents and PDF source
documentation. The Reasoner may require the canonical code for semantic-search insertion;
use the resolved `getId()` value there. Full names remain accepted for resolution/reconciliation.

## Annotate a soil dataset

Consider a polygon layer with a categorical `soil_class` field, or a raster with a separate
category legend. The dataset's category keys may be local numbers; associate them with resolved
WRB identities in the resource's classification mapping:

| Dataset category | Source legend text | Resolved semantic identity |
| --- | --- | --- |
| `1` | Luvisol | `WRB:LV` (also `soil.groups:Luvisol`) |
| `2` | Albic Stagnic Luvisol (Cutanic) | `WRB:[LV-st.ab--ct]` |
| `3` | Dystric Arenosol (Bathyspodic) | `WRB:[AR-dy--sdd]` |
| no-data | No soil classification recorded | No identity; retain as missing data |

Bind the resource's categorical observable to the worldview's soil identity/soil observable
appropriate to the dataset, and configure the importing adapter's category mapping with these
semantic identities. Adapter-specific resource parameters differ; the table defines the mapping,
not a new resource parameter format. Resolve each legend entry before admitting the resource;
error notifications must stop that entry from becoming a semantic concept. Preserve the original
legend, source WRB edition and canonical codes in dataset provenance.

Do not resolve local category `2` as `WRB:2`: numeric category keys are not WRB RSG codes.
If the source is WRB 1998/2006/2014, review its classification against the 2022 rules before
annotating it with this bridge. A group-only dataset supplies no evidence for qualifiers.
Resolve an already classified name; this component cannot infer soil class from laboratory data.

For programmatic import validation, use the common authority contract:

```java
try (var authority = new WRBAuthority()) {
    String bridge = authority.configure(new Authority.ConfigurationRequest(
        "example.worldview", "WRB", "soil:SoilType",
        Map.of("urn", WRBAuthority.URN, "edition", "2022-2024")));
    var identity = authority.reconcile(bridge,
        Map.of("expression", "Albic Stagnic Luvisol (Cutanic)"));
    // Check getNotifications() for errors before accepting the mapping.
    String canonicalCode = identity.getId();     // LV-st.ab--ct
    String semanticReference = identity.getLocator(); // WRB:[LV-st.ab--ct]
}
```

`reconcile` accepts exactly one field: `expression`, `name`, or `code`. Search resolves a valid
expression exactly, or searches generic RSG names/codes by substring. It never enumerates modified
classes or selects a fuzzy spelling guess. Invalid resolution/reconciliation returns an identity
with an error notification; invalid or released configuration handles throw a validation exception.

## Subsumption and semantic distance

The provider answers the directional question "can the candidate supply the target?":

| Target | Candidate | Result |
| --- | --- | --- |
| `LV` | `LV-st.ab--ct` | SUBSUMES; positive compatible distance |
| `LV-ab` | `Albic Luvisol` | Equivalent; distance 0 |
| `LV-st` | `LV-stn.ab--ct` | SUBSUMES; optional Endostagnic preserves Stagnic criteria |
| `LV-st` | `LV` | UNKNOWN; the generic class does not establish Stagnic |
| `LV` | `CM` | INCOMPATIBLE; different RSGs in this edition |

Every qualified class has its generic RSG as parent; generic RSGs inherit the configured
worldview anchor. The provider additionally proves qualifier-set inclusion and optional depth
refinement under rules 1 and 2. This avoids enumerating all possible qualifier-subset parents.
Compatible specialization distance is the extra qualifier count, clamped to 1..100; equivalent
canonical expressions have distance 0. This is a ranking policy, not a measured pedological distance.

Unproven relationships return UNKNOWN rather than asserting disjointness. In particular, Proto-,
Bathy-, Supra-, Thapto-, rule-3/4/5 depth variants and defined subqualifiers do not automatically
imply the unmodified qualifier. All comparisons stay within a single configured bridge and edition.
The host's existing authority-distance and strict-subsumption hooks handle delegation.

## Validation scope

Validation checks vocabulary, naming/code grammar, section placement and sorting, eligible depth
specifiers, explicit RSG-only restrictions, slash alternatives, duplicates, Haplic exclusivity,
the Eutric/Calcaric redundancy and the predominant-qualifier requirement for depth-qualified
Dystric/Eutric. It permits non-listed qualifiers in the final section as the standard requires.

A name cannot prove diagnostic horizons, measured thresholds, complete profile descriptions,
non-overlapping depth intervals, or that every applicable qualifier has been included. Those remain
the responsibility of the soil scientist/source classifier. This is a naming authority, not a pedon
classification engine or a complete satisfiability solver for all diagnostic definitions.
See [reference notes](docs/REFERENCE.md#validation-boundary) for the exact boundary. Expressions
are limited to 8192 characters, 128 qualifiers per soil and eight buried-soil layers.

## WRB inconsistencies and implementation choices

Where examples and normative tables disagree, this implementation follows **Chapter 4 for RSG
membership/rank, Chapter 5 for definition/specifier rules, and Chapter 6 for codes**:

- Pelocrustic is `pq` from Chapter 6; the definition's `p` is treated as a typo.
- Hypergypsic is `jg`, as in its definition and Chapter 6; the cross-reference's `jy` is treated
  as a typo. `jy` denotes Hyperhydragric.
- The printed Ferralsol example includes Bathypetroplinthic among listed supplementary qualifiers,
  although Petroplinthic is absent from the current Ferralsol qualifier list. It is therefore moved
  to the final non-listed section: `FR-ha-loe-dy.ed.hu.po-ppd`, displayed as
  `Haplic Ferralsol (Pantoloamic, Dystric, Endic, Humic, Posic, Bathypetroplinthic)`.
  Supplying the printed full name normalizes to this form; the printed compact code with the
  wrong section placement is rejected. This keeps canonicalization consistent with other RSGs.

These choices are covered by tests. Future edition changes must be explicit; this component never
silently updates its vocabulary from the web. [Reference notes](docs/REFERENCE.md#source-inconsistencies)
record the source pages and extraction details for maintenance.

## Build, packaging and host requirements

Use JDK 21 and the current k.LAB authority API (including `SnapshottingAuthority`, codelists,
semantic distance and strict subsumption):

```powershell
mvn test
mvn package
```

When the sibling API is newer than your installed snapshot, a development-only override can
compile against its already-built jar without modifying shared Maven artifacts:

```powershell
mvn test "-Dklab.api.jar=C:/Users/Ferd/git/klab-services/klab.core.api/target/klab.core.api-1.0.0-SNAPSHOT.jar"
```

The k.LAB packaging plugin produces `target/klab.authority.wrb-component-1.0.0-SNAPSHOT.jar`.
Install that component on the Reasoner/Resources host through its component-management workflow.
`mvn install` makes Maven artifacts available locally; `mvn deploy` publishes using configured
repository credentials. Publication and actual service installation are separate from packaging.

Host libraries are `provided`; this component adds no runtime third-party libraries. The PF4J
entry point is `org.integratedmodelling.klab.authority.wrb.WRBPlugin`. Configuration capture/replay
is deterministic and offline: snapshots pin the bridge request and the bundled vocabulary digest,
and reject differing requests, format revisions or vocabulary bytes. Codelist seeds are fixed by
this vocabulary and returned as fresh DTOs. Successful results use an immutable edition/revision
cache policy. Release the configuration on bridge removal; `close()` releases all bridges.

Tests cover the official naming/code examples, every listed qualifier and RSG, canonical round
trips, malformed names/codes, reconciliation, independent bridges, generic/refined matching,
codelist policy and snapshot tampering. Full deployment against a running Reasoner and a real
dataset import must be verified in the target installation.
