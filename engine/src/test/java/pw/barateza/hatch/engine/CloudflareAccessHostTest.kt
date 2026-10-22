package pw.barateza.hatch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CloudflareAccessHostTest {
  @Test
  fun `endpoint and tunnel URLs parse for an example host`() {
    assertNotNull(CloudflareAccess.endpointUrl("ssh.example.com"))
    assertNotNull(CloudflareAccess.tunnelUrl("ssh.example.com"))
  }

  @Test
  fun `tunnel URL keeps the host and becomes the https origin OkHttp needs`() {
    val url = CloudflareAccess.tunnelUrl("ssh.example.com")
    assertNotNull(url)
    assertEquals("ssh.example.com", url!!.host)
    assertEquals("https", url.scheme)
    assertEquals("/", url.encodedPath)
  }

  @Test
  fun `prefixes and trailing slashes are tolerated`() {
    for (input in listOf("https://ssh.example.com/", "wss://ssh.example.com", "https://ssh.example.com")) {
      assertEquals("ssh.example.com", CloudflareAccess.tunnelUrl(input)!!.host)
    }
  }
}
