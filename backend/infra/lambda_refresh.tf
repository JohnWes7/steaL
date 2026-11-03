# refresh Lambda：每次调用刷新事件里的一个商店，写进 S3。
# refresh Lambda: each invocation refreshes the one store named in the event and writes to S3.

locals {
  backend_root      = abspath("${path.module}/..")
  refresh_build_dir = "${local.backend_root}/build/refresh"
  refresh_function  = "steal-sale-events-refresh"

  # 这些文件变了才重新打包（依赖锁、包源码、入口、打包脚本）。
  # Rebuild only when these change (dependency lock, package source, entry files, build script).
  refresh_source_files = concat(
    ["uv.lock", "pyproject.toml", "scripts/build_refresh_lambda.sh"],
    [for file in fileset(local.backend_root, "src/sale_events/**/*.py") : file],
    [for file in fileset(local.backend_root, "functions/sale_events_refresh/*.py") : file],
  )
  refresh_source_hashes = [for file in local.refresh_source_files : filesha256("${local.backend_root}/${file}")]
  refresh_source_hash   = sha256(join("", local.refresh_source_hashes))
}

# 跑打包脚本（按 uv.lock 装 Linux arm64 依赖），源码没变就不重跑。
# Runs the build script (Linux arm64 dependencies from uv.lock); skipped when the source is unchanged.
resource "terraform_data" "refresh_build" {
  triggers_replace = [local.refresh_source_hash]

  provisioner "local-exec" {
    command = "${local.backend_root}/scripts/build_refresh_lambda.sh"
  }
}

# depends_on 让它在打包之后才读目录（打包有变化时推迟到 apply 阶段）。
# depends_on makes it read the folder after the build (deferred to apply when the build changes).
data "archive_file" "refresh" {
  type        = "zip"
  source_dir  = local.refresh_build_dir
  output_path = "${local.backend_root}/build/refresh.zip"

  depends_on = [terraform_data.refresh_build]
}

resource "aws_cloudwatch_log_group" "refresh" {
  name              = "/aws/lambda/${local.refresh_function}"
  retention_in_days = 30
}

resource "aws_lambda_function" "refresh" {
  function_name    = local.refresh_function
  description      = "Refreshes one store's sale events into S3 (event: {\"store\": ...})."
  role             = aws_iam_role.refresh.arn
  runtime          = "python3.12"
  architectures    = ["arm64"]
  handler          = "refresh_handler.handler"
  filename         = data.archive_file.refresh.output_path
  source_code_hash = data.archive_file.refresh.output_base64sha256
  memory_size      = 512
  timeout          = 600

  environment {
    variables = {
      BUCKET_NAME      = aws_s3_bucket.data.bucket
      DEEPSEEK_API_KEY = var.deepseek_api_key
    }
  }

  logging_config {
    log_format = "Text"
    log_group  = aws_cloudwatch_log_group.refresh.name
  }
}

# Scheduler 是异步调用：失败不让 Lambda 自动重试（每次都会调 DeepSeek 花钱，下一次定时会再跑）。
# Scheduler invokes asynchronously: no automatic retries on failure (each one calls DeepSeek; the next schedule runs anyway).
resource "aws_lambda_function_event_invoke_config" "refresh" {
  function_name          = aws_lambda_function.refresh.function_name
  maximum_retry_attempts = 0
}

# MARK: - IAM

data "aws_iam_policy_document" "lambda_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "refresh" {
  name               = "${local.refresh_function}-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume.json
}

# 只能读写 sale-events/ 下的对象、写自己的日志。
# Can only read and write objects under sale-events/ and write its own logs.
data "aws_iam_policy_document" "refresh" {
  # 不加前缀条件：读不存在的对象时，S3 只有在调用者能 ListBucket 时才返回 404（否则 403），
  # 「还没有数据」靠的就是这个 404。这个 bucket 只放本项目数据。
  # No prefix condition: S3 returns 404 for a missing object only when the caller may ListBucket (403 otherwise),
  # and "no data yet" relies on that 404. The bucket holds nothing but this project's data.
  statement {
    sid       = "ListBucket"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.data.arn]
  }

  statement {
    sid       = "ReadWriteData"
    actions   = ["s3:GetObject", "s3:PutObject"]
    resources = ["${aws_s3_bucket.data.arn}/${local.data_prefix}*"]
  }

  statement {
    sid       = "WriteLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.refresh.arn}:*"]
  }
}

resource "aws_iam_role_policy" "refresh" {
  name   = "refresh-access"
  role   = aws_iam_role.refresh.id
  policy = data.aws_iam_policy_document.refresh.json
}
