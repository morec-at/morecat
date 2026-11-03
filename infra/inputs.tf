locals {
  workspace_inputs = {
    hcp_organization  = var.hcp_organization
    workspace_name    = var.workspace_name
    terraform_version = var.terraform_version
  }
}

resource "tfe_variable" "inputs" {
  for_each = local.workspace_inputs

  workspace_id = tfe_workspace.prod.id
  category     = "terraform"
  key          = each.key
  value        = each.value
}
