variable "region" {
  description = "部署区域（香港）/ Deployment region (Hong Kong)."
  type        = string
  default     = "ap-east-1"
}

variable "aws_profile" {
  description = "本机 AWS CLI profile，先 `aws login --profile steal` / Local AWS CLI profile; run `aws login --profile steal` first."
  type        = string
  default     = "steal"
}

variable "deepseek_api_key" {
  description = "云上专用的 DeepSeek key，从 terraform.tfvars（不提交）或 TF_VAR_deepseek_api_key 传入 / Cloud-only DeepSeek key, from terraform.tfvars (not committed) or TF_VAR_deepseek_api_key."
  type        = string
  sensitive   = true
}

variable "schedules_enabled" {
  description = "定时刷新是否开启。先手动调用 refresh 验证，再改成 true / Whether scheduled refreshes run. Verify refresh by hand first, then set to true."
  type        = bool
  default     = false
}
