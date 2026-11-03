# api Lambda：读 S3 里各商店的文件，合并后返回。只有两个文件、只用标准库与运行时自带的 boto3，不用打包脚本。
# api Lambda: reads each store's file from S3 and returns them merged. Two files, standard library plus the
# runtime's own boto3, so no build script is needed.

locals {
  api_function   = "steal-sale-events-api"
  api_source_dir = "${local.backend_root}/functions/sale_events_api"
}

data "archive_file" "api" {
  type        = "zip"
  output_path = "${local.backend_root}/build/api.zip"

  source {
    content  = file("${local.api_source_dir}/api_handler.py")
    filename = "api_handler.py"
  }

  source {
    content  = file("${local.api_source_dir}/api_core.py")
    filename = "api_core.py"
  }
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/lambda/${local.api_function}"
  retention_in_days = 30
}

resource "aws_lambda_function" "api" {
  function_name    = local.api_function
  description      = "Serves GET /v1/sale-events from the store files in S3."
  role             = aws_iam_role.api.arn
  runtime          = "python3.12"
  architectures    = ["arm64"]
  handler          = "api_handler.handler"
  filename         = data.archive_file.api.output_path
  source_code_hash = data.archive_file.api.output_base64sha256
  memory_size      = 128
  timeout          = 10

  environment {
    variables = {
      BUCKET_NAME = aws_s3_bucket.data.bucket
    }
  }

  logging_config {
    log_format = "Text"
    log_group  = aws_cloudwatch_log_group.api.name
  }
}

# MARK: - IAM

resource "aws_iam_role" "api" {
  name               = "${local.api_function}-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

# 只读：列出与读取 sale-events/ 下的对象、写自己的日志。
# Read only: list and read objects under sale-events/ and write its own logs.
data "aws_iam_policy_document" "api" {
  statement {
    sid       = "ListBucket"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.data.arn]
  }

  statement {
    sid       = "ReadData"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.data.arn}/${local.data_prefix}*"]
  }

  statement {
    sid       = "WriteLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.api.arn}:*"]
  }
}

resource "aws_iam_role_policy" "api" {
  name   = "api-read-access"
  role   = aws_iam_role.api.id
  policy = data.aws_iam_policy_document.api.json
}

# MARK: - API Gateway HTTP API

resource "aws_apigatewayv2_api" "public" {
  name          = "steal-public-api"
  protocol_type = "HTTP"
  description   = "steaL public data API (GET /v1/sale-events)."
}

resource "aws_apigatewayv2_integration" "api" {
  api_id                 = aws_apigatewayv2_api.public.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.api.invoke_arn
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "sale_events" {
  api_id    = aws_apigatewayv2_api.public.id
  route_key = "GET /v1/sale-events"
  target    = "integrations/${aws_apigatewayv2_integration.api.id}"
}

# $default stage 自动部署；限流是整个 stage 的上限，超出返回 429。
# The $default stage deploys automatically; throttling caps the whole stage and returns 429 beyond it.
resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.public.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = 10
    throttling_burst_limit = 20
  }
}

resource "aws_lambda_permission" "api_gateway" {
  statement_id  = "AllowHttpApiInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.api.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.public.execution_arn}/*/*"
}
