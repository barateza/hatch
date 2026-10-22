package pw.barateza.hatch.engine

import com.jcraft.jsch.JSchException
import java.io.EOFException
import java.io.IOException
import java.net.BindException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pw.barateza.hatch.model.ErrorKind

class ErrorClassifierTest {

  @Test
  fun wrongSshPasswordOrKeyIsFatalSshAuth() {
    val failedPassword = ErrorClassifier.classify(JSchException("Auth fail for methods 'password'"))
    assertEquals(ErrorKind.SSH_AUTH, failedPassword.kind)
    assertFalse(failedPassword.retryable)
    assertEquals(FailureAction.FATAL, failedPassword.action)

    val invalidKey = ErrorClassifier.classify(JSchException("invalid privatekey: [B@1234"))
    assertEquals(ErrorKind.SSH_AUTH, invalidKey.kind)
    assertEquals(FailureAction.FATAL, invalidKey.action)

    val badPassphrase = ErrorClassifier.classify(JSchException("Incorrect passphrase provided."))
    assertEquals(ErrorKind.SSH_AUTH, badPassphrase.kind)

    val missingPassphrase = ErrorClassifier.classify(JSchException("Passphrase is not given."))
    assertEquals(ErrorKind.SSH_AUTH, missingPassphrase.kind)
  }

  @Test
  fun localPortAlreadyInUseIsFatalPortInUse() {
    val fromJsch =
      ErrorClassifier.classify(
        JSchException(
          "PortForwardingL: local port 3080 cannot be bound.",
          BindException("Address already in use"),
        ),
      )
    assertEquals(ErrorKind.PORT_IN_USE, fromJsch.kind)
    assertFalse(fromJsch.retryable)
    assertEquals(FailureAction.FATAL, fromJsch.action)

    val direct = ErrorClassifier.classify(BindException("Address already in use"))
    assertEquals(ErrorKind.PORT_IN_USE, direct.kind)
  }

  @Test
  fun badLocalForwardPortIsFatalPortInUse() {
    val classification = ErrorClassifier.classify(LocalPortException("bound 45000 instead of 3080"))
    assertEquals(ErrorKind.PORT_IN_USE, classification.kind)
    assertEquals(FailureAction.FATAL, classification.action)
  }

  @Test
  fun unresolvedHostIsRetryableDns() {
    val classification = ErrorClassifier.classify(UnknownHostException("ssh.example.com"))
    assertEquals(ErrorKind.DNS, classification.kind)
    assertTrue(classification.retryable)
    assertEquals(FailureAction.RETRY, classification.action)
  }

  @Test
  fun timeoutIsRetryable() {
    val classification = ErrorClassifier.classify(SocketTimeoutException("Read timed out"))
    assertEquals(ErrorKind.TIMEOUT, classification.kind)
    assertTrue(classification.retryable)
    assertEquals(FailureAction.RETRY, classification.action)
  }

  @Test
  fun genericIoFailureIsRetryableNetwork() {
    val classification = ErrorClassifier.classify(IOException("Connection reset by peer"))
    assertEquals(ErrorKind.NETWORK, classification.kind)
    assertTrue(classification.retryable)

    val eof = ErrorClassifier.classify(EOFException("unexpected end of stream"))
    assertEquals(ErrorKind.NETWORK, eof.kind)
  }

  @Test
  fun connectionRefusedIsRetryableNetwork() {
    val classification = ErrorClassifier.classify(ConnectException("Failed to connect to /1.2.3.4:443"))
    assertEquals(ErrorKind.NETWORK, classification.kind)
    assertTrue(classification.retryable)
  }

  @Test
  fun tokenRejectionRoutesToNeedsAuth() {
    for (status in listOf(302, 401, 403)) {
      val classification = ErrorClassifier.classify(WebSocketUpgradeException(status))
      assertEquals(ErrorKind.ACCESS_AUTH, classification.kind)
      assertEquals(FailureAction.NEEDS_AUTH, classification.action)
      assertTrue(classification.retryable)
    }
  }

  @Test
  fun websocketFailureWithoutStatusFallsThroughToCause() {
    val dns = ErrorClassifier.classify(WebSocketUpgradeException(null, UnknownHostException("ssh.example.com")))
    assertEquals(ErrorKind.DNS, dns.kind)

    val network = ErrorClassifier.classify(WebSocketUpgradeException(null, IOException("boom")))
    assertEquals(ErrorKind.NETWORK, network.kind)
  }

  @Test
  fun httpServerErrorsAreRetryableNetworkAndClientErrorsAreProtocol() {
    assertEquals(ErrorKind.NETWORK, ErrorClassifier.fromHttpStatus(503).kind)
    assertTrue(ErrorClassifier.fromHttpStatus(503).retryable)
    assertEquals(ErrorKind.PROTOCOL, ErrorClassifier.fromHttpStatus(404).kind)
  }

  @Test
  fun protocolFailuresAreRetryableProtocol() {
    val classification = ErrorClassifier.classify(JSchException("Algorithm negotiation fail"))
    assertEquals(ErrorKind.PROTOCOL, classification.kind)
    assertTrue(classification.retryable)
    assertEquals(FailureAction.RETRY, classification.action)
  }

  @Test
  fun invalidConfigurationIsFatalConfig() {
    val classification = ErrorClassifier.classify(ConfigException("Invalid SSH host"))
    assertEquals(ErrorKind.CONFIG, classification.kind)
    assertFalse(classification.retryable)
    assertEquals(FailureAction.FATAL, classification.action)
  }

  @Test
  fun unknownFailuresAreRetryableUnknown() {
    val classification = ErrorClassifier.classify(IllegalStateException("something odd"))
    assertEquals(ErrorKind.UNKNOWN, classification.kind)
    assertTrue(classification.retryable)
    assertEquals(FailureAction.RETRY, classification.action)
  }

  @Test
  fun titlesAreStable() {
    assertEquals("SSH authentication failed", FailureClassification.titleFor(ErrorKind.SSH_AUTH))
    assertEquals("Local port already in use", FailureClassification.titleFor(ErrorKind.PORT_IN_USE))
    assertEquals("Cloudflare Access login required", FailureClassification.titleFor(ErrorKind.ACCESS_AUTH))
  }
}
