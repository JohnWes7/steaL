from types import SimpleNamespace

import pytest

from sale_events.extraction import DeepSeekExtractor, ExtractionError, parse_extraction

VALID_OUTPUT = '{"events": [{"section": "nextFest", "name": "Next Fest", "startDate": "2027-02-22", "endDate": "2027-03-01"}]}'


def test_parse_extraction_reads_camel_case_json() -> None:
    result = parse_extraction(VALID_OUTPUT)
    assert len(result.events) == 1
    assert result.events[0].name == "Next Fest"


def test_parse_extraction_rejects_non_json() -> None:
    with pytest.raises(ExtractionError) as error_info:
        parse_extraction("Sure! Here are the events:")
    assert error_info.value.raw_output == "Sure! Here are the events:"


def test_parse_extraction_rejects_unknown_section() -> None:
    output = '{"events": [{"section": "summer", "name": "X", "startDate": "2027-01-01", "endDate": "2027-01-02"}]}'
    with pytest.raises(ExtractionError):
        parse_extraction(output)


def test_parse_extraction_rejects_tbd_dates() -> None:
    output = '{"events": [{"section": "seasonalSale", "name": "Autumn Sale 2027", "startDate": "TBD", "endDate": "TBD"}]}'
    with pytest.raises(ExtractionError):
        parse_extraction(output)


class FakeCompletions:
    """按顺序返回预设 content 的假 Chat Completions。
    A fake Chat Completions that returns preset contents in order.
    """

    def __init__(self, contents: list[str]) -> None:
        self.contents = contents
        self.calls = 0

    def create(self, **kwargs: object) -> SimpleNamespace:
        content = self.contents[self.calls]
        self.calls += 1
        message = SimpleNamespace(content=content)
        choice = SimpleNamespace(message=message, finish_reason="stop")
        return SimpleNamespace(choices=[choice], usage=None)


def make_extractor(contents: list[str]) -> tuple[DeepSeekExtractor, FakeCompletions]:
    extractor = DeepSeekExtractor(api_key="test")
    completions = FakeCompletions(contents)
    chat = SimpleNamespace(completions=completions)
    extractor.client = SimpleNamespace(chat=chat)
    return extractor, completions


def test_extractor_retries_once_on_empty_content() -> None:
    extractor, completions = make_extractor(["   ", VALID_OUTPUT])
    result = extractor.extract("page")
    assert completions.calls == 2
    assert len(result.events) == 1


def test_extractor_fails_after_two_empty_replies() -> None:
    extractor, completions = make_extractor(["", " "])
    with pytest.raises(ExtractionError):
        extractor.extract("page")
    assert completions.calls == 2
