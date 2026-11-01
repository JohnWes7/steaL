from datetime import date

from sale_events.normalize import make_event_id, slugify


def test_slugify_replaces_punctuation_and_spaces() -> None:
    assert slugify("Dinos vs. Robots Fest") == "dinos-vs-robots-fest"
    assert slugify("Couch Co-Op Fest") == "couch-co-op-fest"


def test_event_id_includes_start_date() -> None:
    assert make_event_id("steam", date(2027, 2, 22), "Next Fest") == "steam-2027-02-22-next-fest"
