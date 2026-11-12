# EventBridge Scheduler：每个商店一个定时器，都调同一个 refresh Lambda，事件里带商店名。
# EventBridge Scheduler: one schedule per store, all invoking the same refresh Lambda with the store name in the event.

locals {
  # cron 用 UTC。暂时全部每周一刷新一次（itch.io / GOG 只在促销开始时发公告，每周刷新最多晚 7 天才收录）。
  # Cron in UTC. For now every store refreshes once a week on Monday (itch.io / GOG only announce when a sale starts, so a sale may show up to 7 days late).
  refresh_schedules = {
    steam  = "cron(0 0 ? * MON *)"
    epic   = "cron(10 0 ? * MON *)"
    itchio = "cron(0 1 ? * MON *)"
    gog    = "cron(10 1 ? * MON *)"
  }
}

data "aws_iam_policy_document" "scheduler_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["scheduler.amazonaws.com"]
    }

    # 只允许本账号的 Scheduler 用这个角色。
    # Only this account's Scheduler may use the role.
    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "scheduler" {
  name               = "steal-refresh-scheduler-role"
  assume_role_policy = data.aws_iam_policy_document.scheduler_assume.json
}

data "aws_iam_policy_document" "scheduler" {
  statement {
    actions   = ["lambda:InvokeFunction"]
    resources = [aws_lambda_function.refresh.arn]
  }
}

resource "aws_iam_role_policy" "scheduler" {
  name   = "invoke-refresh"
  role   = aws_iam_role.scheduler.id
  policy = data.aws_iam_policy_document.scheduler.json
}

resource "aws_scheduler_schedule" "refresh" {
  for_each = local.refresh_schedules

  name                         = "steal-refresh-${each.key}"
  schedule_expression          = each.value
  schedule_expression_timezone = "UTC"
  state                        = var.schedules_enabled ? "ENABLED" : "DISABLED"

  flexible_time_window {
    mode = "OFF"
  }

  target {
    arn      = aws_lambda_function.refresh.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ store = each.key })

    # 这里的重试只管「调用没送达」；送达后 Lambda 执行失败不重试（见 lambda_refresh.tf）。
    # These retries only cover undelivered invocations; failures after delivery are not retried (see lambda_refresh.tf).
    retry_policy {
      maximum_retry_attempts = 2
    }
  }
}
