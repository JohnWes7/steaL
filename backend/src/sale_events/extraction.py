"""调 DeepSeek 一次，从预处理后的正文里抄出活动日期。prompt 与输出结构由各商店的模块提供。
Calls DeepSeek once to copy event dates out of reduced page text. Each store's module supplies the prompt and output shape.

AI 只抄日期，不换算时区、不生成 id、不填来源网址，这些由代码做。
The AI only copies dates; time zones, ids and source URLs are handled by code.
"""

import json
import logging

from openai import OpenAI
from pydantic import BaseModel, ValidationError

logger = logging.getLogger(__name__)

DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-flash"
MAX_TOKENS = 4000


class ExtractionError(Exception):
    """AI 没有给出能用的 JSON。带着原始输出，方便存进 rejected 调 prompt。
    The AI did not return usable JSON. Carries the raw output so it can be saved for prompt tuning.
    """

    def __init__(self, message: str, raw_output: str) -> None:
        super().__init__(message)
        self.raw_output = raw_output


def parse_extraction[Result: BaseModel](raw_output: str, result_type: type[Result]) -> Result:
    """把 AI 的原始输出解析成结构化结果（结构校验在这里）。
    Parses the AI's raw output into a structured result (this is the structure check).

    - Parameters:
      - raw_output: AI 返回的 content / The content returned by the AI.
      - result_type: 输出应有的结构，由商店模块定义 / The expected output shape, defined by the store module.
    - Returns: 解析后的结果 / The parsed result.
    - Raises: `ExtractionError` 不是 JSON 或字段不对时 / `ExtractionError` when it is not JSON or fields are wrong.
    """
    try:
        payload = json.loads(raw_output)
    except json.JSONDecodeError as error:
        raise ExtractionError(f"not json: {error}", raw_output) from error

    try:
        return result_type.model_validate(payload)
    except ValidationError as error:
        raise ExtractionError(f"schema mismatch: {error}", raw_output) from error


class DeepSeekExtractor:
    """用 DeepSeek 的 JSON Output 提取活动，与商店无关。
    Extracts events with DeepSeek's JSON Output mode, independent of the store.
    """

    def __init__(self, api_key: str, model: str = DEFAULT_MODEL, base_url: str = DEFAULT_BASE_URL) -> None:
        self.client = OpenAI(api_key=api_key, base_url=base_url)
        self.model = model
        self.last_raw_output = ""

    def extract[Result: BaseModel](self, system_prompt: str, page_text: str, result_type: type[Result]) -> Result:
        """把正文发给 AI，返回抄出来的活动。
        Sends the text to the AI and returns the copied events.

        官方说明 JSON Output 偶尔会返回空 content，所以空的时候重试一次。
        DeepSeek documents that JSON Output occasionally returns empty content, so an empty reply is retried once.

        - Parameters:
          - system_prompt: 商店模块的提取规则 / The store module's extraction rules.
          - page_text: 预处理后的正文 / The reduced page text.
          - result_type: 输出应有的结构 / The expected output shape.
        - Returns: 抄出来的活动 / The copied events.
        - Raises: `ExtractionError` 两次都是空或解析失败 / `ExtractionError` if both tries are empty or unparsable.
        """
        raw_output = self._request(system_prompt, page_text)
        if raw_output.strip() == "":
            logger.warning("DeepSeek returned empty content, retrying once")
            raw_output = self._request(system_prompt, page_text)

        self.last_raw_output = raw_output
        if raw_output.strip() == "":
            raise ExtractionError("empty content twice", raw_output)

        return parse_extraction(raw_output, result_type)

    def _request(self, system_prompt: str, page_text: str) -> str:
        """发一次 Chat Completions 请求，返回 content（可能为空字符串）。
        Sends one Chat Completions request and returns the content (possibly empty).
        """
        messages = [
            {"role": "system", "content": system_prompt},
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
