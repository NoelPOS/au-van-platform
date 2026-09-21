# The availability zones this account may use in the region. Reading them
# rather than naming them keeps the module portable and keeps the subnet
# definitions free of region-specific literals.
data "aws_availability_zones" "available" {
  state = "available"
}
