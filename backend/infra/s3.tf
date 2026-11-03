# 活动数据的私有 bucket：sale-events/{store}.json 与 sale-events/rejected/。
# Private bucket for event data: sale-events/{store}.json and sale-events/rejected/.

data "aws_caller_identity" "current" {}

locals {
  # bucket 名全球唯一，带上账号 ID 避免撞名。
  # Bucket names are globally unique; the account ID avoids collisions.
  bucket_name = "steal-sale-events-${data.aws_caller_identity.current.account_id}"
  data_prefix = "sale-events/"
}

resource "aws_s3_bucket" "data" {
  bucket = local.bucket_name
}

resource "aws_s3_bucket_public_access_block" "data" {
  bucket = aws_s3_bucket.data.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "data" {
  bucket = aws_s3_bucket.data.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "data" {
  bucket = aws_s3_bucket.data.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 旧版本自动留着，出错可以回滚、对比。
# Old versions are kept automatically for rollback and comparison.
resource "aws_s3_bucket_versioning" "data" {
  bucket = aws_s3_bucket.data.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "data" {
  bucket = aws_s3_bucket.data.id

  # 覆盖前的旧版本留 180 天。
  # Overwritten versions are kept for 180 days.
  rule {
    id     = "expire-old-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days = 180
    }
  }

  # 校验不过的 AI 输出只用来调 prompt，留 90 天。
  # Rejected AI output is only for prompt tuning; keep it for 90 days.
  rule {
    id     = "expire-rejected"
    status = "Enabled"

    filter {
      prefix = "${local.data_prefix}rejected/"
    }

    expiration {
      days = 90
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.data]
}
