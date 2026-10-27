#!/usr/bin/env python3
"""Extrage ofertele curente Penny și Lidl și le salvează în catalogul aplicației.

Rulare (din rădăcina proiectului):
    pip install -r scripts/requirements.txt
    python scripts/fetch_catalogs.py                 # ambele magazine
    python scripts/fetch_catalogs.py --store penny   # un singur magazin

Pentru fiecare magazin:
    1. Citește perioada de valabilitate a pliantului de pe site (`found`).
    2. Compară cu valabilitatea salvată deja în JSON (`current`):
         found <= current  -> pliantul nou nu e publicat; magazinul nu se modifică.
         found >  current  -> pliant nou: se parsează produsele și se înlocuiește secțiunea magazinului.
    Produsele se parsează abia după această verificare, deci o rulare fără pliant nou e ieftină.

Surse:
    Penny: pliantul național activ. Slug-ul categoriei se citește din datele paginii
           https://www.penny.ro/oferte; valabilitatea și produsele vin din API-ul intern de
           categorii, paginat. Fallback: produsele afișate în /oferte.
    Lidl:  produsele promoționale din https://www.lidl.ro/oferte (redirecționează la homepage),
           din atributele `data-grid-data`. Valabilitatea = cea mai târzie dată de sfârșit a
           produselor cu preț public.

Ieșire: app/src/main/assets/catalog_products.json

Coduri de ieșire:
    0  fiecare magazin a fost verificat (pliant nou actualizat sau pliant nepublicat încă)
    1  s-a obținut un catalog gol (fișierul nu a fost modificat)
    2  un magazin a eșuat; datele lui din fișierul anterior au fost păstrate
"""

from __future__ import annotations

import argparse
import html
import json
import logging
import os
import re
import sys
import tempfile
from collections.abc import Callable
from dataclasses import asdict, dataclass
from datetime import date, datetime
from pathlib import Path
from typing import Any, Iterator
from zoneinfo import ZoneInfo

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

log = logging.getLogger("fetch_catalogs")

BUCHAREST = ZoneInfo("Europe/Bucharest")
ROOT_DIR = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT = ROOT_DIR / "app" / "src" / "main" / "assets" / "catalog_products.json"

STORES = ("Penny", "Lidl")
STORE_ARGUMENTS = {"all": None, "penny": "Penny", "lidl": "Lidl"}

PENNY_PLIANT_PAGE = "https://www.penny.ro/vezi-pliantele-saptamanii"
PENNY_OFFERS_PAGE = "https://www.penny.ro/oferte"
PENNY_CATEGORY_PRODUCTS = "https://www.penny.ro/api/product-discovery/categories/{slug}/products"
PENNY_PAGE_SIZE = 14
PENNY_MAX_PAGES = 100
PENNY_FLYER_NAME = re.compile(r"PLIANT NATIONAL", re.IGNORECASE)
PENNY_DEFAULT_CATEGORY = "Generale"

LIDL_OFFERS_PAGE = "https://www.lidl.ro/oferte"
LIDL_REGION_KEY = "1"
LIDL_DEFAULT_CATEGORY = "Generale"
LIDL_CATEGORY_LABELS = {"Food": "Alimente", "F+V": "Fructe și legume"}
LIDL_GRID_ATTRIBUTE = re.compile(r'data-grid-data="([^"]*)"')

NUXT_PAYLOAD = re.compile(r'<script[^>]*id="__NUXT_DATA__"[^>]*>(.*?)</script>', re.DOTALL)
NUXT_REACTIVE_WRAPPERS = frozenset(
    {"ShallowReactive", "Reactive", "Ref", "ShallowRef", "EmptyShallowRef", "EmptyRef"}
)

FALLBACK_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/130.0.0.0 Safari/537.36"
)

MAX_PRICE_RON = 100_000.0


class ScrapeError(RuntimeError):
    """Pagina nu are structura așteptată sau nu conține produse valide."""


@dataclass(frozen=True)
class CatalogEntry:
    storeName: str
    title: str
    price: float
    category: str
    validUntil: str  # yyyy-MM-dd, ora României

    def dedupe_key(self) -> tuple[str, str, float]:
        return (self.storeName, self.title.casefold(), self.price)


@dataclass(frozen=True)
class StoreProbe:
    """Rezultatul verificării rapide: valabilitatea pliantului și cum se încarcă produsele."""

    valid_until: date
    load: Callable[[], list[CatalogEntry]]


@dataclass(frozen=True)
class StoreOutcome:
    store: str
    entries: list[CatalogEntry] | None  # None = magazinul nu s-a modificat


# --- Rețea -----------------------------------------------------------------------------


def pick_user_agent() -> str:
    try:
        from fake_useragent import UserAgent

        return UserAgent(browsers=["chrome"], os=["windows"], platforms=["desktop"]).random
    except Exception as exc:  # biblioteca poate cere rețea la prima utilizare
        log.warning("fake-useragent indisponibil (%s); folosesc un User-Agent fix", exc)
        return FALLBACK_USER_AGENT


def build_session() -> requests.Session:
    retry = Retry(
        total=4,
        backoff_factor=1.0,
        status_forcelist=(429, 500, 502, 503, 504),
        allowed_methods=frozenset({"GET"}),
        respect_retry_after_header=True,
    )
    session = requests.Session()
    adapter = HTTPAdapter(max_retries=retry)
    session.mount("https://", adapter)
    session.mount("http://", adapter)
    session.headers.update(
        {
            "User-Agent": pick_user_agent(),
            "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            "Accept-Language": "ro-RO,ro;q=0.9,en;q=0.7",
        }
    )
    return session


def fetch(
    session: requests.Session,
    url: str,
    *,
    params: dict[str, Any] | None = None,
    expect_json: bool = False,
) -> requests.Response:
    headers = {"Accept": "application/json"} if expect_json else None
    response = session.get(url, params=params, headers=headers, timeout=(10, 45))
    response.raise_for_status()
    log.info("GET %s -> %s (%d octeți)", response.url, response.status_code, len(response.content))
    return response


# --- Decodor pentru payload-ul Nuxt (__NUXT_DATA__) ------------------------------------


class NuxtPayload:
    """Formatul „devalue” al Nuxt: un array plat în care valorile întregi sunt indici."""

    def __init__(self, flat: list[Any]) -> None:
        self._flat = flat
        self._cache: dict[int, Any] = {}

    @classmethod
    def from_html(cls, page: str) -> NuxtPayload:
        match = NUXT_PAYLOAD.search(page)
        if match is None:
            raise ScrapeError("__NUXT_DATA__ lipsește din pagină")
        return cls(json.loads(match.group(1)))

    def root(self) -> Any:
        return self._resolve(0)

    def _resolve(self, index: Any) -> Any:
        if not isinstance(index, int) or isinstance(index, bool):
            return index  # valoare literală, nu referință
        if index < 0 or index >= len(self._flat):
            return None
        if index in self._cache:
            return self._cache[index]
        raw = self._flat[index]
        self._cache[index] = None  # protecție împotriva referințelor circulare
        if isinstance(raw, dict):
            value: Any = {key: self._resolve(ref) for key, ref in raw.items()}
        elif isinstance(raw, list):
            if len(raw) == 2 and isinstance(raw[0], str) and raw[0] in NUXT_REACTIVE_WRAPPERS:
                value = self._resolve(raw[1])
            else:
                value = [self._resolve(ref) for ref in raw]
        else:
            value = raw
        self._cache[index] = value
        return value


def iter_nodes(root: Any) -> Iterator[Any]:
    stack = [root]
    while stack:
        current = stack.pop()
        yield current
        if isinstance(current, dict):
            stack.extend(current.values())
        elif isinstance(current, list):
            stack.extend(current)


# --- Date și valabilitate --------------------------------------------------------------


def as_date(value: str) -> date:
    return date.fromisoformat(value)


def current_valid_until(entries: list[CatalogEntry], store: str) -> date | None:
    """Cea mai târzie valabilitate salvată pentru magazin; None dacă nu există nimic salvat."""
    dates = [as_date(e.validUntil) for e in entries if e.storeName == store]
    return max(dates) if dates else None


def is_new_flyer(current: date | None, found: date, today: date) -> bool:
    """Pliantul este nou dacă e mai târziu decât cel salvat și nu e deja expirat."""
    if found < today:
        return False
    return current is None or found > current


def log_not_published(store: str, found: date) -> None:
    log.info(
        "[%s] Pliantul nou nu este încă publicat (valabilitatea găsită: %s). Nu se modifică fișierul.",
        store,
        found.isoformat(),
    )


def log_expired(store: str, found: date) -> None:
    log.info(
        "[%s] Pliantul găsit a expirat (valabil până la %s). Nu se modifică fișierul.",
        store,
        found.isoformat(),
    )


# --- Penny -----------------------------------------------------------------------------


def find_penny_flyer_slug(root: Any) -> str | None:
    for node in iter_nodes(root):
        if (
            isinstance(node, dict)
            and isinstance(node.get("name"), str)
            and PENNY_FLYER_NAME.search(node["name"])
            and isinstance(node.get("slug"), str)
        ):
            return node["slug"]
    return None


def find_penny_payload_products(root: Any) -> list[dict[str, Any]]:
    for node in iter_nodes(root):
        if (
            isinstance(node, list)
            and node
            and all(isinstance(item, dict) and "productId" in item for item in node)
        ):
            return node
    return []


def fetch_penny_flyer_page(session: requests.Session, slug: str, page: int) -> list[dict[str, Any]]:
    data = fetch(
        session,
        PENNY_CATEGORY_PRODUCTS.format(slug=slug),
        params={"page": page, "pageSize": PENNY_PAGE_SIZE},
        expect_json=True,
    ).json()
    return data.get("results") or []


def fetch_penny_flyer_products(session: requests.Session, slug: str) -> list[dict[str, Any]]:
    products: dict[Any, dict[str, Any]] = {}
    for page in range(PENNY_MAX_PAGES):
        results = fetch_penny_flyer_page(session, slug, page)
        new_items = [item for item in results if item.get("productId") not in products]
        if not new_items:
            break
        for item in new_items:
            products[item["productId"]] = item
    log.info("Penny: %d produse din pliantul „%s”", len(products), slug)
    return list(products.values())


def penny_entry(item: dict[str, Any]) -> CatalogEntry | None:
    price_info = item.get("price") or {}
    regular = price_info.get("regular") or {}
    value = regular.get("value")  # prețul este întreg, în bani
    quantity = regular.get("promotionQuantity") or 1
    valid_until = price_info.get("validityEnd")
    title = " ".join(str(item.get("name") or "").split())
    if not title or not isinstance(value, (int, float)) or value <= 0:
        return None
    if not isinstance(quantity, (int, float)) or quantity <= 0 or not isinstance(valid_until, str):
        return None
    category = " ".join(str(item.get("category") or "").split()) or PENNY_DEFAULT_CATEGORY
    return CatalogEntry(
        storeName="Penny",
        title=title,
        price=round(value / 100 / quantity, 2),
        category=category,
        validUntil=valid_until,
    )


def penny_entries_from(products: list[dict[str, Any]]) -> list[CatalogEntry]:
    return [entry for entry in (penny_entry(item) for item in products) if entry is not None]


def penny_valid_until(products: list[dict[str, Any]]) -> date:
    """Valabilitatea pliantului = cea mai târzie `validityEnd` dintre produsele valide."""
    dates = [as_date(entry.validUntil) for entry in penny_entries_from(products)]
    if not dates:
        raise ScrapeError("Penny: nu există produse cu dată de valabilitate")
    return max(dates)


def probe_penny(session: requests.Session) -> StoreProbe:
    # Pagina pliantului conține doar imaginea de copertă; o cerem ca să confirmăm că e disponibilă.
    fetch(session, PENNY_PLIANT_PAGE)
    root = NuxtPayload.from_html(fetch(session, PENNY_OFFERS_PAGE).text).root()

    slug = find_penny_flyer_slug(root)
    if slug is None:
        log.warning("Penny: nu am găsit categoria „PLIANT NATIONAL” în /oferte")
    else:
        try:
            first_page = fetch_penny_flyer_page(session, slug, 0)
            return StoreProbe(
                valid_until=penny_valid_until(first_page),
                load=lambda: penny_entries_from(fetch_penny_flyer_products(session, slug)),
            )
        except requests.RequestException as exc:
            log.warning("Penny: API-ul pliantului a eșuat (%s)", exc)

    log.warning("Penny: folosesc fallback, produsele din pagina /oferte")
    payload_products = find_penny_payload_products(root)
    return StoreProbe(
        valid_until=penny_valid_until(payload_products),
        load=lambda: penny_entries_from(payload_products),
    )


# --- Lidl ------------------------------------------------------------------------------


def iso_timestamp_to_bucharest_date(value: str) -> str:
    moment = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return moment.astimezone(BUCHAREST).date().isoformat()


def lidl_entry(product: dict[str, Any]) -> CatalogEntry | None:
    if product.get("havingPrice") is not True:
        return None
    price = (product.get("price") or {}).get("price")
    region = (product.get("regionsPrices") or {}).get(LIDL_REGION_KEY) or {}
    end_date = (region.get("currentPrice") or {}).get("endDate")
    title = " ".join(str(product.get("title") or "").split())
    if not title or not isinstance(price, (int, float)) or price <= 0 or not isinstance(end_date, str):
        return None
    raw_category = str(product.get("category") or "")
    return CatalogEntry(
        storeName="Lidl",
        title=title,
        price=round(float(price), 2),
        category=LIDL_CATEGORY_LABELS.get(raw_category, raw_category or LIDL_DEFAULT_CATEGORY),
        validUntil=iso_timestamp_to_bucharest_date(end_date),
    )


def lidl_entries_from_html(page: str) -> list[CatalogEntry]:
    entries: dict[Any, CatalogEntry] = {}
    for match in LIDL_GRID_ATTRIBUTE.finditer(page):
        try:
            product = json.loads(html.unescape(match.group(1)))
        except json.JSONDecodeError:
            continue
        if not isinstance(product, dict) or "productId" not in product:
            continue
        entry = lidl_entry(product)
        if entry is not None:
            entries.setdefault(product["productId"], entry)
    return list(entries.values())


def probe_lidl(session: requests.Session) -> StoreProbe:
    entries = lidl_entries_from_html(fetch(session, LIDL_OFFERS_PAGE).text)
    if not entries:
        raise ScrapeError("Lidl: niciun produs promoțional valid în pagină")
    valid_until = max(as_date(entry.validUntil) for entry in entries)
    log.info("Lidl: %d produse promoționale, valabile până la %s", len(entries), valid_until)
    return StoreProbe(valid_until=valid_until, load=lambda: entries)


PROBES: dict[str, Callable[[requests.Session], StoreProbe]] = {
    "Penny": probe_penny,
    "Lidl": probe_lidl,
}


# --- Salvare --------------------------------------------------------------------------


def is_valid(entry: CatalogEntry, today: date) -> bool:
    try:
        valid_until = as_date(entry.validUntil)
    except ValueError:
        return False
    return bool(entry.title) and 0 < entry.price < MAX_PRICE_RON and valid_until >= today


def clean_entries(entries: list[CatalogEntry], today: date) -> list[CatalogEntry]:
    unique: dict[tuple[str, str, float], CatalogEntry] = {}
    dropped = 0
    for entry in entries:
        if not is_valid(entry, today):
            dropped += 1
            continue
        unique.setdefault(entry.dedupe_key(), entry)
    if dropped:
        log.info("Eliminate %d produse invalide sau expirate", dropped)
    return sorted(unique.values(), key=lambda e: (e.storeName, e.title.casefold()))


def assemble(
    previous: list[CatalogEntry],
    updated: dict[str, list[CatalogEntry]],
    today: date,
) -> list[CatalogEntry]:
    """Înlocuiește secțiunile magazinelor actualizate; restul rămân cum erau."""
    kept = [entry for entry in previous if entry.storeName not in updated]
    replaced = [entry for store in updated for entry in updated[store]]
    return clean_entries(kept + replaced, today)


def load_previous(path: Path) -> list[CatalogEntry]:
    if not path.exists():
        return []
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
        return [CatalogEntry(**item) for item in raw]
    except (OSError, ValueError, TypeError) as exc:
        log.warning("Fișierul anterior %s nu poate fi citit (%s)", path, exc)
        return []


def write_atomic(path: Path, payload: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(dir=path.parent, suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        os.replace(tmp_name, path)
    except BaseException:
        Path(tmp_name).unlink(missing_ok=True)
        raise


# --- Orchestrare ----------------------------------------------------------------------


def run_store(
    store: str,
    session: requests.Session,
    previous: list[CatalogEntry],
    today: date,
) -> StoreOutcome:
    probe = PROBES[store](session)
    current = current_valid_until(previous, store)
    if not is_new_flyer(current, probe.valid_until, today):
        if probe.valid_until < today:
            log_expired(store, probe.valid_until)
        else:
            log_not_published(store, probe.valid_until)
        return StoreOutcome(store=store, entries=None)

    entries = clean_entries(probe.load(), today)
    if not entries:
        raise ScrapeError(f"{store}: pliantul nou nu are produse valide")
    log.info("[%s] Pliant nou detectat și actualizat! (valabil până la %s)", store, probe.valid_until)
    return StoreOutcome(store=store, entries=entries)


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Extrage ofertele Penny și Lidl.")
    parser.add_argument(
        "--store",
        type=str.lower,
        choices=sorted(STORE_ARGUMENTS),
        default="all",
        help="Magazinul de verificat (implicit: toate)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help="Fișierul JSON de ieșire (implicit: app/src/main/assets/catalog_products.json)",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    args = parse_args(argv)

    selected = [STORE_ARGUMENTS[args.store]] if args.store != "all" else list(STORES)
    today = datetime.now(BUCHAREST).date()
    previous = load_previous(args.output)
    session = build_session()

    updated: dict[str, list[CatalogEntry]] = {}
    failed: list[str] = []
    for store in selected:
        try:
            outcome = run_store(store, session, previous, today)
        except (requests.RequestException, ScrapeError, ValueError, KeyError, TypeError, IndexError) as exc:
            log.error("%s: verificarea a eșuat: %s", store, exc)
            failed.append(store)
            continue
        if outcome.entries is not None:
            updated[store] = outcome.entries

    if not updated:
        log.info("Niciun pliant nou; %s nu a fost modificat.", args.output)
        return 2 if failed else 0

    final_entries = assemble(previous, updated, today)
    if not final_entries:
        log.error("Catalog gol după actualizare; %s nu a fost modificat", args.output)
        return 1

    write_atomic(args.output, [asdict(entry) for entry in final_entries])
    per_store = {store: sum(1 for e in final_entries if e.storeName == store) for store in STORES}
    log.info("Salvat %d produse în %s (%s)", len(final_entries), args.output, per_store)
    return 2 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
