package morecat.infrastructure.firestore

import com.google.cloud.firestore.FirestoreException
import io.grpc.Status
import zio.test.*

import java.util.concurrent.{CompletionException, ExecutionException}

object GoogleFirestoreErrorMapperSpec extends ZIOSpecDefault:

  def spec = suite("GoogleFirestoreErrorMapper")(
    test("maps ALREADY_EXISTS gRPC failures to AlreadyExists") {
      val error = Status.ALREADY_EXISTS.withDescription("document exists").asRuntimeException()

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) == FirestoreClientError.AlreadyExists
      )
    },
    test("maps ALREADY_EXISTS FirestoreException failures to AlreadyExists") {
      val error = FirestoreException.forServerRejection(
        Status.ALREADY_EXISTS,
        "document exists"
      )

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) == FirestoreClientError.AlreadyExists
      )
    },
    test("maps ABORTED FirestoreException failures to Conflict") {
      val error = FirestoreException.forServerRejection(
        Status.ABORTED,
        "transaction contention"
      )

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) ==
          FirestoreClientError.Conflict("transaction contention")
      )
    },
    test("returns an unwrapped ABORTED Firestore cause for transaction retry") {
      val cause = FirestoreException.forServerRejection(
        Status.ABORTED,
        "transaction contention"
      )

      assertTrue(
        GoogleFirestoreErrorMapper.abortedCause(ExecutionException(cause)).contains(cause)
      )
    },
    test("does not retry non-ABORTED Firestore failures") {
      val grpcError = Status.FAILED_PRECONDITION.asRuntimeException()
      val otherError = RuntimeException("failed")

      assertTrue(
        GoogleFirestoreErrorMapper.abortedCause(grpcError).isEmpty,
        GoogleFirestoreErrorMapper.abortedCause(otherError).isEmpty,
      )
    },
    test("maps FAILED_PRECONDITION gRPC failures separately") {
      val error = Status.FAILED_PRECONDITION
        .withDescription("precondition failed")
        .asRuntimeException()

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) ==
          FirestoreClientError.FailedPrecondition("FAILED_PRECONDITION: precondition failed")
      )
    },
    test("maps PERMISSION_DENIED FirestoreException failures separately") {
      val error = FirestoreException.forServerRejection(
        Status.PERMISSION_DENIED,
        "iam denied"
      )

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) ==
          FirestoreClientError.PermissionDenied("iam denied")
      )
    },
    test("maps INVALID_ARGUMENT gRPC failures separately") {
      val error = Status.INVALID_ARGUMENT.withDescription("bad request").asRuntimeException()

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) ==
          FirestoreClientError.InvalidArgument("INVALID_ARGUMENT: bad request")
      )
    },
    test("unwraps ExecutionException before mapping Firestore failures") {
      val error =
        ExecutionException(
          Status.ALREADY_EXISTS.withDescription("document exists").asRuntimeException()
        )

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) == FirestoreClientError.AlreadyExists
      )
    },
    test("unwraps CompletionException before mapping Firestore failures") {
      val error =
        CompletionException(
          Status.ALREADY_EXISTS.withDescription("document exists").asRuntimeException()
        )

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) == FirestoreClientError.AlreadyExists
      )
    },
    test("keeps wrapper messages when ExecutionException has no cause") {
      val error = ExecutionException("wrapper only", null)

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) ==
          FirestoreClientError.Unavailable("wrapper only")
      )
    },
    test("keeps wrapper messages when CompletionException has no cause") {
      val error = CompletionException("wrapper only", null)

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) ==
          FirestoreClientError.Unavailable("wrapper only")
      )
    },
    test("maps other failures to Unavailable with the failure message") {
      val error = Status.UNAVAILABLE.withDescription("firestore down").asRuntimeException()

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) ==
          FirestoreClientError.Unavailable("UNAVAILABLE: firestore down")
      )
    },
    test("falls back to toString when failures have no message") {
      val error = RuntimeException(null: String)

      assertTrue(
        GoogleFirestoreErrorMapper.toClientError(error) ==
          FirestoreClientError.Unavailable("java.lang.RuntimeException")
      )
    },
  )
