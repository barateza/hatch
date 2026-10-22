package pw.barateza.hatch.runtime.tailscale

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleHostsTest {
  @Test
  fun magicDnsNamesAreTailscaleHosts() {
    assertTrue(TailscaleHosts.isTailscaleHost("node.tailnet.ts.net"))
    assertTrue(TailscaleHosts.isTailscaleHost("NODE.Tailnet.TS.NET."))
  }

  @Test
  fun cgnatAddressesAreTailscaleHosts() {
    assertTrue(TailscaleHosts.isTailscaleHost("100.64.0.1"))
    assertTrue(TailscaleHosts.isTailscaleHost("100.127.255.254"))
    assertFalse(TailscaleHosts.isTailscaleHost("100.63.255.255"))
    assertFalse(TailscaleHosts.isTailscaleHost("100.128.0.1"))
    assertFalse(TailscaleHosts.isTailscaleHost("100.64.0.999"))
  }

  @Test
  fun tailscaleIpv6AndBracketsAreRecognized() {
    assertTrue(TailscaleHosts.isTailscaleHost("[fd7a:115c:a1e0::1]"))
    assertTrue(TailscaleHosts.isTailscaleHost("fd7a:115c:a1e0::1"))
  }

  @Test
  fun publicHostsAreNotTailscale() {
    assertFalse(TailscaleHosts.isTailscaleHost("ssh.example.com"))
    assertFalse(TailscaleHosts.isTailscaleHost("127.0.0.1"))
    assertFalse(TailscaleHosts.isTailscaleHost(""))
  }
}
