resource "tfe_workspace" "prod" {
  name               = var.workspace_name
  organization       = var.hcp_organization
  terraform_version  = var.terraform_version
  working_directory  = "infra"
  auto_apply         = false
  allow_destroy_plan = false
  force_delete       = false

  lifecycle {
    prevent_destroy = true
  }
}

resource "tfe_workspace_settings" "prod" {
  workspace_id              = tfe_workspace.prod.id
  execution_mode            = "remote"
  global_remote_state       = false
  project_remote_state      = false
  remote_state_consumer_ids = []
}
