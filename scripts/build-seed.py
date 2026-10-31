#!/usr/bin/env python3
"""
Builds src/main/resources/seed/*.json — the dataset museum-service loads at boot — ONCE, from the
open-access APIs of three museums. The service itself never calls a museum: it serves what this
script wrote, and the JSON files are committed.

    python3 scripts/build-seed.py            # fetch (cached under scripts/.cache) and write seed/
    python3 scripts/build-seed.py --offline  # rebuild from the cache only, no network

What it fetches, and why it is legitimate to republish:

  * The Metropolitan Museum of Art — collectionapi.metmuseum.org, objects with isPublicDomain=true.
    Met Open Access: the data and the images of public-domain works are CC0.
  * Art Institute of Chicago — api.artic.edu, is_public_domain=true; images through its IIIF server.
    The API's data is CC0 EXCEPT its `description` field (CC BY), which is therefore never read here;
    public-domain images are CC0.
  * Cleveland Museum of Art — openaccess-api.clevelandart.org, share_license_status=CC0 (cc0=1).
    Cleveland Open Access: data and images are CC0.

Only records with an image and a CC0 / public-domain status are kept. The images are NOT copied:
each artwork links the museum's own official image URLs (a small and a large one), which the museums
publish for exactly this use, with a credit line and a link back to the museum's page.

The rest — estimated values, exhibitions, loans, conservation tasks, daily visits, the artists'
short bios — is SYNTHETIC: plausible, generated here, and deterministic (every random choice comes
from a hash of the record it is about, or from a seeded generator run over records in a fixed
order), so running the script twice over the same cache writes the same files.
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import html
import json
import math
import random
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "scripts" / ".cache"
SEED = ROOT / "src" / "main" / "resources" / "seed"
UA = "museum-service-seed/0.1 (+https://github.com/miguelperezcolom; one-off build of a CC0 demo dataset)"

# The day the synthetic data is anchored to: statuses (open/closed, in transit…) are relative to it.
TODAY = dt.date(2026, 10, 31)

OFFLINE = False


# ── fetching, cached ───────────────────────────────────────────────────────────

def _key(url: str, body: str | None) -> Path:
    h = hashlib.sha1((url + "\n" + (body or "")).encode()).hexdigest()
    return CACHE / f"{h}.json"


def fetch(url: str, body: dict | None = None, tries: int = 5):
    raw_body = json.dumps(body, sort_keys=True) if body is not None else None
    path = _key(url, raw_body)
    if path.exists():
        return json.loads(path.read_text())
    if OFFLINE:
        raise RuntimeError(f"offline and not cached: {url}")
    last = None
    for attempt in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
            if raw_body is not None:
                req.data = raw_body.encode()
                req.add_header("Content-Type", "application/json")
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.loads(r.read().decode())
            CACHE.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data))
            time.sleep(0.05)  # polite: well under every API's published rate limit
            return data
        except Exception as e:  # 404s are answers; 5xx and timeouts are retried
            last = e
            if getattr(e, "code", None) == 404:
                CACHE.mkdir(parents=True, exist_ok=True)
                path.write_text("null")  # a 404 is an answer too: remembered, so --offline agrees
                return None
            time.sleep(1.5 * (attempt + 1))
    print(f"  ! giving up on {url}: {last}", file=sys.stderr)
    return None


# ── deterministic helpers ──────────────────────────────────────────────────────

def h01(*parts) -> float:
    """A stable number in [0, 1) from the given parts — the same record always gets the same draw."""
    digest = hashlib.sha256("|".join(str(p) for p in parts).encode()).digest()
    return int.from_bytes(digest[:8], "big") / 2**64


def pick(seq, *parts):
    return seq[int(h01(*parts) * len(seq))]


def two_sig(x: float) -> int:
    if x <= 0:
        return 0
    mag = 10 ** (int(math.floor(math.log10(x))) - 1)
    return int(round(x / mag) * mag)


def clean(text):
    if text is None:
        return None
    text = html.unescape(re.sub(r"<[^>]+>", "", str(text))).strip()
    text = re.sub(r"\s+", " ", text)
    return text or None


def year(v):
    try:
        return int(str(v).strip()[:5].strip()) if v not in (None, "") else None
    except ValueError:
        return None


NATIONALITY_RE = re.compile(r"\(([^,()]+?),\s*(?:born\s+)?(?:c\.\s*)?(-?\d{3,4})?")


def parse_display(display: str | None):
    """'Vincent van Gogh (Dutch, 1853–1890)' → ('Vincent van Gogh', 'Dutch', 1853, 1890)."""
    if not display:
        return None, None, None, None
    first = display.split("\n")[0].strip()
    name = re.sub(r"\s*\(.*$", "", first).strip()
    nat = birth = death = None
    m = re.search(r"\(([^()]*)\)", first)
    if m:
        inside = m.group(1)
        parts = [p.strip() for p in inside.split(",")]
        if parts and not re.search(r"\d", parts[0]):
            nat = parts[0]
        years = re.findall(r"(\d{3,4})", inside)
        if years:
            birth = int(years[0])
            if len(years) > 1:
                death = int(years[1])
    return name, nat, birth, death


def artist_key(name: str) -> str:
    n = name.lower()
    n = re.sub(r"\(.*?\)", "", n)
    n = re.sub(r"^(attributed to|workshop of|circle of|follower of|after|style of)\s+", "", n)
    n = re.sub(r"[^a-zÀ-ɏ ]", " ", n)
    return re.sub(r"\s+", " ", n).strip()


# The three museums each have their own vocabulary ("Paintings", "Painting", "Oil paintings"…). A
# filter needs ONE: this folds them into a short canonical list, and the museum's own term is kept
# as `objectType`.
CLASSES = [
    (r"photo", "Photograph"),
    (r"arms|armor|armour|sword|helmet|firearm|dagger|shield|crossbow|archery|spur|equestrian", "Arms and Armor"),
    (r"paint|portrait miniature", "Painting"), (r"mastaba|architectur", "Architecture"),
    (r"sculpt|stone|bronze|figure|relief|statue|head|protome|talatat", "Sculpture"),
    (r"drawing|watercolor|watercolour|pastel", "Drawing"), (r"print|woodcut|etching", "Print"),
    (r"ceramic|porcelain|vessel|pottery|stoneware|earthenware|vase|dish", "Ceramics"),
    (r"textile|garment|costume|carpet|tapestr|embroider", "Textile"),
    (r"jewel", "Jewelry"), (r"glass", "Glass"), (r"furniture|wood", "Furniture"),
    (r"manuscript|book|calligraph", "Manuscript"),
    (r"metal|silver|gold", "Metalwork"), (r"lacquer|jade|ivor|enamel|game box|horolog", "Decorative arts"),
]


def canonical_class(*terms):
    hay = " ".join(t for t in terms if t).lower()
    for pattern, name in CLASSES:
        if re.search(pattern, hay):
            return name
    return "Other"


UNKNOWN = re.compile(r"^(unknown|unidentified|anonymous|artist unknown|various|maker unknown)", re.I)


# ── the three museums ──────────────────────────────────────────────────────────

MUSEUMS = [
    {
        "code": "MET", "name": "The Metropolitan Museum of Art", "shortName": "The Met",
        "city": "New York", "country": "United States", "latitude": 40.7794, "longitude": -73.9632,
        "founded": 1870, "website": "https://www.metmuseum.org",
        "description": "Founded in 1870 on Fifth Avenue, the Met holds over two million works spanning "
                       "five thousand years — from Egyptian temples to European old masters, arms and "
                       "armour, and photography.",
        "credit": "The Metropolitan Museum of Art · CC0",
    },
    {
        "code": "AIC", "name": "Art Institute of Chicago", "shortName": "Art Institute",
        "city": "Chicago", "country": "United States", "latitude": 41.8796, "longitude": -87.6237,
        "founded": 1879, "website": "https://www.artic.edu",
        "description": "On Michigan Avenue since 1893, the Art Institute is famous for its "
                       "Impressionist and Post-Impressionist paintings, its Asian art and its "
                       "collection of American art.",
        "credit": "Art Institute of Chicago · CC0",
    },
    {
        "code": "CMA", "name": "Cleveland Museum of Art", "shortName": "Cleveland",
        "city": "Cleveland", "country": "United States", "latitude": 41.5089, "longitude": -81.6116,
        "founded": 1913, "website": "https://www.clevelandart.org",
        "description": "Free to all since it opened in 1916, the Cleveland Museum of Art is known for "
                       "its Asian and Egyptian collections, its medieval treasures and its armour court.",
        "credit": "Cleveland Museum of Art · CC0",
    },
]

# How many works to take from each department — the variety the demo needs (paintings, sculpture,
# arms and armour, photography, Asian, Egyptian, decorative arts…), not each museum's proportions.
MET_QUOTAS = [
    (11, 26), (4, 12), (6, 14), (10, 12), (13, 10), (19, 10), (12, 12), (1, 8), (17, 8), (14, 8),
    (9, 6), (5, 6),
]
AIC_QUOTAS = [
    ("Painting and Sculpture of Europe", 30), ("Arts of the Americas", 20), ("Arts of Asia", 18),
    ("Prints and Drawings", 10), ("Applied Arts of Europe", 10), ("Textiles", 8),
    ("Arts of Africa", 7), ("Photography and Media", 8), ("Modern Art", 5),
    ("Arts of Greece, Rome, and Byzantium", 6), ("Architecture and Design", 2),
]
CMA_QUOTAS = [
    ("European Painting and Sculpture", 16), ("Modern European Painting and Sculpture", 10),
    ("American Painting and Sculpture", 8), ("Japanese Art", 10), ("Chinese Art", 8),
    ("Korean Art", 6), ("Indian and Southeast Asian Art", 6), ("Egyptian and Ancient Near Eastern Art", 10),
    ("Greek and Roman Art", 8), ("Medieval Art", 10), ("Decorative Art and Design", 8),
    ("Drawings", 6), ("Prints", 5), ("African Art", 5), ("Art of the Americas", 6), ("Textiles", 3),
    ("Islamic Art", 4), ("Photography", 4),
]
CMA_EXTRA_TYPES = [("Arms and Armor", 8), ("Photograph", 4)]


def met_artworks():
    out = []
    for dept_id, quota in MET_QUOTAS:
        taken = 0
        for highlight in (True, False):
            if taken >= quota:
                break
            q = {"departmentId": dept_id, "hasImages": "true", "q": "*", "limit": 300}
            if highlight:
                q["isHighlight"] = "true"
            res = fetch("https://collectionapi.metmuseum.org/public/collection/v1.1/search?" + urllib.parse.urlencode(q))
            ids = (res or {}).get("objectIDs") or []
            for oid in ids:
                if taken >= quota:
                    break
                o = fetch(f"https://collectionapi.metmuseum.org/public/collection/v1/objects/{oid}")
                if not o or not o.get("isPublicDomain") or not o.get("primaryImageSmall"):
                    continue
                if any(a["source"] == "MET" and a["sourceId"] == str(oid) for a in out):
                    continue
                out.append(met_row(o))
                taken += 1
        print(f"  MET dept {dept_id}: {taken}/{quota}")
    return out


def met_row(o):
    large = o["primaryImageSmall"]  # web-large, ~1000 px
    small = large.replace("/web-large/", "/mobile-large/")
    h = w = d = None
    for m in o.get("measurements") or []:
        if m.get("elementName") in ("Overall", "Painting", "Image", "Sheet", "H.", "Height") or h is None:
            em = m.get("elementMeasurements") or {}
            if em.get("Height") or em.get("Width"):
                h, w, d = em.get("Height"), em.get("Width"), em.get("Depth")
                if m.get("elementName") == "Overall":
                    break
    artist = clean(o.get("artistDisplayName"))
    return {
        "source": "MET", "sourceId": str(o["objectID"]),
        "title": clean(o.get("title")) or "Untitled",
        "artistName": artist, "artistNationality": clean(o.get("artistNationality")),
        "artistBirth": year(o.get("artistBeginDate")), "artistDeath": year(o.get("artistEndDate")),
        "department": o.get("department"),
        "classification": clean(o.get("classification")) or clean(o.get("objectName")),
        "objectType": clean(o.get("objectName")),
        "medium": clean(o.get("medium")),
        "objectDate": clean(o.get("objectDate")),
        "beginYear": o.get("objectBeginDate"), "endYear": o.get("objectEndDate"),
        "dimensions": clean(o.get("dimensions")),
        "heightCm": h, "widthCm": w, "depthCm": d,
        "culture": clean(o.get("culture")) or clean(o.get("country")) or clean(o.get("artistNationality")),
        "onView": bool(o.get("GalleryNumber")),
        "gallery": f"Gallery {o['GalleryNumber']}" if o.get("GalleryNumber") else None,
        "acquisitionYear": year(o.get("accessionYear")),
        "accessionNumber": o.get("accessionNumber"),
        "creditLine": clean(o.get("creditLine")),
        "tags": [t["term"] for t in (o.get("tags") or []) if t.get("term")][:6],
        "description": None,
        "imageSmall": small, "imageLarge": large,
        "sourceUrl": o.get("objectURL") or f"https://www.metmuseum.org/art/collection/search/{o['objectID']}",
        "highlight": bool(o.get("isHighlight")),
    }


AIC_FIELDS = ",".join([
    "id", "title", "artist_title", "artist_id", "artist_display", "department_title",
    "classification_title", "artwork_type_title", "medium_display", "date_start", "date_end",
    "date_display", "dimensions", "dimensions_detail", "place_of_origin", "is_on_view",
    "gallery_title", "fiscal_year", "credit_line", "term_titles", "style_title", "image_id",
    "is_public_domain", "main_reference_number", "is_boosted",
])


def aic_artworks():
    out = []
    for dept, quota in AIC_QUOTAS:
        taken = 0
        for boosted in (True, False):
            if taken >= quota:
                break
            must = [
                {"term": {"is_public_domain": True}},
                {"term": {"department_title.keyword": dept}},
                {"exists": {"field": "image_id"}},
            ]
            if boosted:
                must.append({"term": {"is_boosted": True}})
            res = fetch("https://api.artic.edu/api/v1/artworks/search",
                        {"query": {"bool": {"must": must}}, "fields": AIC_FIELDS, "limit": 100})
            for a in (res or {}).get("data") or []:
                if taken >= quota:
                    break
                if not a.get("image_id") or not a.get("is_public_domain"):
                    continue
                if any(x["source"] == "AIC" and x["sourceId"] == str(a["id"]) for x in out):
                    continue
                out.append(aic_row(a))
                taken += 1
        print(f"  AIC {dept}: {taken}/{quota}")
    return out


def aic_row(a):
    iiif = f"https://www.artic.edu/iiif/2/{a['image_id']}"
    name, nat, birth, death = parse_display(a.get("artist_display"))
    artist = a.get("artist_title") or name
    if a.get("artist_display") and "\n" in a["artist_display"] and not a.get("artist_title"):
        artist = None  # "Japan\nEdo period" — a place, not a person
    dims = (a.get("dimensions_detail") or [{}])[0] or {}
    return {
        "source": "AIC", "sourceId": str(a["id"]),
        "title": clean(a.get("title")) or "Untitled",
        "artistName": clean(artist), "artistNationality": nat,
        "artistBirth": birth, "artistDeath": death,
        "department": a.get("department_title"),
        "classification": clean(a.get("artwork_type_title")) or clean(a.get("classification_title")),
        "objectType": clean(a.get("classification_title")),
        "medium": clean(a.get("medium_display")),
        "objectDate": clean(a.get("date_display")),
        "beginYear": a.get("date_start"), "endYear": a.get("date_end"),
        "dimensions": clean(a.get("dimensions")),
        "heightCm": dims.get("height"), "widthCm": dims.get("width"), "depthCm": dims.get("depth"),
        "culture": clean(a.get("place_of_origin")),
        "onView": bool(a.get("is_on_view")),
        "gallery": a.get("gallery_title"),
        "acquisitionYear": a.get("fiscal_year"),
        "accessionNumber": a.get("main_reference_number"),
        "creditLine": clean(a.get("credit_line")),
        "tags": list(dict.fromkeys(t for t in (a.get("term_titles") or []) if t))[:6],
        "description": None,  # AIC's description is CC BY, not CC0 — deliberately not used
        "imageSmall": f"{iiif}/full/400,/0/default.jpg",
        "imageLarge": f"{iiif}/full/843,/0/default.jpg",
        "sourceUrl": f"https://www.artic.edu/artworks/{a['id']}",
        "highlight": bool(a.get("is_boosted")),
    }


def cma_page(params):
    q = {"cc0": 1, "has_image": 1, **params}
    res = fetch("https://openaccess-api.clevelandart.org/api/artworks/?" + urllib.parse.urlencode(q))
    return (res or {}).get("data") or []


def cma_artworks():
    out = []
    highlights = cma_page({"highlight": 1, "limit": 300})
    for dept, quota in CMA_QUOTAS:
        taken = 0
        pools = [
            [a for a in highlights if a.get("department") == dept],
            cma_page({"department": dept, "limit": 60}),
        ]
        for pool in pools:
            for a in pool:
                if taken >= quota:
                    break
                if a.get("share_license_status") != "CC0" or not (a.get("images") or {}).get("web"):
                    continue
                if any(x["source"] == "CMA" and x["sourceId"] == str(a["id"]) for x in out):
                    continue
                out.append(cma_row(a))
                taken += 1
        print(f"  CMA {dept}: {taken}/{quota}")
    for kind, quota in CMA_EXTRA_TYPES:
        taken = 0
        for a in cma_page({"type": kind, "limit": 60}):
            if taken >= quota:
                break
            if a.get("share_license_status") != "CC0" or not (a.get("images") or {}).get("web"):
                continue
            if any(x["source"] == "CMA" and x["sourceId"] == str(a["id"]) for x in out):
                continue
            out.append(cma_row(a))
            taken += 1
        print(f"  CMA type {kind}: {taken}/{quota}")
    return out


def cma_row(a):
    creator = next((c for c in a.get("creators") or [] if (c.get("role") or "").lower() in ("artist", "maker", "")), None) \
        or (a.get("creators") or [None])[0]
    name = nat = birth = death = None
    if creator:
        name, nat, birth, death = parse_display(creator.get("description"))
        birth = year(creator.get("birth_year")) or birth
        death = year(creator.get("death_year")) or death
    dims = a.get("dimensions") or {}
    main = dims.get("unframed") or dims.get("overall") or next(iter(dims.values()), {}) if dims else {}
    main = main or {}

    def cm(v):
        return round(v * 100, 1) if isinstance(v, (int, float)) else None

    images = a.get("images") or {}
    gallery = a.get("current_location")
    return {
        "source": "CMA", "sourceId": str(a["id"]),
        "title": clean(a.get("title")) or "Untitled",
        "artistName": clean(name), "artistNationality": nat, "artistBirth": birth, "artistDeath": death,
        "department": a.get("department"),
        "classification": clean(a.get("type")),
        "objectType": clean(a.get("collection")),
        "medium": clean(a.get("technique")),
        "objectDate": clean(a.get("creation_date")),
        "beginYear": a.get("creation_date_earliest"), "endYear": a.get("creation_date_latest"),
        "dimensions": clean(a.get("measurements")),
        "heightCm": cm(main.get("height")), "widthCm": cm(main.get("width")), "depthCm": cm(main.get("depth")),
        "culture": ", ".join(a.get("culture") or []) or None,
        "onView": bool(gallery),
        "gallery": f"Gallery {gallery.split(' ')[0]}" if gallery and gallery[:1].isdigit() else gallery,
        "acquisitionYear": year((a.get("accession_date") or "")[:4]),
        "accessionNumber": a.get("accession_number"),
        "creditLine": clean(a.get("creditline")),
        "tags": [],
        "description": clean(a.get("description")),
        "imageSmall": images["web"]["url"],
        "imageLarge": (images.get("print") or images["web"])["url"],
        "sourceUrl": a.get("url") or f"https://www.clevelandart.org/art/{a.get('accession_number')}",
        "highlight": bool(a.get("is_highlight")),
    }


# ── synthetic: values, exhibitions, loans, conservation, visits ───────────────

VALUE_BANDS = [  # (pattern over classification/department, low, high) in USD — log-uniform
    (r"paint", 1_500_000, 45_000_000),
    (r"sculpt", 400_000, 12_000_000),
    (r"arms|armor|armour|sword|helmet", 150_000, 3_500_000),
    (r"photo", 15_000, 900_000),
    (r"print|drawing|book|manuscript", 8_000, 2_000_000),
    (r"textile|garment|carpet|costume", 20_000, 600_000),
    (r"egypt|greek|roman|ancient|near east", 300_000, 9_000_000),
    (r"ceramic|porcelain|vessel|jade|lacquer|metal|silver|glass|furniture|wood", 40_000, 3_000_000),
]


def estimated_value(a):
    hay = f"{a['classification'] or ''} {a['objectType'] or ''} {a['department'] or ''}".lower()
    low, high = 30_000, 1_500_000
    for pattern, lo, hi in VALUE_BANDS:
        if re.search(pattern, hay):
            low, high = lo, hi
            break
    r = h01("value", a["source"], a["sourceId"])
    if a["highlight"]:
        r = 0.35 + 0.65 * r
    return two_sig(math.exp(math.log(low) + r * (math.log(high) - math.log(low))))


FIRST = ["Ana", "Ben", "Clara", "David", "Elena", "Farid", "Grace", "Hiro", "Isabel", "James", "Kenji",
         "Laura", "Marcus", "Nadia", "Omar", "Paula", "Quentin", "Rosa", "Samuel", "Teresa", "Uma",
         "Victor", "Wen", "Ximena", "Yusuf", "Zoe"]
LAST = ["Abbott", "Bianchi", "Castillo", "Dubois", "Eriksen", "Fujita", "García", "Hoffman", "Ibrahim",
        "Jensen", "Kowalski", "López", "Moreau", "Nakamura", "Okafor", "Park", "Quinn", "Rossi",
        "Schmidt", "Tanaka", "Ueda", "Varga", "Weiss", "Xu", "Young", "Zimmermann"]


def person(*parts):
    return f"{pick(FIRST, 'f', *parts)} {pick(LAST, 'l', *parts)}"


EXHIBITION_THEMES = {
    "paint": ["Light and Shadow: {d}", "Masters of the Canvas", "Colour in Revolution", "Portraits of an Age"],
    "asia": ["Ink and Silk", "The Floating World", "Paths of the Buddha", "Porcelain Roads"],
    "egypt": ["Eternal Egypt", "Gods of the Nile", "Afterlives"],
    "greek|roman|mediterr": ["Marble and Myth", "The Classical Ideal"],
    "arms|armor": ["Steel and Ceremony", "Knights and Samurai"],
    "photo": ["The Camera's Eye", "Silver and Light"],
    "print|draw": ["Lines of Thought: Works on Paper", "The Printed Image"],
    "decor|applied|textile|design": ["Objects of Desire", "Threads of History", "Made by Hand"],
    "medieval|cloister": ["Treasures of the Middle Ages", "Faith and Gold"],
    "america": ["American Visions", "New World Stories"],
    "africa|oceania": ["Power and Presence", "Ancestors and Spirits"],
    "islam": ["Patterns of the Divine"],
    "modern": ["Breaking the Frame"],
}


def theme_for(dept, salt):
    d = (dept or "").lower()
    for pattern, titles in EXHIBITION_THEMES.items():
        if re.search(pattern, d):
            return pick(titles, "theme", salt).format(d=dept)
    return f"Highlights of {dept}"


LOAN_FLOW = ["requested", "approved", "in_transit", "on_display", "returned"]
TASK_TYPES = ["Condition report", "Surface cleaning", "Varnish removal", "Inpainting", "Frame repair",
              "Consolidation", "Technical imaging", "Rehousing", "Mount fabrication", "Pest inspection"]
TASK_STATUS = ["backlog", "in_progress", "review", "done"]
PRIORITIES = ["low", "medium", "high", "urgent"]


def main():
    global OFFLINE
    ap = argparse.ArgumentParser()
    ap.add_argument("--offline", action="store_true")
    OFFLINE = ap.parse_args().offline

    print("Fetching from the museums (cached in scripts/.cache)…")
    rows = met_artworks() + aic_artworks() + cma_artworks()

    # ── museums ──
    museums = []
    museum_id = {}
    for i, m in enumerate(MUSEUMS, start=1):
        museum_id[m["code"]] = i

    # ── departments ──
    departments, dept_id = [], {}
    for r in rows:
        k = (r["source"], r["department"] or "Other")
        if k not in dept_id:
            dept_id[k] = len(departments) + 1
            departments.append({"id": dept_id[k], "name": k[1], "museumId": museum_id[k[0]],
                                "head": person("head", *k)})

    # ── artists ──
    artists, artist_id = [], {}
    for r in rows:
        name = r["artistName"]
        if not name or UNKNOWN.match(name) or len(name) < 3:
            r["artistId"] = None
            continue
        k = artist_key(name)
        if not k:
            r["artistId"] = None
            continue
        if k not in artist_id:
            artist_id[k] = len(artists) + 1
            artists.append({"id": artist_id[k], "name": name, "nationality": r["artistNationality"],
                            "birthYear": r["artistBirth"], "deathYear": r["artistDeath"], "_works": []})
        a = artists[artist_id[k] - 1]
        a["nationality"] = a["nationality"] or r["artistNationality"]
        a["birthYear"] = a["birthYear"] or r["artistBirth"]
        a["deathYear"] = a["deathYear"] or r["artistDeath"]
        a["_works"].append(r)
        r["artistId"] = a["id"]

    credit = {m["code"]: m["credit"] for m in MUSEUMS}
    for a in artists:
        works = a["_works"]
        homes = sorted({next(m["shortName"] for m in MUSEUMS if m["code"] == w["source"]) for w in works})
        span = ""
        if a["birthYear"] and a["deathYear"]:
            span = f" ({a['birthYear']}–{a['deathYear']})"
        elif a["birthYear"]:
            span = f" (born {a['birthYear']})"
        kinds = sorted({(w["classification"] or "work").lower() for w in works})
        a["bio"] = (f"{(a['nationality'] + ' artist') if a['nationality'] else 'Artist'}{span}. "
                    f"Represented here by {len(works)} {'work' if len(works) == 1 else 'works'} "
                    f"({', '.join(kinds[:3])}) in the collection{'s' if len(homes) > 1 else ''} of "
                    f"{' and '.join(homes)}.")
        best = max(works, key=lambda w: (w["highlight"], h01("artist-img", w["source"], w["sourceId"])))
        a["image"] = best["imageSmall"]
        a["imageCredit"] = f"{credit[best['source']]} — a work by the artist, not a portrait"
        del a["_works"]

    # ── artworks ──
    artworks = []
    for i, r in enumerate(rows, start=1):
        r["id"] = i
        r["estimatedValue"] = estimated_value(r)
        artworks.append({
            "id": i, "title": r["title"], "artistId": r["artistId"],
            "artistDisplay": r["artistName"] or r["culture"] or "Unknown maker",
            "museumId": museum_id[r["source"]], "departmentId": dept_id[(r["source"], r["department"] or "Other")],
            "classification": canonical_class(r["classification"], r["objectType"]),
            "objectType": r["classification"] or r["objectType"], "medium": r["medium"],
            "objectDate": r["objectDate"], "beginYear": r["beginYear"], "endYear": r["endYear"],
            "dimensions": r["dimensions"],
            "heightCm": r["heightCm"], "widthCm": r["widthCm"], "depthCm": r["depthCm"],
            "culture": r["culture"], "onView": r["onView"], "gallery": r["gallery"],
            "acquisitionYear": r["acquisitionYear"], "accessionNumber": r["accessionNumber"],
            "creditLine": r["creditLine"], "estimatedValue": r["estimatedValue"],
            "tags": ", ".join(r["tags"]) if r["tags"] else None,
            "description": r["description"], "highlight": r["highlight"],
            "imageSmall": r["imageSmall"], "imageLarge": r["imageLarge"],
            "imageCredit": credit[r["source"]], "sourceUrl": r["sourceUrl"],
            "source": r["source"], "sourceId": r["sourceId"],
        })

    for m in MUSEUMS:
        mine = [a for a in artworks if a["museumId"] == museum_id[m["code"]]]
        best = max(mine, key=lambda a: (a["highlight"] and (a["classification"] or "").lower().startswith("paint"),
                                        a["estimatedValue"]))
        museums.append({
            "id": museum_id[m["code"]], "code": m["code"], "name": m["name"], "shortName": m["shortName"],
            "city": m["city"], "country": m["country"], "latitude": m["latitude"], "longitude": m["longitude"],
            "founded": m["founded"], "website": m["website"], "description": m["description"],
            "image": best["imageLarge"], "imageCredit": f"{m['credit']} — {best['title']}",
        })

    rnd = random.Random(20261031)

    # ── exhibitions: per museum, two closed, two open, two planned ──
    exhibitions = []
    by_dept = {}
    for a in artworks:
        by_dept.setdefault(a["departmentId"], []).append(a)
    for m in museums:
        depts = sorted([d for d in departments if d["museumId"] == m["id"]],
                       key=lambda d: -len(by_dept.get(d["id"], [])))
        plan = [("closed", -400, -250), ("closed", -230, -120), ("open", -90, 60), ("open", -40, 150),
                ("planned", 60, 160), ("planned", 140, 260)]
        for j, (status, start_from, start_to) in enumerate(plan):
            d = depts[j % len(depts)]
            start = TODAY + dt.timedelta(days=rnd.randint(start_from, start_to) if status != "open" else rnd.randint(start_from, -10))
            length = rnd.choice([90, 105, 120, 150, 180])
            end = start + dt.timedelta(days=length)
            if status == "open" and end <= TODAY:
                end = TODAY + dt.timedelta(days=rnd.randint(30, 150))
            if status == "closed" and end >= TODAY:
                end = TODAY - dt.timedelta(days=rnd.randint(5, 60))
            pool = by_dept.get(d["id"], [])
            works = sorted(rnd.sample(pool, min(len(pool), rnd.randint(6, 12))), key=lambda a: a["id"])
            daily = {"MET": 1800, "AIC": 700, "CMA": 300}[m["code"]]
            days = (min(end, TODAY) - start).days
            visitors = 0 if status == "planned" else int(max(days, 0) * daily * rnd.uniform(0.6, 1.4))
            title = theme_for(d["name"], f"{m['code']}{j}")
            if any(e["title"] == title for e in exhibitions):
                title = f"{title} ({m['shortName']})"
            exhibitions.append({
                "id": len(exhibitions) + 1, "title": title, "museumId": m["id"],
                "startDate": start.isoformat(), "endDate": end.isoformat(), "status": status,
                "curator": person("curator", m["code"], j),
                "description": f"An exhibition drawn from the {d['name']} collection of {m['name']}, "
                               f"with {len(works)} works on view.",
                "budget": two_sig(rnd.uniform(150_000, 2_400_000)), "visitors": visitors,
                "artworkIds": [a["id"] for a in works],
            })

    # ── loans: works travelling to another museum's exhibition, across the whole workflow ──
    loans = []
    targets = [e for e in exhibitions if e["status"] in ("open", "planned")]
    flow_for = {"open": ["on_display", "on_display", "in_transit"], "planned": ["requested", "approved", "approved", "requested"]}
    for e in targets:
        others = [a for a in artworks if a["museumId"] != e["museumId"] and a["estimatedValue"] > 50_000]
        for k in range(rnd.randint(2, 4)):
            a = rnd.choice(others)
            status = rnd.choice(flow_for[e["status"]])
            start = dt.date.fromisoformat(e["startDate"]) - dt.timedelta(days=14)
            end = dt.date.fromisoformat(e["endDate"]) + dt.timedelta(days=14)
            loans.append({
                "id": len(loans) + 1, "artworkId": a["id"], "fromMuseumId": a["museumId"],
                "toMuseumId": e["museumId"], "exhibitionId": e["id"],
                "startDate": start.isoformat(), "endDate": end.isoformat(), "status": status,
                "insuranceValue": two_sig(a["estimatedValue"] * rnd.uniform(1.1, 1.5)),
                "courier": person("courier", e["id"], k),
                "notes": "Climate-controlled crate; courier accompanies the work." if a["estimatedValue"] > 5_000_000 else None,
            })
    for e in [e for e in exhibitions if e["status"] == "closed"]:
        others = [a for a in artworks if a["museumId"] != e["museumId"]]
        for k in range(2):
            a = rnd.choice(others)
            loans.append({
                "id": len(loans) + 1, "artworkId": a["id"], "fromMuseumId": a["museumId"],
                "toMuseumId": e["museumId"], "exhibitionId": e["id"],
                "startDate": (dt.date.fromisoformat(e["startDate"]) - dt.timedelta(days=14)).isoformat(),
                "endDate": (dt.date.fromisoformat(e["endDate"]) + dt.timedelta(days=14)).isoformat(),
                "status": "returned", "insuranceValue": two_sig(a["estimatedValue"] * rnd.uniform(1.1, 1.5)),
                "courier": person("courier", e["id"], k), "notes": None,
            })

    # ── conservation tasks: a kanban per museum ──
    tasks = []
    for m in museums:
        mine = [a for a in artworks if a["museumId"] == m["id"]]
        conservators = [person("cons", m["code"], k) for k in range(5)]
        for k in range(24):
            a = rnd.choice(mine)
            status = TASK_STATUS[min(3, int(rnd.random() ** 0.8 * 4))]
            due = TODAY + dt.timedelta(days=rnd.randint(-45, 120) if status != "done" else rnd.randint(-120, -1))
            kind = rnd.choice(TASK_TYPES)
            tasks.append({
                "id": len(tasks) + 1, "title": f"{kind}: {a['title'][:60]}", "artworkId": a["id"],
                "type": kind, "priority": rnd.choices(PRIORITIES, weights=[3, 5, 3, 1])[0], "status": status,
                "assignee": rnd.choice(conservators), "dueDate": due.isoformat(),
                "estimatedHours": rnd.choice([2, 4, 6, 8, 12, 16, 24, 40]),
                "notes": None,
            })

    # ── visits: one row per museum per OPEN day, 1 Jan → today ──
    closed_days = {"MET": {2}, "AIC": {1, 2}, "CMA": {0}}  # Mon=0: Met closed Wed; AIC Tue+Wed; CMA Mon
    base = {"MET": 15500, "AIC": 4800, "CMA": 2100}
    ticket = {"MET": 30, "AIC": 32, "CMA": 0}  # Cleveland is free
    visits = []
    day = dt.date(2026, 1, 1)
    while day <= TODAY:
        for m in museums:
            if day.weekday() in closed_days[m["code"]]:
                continue
            season = 1 + 0.22 * math.sin((day.timetuple().tm_yday - 100) / 365 * 2 * math.pi)
            weekend = 1.35 if day.weekday() >= 5 else 1.0
            noise = 0.85 + 0.3 * h01("visit", m["code"], day.isoformat())
            n = int(base[m["code"]] * season * weekend * noise)
            members = int(n * (0.18 + 0.08 * h01("members", m["code"], day.isoformat())))
            visits.append({"id": len(visits) + 1, "museumId": m["id"], "date": day.isoformat(),
                           "visitors": n, "members": members,
                           "revenue": round((n - members) * ticket[m["code"]] * 0.62, 2)})
        day += dt.timedelta(days=1)

    SEED.mkdir(parents=True, exist_ok=True)
    for name, data in [("museums", museums), ("departments", departments), ("artists", artists),
                       ("artworks", artworks), ("exhibitions", exhibitions), ("loans", loans),
                       ("conservation-tasks", tasks), ("visits", visits)]:
        (SEED / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n")
        print(f"  wrote seed/{name}.json: {len(data)}")

    write_credits(museums, artworks)


def write_credits(museums, artworks):
    by_museum = {m["id"]: m for m in museums}
    lines = [
        "# Image credits",
        "",
        "Every artwork in this dataset is a **public-domain work** whose record and image the holding",
        "museum publishes under **CC0** (Creative Commons Zero) through its open-access programme. The",
        "images are not copied into this repository: each record links the museum's own official image",
        "URLs (`imageSmall`, `imageLarge`) and its page (`sourceUrl`). CC0 asks for nothing, but the",
        "museums ask for a courtesy credit, so every record carries `imageCredit` and `/api/credits`",
        "lists them all.",
        "",
        "| Museum | Programme | Licence | Works |",
        "|---|---|---|---|",
        "| The Metropolitan Museum of Art | [Open Access](https://www.metmuseum.org/hubs/open-access) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | %d |" % sum(1 for a in artworks if a["source"] == "MET"),
        "| Art Institute of Chicago | [Open Access](https://www.artic.edu/open-access/open-access-images) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) (the API's `description` field is CC BY and is not used) | %d |" % sum(1 for a in artworks if a["source"] == "AIC"),
        "| Cleveland Museum of Art | [Open Access](https://www.clevelandart.org/open-access) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | %d |" % sum(1 for a in artworks if a["source"] == "CMA"),
        "",
        "The museums' names are used only to say where each work is held; this project is not affiliated",
        "with or endorsed by any of them.",
        "",
        "## Works",
        "",
        "| # | Title | Artist / culture | Museum | Credit | Source |",
        "|---|---|---|---|---|---|",
    ]
    for a in artworks:
        title = a["title"].replace("|", "\\|")
        who = (a["artistDisplay"] or "").replace("|", "\\|")
        lines.append(f"| {a['id']} | {title} | {who} | {by_museum[a['museumId']]['shortName']} | "
                     f"{a['imageCredit']} | [page]({a['sourceUrl']}) |")
    (ROOT / "CREDITS.md").write_text("\n".join(lines) + "\n")
    print("  wrote CREDITS.md")


if __name__ == "__main__":
    main()
