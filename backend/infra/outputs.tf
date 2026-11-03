output "api_url" {
  description = "对外接口地址 / Public API URL."
  value       = "${aws_apigatewayv2_stage.default.invoke_url}v1/sale-events"
}

output "bucket_name" {
  description = "活动数据 bucket / Event data bucket."
  value       = aws_s3_bucket.data.bucket
}

output "refresh_function_name" {
  description = "手动调用：aws lambda invoke --function-name <它> --payload '{\"store\":\"steam\"}' ... / Manual invocation target."
  value       = aws_lambda_function.refresh.function_name
}

output "api_function_name" {
  description = "api Lambda 的名字 / The api Lambda's name."
  value       = aws_lambda_function.api.function_name
}
