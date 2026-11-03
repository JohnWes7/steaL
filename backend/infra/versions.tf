# Terraform 与 provider 版本。.terraform.lock.hcl 要提交（锁住 provider 的精确版本）。
# Terraform and provider versions. Commit .terraform.lock.hcl (it pins exact provider versions).

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.71"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.8"
    }
  }

  # state 先放本地（已 gitignore，里面有 DeepSeek key 明文），以后再挪到 S3 远端 backend。
  # State stays local for now (gitignored; it holds the DeepSeek key in plain text); move to an S3 backend later.
}

provider "aws" {
  region  = var.region
  profile = var.aws_profile

  # 所有资源统一打标签，账单里能按项目筛选。
  # Tag every resource so billing can be filtered by project.
  default_tags {
    tags = {
      Project   = "steal"
      ManagedBy = "terraform"
    }
  }
}
