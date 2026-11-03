variable "hcp_organization" {
  description = "Existing HCP Terraform organization name."
  type        = string
  default     = "emag-org"

  validation {
    condition     = length(trimspace(var.hcp_organization)) > 0
    error_message = "hcp_organization must not be empty."
  }
}

variable "workspace_name" {
  description = "Workspace managing this configuration and its own state."
  type        = string
  default     = "morecat-prod"
}

variable "terraform_version" {
  description = "Terraform version used for remote runs; match the local CLI."
  type        = string
  default     = "1.17.1"
}
