"""调 DeepSeek 一次，从预处理后的 Steamworks 正文里抄出活动日期。
Calls DeepSeek once to copy event dates out of the reduced Steamworks text.

AI 只抄日期，不换算时区、不生成 id、不填来源网址，这些由代码做。
The AI only copies dates; time zones, ids and source URLs are handled by code.
"""

import json
import logging

from openai import OpenAI
from pydantic import ValidationError

from .models import ExtractionResult

logger = logging.getLogger(__name__)

DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-flash"
MAX_TOKENS = 4000

SYSTEM_PROMPT = """\
You copy sale event dates from the Steamworks "Upcoming Steam Events" page. Reply with json only.

Rules:
- One entry per event that has concrete dates. Skip events whose dates are TBD.
- "section" is where the event is listed: "seasonalSale" under "## Seasonal Sales", \
"themedFest" in the tables under "## Themed Sale Events (Fests)", "nextFest" under "## Next Fest".
- "name" must be copied exactly as written on the page (for fests, the Theme column). \
Do not translate, shorten or rephrase it. Several Next Fest events are all named "Next Fest".
- "startDate" and "endDate" are YYYY-MM-DD, copied from the page. Do not add times or convert time zones.
- Fest table dates only have month and day: take the year from the heading above the table \
("## 2026 Fests" means 2026). If the end month is before the start month, the end is in the next year.
- "June 14 - 21, 2027" means June 14, 2027 to June 21, 2027.

Example json output:
{
  "events": [
    {"section": "seasonalSale", "name": "Winter Sale 2026", "startDate": "2026-12-17", "endDate": "2027-01-04"},
    {"section": "themedFest", "name": "Steam Scream V", "startDate": "2026-10-26", "endDate": "2026-11-02"},
    {"section": "nextFest", "name": "Next Fest", "startDate": "2027-02-22", "endDate": "2027-03-01"}
  ]
}
"""


class ExtractionError(Exception):
    """AI 没有给出能用的 JSON。带着原始输出，方便存进 rejected 调 prompt。
    The AI did not return usable JSON. Carries the raw output so it can be saved for prompt tuning.
    """

    def __init__(self, message: str, raw_output: str) -> None:
        super().__init__(message)
        self.raw_output = raw_output


def parse_extraction(raw_output: str) -> ExtractionResult:
    """把 AI 的原始输出解析成结构化结果（结构校验在这里）。
    Parses the AI's raw output into a structured result (this is the structure check).

    - Parameter raw_output: AI 返回的 content / The content returned by the AI.
    - Returns: 解析后的活动 / The parsed events.
    - Raises: `ExtractionError` 不是 JSON 或字段不对时 / `ExtractionError` when it is not JSON or fields are wrong.
    """
    try:
        payload = json.loads(raw_output)
    except json.JSONDecodeError as error:
        raise ExtractionError(f"not json: {error}", raw_output) from error

    try:
        return ExtractionResult.model_validate(payload)
    except ValidationError as error:
        raise ExtractionError(f"schema mismatch: {error}", raw_output) from error


class DeepSeekExtractor:
    """用 DeepSeek 的 JSON Output 提取 Steam 活动。
    Extracts Steam events with DeepSeek's JSON Output mode.
    """

    def __init__(self, api_key: str, model: str = DEFAULT_MODEL, base_url: str = DEFAULT_BASE_URL) -> None:
        self.client = OpenAI(api_key=api_key, base_url=base_url)
        self.model = model
        self.last_raw_output = ""

    def extract(self, page_text: str) -> ExtractionResult:
        """把正文发给 AI，返回抄出来的活动。
        Sends the text to the AI and returns the copied events.

        官方说明 JSON Output 偶尔会返回空 content，所以空的时候重试一次。
        DeepSeek documents that JSON Output occasionally returns empty content, so an empty reply is retried once.

        - Parameter page_text: 预处理后的正文 / The reduced page text.
        - Returns: 抄出来的活动 / The copied events.
        - Raises: `ExtractionError` 两次都是空或解析失败 / `ExtractionError` if both tries are empty or unparsable.
        """
        raw_output = self._request(page_text)
        if raw_output.strip() == "":
            logger.warning("DeepSeek returned empty content, retrying once")
            raw_output = self._request(page_text)

        self.last_raw_output = raw_output
        if raw_output.strip() == "":
            raise ExtractionError("empty content twice", raw_output)

        return parse_extraction(raw_output)

    def _request(self, page_text: str) -> str:
        """发一次 Chat Completions 请求，返回 content（可能为空字符串）。
        Sends one Chat Completions request and returns the content (possibly empty).
        """
        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": page_text},
        ]
        completion = self.client.chat.completions.create(
            model=self.model,
            messages=messages,
            response_format={"type": "json_object"},
            max_tokens=MAX_TOKENS,
            temperature=0,
        )
        choice = completion.choices[0]
        logger.debug("finish_reason=%s usage=%s", choice.finish_reason, completion.usage)
        content = choice.message.content
        if content is None:
            return ""
        return content
