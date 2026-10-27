"""Teste pentru logica de verificare a valabilității (fără rețea).

Rulare (din rădăcina proiectului):
    python -m unittest discover -s scripts -p "test_*.py" -v
"""

from __future__ import annotations

import unittest
from datetime import date
from unittest import mock

import fetch_catalogs as fc
from fetch_catalogs import CatalogEntry, StoreProbe

TODAY = date(2026, 10, 27)


def entry(store: str, title: str, valid_until: str, price: float = 5.0) -> CatalogEntry:
    return CatalogEntry(
        storeName=store,
        title=title,
        price=price,
        category="Test",
        validUntil=valid_until,
    )


class IsNewFlyerTest(unittest.TestCase):

    def test_same_validity_is_not_new(self) -> None:
        self.assertFalse(fc.is_new_flyer(date(2026, 11, 3), date(2026, 11, 3), TODAY))

    def test_older_validity_is_not_new(self) -> None:
        self.assertFalse(fc.is_new_flyer(date(2026, 11, 3), date(2026, 10, 27), TODAY))

    def test_later_validity_is_new(self) -> None:
        self.assertTrue(fc.is_new_flyer(date(2026, 11, 1), date(2026, 11, 8), TODAY))

    def test_nothing_saved_is_new(self) -> None:
        self.assertTrue(fc.is_new_flyer(None, date(2026, 11, 1), TODAY))

    def test_expired_flyer_is_not_new(self) -> None:
        self.assertFalse(fc.is_new_flyer(None, date(2026, 10, 20), TODAY))


class CurrentValidUntilTest(unittest.TestCase):

    def test_uses_latest_date_of_the_store_only(self) -> None:
        entries = [
            entry("Penny", "A", "2026-11-03"),
            entry("Penny", "B", "2026-11-10"),
            entry("Lidl", "C", "2026-12-01"),
        ]
        self.assertEqual(date(2026, 11, 10), fc.current_valid_until(entries, "Penny"))

    def test_none_when_store_missing(self) -> None:
        self.assertIsNone(fc.current_valid_until([entry("Lidl", "C", "2026-11-01")], "Penny"))


class PennyValidityTest(unittest.TestCase):

    def test_takes_latest_validity_end(self) -> None:
        products = [
            {"name": "A", "price": {"regular": {"value": 425}, "validityEnd": "2026-11-03"}},
            {"name": "B", "price": {"regular": {"value": 199}, "validityEnd": "2026-11-10"}},
        ]
        self.assertEqual(date(2026, 11, 10), fc.penny_valid_until(products))

    def test_raises_without_valid_products(self) -> None:
        with self.assertRaises(fc.ScrapeError):
            fc.penny_valid_until([{"name": "", "price": {}}])


class AssemblyTest(unittest.TestCase):

    def test_replaces_only_updated_store(self) -> None:
        previous = [
            entry("Penny", "Vechi P", "2026-11-03"),
            entry("Lidl", "Lidl păstrat", "2026-11-01"),
        ]
        updated = {"Penny": [entry("Penny", "Nou P", "2026-11-10")]}
        result = fc.assemble(previous, updated, TODAY)
        titles = {(e.storeName, e.title) for e in result}
        self.assertEqual({("Penny", "Nou P"), ("Lidl", "Lidl păstrat")}, titles)


class RunStoreTest(unittest.TestCase):

    def _run(self, store: str, found: date, previous: list[CatalogEntry]):
        probe = StoreProbe(
            valid_until=found,
            load=lambda: [entry(store, f"{store} produs", found.isoformat())],
        )
        with mock.patch.dict(fc.PROBES, {store: lambda session: probe}):
            return fc.run_store(store, session=None, previous=previous, today=TODAY)

    def test_unpublished_flyer_leaves_store_untouched(self) -> None:
        previous = [entry("Penny", "Vechi", "2026-11-03")]
        outcome = self._run("Penny", date(2026, 11, 3), previous)
        self.assertIsNone(outcome.entries)

    def test_new_flyer_returns_products(self) -> None:
        previous = [entry("Penny", "Vechi", "2026-11-03")]
        outcome = self._run("Penny", date(2026, 11, 10), previous)
        self.assertIsNotNone(outcome.entries)
        self.assertEqual("Penny produs", outcome.entries[0].title)

    def test_expired_flyer_leaves_store_untouched(self) -> None:
        outcome = self._run("Lidl", date(2026, 10, 1), [])
        self.assertIsNone(outcome.entries)


if __name__ == "__main__":
    unittest.main()
