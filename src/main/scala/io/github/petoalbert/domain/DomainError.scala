package io.github.petoalbert.domain

sealed trait DomainError

case object NotFound extends DomainError
